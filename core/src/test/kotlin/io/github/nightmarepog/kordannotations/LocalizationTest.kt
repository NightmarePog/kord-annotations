package io.github.nightmarepog.kordannotations

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationTest {
    @Test
    fun `uses locale fallback and ICU pluralization`() {
        val translations = MapTranslationProvider(
            mapOf("en" to mapOf("cooldown" to "Wait {seconds, plural, one {# second} other {# seconds}}.")),
        )

        assertEquals("Wait 2 seconds.", translations.translate("en-US", "cooldown", mapOf("seconds" to 2)))
    }
}
