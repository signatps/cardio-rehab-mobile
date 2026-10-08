package pl.cardioscp.rehab.clinic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedCatalogTest {
    private val fixture = """
        {
          "source": "RPL test",
          "source_url": "https://example.test",
          "portal": "test",
          "as_of": "2026-01-01",
          "count": 3,
          "items": [
            ["1", "Ramipril Teva", "Ramiprilum", "Tabletki", "5 mg"],
            ["2", "Amlozek", "Amlodipinum", "Tabletki", "5 mg"],
            ["3", "Metformin XR", "Metforminum", "Tabletki o przedłużonym uwalnianiu", "500 mg"]
          ]
        }
    """.trimIndent()

    @Test
    fun searchRanksNamePrefixAndFoldsAccents() {
        val idx = MedCatalog.loadJson(fixture)
        assertEquals(3, idx.meta.count)
        val hits = idx.search("rami")
        assertEquals(1, hits.size)
        assertEquals("Ramipril Teva", hits.single().name)
        assertTrue(hits.single().label.contains("5 mg"))
        assertEquals(1, idx.search("amlo").size)
        assertEquals("Amlozek", idx.search("amlo").single().name)
    }

    @Test
    fun shortQueryReturnsEmpty() {
        val idx = MedCatalog.loadJson(fixture)
        assertTrue(idx.search("r").isEmpty())
    }
}
