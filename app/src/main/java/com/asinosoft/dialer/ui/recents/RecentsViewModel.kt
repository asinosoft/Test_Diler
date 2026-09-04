package com.asinosoft.dialer.ui.recents

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.data.model.DialerOpenMode
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
import com.asinosoft.dialer.data.repository.ContactsRepository
import kotlin.time.Duration.Companion.milliseconds

data class ContactDetailState(
    val contact: FavoriteContact,
    val initialTab: Int = 0,
    val isFavorite: Boolean = false
)

enum class CallTypeFilter(val title: String) {
    ALL("Все"),
    INCOMING("Входящие"),
    OUTGOING("Исходящие"),
    MISSED("Пропущенные")
}

enum class SimFilter(val title: String) {
    ALL("Все SIM"),
    SIM_1("SIM 1"),
    SIM_2("SIM 2")
}

data class UnsavedNumberFlowState(
    val phoneNumber: String,
    val step: UnsavedNumberFlowStep
)

sealed class UnsavedNumberFlowStep {
    data object Choose : UnsavedNumberFlowStep()
    data object CreateNew : UnsavedNumberFlowStep()
    data object PickExisting : UnsavedNumberFlowStep()
    data class EditExisting(val contact: FavoriteContact) : UnsavedNumberFlowStep()
}

data class SearchDialerItem(
    val id: String,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val timestamp: Long = 0L,
    val simSlot: Int? = null,
    val callType: CallType? = null,
    val isFavorite: Boolean = false
) {
    class Matcher(query: String) {
        private val query = query.lowercase().trim()

        fun isBlank(): Boolean = query.isBlank()

        fun match(log: CallLogItem): Boolean =
            (log.name?.lowercase()?.contains(query) == true) ||
                    log.number.contains(query) ||
                    (log.name != null && log.name.toT9Digits().contains(query))


        fun match(item: SearchDialerItem): Boolean =
            item.name.lowercase().contains(query) ||
                    item.number.contains(query) ||
                    item.name.toT9Digits().contains(query)

        fun order(item: SearchDialerItem): Int =
            item.name.lowercase().indexOf(query).takeIf { it >= 0 }
                ?: item.name.toT9Digits().indexOf(query).takeIf { it >= 0 }
                ?: Int.MAX_VALUE
    }
}

data class SearchResults(
    val calls: List<SearchDialerItem> = listOf(),
    val contacts: List<SearchDialerItem> = listOf(),
) {
    fun isEmpty(): Boolean = calls.isEmpty() && contacts.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()
}

class RecentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)
    private val favoritesRepository = FavoritesRepository(application)

    private val contactsRepository = ContactsRepository(application)
    private val contactsWriteRepository = ContactsWriteRepository(application)

    private val _rawCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())

    private val _callTypeFilter = MutableStateFlow(CallTypeFilter.ALL)
    val callTypeFilter: StateFlow<CallTypeFilter> = _callTypeFilter.asStateFlow()

    private val _simFilter = MutableStateFlow(SimFilter.ALL)
    val simFilter: StateFlow<SimFilter> = _simFilter.asStateFlow()

    val isFilterActive: StateFlow<Boolean> = combine(
        _callTypeFilter,
        _simFilter
    ) { type, sim ->
        type != CallTypeFilter.ALL || sim != SimFilter.ALL
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recentCalls: StateFlow<List<CallLogItem>> = combine(
        _rawCallLogs,
        _callTypeFilter,
        _simFilter
    ) { logs, typeFilter, simFilter ->
        val filtered = logs.filter { item ->
            val matchesType = when (typeFilter) {
                CallTypeFilter.ALL -> true
                CallTypeFilter.INCOMING -> item.type == CallType.INCOMING
                CallTypeFilter.OUTGOING -> item.type == CallType.OUTGOING
                CallTypeFilter.MISSED -> item.type == CallType.MISSED || item.type == CallType.REJECTED
            }
            val matchesSim = when (simFilter) {
                SimFilter.ALL -> true
                SimFilter.SIM_1 -> item.simNumber == 1
                SimFilter.SIM_2 -> item.simNumber == 2
            }
            matchesType && matchesSim
        }
        repository.groupConsecutiveCallLogs(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCallFilters(type: CallTypeFilter, sim: SimFilter) {
        _callTypeFilter.value = type
        _simFilter.value = sim
    }

    fun resetCallFilters() {
        _callTypeFilter.value = CallTypeFilter.ALL
        _simFilter.value = SimFilter.ALL
    }

    private val _contacts = MutableStateFlow<List<SearchDialerItem>>(emptyList())

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

    private val _dialerOpenMode = MutableStateFlow(
        DialerOpenMode.fromStorageKey(prefs.getString("dialer_open_mode", null))
    )
    val dialerOpenMode: StateFlow<DialerOpenMode> = _dialerOpenMode.asStateFlow()

    private val _isAppSettingsOpen = MutableStateFlow(false)
    val isAppSettingsOpen: StateFlow<Boolean> = _isAppSettingsOpen.asStateFlow()

    private val _showSwipeHint = MutableStateFlow(prefs.getBoolean("show_swipe_hint", true))
    val showSwipeHint: StateFlow<Boolean> = _showSwipeHint.asStateFlow()

    private val _contactDetailToShow = MutableStateFlow<ContactDetailState?>(null)
    val contactDetailToShow: StateFlow<ContactDetailState?> = _contactDetailToShow.asStateFlow()

    private val _unsavedNumberFlow = MutableStateFlow<UnsavedNumberFlowState?>(null)
    val unsavedNumberFlow: StateFlow<UnsavedNumberFlowState?> = _unsavedNumberFlow.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _hasLoadedCallLogs = MutableStateFlow(false)
    val hasLoadedCallLogs: StateFlow<Boolean> = _hasLoadedCallLogs.asStateFlow()

    val filteredDialerResults: StateFlow<SearchResults> = combine(
        _rawCallLogs,
        _contacts,
        _searchQuery
    ) { logs, contacts, query ->
        val query = SearchDialerItem.Matcher(query)
        if (query.isBlank()) {
            SearchResults(
                calls = logs.map { log ->
                    SearchDialerItem(
                        id = "log_${log.id}",
                        name = log.name ?: log.number,
                        number = log.number,
                        photoUri = log.photoUri,
                        timestamp = log.timestamp,
                        simSlot = log.simNumber,
                        callType = log.type,
                        isFavorite = contacts.any { it.number == log.number }
                    )
                }.distinctBy { it.number }
            )
        } else {
            val matchedContacts = contacts.filter {
                query.match(it)
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
            }.sortedBy { query.order(it) }

            val matchedLogs = logs.filter { query.match(it) }
                .map { log ->
                    SearchDialerItem(
                        id = "log_${log.id}",
                        name = log.name ?: log.number,
                        number = log.number,
                        photoUri = log.photoUri,
                        timestamp = log.timestamp,
                        simSlot = log.simNumber,
                        callType = log.type,
                        isFavorite = contacts.any { it.number == log.number }
                    )
                }
                .distinctBy { it.number }
                .sortedBy { query.order(it) }

            val calledNumbers = matchedLogs.map { it.number }

            SearchResults(
                calls = matchedLogs,
                contacts = matchedContacts.filter { !calledNumbers.contains(it.number) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    private var openContactDetailJob: Job? = null

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    private lateinit var callLogObserver: ContentObserver
    private lateinit var contactsObserver: ContentObserver
    private var callLogReloadJob: Job? = null
    private var contactsReloadJob: Job? = null
    private var loadCallLogsJob: Job? = null
    private var suppressCallLogObserverUntilElapsed = 0L
    private var suppressContactsObserverUntilElapsed = 0L

    /** Bumps to drop in-flight load results (prevents deleted rows from reappearing). */
    private var callLogLoadGeneration = 0

    /** CallLog _IDs removed in-app; filter them out of any subsequent loads. */
    private val hiddenCallLogIds = mutableSetOf<String>()

    /** last-7 phone digits → hide until elapsedRealtime (clear-contact grace). */
    private val hiddenPhoneSuffixesUntil = mutableMapOf<String, Long>()

    init {
        loadTabs()
        loadCachedData()
        startObservingCallLogs()
        startObservingContacts()
    }

    private fun loadCachedData() {
        try {
            val cachedFavs = favoritesRepository.getCachedFavorites()
            val cachedLogs = repository.getCachedCallLogs()

            if (cachedFavs.isNotEmpty() || cachedLogs.isNotEmpty()) {
                if (cachedFavs.isNotEmpty()) {
                    _favorites.value = cachedFavs
                }
                if (cachedLogs.isNotEmpty()) {
                    _rawCallLogs.value = applyTombstones(applyFavoriteNames(cachedLogs, cachedFavs))
                }
                _hasLoadedCallLogs.value = true
            }
        } catch (_: Exception) {
            // ignore
        }
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
            delay(150.milliseconds)
            loadCallLogs(showLoading = false)
            // Late system write after call end
            delay(800.milliseconds)
            loadCallLogs(showLoading = false)
        }
    }

    private fun invalidateInFlightCallLogLoads() {
        callLogLoadGeneration++
        loadCallLogsJob?.cancel()
        loadCallLogsJob = null
        callLogReloadJob?.cancel()
        callLogReloadJob = null
    }

    private fun phoneSuffix7(number: String): String? {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(7) else null
    }

    private fun rememberDeletedIds(ids: Collection<String>) {
        hiddenCallLogIds.addAll(ids.filter { it.isNotBlank() })
    }

    private fun rememberDeletedPhone(number: String, graceMs: Long = 45_000L) {
        phoneSuffix7(number)?.let { suffix ->
            hiddenPhoneSuffixesUntil[suffix] = SystemClock.elapsedRealtime() + graceMs
        }
    }

    private fun applyTombstones(logs: List<CallLogItem>): List<CallLogItem> {
        val now = SystemClock.elapsedRealtime()
        hiddenPhoneSuffixesUntil.entries.removeAll { (_, until) -> until <= now }

        val filtered = logs.filterNot { item ->
            if (item.id in hiddenCallLogIds) return@filterNot true
            if (item.allEntryIds().any { it in hiddenCallLogIds }) return@filterNot true
            val suffix = phoneSuffix7(item.number)
            suffix != null && (hiddenPhoneSuffixesUntil[suffix] ?: 0L) > now
        }

        // Drop id tombstones confirmed absent from Android snapshot
        val presentIds = logs.asSequence()
            .flatMap { sequenceOf(it.id) + it.allEntryIds().asSequence() }
            .filter { it.isNotBlank() }
            .toHashSet()
        hiddenCallLogIds.removeAll { it !in presentIds }

        return filtered
    }

    private fun scheduleContactsReload() {
        contactsReloadJob?.cancel()
        contactsReloadJob = viewModelScope.launch {
            delay(500.milliseconds)
            repository.resetCache()
            _contacts.value = withContext(Dispatchers.IO) {
                contactsRepository.getContacts()
            }
            val favorites = withContext(Dispatchers.IO) {
                favoritesRepository.getFavorites()
            }
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

    fun loadCallLogs(showLoading: Boolean = true) {
        if (loadCallLogsJob?.isActive == true) {
            if (showLoading) return
            loadCallLogsJob?.cancel()
        }
        val generation = ++callLogLoadGeneration
        loadCallLogsJob = viewModelScope.launch(Dispatchers.IO) {
            val shouldShowLoading = showLoading && !_hasLoadedCallLogs.value
            try {
                val contacts = contactsRepository.getContacts()
                val favorites = favoritesRepository.getFavorites()
                
                withContext(Dispatchers.Main) {
                    _contacts.value = contacts
                    _favorites.value = favorites
                }
                if (generation != callLogLoadGeneration) return@launch

                fun publish(logs: List<CallLogItem>) {
                    if (generation != callLogLoadGeneration) return
                    _rawCallLogs.value = applyTombstones(applyFavoriteNames(logs, favorites))
                    _favorites.value = favorites
                }

                if (shouldShowLoading || _rawCallLogs.value.isEmpty()) {
                    val initialLogs = repository.getCallLogs(CallLogRepository.DEFAULT_RECENTS_LIMIT)
                    withContext(Dispatchers.Main) {
                        publish(initialLogs)
                        _hasLoadedCallLogs.value = true
                    }

                    val fullLogs = repository.getCallLogs(limit = null)
                    withContext(Dispatchers.Main) {
                        publish(fullLogs)
                        suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 1_500L
                    }
                } else {
                    val initialLogs = repository.getCallLogs(CallLogRepository.DEFAULT_RECENTS_LIMIT)
                    withContext(Dispatchers.Main) {
                        publish(initialLogs)
                    }
                    val fullLogs = repository.getCallLogs(limit = null)
                    withContext(Dispatchers.Main) {
                        publish(fullLogs)
                    }
                }
                if (generation == callLogLoadGeneration) {
                    withContext(Dispatchers.Main) {
                        syncOpenContactDetail(favorites)
                    }
                }
            } finally {
                if (generation == callLogLoadGeneration) {
                    withContext(Dispatchers.Main) {
                        _hasLoadedCallLogs.value = true
                    }
                }
            }
        }
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

    private fun clearCallLogNames(phones: Collection<String>) {
        val keys = phones.mapNotNull { phoneMatchKey(it) }.toSet()
        if (keys.isEmpty()) return
        _rawCallLogs.value = _rawCallLogs.value.map { item ->
            val key = phoneMatchKey(item.number)
            if (key != null && key in keys) item.copy(name = null) else item
        }
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

    fun setDialerOpenMode(mode: DialerOpenMode) {
        _dialerOpenMode.value = mode
        prefs.edit { putString("dialer_open_mode", mode.storageKey) }
    }

    fun openAppSettings() {
        clearFavoriteSelection()
        _isAppSettingsOpen.value = true
    }

    fun closeAppSettings() {
        _isAppSettingsOpen.value = false
    }

    fun dismissSwipeHint() {
        _showSwipeHint.value = false
        prefs.edit { putBoolean("show_swipe_hint", false) }
    }

    fun addFavorite(contact: FavoriteContact) {
        viewModelScope.launch {
            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 1_500L
            val contactWithTab = contact.copy(tabId = _activeTabId.value)
            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.addFavorite(contactWithTab)
            }
            _contactDetailToShow.update { state ->
                state?.copy(
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

    fun deleteContact(contact: FavoriteContact) {
        viewModelScope.launch {
            if (ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.WRITE_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showToast("Нет разрешения на запись контактов")
                return@launch
            }

            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L

            val phoneNumbers = withContext(Dispatchers.IO) {
                contactsWriteRepository.resolvePhoneNumbers(contact)
            }

            val deleted = withContext(Dispatchers.IO) {
                contactsWriteRepository.deleteContact(contact)
            }

            if (!deleted) {
                showToast("Не удалось удалить контакт")
                return@launch
            }

            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.removeFavorite(contact)
            }
            clearCallLogNames(phoneNumbers)
            repository.resetCache()
            withContext(Dispatchers.IO) {
                repository.seedCachesFromContacts()
            }
            loadCallLogs(showLoading = false)
            closeContactDetail()
            showToast("Контакт удалён")
        }
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
        openContactDetailJob?.cancel()
        openContactDetailJob = viewModelScope.launch {
            delay(200.milliseconds)
            val isFav = withContext(Dispatchers.IO) {
                favoritesRepository.isFavorite(contact)
            }
            _contactDetailToShow.value = ContactDetailState(contact, initialTab, isFav)
        }
    }

    fun openContactDetailFromCallLog(item: CallLogItem, initialTab: Int = 1) {
        openContactDetail(contactFromCallLogItem(item), initialTab = initialTab)
    }

    fun openUnsavedNumberContactFlow(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        _unsavedNumberFlow.value = UnsavedNumberFlowState(
            phoneNumber = phoneNumber,
            step = UnsavedNumberFlowStep.Choose
        )
    }

    fun closeUnsavedNumberContactFlow() {
        _unsavedNumberFlow.value = null
    }

    fun unsavedNumberChooseCreateNew() {
        val state = _unsavedNumberFlow.value ?: return
        _unsavedNumberFlow.value = state.copy(step = UnsavedNumberFlowStep.CreateNew)
    }

    fun unsavedNumberChoosePickExisting() {
        val state = _unsavedNumberFlow.value ?: return
        _unsavedNumberFlow.value = state.copy(step = UnsavedNumberFlowStep.PickExisting)
    }

    fun unsavedNumberSelectExistingContact(contact: FavoriteContact) {
        val state = _unsavedNumberFlow.value ?: return
        _unsavedNumberFlow.value = state.copy(
            step = UnsavedNumberFlowStep.EditExisting(contact)
        )
    }

    fun unsavedNumberBackToChoose() {
        val state = _unsavedNumberFlow.value ?: return
        _unsavedNumberFlow.value = state.copy(step = UnsavedNumberFlowStep.Choose)
    }

    fun unsavedNumberBackToPickExisting() {
        val state = _unsavedNumberFlow.value ?: return
        _unsavedNumberFlow.value = state.copy(step = UnsavedNumberFlowStep.PickExisting)
    }

    fun saveExistingContactWithNumberFromCallLog(
        original: FavoriteContact,
        updated: FavoriteContact,
        phones: List<ContactsWriteRepository.PhoneEntry>,
        emails: List<ContactsWriteRepository.EmailEntry>,
        birthdayDateString: String?,
        photoBitmap: android.graphics.Bitmap?
    ) {
        viewModelScope.launch {
            if (ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.WRITE_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showToast("Нет разрешения на запись контактов")
                return@launch
            }

            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L

            val saved = withContext(Dispatchers.IO) {
                contactsWriteRepository.updateContactDetails(
                    contact = original,
                    displayName = updated.name,
                    phones = phones,
                    emails = emails,
                    birthdayDateString = birthdayDateString,
                    photoBitmap = photoBitmap
                )
            }

            if (!saved) {
                showToast("Не удалось сохранить контакт")
                return@launch
            }

            _favorites.value = withContext(Dispatchers.IO) {
                favoritesRepository.updateFavorite(updated)
            }
            val phoneNumbers = phones.map { it.number }.ifEmpty { listOf(updated.number) }
            patchCallLogNames(phoneNumbers, updated.name)
            repository.resetCache()
            withContext(Dispatchers.IO) {
                repository.seedCachesFromContacts()
            }
            loadCallLogs(showLoading = false)
            _unsavedNumberFlow.value = null
            showToast("Контакт сохранён")
        }
    }

    fun saveNewContactFromCallLog(
        phoneNumber: String,
        displayName: String,
        phones: List<ContactsWriteRepository.PhoneEntry>,
        emails: List<ContactsWriteRepository.EmailEntry>,
        birthdayDateString: String?,
        photoBitmap: android.graphics.Bitmap?
    ) {
        viewModelScope.launch {
            val trimmedName = displayName.trim()
            if (trimmedName.isEmpty()) {
                showToast("Введите имя контакта")
                return@launch
            }

            if (ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.WRITE_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showToast("Нет разрешения на запись контактов")
                return@launch
            }

            suppressContactsObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 2_500L

            val phoneEntries = phones.filter { it.number.isNotBlank() }.ifEmpty {
                listOf(ContactsWriteRepository.PhoneEntry(phoneNumber, "Мобильный"))
            }

            val contactId = withContext(Dispatchers.IO) {
                contactsWriteRepository.createContact(
                    displayName = trimmedName,
                    phones = phoneEntries,
                    emails = emails,
                    birthdayDateString = birthdayDateString,
                    photoBitmap = photoBitmap
                )
            }

            if (contactId == null) {
                showToast("Не удалось сохранить контакт")
                return@launch
            }

            repository.resetCache()
            withContext(Dispatchers.IO) {
                repository.seedCachesFromContacts()
            }
            patchCallLogNames(phoneEntries.map { it.number }, trimmedName)
            loadCallLogs(showLoading = false)
            _unsavedNumberFlow.value = null
            showToast("Контакт сохранён")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    private fun contactFromCallLogItem(item: CallLogItem): FavoriteContact {
        val cleanCallNum = item.number.filter { it.isDigit() || it == '+' }
        val existingFav = _favorites.value.find { fav ->
            val cleanFavNum = fav.number.filter { it.isDigit() || it == '+' }
            (cleanCallNum.isNotBlank() && cleanFavNum.isNotBlank() &&
                    cleanCallNum.takeLast(7) == cleanFavNum.takeLast(7)) ||
                    (!item.name.isNullOrBlank() && fav.name.trim()
                        .equals(item.name.trim(), ignoreCase = true))
        }

        return existingFav ?: FavoriteContact(
            id = cleanCallNum.ifBlank { "call_log_${item.id}" },
            name = item.name ?: item.number,
            number = item.number,
            photoUri = item.photoUri
        )
    }

    fun deleteCallLogGroup(item: CallLogItem) {
        viewModelScope.launch {
            invalidateInFlightCallLogLoads()
            val ids = item.allEntryIds().filter { it.isNotBlank() }
            rememberDeletedIds(ids)
            _rawCallLogs.value = applyTombstones(_rawCallLogs.value)

            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 800L
            withContext(Dispatchers.IO) {
                val deleted = repository.deleteCallLogEntries(ids)
                if (deleted < item.count) {
                    repository.deleteNewestCallLogsForNumber(
                        item.number,
                        item.count - deleted
                    )
                }
            }
            loadCallLogs(showLoading = false)
        }
    }

    fun clearContactCallLogs(item: CallLogItem) {
        viewModelScope.launch {
            invalidateInFlightCallLogLoads()

            val matching = _rawCallLogs.value.filter { isSameCallLogContact(it, item) }
            val idsFromUi =
                matching.flatMap { it.allEntryIds() }.filter { it.isNotBlank() }.distinct()
            rememberDeletedIds(idsFromUi)
            rememberDeletedPhone(item.number)
            _rawCallLogs.value = applyTombstones(_rawCallLogs.value)

            suppressCallLogObserverUntilElapsed = SystemClock.elapsedRealtime() + 800L
            withContext(Dispatchers.IO) {
                repository.deleteCallLogEntries(idsFromUi)
                repository.deleteCallLogsForNumber(item.number)
                // Second pass if provider still has rows (OEM race / partial delete)
                if (repository.countCallLogsForNumber(item.number) > 0) {
                    repository.deleteCallLogsForNumber(item.number)
                }
            }
            loadCallLogs(showLoading = false)
        }
    }

    /** Match by last 7–10 digits, or same display name when both named. */
    private fun isSameCallLogContact(a: CallLogItem, b: CallLogItem): Boolean {
        val da = a.number.filter { it.isDigit() }
        val db = b.number.filter { it.isDigit() }
        if (da.length >= 7 && db.length >= 7) {
            if (da.takeLast(7) == db.takeLast(7)) return true
            if (da.takeLast(10) == db.takeLast(10)) return true
        }
        val nameA = a.name?.trim().orEmpty()
        val nameB = b.name?.trim().orEmpty()
        return nameA.isNotEmpty() && nameA.equals(nameB, ignoreCase = true)
    }

    fun blockCallLogNumber(item: CallLogItem): Boolean {
        return repository.blockNumber(item.number)
    }

    fun closeContactDetail() {
        openContactDetailJob?.cancel()
        _contactDetailToShow.value = null
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
