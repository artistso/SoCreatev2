package com.socreate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.socreate.model.BlendMode
import com.socreate.model.Layer
import kotlin.math.*

/**
 * Circular / Spiral Layer Panel - unique UI from original vision.
 * Layers shown as colored circles.
 * Long press on a circle to "unwind" into a spiral menu for properties (opacity, blend, etc.).
 * Drag circles to reorder.
 */
@Composable
fun CircularLayerPanel(
    layers: List<Layer>,
    currentLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onBlendModeChanged: (Int, BlendMode) -> Unit,
    onOpacityChanged: (Int, Float) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedLayer by remember { mutableStateOf<Int?>(null) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .background(Color(0xAA000000), shape = MaterialTheme.shapes.medium)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Layers (Circular)", color = Color.White, style = MaterialTheme.typography.labelSmall)

        Box(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Find which circle was touched for drag reorder
                            val idx = findClosestLayerIndex(offset, layers.size)
                            dragIndex = idx
                        },
                        onDrag = { change, dragAmount ->
                            // Simple reorder logic (can be improved with actual positions)
                            if (dragIndex != null) {
                                val newIdx = findClosestLayerIndex(change.position, layers.size)
                                if (newIdx != dragIndex) {
                                    onReorder(dragIndex!!, newIdx)
                                    dragIndex = newIdx
                                }
                            }
                        },
                        onDragEnd = {
                            dragIndex = null
                        }
                    )
                }
        ) {
            layers.forEachIndexed { index, layer ->
                val isCurrent = index == currentLayerIndex
                val isExpanded = expandedLayer == index

                // Position in a vertical column that "spirals" when expanded
                val baseY = 30.dp * index + 10.dp
                val spiralOffset = if (isExpanded) (index * 15).dp else 0.dp
                val xOffset = if (isExpanded) (sin(index * 0.8) * 30).dp else 0.dp

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = baseY + spiralOffset)
                        .size(if (isExpanded) 48.dp else 36.dp)
                        .clip(CircleShape)
                        .background(
                            color = when (layer.blendMode) {
                                BlendMode.NORMAL -> Color(0xFF4CAF50)
                                BlendMode.MULTIPLY -> Color(0xFF2196F3)
                                BlendMode.SCREEN -> Color(0xFFFFEB3B)
                                BlendMode.OVERLAY -> Color(0xFF9C27B0)
                            }.copy(alpha = layer.opacity)
                        )
                        .clickable {
                            onLayerSelected(index)
                            expandedLayer = if (isExpanded) null else index
                        }
                        .border(
                            width = if (isCurrent) 3.dp else 1.dp,
                            color = if (isCurrent) Color.White else Color.Gray,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Spiral expanded menu for properties
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .offset(x = xOffset + 60.dp, y = baseY + spiralOffset)
                            .background(Color(0xCC333333), shape = MaterialTheme.shapes.small)
                            .padding(4.dp)
                            .width(120.dp)
                    ) {
                        Text("Layer ${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        
                        // Opacity slider
                        Text("Opacity: ${(layer.opacity * 100).toInt()}%", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = layer.opacity,
                            onValueChange = { onOpacityChanged(index, it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(20.dp)
                        )

                        // Blend mode buttons (circular feel)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BlendMode.values().forEach { mode ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (layer.blendMode == mode) Color(0xFF4CAF50) else Color.Gray
                                        )
                                        .clickable { onBlendModeChanged(index, mode) }
                                ) {
                                    Text(
                                        mode.name.take(1),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onAddLayer, modifier = Modifier.fillMaxWidth()) {
            Text("+ Layer (Circular)")
        }
    }
}

private fun findClosestLayerIndex(offset: Offset, layerCount: Int): Int {
    val y = offset.y
    val index = (y / 40).toInt().coerceIn(0, layerCount - 1)
    return index
}
