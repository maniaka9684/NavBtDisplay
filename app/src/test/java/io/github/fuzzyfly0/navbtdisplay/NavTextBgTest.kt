package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertEquals
import org.junit.Test

/** ASSUMED Bulgarian Google Maps layouts; replace with real samples from the debug log. */
class NavTextBgTest {
    @Test
    fun `distance title and bulgarian maneuver text`() {
        val info = NavText.parse(
            RawNavFields(title = "300 м", text = "Завийте надясно към ул. Шипка", subText = "12 мин · 5,3 км · Пристигане 18:34"),
            "Google Maps",
        )!!
        assertEquals("300 м", info.distance)
        assertEquals("Завийте надясно", info.maneuver)
        assertEquals("ул. Шипка", info.street)
        assertEquals("12 мин · 5,3 км · Пристигане 18:34", info.summary)
    }

    @Test
    fun `distance prefix with sled`() {
        val info = NavText.parse(RawNavFields(title = "След 1,2 км завийте наляво"))!!
        assertEquals("1,2 км", info.distance)
        assertEquals("завийте наляво", info.maneuver)
    }

    @Test
    fun `then line in big text`() {
        val info = NavText.parse(
            RawNavFields(title = "200 м", text = "Завийте наляво", bigText = "Завийте наляво\nСлед това завийте надясно"),
        )!!
        assertEquals("След това завийте надясно", info.then)
    }

    @Test
    fun `key ignores the counting distance`() {
        assertEquals(NavText.maneuverKey("300 м завийте"), NavText.maneuverKey("200 м завийте"))
    }
}
