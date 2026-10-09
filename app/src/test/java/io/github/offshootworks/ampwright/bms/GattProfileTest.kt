package io.github.offshootworks.ampwright.bms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattProfileTest {

    @Test
    fun ltwModuleIsFirstAndUnchanged() {
        val ltw = GattProfile.all.first()
        assertEquals(UUID.fromString("0000fe60-0000-1000-8000-00805f9b34fb"), ltw.service)
        assertEquals(UUID.fromString("0000fe61-0000-1000-8000-00805f9b34fb"), ltw.write)
        assertEquals(UUID.fromString("0000fe62-0000-1000-8000-00805f9b34fb"), ltw.notify)
        assertTrue(ltw.needsBond)
    }

    @Test
    fun onlyLtwModulePairs() {
        assertEquals(listOf("LTW"), GattProfile.all.filter { it.needsBond }.map { it.name })
    }

    @Test
    fun coversEveryModuleThePlayAppKnows() {
        assertEquals(9, GattProfile.all.size)
        assertEquals(
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
            GattProfile.all.single { it.name == "FFF0" }.write,
        )
    }
}
