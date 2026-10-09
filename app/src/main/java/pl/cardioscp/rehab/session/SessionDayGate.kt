package pl.cardioscp.rehab.session

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Domyślnie jedna sesja dziennie (dni robocze w planie).
 * Kolejna sesja tego samego dnia wymaga PIN (domyślnie 9999) i jest oznaczana w historii.
 */
class SessionDayGate(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isWeekday(day: LocalDate = LocalDate.now()): Boolean =
        day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY

    fun defaultPin(): String = DEFAULT_PIN

    fun isExtraGranted(day: LocalDate = LocalDate.now()): Boolean =
        prefs.getString(KEY_EXTRA_GRANTED_DAY, null) == day.toString() &&
            !prefs.getBoolean(KEY_EXTRA_USED_PREFIX + day, false)

    fun grantExtraSession(day: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(KEY_EXTRA_GRANTED_DAY, day.toString())
            .putBoolean(KEY_EXTRA_USED_PREFIX + day, false)
            .apply()
    }

    fun markExtraSessionStarted(day: LocalDate = LocalDate.now()) {
        if (prefs.getString(KEY_EXTRA_GRANTED_DAY, null) == day.toString()) {
            prefs.edit().putBoolean(KEY_EXTRA_USED_PREFIX + day, true).apply()
        }
    }

    fun isCurrentStartExtra(day: LocalDate = LocalDate.now()): Boolean =
        prefs.getString(KEY_EXTRA_GRANTED_DAY, null) == day.toString() &&
            prefs.getBoolean(KEY_EXTRA_USED_PREFIX + day, false)

    /** True when a same-day retry needs PIN (slot already used, no unused grant). */
    fun requiresPinForNewSession(
        day: LocalDate = LocalDate.now(),
        slotAlreadyUsed: Boolean,
    ): Boolean = needsPin(slotAlreadyUsed, isExtraGranted(day))

    companion object {
        private const val PREFS = "rehab_session_day_gate"
        private const val KEY_EXTRA_GRANTED_DAY = "extra_granted_day"
        private const val KEY_EXTRA_USED_PREFIX = "extra_used_"
        const val DEFAULT_PIN = "9999"

        fun needsPin(slotAlreadyUsed: Boolean, hasUnusedExtraGrant: Boolean): Boolean =
            slotAlreadyUsed && !hasUnusedExtraGrant
    }
}
