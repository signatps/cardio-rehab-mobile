package pl.cardioscp.rehab.scp

import pl.cardioscp.rehab.ecg.CalibrationInfo
import pl.cardioscp.rehab.ecg.Lead

/**
 * Bridges EHO-MINI SCP samples (AVM nV/LSB) into the CardioSCP-mobile analysis
 * lead map (values in millivolts).
 */
object ScpToEcgLeads {
    fun toMillivolts(recording: ScpEcgRecording): Map<Lead, DoubleArray> {
        val out = LinkedHashMap<Lead, DoubleArray>()
        for (lead in recording.leads) {
            val key = mapLead(lead.label) ?: continue
            out[key] = DoubleArray(lead.samples.size) { i ->
                EcgScale.sampleToMv(lead.samples[i].toInt(), recording.avm).toDouble()
            }
        }
        // Derive Einthoven/Goldberger when we have I+II (EHO-MINI often stores I, II, Vx).
        val i = out[Lead.I]
        val ii = out[Lead.II]
        if (i != null && ii != null && i.size == ii.size) {
            if (Lead.III !in out) {
                out[Lead.III] = DoubleArray(i.size) { idx -> ii[idx] - i[idx] }
            }
            if (Lead.AVR !in out) {
                out[Lead.AVR] = DoubleArray(i.size) { idx -> -(i[idx] + ii[idx]) / 2.0 }
            }
            if (Lead.AVL !in out) {
                out[Lead.AVL] = DoubleArray(i.size) { idx -> i[idx] - ii[idx] / 2.0 }
            }
            if (Lead.AVF !in out) {
                out[Lead.AVF] = DoubleArray(i.size) { idx -> ii[idx] - i[idx] / 2.0 }
            }
        }
        return out
    }

    fun calibration(recording: ScpEcgRecording): CalibrationInfo =
        CalibrationInfo.fromScpAvm(recording.avm)

    fun paperLeads(leadsMv: Map<Lead, DoubleArray>): List<Pair<String, DoubleArray>> {
        val order = listOf(
            Lead.I, Lead.II, Lead.III, Lead.AVR, Lead.AVL, Lead.AVF,
            Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6,
        )
        return order.mapNotNull { lead ->
            leadsMv[lead]?.let { lead.label to it }
        }
    }

    private fun mapLead(label: String): Lead? = when (label) {
        "I" -> Lead.I
        "II" -> Lead.II
        "III" -> Lead.III
        "aVR" -> Lead.AVR
        "aVL" -> Lead.AVL
        "aVF" -> Lead.AVF
        "V1", "Vx" -> Lead.V1
        "V2" -> Lead.V2
        "V3" -> Lead.V3
        "V4" -> Lead.V4
        "V5" -> Lead.V5
        "V6" -> Lead.V6
        else -> null
    }
}
