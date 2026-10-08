package pl.cardioscp.rehab.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EhoMiniConnectionStateTest {
    @Test
    fun connected_holdsDeviceIdentity() {
        val state = EhoMiniConnectionState.Connected(
            deviceName = "PRO_PLUS_ECG_740579",
            address = "AA:BB:CC:DD:EE:FF",
        )
        assertEquals("PRO_PLUS_ECG_740579", state.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", state.address)
    }

    @Test
    fun error_carriesMessage() {
        val state = EhoMiniConnectionState.Error("timeout")
        assertTrue(state.message.contains("timeout"))
    }
}
