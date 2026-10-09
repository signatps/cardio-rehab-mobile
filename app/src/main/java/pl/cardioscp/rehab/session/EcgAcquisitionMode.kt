package pl.cardioscp.rehab.session

/**
 * Tryb EKG z protokołu EHO-Mini / Silvermedia:
 * - [OFFLINE] — zapis SCP na urządzeniu (Init → EcgOffline → GetScp), bez strumienia próbek
 * - [ONLINE] — ciągły strumień EcgOnlineData (podgląd na żywo) równolegle z zapisem Offline/SCP
 */
enum class EcgAcquisitionMode {
    OFFLINE,
    ONLINE,
    ;

    val labelPl: String
        get() = when (this) {
            OFFLINE -> "Offline (SCP)"
            ONLINE -> "Online (podgląd na żywo)"
        }

    val hintPl: String
        get() = when (this) {
            OFFLINE -> "Zapis pliku SCP na urządzeniu — bez rysowania EKG w trakcie."
            ONLINE -> "Strumień EKG Online podczas zapisu — przebieg na ekranie."
        }
}
