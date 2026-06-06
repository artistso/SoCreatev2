package com.socreate.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.socreate.model.Brush
import com.socreate.model.Layer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CPU-based pixel editor for Phase 1.
 * Handles brush stamping directly into Layer tile IntArrays (ARGB).
 * Supports pressure, size, opacity, simple soft stamping.
 * Later phases can move heavy lifting to GPU/FBO.
 */
class PixelEditor(private val context: android.content.Context) {

    private var brushBitmap: Bitmap? = null
    private val brushPixels = mutableMapOf<String, IntArray>() // cache decoded brush textures

    private val TILE_SIZE = 512

    fun stampBrush(
        layer: Layer,
        worldX: Float,
        worldY: Float,
        pressure: Float,
        brush: Brush,
        color: Int = brush.color
    ) {
        val mapped = brush.mapPressure(pressure)
        val size = brush.size * mapped
        val opacity = brush.opacity * mapped

        if (size < 1f) return

        val stampRadius = (size / 2f).roundToInt().coerceAtLeast(1)
        val centerTileX = (worldX / TILE_SIZE).toInt()
        val centerTileY = (worldY / TILE_SIZE).toInt()

        // Affect up to 2x2 tiles around the stamp for large brushes
        for (tx in centerTileX - 1..centerTileX + 1) {
            for (ty in centerTileY - 1..centerTileY + 1) {
                stampOnTile(layer, tx, ty, worldX, worldY, stampRadius, opacity, color, brush.textureAsset)
            }
        }
    }

