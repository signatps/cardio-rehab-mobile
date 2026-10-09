package pl.cardioscp.rehab.session

/**
 * Tryb EKG wybierany przed startem sesji:
 * - [OFFLINE] — każde EKG (kwalifikacyjne / spoczynek / szczyt) to komenda Offline + GetScp
 * - [ONLINE] — ciągły strumień Online przez sesję; fragmenty wg profilu treningu
 *   (bez komend Offline urządzenia)
 */
enum class EcgAcquisitionMode {
    OFFLINE,
    ONLINE,
    ;

    val labelPl: String
        get() = when (this) {
            OFFLINE -> "Offline (SCP na urządzeniu)"
            ONLINE -> "Online (ciągły podgląd)"
        }

    val hintPl: String
        get() = when (this) {
            OFFLINE -> "Każdy zapis EKG: Offline → plik SCP z urządzenia."
            ONLINE -> "EKG Online przez całą sesję — wycinane fragmenty (spoczynek / szczyt)."
        }
}
