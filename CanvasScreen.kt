package com.socreate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.socreate.engine.BrushEngine
import com.socreate.rendering.SoCreateSurfaceView
import com.socreate.viewmodel.CanvasViewModel

@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    var showColorPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main OpenGL Canvas with pan/zoom + drawing
        AndroidView(
            factory = { ctx ->
                SoCreateSurfaceView(
                    ctx,
                    viewModel.renderer,
                    viewModel.brushEngine,
                    viewModel
                ).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top toolbar (global actions - many tools now in circular panels; kept minimal for quick access)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color(0xAA000000), shape = MaterialTheme.shapes.medium)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.undo() }) { Text("Undo") }
            Button(onClick = { viewModel.redo() }) { Text("Redo") }
            Button(onClick = { viewModel.toggleOnionSkin() }) {
                Text(if (viewModel.showOnionSkin) "Onion ✓" else "Onion")
            }
            Button(onClick = { viewModel.saveProject() }) { Text("Save") }
        }

        // Full circular/spiral collapsible tool panels (core UI vision + further circular UI this step)
        // Deeper brush preview, full spiral clustering, haptic feedback integrated here.
        // Replaces fixed sidebars; 5 satellites with spiral/cyclical children + magnetic clustering.
        CircularToolPanels(viewModel = viewModel)

        // Phase 2 Advanced Timeline (polish: waveforms, full X-sheet with notes/camera, audio export)
        AdvancedTimeline(
            frames = viewModel.project.frames,
            currentFrame = viewModel.currentPlaybackFrame,
            playbackTimeMs = viewModel.playbackTimeMs,
            isPlaying = viewModel.isPlaying,
            onFrameSelected = { viewModel.selectFrame(it) },
            onFrameDurationChanged = { idx, delta -> viewModel.adjustFrameExposure(idx, delta) },
            onAddFrame = { viewModel.addFrame() },
            onScrub = { viewModel.scrubToTime(it) },
            hasAudio = viewModel.audioPath != null,
            onExportAudio = { viewModel.exportAudioWithProject() },
            frameNotes = viewModel.frameNotes,
            onNoteChanged = { idx, note -> viewModel.updateFrameNote(idx, note) },
            frameCameraMoves = viewModel.frameCameraMoves,
            onCameraMoveChanged = { idx, move -> viewModel.updateFrameCameraMove(idx, move) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Playback controls (kept compact; full controls now also in circular Playback panel)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color(0xAA000000))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.togglePlayback() }) {
                Text(if (viewModel.isPlaying) "⏸" else "▶")
            }
            Button(onClick = { viewModel.scrubToTime(0) }) {
                Text("⟲")
            }
            // Audio import stub (in real use, launch file picker and call viewModel.importAudio(uri))
            Button(onClick = { /* TODO: Launch file picker for audio */ }) {
                Text("♪")
            }
        }

        // Color picker now triggered from circular Color panel (expand > swatches + preview)

        // Draggable perspective vanishing points overlay (with snap) - visual guides also in renderer
        if (viewModel.perspectiveGuidesEnabled) {
            viewModel.vanishingPoints.forEachIndexed { idx, (vx, vy) ->
                // Convert world to screen approx for overlay (uses rough center; refined in future with actual viewport center)
                val centerX = 540f
                val centerY = 960f
                val screenX = (vx - viewModel.panX) * viewModel.zoom + centerX
                val screenY = (vy - viewModel.panY) * viewModel.zoom + centerY
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(screenX.toInt().coerceIn(0, 2000), screenY.toInt().coerceIn(0, 2000)) }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.7f))
                        .border(2.dp, Color.White, CircleShape)
                        .pointerInput(idx) {
                            detectDragGestures { change, drag ->
                                val newWorldX = (change.position.x - centerX) / viewModel.zoom + viewModel.panX
                                val newWorldY = (change.position.y - centerY) / viewModel.zoom + viewModel.panY
                                viewModel.updateVanishingPoint(idx, newWorldX, newWorldY)
                                change.consume()
                            }
                        }
                )
            }
        }
    }
}
