package com.socreate.model

data class Brush(
    val name: String = "Round Soft",
    val textureAsset: String = "brushes/round_soft.png",
    var size: Float = 20f,
    var opacity: Float = 1f,
    var spacing: Float = 0.15f, // relative to size
    var pressureSensitivity: Boolean = true,
    var color: Int = 0xFF000000.toInt(),
    // Pressure curve as list of normalized (x,y) control points for a simple Bézier-like response (0..1)
    // Default: linear
    var pressureCurvePoints: MutableList<Pair<Float, Float>> = mutableListOf(
        0f to 0f,
        0.33f to 0.33f,
        0.66f to 0.66f,
        1f to 1f
    )
) {
    companion object {
        fun default() = Brush()
    }

    /**
     * Map input pressure (0-1) through the curve using linear interpolation between points.
     * For full Bézier, this can be upgraded later.
     */
    fun mapPressure(inputPressure: Float): Float {
        if (!pressureSensitivity) return 1f
        val p = inputPressure.coerceIn(0f, 1f)
        if (pressureCurvePoints.isEmpty()) return p

        // Find segment
        for (i in 0 until pressureCurvePoints.size - 1) {
            val (x1, y1) = pressureCurvePoints[i]
            val (x2, y2) = pressureCurvePoints[i + 1]
            if (p >= x1 && p <= x2) {
                val t = if (x2 == x1) 0f else (p - x1) / (x2 - x1)
                return y1 + t * (y2 - y1)
            }
        }
        return pressureCurvePoints.last().second
    }
}
