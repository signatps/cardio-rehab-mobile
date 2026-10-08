package pl.cardioscp.rehab.host

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Powitanie głosowe (jak mobile-DSD).
 * Preferuje Google TTS + PL; SM-T835: Samsung TTS często milczy mimo SUCCESS.
 */
class VoiceGreeting(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var enginePackage: String? = null
    private var engineAttempt = 0
    @Volatile private var ready = false
    @Volatile private var initFailed = false
    private val pending = AtomicReference<String?>(null)
    private var lastPhrase: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private var heardStart = false

    @Volatile var lastStatus: String? = null
        private set

    fun warmUp() {
        if (initFailed && engineAttempt >= preferredEngines().size) return
        ensureEngine()
    }

    fun speak(text: String) {
        val phrase = text.trim()
        if (phrase.isBlank()) return
        lastPhrase = phrase
        pending.set(phrase)
        heardStart = false
        if (initFailed) {
            initFailed = false
            engineAttempt = 0
            runCatching { tts?.shutdown() }
            tts = null
            ready = false
        }
        ensureEngine()
        if (ready && tts != null) {
            main.postDelayed({ flushPending() }, 700)
        }
    }

    fun shutdown() {
        pending.set(null)
        ready = false
        main.removeCallbacksAndMessages(null)
        abandonFocus()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun listInstalledEngines(): List<String> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val flags = if (Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_ALL else 0
        return runCatching {
            app.packageManager.queryIntentServices(intent, flags)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun preferredEngines(): List<String?> {
        val installed = listInstalledEngines()
        val ordered = mutableListOf<String?>()
        installed.firstOrNull { it.contains("google", ignoreCase = true) }?.let { ordered += it }
        if (ordered.isEmpty()) ordered += "com.google.android.tts"
        ordered += null
        installed.forEach { pkg ->
            if (ordered.none { it == pkg }) ordered += pkg
        }
        return ordered
    }

    private fun ensureEngine() {
        if (ready && tts != null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { ensureEngine() }
            return
        }
        if (tts != null) return
        val engines = preferredEngines()
        if (engineAttempt >= engines.size) {
            initFailed = true
            lastStatus =
                "Brak działającego TTS. Zainstaluj Speech Services by Google i język polski."
            return
        }
        val pkg = engines[engineAttempt]
        enginePackage = pkg
        ready = false
        tts = if (pkg.isNullOrBlank()) {
            TextToSpeech(app) { status -> main.post { onEngineInit(status) } }
        } else {
            TextToSpeech(app, { status -> main.post { onEngineInit(status) } }, pkg)
        }
    }

    private fun onEngineInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            failEngineAndTryNext("init status=$status")
            return
        }
        when (applyLanguage(engine)) {
            LangResult.OK -> Unit
            LangResult.MISSING_DATA -> {
                runCatching {
                    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(install)
                }
                failEngineAndTryNext("brak danych języka")
                return
            }
            LangResult.UNSUPPORTED -> {
                failEngineAndTryNext("język nieobsługiwany")
                return
            }
        }
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                heardStart = true
                lastStatus = null
            }
            override fun onDone(utteranceId: String?) {
                abandonFocus()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                lastStatus = "Błąd odtwarzania TTS."
                abandonFocus()
                tryNextEngineAfterSilentFail()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                lastStatus = "Błąd TTS ($errorCode)."
                abandonFocus()
                tryNextEngineAfterSilentFail()
            }
        })
        ready = true
        initFailed = false
        main.postDelayed({ flushPending() }, 500)
    }

    private enum class LangResult { OK, MISSING_DATA, UNSUPPORTED }

    private fun applyLanguage(engine: TextToSpeech): LangResult {
        val candidates = listOf(
            Locale.forLanguageTag("pl-PL"),
            Locale.forLanguageTag("pl"),
            Locale.getDefault(),
            Locale.US,
        )
        var missing = false
        for (locale in candidates) {
            val avail = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(
                TextToSpeech.LANG_NOT_SUPPORTED,
            )
            when {
                avail == TextToSpeech.LANG_MISSING_DATA -> missing = true
                avail >= TextToSpeech.LANG_AVAILABLE -> {
                    val set = runCatching { engine.setLanguage(locale) }.getOrDefault(
                        TextToSpeech.LANG_NOT_SUPPORTED,
                    )
                    when {
                        set >= TextToSpeech.LANG_AVAILABLE -> return LangResult.OK
                        set == TextToSpeech.LANG_MISSING_DATA -> missing = true
                    }
                }
            }
        }
        return if (missing) LangResult.MISSING_DATA else LangResult.UNSUPPORTED
    }

    private fun failEngineAndTryNext(reason: String) {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        engineAttempt++
        lastStatus = "TTS ${enginePackage ?: "domyślny"}: $reason — kolejny silnik…"
        main.postDelayed({ ensureEngine() }, 250)
    }

    private fun tryNextEngineAfterSilentFail() {
        val toRetry = pending.get() ?: lastPhrase ?: return
        ready = false
        runCatching { tts?.shutdown() }
        tts = null
        engineAttempt++
        pending.set(toRetry)
        main.postDelayed({ ensureEngine() }, 300)
    }

    private fun ensureMediaVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (cur == 0) {
            val target = (max * 0.5f).toInt().coerceIn(1, max)
            runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { audio.abandonAudioFocus(null) }
        }
    }

    private fun flushPending() {
        val phrase = pending.get() ?: return
        val engine = tts
        if (!ready || engine == null) return
        pending.set(null)
        heardStart = false
        ensureMediaVolume()
        requestFocus()
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "rehab-welcome-${System.currentTimeMillis()}"
        val ok = runCatching {
            engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (ok == TextToSpeech.ERROR) {
            lastStatus = "speak() ERROR"
            pending.set(phrase)
            tryNextEngineAfterSilentFail()
            return
        }
        main.postDelayed({
            if (heardStart) return@postDelayed
            if (engineAttempt + 1 >= preferredEngines().size) {
                lastStatus = "TTS nie odtwarza dźwięku — sprawdź Google TTS i język polski."
                return@postDelayed
            }
            pending.set(phrase)
            ready = false
            runCatching { tts?.shutdown() }
            tts = null
            engineAttempt++
            ensureEngine()
        }, 2000)
    }
}
