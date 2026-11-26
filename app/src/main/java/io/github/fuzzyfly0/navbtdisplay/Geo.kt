package io.github.fuzzyfly0.navbtdisplay

/**
 * Speed from the counting-down distance of the notification (this build has no GPS).
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
