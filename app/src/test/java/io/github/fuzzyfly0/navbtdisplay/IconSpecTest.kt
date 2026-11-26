package io.github.fuzzyfly0.navbtdisplay

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Every icon builds; with -Dicons.preview=<dir> it also writes SVG previews. */
class IconSpecTest {
    private val all = listOf(
        "straight" to Maneuver(ManeuverType.STRAIGHT),
        "left" to Maneuver(ManeuverType.TURN, Side.LEFT),
        "right" to Maneuver(ManeuverType.TURN, Side.RIGHT),
        "slight_left" to Maneuver(ManeuverType.SLIGHT, Side.LEFT),
        "slight_right" to Maneuver(ManeuverType.SLIGHT, Side.RIGHT),
        "sharp_left" to Maneuver(ManeuverType.SHARP, Side.LEFT),
        "sharp_right" to Maneuver(ManeuverType.SHARP, Side.RIGHT),
        "keep_left" to Maneuver(ManeuverType.KEEP, Side.LEFT),
        "keep_right" to Maneuver(ManeuverType.KEEP, Side.RIGHT),
        "uturn" to Maneuver(ManeuverType.UTURN, Side.LEFT),
        "rb_exit1" to Maneuver(ManeuverType.ROUNDABOUT, exit = 1),
        "rb_exit2" to Maneuver(ManeuverType.ROUNDABOUT, exit = 2),
        "rb_exit3" to Maneuver(ManeuverType.ROUNDABOUT, exit = 3),
        "rb_exit4" to Maneuver(ManeuverType.ROUNDABOUT, exit = 4),
        "rb_slight_right" to Maneuver(ManeuverType.ROUNDABOUT, Side.RIGHT, 2, ManeuverType.SLIGHT),
        "rb_leave" to Maneuver(ManeuverType.ROUNDABOUT_EXIT),
        "offramp_right" to Maneuver(ManeuverType.OFF_RAMP, Side.RIGHT),
        "offramp_left" to Maneuver(ManeuverType.OFF_RAMP, Side.LEFT),
        "onramp_right" to Maneuver(ManeuverType.ON_RAMP, Side.RIGHT),
        "merge_left" to Maneuver(ManeuverType.MERGE, Side.LEFT),
        "merge_right" to Maneuver(ManeuverType.MERGE, Side.RIGHT),
        "ferry" to Maneuver(ManeuverType.FERRY),
        "dest" to Maneuver(ManeuverType.DESTINATION),
        "dest_left" to Maneuver(ManeuverType.DESTINATION, Side.LEFT),
        "dest_right" to Maneuver(ManeuverType.DESTINATION, Side.RIGHT),
    )

    @Test fun allBuild() {
        for ((_, m) in all) assertNotNull(IconSpec.build(m))
        assertNull(IconSpec.build(Maneuver(ManeuverType.UNKNOWN)))
        val dir = System.getProperty("icons.preview") ?: return
        File(dir).mkdirs()
        for ((name, m) in all) File(dir, "$name.svg").writeText(svg(IconSpec.build(m)!!))
        File(dir, "straight_white.svg").writeText(svg(IconSpec.straightWhite()))
    }

    private fun hex(c: Int) = String.format("#%06x", c and 0xFFFFFF)
    private fun f(v: Float) = String.format(java.util.Locale.ROOT, "%.2f", v)

    private fun svg(shapes: List<Shape>): String {
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 150 150' width='300' height='300'>")
        sb.append("<rect width='150' height='150' fill='${hex(IconSpec.BG)}'/>")
        for (s in shapes) when (s) {
            is Shape.Stroke -> {
                val d = StringBuilder()
                var cx = 0f; var cy = 0f
                for (c in s.cmds) when (c) {
                    is PathCmd.Move -> { d.append("M${f(c.x)} ${f(c.y)} "); cx = c.x; cy = c.y }
                    is PathCmd.Line -> { d.append("L${f(c.x)} ${f(c.y)} "); cx = c.x; cy = c.y }
                    is PathCmd.Quad -> { d.append("Q${f(c.cx)} ${f(c.cy)} ${f(c.x)} ${f(c.y)} "); cx = c.x; cy = c.y }
                    is PathCmd.Arc -> {
                        val a = Math.toRadians((c.startDeg + c.sweepDeg).toDouble())
                        val ex = c.cx + c.r * Math.cos(a).toFloat(); val ey = c.cy + c.r * Math.sin(a).toFloat()
                        val large = if (Math.abs(c.sweepDeg) > 180) 1 else 0
                        val sweep = if (c.sweepDeg > 0) 1 else 0
                        d.append("A${f(c.r)} ${f(c.r)} 0 $large $sweep ${f(ex)} ${f(ey)} "); cx = ex; cy = ey
                    }
                }
                sb.append("<path d='$d' fill='none' stroke='${hex(s.color)}' stroke-width='${f(s.width)}' stroke-linecap='round' stroke-linejoin='round'/>")
            }
            is Shape.Polygon -> sb.append("<polygon points='${s.points.joinToString(" ") { "${f(it.first)},${f(it.second)}" }}' fill='${hex(s.color)}'/>")
            is Shape.Circle -> sb.append("<circle cx='${f(s.cx)}' cy='${f(s.cy)}' r='${f(s.r)}' fill='${s.fill?.let(::hex) ?: "none"}' stroke='${s.stroke?.let(::hex) ?: "none"}' stroke-width='${f(s.strokeWidth)}'/>")
            is Shape.Text -> sb.append("<text x='${f(s.x)}' y='${f(s.y)}' font-family='sans-serif' font-weight='bold' font-size='${f(s.size)}' text-anchor='middle' fill='${hex(s.color)}'>${s.text}</text>")
        }
        return sb.append("</svg>").toString()
    }
}
