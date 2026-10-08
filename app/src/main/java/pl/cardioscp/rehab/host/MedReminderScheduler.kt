package pl.cardioscp.rehab.host

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import pl.cardioscp.rehab.clinic.ClinicDemoStore

/** Planuje najbliższe przypomnienie lekowe (AlarmManager, jak Worker w DSD). */
object MedReminderScheduler {
    private const val REQ = 2043

    fun reschedule(context: Context, store: ClinicDemoStore = ClinicDemoStore(context)) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(app)
        alarm.cancel(pi)
        val next = store.nextPendingDoseAlarm() ?: return
        val (atMs, title, body) = next
        val intent = Intent(app, MedReminderReceiver::class.java).apply {
            action = MedReminderReceiver.ACTION_DOSE
            putExtra(MedReminderReceiver.EXTRA_TITLE, title)
            putExtra(MedReminderReceiver.EXTRA_BODY, body)
        }
        val triggerPi = PendingIntent.getBroadcast(
            app,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= 23) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, triggerPi)
            } else {
                @Suppress("DEPRECATION")
                alarm.setExact(AlarmManager.RTC_WAKEUP, atMs, triggerPi)
            }
        }.onFailure {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, triggerPi)
        }
    }

    private fun pendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, MedReminderReceiver::class.java).apply {
            action = MedReminderReceiver.ACTION_DOSE
        }
        return PendingIntent.getBroadcast(
            app,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
