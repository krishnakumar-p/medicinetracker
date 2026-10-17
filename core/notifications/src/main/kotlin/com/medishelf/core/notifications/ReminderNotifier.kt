package com.medishelf.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medishelf.core.domain.reminders.ReminderPlan
import com.medishelf.core.model.MedicineDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts at most three summary notifications (expiring, expired, low stock) per check.
 * Medicine names appear only when the phone is unlocked: the lock-screen version is generic.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Target(val notificationId: Int, val channelId: String) {
        EXPIRING(1001, CHANNEL_EXPIRY),
        EXPIRED(1002, CHANNEL_EXPIRY),
        LOW_STOCK(1003, CHANNEL_STOCK),
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_EXPIRY, context.getString(R.string.core_notifications_channel_expiry), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.core_notifications_channel_expiry_desc) },
                NotificationChannel(CHANNEL_STOCK, context.getString(R.string.core_notifications_channel_stock), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.core_notifications_channel_stock_desc) },
            ),
        )
    }

    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Returns the number of notifications posted. */
    fun show(plan: ReminderPlan): Int {
        if (plan.isEmpty || !canNotify()) return 0
        val res = context.resources
        var posted = 0
        if (plan.expiring.isNotEmpty()) {
            val lines = plan.expiring.map { (details, days) ->
                val when_ = if (days == 0L) res.getString(R.string.core_notifications_today) else res.getQuantityString(R.plurals.core_notifications_in_days, days.toInt(), days.toInt())
                res.getString(R.string.core_notifications_line, details.name(), when_)
            }
            post(Target.EXPIRING, res.getQuantityString(R.plurals.core_notifications_expiring_title, lines.size, lines.size), lines)
            posted++
        }
        if (plan.expired.isNotEmpty()) {
            post(Target.EXPIRED, res.getQuantityString(R.plurals.core_notifications_expired_title, plan.expired.size, plan.expired.size), plan.expired.map { it.name() })
            posted++
        }
        if (plan.lowStock.isNotEmpty()) {
            post(Target.LOW_STOCK, res.getQuantityString(R.plurals.core_notifications_low_title, plan.lowStock.size, plan.lowStock.size), plan.lowStock.map { it.name() })
            posted++
        }
        return posted
    }

    @Suppress("MissingPermission") // Checked in canNotify().
    private fun post(target: Target, title: String, lines: List<String>) {
        val body = lines.joinToString("\n")
        val publicVersion = NotificationCompat.Builder(context, target.channelId)
            .setSmallIcon(R.drawable.ic_stat_medishelf)
            .setContentTitle(context.getString(R.string.core_notifications_public_title))
            .setContentText(context.getString(R.string.core_notifications_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, target.channelId)
            .setSmallIcon(R.drawable.ic_stat_medishelf)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(target))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(target.notificationId, notification)
    }

    /** Opens the app (through the lock screen if enabled) on the matching filtered list. */
    private fun openAppIntent(target: Target): PendingIntent? {
        // A reminder without a tap action is still better than no reminder.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val intent = launch
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_LIST, target.name)
        return PendingIntent.getActivity(
            context,
            target.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun MedicineDetails.name(): String =
        listOf(medicine.displayName, medicine.strength).filter(String::isNotBlank).joinToString(" ")

    companion object {
        const val EXTRA_OPEN_LIST = "com.medishelf.extra.OPEN_LIST"
        private const val CHANNEL_EXPIRY = "expiry"
        private const val CHANNEL_STOCK = "stock"
    }
}
