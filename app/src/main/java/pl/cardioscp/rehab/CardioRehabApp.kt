package pl.cardioscp.rehab

import android.app.Application
import pl.cardioscp.rehab.clinic.ClinicDemoStore
import pl.cardioscp.rehab.host.MedReminderScheduler
import pl.cardioscp.rehab.host.VoiceGreeting

class CardioRehabApp : Application() {
    lateinit var voiceGreeting: VoiceGreeting
        private set

    override fun onCreate() {
        super.onCreate()
        voiceGreeting = VoiceGreeting(this)
        voiceGreeting.warmUp()
        MedReminderScheduler.reschedule(this, ClinicDemoStore(this))
    }

    override fun onTerminate() {
        voiceGreeting.shutdown()
        super.onTerminate()
    }
}
