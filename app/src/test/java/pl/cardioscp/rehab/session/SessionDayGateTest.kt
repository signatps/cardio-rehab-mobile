package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDayGateTest {
    @Test
    fun needsPinLogic() {
        assertFalse(SessionDayGate.needsPin(slotAlreadyUsed = false, hasUnusedExtraGrant = false))
        assertFalse(SessionDayGate.needsPin(slotAlreadyUsed = false, hasUnusedExtraGrant = true))
        assertTrue(SessionDayGate.needsPin(slotAlreadyUsed = true, hasUnusedExtraGrant = false))
        assertFalse(SessionDayGate.needsPin(slotAlreadyUsed = true, hasUnusedExtraGrant = true))
    }

    @Test
    fun defaultPinIs9999() {
        assertEquals("9999", SessionDayGate.DEFAULT_PIN)
    }
}
