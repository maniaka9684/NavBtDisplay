package io.github.fuzzyfly0.navbtdisplay

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure geometry for the compass arrow (no Android classes). */
object Geo {
    /** Initial great-circle bearing from point 1 to point 2, degrees 0..360 (0 = north). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Angle of the target relative to the driving direction, -180..180 (0 = straight ahead, + = right). */
    fun relative(targetBearing: Double, heading: Double): Double {
        var d = (targetBearing - heading) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** Rounds to [step] degrees so the arrow (and the album art) only changes on real turns. */
    fun bucket(relative: Double, step: Int = 30): Int {
        val b = (relative / step).roundToInt() * step
        return if (b == -180) 180 else b
    }
}

/**
 * Speed from the counting-down distance of the notification, for when no GPS
 * speed is available (navigation started directly in Google Maps).
 * Distances are rounded by the app, so this is only good for "city or motorway".
 */
class SpeedEstimator {
    private var key: String? = null
    private var startMeters = 0
    private var startMs = 0L
    var kmh: Double? = null
        private set

    fun onDistance(maneuverKey: String, meters: Int?, nowMs: Long) {
        if (meters == null) return
        if (maneuverKey != key || meters > startMeters) {
            key = maneuverKey
            startMeters = meters
            startMs = nowMs
            return
        }
        val dm = startMeters - meters
        val dt = nowMs - startMs
        if (dm >= 200 && dt >= 4_000) {
            val v = dm / (dt / 1000.0) * 3.6
            kmh = kmh?.let { it * 0.5 + v * 0.5 } ?: v
            startMeters = meters
            startMs = nowMs
        } else if (dt > 120_000) {
            // Standing or crawling for two minutes.
            kmh = 0.0
            startMs = nowMs
            startMeters = meters
        }
    }
}
