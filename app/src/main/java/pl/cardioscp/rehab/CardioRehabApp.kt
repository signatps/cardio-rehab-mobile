package pl.cardioscp.rehab

import android.app.Application
import pl.cardioscp.rehab.host.VoiceGreeting

class CardioRehabApp : Application() {
    lateinit var voiceGreeting: VoiceGreeting
        private set

    override fun onCreate() {
        super.onCreate()
        voiceGreeting = VoiceGreeting(this)
        voiceGreeting.warmUp()
    }

    override fun onTerminate() {
        voiceGreeting.shutdown()
        super.onTerminate()
    }
}
