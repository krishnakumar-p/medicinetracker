package com.medishelf.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppLockSettings(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    /**
     * Re-lock after the app has been in the background this long. No "immediately" option:
     * the system photo picker and permission dialogs briefly background the app, and an
     * instant lock would interrupt them.
     */
    val autoLockAfterSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val MIN_AUTO_LOCK_SECONDS = 30
        val AUTO_LOCK_CHOICES = listOf(30, 60, 300, 900)
    }
}

data class ReminderSettings(
    val enabled: Boolean = true,
    /** Remind when a medicine is this many days (or fewer) from expiry. */
    val daysBefore: Set<Int> = DEFAULT_DAYS,
    val notifyExpired: Boolean = true,
    val notifyLowStock: Boolean = true,
    /** Local hour (0-23) the daily check runs. */
    val hourOfDay: Int = 9,
) {
    companion object {
        val DEFAULT_DAYS = setOf(30, 7, 1)
        val DAY_CHOICES = listOf(60, 30, 14, 7, 3, 1)
    }
}

data class UserSettings(
    /** Version of the medical disclaimer the user accepted; 0 = never. */
    val disclaimerAcceptedVersion: Int = 0,
    val appLock: AppLockSettings = AppLockSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminders: ReminderSettings = ReminderSettings(),
) {
    val hasAcceptedCurrentDisclaimer: Boolean get() = disclaimerAcceptedVersion >= CURRENT_DISCLAIMER_VERSION

    companion object {
        /** Bump when the disclaimer text changes materially so users see it again. */
        const val CURRENT_DISCLAIMER_VERSION = 1
    }
}
