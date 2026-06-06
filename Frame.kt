package com.socreate.model

import java.util.UUID

data class Frame(
    val id: UUID = UUID.randomUUID(),
    val layers: MutableList<Layer> = mutableListOf(Layer()),
    var durationMs: Int = 100,
    var isVisible: Boolean = true
)
