package com.socreate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.socreate.model.Brush

@Composable
fun BrushSettingsPanel(
    brush: Brush,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Float) -> Unit,
    onColorChange: (Int) -> Unit = {}, // optional
    onPressureCurveUpdate: (List<Pair<Float, Float>>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Brush Size: ${brush.size.toInt()}px")
            Slider(
                value = brush.size,
                onValueChange = onBrushSizeChange,
                valueRange = 1f..200f
            )
            Text("Opacity: ${(brush.opacity * 100).toInt()}%")
            Slider(
                value = brush.opacity,
                onValueChange = onBrushOpacityChange,
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(8.dp))

            // Pressure curve with draggable Bézier points (Polish Phase 1)
            PressureCurveEditor(
                brush = brush,
                onCurveUpdated = onPressureCurveUpdate
            )
        }
    }
}
