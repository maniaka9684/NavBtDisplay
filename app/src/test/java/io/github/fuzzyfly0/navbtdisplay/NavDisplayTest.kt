package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDisplayTest {
    private fun g(dist: String, text: String, street: String? = null, summary: String? = "12 мин · 5,3 км") =
        Guidance(
            NavInfo(distance = dist, maneuver = text, street = street, summary = summary),
            ManeuverParser.parse(text),
            ManeuverParser.meters(dist),
        )

    @Test fun nearManeuver() {
        val d = NavDisplay.compose(g("200 м", "Завийте наляво", "ул. Шипка"), 500, "♪ Artist – Song")
        assertEquals("Наляво · 200 м", d.title)
        assertEquals("ул. Шипка", d.artist)
        assertEquals("♪ Artist – Song", d.album)
        assertEquals(ManeuverType.TURN, d.icon.type)
    }

    @Test fun farManeuverShowsStraight() {
        val d = NavDisplay.compose(g("8,4 км", "Завийте наляво", "ул. Шипка"), 500, null)
        assertEquals("Направо · 8,4 км", d.title)
        assertEquals("след това: наляво", d.artist)
        assertEquals("12 мин · 5,3 км", d.album)
        assertEquals(ManeuverType.STRAIGHT, d.icon.type)
    }

    @Test fun keyStableWhileCountingDown() {
        val a = NavDisplay.compose(g("400 м", "Завийте наляво", "ул. Шипка"), 500, null)
        val b = NavDisplay.compose(g("300 м", "Завийте наляво", "ул. Шипка"), 500, null)
        assertEquals(a.key, b.key)
        val far = NavDisplay.compose(g("900 м", "Завийте наляво", "ул. Шипка"), 500, null)
        assertTrue(far.key != a.key)
    }

    @Test fun thresholdHysteresis() {
        val t = NavDisplay.Threshold()
        assertEquals(500, t.meters(50.0))
        assertEquals(2000, t.meters(110.0))
        assertEquals(2000, t.meters(80.0))
        assertEquals(500, t.meters(60.0))
        assertEquals(500, t.meters(null))
    }

    @Test fun speedFromCountdown() {
        val s = SpeedEstimator()
        s.onDistance("k", 2000, 0)
        s.onDistance("k", 1500, 15_000) // 500 m in 15 s = 120 km/h
        assertEquals(120.0, s.kmh!!, 0.5)
    }

    @Test fun geo() {
        // Sofia -> Plovdiv is roughly south-east.
        val b = Geo.bearing(42.6977, 23.3219, 42.1354, 24.7453)
        assertTrue(b in 110.0..130.0)
        assertEquals(-90.0, Geo.relative(0.0, 90.0), 0.001)
        assertEquals(180, Geo.bucket(-179.0))
        assertEquals(30, Geo.bucket(40.0))
    }
}
