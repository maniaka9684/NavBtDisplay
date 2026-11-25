package io.github.fuzzyfly0.navbtdisplay

import kotlin.math.cos
import kotlin.math.sin

/** Drawing commands of a path in the 150×150 icon space. */
sealed class PathCmd {
    data class Move(val x: Float, val y: Float) : PathCmd()
    data class Line(val x: Float, val y: Float) : PathCmd()
    data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCmd()

    /** Arc on the circle ([cx],[cy],[r]), from [startDeg] by [sweepDeg]; 0° = east, positive = clockwise (y down). */
    data class Arc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweepDeg: Float) : PathCmd()
}

sealed class Shape {
    data class Stroke(val cmds: List<PathCmd>, val width: Float, val color: Int) : Shape()
    data class Polygon(val points: List<Pair<Float, Float>>, val color: Int) : Shape()
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Int?, val stroke: Int? = null, val strokeWidth: Float = 0f) : Shape()
    data class Text(val x: Float, val y: Float, val text: String, val size: Float, val color: Int) : Shape()
}

/**
 * The maneuver icons, in the style approved for this build: dark square tile, maneuver
 * path thick and blue with a solid head, skipped alternatives grey, roundabouts
 * counter-clockwise (right-hand traffic) with the exit number in a blue centre circle,
 * optional yellow compass badge at the bottom right. Pure Kotlin, so the geometry
 * can be checked on the JVM (see IconSpecTest, which also writes SVG previews).
 */
object IconSpec {
    const val SIZE = 150f
    const val BG = 0xFF1B212B.toInt()
    const val BLUE = 0xFF4DA3FF.toInt()
    const val GREY = 0xFF5C6470.toInt()
    const val YELLOW = 0xFFF0B429.toInt()
    const val BADGE = 0xFF242C38.toInt()
    const val DARK = 0xFF0F1216.toInt()

    private const val ROUTE_W = 11f
    private const val ROAD_W = 10f

    private fun mv(x: Number, y: Number) = PathCmd.Move(x.toFloat(), y.toFloat())
    private fun ln(x: Number, y: Number) = PathCmd.Line(x.toFloat(), y.toFloat())
    private fun q(cx: Number, cy: Number, x: Number, y: Number) =
        PathCmd.Quad(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat())

    private fun route(vararg c: PathCmd) = Shape.Stroke(c.toList(), ROUTE_W, BLUE)
    private fun road(vararg c: PathCmd) = Shape.Stroke(c.toList(), ROAD_W, GREY)

    /** Arrow head with its base centre at ([x],[y]); [deg] 0 = pointing up, positive = clockwise. */
    fun head(x: Float, y: Float, deg: Float, color: Int = BLUE, scale: Float = 1f): Shape.Polygon {
        val pts = listOf(0f to -17f, -13f to 4f, 13f to 4f)
        return Shape.Polygon(pts.map { (px, py) -> rot(px * scale, py * scale, deg, x, y) }, color)
    }

    private fun rot(px: Float, py: Float, deg: Float, tx: Float, ty: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return (px * c - py * s + tx) to (px * s + py * c + ty)
    }

    /** Mirrors left-hand geometry to the right (x → 150 − x, angles negated). */
    private fun mirror(shapes: List<Shape>): List<Shape> = shapes.map { sh ->
        when (sh) {
            is Shape.Stroke -> sh.copy(cmds = sh.cmds.map { c ->
                when (c) {
                    is PathCmd.Move -> c.copy(x = SIZE - c.x)
                    is PathCmd.Line -> c.copy(x = SIZE - c.x)
                    is PathCmd.Quad -> c.copy(cx = SIZE - c.cx, x = SIZE - c.x)
                    is PathCmd.Arc -> c.copy(cx = SIZE - c.cx, startDeg = 180f - c.startDeg, sweepDeg = -c.sweepDeg)
                }
            })
            is Shape.Polygon -> sh.copy(points = sh.points.map { (x, y) -> (SIZE - x) to y })
            is Shape.Circle -> sh.copy(cx = SIZE - sh.cx)
            is Shape.Text -> sh.copy(x = SIZE - sh.x)
        }
    }

    private fun sided(side: Side, leftShapes: List<Shape>) =
        if (side == Side.RIGHT) mirror(leftShapes) else leftShapes

