package io.github.fuzzyfly0.navbtdisplay

/**
 * Recognises the maneuver picture of a navigation notification.
 *
 * Google Maps in Bulgarian puts no maneuver words into its notification (only the distance
 * and the street); the maneuver exists only as the large icon. The icon is reduced to a
 * 32×32 black/white fingerprint (cropped to the drawn shape, so its size does not matter)
 * and compared with [KnownIcons]. Pure Kotlin, unit-tested on the JVM.
 */
object IconFingerprint {
    const val N = 32
    const val HEX_LENGTH = N * N / 4

    /** Two pictures with at most this many different bits count as the same icon (≈6 %). */
    const val MAX_DISTANCE = 60

    private fun lum(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Fingerprint of an ARGB picture ([w]×[h]); null if nothing is drawn on it. */
    fun fromArgb(px: IntArray, w: Int, h: Int): String? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        val transparent = px.count { (it ushr 24) < 128 }
        val ink = BooleanArray(w * h)
        if (transparent > w * h / 10) {
            // Shape on a transparent background (usual for notification icons).
            for (i in 0 until w * h) ink[i] = (px[i] ushr 24) >= 128
        } else {
            // Opaque picture: whatever differs clearly from the border colour.
            var sum = 0L
            var n = 0
            for (x in 0 until w) {
                sum += lum(px[x]) + lum(px[(h - 1) * w + x]); n += 2
            }
            for (y in 0 until h) {
                sum += lum(px[y * w]) + lum(px[y * w + w - 1]); n += 2
            }
            val bg = (sum / n).toInt()
            for (i in 0 until w * h) ink[i] = kotlin.math.abs(lum(px[i]) - bg) > 60
        }
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (ink[y * w + x]) {
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        // Square box around the shape, so left/right and up/down positions are kept.
        val side = maxOf(x1 - x0 + 1, y1 - y0 + 1).toFloat()
        val cx = (x0 + x1 + 1) / 2f
        val cy = (y0 + y1 + 1) / 2f
        val bits = BooleanArray(N * N)
        for (gy in 0 until N) for (gx in 0 until N) {
            // 3×3 samples per cell, majority vote.
            var on = 0
            for (sy in 0..2) for (sx in 0..2) {
                val fx = cx - side / 2 + (gx + (sx + 0.5f) / 3f) * side / N
                val fy = cy - side / 2 + (gy + (sy + 0.5f) / 3f) * side / N
                val ix = fx.toInt()
                val iy = fy.toInt()
                if (ix in 0 until w && iy in 0 until h && ink[iy * w + ix]) on++
            }
            bits[gy * N + gx] = on >= 5
        }
        val sb = StringBuilder(HEX_LENGTH)
        for (i in 0 until N * N step 4) {
            var v = 0
            for (k in 0..3) if (bits[i + k]) v = v or (8 shr k)
            sb.append("0123456789abcdef"[v])
        }
        return sb.toString()
    }

    fun distance(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        var d = 0
        for (i in a.indices) d += Integer.bitCount(Character.digit(a[i], 16) xor Character.digit(b[i], 16))
        return d
    }

    /** The fingerprint as 32 lines of '#' and '.', for the debug log. */
    fun ascii(hex: String): String {
        val sb = StringBuilder()
        for (y in 0 until N) {
            for (x in 0 until N) {
                val i = y * N + x
                val v = Character.digit(hex[i / 4], 16)
                sb.append(if (v and (8 shr (i % 4)) != 0) '#' else '.')
            }
            if (y < N - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /** Short id for log lines and keys. */
    fun shortId(hex: String) = Integer.toHexString(hex.hashCode()).padStart(8, '0')
}

/**
 * Google Maps maneuver pictures (fingerprints from real debug logs) → our maneuver.
 * Code format: TYPE[:SIDE[:DIRECTION_IN_ROUNDABOUT]], e.g. "TURN:LEFT", "ROUNDABOUT:RIGHT:SLIGHT".
 * Empty until the first drive with this build has logged the icons ("NEW ICON").
 */
object KnownIcons {
    val table: List<Pair<String, String>> = listOf(
    )

    fun match(fp: String, table: List<Pair<String, String>> = this.table): Maneuver? {
        var best: Pair<String, String>? = null
        var bestD = Int.MAX_VALUE
        for (e in table) {
            val d = IconFingerprint.distance(fp, e.first)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        if (best == null || bestD > IconFingerprint.MAX_DISTANCE) return null
        return decode(best.second)
    }

    fun decode(code: String): Maneuver? {
        val p = code.split(':')
        val type = runCatching { ManeuverType.valueOf(p[0]) }.getOrNull() ?: return null
        val side = p.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { runCatching { Side.valueOf(it) }.getOrNull() } ?: Side.NONE
        val dir = p.getOrNull(2)?.let { runCatching { ManeuverType.valueOf(it) }.getOrNull() }
        return Maneuver(type, side, null, dir)
    }
}
