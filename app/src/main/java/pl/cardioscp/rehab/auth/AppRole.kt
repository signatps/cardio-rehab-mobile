package pl.cardioscp.rehab.auth

enum class AppRole {
    PATIENT,
    DOCTOR,
    ADMIN,
}

data class DemoUser(
    val pin: String,
    val displayName: String,
    val role: AppRole,
) {
    val roleLabelPl: String
        get() = when (role) {
            AppRole.PATIENT -> "Pacjent"
            AppRole.DOCTOR -> "Lekarz"
            AppRole.ADMIN -> "Administrator"
        }
}

object DemoUsers {
    val all: List<DemoUser> = listOf(
        DemoUser(pin = "1111", displayName = "Jan Lekarski", role = AppRole.DOCTOR),
        DemoUser(pin = "2222", displayName = "Adam Testowski", role = AppRole.PATIENT),
        DemoUser(pin = "9999", displayName = "Admin Adminowy", role = AppRole.ADMIN),
    )

    fun byPin(pin: String): DemoUser? =
        all.firstOrNull { it.pin == pin.trim() }
}
