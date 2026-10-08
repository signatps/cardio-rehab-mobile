package pl.cardioscp.rehab.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothDeviceFilterTest {
    @Before
    fun reset() {
        BluetoothDeviceFilter.nameContains = null
    }

    @Test
    fun anyName_acceptedByDefault() {
        assertTrue(BluetoothDeviceFilter.matches(null))
        assertTrue(BluetoothDeviceFilter.matches(""))
        assertTrue(BluetoothDeviceFilter.matches("EHO-Mini"))
        assertTrue(BluetoothDeviceFilter.matches("random-device"))
    }

    @Test
    fun optionalSubstring_narrowsMatch() {
        BluetoothDeviceFilter.nameContains = "eho"
        assertTrue(BluetoothDeviceFilter.matches("EHO-Mini-12"))
        assertFalse(BluetoothDeviceFilter.matches("PulseOx"))
    }
}
