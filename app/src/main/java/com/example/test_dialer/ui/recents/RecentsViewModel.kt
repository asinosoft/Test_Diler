package com.example.test_dialer.ui.recents

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.data.model.FavoriteTab
import com.example.test_dialer.data.repository.CallLogRepository
import com.example.test_dialer.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactDetailState(
    val contact: FavoriteContact,
    val initialTab: Int = 0
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

    val activeTabFavorites: StateFlow<List<FavoriteContact>> = combine(_favorites, _activeTabId) { favs, tabId ->
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

    private var callLogObserver: ContentObserver? = null

    init {
        loadTabs()
        loadFavorites()
        startObservingCallLogs()
    }

    fun startObservingCallLogs() {
        if (callLogObserver != null) return
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
                callLogObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        callLogObserver?.let {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                // ignore
            }
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

    fun loadFavorites() {
        viewModelScope.launch {
            _favorites.value = favoritesRepository.getFavorites()
        }
    }

    fun loadTabs() {
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
            val reorderedTabContacts = tabContacts.mapIndexed { idx, contact -> contact.copy(order = idx) }

            val otherTabContacts = allList.filter { it.tabId != currentTabId }
            val updatedAllList = (otherTabContacts + reorderedTabContacts).mapIndexed { idx, contact ->
                contact.copy(order = idx)
            }

            _favorites.value = updatedAllList
            viewModelScope.launch {
                favoritesRepository.saveFavorites(updatedAllList)
            }
        }
    }

    fun swapFavorites(contact1Id: String, contact2Id: String) {
        val list = _favorites.value.toMutableList()
        val idx1 = list.indexOfFirst { it.id == contact1Id }
        val idx2 = list.indexOfFirst { it.id == contact2Id }
        if (idx1 != -1 && idx2 != -1 && idx1 != idx2) {
            reorderFavorites(idx1, idx2)
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
        prefs.edit().putInt("favorite_rows_count", validCount).apply()
    }

    fun openAppSettings() {
        clearFavoriteSelection()
        _isAppSettingsOpen.value = true
    }

    fun closeAppSettings() {
        _isAppSettingsOpen.value = false
    }

    fun openContactDetail(contact: FavoriteContact, initialTab: Int = 0) {
        _contactDetailToShow.value = ContactDetailState(contact, initialTab)
    }

    fun openContactDetailFromCallLog(item: CallLogItem) {
        val contact = FavoriteContact(
            id = item.id,
            name = item.name ?: item.number,
            number = item.number,
            photoUri = item.photoUri
        )
        openContactDetail(contact, initialTab = 1)
    }

    fun closeContactDetail() {
        _contactDetailToShow.value = null
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setShowOnlyMissed(missedOnly: Boolean) {
        _showOnlyMissed.value = missedOnly
    }
}
