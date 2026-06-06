package com.socreate.engine

import com.socreate.model.Layer
import java.util.*

/**
 * Simple flood fill implementation for Phase 1.
 * Operates on tile pixel data (IntArray ARGB).
 * TODO: Optimize for large canvases, handle tolerance, anti-aliasing.
 */
object FloodFillEngine {

    fun floodFill(
        layer: Layer,
        startX: Int,
        startY: Int,
        fillColor: Int,
        tolerance: Int = 32,
        tileSize: Int = 512
    ) {
        // For simplicity in Phase 1, assume single-tile or small area.
        // In full tiled impl, calculate which tiles are affected and fill across boundaries.
        val tileX = startX / tileSize
        val tileY = startY / tileSize
        val localX = startX % tileSize
        val localY = startY % tileSize

        val pixels = layer.getTile(tileX, tileY) ?: IntArray(tileSize * tileSize) { 0xFFFFFFFF.toInt() } // default white

        val targetColor = pixels[localY * tileSize + localX]
        if (targetColor == fillColor) return

        val queue = LinkedList<Pair<Int, Int>>()
        queue.add(Pair(localX, localY))

        val visited = BooleanArray(tileSize * tileSize) { false }

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            val idx = y * tileSize + x
            if (x < 0 || x >= tileSize || y < 0 || y >= tileSize || visited[idx]) continue

            val current = pixels[idx]
            if (!colorsMatch(current, targetColor, tolerance)) continue

            visited[idx] = true
            pixels[idx] = fillColor

            // 4-connected
            queue.add(Pair(x + 1, y))
            queue.add(Pair(x - 1, y))
            queue.add(Pair(x, y + 1))
            queue.add(Pair(x, y - 1))
        }

        layer.setTile(tileX, tileY, pixels)
        // TODO: Propagate to adjacent tiles if fill crosses boundaries
    }

    private fun colorsMatch(c1: Int, c2: Int, tolerance: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        return Math.abs(r1 - r2) <= tolerance &&
                Math.abs(g1 - g2) <= tolerance &&
                Math.abs(b1 - b2) <= tolerance
    }
}
