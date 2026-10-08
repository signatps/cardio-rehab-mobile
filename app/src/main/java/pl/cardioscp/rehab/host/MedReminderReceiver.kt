package pl.cardioscp.rehab.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.cardioscp.rehab.clinic.ClinicDemoStore

class MedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_RESCHEDULE -> {
                MedReminderScheduler.reschedule(context)
            }
            ACTION_DOSE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Przypomnienie o leku"
                val body = intent.getStringExtra(EXTRA_BODY)
                    ?: ClinicDemoStore(context).dueMedicationReminderPayload()?.second
                    ?: "Czas na przyjęcie leku"
                MedReminderNotifier.notifyMedicationReminder(context, title, body)
                MedReminderScheduler.reschedule(context)
            }
        }
    }

    companion object {
        const val ACTION_DOSE = "pl.cardioscp.rehab.action.DOSE_REMINDER"
        const val ACTION_RESCHEDULE = "pl.cardioscp.rehab.action.RESCHEDULE_MEDS"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
