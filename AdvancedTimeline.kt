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
 * AdvancedTimeline (Phase 2 polish stub + further integration).
 * Builds on basic TimelineBar with:
 * - Waveform visualization (stub for audio scrubbing; real would sample from MediaPlayer PCM but local only).
 * - Gesture drag on thumbnails for exposure delta (calls adjust).
 * - Simple X-sheet column (notes stub, camera move indicators).
 * - Scrub bar with time.
 * Integrates with circular panels for playback controls.
 * 100% local, uses Compose Canvas + gestures.
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
    modifier: Modifier = Modifier
) {
    var waveformSeed by remember { mutableStateOf(0) } // for fake wave anim

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xAA000000), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(4.dp)
    ) {
        // Waveform stub (visual only; updates on play/scrub)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF111111))
                .pointerInput(playbackTimeMs) {
                    detectDragGestures { change, _ ->
                        val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                        // Rough total duration stub (assume 1000ms per frame avg)
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
                val numSamples = 40
                val amp = h * 0.4f
                path.moveTo(0f, h / 2)
                for (i in 0..numSamples) {
                    val x = i * w / numSamples
                    val phase = (i + (playbackTimeMs / 50) % 20) * 0.6f
                    val y = h / 2 + amp * kotlin.math.sin(phase) * (if (isPlaying) 1f else 0.6f)
                    path.lineTo(x, y)
                }
                drawPath(path, Color(0xFF00FFAA), style = Stroke(width = 2f))

                // Playhead
                val playX = if (frames.isNotEmpty()) {
                    val total = frames.sumOf { it.durationMs }.coerceAtLeast(1)
                    (playbackTimeMs.toFloat() / total * w).coerceIn(0f, w)
                } else 0f
                drawLine(Color.Red, Offset(playX, 0f), Offset(playX, h), strokeWidth = 2f)
            }
        }

        // Thumbnails row with gesture exposure adjust
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
                            onClick = { onFrameSelected(idx) },
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
        }

        // X-sheet stub row (notes, camera)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(Color(0xFF1A1A1A)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            frames.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (idx == currentFrame) Color(0xFF334455) else Color.Transparent)
                        .clickable { onFrameSelected(idx) }
                ) {
                    Text(
                        if (idx % 3 == 0) "Note" else if (idx % 5 == 0) "Cam" else "",
                        color = Color(0xFF88AAFF),
                        fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp),
                        modifier = Modifier.align(Alignment.Center)
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
