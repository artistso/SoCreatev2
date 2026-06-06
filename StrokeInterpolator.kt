package com.socreate.engine

import android.graphics.PointF

object StrokeInterpolator {

    fun interpolateBezier(points: List<PointF>, t: Float): PointF {
        // Cubic Bézier interpolation for smooth strokes
        if (points.size < 4) return linearInterpolate(points, t)
        val i = (t * (points.size - 1)).toInt().coerceIn(0, points.size - 4)
        val t2 = (t * (points.size - 1)) - i
        val p0 = points[i]
        val p1 = points[i + 1]
        val p2 = points[i + 2]
        val p3 = points[i + 3]
        val x = cubicBezier(p0.x, p1.x, p2.x, p3.x, t2)
        val y = cubicBezier(p0.y, p1.y, p2.y, p3.y, t2)
        return PointF(x, y)
    }

    private fun cubicBezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    private fun linearInterpolate(points: List<PointF>, t: Float): PointF {
        val idx = (t * (points.size - 1)).toInt()
        val idx2 = (idx + 1).coerceAtMost(points.size - 1)
        val t2 = (t * (points.size - 1)) - idx
        val x = points[idx].x + (points[idx2].x - points[idx].x) * t2
        val y = points[idx].y + (points[idx2].y - points[idx].y) * t2
        return PointF(x, y)
    }
}
