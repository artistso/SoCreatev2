package com.socreate.model

import java.util.UUID

enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY }

data class Layer(
    val id: UUID = UUID.randomUUID(),
    var opacity: Float = 1f,
    var blendMode: BlendMode = BlendMode.NORMAL,
    val tiles: MutableMap<Pair<Int, Int>, IntArray> = mutableMapOf()
) {
    fun getTile(tileX: Int, tileY: Int): IntArray? = tiles[Pair(tileX, tileY)]
    fun setTile(tileX: Int, tileY: Int, pixels: IntArray) {
        tiles[Pair(tileX, tileY)] = pixels
    }
}
