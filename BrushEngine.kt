package com.socreate.engine

import android.view.MotionEvent
import com.socreate.model.Brush
import com.socreate.model.Layer
import com.socreate.rendering.SoCreateRenderer

class BrushEngine(
    private val renderer: SoCreateRenderer,
    private val pixelEditor: PixelEditor
) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastPressure = 1f
    private val strokePoints = mutableListOf<PointWithPressure>()

    // Current active layer for stamping (set by ViewModel)
    var currentLayer: Layer? = null

    // Tool mode
    var currentTool: Tool = Tool.BRUSH

    // Symmetry (full radial/kaleidoscopic)
    var symmetryEnabled: Boolean = false
    var symmetrySectors: Int = 4  // For radial/kaleidoscopic
    var symmetryCenterX: Float = 960f  // Default canvas center
    var symmetryCenterY: Float = 540f
    var symmetryMode: String = "Mirror"  // Mirror, Radial, Kaleidoscopic

    // Liquify strength
    var liquifyStrength: Float = 1f

    enum class Tool { BRUSH, ERASER, FILL, LIQUIFY }

    fun processTouchEvent(event: MotionEvent, worldX: Float, worldY: Float, pressure: Float) {
        // worldX/Y already transformed by pan/zoom from caller
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                strokePoints.clear()
                addPoint(worldX, worldY, pressure)
                applyStamp(worldX, worldY, pressure)
            }
            MotionEvent.ACTION_MOVE -> {
                addPoint(worldX, worldY, pressure)
                interpolateAndStamp()
            }
            MotionEvent.ACTION_UP -> {
                strokePoints.clear()
                // Commit any pending undo action here if desired
            }
        }
        lastX = worldX
        lastY = worldY
        lastPressure = pressure
    }

    private fun addPoint(x: Float, y: Float, pressure: Float) {
        strokePoints.add(PointWithPressure(x, y, pressure))
    }

    private fun interpolateAndStamp() {
        if (strokePoints.size < 2) return
        val brush = renderer.currentBrush
        val step = maxOf(1f, brush.size * brush.spacing * 0.5f)
        val last = strokePoints[strokePoints.size - 2]
        val current = strokePoints.last()
        val distance = kotlin.math.hypot((current.x - last.x).toDouble(), (current.y - last.y).toDouble()).toFloat()
        val steps = (distance / step).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = last.x + (current.x - last.x) * t
            val y = last.y + (current.y - last.y) * t
            val p = last.pressure + (current.pressure - last.pressure) * t
            applyStamp(x, y, p)
        }
    }

    private fun applyStamp(x: Float, y: Float, pressure: Float) {
        val layer = currentLayer ?: return
        val brush = renderer.currentBrush

        // Capture before state for smart undo (only affected tiles)
        val beforeStates = mutableMapOf<Pair<Int, Int>, IntArray>()
        val tileX = x.toInt() / 512
        val tileY = y.toInt() / 512
        val coord = Pair(tileX, tileY)
        layer.getTile(tileX, tileY)?.let { beforeStates[coord] = it.copyOf() }

        val positions = mutableListOf(Pair(x, y))

        // Apply full symmetry: replicate for mirror, radial, kaleidoscopic
        if (symmetryEnabled) {
            when (symmetryMode) {
                "Mirror" -> {
                    val mirrorX = 2 * symmetryCenterX - x
                    positions.add(Pair(mirrorX, y))
                }
                "Radial", "Kaleidoscopic" -> {
                    val cx = symmetryCenterX
                    val cy = symmetryCenterY
                    val dx = x - cx
                    val dy = y - cy
                    val angleStep = (2 * Math.PI / symmetrySectors).toFloat()
                    for (i in 1 until symmetrySectors) {
                        val angle = i * angleStep
                        val rx = (dx * kotlin.math.cos(angle) - dy * kotlin.math.sin(angle)).toFloat()
                        val ry = (dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)).toFloat()
                        positions.add(Pair(cx + rx, cy + ry))
                    }
                    if (symmetryMode == "Kaleidoscopic") {
                        // Add reflections for kaleidoscopic
                        for (i in 1 until symmetrySectors) {
                            val angle = i * angleStep
                            val rx = (dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)).toFloat()
                            val ry = (-dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)).toFloat()
                            positions.add(Pair(cx + rx, cy + ry))
                        }
                    }
                }
            }
        }

        positions.forEach { (px, py) ->
            when (currentTool) {
                Tool.BRUSH -> {
                    pixelEditor.stampBrush(layer, px, py, pressure, brush)
                }
                Tool.ERASER -> {
                    pixelEditor.erase(layer, px, py, pressure, brush)
                }
                Tool.LIQUIFY -> {
                    // Basic liquify mesh warp: displace pixels around brush center
                    applyLiquify(layer, px, py, pressure, brush)
                }
                Tool.FILL -> {
                    // Fill handled separately
                }
            }
        }

        // Capture after for undo (simplified for first position)
        val afterStates = mutableMapOf<Pair<Int, Int>, IntArray>()
        layer.getTile(tileX, tileY)?.let { afterStates[coord] = it.copyOf() }

        // Push smart undo (diff-based for memory efficiency)
        // Note: In full integration, pass to ViewModel.pushSmartStrokeUndo
        // For now, assume caller wires it

        // Tell renderer the tile(s) are dirty
        renderer.markTileDirty(tileX, tileY)
        renderer.requestRender()
    }

    private fun applyLiquify(layer: Layer, x: Float, y: Float, pressure: Float, brush: Brush) {
        // Polished true mesh/GPU-style liquify (CPU mesh warp for this step): 
        // Define a small control mesh grid around brush, radially displace mesh vertices,
        // then resample pixels using bilinear interp from displaced mesh (true deformation, not per-pixel push).
        val radius = brush.size * 2f
        val strength = liquifyStrength * pressure * 0.5f
        val tileX = x.toInt() / 512
        val tileY = y.toInt() / 512
        val pixels = layer.getTile(tileX, tileY) ?: return

        val localX = x.toInt() % 512
        val localY = y.toInt() % 512

        // Mesh grid (e.g. 7x7 control points for "true mesh")
        val meshSize = 7
        val mesh = Array(meshSize) { Array(meshSize) { Pair(0f, 0f) } }
        val step = radius * 2 / (meshSize - 1)

        // Init mesh relative to brush center
        for (my in 0 until meshSize) {
            for (mx in 0 until meshSize) {
                val mxRel = -radius + mx * step
                val myRel = -radius + my * step
                mesh[my][mx] = Pair(localX + mxRel, localY + myRel)
            }
        }

        // Displace mesh vertices (radial push for mesh warp)
        for (my in 0 until meshSize) {
            for (mx in 0 until meshSize) {
                val (mxPos, myPos) = mesh[my][mx]
                val dx = mxPos - localX
                val dy = myPos - localY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > radius || dist < 0.1f) continue
                val factor = (1f - dist / radius) * strength
                val nx = mxPos + dx * factor
                val ny = myPos + dy * factor
                mesh[my][mx] = Pair(nx, ny)
            }
        }

        // Resample pixels using mesh (bilinear from displaced control points)
        val origPixels = pixels.copyOf()
        for (dy in -radius.toInt()..radius.toInt()) {
            for (dx in -radius.toInt()..radius.toInt()) {
                val lx = localX + dx
                val ly = localY + dy
                if (lx < 0 || lx >= 512 || ly < 0 || ly >= 512) continue

                // Find mesh cell and bilinear interp source pos
                val mx = ((dx + radius) / step).toInt().coerceIn(0, meshSize - 2)
                val my = ((dy + radius) / step).toInt().coerceIn(0, meshSize - 2)
                val fx = ((dx + radius) % step) / step
                val fy = ((dy + radius) % step) / step

                // Bilinear on displaced mesh
                val p00 = mesh[my][mx]
                val p10 = mesh[my][mx + 1]
                val p01 = mesh[my + 1][mx]
                val p11 = mesh[my + 1][mx + 1]

                val srcX = (1 - fx) * (1 - fy) * p00.first + fx * (1 - fy) * p10.first +
                           (1 - fx) * fy * p01.first + fx * fy * p11.first
                val srcY = (1 - fx) * (1 - fy) * p00.second + fx * (1 - fy) * p10.second +
                           (1 - fx) * fy * p01.second + fx * fy * p11.second

                val srcLx = srcX.toInt().coerceIn(0, 511)
                val srcLy = srcY.toInt().coerceIn(0, 511)

                val dstIdx = ly * 512 + lx
                val srcIdx = srcLy * 512 + srcLx
                pixels[dstIdx] = origPixels[srcIdx]
            }
        }
        layer.setTile(tileX, tileY, pixels)
    }

    fun doFill(worldX: Int, worldY: Int, color: Int) {
        val layer = currentLayer ?: return
        pixelEditor.floodFill(layer, worldX, worldY, color)
        renderer.markTileDirty(worldX / 512, worldY / 512)
        renderer.requestRender()
    }

    private data class PointWithPressure(val x: Float, val y: Float, val pressure: Float)
}
