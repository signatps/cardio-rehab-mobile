package pl.cardioscp.rehab.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDeviceFilterTest {
    @Test
    fun acceptsProPlusEcgWithSixDigitSerial() {
        assertTrue(BluetoothDeviceFilter.matches("PRO_PLUS_ECG_740579"))
        assertEquals("740579", BluetoothDeviceFilter.serialSuffix("PRO_PLUS_ECG_740579"))
    }

    @Test
    fun rejectsWrongPrefixOrSerialLength() {
        assertFalse(BluetoothDeviceFilter.matches(null))
        assertFalse(BluetoothDeviceFilter.matches(""))
        assertFalse(BluetoothDeviceFilter.matches("EHO-Mini"))
        assertFalse(BluetoothDeviceFilter.matches("PRO_PLUS_ECG_74057"))
        assertFalse(BluetoothDeviceFilter.matches("PRO_PLUS_ECG_7405799"))
        assertFalse(BluetoothDeviceFilter.matches("pro_plus_ecg_740579"))
        assertFalse(BluetoothDeviceFilter.matches("PRO_PLUS_ECG_74A579"))
        assertNull(BluetoothDeviceFilter.serialSuffix("random"))
    }
}
