package io.github.fuzzyfly0.navbtdisplay

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Receives the notifications of the supported apps and feeds the guidance
 * (or warning) text to [NavPublisher].
 *
 * The system binds this service once the user grants notification access.
 */
class NavListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        DebugLog.log(this, "listener connected")
        runCatching { activeNotifications }.getOrNull()?.forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        DebugLog.log(this, "listener disconnected")
        LiveHint.clear()
        NavPublisher.stop(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = NavApps.forPackage(sbn.packageName) ?: return
        val enabled = Prefs(this).isAppEnabled(app)
        if (app.kind == NavApp.Kind.WARNING) {
            if (!enabled) return
            DebugLog.log(this, "posted " + NotificationDump.describe(this, sbn))
            val car = NavText.composeWarning(app.label, NotificationDump.fields(this, sbn, app.label)) ?: return
            DebugLog.log(this, "warning title='${car.title}' artist='${car.artist}' key='${car.maneuverKey}'")
            LiveHint.onWarning(app.label, car)
            NavPublisher.onWarning(this, sbn.key, car)
            return
        }
        if (!enabled) {
            LiveHint.onGuidanceEnded(sbn.key)
            NavPublisher.onGuidanceEnded(this, sbn.key)
            return
        }
        DebugLog.log(this, "posted " + NotificationDump.describe(this, sbn))
        if (!NotificationDump.isGuidanceCandidate(sbn)) return
        val raw = NotificationDump.fields(this, sbn, app.label)
        val car = NavText.compose(app.label, raw) ?: return
        DebugLog.log(this, "parsed title='${car.title}' artist='${car.artist}' album='${car.album}' key='${car.maneuverKey}'")
        if (Prefs(this).artMode == ArtMode.NOTIFICATION) {
            NavPublisher.onArt(this, ArtMode.NOTIFICATION, runCatching { ArtImages.fromNotification(this, sbn) }.getOrNull())
        }
        LiveHint.onGuidance(sbn.key, app.label, car)
        val info = NavText.parse(raw, app.label)
        if (info == null) {
            NavPublisher.onGuidance(this, sbn.key, car)
            return
        }
        // Google Maps in Bulgarian has no maneuver words, only the picture: recognise the picture
        // first, then the text (which may still add a roundabout exit number).
        val fp = runCatching {
            ArtImages.largeIconPixels(this, sbn, FP_SAMPLE)?.let { IconFingerprint.fromArgb(it, FP_SAMPLE, FP_SAMPLE) }
        }.getOrNull()
        val byIcon = fp?.let { KnownIcons.match(it) }
        val iconKey = NotificationDump.iconNames(this, sbn).firstNotNullOfOrNull(NavText::directionKey)
        val byText = ManeuverParser.parse(listOfNotNull(info.maneuver, info.street).joinToString(" "), iconKey)
        val maneuver = byIcon?.copy(exit = byText.exit) ?: byText
        val iconId = fp?.let(IconFingerprint::shortId)
        if (fp != null && byIcon == null && loggedIcons.none { IconFingerprint.distance(it, fp) <= IconFingerprint.MAX_DISTANCE }) {
            loggedIcons += fp
            DebugLog.log(this, "NEW ICON id=$iconId fp=$fp\n" + IconFingerprint.ascii(fp))
        }
        val g = Guidance(info, maneuver, ManeuverParser.meters(info.distance), iconId)
        DebugLog.log(this, "classified $maneuver icon=$iconId meters=${g.meters}")
        // Google Maps' own picture, unchanged, on our dark background (#1b212b).
        val fallback = if (Prefs(this).artMode == ArtMode.ICONS) {
            runCatching { ArtImages.fromNotificationOn(this, sbn, IconSpec.BG) }.getOrNull()
        } else {
            null
        }
        NavPublisher.onClassifiedGuidance(this, sbn.key, g, fallback)
    }

    /** Pictures already written to the log as "NEW ICON" in this run. */
    private val loggedIcons = mutableListOf<String>()

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val app = NavApps.forPackage(sbn.packageName) ?: return
        DebugLog.log(this, "removed pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag}")
        // A warning stays on the display for its full time, even if the heads-up is gone.
        if (app.kind == NavApp.Kind.NAVIGATION) {
            LiveHint.onGuidanceEnded(sbn.key)
            NavPublisher.onGuidanceEnded(this, sbn.key)
        }
    }

    private companion object {
        /** Size the notification picture is drawn at before fingerprinting. */
        const val FP_SAMPLE = 96
    }
}
