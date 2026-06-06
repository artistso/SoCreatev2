package com.socreate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.socreate.model.Frame

@Composable
fun TimelineBar(
    frames: List<Frame>,
    currentFrame: Int,
    onFrameSelected: (Int) -> Unit,
    onAddFrame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color(0xAA000000))
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(frames) { idx, frame ->
                FrameThumbnail(
                    frame = frame,
                    isSelected = idx == currentFrame,
                    onClick = { onFrameSelected(idx) }
                )
            }
        }
        Button(onClick = onAddFrame) { Text("+ Frame") }
    }
}

@Composable
fun FrameThumbnail(frame: Frame, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp, 40.dp)
            .background(if (isSelected) Color.Gray else Color.DarkGray)
            .clickable { onClick() }
    ) {
        // In Phase 1, just show placeholder. Later: actual thumbnail render.
        Text(
            "${frame.durationMs}ms",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White
        )
    }
}
