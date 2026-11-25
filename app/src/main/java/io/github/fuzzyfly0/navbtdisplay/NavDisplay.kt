package io.github.fuzzyfly0.navbtdisplay

/** One guidance update, already split and classified. */
data class Guidance(
    val info: NavInfo,
    val maneuver: Maneuver,
    val meters: Int?,
)

/** What goes to the car: three lines, a stable key per shown maneuver, and the maneuver to draw. */
data class DisplayText(
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val icon: Maneuver,
)

/**
 * Car layout agreed for this build:
 *  - maneuver near:  title "Наляво · 200 м", artist = street, album = ♪ song
 *  - maneuver far:   title "Направо · 8,4 км", artist "след това: наляво", album = ♪ song,
 *                    icon = straight arrow
 * Without music the album line shows the trip summary instead.
 */
object NavDisplay {
    const val CITY_THRESHOLD_M = 500
    const val FAST_THRESHOLD_M = 2_000

    /** Speed (km/h) above which the motorway threshold is used; hysteresis avoids flapping. */
    private const val FAST_ON_KMH = 90.0
    private const val FAST_OFF_KMH = 70.0

    class Threshold {
        var fast = false
            private set

        fun meters(kmh: Double?): Int {
            if (kmh != null) {
                if (!fast && kmh >= FAST_ON_KMH) fast = true
                else if (fast && kmh < FAST_OFF_KMH) fast = false
            }
            return if (fast) FAST_THRESHOLD_M else CITY_THRESHOLD_M
        }
    }

    fun isCruise(g: Guidance, thresholdM: Int): Boolean =
        g.meters != null && g.meters > thresholdM && g.maneuver.type != ManeuverType.STRAIGHT

    private fun lowerFirst(s: String) = s.replaceFirstChar { it.lowercase() }

    fun compose(g: Guidance, thresholdM: Int, song: String?): DisplayText {
        val label = ManeuverParser.label(g.maneuver) ?: g.info.maneuver
        val album = song ?: g.info.summary ?: ""
        val m = g.maneuver
        return if (isCruise(g, thresholdM)) {
            DisplayText(
                title = listOfNotNull("Направо", g.info.distance).joinToString(" · "),
                artist = label?.let { "след това: ${lowerFirst(it)}" } ?: "",
                album = album,
                key = "cruise|$label|${g.info.street}",
                icon = Maneuver(ManeuverType.STRAIGHT),
            )
        } else {
            DisplayText(
                title = listOfNotNull(label, g.info.distance).joinToString(" · "),
                artist = g.info.street ?: g.info.then ?: "",
                album = album,
                key = "m|${m.type}|${m.side}|${m.exit}|${m.roundaboutDirection}|$label|${g.info.street}",
                icon = m,
            )
        }
    }
}