    /** Shapes of [m], or null for UNKNOWN (the caller then uses the notification picture). */
    fun build(m: Maneuver, compassDeg: Int? = null): List<Shape>? {
        val body: List<Shape> = when (m.type) {
            ManeuverType.STRAIGHT -> straight()
            ManeuverType.TURN -> sided(m.side, turnLeft())
            ManeuverType.SLIGHT -> sided(m.side, slightLeft())
            ManeuverType.SHARP -> sided(m.side, sharpLeft())
            ManeuverType.KEEP -> sided(m.side, keepLeft())
            ManeuverType.UTURN -> uturn()
            ManeuverType.ROUNDABOUT -> roundabout(exitAngle(m), m.exit)
            ManeuverType.ROUNDABOUT_EXIT -> roundabout(-90f, null)
            ManeuverType.OFF_RAMP -> if (m.side == Side.LEFT) mirror(offRampRight()) else offRampRight()
            ManeuverType.ON_RAMP -> if (m.side == Side.LEFT) mirror(onRampRight()) else onRampRight()
            ManeuverType.MERGE -> if (m.side == Side.RIGHT) mirror(mergeLeft()) else mergeLeft()
            ManeuverType.FERRY -> ferry()
            ManeuverType.DESTINATION -> destination(m.side)
            ManeuverType.UNKNOWN -> return null
        }
        return body + (compassDeg?.let(::compass) ?: emptyList())
    }

    private fun straight() = listOf(route(mv(75, 135), ln(75, 40)), head(75f, 36f, 0f))

    private fun turnLeft() = listOf(
        road(mv(75, 65), ln(75, 22)),
        road(mv(75, 65), ln(128, 65)),
        route(mv(75, 135), ln(75, 82), q(75, 65, 58, 65), ln(38, 65)),
        head(36f, 65f, -90f),
    )

    private fun slightLeft() = listOf(
        road(mv(75, 88), ln(75, 22)),
        route(mv(75, 135), ln(75, 88), ln(50, 56)),
        head(50f, 56f, -38f),
    )

    private fun sharpLeft() = listOf(
        road(mv(75, 70), ln(75, 22)),
        route(mv(75, 135), ln(75, 70), ln(44, 106)),
        head(44f, 106f, -139f),
    )

    private fun keepLeft() = listOf(
        road(mv(75, 95), ln(105, 45), ln(105, 22)),
        route(mv(75, 135), ln(75, 95), ln(45, 45), ln(45, 36)),
        head(45f, 34f, 0f),
    )

    /** To the left, as in right-hand traffic. */
    private fun uturn() = listOf(
        route(mv(97, 135), ln(97, 72), PathCmd.Arc(75f, 72f, 22f, 0f, -180f), ln(53, 100)),
        head(53f, 104f, 180f),
    )

    /**
     * Exit direction on the ring, degrees (0 = east, −90 = north). From the text direction
     * when known; otherwise from the exit number, assuming a four-arm roundabout
     * (1 = right, 2 = straight, 3 = left, 4+ = back). The number in the centre is exact.
     */
    fun exitAngle(m: Maneuver): Float {
        val right = m.side == Side.RIGHT
        return when (m.roundaboutDirection) {
            ManeuverType.STRAIGHT -> -90f
            ManeuverType.TURN -> if (right) 0f else -180f
            ManeuverType.SLIGHT -> if (right) -45f else -135f
            ManeuverType.SHARP -> if (right) 40f else -205f
            ManeuverType.UTURN -> -235f
            else -> when (m.exit) {
                1 -> 0f
                2, null -> -90f
                3 -> -180f
                else -> -235f
            }
        }
    }

    private const val RC_X = 75f
    private const val RC_Y = 80f
    private const val RING_R = 36f
    private const val EXIT_R = 58f

    private fun ringPoint(r: Float, deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        return (RC_X + r * cos(a).toFloat()) to (RC_Y + r * sin(a).toFloat())
    }

