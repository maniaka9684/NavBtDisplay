package io.github.fuzzyfly0.navbtdisplay

/** One guidance update, already split and classified. [iconId]: short id of the notification picture. */
data class Guidance(
    val info: NavInfo,
    val maneuver: Maneuver,
    val meters: Int?,
    val iconId: String? = null,
)

/** The song playing in the music app. */
data class Song(val title: String?, val artist: String?, val album: String? = null)

/** What goes to the car: three lines, a stable key per maneuver, and the maneuver to draw. */
data class DisplayText(
    val title: String,
    val artist: String,
    val album: String,
    val key: String,
    val icon: Maneuver,
)

/**
 * Car layout of this build (line 1 = title, line 2 = "artist", line 3 = "album"):
 *  - maneuver near:      icon = maneuver,      line 1 "⮙ 200 м" (roundabout: "⮙ 2. изх. 200 м")
 *  - driving straight:   icon = straight arrow, line 1 "⮙" (no distance)
 *  - line 2 "♪ <song title> • <album>", line 3 "<song artist>" (empty without music)
 */
object NavDisplay {
    const val ARROW = "⮙"
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

    /** Far from the next maneuver (or the maneuver is just "continue"): show the straight arrow. */
    fun isCruise(g: Guidance, thresholdM: Int): Boolean =
        g.maneuver.type == ManeuverType.STRAIGHT || (g.meters != null && g.meters > thresholdM)

    /**
     * Identity of the maneuver itself: the same while the distance counts down and when the
     * display switches between straight and the maneuver. Numbers are removed from texts.
     */
    fun identity(g: Guidance): String {
        val m = g.maneuver
        val text = NavText.maneuverKey(listOfNotNull(g.info.maneuver, g.info.street).joinToString(" "))
        return "${m.type}|${m.side}|${m.exit}|${m.roundaboutDirection}|${g.iconId}|$text"
    }

    /** "♪ Title • Album"; parts that are missing are left out. */
    fun songLine(song: Song?): String {
        val parts = listOfNotNull(song?.title, song?.album).map(String::trim).filter(String::isNotEmpty)
        return if (parts.isEmpty()) "" else "♪ " + parts.joinToString(" • ")
    }

    /** [keepNear]: this maneuver was already shown near; stay on it until the next one. */
    fun compose(g: Guidance, thresholdM: Int, song: Song?, keepNear: Boolean = false): DisplayText {
        val near = keepNear || !isCruise(g, thresholdM)
        val showDistance = near && g.maneuver.type != ManeuverType.STRAIGHT && (g.meters ?: 1) > 0
        val exit = g.maneuver.exit?.takeIf { near && g.maneuver.type == ManeuverType.ROUNDABOUT }?.let { "$it. изх." }
        val title = listOfNotNull(ARROW, exit, g.info.distance?.takeIf { showDistance }).joinToString(" ")
        return DisplayText(
            title = title,
            artist = songLine(song),
            album = song?.artist?.takeIf { it.isNotBlank() } ?: "",
            key = identity(g),
            icon = if (near) g.maneuver else Maneuver(ManeuverType.STRAIGHT),
        )
    }
}
