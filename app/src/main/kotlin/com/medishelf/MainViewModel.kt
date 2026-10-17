package com.medishelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.database.DatabaseHealth
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.domain.usecase.ReconcileExpiredStatusesUseCase
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.notifications.ReminderNotifier
import com.medishelf.feature.medicines.navigation.MedicineListPreset
import com.medishelf.core.model.UserSettings
import com.medishelf.core.security.AppLockManager
import com.medishelf.core.security.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the root shows, in priority order. Only READY reveals inventory data. */
enum class AppGate { LOADING, DATABASE_UNAVAILABLE, DISCLAIMER, LOCKED, READY }

data class MainUiState(
    val gate: AppGate = AppGate.LOADING,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Hide screen content from screenshots and the recents preview while the app lock is on. */
    val secureWindow: Boolean = false,
    /** Pending jump requested by a reminder notification. */
    val openList: MedicineListPreset? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    lockManager: AppLockManager,
    private val databaseHealth: DatabaseHealth,
    private val reconcileExpired: ReconcileExpiredStatusesUseCase,
) : ViewModel() {

    private val databaseUsable = MutableStateFlow<Boolean?>(null)
    private val openList = MutableStateFlow<MedicineListPreset?>(null)
    private var reconciled = false

    val uiState: StateFlow<MainUiState> = combine(databaseUsable, settings.settings, lockManager.lockState, openList) { dbOk, s, lock, open ->
        val gate = gateFor(dbOk, s, lock)
        if (gate == AppGate.READY) catchUpExpiries()
        MainUiState(gate = gate, themeMode = s.themeMode, secureWindow = s.appLock.enabled, openList = open)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        viewModelScope.launch { databaseUsable.value = databaseHealth.isUsable() }
    }

    fun onNotificationTarget(target: String?) {
        openList.value = when (target) {
            ReminderNotifier.Target.EXPIRING.name -> MedicineListPreset.EXPIRING
            ReminderNotifier.Target.EXPIRED.name -> MedicineListPreset.EXPIRED
            ReminderNotifier.Target.LOW_STOCK.name -> MedicineListPreset.LOW_STOCK
            else -> return
        }
    }

    fun onOpenListHandled() {
        openList.value = null
    }

    /** Irreversibly deletes the unreadable inventory and starts with an empty one. */
    fun onStartFresh() = viewModelScope.launch {
        databaseUsable.value = null
        databaseHealth.startFresh()
        databaseUsable.value = databaseHealth.isUsable()
    }

    // Catch up on expiries while the daily worker hasn't run yet (e.g. first launch of the day).
    private fun catchUpExpiries() {
        if (reconciled) return
        reconciled = true
        viewModelScope.launch { reconcileExpired() }
    }
}

internal fun gateFor(databaseUsable: Boolean?, settings: UserSettings, lock: LockState): AppGate = when {
    databaseUsable == null || lock == LockState.LOADING -> AppGate.LOADING
    !databaseUsable -> AppGate.DATABASE_UNAVAILABLE
    !settings.hasAcceptedCurrentDisclaimer -> AppGate.DISCLAIMER
    lock == LockState.LOCKED -> AppGate.LOCKED
    else -> AppGate.READY
}
