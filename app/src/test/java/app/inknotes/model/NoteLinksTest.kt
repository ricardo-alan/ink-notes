package app.inknotes.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinksTest {

    @Test
    fun findsUrlsWithoutTrailingPunctuation() {
        val text = "Mira https://example.com/pan, y también www.ink.app."
        val links = NoteLinks.find(text)
        assertEquals(listOf("https://example.com/pan", "https://www.ink.app"), links.map { it.uri })
        assertEquals("https://example.com/pan", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun findsEmailAndPhone() {
        val links = NoteLinks.find("Escribe a ana@correo.mx o llama al +52 55 1234 5678")
        assertEquals(listOf("mailto:ana@correo.mx", "tel:+525512345678"), links.map { it.uri })
    }

    @Test
    fun ignoresDatesTimesAndShortNumbers() {
        assertTrue(NoteLinks.find("Cita 18/10/2026 a las 10:30, costo 1500").isEmpty())
    }

    @Test
    fun emailInsideUrlIsNotDuplicated() {
        val links = NoteLinks.find("https://site.com/user@host.com")
        assertEquals(1, links.size)
    }
}
