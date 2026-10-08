package pl.cardioscp.rehab.host

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
import pl.cardioscp.rehab.MainActivity
import pl.cardioscp.rehab.R

/** Lokalne powiadomienia lekowe (jak LockScreenNotifier w mobile-DSD). */
object MedReminderNotifier {
    private const val CHANNEL_ID = "rehab_med_reminders"
    private const val NOTIFICATION_ID = 1043

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyMedicationReminder(context: Context, title: String, body: String): Boolean {
        val app = context.applicationContext
        ensureChannel(app)
        // Jawne sprawdzenie tuż przed notify — lint MissingPermission (API 33+).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return runCatching {
            nm.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun ensureChannel(app: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = app.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Przypomnienia o lekach",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Lokalne przypomnienia o przyjęciu dawki"
        }
        mgr.createNotificationChannel(channel)
    }
}
