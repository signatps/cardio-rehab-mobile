package pl.cardioscp.rehab.bluetooth

import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPermissionHelperTest {
    @Test
    fun requiredPermissions_isNotEmpty() {
        assertTrue(BluetoothPermissionHelper.requiredPermissions().isNotEmpty())
    }
}
