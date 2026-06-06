package com.socreate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.socreate.model.Brush

/**
 * Draggable Bézier-style pressure curve editor (Polish Phase 1 feature).
 * Points are pairs (inputPressure 0-1, outputMultiplier 0-1). Used in BrushEngine for variable width/opacity.
 * Pure Compose, no external deps. Integrates with circular brush panel preview.
 */
@Composable
fun PressureCurveEditor(
    brush: Brush,
    onCurveUpdated: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var points by remember(brush) {
        mutableStateOf(
            if (brush.pressureCurvePoints.isNotEmpty()) brush.pressureCurvePoints
            else listOf(0f to 0f, 0.5f to 0.7f, 1f to 1f)
        )
    }

    Column(modifier = modifier) {
        Text("Pressure Curve (drag points)", fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp))

        Box(
            modifier = Modifier
                .size(200.dp, 120.dp)
                .background(Color(0xFF222222))
                .padding(4.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectDragGestures { change, _ ->
                            val pos = change.position
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val px = (pos.x / w).coerceIn(0f, 1f)
                            val py = (1f - pos.y / h).coerceIn(0f, 1f)  // y inverted for curve (0 bottom)

                            // Find closest point to drag (or add if far)
                            val idx = points.indices.minByOrNull { i ->
                                val p = points[i]
                                (p.first - px).let { it * it } + (p.second - py).let { it * it }
                            } ?: 0

                            val newPoints = points.toMutableList()
                            // Snap x a bit for ends
                            val newX = if (idx == 0) 0f else if (idx == newPoints.lastIndex) 1f else px
                            newPoints[idx] = newX to py
                            // Keep sorted by x
                            newPoints.sortBy { it.first }
                            points = newPoints
                            onCurveUpdated(points)
                            change.consume()
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Grid
                drawLine(Color.Gray, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                drawLine(Color.Gray, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 1f)

                // Curve path (linear interp for simplicity; could be cubic)
                val path = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].first * w, h - points[0].second * h)
                        for (i in 1 until points.size) {
                            lineTo(points[i].first * w, h - points[i].second * h)
                        }
                    }
                }
                drawPath(path, Color.Cyan, style = Stroke(width = 3f))

                // Points as draggable handles
                points.forEachIndexed { i, (px, py) ->
                    val cx = px * w
                    val cy = h - py * h
                    drawCircle(Color.White, radius = 8f, center = Offset(cx, cy))
                    drawCircle(Color(0xFF00AAFF), radius = 5f, center = Offset(cx, cy))
                }
            }
        }

        // Reset button
        androidx.compose.material3.Button(
            onClick = {
                val reset = listOf(0f to 0f, 0.5f to 0.7f, 1f to 1f)
                points = reset
                onCurveUpdated(reset)
            },
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
        ) {
            Text("Reset", fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
        }
    }
}
