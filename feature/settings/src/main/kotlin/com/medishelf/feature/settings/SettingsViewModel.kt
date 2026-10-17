package com.medishelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.backup.BackupService
import com.medishelf.core.domain.backup.BackupSummary
import com.medishelf.core.domain.backup.DocumentStorage
import com.medishelf.core.domain.backup.ImportResult
import com.medishelf.core.domain.demo.DemoData
import com.medishelf.core.domain.export.CsvExporter
import com.medishelf.core.domain.export.CsvLabels
import com.medishelf.core.domain.reminders.ReminderTrigger
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.ReminderSettings
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsMessage {
    data class BackupSaved(val summary: BackupSummary) : SettingsMessage
    data class Restored(val summary: BackupSummary) : SettingsMessage
    data class CsvSaved(val rows: Int) : SettingsMessage
    data object WrongPassword : SettingsMessage
    data object NotABackup : SettingsMessage
    data object TooNew : SettingsMessage
    data object OperationFailed : SettingsMessage
    data object ReminderCheckQueued : SettingsMessage
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val members: List<FamilyMember> = emptyList(),
    val demoLoaded: Boolean = false,
    val busy: Boolean = false,
    val message: SettingsMessage? = null,
)

/** CSV export choices: one person (for their doctor) or everyone, current medicines or all. */
data class CsvRequest(val memberId: Long?, val includePast: Boolean, val labels: CsvLabels)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupService,
    private val documents: DocumentStorage,
    private val medicines: MedicineRepository,
    private val demoData: DemoData,
    private val reminderTrigger: ReminderTrigger,
    private val time: TimeProvider,
    members: FamilyMemberRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<SettingsMessage?>(null)

    /** Held only between "enter password" and "pick file"; wiped right after use. */
    private var pendingPassword: CharArray? = null
    private var pendingCsv: CsvRequest? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings, members.observeAll(), demoData.isLoaded, busy, message,
    ) { s, m, demo, b, msg -> SettingsUiState(s, m, demo, b, msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onRemindersChange(transform: (ReminderSettings) -> ReminderSettings) =
        viewModelScope.launch { settings.updateReminders(transform) }

    fun onThemeChange(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun onPasswordEntered(password: CharArray) {
        clearPassword()
        pendingPassword = password
    }

    fun onBackupDestinationChosen(uri: String?) = withPassword(uri) { target, password ->
        val summary = documents.write(target) { backup.export(it, password) }
        SettingsMessage.BackupSaved(summary)
    }

    fun onRestoreSourceConfirmed(uri: String?) = withPassword(uri) { source, password ->
        when (val result = documents.read(source) { backup.import(it, password) }) {
            is ImportResult.Success -> {
                reminderTrigger.runNow()
                SettingsMessage.Restored(result.summary)
            }
            ImportResult.WrongPassword -> SettingsMessage.WrongPassword
            ImportResult.NotABackup -> SettingsMessage.NotABackup
            ImportResult.TooNew -> SettingsMessage.TooNew
            is ImportResult.Failed -> SettingsMessage.OperationFailed
        }
    }

    fun onCsvRequested(request: CsvRequest) {
        pendingCsv = request
    }

    fun onCsvDestinationChosen(uri: String?) {
        val request = pendingCsv.also { pendingCsv = null } ?: return
        if (uri == null) return
        run {
            val today = time.today()
            val rows = medicines.observeAll().first()
                .filter { request.includePast || it.medicine.effectiveStatus(today).let { s -> s == MedicineStatus.ACTIVE || s == MedicineStatus.EXPIRED } }
                .filter { request.memberId == null || it.assignments.any { a -> a.member.id == request.memberId } }
                .sortedBy { it.medicine.displayName.lowercase() }
            val csv = CsvExporter.export(rows, today, request.labels, request.memberId)
            documents.write(uri) { it.write(csv.encodeToByteArray()) }
            SettingsMessage.CsvSaved(rows.size)
        }
    }

    fun onDemoDataChange(enabled: Boolean) = viewModelScope.launch {
        busy.value = true
        try {
            if (enabled) demoData.load() else demoData.clear()
        } finally {
            busy.value = false
        }
    }

    fun onRunReminderCheck() {
        reminderTrigger.runNow()
        message.value = SettingsMessage.ReminderCheckQueued
    }

    fun onMessageShown() {
        message.value = null
    }

    fun onDialogCancelled() {
        clearPassword()
        pendingCsv = null
    }

    private fun withPassword(uri: String?, action: suspend (String, CharArray) -> SettingsMessage) {
        val password = pendingPassword ?: return
        pendingPassword = null
        if (uri == null) {
            password.fill('\u0000')
            return
        }
        run {
            try {
                action(uri, password)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun run(block: suspend () -> SettingsMessage) {
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                block()
            } catch (e: Exception) {
                SettingsMessage.OperationFailed
            } finally {
                busy.value = false
            }
        }
    }

    private fun clearPassword() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    override fun onCleared() = clearPassword()
}
