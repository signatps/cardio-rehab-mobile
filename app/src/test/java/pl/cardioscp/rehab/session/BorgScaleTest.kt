package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorgScaleTest {
    @Test
    fun fullScale_hasFifteenLevels_from6to20() {
        assertEquals(15, BorgScale.levels.size)
        assertEquals(6, BorgScale.levels.first().score)
        assertEquals(20, BorgScale.levels.last().score)
        assertEquals((6..20).toList(), BorgScale.levels.map { it.score })
    }

    @Test
    fun isValid_onlyRpeRange() {
        assertTrue(BorgScale.isValid(6))
        assertTrue(BorgScale.isValid(13))
        assertTrue(BorgScale.isValid(20))
        assertFalse(BorgScale.isValid(5))
        assertFalse(BorgScale.isValid(21))
    }

    @Test
    fun labeledAnchors_matchClassicBorg() {
        assertEquals("Brak wysiłku", BorgScale.labelFor(6))
        assertEquals("Niezwykle lekki", BorgScale.labelFor(7))
        assertEquals("Bardzo lekki", BorgScale.labelFor(9))
        assertEquals("Lekki", BorgScale.labelFor(11))
        assertEquals("Dość ciężki", BorgScale.labelFor(13))
        assertEquals("Ciężki", BorgScale.labelFor(15))
        assertEquals("Bardzo ciężki", BorgScale.labelFor(17))
        assertEquals("Niezwykle ciężki", BorgScale.labelFor(19))
        assertEquals("Wysiłek maksymalny", BorgScale.labelFor(20))
        assertEquals("", BorgScale.labelFor(8))
        assertEquals("", BorgScale.labelFor(14))
    }

    @Test
    fun summaryPl_formatsSelection() {
        assertEquals("brak", BorgScale.summaryPl(null))
        assertEquals("13 — Dość ciężki", BorgScale.summaryPl(13))
        assertEquals("14", BorgScale.summaryPl(14))
    }
}
