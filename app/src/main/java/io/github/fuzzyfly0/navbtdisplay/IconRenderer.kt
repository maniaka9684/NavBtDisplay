package io.github.fuzzyfly0.navbtdisplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Draws [IconSpec] shapes into the square album art bitmap sent to the car. */
object IconRenderer {

    fun render(shapes: List<Shape>, size: Int = ArtImages.SIZE): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(IconSpec.BG)
        val k = size / IconSpec.SIZE
        canvas.scale(k, k)
        val paint = Paint()
        for (s in shapes) {
            paint.reset()
            paint.isAntiAlias = true
            when (s) {
                is Shape.Stroke -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = s.width
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = s.color
                    canvas.drawPath(path(s.cmds), paint)
                }
                is Shape.Polygon -> {
                    paint.style = Paint.Style.FILL
                    paint.color = s.color
                    val p = Path()
                    s.points.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                    p.close()
                    canvas.drawPath(p, paint)
                }
                is Shape.Circle -> {
                    s.fill?.let {
                        paint.style = Paint.Style.FILL
                        paint.color = it
                        canvas.drawCircle(s.cx, s.cy, s.r, paint)
                    }
                    s.stroke?.let {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = s.strokeWidth
                        paint.color = it
                        canvas.drawCircle(s.cx, s.cy, s.r, paint)
                    }
                }
                is Shape.Text -> {
                    paint.style = Paint.Style.FILL
                    paint.color = s.color
                    paint.textSize = s.size
                    paint.textAlign = Paint.Align.CENTER
                    paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText(s.text, s.x, s.y, paint)
                }
            }
        }
        return bmp
    }

    private fun path(cmds: List<PathCmd>): Path {
        val p = Path()
        for (c in cmds) {
            when (c) {
                is PathCmd.Move -> p.moveTo(c.x, c.y)
                is PathCmd.Line -> p.lineTo(c.x, c.y)
                is PathCmd.Quad -> p.quadTo(c.cx, c.cy, c.x, c.y)
                is PathCmd.Arc -> p.arcTo(RectF(c.cx - c.r, c.cy - c.r, c.cx + c.r, c.cy + c.r), c.startDeg, c.sweepDeg, false)
            }
        }
        return p
    }
}
