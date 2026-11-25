package io.github.fuzzyfly0.navbtdisplay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Looper

/**
 * GPS while a trip from [DestinationActivity] runs, as a foreground service so it
 * keeps working with Google Maps in front. Started only from the activity
 * (Android allows location foreground services only from the foreground).
 * Locations stay in memory ([TripState]); nothing is stored or sent anywhere.
 */
class TripLocationService : Service() {

    // All four methods: on Android 10 they are still abstract in the interface.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = TripState.onLocation(this@TripLocationService, location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav)
            .setContentTitle(getString(R.string.trip_running))
            .setContentText(TripState.destination?.label ?: "")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            DebugLog.log(this, "trip: no location permission")
            stopSelf()
            return START_NOT_STICKY
        }
        val lm = getSystemService(LocationManager::class.java)
        lm.removeUpdates(listener)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(listener) }
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 42

        var running = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripLocationService::class.java))
        }
    }
}
