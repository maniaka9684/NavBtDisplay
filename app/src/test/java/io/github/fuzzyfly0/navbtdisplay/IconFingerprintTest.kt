package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconFingerprintTest {
    /** A left- or right-pointing "L" arrow drawn opaque on transparency, at any size and offset. */
    private fun arrow(size: Int, offset: Int, right: Boolean, canvas: Int = 96): IntArray {
        val px = IntArray(canvas * canvas)
        fun set(x: Int, y: Int) {
            val xx = if (right) offset + size - 1 - x else offset + x
            px[(offset + y) * canvas + xx] = 0xFFFFFFFF.toInt()
        }
        for (y in 0 until size) for (x in 0 until size) {
            val stem = x in size * 6 / 10 until size * 8 / 10 && y > size / 3
            val bar = y in size / 3 until size / 2 && x > size / 5
            val head = x <= size / 5 && kotlin.math.abs(y - size * 5 / 12) <= (x + 1)
            if (stem || bar || head) set(x, y)
        }
        return px
    }

    @Test fun sameShapeDifferentSizeMatches() {
        val a = IconFingerprint.fromArgb(arrow(80, 4, false), 96, 96)!!
        val b = IconFingerprint.fromArgb(arrow(48, 20, false), 96, 96)!!
        assertTrue(IconFingerprint.distance(a, b) <= IconFingerprint.MAX_DISTANCE)
    }

    @Test fun mirroredShapeDiffers() {
        val l = IconFingerprint.fromArgb(arrow(80, 4, false), 96, 96)!!
        val r = IconFingerprint.fromArgb(arrow(80, 4, true), 96, 96)!!
        assertTrue(IconFingerprint.distance(l, r) > IconFingerprint.MAX_DISTANCE)
    }

    @Test fun tableLookup() {
        val l = IconFingerprint.fromArgb(arrow(80, 4, false), 96, 96)!!
        val table = listOf(l to "TURN:LEFT")
        assertEquals(Maneuver(ManeuverType.TURN, Side.LEFT), KnownIcons.match(IconFingerprint.fromArgb(arrow(60, 10, false), 96, 96)!!, table))
        assertNull(KnownIcons.match(IconFingerprint.fromArgb(arrow(80, 4, true), 96, 96)!!, table))
        assertEquals(Maneuver(ManeuverType.ROUNDABOUT, Side.RIGHT, null, ManeuverType.SLIGHT), KnownIcons.decode("ROUNDABOUT:RIGHT:SLIGHT"))
    }

    @Test fun emptyPictureHasNoFingerprint() {
        assertNull(IconFingerprint.fromArgb(IntArray(64 * 64), 64, 64))
    }

    @Test fun asciiHas32Lines() {
        val l = IconFingerprint.fromArgb(arrow(80, 4, false), 96, 96)!!
        assertEquals(IconFingerprint.HEX_LENGTH, l.length)
        assertEquals(32, IconFingerprint.ascii(l).lines().size)
    }
}
