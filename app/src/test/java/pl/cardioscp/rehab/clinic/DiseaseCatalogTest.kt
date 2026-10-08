package pl.cardioscp.rehab.clinic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseCatalogTest {
    @Test
    fun searchFindsIcd10ByCodeAndPolishName() {
        val json = """
            {"source":"t","source_url_icd9":"","source_url_icd10":"","portal":"","as_of":"2026-10-07","count":2,
             "items":[["ICD10","I10","Nadciśnienie samoistne (pierwotne)"],
                      ["ICD9","A01","Badanie ogólne moczu (profil)"]]}
        """.trimIndent()
        val idx = DiseaseCatalog.loadJson(json)
        val byCode = idx.search("I10", limit = 5)
        assertTrue(byCode.any { it.code == "I10" && it.system == DiseaseCatalog.System.ICD10 })
        val byName = idx.search("nadcisnienie", limit = 5)
        assertTrue(byName.any { it.code == "I10" })
        val only9 = idx.search("badanie", limit = 5, system = DiseaseCatalog.System.ICD9)
        assertEquals(1, only9.size)
        assertEquals("A01", only9.first().code)
    }

    @Test
    fun parseNfzIcd9XmlExtractsActiveRows() {
        val xml = """
            <?xml version="1.0"?><komunikat typ="ICD9" nr_gen="93" czas_pub="2026-09-24T08:35:45">
            <pozycja_slownika kod="A01" nazwa="Badanie ogólne moczu" status="A"></pozycja_slownika>
            <pozycja_slownika kod="ZZ" nazwa="Wycofane" status="N"></pozycja_slownika>
            </komunikat>
        """.trimIndent()
        val hits = DiseaseCatalog.parseNfzIcd9Xml(xml)
        assertEquals(1, hits.size)
        assertEquals("A01", hits.first().code)
    }
}
