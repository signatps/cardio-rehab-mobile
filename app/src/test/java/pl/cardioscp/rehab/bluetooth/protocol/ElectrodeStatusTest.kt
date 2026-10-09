package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectrodeStatusTest {
    @Test
    fun getAnsEmptyMeansAllAttached() {
        val status = ElectrodeStatus.fromGetAnsElectrodes(byteArrayOf(0))
        assertTrue(status.allAttached)
        assertTrue(status.detachedSites.isEmpty())
    }

    @Test
    fun mapsRaLfV1FromDevError() {
        // DevError: code 0x01, count 3, RA(22), II→LF(2), V1(3)
        val payload = byteArrayOf(0x01, 3, 22, 2, 3)
        val status = ElectrodeStatus.fromDevErrorPayload(payload)!!
        assertFalse(status.allAttached)
        assertEquals(
            setOf(ElectrodeSite.RA, ElectrodeSite.LF, ElectrodeSite.V1),
            status.detachedSites.toSet(),
        )
        assertEquals(ElectrodeContact.ATTACHED, status.sites[ElectrodeSite.LA])
        assertEquals(ElectrodeContact.ATTACHED, status.sites[ElectrodeSite.RF])
    }

    @Test
    fun mapsLeadIToLa() {
        assertEquals(ElectrodeSite.LA, ElectrodeStatus.mapCodeToSite(1))
        assertEquals(ElectrodeSite.RF, ElectrodeStatus.mapCodeToSite(147))
    }
}
