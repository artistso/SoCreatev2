package com.socreate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.socreate.rendering.SoCreateSurfaceView
import com.socreate.viewmodel.CanvasViewModel

@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        // OpenGL Surface - the main canvas
        AndroidView(
            factory = { ctx ->
                SoCreateSurfaceView(ctx, viewModel.renderer, viewModel.brushEngine).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar with undo/redo and onion skin toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.undo() }) { Text("Undo") }
            Button(onClick = { viewModel.redo() }) { Text("Redo") }
            Button(onClick = { viewModel.toggleOnionSkin() }) {
                Text(if (viewModel.showOnionSkin) "Onion On" else "Onion Off")
            }
            Button(onClick = { viewModel.saveProject() }) { Text("Save") }
        }

        // Brush settings panel at bottom
        BrushSettingsPanel(
            brush = viewModel.currentBrush,
            onBrushSizeChange = { viewModel.updateBrushSize(it) },
            onBrushOpacityChange = { viewModel.updateBrushOpacity(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Timeline at bottom (simple linear for Phase 1)
        TimelineBar(
            frames = viewModel.project.frames,
            currentFrame = viewModel.project.currentFrameIndex,
            onFrameSelected = { viewModel.selectFrame(it) },
            onAddFrame = { viewModel.addFrame() },
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
