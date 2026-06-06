package com.socreate.rendering

import android.opengl.GLES30.*
import android.util.LruCache

class TileManager(private val tileSize: Int = 512) {

    data class TileCoord(val x: Int, val y: Int)

    private val tileCache = LruCache<TileCoord, Int>(64) // Max 64 tiles in GPU memory

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
    }

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

    // Helper to calculate which tiles are visible (called from renderer or ViewModel)
    fun getVisibleTiles(
        screenWidth: Int, screenHeight: Int,
        panX: Float, panY: Float,
        zoom: Float,
        canvasWidth: Int = 1920, canvasHeight: Int = 1080
    ): List<TileCoord> {
        val left = (-panX / zoom).toInt().coerceAtLeast(0)
        val right = left + (screenWidth / zoom).toInt()
        val top = (-panY / zoom).toInt().coerceAtLeast(0)
        val bottom = top + (screenHeight / zoom).toInt()

        val startX = (left / tileSize).coerceAtLeast(0)
        val endX = ((right / tileSize) + 1).coerceAtMost((canvasWidth / tileSize) + 1)
        val startY = (top / tileSize).coerceAtLeast(0)
        val endY = ((bottom / tileSize) + 1).coerceAtMost((canvasHeight / tileSize) + 1)

        return (startX..endX).flatMap { x ->
            (startY..endY).map { y -> TileCoord(x, y) }
        }
    }
}
