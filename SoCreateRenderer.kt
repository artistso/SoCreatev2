package com.socreate.rendering

import android.content.Context
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView.Renderer
import com.socreate.engine.StrokeInterpolator
import com.socreate.model.Brush
import com.socreate.model.Project
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SoCreateRenderer(
    private val context: Context,
    private val tileManager: TileManager
) : Renderer {

    private var brushProgram = 0
    private var compositeProgram = 0
    private var brushTexture = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Current drawing state - exposed for ViewModel
    var currentBrush: Brush = Brush.default()
    var currentColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
    var showOnionSkin = false
    var onionOpacity = 0.3f

    // Reference to current project for drawing tiles (set by ViewModel)
    var currentProject: Project? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(1f, 1f, 1f, 1f)
        brushProgram = BrushShader.loadBrushProgram(context)
        compositeProgram = BrushShader.loadCompositeProgram()
        brushTexture = loadTexture("brushes/round_soft.png")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT)

        val project = currentProject ?: return
        val currentFrame = project.frames.getOrNull(project.currentFrameIndex) ?: return

        // TODO: Full implementation - draw current frame's tiles using tileManager
        // For Phase 1 skeleton: basic placeholder
        // In real impl: calculate visible tiles from pan/zoom (add panX, panY, zoom state)
        // Draw each visible tile with proper transforms

        // Draw current layer tiles (stub)
        // visibleTiles.forEach { tileCoord ->
        //     val texId = tileManager.getTileTexture(tileCoord)
        //     // Bind and draw quad at correct world position
        // }

        // Onion skinning stub
        if (showOnionSkin) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            // TODO: Draw previous and next frame tiles with reduced opacity using compositeProgram
            // Similar loop for project.frames[project.currentFrameIndex - 1] etc.
            glDisable(GL_BLEND)
        }

        // Brush preview / current stroke would be drawn here if in progress
        // (handled via direct calls to drawBrushStamp from BrushEngine)
    }

    fun drawBrushStamp(x: Float, y: Float, pressure: Float, brush: Brush) {
        // This is called from BrushEngine during touch
        // In full impl, this would update the current frame's tile pixels via CPU or FBO
        // For now, stub: in real version, use FBO or direct texture update + stamp quad
        glUseProgram(brushProgram)
        // Set uniforms: uBrushTex, uColor, uOpacity, uSize (based on pressure), uOffset (x,y)
        // Draw quad or point sprite for the brush stamp
        // After stamp, mark affected tiles dirty and request re-render
    }

    private fun loadTexture(assetPath: String): Int {
        // TODO: Full implementation - load PNG from assets using BitmapFactory + GLUtils
        // Return texture ID. For skeleton, return 0 or a placeholder.
        // Example:
        // val bitmap = BitmapFactory.decodeStream(context.assets.open(assetPath))
        // Generate texture, glTexImage2D etc.
        return 0 // Placeholder
    }

    // Helper for ViewModel to force redraw (in full app, the GLSurfaceView.requestRender() is called from touch)
    // This is a no-op placeholder; integrate with actual SurfaceView reference in production
    fun requestRender() {
        // In real usage: the owning GLSurfaceView calls requestRender()
        // For skeleton, this is called after state changes like onion toggle
    }
}
