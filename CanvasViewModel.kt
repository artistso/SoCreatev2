package com.socreate.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socreate.engine.BackgroundRenderQueue
import com.socreate.engine.BrushEngine
import com.socreate.engine.PixelEditor
import com.socreate.engine.UndoManager
import com.socreate.model.BlendMode
import com.socreate.model.Brush
import com.socreate.model.Layer
import com.socreate.model.Project
import com.socreate.persistence.FileManager
import com.socreate.rendering.SoCreateRenderer
import com.socreate.rendering.TileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CanvasViewModel(application: Application) : AndroidViewModel(application) {

    val tileManager = TileManager(maxTilesInMemory = 96) // Refined for better perf on mid-range devices
    val renderer = SoCreateRenderer(application.applicationContext, tileManager)
    private val pixelEditor = PixelEditor(application.applicationContext)
    val brushEngine = BrushEngine(renderer, pixelEditor)
    private val undoManager = UndoManager(maxHistory = 50) // Smart limited history
    private val fileManager = FileManager(application.applicationContext)
    private val backgroundQueue = BackgroundRenderQueue() // For pre-render, thumbnails, etc.

    var project = Project()
        private set

    var currentBrush = Brush.default()
        private set

    var showOnionSkin = false
        private set

    // Pan / Zoom state (world coordinates)
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set
    var zoom: Float = 1f
        private set

    // Current tool
    var currentTool: BrushEngine.Tool = BrushEngine.Tool.BRUSH
        private set

    // Grid / Guides (Polish Phase 1)
    var gridSnapEnabled: Boolean = false
        private set
    var gridSize: Float = 50f  // world units (pixels)

    // Advanced drawing: Symmetry (full radial/kaleidoscopic)
    var symmetryEnabled: Boolean = false
        private set
    var symmetryMode: String = "Mirror" // Mirror, Radial, Kaleidoscopic
    var symmetrySectors: Int = 4
    var symmetryCenterX: Float = 960f
    var symmetryCenterY: Float = 540f

    // Perspective vanishing points (draggable with snap)
    var perspectiveGuidesEnabled: Boolean = false
        private set
    var vanishingPoints: MutableList<Pair<Float, Float>> = mutableListOf(Pair(960f, 200f), Pair(200f, 540f)) // 1-2 points example
    var perspectiveSnapEnabled: Boolean = true

    // Liquify
    var liquifyStrength: Float = 1f
        private set

    // Layer management (basic multi-layer support)
    var currentLayerIndex: Int = 0
        private set

    // === Phase 2: Timeline & Animation ===
    var isPlaying: Boolean = false
        private set
    var playbackTimeMs: Int = 0  // cumulative time in animation
    var currentPlaybackFrame: Int = 0
        private set

    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    var audioPath: String? = null
        private set
    var audioDurationMs: Int = 0
        private set

    // === Phase 2 polish: waveforms, full X-sheet (notes + camera), audio export ===
    var frameNotes: MutableList<String> = mutableListOf()
        private set
    var frameCameraMoves: MutableList<String> = mutableListOf()
        private set
    var audioExported: Boolean = false
        private set

    init {
        renderer.currentProject = project
        renderer.currentBrush = currentBrush
        renderer.panX = panX
        renderer.panY = panY
        renderer.zoom = zoom

        updateCurrentLayer()
        syncPhase2State()

        // Load last project if exists
        val projects = fileManager.getAllProjects()
        if (projects.isNotEmpty()) {
            fileManager.loadProject(projects.first())?.let {
                project = it
                renderer.currentProject = project
                currentLayerIndex = 0
                updateCurrentLayer()
                syncPhase2State()
            }
        }
    }

    private fun syncPhase2State() {
        val numFrames = project.frames.size
        while (frameNotes.size < numFrames) frameNotes.add("")
        while (frameNotes.size > numFrames) frameNotes.removeAt(frameNotes.lastIndex)
        while (frameCameraMoves.size < numFrames) frameCameraMoves.add("pan:0,0 zoom:1")
        while (frameCameraMoves.size > numFrames) frameCameraMoves.removeAt(frameCameraMoves.lastIndex)
    }

    private fun updateCurrentLayer() {
        val frame = project.frames.getOrNull(project.currentFrameIndex)
        brushEngine.currentLayer = frame?.layers?.getOrNull(currentLayerIndex)
    }

    // ... (previous brush, tool, layer, pan/zoom, symmetry, grid functions remain the same - abbreviated for brevity)

    fun updateBrushSize(size: Float) {
        currentBrush = currentBrush.copy(size = size)
        renderer.currentBrush = currentBrush
    }

    fun updateBrushOpacity(opacity: Float) {
        currentBrush = currentBrush.copy(opacity = opacity)
        renderer.currentBrush = currentBrush
    }

    fun updateBrushColor(color: Int) {
        currentBrush = currentBrush.copy(color = color)
        renderer.currentBrush = currentBrush
    }

    fun updatePressureCurve(points: List<Pair<Float, Float>>) {
        currentBrush = currentBrush.copy(pressureCurvePoints = points.toMutableList())
        renderer.currentBrush = currentBrush
    }

    fun setTool(tool: BrushEngine.Tool) {
        currentTool = tool
        brushEngine.currentTool = tool
    }

    fun toggleGridSnap() {
        gridSnapEnabled = !gridSnapEnabled
    }

    fun setGridSize(size: Float) {
        gridSize = size.coerceAtLeast(10f)
    }

    fun toggleSymmetry() {
        symmetryEnabled = !symmetryEnabled
        brushEngine.symmetryEnabled = symmetryEnabled
        brushEngine.symmetryMode = symmetryMode
        brushEngine.symmetrySectors = symmetrySectors
    }

    fun setSymmetryMode(mode: String) {
        symmetryMode = mode
        brushEngine.symmetryMode = mode
    }

    fun setSymmetrySectors(sectors: Int) {
        symmetrySectors = sectors.coerceIn(2, 12)
        brushEngine.symmetrySectors = symmetrySectors
    }

    fun setSymmetryCenter(x: Float, y: Float) {
        brushEngine.symmetryCenterX = x
        brushEngine.symmetryCenterY = y
    }

    fun togglePerspectiveGuides() {
        perspectiveGuidesEnabled = !perspectiveGuidesEnabled
    }

    fun updateVanishingPoint(index: Int, x: Float, y: Float) {
        if (index in vanishingPoints.indices) {
            var nx = x
            var ny = y
            if (perspectiveSnapEnabled) {
                nx = (nx / 50).toInt() * 50f  // Snap to 50px grid
                ny = (ny / 50).toInt() * 50f
            }
            vanishingPoints[index] = Pair(nx, ny)
        }
    }

    fun setLiquifyStrength(strength: Float) {
        liquifyStrength = strength.coerceIn(0.1f, 5f)
        brushEngine.liquifyStrength = liquifyStrength
    }

    // === Layer management (basic) ===
    fun addLayer() {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return
        frame.layers.add(Layer())
        currentLayerIndex = frame.layers.size - 1
        updateCurrentLayer()
        renderer.requestRender()
    }

    fun selectLayer(index: Int) {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return
        if (index in 0 until frame.layers.size) {
            currentLayerIndex = index
            updateCurrentLayer()
            renderer.requestRender()
        }
    }

    fun getCurrentLayerCount(): Int {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return 1
        return frame.layers.size
    }

    // === Deeper layers/compositing (this step) ===
    fun setCurrentLayerOpacity(opacity: Float) {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return
        val layer = frame.layers.getOrNull(currentLayerIndex) ?: return
        layer.opacity = opacity.coerceIn(0f, 1f)
        renderer.requestRender()
    }

    fun setCurrentLayerBlendMode(mode: BlendMode) {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return
        val layer = frame.layers.getOrNull(currentLayerIndex) ?: return
        layer.blendMode = mode
        renderer.requestRender()
    }

    fun getCurrentLayerOpacity(): Float {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return 1f
        return frame.layers.getOrNull(currentLayerIndex)?.opacity ?: 1f
    }

    fun getCurrentLayerBlendMode(): BlendMode {
        val frame = project.frames.getOrNull(project.currentFrameIndex) ?: return BlendMode.NORMAL
        return frame.layers.getOrNull(currentLayerIndex)?.blendMode ?: BlendMode.NORMAL
    }

    fun getCurrentLayerBlendModes(): List<BlendMode> = BlendMode.values().toList()

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
        if (isPlaying) pausePlayback()
        project.currentFrameIndex = index.coerceIn(0, project.frames.size - 1)
        currentLayerIndex = 0
        renderer.currentProject = project
        updateCurrentLayer()
        playbackTimeMs = calculateCumulativeTimeUpTo(project.currentFrameIndex)
        currentPlaybackFrame = project.currentFrameIndex
        syncPhase2State()
        preRenderUpcomingFrames(3) // Performance further: pre-render for smooth scrubbing with layers/effects
        renderer.requestRender()
    }

    fun addFrame() {
        if (isPlaying) pausePlayback()
        project.frames.add(com.socreate.model.Frame())
        selectFrame(project.frames.size - 1)
        syncPhase2State()
    }

    fun updateFrameDuration(frameIndex: Int, newDurationMs: Int) {
        val frame = project.frames.getOrNull(frameIndex) ?: return
        frame.durationMs = newDurationMs.coerceAtLeast(10)
        if (!isPlaying) {
            renderer.requestRender()
        }
    }

    fun saveProject() {
        fileManager.saveProject(project, project.name.replace(" ", "_"))
    }

    // === Pan / Zoom ===
    fun setPan(x: Float, y: Float) {
        panX = x
        panY = y
        renderer.panX = panX
        renderer.panY = panY
        renderer.requestRender()
    }

    fun setZoom(z: Float) {
        zoom = z.coerceIn(0.1f, 20f)
        renderer.updateZoom(zoom)
        renderer.requestRender()
    }

    fun zoomBy(delta: Float, focusX: Float = 0f, focusY: Float = 0f) {
        val oldZoom = zoom
        zoom = (zoom * delta).coerceIn(0.1f, 20f)

        val focusWorldX = (focusX - viewportWidth / 2f) / oldZoom + panX
        val focusWorldY = (focusY - viewportHeight / 2f) / oldZoom + panY

        panX = focusWorldX - (focusX - viewportWidth / 2f) / zoom
        panY = focusWorldY - (focusY - viewportHeight / 2f) / zoom

        renderer.panX = panX
        renderer.panY = panY
        renderer.updateZoom(zoom)
        renderer.requestRender()
    }

    private var viewportWidth = 1080f
    private var viewportHeight = 1920f

    fun updateViewport(width: Float, height: Float) {
        viewportWidth = width
        viewportHeight = height
    }

    fun screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
        var worldX = (screenX - viewportWidth / 2f) / zoom + panX
        var worldY = (screenY - viewportHeight / 2f) / zoom + panY

        if (gridSnapEnabled) {
            worldX = (worldX / gridSize).toInt() * gridSize
            worldY = (worldY / gridSize).toInt() * gridSize
        }

        // Advanced drawing polish: VP line snapping (if perspective guides + snap enabled)
        if (perspectiveGuidesEnabled && perspectiveSnapEnabled && vanishingPoints.isNotEmpty()) {
            val (snappedX, snappedY) = snapToVanishingLines(worldX, worldY)
            worldX = snappedX
            worldY = snappedY
        }
        return Pair(worldX, worldY)
    }

    private fun snapToVanishingLines(wx: Float, wy: Float): Pair<Float, Float> {
        var bestDist = Float.MAX_VALUE
        var bestPoint = Pair(wx, wy)
        for ((vx, vy) in vanishingPoints) {
            // Snap to lines radiating from VP (simple: project to 4 main rays for polish)
            val rays = listOf(
                Pair(1f, 0f), Pair(-1f, 0f), Pair(0f, 1f), Pair(0f, -1f),
                Pair(1f, 1f), Pair(-1f, 1f), Pair(1f, -1f), Pair(-1f, -1f)
            )
            for ((dx, dy) in rays) {
                // Project point onto ray from VP
                val t = ((wx - vx) * dx + (wy - vy) * dy) / (dx * dx + dy * dy)
                val projX = vx + t * dx
                val projY = vy + t * dy
                val dist = kotlin.math.hypot(wx - projX, wy - projY)
                if (dist < bestDist && dist < 30f) {  // snap threshold
                    bestDist = dist
                    bestPoint = Pair(projX, projY)
                }
            }
        }
        return bestPoint
    }

    fun handleCanvasTouch(event: android.view.MotionEvent, viewWidth: Float, viewHeight: Float) {
        updateViewport(viewWidth, viewHeight)
        val (worldX, worldY) = screenToWorld(event.x, event.y)
        val pressure = event.getPressure(0)

        // Pass symmetry center
        brushEngine.symmetryCenterX = symmetryCenterX  // Assume we add var or use vanishing for demo
        brushEngine.symmetryCenterY = symmetryCenterY
        brushEngine.liquifyStrength = liquifyStrength

        if (currentTool == BrushEngine.Tool.FILL && event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            brushEngine.doFill(worldX.toInt(), worldY.toInt(), currentBrush.color)
        } else {
            brushEngine.processTouchEvent(event, worldX, worldY, pressure)
        }
    }

    // === Phase 2: Timeline & Animation ===

    private fun calculateCumulativeTimeUpTo(frameIndex: Int): Int {
        var time = 0
        for (i in 0 until frameIndex) {
            time += project.frames.getOrNull(i)?.durationMs ?: 0
        }
        return time
    }

    private fun findFrameAtTime(timeMs: Int): Int {
        var cumulative = 0
        project.frames.forEachIndexed { index, frame ->
            cumulative += frame.durationMs
            if (timeMs < cumulative) return index
        }
        return project.frames.size - 1
    }

    fun togglePlayback() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        if (isPlaying) return
        isPlaying = true
        playbackJob?.cancel()

        playbackJob = viewModelScope.launch {
            var startTime = System.currentTimeMillis() - playbackTimeMs
            while (isPlaying) {
                val elapsed = (System.currentTimeMillis() - startTime).toInt()
                playbackTimeMs = elapsed

                val newFrame = findFrameAtTime(elapsed)
                if (newFrame != currentPlaybackFrame) {
                    currentPlaybackFrame = newFrame
                    project.currentFrameIndex = newFrame
                    updateCurrentLayer()
                    renderer.currentProject = project
                    renderer.requestRender()
                }

                // Audio scrubbing sync
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        // Rough sync; for precise, use player.currentPosition
                    }
                }

                // Loop if needed
                val totalDuration = project.frames.sumOf { it.durationMs }
                if (playbackTimeMs >= totalDuration) {
                    playbackTimeMs = 0
                    startTime = System.currentTimeMillis()
                    currentPlaybackFrame = 0
                    project.currentFrameIndex = 0
                    updateCurrentLayer()
                    renderer.currentProject = project
                    renderer.requestRender()
                }

                delay(16) // ~60fps update
            }
        }

        // Start audio if loaded
        mediaPlayer?.start()
    }

    fun pausePlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        mediaPlayer?.pause()
        // Sync audio position
        mediaPlayer?.seekTo(playbackTimeMs)
    }

    fun scrubToTime(timeMs: Int) {
        playbackTimeMs = timeMs.coerceAtLeast(0)
        val newFrame = findFrameAtTime(playbackTimeMs)
        if (newFrame != currentPlaybackFrame) {
            currentPlaybackFrame = newFrame
            project.currentFrameIndex = newFrame
            updateCurrentLayer()
            renderer.currentProject = project
            renderer.requestRender()
        }

        mediaPlayer?.seekTo(playbackTimeMs)
    }

    fun importAudio(uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor)
                prepare()
                audioDurationMs = duration
            }
            audioPath = uri.toString()
            // Optional: start from current playback time
            mediaPlayer?.seekTo(playbackTimeMs)
        } catch (e: Exception) {
            // Handle error (e.g. log)
            mediaPlayer = null
        }
    }

    fun setAudioVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    // === Phase 2 polish functions ===
    fun updateFrameNote(frameIndex: Int, note: String) {
        if (frameIndex in frameNotes.indices) {
            frameNotes[frameIndex] = note
            // Persist lightly on save
        }
    }

    fun updateFrameCameraMove(frameIndex: Int, move: String) {
        if (frameIndex in frameCameraMoves.indices) {
            frameCameraMoves[frameIndex] = move
        }
    }

    fun exportAudioWithProject() {
        if (audioPath != null) {
            // Polish: mark exported, re-save project (in real: use FileManager + MediaMuxer or copy audio sidecar + embed ref in .socreate;
            // here pure local stub - audio file remains in internal storage, project updated with flag)
            audioExported = true
            saveProject()
            // Could launch file picker or write a companion .wav if we had PCM, but keep 100% local/no extra deps
            println("Phase 2 polish: Audio exported alongside project (stub - audioPath=$audioPath, exported=$audioExported)")
        }
    }

    fun getFrameNote(index: Int): String = frameNotes.getOrNull(index) ?: ""
    fun getFrameCameraMove(index: Int): String = frameCameraMoves.getOrNull(index) ?: "pan:0,0 zoom:1"

    // Gesture-based frame exposure (for timeline)
    fun adjustFrameExposure(frameIndex: Int, deltaMs: Int) {
        val frame = project.frames.getOrNull(frameIndex) ?: return
        frame.durationMs = (frame.durationMs + deltaMs).coerceAtLeast(10)
        if (!isPlaying) {
            renderer.requestRender()
        }
    }

    // === Performance: Background queue integration (further refined) ===
    var highPerfMode: Boolean = false
        private set

    fun setHighPerfMode(enabled: Boolean) {
        highPerfMode = enabled
        tileManager.setMaxTiles(if (enabled) 64 else 96)
        // Reduce history or effects if needed for perf
        if (enabled) {
            // e.g., limit undo further in real
        }
        renderer.requestRender()
    }

    fun preRenderUpcomingFrames(count: Int = 5) {
        val start = currentPlaybackFrame + 1
        backgroundQueue.preRenderFrames(start, count) { rendered ->
            // Cache or notify UI (e.g., thumbnails ready for timeline, pre-composited layers)
            println("Pre-rendered frames: $rendered (highPerf=$highPerfMode)")
        }
    }

    fun getPerformanceStats(): String {
        val memEst = tileManager.getMemoryUsageEstimate() / (1024 * 1024)
        return "Tiles mem ~${memEst}MB, HighPerf: $highPerfMode, Layers: ${getCurrentLayerCount()}"
    }

    // Smart undo push example (call from brush engine after stroke)
    // Note: Signature updated in UndoManager for sparse diffs; actual push happens inside BrushEngine/PixelEditor in full wiring.
    // This remains for future direct integration (currently commented in BrushEngine).
    fun pushSmartStrokeUndo(frameIndex: Int, layerIdx: Int, affected: Map<Pair<Int, Int>, Pair<IntArray, IntArray>>) {
        // Disabled for now to match current UndoManager.StrokeAction ctor (uses explicit before/after tile diffs).
        // In production: construct proper StrokeAction and undoManager.push(...)
        println("pushSmartStrokeUndo called (stub - no-op for current undo impl)")
    }

    fun getUndoDescriptions(): List<String> = undoManager.getUndoHistory()

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        backgroundQueue.shutdown()
        // Other cleanups...
    }
}
