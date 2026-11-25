package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Bulgarian phrases are ASSUMED wording of Google Maps, not recorded data.
 * Replace them with real samples from the debug log once available.
 */
class ManeuverTest {
    private fun p(t: String) = ManeuverParser.parse(t)

    @Test fun turns() {
        assertEquals(Maneuver(ManeuverType.TURN, Side.LEFT), p("Завийте наляво към ул. Шипка"))
        assertEquals(Maneuver(ManeuverType.TURN, Side.RIGHT), p("Завийте надясно"))
        assertEquals(Maneuver(ManeuverType.SLIGHT, Side.RIGHT), p("Завийте леко надясно"))
        assertEquals(Maneuver(ManeuverType.SHARP, Side.LEFT), p("Завийте рязко наляво"))
        assertEquals(Maneuver(ManeuverType.TURN, Side.RIGHT), p("Turn right onto Main St"))
        assertEquals(Maneuver(ManeuverType.SLIGHT, Side.LEFT), p("Slight left"))
    }

    @Test fun keepAndFork() {
        assertEquals(Maneuver(ManeuverType.KEEP, Side.LEFT), p("Дръжте се вляво"))
        assertEquals(Maneuver(ManeuverType.KEEP, Side.RIGHT), p("Придържайте се вдясно към А1"))
        assertEquals(Maneuver(ManeuverType.KEEP, Side.RIGHT), p("Keep right to stay on A1"))
    }

    @Test fun straightIsNotRight() {
        assertEquals(Maneuver(ManeuverType.STRAIGHT), p("Продължете направо"))
        assertEquals(Maneuver(ManeuverType.STRAIGHT), p("Продължете по бул. България"))
    }

    @Test fun roundabout() {
        val m = p("На кръговото кръстовище вземете втория изход към ул. Шипка")
        assertEquals(ManeuverType.ROUNDABOUT, m.type)
        assertEquals(2, m.exit)
        assertEquals(3, p("На кръговото кръстовище вземете 3-тия изход").exit)
        assertEquals(1, p("At the roundabout, take the 1st exit onto Elm St").exit)
        assertEquals(4, p("At the roundabout, take the fourth exit").exit)
        assertEquals(ManeuverType.ROUNDABOUT_EXIT, p("Излезте от кръговото кръстовище").type)
        assertEquals(ManeuverType.TURN, p("На кръговото кръстовище завийте надясно").roundaboutDirection)
    }

    @Test fun others() {
        assertEquals(ManeuverType.UTURN, p("Направете обратен завой").type)
        assertEquals(Maneuver(ManeuverType.OFF_RAMP, Side.RIGHT), p("Излезте на изход 12 вдясно"))
        assertEquals(ManeuverType.OFF_RAMP, p("Take exit 12 toward Plovdiv").type)
        assertEquals(ManeuverType.MERGE, p("Влейте се в А1").type)
        assertEquals(ManeuverType.FERRY, p("Качете се на ферибота").type)
        assertEquals(Maneuver(ManeuverType.DESTINATION, Side.LEFT), p("Дестинацията ще бъде отляво"))
        assertEquals(ManeuverType.DESTINATION, p("Пристигнахте").type)
        assertEquals(ManeuverType.UNKNOWN, p("бул. България").type)
    }

    @Test fun iconKeyFallback() {
        assertEquals(Maneuver(ManeuverType.TURN, Side.RIGHT), ManeuverParser.parse("Шипка", "right"))
        assertEquals(Maneuver(ManeuverType.SLIGHT, Side.LEFT), ManeuverParser.parse(null, "slight_left"))
    }

    @Test fun meters() {
        assertEquals(300, ManeuverParser.meters("300 м"))
        assertEquals(1200, ManeuverParser.meters("1,2 км"))
        assertEquals(8400, ManeuverParser.meters("8.4 km"))
        assertNull(ManeuverParser.meters("скоро"))
    }

    @Test fun labels() {
        assertEquals("Кръгово – изход 2", ManeuverParser.label(Maneuver(ManeuverType.ROUNDABOUT, exit = 2)))
        assertEquals("Дръжте дясно", ManeuverParser.label(Maneuver(ManeuverType.KEEP, Side.RIGHT)))
        assertNull(ManeuverParser.label(Maneuver(ManeuverType.UNKNOWN)))
    }
}
