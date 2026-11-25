package io.github.fuzzyfly0.navbtdisplay

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Start navigation with the compass arrow: the destination is resolved here (so the
 * app knows its coordinates), GPS starts, then Google Maps navigates to the same point.
 * Also receives "Share" from Google Maps (text with name, address and link).
 */
class DestinationActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var pendingQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            setText(R.string.dest_title)
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
        })
        root.addView(TextView(this).apply { setText(R.string.dest_hint) })
        input = EditText(this).apply {
            setHint(R.string.dest_input_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_GO) start()
                id == EditorInfo.IME_ACTION_GO
            }
        }
        root.addView(input)
        root.addView(Button(this).apply {
            setText(R.string.dest_go)
            setOnClickListener { start() }
        })
        root.addView(Button(this).apply {
            setText(R.string.dest_stop)
            setOnClickListener {
                TripState.end(this@DestinationActivity)
                status.setText(R.string.dest_stopped)
            }
        })
        status = TextView(this).apply { setPadding(0, pad, 0, 0) }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })

        sharedText(intent)?.let { input.setText(it) }
        TripState.destination?.let { status.text = getString(R.string.dest_active, it.label) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { input.setText(it) }
    }

    /** Text shared from Google Maps, without the links. */
    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("http") }
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    }

    private fun start() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            pendingQuery = q
            requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
            return
        }
        resolveAndGo(q)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        val q = pendingQuery ?: return
        pendingQuery = null
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            resolveAndGo(q)
        } else {
            status.setText(R.string.dest_no_permission)
            launchMaps(q, null)
        }
    }

    private fun resolveAndGo(q: String) {
        status.setText(R.string.dest_searching)
        Thread {
            val d = parseCoordinates(q) ?: geocode(q)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (d != null) {
                    TripState.start(this, d)
                    status.text = getString(R.string.dest_active, d.label)
                } else {
                    TripState.end(this)
                    status.setText(R.string.dest_not_found)
                }
                launchMaps(q, d)
            }
        }.start()
    }

    private fun parseCoordinates(q: String): TripState.Destination? {
        val m = Regex("^\\s*(-?\\d{1,2}\\.\\d+)\\s*[,; ]\\s*(-?\\d{1,3}\\.\\d+)\\s*$").find(q) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return TripState.Destination(lat, lon, q)
    }

    @Suppress("DEPRECATION")
    private fun geocode(q: String): TripState.Destination? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        val a = Geocoder(this, Locale.forLanguageTag("bg-BG")).getFromLocationName(q, 1)?.firstOrNull()
            ?: return@runCatching null
        TripState.Destination(a.latitude, a.longitude, a.getAddressLine(0) ?: q)
    }.getOrNull()

    private fun launchMaps(q: String, d: TripState.Destination?) {
        val target = if (d != null) "${d.lat},${d.lon}" else Uri.encode(q)
        val uri = Uri.parse("google.navigation:q=$target&mode=d")
        val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        try {
            startActivity(maps)
        } catch (e: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    private companion object {
        const val REQ_PERMISSIONS = 10
    }
}
