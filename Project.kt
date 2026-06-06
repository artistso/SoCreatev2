package com.socreate.model

import java.util.UUID

data class Project(
    val id: UUID = UUID.randomUUID(),
    var name: String = "Untitled",
    val frames: MutableList<Frame> = mutableListOf(Frame()),
    var currentFrameIndex: Int = 0,
    var canvasWidth: Int = 1920,
    var canvasHeight: Int = 1080,
    var fps: Int = 24
)
