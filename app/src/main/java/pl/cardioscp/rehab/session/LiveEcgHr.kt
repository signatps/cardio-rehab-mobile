package pl.cardioscp.rehab.session

import pl.cardioscp.rehab.ecg.HeartRateStatsEngine
import pl.cardioscp.rehab.ecg.Lead

/**
 * Tętno z przebiegu EKG (detekcja R / R-R w [HeartRateStatsEngine]) —
 * nie z komendy GetPulse urządzenia.
 */
object LiveEcgHr {
    fun fromSnapshot(snap: LiveEcgSnapshot, windowSec: Double = 8.0): Int? {
        if (!snap.hasTrace || snap.samplingHz <= 0) return null
        val leads = linkedMapOf<Lead, DoubleArray>()
        for ((label, samples) in snap.leads) {
            val lead = when (label) {
                "Vx", "V1" -> Lead.V1
                else -> runCatching { Lead.fromLabel(label) }.getOrNull()
            } ?: continue
            leads[lead] = samples
        }
        if (leads.isEmpty()) return null
        return HeartRateStatsEngine.liveFromLeads(leads, snap.samplingHz, windowSec)
    }
}
