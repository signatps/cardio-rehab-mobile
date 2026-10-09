package pl.cardioscp.rehab.clinic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClinicDayRolloverTest {
    @Test
    fun doseNeedsActionIncludesPendingAndSnoozed() {
        val pending = MedDose(
            id = "1",
            drugName = "A",
            doseLabel = "1",
            time = LocalTime.of(8, 0),
            day = LocalDate.of(2026, 10, 9),
            status = DoseStatus.PENDING,
        )
        val snoozed = pending.copy(id = "2", status = DoseStatus.SNOOZED)
        val taken = pending.copy(id = "3", status = DoseStatus.TAKEN)
        assertTrue(pending.needsAction)
        assertTrue(snoozed.needsAction)
        assertTrue(!taken.needsAction)
    }

    @Test
    fun skipReasonsDictionaryHasEntries() {
        assertTrue(DoseSkipReasons.ALL.size >= 4)
        assertTrue(DoseSkipReasons.isKnown(DoseSkipReasons.ALL.first()))
        assertEquals(false, DoseSkipReasons.isKnown("xyz-nieznany"))
    }
}
