package com.medishelf.core.datastore

import androidx.datastore.core.DataStore
import com.medishelf.core.domain.reminders.ReminderLedger
import com.medishelf.core.domain.reminders.ReminderLedgerRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class DataStoreReminderLedgerRepository @Inject constructor(
    private val store: DataStore<StoredReminderLedger>,
) : ReminderLedgerRepository {
    override suspend fun get(): ReminderLedger =
        ReminderLedger(store.data.first().sent.mapNotNull { (id, keys) -> id.toLongOrNull()?.let { it to keys.toSet() } }.toMap())

    override suspend fun save(ledger: ReminderLedger) {
        store.updateData { StoredReminderLedger(ledger.sent.map { (id, keys) -> id.toString() to keys.sorted() }.toMap()) }
    }
}
