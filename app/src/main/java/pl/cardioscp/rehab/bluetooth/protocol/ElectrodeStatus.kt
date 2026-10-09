package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Limb + V1 electrode contact derived from Silvermedia / EHO-MINI lead-off codes
 * (`DevError 0x01` and `GetAns` info `0x02` — see firmware `get_unconnected_codes`).
 */
enum class ElectrodeSite(val label: String) {
    RA("RA"),
    LA("LA"),
    LF("LF"),
    RF("RF"),
    V1("V1"),
}

enum class ElectrodeContact {
    UNKNOWN,
    ATTACHED,
    DETACHED,
}

data class ElectrodeStatus(
    val sites: Map<ElectrodeSite, ElectrodeContact> = ElectrodeSite.entries.associateWith {
        ElectrodeContact.UNKNOWN
    },
    val updatedAtMs: Long? = null,
    val rawCodes: List<Int> = emptyList(),
) {
    val known: Boolean get() = updatedAtMs != null

    val allAttached: Boolean
        get() = known && ElectrodeSite.entries.all { sites[it] == ElectrodeContact.ATTACHED }

    val detachedSites: List<ElectrodeSite>
        get() = ElectrodeSite.entries.filter { sites[it] == ElectrodeContact.DETACHED }

    val summaryPl: String
        get() = when {
            !known -> "Status elektrod nieznany — odpytaj urządzenie"
            detachedSites.isEmpty() -> "Elektrody OK (RA, LA, LF, RF, V1)"
            else -> "Odpięte: ${detachedSites.joinToString(", ") { it.label }}"
        }

    companion object {
        fun unknown(): ElectrodeStatus = ElectrodeStatus()

        /** DevError payload: `[0x01, count, code…]`. */
        fun fromDevErrorPayload(payload: ByteArray, nowMs: Long = System.currentTimeMillis()): ElectrodeStatus? {
            if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != 0x01) return null
            if (payload.size < 2) {
                return fromDetachedCodes(emptyList(), nowMs)
            }
            val count = payload[1].toInt() and 0xFF
            val codes = payload
                .drop(2)
                .take(count.coerceAtMost(payload.size - 2))
                .map { it.toInt() and 0xFF }
            return fromDetachedCodes(codes, nowMs)
        }

        /** GetAns value for info `0x02`: `[count, code…]`. */
        fun fromGetAnsElectrodes(value: ByteArray, nowMs: Long = System.currentTimeMillis()): ElectrodeStatus {
            if (value.isEmpty()) return fromDetachedCodes(emptyList(), nowMs)
            val count = value[0].toInt() and 0xFF
            val codes = value
                .drop(1)
                .take(count.coerceAtMost(value.size - 1))
                .map { it.toInt() and 0xFF }
            return fromDetachedCodes(codes, nowMs)
        }

        fun fromDetachedCodes(codes: List<Int>, nowMs: Long = System.currentTimeMillis()): ElectrodeStatus {
            val detached = codes.mapNotNull { mapCodeToSite(it) }.toSet()
            val sites = ElectrodeSite.entries.associateWith { site ->
                if (site in detached) ElectrodeContact.DETACHED else ElectrodeContact.ATTACHED
            }
            return ElectrodeStatus(
                sites = sites,
                updatedAtMs = nowMs,
                rawCodes = codes,
            )
        }

        /**
         * Map SCP / firmware lead-off codes to physical electrodes used in the UI.
         * Default Init programs I + II + V1 (`PayloadCodec.init`), so:
         * - `22` RA, `147` RL→RF, `3` V1
         * - lead `I` (1) → LA (RA already reported as 22 when bit0 fires)
         * - lead `II` (2) → LF (LL)
         * - `105` RM → LA fallback
         */
        fun mapCodeToSite(code: Int): ElectrodeSite? = when (code) {
            PayloadCodec.ScpLead.V1.toInt() and 0xFF -> ElectrodeSite.V1
            22 -> ElectrodeSite.RA // SCP_6_RA_CODE
            147 -> ElectrodeSite.RF // SCP_6_RL_CODE
            1 -> ElectrodeSite.LA // SCP_6_I_CODE
            2 -> ElectrodeSite.LF // SCP_6_II_CODE
            105 -> ElectrodeSite.LA // SCP_6_RM_CODE
            else -> null
        }
    }
}
