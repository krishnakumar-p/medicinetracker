package com.medishelf.core.datastore

import kotlinx.serialization.Serializable

/** On-disk settings. Field names are the persisted schema: rename only with a migration. */
@Serializable
data class StoredSettings(
    val disclaimerAcceptedVersion: Int = 0,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockAfterSeconds: Int = 60,
    val themeMode: String = "SYSTEM",
    val remindersEnabled: Boolean = true,
    val reminderDays: List<Int> = listOf(30, 7, 1),
    val notifyExpired: Boolean = true,
    val notifyLowStock: Boolean = true,
    val reminderHour: Int = 9,
)

/** medicineId (as string) -> reminder keys already sent. */
@Serializable
data class StoredReminderLedger(val sent: Map<String, List<String>> = emptyMap())

/** IDs created by the debug demo-data loader, so it can remove exactly those. */
@Serializable
data class StoredDemoIds(
    val members: List<Long> = emptyList(),
    val locations: List<Long> = emptyList(),
    val medicines: List<Long> = emptyList(),
)

/** PIN verifier and throttling state. Holds no PIN, only a PBKDF2 hash. */
@Serializable
data class StoredLockState(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinIterations: Int = 0,
    val failedAttempts: Int = 0,
    val lockedOutUntilEpochMillis: Long = 0,
)
