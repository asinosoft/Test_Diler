package com.example.test_dialer.ui.recents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.data.repository.CallLogRepository
import com.example.test_dialer.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RecentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)
    private val favoritesRepository = FavoritesRepository(application)

    private val _rawCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())

    private val _favorites = MutableStateFlow<List<FavoriteContact>>(emptyList())
    val favorites: StateFlow<List<FavoriteContact>> = _favorites.asStateFlow()

    private val _selectedFavorite = MutableStateFlow<FavoriteContact?>(null)
    val selectedFavorite: StateFlow<FavoriteContact?> = _selectedFavorite.asStateFlow()

    private val _isTopBarVisible = MutableStateFlow(false)
    val isTopBarVisible: StateFlow<Boolean> = _isTopBarVisible.asStateFlow()

    private val _isAddFavoriteOpen = MutableStateFlow(false)
    val isAddFavoriteOpen: StateFlow<Boolean> = _isAddFavoriteOpen.asStateFlow()

    private val _contactDetailToShow = MutableStateFlow<FavoriteContact?>(null)
    val contactDetailToShow: StateFlow<FavoriteContact?> = _contactDetailToShow.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyMissed = MutableStateFlow(false)
    val showOnlyMissed: StateFlow<Boolean> = _showOnlyMissed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadFavorites()
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

    fun selectFavorite(contact: FavoriteContact) {
        if (_selectedFavorite.value?.id == contact.id) {
            clearFavoriteSelection()
        } else {
            _selectedFavorite.value = contact
            _isTopBarVisible.value = true
        }
    }

    fun clearFavoriteSelection() {
        _selectedFavorite.value = null
        _isTopBarVisible.value = false
    }

    fun addFavorite(contact: FavoriteContact) {
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

    fun openAddFavoriteDialog() {
        _isAddFavoriteOpen.value = true
    }

    fun closeAddFavoriteDialog() {
        _isAddFavoriteOpen.value = false
    }

    fun openContactDetail(contact: FavoriteContact) {
        _contactDetailToShow.value = contact
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