    /** Counter-clockwise ring, entry from the bottom, exit at [exitDeg]. */
    private fun roundabout(exitDeg: Float, exit: Int?): List<Shape> {
        val out = mutableListOf<Shape>()
        for (arm in listOf(0f, -90f, -180f)) {
            if (kotlin.math.abs(arm - exitDeg) < 20f) continue
            val (x1, y1) = ringPoint(RING_R, arm)
            val (x2, y2) = ringPoint(EXIT_R + 6f, arm)
            out += road(mv(x1, y1), ln(x2, y2))
        }
        out += Shape.Circle(RC_X, RC_Y, RING_R, fill = null, stroke = GREY, strokeWidth = 13f)
        val (ex1, ey1) = ringPoint(RING_R, exitDeg)
        val (ex2, ey2) = ringPoint(EXIT_R, exitDeg)
        out += route(
            mv(RC_X, 142f),
            ln(RC_X, RC_Y + RING_R),
            PathCmd.Arc(RC_X, RC_Y, RING_R, 90f, exitDeg - 90f),
            ln(ex1, ey1),
            ln(ex2, ey2),
        )
        out += head(ex2, ey2, exitDeg + 90f)
        if (exit != null) {
            out += Shape.Circle(RC_X, RC_Y, 18f, fill = BLUE)
            out += Shape.Text(RC_X, RC_Y + 8f, exit.toString(), 23f, DARK)
        }
        return out
    }

    /** Leaving the road to the right (motorway exit). */
    private fun offRampRight() = listOf(
        road(mv(62, 95), ln(62, 22)),
        route(mv(62, 135), ln(62, 95), q(62, 72, 86, 63), q(104, 56, 104, 40)),
        head(104f, 37f, 0f),
    )

    /** From the local road (left) onto the motorway (right). */
    private fun onRampRight() = listOf(
        road(mv(110, 135), ln(110, 22)),
        road(mv(62, 95), ln(62, 22)),
        route(mv(62, 135), ln(62, 95), q(62, 74, 86, 66), q(110, 58, 110, 40)),
        head(110f, 37f, 0f),
    )

    /** Merging into the lane on the left (as from a motorway on-ramp). */
    private fun mergeLeft() = listOf(
        road(mv(60, 135), ln(60, 22)),
        route(mv(100, 135), ln(100, 100), q(100, 80, 80, 70), q(60, 60, 60, 44)),
        head(60f, 40f, 0f),
    )

    private fun ferry() = listOf(
        Shape.Polygon(listOf(60f to 60f, 92f to 60f, 92f to 84f, 60f to 84f), BLUE),
        Shape.Polygon(listOf(32f to 86f, 118f to 86f, 102f to 108f, 48f to 108f), BLUE),
        road(mv(25, 124), q(37, 116, 50, 124), q(62, 132, 75, 124), q(87, 116, 100, 124), q(112, 132, 125, 124)),
    )

    private fun pin(cx: Float, cy: Float, r: Float): List<Shape> = listOf(
        Shape.Polygon(listOf(cx - r * 0.85f to cy + r * 0.5f, cx + r * 0.85f to cy + r * 0.5f, cx to cy + r * 1.9f), BLUE),
        Shape.Circle(cx, cy, r, fill = BLUE),
        Shape.Circle(cx, cy, r * 0.4f, fill = BG),
    )

    private fun destination(side: Side): List<Shape> = when (side) {
        Side.NONE -> listOf(route(mv(75, 135), ln(75, 98))) + pin(75f, 56f, 22f)
        Side.LEFT -> listOf(route(mv(95, 135), ln(95, 44)), head(95f, 40f, 0f)) + pin(48f, 66f, 17f)
        Side.RIGHT -> listOf(route(mv(55, 135), ln(55, 44)), head(55f, 40f, 0f)) + pin(102f, 66f, 17f)
    }

    /** Yellow badge at the bottom right; [deg] = direction to the destination relative to driving (0 = ahead). */
    fun compass(deg: Int): List<Shape> {
        val cx = 128f
        val cy = 128f
        val pts = listOf(0f to -12f, -8f to 9f, 0f to 4f, 8f to 9f)
        return listOf(
            Shape.Circle(cx, cy, 17f, fill = BADGE, stroke = YELLOW, strokeWidth = 2.5f),
            Shape.Polygon(pts.map { (x, y) -> rot(x, y, deg.toFloat(), cx, cy) }, YELLOW),
        )
    }
}
