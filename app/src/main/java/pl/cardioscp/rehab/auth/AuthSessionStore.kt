package pl.cardioscp.rehab.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sesja logowania PIN (demo) — kto jest zalogowany w tej instalacji. */
class AuthSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _user = MutableStateFlow(load())
    val user: StateFlow<DemoUser?> = _user.asStateFlow()

    val isLoggedIn: Boolean get() = _user.value != null
    val role: AppRole? get() = _user.value?.role

    fun login(pin: String): DemoUser? {
        val matched = DemoUsers.byPin(pin) ?: return null
        prefs.edit().putString(KEY_PIN, matched.pin).apply()
        _user.value = matched
        return matched
    }

    fun logout() {
        prefs.edit().remove(KEY_PIN).apply()
        _user.value = null
    }

    private fun load(): DemoUser? {
        val pin = prefs.getString(KEY_PIN, null) ?: return null
        return DemoUsers.byPin(pin)
    }

    companion object {
        private const val PREFS = "auth_session"
        private const val KEY_PIN = "logged_pin"
    }
}
