package pl.cardioscp.rehab.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.cardioscp.rehab.ui.shell.AppDestination

class DemoUsersTest {
    @Test
    fun pinsResolveExpectedRoles() {
        assertEquals(AppRole.DOCTOR, DemoUsers.byPin("1111")?.role)
        assertEquals("Jan Lekarski", DemoUsers.byPin("1111")?.displayName)
        assertEquals(AppRole.PATIENT, DemoUsers.byPin("2222")?.role)
        assertEquals("Adam Testowski", DemoUsers.byPin("2222")?.displayName)
        assertEquals(AppRole.ADMIN, DemoUsers.byPin("9999")?.role)
        assertEquals("Admin Adminowy", DemoUsers.byPin("9999")?.displayName)
        assertNull(DemoUsers.byPin("0000"))
    }

    @Test
    fun patientMenuExcludesEcgAndPatients() {
        val visible = AppDestination.visibleFor(AppRole.PATIENT)
        assertEquals(false, AppDestination.ECG in visible)
        assertEquals(false, AppDestination.PATIENTS in visible)
        assertEquals(false, AppDestination.DAY_PLAN in visible)
        assertEquals(true, AppDestination.DASHBOARD in visible)
        assertEquals(true, AppDestination.REHAB in visible)
        assertEquals(true, AppDestination.DEVICE in visible)
    }

    @Test
    fun doctorMenuIsPatientsSessionsResults() {
        val visible = AppDestination.visibleFor(AppRole.DOCTOR)
        assertEquals(
            listOf(
                AppDestination.PATIENTS,
                AppDestination.SESSIONS,
                AppDestination.MEASUREMENTS,
            ),
            visible,
        )
    }

    @Test
    fun adminKeepsFullAppWithoutPatientsHub() {
        val visible = AppDestination.visibleFor(AppRole.ADMIN)
        assertEquals(false, AppDestination.PATIENTS in visible)
        assertEquals(true, AppDestination.ECG in visible)
        assertEquals(true, AppDestination.DEVICE in visible)
        assertEquals(true, AppDestination.REHAB in visible)
    }
}
