package com.socreate.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView.Renderer
import android.opengl.GLUtils
import com.socreate.model.BlendMode
import com.socreate.model.Brush
import com.socreate.model.Layer
import com.socreate.model.Project
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SoCreateRenderer(
    private val context: Context,
    private val tileManager: TileManager
) : Renderer {

    // View transform state (updated by ViewModel)
    var panX: Float = 0f
    var panY: Float = 0f
    var zoom: Float = 1f

    // Current state
    var currentBrush: Brush = Brush.default()
    var currentColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
    var showOnionSkin = false
    var onionOpacity = 0.3f
    var currentProject: Project? = null
    var showGrid = false

    // For perspective
    var perspectiveGuidesEnabled = false
    var vanishingPoints: MutableList<Pair<Float, Float>> = mutableListOf(Pair(960f, 200f))

    private var tileProgram = 0
    private var brushTexture = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Quad for drawing tiles (unit square -1 to 1)
    private lateinit var quadVertexBuffer: FloatBuffer
    private var quadVao = 0

    // Dirty tiles that need texture re-upload
    private val dirtyTiles = mutableSetOf<TileManager.TileCoord>()

    // Camera matrices (column-major for OpenGL)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0.95f, 0.95f, 0.97f, 1f) // light paper background

        tileProgram = createTileProgram()

        brushTexture = loadBrushTexture("brushes/round_soft.png")

        setupQuad()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        glViewport(0, 0, width, height)

        // Update projection when viewport changes
        updateProjectionMatrix()
    }

    private fun updateProjectionMatrix() {
        // Orthographic projection: left, right, bottom, top, near, far
        // We work in world pixels. Canvas center at (0,0) for simplicity.
        val aspect = viewportWidth.toFloat() / viewportHeight
        val halfHeight = (viewportHeight / 2f) / zoom
        val halfWidth = halfHeight * aspect

        val left = -halfWidth
        val right = halfWidth
        val bottom = -halfHeight
        val top = halfHeight

        // Simple ortho matrix (column major)
        projectionMatrix.fill(0f)
        projectionMatrix[0] = 2f / (right - left)
        projectionMatrix[5] = 2f / (top - bottom)
        projectionMatrix[10] = -1f
        projectionMatrix[12] = -(right + left) / (right - left)
        projectionMatrix[13] = -(top + bottom) / (top - bottom)
        projectionMatrix[15] = 1f
    }

    private fun updateViewMatrix() {
        // View matrix: translate by -pan, then scale by zoom (but zoom is handled in proj for simplicity here)
        // For pure 2D pan, we translate the world.
        viewMatrix.fill(0f)
        viewMatrix[0] = 1f
        viewMatrix[5] = 1f
        viewMatrix[10] = 1f
        viewMatrix[12] = -panX
        viewMatrix[13] = -panY
        viewMatrix[15] = 1f
    }

    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT)

        val project = currentProject ?: return
        val currentFrame = project.frames.getOrNull(project.currentFrameIndex) ?: return

        updateViewMatrix()

        // Draw all layers in the current frame (bottom to top)
        currentFrame.layers.forEachIndexed { index, layer ->
            val alpha = layer.opacity
            drawLayer(layer, alpha)
        }

        // Onion skinning (previous frame) - draw on top with reduced opacity
        if (showOnionSkin) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            val prevFrame = project.frames.getOrNull(project.currentFrameIndex - 1)
            prevFrame?.layers?.forEach { prevLayer ->
                drawLayer(prevLayer, onionOpacity * 0.6f)  // slightly more transparent for onion
            }
            glDisable(GL_BLEND)
        }

        // Optional grid/guides
        if (showGrid) {
            // Simple grid lines (drawn in world space via current matrices, but for simplicity use GL lines)
            // Note: For full accuracy this should be in a separate shader pass, but this gives visual guides
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            // We can draw grid using immediate mode style or pre-defined lines.
            // For Phase 1 polish, draw a simple 100px grid using GL_LINES (basic)
            drawSimpleGrid()
            glDisable(GL_BLEND)
        }

        // Perspective guides with draggable vanishing points + snap
        if (perspectiveGuidesEnabled && vanishingPoints.isNotEmpty()) {
            drawPerspectiveGuides()
        }
    }

    private fun drawSimpleGrid() {
        // Basic grid using GL lines - 100 unit spacing
        glUseProgram(tileProgram) // reuse for simplicity
        glLineWidth(1f)

        // Draw horizontal and vertical lines at 100px intervals
        // This is approximate; in production use a dedicated grid shader
        val gridColor = floatArrayOf(0.7f, 0.7f, 0.7f, 0.4f)
        // For demo, we skip full matrix application here and draw in screen space approximation
        // (full implementation would calculate visible world range and draw lines)
        // Placeholder: draw a few lines
        // In real use, expand this to loop over visible tiles and draw grid within each
    }

    private fun drawPerspectiveGuides() {
        // Polished VP guides: converging lines for perspective (interactive draggable in Compose overlays in CanvasScreen; this is GL visual aid)
        // Full implementation would use a dedicated guide shader/VBO with current projection/view matrices to draw lines from each VP.
        // For now, stub to avoid GL errors; snapping logic in ViewModel.
        glLineWidth(1.5f)
    }

    private fun drawLayer(layer: Layer, alpha: Float) {
        val visibleTiles = tileManager.getVisibleTiles(
            viewportWidth, viewportHeight, panX, panY, zoom,
            currentProject?.canvasWidth ?: 1920, currentProject?.canvasHeight ?: 1080
        )

        glUseProgram(tileProgram)

        // Pass matrices
        val projLoc = glGetUniformLocation(tileProgram, "uProjection")
        val viewLoc = glGetUniformLocation(tileProgram, "uView")
        val alphaLoc = glGetUniformLocation(tileProgram, "uAlpha")

        glUniformMatrix4fv(projLoc, 1, false, projectionMatrix, 0)
        glUniformMatrix4fv(viewLoc, 1, false, viewMatrix, 0)
        glUniform1f(alphaLoc, alpha)

        // Apply blend mode (approximation with glBlendFunc for common modes)
        when (layer.blendMode) {
            BlendMode.NORMAL -> {
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.MULTIPLY -> {
                glBlendFunc(GL_DST_COLOR, GL_ZERO)
            }
            BlendMode.SCREEN -> {
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_COLOR)
            }
            BlendMode.OVERLAY -> {
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA) // fallback
            }
        }

        for (tileCoord in visibleTiles) {
            val tilePixels = layer.getTile(tileCoord.x, tileCoord.y) ?: continue

            // Refined: Only upload dirty tiles or those not yet in GPU (perf + memory)
            if (dirtyTiles.contains(tileCoord) || tileManager.getDirtyTiles().contains(tileCoord)) {
                tileManager.writeToTile(tileCoord, tilePixels)
                dirtyTiles.remove(tileCoord)
            }

            val texId = tileManager.getTileTexture(tileCoord)
            drawTexturedQuad(texId, tileCoord.x, tileCoord.y)
        }

        // Reset to normal blend
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawTexturedQuad(texId: Int, tileX: Int, tileY: Int) {
        glBindTexture(GL_TEXTURE_2D, texId)

        // Model offset for this tile (translate in world space)
        val tileWorldX = tileX * 512f
        val tileWorldY = tileY * 512f

        val modelLoc = glGetUniformLocation(tileProgram, "uModelOffset")
        glUniform2f(modelLoc, tileWorldX, tileWorldY)

        glBindVertexArray(quadVao)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glBindVertexArray(0)
    }

    fun drawBrushStamp(x: Float, y: Float, pressure: Float, brush: Brush) {
        markTileDirty((x / 512).toInt(), (y / 512).toInt())
        requestRender()
    }

    fun markTileDirty(tileX: Int, tileY: Int) {
        dirtyTiles.add(TileManager.TileCoord(tileX, tileY))
    }

    private fun setupQuad() {
        val vertices = floatArrayOf(
            -1f, -1f,   // bottom-left
             1f, -1f,   // bottom-right
            -1f,  1f,   // top-left
             1f,  1f    // top-right
        )

        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
        quadVertexBuffer = bb.asFloatBuffer()
        quadVertexBuffer.put(vertices)
        quadVertexBuffer.position(0)

        val vaoIds = IntArray(1)
        glGenVertexArrays(1, vaoIds, 0)
        quadVao = vaoIds[0]
        glBindVertexArray(quadVao)

        val vboIds = IntArray(1)
        glGenBuffers(1, vboIds, 0)
        val vbo = vboIds[0]
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, quadVertexBuffer, GL_STATIC_DRAW)

        val posLoc = 0
        glEnableVertexAttribArray(posLoc)
        glVertexAttribPointer(posLoc, 2, GL_FLOAT, false, 0, 0)

        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    private fun createTileProgram(): Int {
        val vertexSrc = """
            #version 300 es
            layout(location = 0) in vec2 aPos;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform vec2 uModelOffset;

            out vec2 vTexCoord;

            void main() {
                // Quad is -1..1 representing one 512x512 tile
                vec2 local = aPos * 512.0;
                vec2 worldPos = local + uModelOffset;

                // Apply view (pan) then projection
                vec4 pos = uProjection * uView * vec4(worldPos, 0.0, 1.0);
                gl_Position = pos;

                vTexCoord = aPos * 0.5 + 0.5;
            }
        """.trimIndent()

        val fragSrc = """
            #version 300 es
            precision highp float;
            uniform sampler2D uTexture;
            uniform float uAlpha;
            in vec2 vTexCoord;
            out vec4 FragColor;
            void main() {
                vec4 tex = texture(uTexture, vTexCoord);
                FragColor = vec4(tex.rgb, tex.a * uAlpha);
            }
        """.trimIndent()

        val vs = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vs, vertexSrc)
        glCompileShader(vs)

        val fs = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fs, fragSrc)
        glCompileShader(fs)

        val prog = glCreateProgram()
        glAttachShader(prog, vs)
        glAttachShader(prog, fs)
        glLinkProgram(prog)

        glDeleteShader(vs)
        glDeleteShader(fs)
        return prog
    }

    private fun loadBrushTexture(assetPath: String): Int {
        return try {
            val input = context.assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            val textureIds = IntArray(1)
            glGenTextures(1, textureIds, 0)
            val texId = textureIds[0]
            glBindTexture(GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            bitmap.recycle()
            texId
        } catch (e: Exception) {
            0
        }
    }

    fun requestRender() {
        // Called from outside; actual request is on the GLSurfaceView
    }

    fun updateZoom(newZoom: Float) {
        // Call this when zoom changes so projection is recalculated
        zoom = newZoom
        updateProjectionMatrix()
    }
}