    private fun stampOnTile(
        layer: Layer,
        tileX: Int,
        tileY: Int,
        worldX: Float,
        worldY: Float,
        radius: Int,
        opacity: Float,
        color: Int,
        textureAsset: String
    ) {
        val tilePixels = layer.getTile(tileX, tileY) ?: IntArray(TILE_SIZE * TILE_SIZE) { 0xFFFFFFFF.toInt() }

        val tileWorldLeft = tileX * TILE_SIZE.toFloat()
        val tileWorldTop = tileY * TILE_SIZE.toFloat()

        val localCenterX = (worldX - tileWorldLeft).roundToInt()
        val localCenterY = (worldY - tileWorldTop).roundToInt()

        val brushTex = getBrushTexture(textureAsset, radius * 2)

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val lx = localCenterX + dx
                val ly = localCenterY + dy
                if (lx < 0 || lx >= TILE_SIZE || ly < 0 || ly >= TILE_SIZE) continue

                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                if (dist > radius) continue

                val idx = ly * TILE_SIZE + lx

                // Sample brush texture (normalized 0-1 alpha)
                val u = (dx + radius).toFloat() / (radius * 2)
                val v = (dy + radius).toFloat() / (radius * 2)
                val brushAlpha = sampleBrushAlpha(brushTex, u, v, radius * 2)

                if (brushAlpha <= 0.01f) continue

                val finalAlpha = brushAlpha * opacity

                val dst = tilePixels[idx]
                val srcR = Color.red(color)
                val srcG = Color.green(color)
                val srcB = Color.blue(color)
                val srcA = (finalAlpha * 255).toInt().coerceIn(0, 255)

                val dstA = Color.alpha(dst)
                val outA = srcA + dstA * (255 - srcA) / 255
                if (outA == 0) continue

                val outR = (srcR * srcA + Color.red(dst) * (255 - srcA)) / outA
                val outG = (srcG * srcA + Color.green(dst) * (255 - srcA)) / outA
                val outB = (srcB * srcA + Color.blue(dst) * (255 - srcA)) / outA

                tilePixels[idx] = Color.argb(outA, outR, outG, outB)
            }
        }

        layer.setTile(tileX, tileY, tilePixels)
    }

    private fun getBrushTexture(assetPath: String, size: Int): IntArray {
        val key = "$assetPath-$size"
        brushPixels[key]?.let { return it }

        return try {
            val input = context.assets.open(assetPath)
            val original = BitmapFactory.decodeStream(input)
            input.close()

            val scaled = if (original.width != size || original.height != size) {
                Bitmap.createScaledBitmap(original, size, size, true)
            } else original

            val pixels = IntArray(size * size)
            scaled.getPixels(pixels, 0, size, 0, 0, size, size)
            brushPixels[key] = pixels
            pixels
        } catch (e: Exception) {
            // Fallback: solid soft circle
            createSoftCircle(size)
        }
    }

    private fun createSoftCircle(size: Int): IntArray {
        val pixels = IntArray(size * size)
        val radius = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - radius
                val dy = y - radius
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val alpha = if (dist > radius) 0f else (1f - dist / radius).coerceIn(0f, 1f)
                pixels[y * size + x] = Color.argb((alpha * 255).toInt(), 255, 255, 255)
            }
        }
        return pixels
    }

    private fun sampleBrushAlpha(brushPixels: IntArray, u: Float, v: Float, size: Int): Float {
        val x = (u * size).toInt().coerceIn(0, size - 1)
        val y = (v * size).toInt().coerceIn(0, size - 1)
        val argb = brushPixels[y * size + x]
        return Color.alpha(argb) / 255f
    }

    fun erase(
        layer: Layer,
        worldX: Float,
        worldY: Float,
        pressure: Float,
        brush: Brush
    ) {
        val mapped = brush.mapPressure(pressure)
        val size = brush.size * mapped
        val opacity = brush.opacity * mapped

        if (size < 1f) return

        val stampRadius = (size / 2f).roundToInt().coerceAtLeast(1)
        val centerTileX = (worldX / TILE_SIZE).toInt()
        val centerTileY = (worldY / TILE_SIZE).toInt()

        for (tx in centerTileX - 1..centerTileX + 1) {
            for (ty in centerTileY - 1..centerTileY + 1) {
                eraseOnTile(layer, tx, ty, worldX, worldY, stampRadius, opacity, brush.textureAsset)
            }
        }
    }

    private fun eraseOnTile(
        layer: Layer,
        tileX: Int,
        tileY: Int,
        worldX: Float,
        worldY: Float,
        radius: Int,
        opacity: Float,
        textureAsset: String
    ) {
        val tilePixels = layer.getTile(tileX, tileY) ?: return

        val tileWorldLeft = tileX * TILE_SIZE.toFloat()
        val tileWorldTop = tileY * TILE_SIZE.toFloat()

        val localCenterX = (worldX - tileWorldLeft).roundToInt()
        val localCenterY = (worldY - tileWorldTop).roundToInt()

        val brushTex = getBrushTexture(textureAsset, radius * 2)

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val lx = localCenterX + dx
                val ly = localCenterY + dy
                if (lx < 0 || lx >= TILE_SIZE || ly < 0 || ly >= TILE_SIZE) continue

                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                if (dist > radius) continue

                val idx = ly * TILE_SIZE + lx

                val u = (dx + radius).toFloat() / (radius * 2)
                val v = (dy + radius).toFloat() / (radius * 2)
                val brushAlpha = sampleBrushAlpha(brushTex, u, v, radius * 2)

                if (brushAlpha <= 0.01f) continue

                val finalAlpha = brushAlpha * opacity

                val dst = tilePixels[idx]
                val dstA = Color.alpha(dst)

                // True alpha erase: reduce alpha
                val newA = (dstA - (finalAlpha * 255)).toInt().coerceAtLeast(0)

                if (newA == 0) {
                    tilePixels[idx] = 0 // fully transparent
                } else {
                    // Keep RGB, reduce alpha
                    val dstR = Color.red(dst)
                    val dstG = Color.green(dst)
                    val dstB = Color.blue(dst)
                    tilePixels[idx] = Color.argb(newA, dstR, dstG, dstB)
                }
            }
        }

        layer.setTile(tileX, tileY, tilePixels)
    }

    fun floodFill(
        layer: Layer,
        worldX: Int,
        worldY: Int,
        fillColor: Int,
        tolerance: Int = 40
    ) {
        FloodFillEngine.floodFill(layer, worldX, worldY, fillColor, tolerance)
    }
}
