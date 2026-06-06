package com.socreate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.socreate.model.Frame

/**
 * AdvancedTimeline (Phase 2 polish: waveforms, full X-sheet, audio export).
 * Builds on basic + prior stub with:
 * - Improved waveform visualization (dynamic, hasAudio-aware color/amp for real scrubbing feel; local only, no PCM sampling needed).
 * - Gesture drag on thumbnails for exposure delta.
 * - Full X-sheet: multi-row (Exposure editable via drag, Notes (per-frame editable via selector + TextField), Camera moves stub).
 * - Audio export button (wires to VM for project+audio sidecar polish).
 * - Scrub bar with time + playhead.
 * Integrates with circular panels for playback controls.
 * 100% local, uses Compose Canvas + gestures + basic TextField. No AI/cloud.
 */
@Composable
fun AdvancedTimeline(
    frames: List<Frame>,
    currentFrame: Int,
    playbackTimeMs: Int,
    isPlaying: Boolean,
    onFrameSelected: (Int) -> Unit,
    onFrameDurationChanged: (Int, Int) -> Unit,
    onAddFrame: () -> Unit,
    onScrub: (Int) -> Unit,
    // Phase 2 polish additions
    hasAudio: Boolean = false,
    onExportAudio: () -> Unit = {},
    frameNotes: List<String> = emptyList(),
    onNoteChanged: (Int, String) -> Unit = { _, _ -> },
    frameCameraMoves: List<String> = emptyList(),
    onCameraMoveChanged: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var waveformSeed by remember { mutableStateOf(0) } // for fake wave anim

    // Local polish state for X-sheet editing (notes/camera per frame, persists via callbacks to VM)
    var editingNoteIdx by remember { mutableStateOf(currentFrame) }
    var currentNoteText by remember(editingNoteIdx, frameNotes) { 
        mutableStateOf(frameNotes.getOrNull(editingNoteIdx) ?: "") 
    }
    var currentCameraText by remember(editingNoteIdx, frameCameraMoves) {
        mutableStateOf(frameCameraMoves.getOrNull(editingNoteIdx) ?: "pan:0,0 zoom:1")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xAA000000), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(4.dp)
    ) {
        // Polished waveform (Phase 2): dynamic, hasAudio aware (different color/amp for "real" audio feel), scrub + playhead
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFF111111))
                .pointerInput(playbackTimeMs, hasAudio) {
                    detectDragGestures { change, _ ->
                        val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                        val totalEst = frames.sumOf { it.durationMs }.coerceAtLeast(1000)
                        onScrub((progress * totalEst).toInt())
                        change.consume()
                    }
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path = Path()
                val numSamples = 48
                val baseAmp = h * 0.42f
                val amp = if (hasAudio) baseAmp * 1.15f else baseAmp * 0.75f
                val waveColor = if (hasAudio) Color(0xFF00FFCC) else Color(0xFF00FFAA)
                path.moveTo(0f, h / 2)
                for (i in 0..numSamples) {
                    val x = i * w / numSamples
                    val phase = (i + (playbackTimeMs / 40) % 24) * (if (hasAudio) 0.75f else 0.55f)
                    val playMod = if (isPlaying) 1.0f else 0.55f
                    val y = h / 2 + amp * kotlin.math.sin(phase) * playMod * (if (hasAudio && isPlaying) 1.1f else 1f)
                    path.lineTo(x, y)
                }
                drawPath(path, waveColor, style = Stroke(width = if (hasAudio) 2.5f else 2f))

                // Playhead
                val playX = if (frames.isNotEmpty()) {
                    val total = frames.sumOf { it.durationMs }.coerceAtLeast(1)
                    (playbackTimeMs.toFloat() / total * w).coerceIn(0f, w)
                } else 0f
                drawLine(Color.Red, Offset(playX, 0f), Offset(playX, h), strokeWidth = 2.5f)
                if (hasAudio) {
                    drawLine(Color(0xFF00FFCC).copy(alpha = 0.4f), Offset(playX - 1, 0f), Offset(playX - 1, h), strokeWidth = 1f)
                }
            }
        }

        // Thumbnails + exposure + export (polish)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(frames) { idx, frame ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FrameThumbnailAdvanced(
                            frame = frame,
                            isSelected = idx == currentFrame,
                            onClick = { onFrameSelected(idx); editingNoteIdx = idx },
                            onDragAdjust = { delta -> onFrameDurationChanged(idx, delta) }
                        )
                        Text(
                            "${frame.durationMs}ms",
                            color = Color.LightGray,
                            fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
                    }
                }
            }
            Button(onClick = onAddFrame, modifier = Modifier.padding(start = 4.dp)) {
                Text("+", fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            Button(onClick = onExportAudio, modifier = Modifier.padding(start = 4.dp)) {
                Text("Export ♪", fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
        }

        // Full X-sheet (Phase 2 polish): Exposure (via thumbnails), Notes (editable), Camera moves
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(2.dp)
        ) {
            // Notes row (full X-sheet)
            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                frames.forEachIndexed { idx, _ ->
                    val note = frameNotes.getOrNull(idx)?.take(4) ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (idx == currentFrame) Color(0xFF445566) else Color(0xFF222222))
                            .clickable { 
                                editingNoteIdx = idx
                                currentNoteText = frameNotes.getOrNull(idx) ?: ""
                            }
                    ) {
                        Text(
                            note.ifEmpty { if (idx % 4 == 0) "Note" else "" },
                            color = if (hasAudio) Color(0xFF88FFAA) else Color(0xFF88AAFF),
                            fontSize = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Camera moves row (full X-sheet stub)
            Row(
                modifier = Modifier.fillMaxWidth().height(18.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                frames.forEachIndexed { idx, _ ->
                    val cam = frameCameraMoves.getOrNull(idx)?.take(5) ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (idx == currentFrame) Color(0xFF334455) else Color.Transparent)
                            .clickable { 
                                editingNoteIdx = idx
                                currentCameraText = frameCameraMoves.getOrNull(idx) ?: "pan:0,0 zoom:1"
                            }
                    ) {
                        Text(
                            cam.ifEmpty { if (idx % 3 == 0) "Cam" else "" },
                            color = Color(0xFFFFCC88),
                            fontSize = androidx.compose.ui.unit.TextUnit(5.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Selected frame note + camera editor (deeper X-sheet polish)
            if (frames.isNotEmpty() && editingNoteIdx in frames.indices) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Note[${editingNoteIdx}]: ", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
                    androidx.compose.material3.TextField(
                        value = currentNoteText,
                        onValueChange = { 
                            currentNoteText = it
                            onNoteChanged(editingNoteIdx, it)
                        },
                        modifier = Modifier.weight(1f).height(28.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                    )
                    Text(" Cam: ", color = Color(0xFFFFCC88), fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
                    androidx.compose.material3.TextField(
                        value = currentCameraText,
                        onValueChange = { 
                            currentCameraText = it
                            onCameraMoveChanged(editingNoteIdx, it)
                        },
                        modifier = Modifier.width(90.dp).height(28.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
                    )
                }
            }
        }
    }
}

@Composable
fun FrameThumbnailAdvanced(
    frame: Frame,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDragAdjust: (Int) -> Unit
) {
    var dragStartY by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .size(48.dp, 32.dp)
            .background(if (isSelected) Color(0xFF556677) else Color(0xFF333333), shape = RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragStartY = it.y },
                    onDrag = { change, _ ->
                        val delta = ((change.position.y - dragStartY) / 10).toInt() * 10  // 10ms steps
                        if (delta != 0) {
                            onDragAdjust(delta)
                            dragStartY = change.position.y  // incremental
                        }
                        change.consume()
                    }
                )
            }
    ) {
        // Simple frame content stub (could be thumbnail render later)
        Text(
            "${frame.durationMs}",
            color = Color.White,
            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
