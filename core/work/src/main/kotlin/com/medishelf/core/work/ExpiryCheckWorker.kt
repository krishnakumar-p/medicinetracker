package com.medishelf.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.reminders.ReminderLedgerRepository
import com.medishelf.core.domain.reminders.ReminderPlanner
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.domain.usecase.ReconcileExpiredStatusesUseCase
import com.medishelf.core.notifications.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Daily: mark newly expired medicines, then post due reminders. Works while the app is locked
 * (the DB key isn't tied to the PIN) and never touches the network.
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconcile: ReconcileExpiredStatusesUseCase,
    private val medicines: MedicineRepository,
    private val settings: SettingsRepository,
    private val ledger: ReminderLedgerRepository,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        reconcile()
        val reminderSettings = settings.settings.first().reminders
        val plan = ReminderPlanner.plan(medicines.observeAll().first(), time.today(), reminderSettings, ledger.get())
        notifier.show(plan)
        // Only record reminders as sent if the user could actually see them; otherwise they
        // fire once notifications are allowed.
        if (plan.isEmpty || notifier.canNotify()) ledger.save(plan.ledger)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
