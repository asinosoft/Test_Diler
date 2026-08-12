package com.asinosoft.dialer.ui.recents

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.data.repository.CallLogRepository
import com.asinosoft.dialer.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

data class ContactDetailState(
    val contact: FavoriteContact,
    val initialTab: Int = 0
)

data class SearchDialerItem(
    val id: String,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val timestamp: Long = 0L,
    val simSlot: Int? = null,
    val callType: CallType? = null,
    val isFavorite: Boolean = false
)

class RecentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)
    private val favoritesRepository = FavoritesRepository(application)

    private val _rawCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())

    private val _favorites = MutableStateFlow<List<FavoriteContact>>(emptyList())
    val favorites: StateFlow<List<FavoriteContact>> = _favorites.asStateFlow()

    private val _tabs = MutableStateFlow<List<FavoriteTab>>(emptyList())
    val tabs: StateFlow<List<FavoriteTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("default")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    val activeTabFavorites: StateFlow<List<FavoriteContact>> =
        combine(_favorites, _activeTabId) { favs, tabId ->
            favs.filter { it.tabId == tabId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFavorite = MutableStateFlow<FavoriteContact?>(null)
    val selectedFavorite: StateFlow<FavoriteContact?> = _selectedFavorite.asStateFlow()

    private val _isTopBarVisible = MutableStateFlow(false)
    val isTopBarVisible: StateFlow<Boolean> = _isTopBarVisible.asStateFlow()

    private val _isAddFavoriteOpen = MutableStateFlow(false)
    val isAddFavoriteOpen: StateFlow<Boolean> = _isAddFavoriteOpen.asStateFlow()

    private val prefs = application.getSharedPreferences("dialer_settings", Context.MODE_PRIVATE)

    private val _favoriteRowsCount = MutableStateFlow(prefs.getInt("favorite_rows_count", 3))
    val favoriteRowsCount: StateFlow<Int> = _favoriteRowsCount.asStateFlow()

    private val _isAppSettingsOpen = MutableStateFlow(false)
    val isAppSettingsOpen: StateFlow<Boolean> = _isAppSettingsOpen.asStateFlow()

    private val _contactDetailToShow = MutableStateFlow<ContactDetailState?>(null)
    val contactDetailToShow: StateFlow<ContactDetailState?> = _contactDetailToShow.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyMissed = MutableStateFlow(false)
    val showOnlyMissed: StateFlow<Boolean> = _showOnlyMissed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearchDialerOpen = MutableStateFlow(false)
    val isSearchDialerOpen: StateFlow<Boolean> = _isSearchDialerOpen.asStateFlow()

    private val _dialerQuery = MutableStateFlow("")
    val dialerQuery: StateFlow<String> = _dialerQuery.asStateFlow()

    val filteredDialerResults: StateFlow<List<SearchDialerItem>> = combine(
        _rawCallLogs,
        _favorites,
        _dialerQuery
    ) { logs, favs, query ->
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank()) {
            logs.map { log ->
                SearchDialerItem(
                    id = "log_${log.id}",
                    name = log.name ?: log.number,
                    number = log.number,
                    photoUri = log.photoUri,
                    timestamp = log.timestamp,
                    simSlot = log.simNumber,
                    callType = log.type,
                    isFavorite = favs.any { it.number == log.number }
                )
            }.distinctBy { it.number }
        } else {
            val matchedFavs = favs.filter { fav ->
                fav.name.lowercase().contains(cleanQuery) ||
                        fav.number.contains(cleanQuery) ||
                        fav.name.toT9Digits().contains(cleanQuery)
            }.map { fav ->
                SearchDialerItem(
                    id = "fav_${fav.id}",
                    name = fav.name,
                    number = fav.number,
                    photoUri = fav.photoUri,
                    timestamp = 0L,
                    simSlot = null,
                    callType = null,
                    isFavorite = true
                )
            }

            val matchedLogs = logs.filter { log ->
                (log.name?.lowercase()?.contains(cleanQuery) == true) ||
                        log.number.contains(cleanQuery) ||
                        (log.name != null && log.name.toT9Digits().contains(cleanQuery))
            }.map { log ->
                SearchDialerItem(
                    id = "log_${log.id}",
                    name = log.name ?: log.number,
                    number = log.number,
                    photoUri = log.photoUri,
                    timestamp = log.timestamp,
                    simSlot = log.simNumber,
                    callType = log.type,
                    isFavorite = favs.any { it.number == log.number }
                )
            }

            (matchedFavs + matchedLogs).distinctBy { it.number }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var openContactDetailJob: kotlinx.coroutines.Job? = null

    fun openSearchDialer(initialQuery: String = "") {
        openContactDetailJob?.cancel()
        _contactDetailToShow.value = null
        clearFavoriteSelection()
        _dialerQuery.value = initialQuery
        _isSearchDialerOpen.value = true
    }

    fun closeSearchDialer() {
        _isSearchDialerOpen.value = false
        _dialerQuery.value = ""
    }

    fun onDialerQueryChange(newQuery: String) {
        _dialerQuery.value = newQuery
    }

    fun appendDialerDigit(digit: String) {
        _dialerQuery.update { it + digit }
    }

    fun deleteDialerDigit() {
        _dialerQuery.update { if (it.isNotEmpty()) it.dropLast(1) else "" }
    }

    fun clearDialerQuery() {
        _dialerQuery.value = ""
    }

    private lateinit var callLogObserver: ContentObserver

    init {
        loadTabs()
        loadFavorites()
        startObservingCallLogs()
    }

    private fun startObservingCallLogs() {
        callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                loadCallLogs()
            }
        }
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
        } catch (_: Exception) {
            // ignore
        }
    }

    val filteredCallLogs = combine(
        _rawCallLogs,
        _searchQuery,
        _showOnlyMissed
    ) { logs, query, missedOnly ->
        logs.filter { item ->
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                (item.name?.contains(query, ignoreCase = true) == true) ||
                        item.number.contains(query, ignoreCase = true)
            }

            val matchesFilter = if (missedOnly) {
                item.type == CallType.MISSED || item.type == CallType.REJECTED
            } else {
                true
            }

            matchesQuery && matchesFilter
        }
    }

    fun loadCallLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            _rawCallLogs.value = repository.getCallLogs()
            loadFavorites()
            _isLoading.value = false
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            _favorites.value = favoritesRepository.getFavorites()
        }
    }

    private fun loadTabs() {
        viewModelScope.launch {
            _tabs.value = favoritesRepository.getTabs()
            if (_tabs.value.none { it.id == _activeTabId.value } && _tabs.value.isNotEmpty()) {
                _activeTabId.value = _tabs.value.first().id
            }
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
    }

    fun addTab(name: String) {
        viewModelScope.launch {
            _tabs.value = favoritesRepository.addTab(name)
            if (_tabs.value.isNotEmpty()) {
                _activeTabId.value = _tabs.value.last().id
            }
        }
    }

    fun renameTab(id: String, newName: String) {
        viewModelScope.launch {
            _tabs.value = favoritesRepository.renameTab(id, newName)
        }
    }

    fun deleteTab(id: String) {
        viewModelScope.launch {
            _tabs.value = favoritesRepository.deleteTab(id)
            if (_tabs.value.none { it.id == _activeTabId.value }) {
                _activeTabId.value = _tabs.value.firstOrNull()?.id ?: "default"
            }
            loadFavorites()
        }
    }

    fun selectFavorite(contact: FavoriteContact) {
        _selectedFavorite.value = contact
        _isTopBarVisible.value = true
    }

    fun clearFavoriteSelection() {
        _selectedFavorite.value = null
        _isTopBarVisible.value = false
    }

    fun addFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            val contactWithTab = contact.copy(tabId = _activeTabId.value)
            _favorites.value = favoritesRepository.addFavorite(contactWithTab)
        }
    }

    fun updateFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            _favorites.value = favoritesRepository.addFavorite(contact)
        }
    }

    fun removeFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            _favorites.value = favoritesRepository.removeFavorite(contact.id)
            clearFavoriteSelection()
        }
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val currentTabId = _activeTabId.value
        val allList = _favorites.value.toMutableList()
        val tabContacts = allList.filter { it.tabId == currentTabId }.toMutableList()

        if (fromIndex in tabContacts.indices && toIndex in tabContacts.indices && fromIndex != toIndex) {
            val item = tabContacts.removeAt(fromIndex)
            tabContacts.add(toIndex, item)
            val reorderedTabContacts =
                tabContacts.mapIndexed { idx, contact -> contact.copy(order = idx) }

            val otherTabContacts = allList.filter { it.tabId != currentTabId }
            val updatedAllList =
                (otherTabContacts + reorderedTabContacts).mapIndexed { idx, contact ->
                    contact.copy(order = idx)
                }

            _favorites.value = updatedAllList
            viewModelScope.launch {
                favoritesRepository.saveFavorites(updatedAllList)
            }
        }
    }

    fun openAddFavoriteDialog() {
        _isAddFavoriteOpen.value = true
    }

    fun closeAddFavoriteDialog() {
        _isAddFavoriteOpen.value = false
    }

    fun setFavoriteRowsCount(count: Int) {
        val validCount = count.coerceIn(1, 8)
        _favoriteRowsCount.value = validCount
        prefs.edit { putInt("favorite_rows_count", validCount) }
    }

    fun openAppSettings() {
        clearFavoriteSelection()
        _isAppSettingsOpen.value = true
    }

    fun closeAppSettings() {
        _isAppSettingsOpen.value = false
    }

    fun openContactDetail(contact: FavoriteContact, initialTab: Int = 0) {
        if (_isSearchDialerOpen.value) return
        openContactDetailJob?.cancel()
        openContactDetailJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200.milliseconds)
            if (!_isSearchDialerOpen.value) {
                _contactDetailToShow.value = ContactDetailState(contact, initialTab)
            }
        }
    }

    fun openContactDetailFromCallLog(item: CallLogItem) {
        val cleanCallNum = item.number.replace(Regex("[^0-9+]"), "")
        val existingFav = _favorites.value.find { fav ->
            val cleanFavNum = fav.number.replace(Regex("[^0-9+]"), "")
            (cleanCallNum.isNotBlank() && cleanFavNum.isNotBlank() && cleanCallNum.takeLast(7) == cleanFavNum.takeLast(
                7
            )) ||
                    (!item.name.isNullOrBlank() && fav.name.trim()
                        .equals(item.name.trim(), ignoreCase = true))
        }

        val contact = existingFav ?: FavoriteContact(
            id = cleanCallNum.ifBlank { "call_log_${item.id}" },
            name = item.name ?: item.number,
            number = item.number,
            photoUri = item.photoUri
        )
        openContactDetail(contact, initialTab = 1)
    }

    fun closeContactDetail() {
        openContactDetailJob?.cancel()
        _contactDetailToShow.value = null
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setShowOnlyMissed(missedOnly: Boolean) {
        _showOnlyMissed.value = missedOnly
    }
}

private fun String.toT9Digits(): String {
    val sb = StringBuilder()
    for (ch in this.lowercase()) {
        val digit = when (ch) {
            'a', 'b', 'c', 'а', 'б', 'в', 'г' -> '2'
            'd', 'e', 'f', 'д', 'е', 'ж', 'з' -> '3'
            'g', 'h', 'i', 'и', 'й', 'к', 'л' -> '4'
            'j', 'k', 'l', 'м', 'н', 'о', 'п', 'р' -> '5'
            'm', 'n', 'o', 'с', 'т', 'у', 'ф' -> '6'
            'p', 'q', 'r', 's', 'х', 'ц', 'ч', 'ш' -> '7'
            't', 'u', 'v', 'щ', 'ъ', 'ы', 'ь' -> '8'
            'w', 'x', 'y', 'z', 'э', 'ю', 'я' -> '9'
            else -> if (ch.isDigit()) ch else null
        }
        if (digit != null) sb.append(digit)
    }
    return sb.toString()
}
