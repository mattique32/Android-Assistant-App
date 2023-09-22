package io.homeassistant.companion.android.settings.developer.location

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.database.location.LocationHistoryDao
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val locationHistoryDao: LocationHistoryDao,
    private val prefsRepository: PrefsRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LocationTrackingViewModel"

        private const val PAGE_SIZE = 25
    }

    val historyPagerFlow = Pager(
        PagingConfig(pageSize = PAGE_SIZE, maxSize = PAGE_SIZE * 10)
    ) {
        locationHistoryDao.getAll()
    }.flow

    var historyEnabled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            historyEnabled = prefsRepository.isLocationHistoryEnabled()
        }
    }

    fun enableHistory(enabled: Boolean) {
        if (enabled == historyEnabled) return
        historyEnabled = enabled
        viewModelScope.launch {
            prefsRepository.setLocationHistoryEnabled(enabled)
            if (!enabled) locationHistoryDao.deleteAll()
        }
    }
}
