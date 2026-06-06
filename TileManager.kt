package com.socreate.rendering

import android.opengl.GLES30.*
import android.util.LruCache

class TileManager(private val tileSize: Int = 512, initialMaxTilesInMemory: Int = 96) {

    data class TileCoord(val x: Int, val y: Int)

    private var maxTiles = initialMaxTilesInMemory
    private val tileCache = LruCache<TileCoord, Int>(maxTiles) // Dynamic LRU, adjustable for perf (further refined)
    private val dirtyTiles = mutableSetOf<TileCoord>() // Track tiles needing upload for efficient rendering

    fun setMaxTiles(newMax: Int) {
        maxTiles = newMax.coerceIn(32, 256)
        // Note: LruCache doesn't resize easily; in real would recreate or use custom
    }

    fun getTileTexture(coord: TileCoord): Int {
        return tileCache.get(coord) ?: run {
            val texId = createEmptyTileTexture()
            tileCache.put(coord, texId)
            texId
        }
    }

    fun writeToTile(coord: TileCoord, pixels: IntArray) {
        val texId = getTileTexture(coord)
        glBindTexture(GL_TEXTURE_2D, texId)
        glTexSubImage2D(
            GL_TEXTURE_2D, 0, 0, 0, tileSize, tileSize,
            GL_RGBA, GL_UNSIGNED_BYTE, pixels
        )
        dirtyTiles.remove(coord) // Clean after upload
    }

    fun markTileDirty(coord: TileCoord) {
        dirtyTiles.add(coord)
    }

    fun getDirtyTiles(): Set<TileCoord> = dirtyTiles.toSet()

    private fun createEmptyTileTexture(): Int {
        val texIds = IntArray(1)
        glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        glBindTexture(GL_TEXTURE_2D, texId)
        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_RGBA, tileSize, tileSize, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, null
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return texId
    }

    // Refined visible tiles with padding for smooth panning/zoom (perf improvement)
    fun getVisibleTiles(
        screenWidth: Int, screenHeight: Int,
        panX: Float, panY: Float,
        zoom: Float,
        canvasWidth: Int = 1920, canvasHeight: Int = 1080,
        paddingTiles: Int = 2 // Increased padding for prefetching during movement (performance further)
    ): List<TileCoord> {
        val left = (-panX / zoom).toInt().coerceAtLeast(0)
        val right = left + (screenWidth / zoom).toInt()
        val top = (-panY / zoom).toInt().coerceAtLeast(0)
        val bottom = top + (screenHeight / zoom).toInt()

        val startX = ((left / tileSize) - paddingTiles).coerceAtLeast(0)
        val endX = ((right / tileSize) + 1 + paddingTiles).coerceAtMost((canvasWidth / tileSize) + 1)
        val startY = ((top / tileSize) - paddingTiles).coerceAtLeast(0)
        val endY = ((bottom / tileSize) + 1 + paddingTiles).coerceAtMost((canvasHeight / tileSize) + 1)

        return (startX..endX).flatMap { x ->
            (startY..endY).map { y -> TileCoord(x, y) }
        }
    }

    fun evictOldTiles() {
        // Manual eviction hook if needed beyond LRU (e.g., for very large canvases with layers + effects)
        // LRU handles most; call this on memory pressure events
    }

    fun getMemoryUsageEstimate(): Int {
        // Rough estimate for UI/debug (tiles * 512*512*4 bytes)
        return tileCache.size() * tileSize * tileSize * 4
    }
}
