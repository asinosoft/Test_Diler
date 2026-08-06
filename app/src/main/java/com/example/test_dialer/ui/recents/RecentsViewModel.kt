package com.example.test_dialer.ui.recents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.data.repository.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RecentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)

    private val _rawCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyMissed = MutableStateFlow(false)
    val showOnlyMissed: StateFlow<Boolean> = _showOnlyMissed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    init {
        loadCallLogs()
    }

    fun loadCallLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            _rawCallLogs.value = repository.getCallLogs()
            _isLoading.value = false
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setShowOnlyMissed(missedOnly: Boolean) {
        _showOnlyMissed.value = missedOnly
    }
}
