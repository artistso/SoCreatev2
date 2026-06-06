package com.socreate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.socreate.engine.BrushEngine
import com.socreate.engine.UndoManager
import com.socreate.model.Brush
import com.socreate.model.Project
import com.socreate.persistence.FileManager
import com.socreate.rendering.SoCreateRenderer
import com.socreate.rendering.TileManager

class CanvasViewModel(application: Application) : AndroidViewModel(application) {

    val tileManager = TileManager()
    // Note: Context is passed via Application for renderer if needed for assets
    val renderer = SoCreateRenderer(application.applicationContext, tileManager)
    val brushEngine = BrushEngine(renderer)
    private val undoManager = UndoManager()
    private val fileManager = FileManager(application.applicationContext)

    var project = Project()
        private set

    var currentBrush = Brush.default()
        private set

    var showOnionSkin = false
        private set

    init {
        // Link project to renderer for drawing
        renderer.currentProject = project
        renderer.currentBrush = currentBrush

        // Load last project if exists, or create default
        val projects = fileManager.getAllProjects()
        if (projects.isNotEmpty()) {
            fileManager.loadProject(projects.first())?.let {
                project = it
                renderer.currentProject = project
            }
        }
    }

    fun updateBrushSize(size: Float) {
        currentBrush = currentBrush.copy(size = size)
        renderer.currentBrush = currentBrush
    }

    fun updateBrushOpacity(opacity: Float) {
        currentBrush = currentBrush.copy(opacity = opacity)
        renderer.currentBrush = currentBrush
    }

    fun undo() {
        undoManager.undo()
        renderer.requestRender()
    }

    fun redo() {
        undoManager.redo()
        renderer.requestRender()
    }

    fun toggleOnionSkin() {
        showOnionSkin = !showOnionSkin
        renderer.showOnionSkin = showOnionSkin
        renderer.requestRender()
    }

    fun selectFrame(index: Int) {
        project.currentFrameIndex = index.coerceIn(0, project.frames.size - 1)
        renderer.currentProject = project
        renderer.requestRender()
    }

    fun addFrame() {
        project.frames.add(com.socreate.model.Frame())
        selectFrame(project.frames.size - 1)
    }

    fun saveProject() {
        fileManager.saveProject(project, project.name.replace(" ", "_"))
    }

    // TODO: Add more methods for flood fill, etc. as Phase 1 is expanded
    // Also integrate pan/zoom state for tiled canvas
}
