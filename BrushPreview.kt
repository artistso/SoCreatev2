package com.socreate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.socreate.model.Brush

@Composable
fun BrushPreview(
    brush: Brush,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val displaySize = brush.size.coerceIn(4f, 60f)
    val alpha = brush.opacity

    Canvas(modifier = modifier.size(size)) {
        val center = this.center
        val radius = displaySize / 2f

        // Outer ring for size reference
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Brush stamp preview (soft circle)
        drawCircle(
            color = Color(brush.color).copy(alpha = alpha),
            radius = radius * 0.9f,
            center = center
        )

        // Inner highlight for "soft" feel
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.3f),
            radius = radius * 0.4f,
            center = center
        )
    }
}
