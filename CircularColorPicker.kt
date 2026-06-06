package com.socreate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun CircularColorPicker(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var value by remember { mutableStateOf(1f) }

    // Initialize from initialColor (simple HSV conversion)
    LaunchedEffect(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val currentColor = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Color Picker", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // Circular Hue Wheel
        Box(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val pos = change.position
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val angle = (atan2(dy, dx) * (180 / PI) + 360) % 360
                        hue = angle.toFloat()
                        change.consume()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 8f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Draw hue wheel as sweep gradient
                val colors = List(360) { i ->
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)))
                }
                val sweep = SweepGradient(
                    center.x, center.y,
                    colors.toTypedArray(),
                    null
                )

                drawCircle(
                    brush = Brush.sweepGradient(sweep.colors, sweep.colorStops),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 40f)
                )

                // Hue indicator
                val indicatorAngle = Math.toRadians(hue.toDouble())
                val indicatorX = center.x + cos(indicatorAngle) * (radius - 8f)
                val indicatorY = center.y + sin(indicatorAngle) * (radius - 8f)
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = Offset(indicatorX.toFloat(), indicatorY.toFloat()),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(indicatorX.toFloat(), indicatorY.toFloat())
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Saturation / Value controls (circular feel - inner preview + sliders for polish)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview circle
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(currentColor))
                    .border(2.dp, Color.White, CircleShape)
            )

            Column {
                Text("Saturation: ${(saturation * 100).toInt()}%")
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f
                )
                Text("Brightness: ${(value * 100).toInt()}%")
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { onColorSelected(currentColor) }) {
                Text("Select")
            }
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}
