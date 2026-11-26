package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDisplayTest {
    private val song = Song("Песен", "Изпълнител", "Албум")

    private fun g(dist: String, m: Maneuver, street: String? = "ул. Шипка") =
        Guidance(NavInfo(distance = dist, street = street), m, ManeuverParser.meters(dist), "abc")

    private val left = Maneuver(ManeuverType.TURN, Side.LEFT)

    @Test fun nearManeuver() {
        val d = NavDisplay.compose(g("200 м", left), 500, song)
        assertEquals("⮙ 200 м", d.title)
        assertEquals("♪ Песен • Албум", d.artist)
        assertEquals("Изпълнител", d.album)
        assertEquals(left, d.icon)
    }

    @Test fun cruiseShowsOnlyArrow() {
        val d = NavDisplay.compose(g("8,4 км", left), 500, song)
        assertEquals("⮙", d.title)
        assertEquals(ManeuverType.STRAIGHT, d.icon.type)
    }

    @Test fun songLineWithoutAlbum() {
        assertEquals("♪ Песен", NavDisplay.songLine(Song("Песен", "Изпълнител")))
        assertEquals("♪ Албум", NavDisplay.songLine(Song(null, "Изпълнител", "Албум")))
        assertEquals("", NavDisplay.songLine(Song(" ", "Изпълнител", null)))
    }

    @Test fun noMusicNoLines() {
        val d = NavDisplay.compose(g("200 м", left), 500, null)
        assertEquals("", d.artist)
        assertEquals("", d.album)
    }

    @Test fun destinationCountsDown() {
        val d = NavDisplay.compose(g("150 м", Maneuver(ManeuverType.DESTINATION)), 500, song)
        assertEquals("⮙ 150 м", d.title)
        assertEquals(ManeuverType.DESTINATION, d.icon.type)
    }

    @Test fun zeroMetersAndStraightShowNoDistance() {
        assertEquals("⮙", NavDisplay.compose(g("0 м", left), 500, null).title)
        assertEquals("⮙", NavDisplay.compose(g("300 м", Maneuver(ManeuverType.STRAIGHT)), 500, null).title)
    }

    @Test fun keyStable() {
        val a = NavDisplay.compose(g("400 м", left), 500, null)
        val b = NavDisplay.compose(g("300 м", left), 500, song)
        val far = NavDisplay.compose(g("900 м", left), 500, null)
        assertEquals(a.key, b.key)
        assertEquals(a.key, far.key)
        val next = NavDisplay.compose(g("900 м", Maneuver(ManeuverType.TURN, Side.RIGHT)), 500, null)
        assertTrue(next.key != a.key)
    }

    @Test fun roundaboutExitBeforeDistance() {
        val m = ManeuverParser.parse("На кръговото кръстовище вземете втория изход към ул. Шипка")
        assertEquals("⮙ 2. изх. 300 м", NavDisplay.compose(g("300 м", m), 500, null).title)
        assertEquals("⮙", NavDisplay.compose(g("3 км", m), 500, null).title)
        // A motorway exit number is not a roundabout exit.
        assertEquals("⮙ 400 м", NavDisplay.compose(g("400 м", ManeuverParser.parse("Излезте на изход 12")), 500, null).title)
    }

    @Test fun keepNearDoesNotFlipBack() {
        val d = NavDisplay.compose(g("1,2 км", left), 500, null, keepNear = true)
        assertEquals("⮙ 1,2 км", d.title)
        assertEquals(left, d.icon)
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
        s.onDistance("k", 1500, 15_000)
        assertEquals(120.0, s.kmh!!, 0.5)
    }
}
