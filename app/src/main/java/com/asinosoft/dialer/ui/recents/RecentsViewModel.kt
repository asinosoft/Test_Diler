package com.asinosoft.dialer.ui.recents

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.data.repository.CallLogRepository
import com.asinosoft.dialer.data.repository.ContactsWriteRepository
import com.asinosoft.dialer.data.repository.FavoritesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds
data class ContactDetailState(
    val contact: FavoriteContact,
    val initialTab: Int = 0,
    val isFavorite: Boolean = false
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
    private val contactsWriteRepository = ContactsWriteRepository(application)

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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLoadedCallLogs = MutableStateFlow(false)
    val hasLoadedCallLogs: StateFlow<Boolean> = _hasLoadedCallLogs.asStateFlow()

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
    private lateinit var contactsObserver: ContentObserver
    private var callLogReloadJob: Job? = null
    private var contactsReloadJob: Job? = null
    private var loadCallLogsJob: Job? = null
    private var suppressCallLogObserverUntilElapsed = 0L
    private var suppressContactsObserverUntilElapsed = 0L

    init {
        loadTabs()
        startObservingCallLogs()
        startObservingContacts()
    }

    private fun startObservingCallLogs() {
        callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (!_hasLoadedCallLogs.value) return
                if (SystemClock.elapsedRealtime() < suppressCallLogObserverUntilElapsed) return
                scheduleCallLogReload()
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

    private fun startObservingContacts() {
        contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (!_hasLoadedCallLogs.value) return
                if (SystemClock.elapsedRealtime() < suppressContactsObserverUntilElapsed) return
                scheduleContactsReload()
            }
        }
        try {
            val cr = getApplication<Application>().contentResolver
            cr.registerContentObserver(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver
            )
            cr.registerContentObserver(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                true,
                contactsObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleCallLogReload() {
        callLogReloadJob?.cancel()
        callLogReloadJob = viewModelScope.launch {
            delay(400)
            loadCallLogs(showLoading = false)
        }
    }

    private fun scheduleContactsReload() {
        contactsReloadJob?.cancel()
        contactsReloadJob = viewModelScope.launch {
            delay(500)
            repository.clearNameCache()
            val favorites = withContext(Dispatchers.IO) {
                favoritesRepository.getFavorites()
            }
            seedCallLogCachesFromFavorites(favorites)
            _favorites.value = favorites
            // Instant UI: prefs may have been refreshed from Android already
            _rawCallLogs.value = applyFavoriteNames(_rawCallLogs.value, favorites)
            syncOpenContactDetail(favorites)
            // Force full reload even if a previous load is still running
            loadCallLogsJob?.cancel()
            loadCallLogs(showLoading = false)
        }
    }

    private suspend fun syncOpenContactDetail(favorites: List<FavoriteContact>) {
        val current = _contactDetailToShow.value
        val liveNameForOpen = if (current != null &&
            favorites.none { sameContact(it, current.contact) }
        ) {
            withContext(Dispatchers.IO) {
                repository.resolveDisplayName(current.contact.number)
            }
        } else {
            null
        }

        _contactDetailToShow.update { state ->
            if (state == null) return@update null
            val match = favorites.find { sameContact(it, state.contact) }
            if (match != null) {
                state.copy(
                    contact = match.copy(
                        tabId = state.contact.tabId,
                        order = state.contact.order
                    ),
                    isFavorite = true
                )
            } else {
                val contact = if (!liveNameForOpen.isNullOrBlank() &&
                    liveNameForOpen != state.contact.name
                ) {
                    state.contact.copy(name = liveNameForOpen)
                } else {
                    state.contact
                }
                state.copy(
                    contact = contact,
                    isFavorite = favorites.any { sameContact(it, contact) }
                )
            }
        }
        _selectedFavorite.update { selected ->
            if (selected == null) return@update null
            favorites.find { sameContact(it, selected) }
                ?.copy(tabId = selected.tabId, order = selected.order)
                ?: selected
        }
    }

    private fun sameContact(a: FavoriteContact, b: FavoriteContact): Boolean {
        val aId = a.id.trim().takeIf { it.isNotEmpty() && it != "0" }
        val bId = b.id.trim().takeIf { it.isNotEmpty() && it != "0" }
        if (aId != null && bId != null && aId == bId) return true
        val aPhone = phoneMatchKey(a.number)
        val bPhone = phoneMatchKey(b.number)
        return aPhone != null && aPhone == bPhone
    }

    override fun onCleared() {
        callLogReloadJob?.cancel()
        contactsReloadJob?.cancel()
        loadCallLogsJob?.cancel()
        try {
            val cr = getApplication<Application>().contentResolver
            cr.unregisterContentObserver(callLogObserver)
            cr.unregisterContentObserver(contactsObserver)
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

    fun loadCallLogs(showLoading: Boolean = true) {
        if (loadCallLogsJob?.isActive == true) {
            if (showLoading) return
            loadCallLogsJob?.cancel()
        }
        loadCallLogsJob = viewModelScope.launch {
            val shouldShowLoading = showLoading && !_hasLoadedCallLogs.value
            if (shouldShowLoading) _isLoading.value = true
            try {
                val favorites = withContext(Dispatchers.IO) {
                    favoritesRepository.getFavorites()
                }
                seedCallLogCachesFromFavorites(favorites)

                if (shouldShowLoading || _rawCallLogs.value.isEmpty()) {
                    // Phase 1: fast window so startup time stays the same
                    _rawCallLogs.value = applyFavoriteNames(
                        repository.getCallLogs(CallLogRepository.DEFAULT_RECENTS_LIMIT),
                        favorites
                    )
                    _favorites.value = favorites
                    _hasLoadedCallLogs.value = true
                    _isLoading.value = false

                    // Phase 2: full journal in background (photo cache already warm)
                    _rawCallLogs.value = applyFavoriteNames(
                        repository.getCallLogs(limit = null),
                        favorites
                    )
                } else {
                    // Silent refresh — full list, no spinner
                    _rawCallLogs.value = applyFavoriteNames(
                        repository.getCallLogs(limit = null),
                        favorites
                    )
                    _favorites.value = favorites
                }
                syncOpenContactDetail(favorites)
            } finally {
                _hasLoadedCallLogs.value = true
                // Ignore observer spam for a few seconds after cold start
                suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 3_000L
                _isLoading.value = false
            }
        }
    }

    private fun seedCallLogCachesFromFavorites(favorites: List<FavoriteContact>) {
        val byNumber = favorites.associate { it.number to it.photoUri }
        val byName = favorites.associate { it.name to it.photoUri }
        repository.seedPhotoCache(byNumber, byName)
        repository.seedNameCache(favorites.associate { it.number to it.name })
    }

    /** Prefer favorite display names over stale CallLog.CACHED_NAME. */
    private fun applyFavoriteNames(
        logs: List<CallLogItem>,
        favorites: List<FavoriteContact>
    ): List<CallLogItem> {
        if (logs.isEmpty() || favorites.isEmpty()) return logs
        val nameByPhone = HashMap<String, String>(favorites.size * 2)
        for (fav in favorites) {
            val key = phoneMatchKey(fav.number) ?: continue
            if (fav.name.isNotBlank()) nameByPhone[key] = fav.name
        }
        if (nameByPhone.isEmpty()) return logs
        return logs.map { item ->
            val key = phoneMatchKey(item.number) ?: return@map item
            val favName = nameByPhone[key] ?: return@map item
            if (item.name == favName) item else item.copy(name = favName)
        }
    }

    private fun phoneMatchKey(number: String): String? {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(10) else null
    }

    private fun patchCallLogNames(phones: Collection<String>, newName: String) {
        val keys = phones.mapNotNull { phoneMatchKey(it) }.toSet()
        if (keys.isEmpty() || newName.isBlank()) return
        _rawCallLogs.value = _rawCallLogs.value.map { item ->
            val key = phoneMatchKey(item.number)
            if (key != null && key in keys) item.copy(name = newName) else item
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

    fun addFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 1_500L
            val contactWithTab = contact.copy(tabId = _activeTabId.value)
            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.addFavorite(contactWithTab)
            }
            _contactDetailToShow.update { state ->
                if (state == null) null
                else state.copy(
                    contact = state.contact.copy(tabId = contactWithTab.tabId),
                    isFavorite = true
                )
            }
        }
    }

    fun updateFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L

            withContext(Dispatchers.IO) {
                contactsWriteRepository.renameContact(
                    contact = contact,
                    newDisplayName = contact.name,
                    phoneNumbers = listOf(contact.number)
                )
            }

            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.updateFavorite(contact)
            }
            patchCallLogNames(listOf(contact.number), contact.name)
            _contactDetailToShow.update { state ->
                state?.copy(contact = contact, isFavorite = true)
            }
        }
    }

    /**
     * Full contact edit from detail dialog: Android Contacts + CallLog + favorites + UI list.
     */
    fun saveEditedContact(
        original: FavoriteContact,
        updated: FavoriteContact,
        phones: List<ContactsWriteRepository.PhoneEntry>,
        emails: List<ContactsWriteRepository.EmailEntry>,
        birthdayDateString: String?,
        photoBitmap: android.graphics.Bitmap?
    ) {
        viewModelScope.launch {
            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L

            withContext(Dispatchers.IO) {
                contactsWriteRepository.updateContactDetails(
                    contact = original,
                    displayName = updated.name,
                    phones = phones,
                    emails = emails,
                    birthdayDateString = birthdayDateString,
                    photoBitmap = photoBitmap
                )
            }

            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.updateFavorite(updated)
            }
            val phoneNumbers = phones.map { it.number }.ifEmpty { listOf(updated.number) }
            patchCallLogNames(phoneNumbers, updated.name)
            _contactDetailToShow.update { state ->
                state?.copy(contact = updated, isFavorite = favoritesRepository.isFavorite(updated))
            }
        }
    }

    fun setContactFavorite(contact: FavoriteContact, favorite: Boolean) {
        if (favorite) addFavorite(contact) else removeFavorite(contact)
    }

    fun removeFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 1_500L
            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.removeFavorite(contact)
            }
            _contactDetailToShow.update { state ->
                if (state == null) return@update null
                val same =
                    state.contact.id == contact.id ||
                            state.contact.number == contact.number ||
                            state.contact.name.equals(contact.name, ignoreCase = true)
                if (same) state.copy(isFavorite = false) else state
            }
            clearFavoriteSelection()
        }
    }

    fun openContactDetail(contact: FavoriteContact, initialTab: Int = 0) {
        if (_isSearchDialerOpen.value) return
        openContactDetailJob?.cancel()
        openContactDetailJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200.milliseconds)
            if (!_isSearchDialerOpen.value) {
                val isFav = withContext(Dispatchers.IO) {
                    favoritesRepository.isFavorite(contact)
                }
                _contactDetailToShow.value = ContactDetailState(contact, initialTab, isFav)
            }
        }
    }

    fun openContactDetailFromCallLog(item: CallLogItem) {
        val cleanCallNum = item.number.filter { it.isDigit() || it == '+' }
        val existingFav = _favorites.value.find { fav ->
            val cleanFavNum = fav.number.filter { it.isDigit() || it == '+' }
            (cleanCallNum.isNotBlank() && cleanFavNum.isNotBlank() &&
                    cleanCallNum.takeLast(7) == cleanFavNum.takeLast(7)) ||
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
