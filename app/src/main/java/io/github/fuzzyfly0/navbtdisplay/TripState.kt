package io.github.fuzzyfly0.navbtdisplay

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Destination and the latest GPS fix, for the compass arrow and the speed-dependent
 * "keep straight" threshold. Only set when navigation was started from [DestinationActivity];
 * otherwise there is no compass and the speed comes from the counting-down distance.
 * Main thread only.
 */
object TripState {
    data class Destination(val lat: Double, val lon: Double, val label: String)

    /** GPS course is only reliable while moving. */
    private const val MIN_HEADING_SPEED_MS = 2.0f
    private const val HEADING_MAX_AGE_MS = 30_000L
    private const val SPEED_MAX_AGE_MS = 10_000L

    /** Rerouting can drop the guidance notification for a moment; stop GPS only after this. */
    private const val STOP_AFTER_GUIDANCE_END_MS = 120_000L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context

    var destination: Destination? = null
        private set
    private var location: Location? = null
    private var heading: Double? = null
    private var headingAt = 0L
    private var gpsKmh: Double? = null
    private var gpsAt = 0L

    private val stopRunnable = Runnable { end(appContext) }

    private fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    fun start(context: Context, d: Destination) {
        init(context)
        destination = d
        heading = null
        location = null
        handler.removeCallbacks(stopRunnable)
        TripLocationService.start(appContext)
        DebugLog.log(appContext, "trip started to ${d.label}")
    }

    fun end(context: Context) {
        init(context)
        handler.removeCallbacks(stopRunnable)
        if (destination == null && !TripLocationService.running) return
        destination = null
        location = null
        heading = null
        gpsKmh = null
        TripLocationService.stop(appContext)
        DebugLog.log(appContext, "trip ended")
    }

    fun onGuidance() {
        handler.removeCallbacks(stopRunnable)
    }

    fun onGuidanceEnded(context: Context) {
        init(context)
        if (destination == null) return
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, STOP_AFTER_GUIDANCE_END_MS)
    }

    fun onLocation(context: Context, loc: Location) {
        init(context)
        val now = SystemClock.elapsedRealtime()
        location = loc
        if (loc.hasSpeed()) {
            gpsKmh = loc.speed * 3.6
            gpsAt = now
        }
        if (loc.hasBearing() && loc.hasSpeed() && loc.speed >= MIN_HEADING_SPEED_MS) {
            heading = loc.bearing.toDouble()
            headingAt = now
        }
        NavPublisher.onLocation(appContext)
    }

    /** GPS speed in km/h, if fresh. */
    fun speedKmh(): Double? =
        gpsKmh?.takeIf { SystemClock.elapsedRealtime() - gpsAt < SPEED_MAX_AGE_MS }

    /** Direction to the destination relative to driving, rounded to 30°; null if unknown. */
    fun compassDeg(): Int? {
        val d = destination ?: return null
        val l = location ?: return null
        val h = heading ?: return null
        if (SystemClock.elapsedRealtime() - headingAt > HEADING_MAX_AGE_MS) return null
        return Geo.bucket(Geo.relative(Geo.bearing(l.latitude, l.longitude, d.lat, d.lon), h))
    }
}
