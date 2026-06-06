package com.socreate.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.socreate.engine.BrushEngine
import com.socreate.model.Brush
import com.socreate.viewmodel.CanvasViewModel
import kotlin.math.*

/**
 * CircularToolPanels - Core distinctive UI for SoCreate.
 * Full spiral tool clustering with haptic feedback (this step).
 * Deeper integration of brush preview into panels (live size/opacity accurate Canvas dab + sample stroke preview, inline sliders, mini curve).
 * 
 * Features (matching original vision + further):
 * - 5 draggable satellite circles orbiting canvas: Brush, Color, Layers, Playback, Guides.
 * - Tap main to collapse/expand children in spiral (nautilus) or cyclical (concentric rings).
 * - Global/per-panel S/C mode toggle (long-press main or dedicated button).
 * - Drag any panel freely; on release: magnetic snap/clustering to other panels if within threshold (tool groups form organically).
 * - Haptic feedback on: expand/collapse, tool select, magnetic snap/clustering, mode toggle.
 * - Brush panel deeper: live brush preview (size/opacity/color accurate circle + sample stroke), inline sliders for size/opacity/liquify/sectors, tool buttons as orbiting children.
 * - Other panels: color swatches + simple picker, layer add/select, playback + add frame, guides toggles (grid/perspective/symmetry).
 * - Magnetic clustering: snapped panels visually group (proximity line + shared "cluster" tint); drag one moves influence.
 * - Auto minimal UI: panels auto slightly fade when drawing? (stub), focus on canvas.
 * - 100% local Compose + Android Vibrator, no AI/cloud.
 * 
 * Positions are in screen px (drag persists in remember for session; could persist to prefs later).
 */
@Composable
fun CircularToolPanels(viewModel: CanvasViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Panel definitions
    val panelIds = listOf("brush", "color", "layers", "playback", "guides")
    val panelLabels = mapOf(
        "brush" to "Brush",
        "color" to "Color",
        "layers" to "Layers",
        "playback" to "Play",
        "guides" to "Guides"
    )
    val panelColors = mapOf(
        "brush" to Color(0xFF4CAF50),
        "color" to Color(0xFF2196F3),
        "layers" to Color(0xFF9C27B0),
        "playback" to Color(0xFFFF9800),
        "guides" to Color(0xFFF44336)
    )

    // State: positions (px), expanded, mode
    val positions = remember {
        mutableStateMapOf<String, Offset>(
            "brush" to Offset(90f, 280f),
            "color" to Offset(920f, 180f),
            "layers" to Offset(90f, 1350f),
            "playback" to Offset(920f, 1450f),
            "guides" to Offset(180f, 720f)
        )
    }
    val expanded = remember { mutableStateMapOf<String, Boolean>().apply { panelIds.forEach { put(it, false) } } }
    var globalSpiralMode by remember { mutableStateOf(true) }  // true=spiral/nautilus, false=cyclical rings
    var lastSnapped by remember { mutableStateOf(false) }

    // Current brush for preview (live)
    val currentBrush = viewModel.currentBrush
    val currentTool = viewModel.currentTool

    fun performHaptic(strength: Int = 40) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(strength.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(strength.toLong())
            }
        }
    }

    // Spiral vs Cyclical child position calculator (relative to parent base)
    fun getChildPosition(parent: Offset, childIndex: Int, totalChildren: Int, isSpiral: Boolean): Offset {
        if (totalChildren == 0) return parent
        val angleStep = (2 * PI / totalChildren).toFloat()
        val baseRadius = 52f
        val theta = childIndex * angleStep * if (isSpiral) 1.8f else 1f
        val r = if (isSpiral) baseRadius + childIndex * 16f else baseRadius
        val dx = (r * cos(theta)).toFloat()
        val dy = (r * sin(theta)).toFloat()
        return Offset(parent.x + dx, parent.y + dy)
    }

    // Magnetic clustering / snap logic (called on drag end)
    fun applyClustering(panelId: String, newPos: Offset): Offset {
        var snappedPos = newPos
        val snapDist = 85f
        var didSnap = false
        panelIds.forEach { otherId ->
            if (otherId != panelId) {
                val otherPos = positions[otherId] ?: return@forEach
                val dist = hypot(snappedPos.x - otherPos.x, snappedPos.y - otherPos.y)
                if (dist < snapDist && dist > 5f) {
                    // Snap this panel close to other (magnetic cluster)
                    val dirX = if (dist > 0) (otherPos.x - snappedPos.x) / dist else 0f
                    val dirY = if (dist > 0) (otherPos.y - snappedPos.y) / dist else 0f
                    snappedPos = Offset(
                        otherPos.x + dirX * 28f,  // small offset for cluster spread
                        otherPos.y + dirY * 28f
                    )
                    didSnap = true
                }
            }
        }
        if (didSnap && !lastSnapped) {
            performHaptic(70)  // stronger haptic on cluster snap
            lastSnapped = true
        } else if (!didSnap) {
            lastSnapped = false
        }
        return snappedPos
    }

    // Child action data (labels + callbacks). Deep integration here.
    val brushChildren = listOf(
        "B" to { viewModel.setTool(BrushEngine.Tool.BRUSH); performHaptic(25) },
        "E" to { viewModel.setTool(BrushEngine.Tool.ERASER); performHaptic(25) },
        "F" to { viewModel.setTool(BrushEngine.Tool.FILL); performHaptic(25) },
        "L" to { viewModel.setTool(BrushEngine.Tool.LIQUIFY); performHaptic(25) },
        "Sym" to { viewModel.toggleSymmetry(); performHaptic(30) }
    )
    val colorChildren = listOf(
        "Red" to { viewModel.updateBrushColor(0xFFFF0000.toInt()); performHaptic(20) },
        "Grn" to { viewModel.updateBrushColor(0xFF00FF00.toInt()); performHaptic(20) },
        "Blu" to { viewModel.updateBrushColor(0xFF0000FF.toInt()); performHaptic(20) },
        "Blk" to { viewModel.updateBrushColor(0xFF000000.toInt()); performHaptic(20) },
        "Wht" to { viewModel.updateBrushColor(0xFFFFFFFF.toInt()); performHaptic(20) }
    )
    val layerChildren = listOf(
        "+" to { viewModel.addLayer(); performHaptic(30) },
        "1" to { viewModel.selectLayer(0); performHaptic(20) },
        "2" to { if (viewModel.getCurrentLayerCount() > 1) viewModel.selectLayer(1); performHaptic(20) },
        "3" to { if (viewModel.getCurrentLayerCount() > 2) viewModel.selectLayer(2); performHaptic(20) },
        "Op" to { /* deeper control in expanded card */ performHaptic(15) },
        "Bl" to { /* blend in expanded */ performHaptic(15) }
    )
    val playbackChildren = listOf(
        "▶" to { viewModel.togglePlayback(); performHaptic(30) },
        "+" to { viewModel.addFrame(); performHaptic(25) },
        "⟲" to { viewModel.scrubToTime(0); performHaptic(20) },
        "Exp" to { viewModel.exportAudioWithProject(); performHaptic(30) }  // Phase 2 polish audio export from circular
    )
    val guideChildren = listOf(
        "Grid" to { viewModel.toggleGridSnap(); performHaptic(25) },
        "Persp" to { viewModel.togglePerspectiveGuides(); performHaptic(25) },
        "SymC" to { viewModel.setSymmetryCenter(960f, 540f); performHaptic(25) },  // default center
        "Perf" to { viewModel.setHighPerfMode(!viewModel.highPerfMode); performHaptic(30) }  // Performance further toggle
    )

    // Render each satellite panel
    panelIds.forEach { id ->
        val pos = positions[id] ?: Offset(100f, 100f)
        val isExpanded = expanded[id] ?: false
        val isSpiral = globalSpiralMode
        val children = when (id) {
            "brush" -> brushChildren
            "color" -> colorChildren
            "layers" -> layerChildren
            "playback" -> playbackChildren
            "guides" -> guideChildren
            else -> emptyList()
        }
        val baseColor = panelColors[id] ?: Color.Gray

        // Main draggable satellite circle
        Box(
            modifier = Modifier
                .offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }
                .size(48.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = 0.85f))
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .pointerInput(id) {
                    detectDragGestures(
                        onDragStart = { performHaptic(15) },
                        onDrag = { change, dragAmount ->
                            val newX = (positions[id]?.x ?: 0f) + dragAmount.x
                            val newY = (positions[id]?.y ?: 0f) + dragAmount.y
                            positions[id] = Offset(newX, newY)
                            change.consume()
                        },
                        onDragEnd = {
                            // Apply clustering snap on release
                            val current = positions[id] ?: Offset.Zero
                            val snapped = applyClustering(id, current)
                            positions[id] = snapped
                            performHaptic(15)
                        }
                    )
                }
                .pointerInput(id) {
                    detectTapGestures(
                        onTap = {
                            expanded[id] = !isExpanded
                            performHaptic(if (isExpanded) 20 else 35)
                        },
                        onLongPress = {
                            globalSpiralMode = !globalSpiralMode
                            performHaptic(50)
                        }
                    )
                }
        ) {
            // Main label / icon
            Text(
                panelLabels[id] ?: id,
                color = Color.White,
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.align(Alignment.Center)
            )
            // Small mode indicator on main
            if (isExpanded) {
                Text(
                    if (isSpiral) "S" else "C",
                    color = Color.Yellow,
                    fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                )
            }
        }

        // Expanded children (spiral or cyclical orbiting satellites)
        if (isExpanded) {
            children.forEachIndexed { idx, (label, action) ->
                val childPos = getChildPosition(pos, idx, children.size, isSpiral)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(childPos.x.toInt(), childPos.y.toInt()) }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(baseColor.copy(alpha = 0.7f))
                        .border(1.dp, Color.White.copy(0.5f), CircleShape)
                        .clickable {
                            action()
                            // Optional: auto collapse after action for clean focus (vision)
                            // expanded[id] = false
                        }
                ) {
                    Text(
                        label,
                        color = Color.White,
                        fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Deeper brush preview integration: only for brush panel, shown near expanded children
            if (id == "brush") {
                val previewOffsetX = pos.x + 70f
                val previewOffsetY = pos.y - 10f
                Box(
                    modifier = Modifier
                        .offset { IntOffset(previewOffsetX.toInt(), previewOffsetY.toInt()) }
                        .width(160.dp)
                        .background(Color(0xCC222222), shape = MaterialTheme.shapes.small)
                        .border(1.dp, baseColor, MaterialTheme.shapes.small)
                        .padding(6.dp)
                ) {
                    Column {
                        Text("Brush Preview", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Spacer(Modifier.height(4.dp))

                        // Live brush preview Canvas (deeper integration: accurate size/opacity/color + sample stroke)
                        Canvas(
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color(0xFF111111))
                        ) {
                            val scale = 0.4f
                            val r = (currentBrush.size * scale).coerceIn(3f, 32f)
                            val center = Offset(size.width / 2, size.height / 2 + 5f)

                            // Main brush dab (pressure 1.0)
                            drawCircle(
                                color = Color(currentBrush.color),
                                radius = r,
                                center = center,
                                alpha = currentBrush.opacity
                            )

                            // Sample stroke preview (wavy line showing pressure response)
                            val path = Path()
                            path.moveTo(8f, size.height - 18f)
                            for (t in 0..8) {
                                val px = 8f + t * 7f
                                val press = 0.3f + 0.7f * sin(t * 0.9f)  // fake pressure curve
                                val py = size.height - 18f - (currentBrush.size * scale * 0.6f * press)
                                path.lineTo(px, py)
                            }
                            drawPath(
                                path,
                                color = Color(currentBrush.color).copy(alpha = currentBrush.opacity * 0.8f),
                                style = Stroke(width = (currentBrush.size * scale * 0.35f).coerceAtLeast(1.5f))
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Inline controls (deeper than old sidebar)
                        Text("Size: ${currentBrush.size.toInt()}px", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Slider(
                            value = currentBrush.size,
                            onValueChange = { viewModel.updateBrushSize(it) },
                            valueRange = 1f..180f,
                            modifier = Modifier.height(18.dp)
                        )

                        Text("Opacity: ${(currentBrush.opacity * 100).toInt()}%", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Slider(
                            value = currentBrush.opacity,
                            onValueChange = { viewModel.updateBrushOpacity(it) },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.height(18.dp)
                        )

                        if (currentTool == BrushEngine.Tool.LIQUIFY) {
                            Text("Liquify: ${viewModel.liquifyStrength.toInt()}", color = Color(0xFFFFAA00), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                            Slider(
                                value = viewModel.liquifyStrength,
                                onValueChange = { viewModel.setLiquifyStrength(it) },
                                valueRange = 0.1f..4f,
                                modifier = Modifier.height(18.dp)
                            )
                        }

                        if (viewModel.symmetryEnabled) {
                            Text("Sectors: ${viewModel.symmetrySectors}", color = Color(0xFF88FFAA), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                            Slider(
                                value = viewModel.symmetrySectors.toFloat(),
                                onValueChange = { viewModel.setSymmetrySectors(it.toInt()) },
                                valueRange = 2f..12f,
                                steps = 9,
                                modifier = Modifier.height(18.dp)
                            )
                        }

                        // Quick pressure curve access (deeper)
                        Text("Curve", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
                        // Mini curve editor stub (full one available via settings if wanted)
                        Canvas(Modifier.size(70.dp, 22.dp).background(Color.Black)) {
                            val pts = currentBrush.pressureCurvePoints
                            if (pts.isNotEmpty()) {
                                val path = Path()
                                path.moveTo(pts[0].first * size.width, size.height - pts[0].second * size.height)
                                pts.drop(1).forEach { (x, y) ->
                                    path.lineTo(x * size.width, size.height - y * size.height)
                                }
                                drawPath(path, Color.Cyan, style = Stroke(1.5f))
                            }
                        }
                    }
                }
            }

            // Deeper layers/compositing integration (this step): expanded card for current layer opacity, blend modes, layer list
            if (id == "layers") {
                val previewOffsetX = pos.x + 70f
                val previewOffsetY = pos.y - 20f
                Box(
                    modifier = Modifier
                        .offset { IntOffset(previewOffsetX.toInt(), previewOffsetY.toInt()) }
                        .width(180.dp)
                        .background(Color(0xCC222222), shape = MaterialTheme.shapes.small)
                        .border(1.dp, baseColor, MaterialTheme.shapes.small)
                        .padding(6.dp)
                ) {
                    Column {
                        Text("Layer Compositing", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Spacer(Modifier.height(2.dp))

                        // Current layer info
                        val layerCount = viewModel.getCurrentLayerCount()
                        val curIdx = viewModel.currentLayerIndex  // note: may need exposure, using direct for now
                        Text("Layer ${curIdx + 1} / $layerCount", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))

                        // Opacity slider (deeper)
                        val curOpacity = viewModel.getCurrentLayerOpacity()
                        Text("Opacity: ${(curOpacity * 100).toInt()}%", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Slider(
                            value = curOpacity,
                            onValueChange = { viewModel.setCurrentLayerOpacity(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(18.dp)
                        )

                        // Blend mode selector (deeper)
                        Text("Blend: ${viewModel.getCurrentLayerBlendMode().name}", color = Color(0xFF88FFAA), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            viewModel.getCurrentLayerBlendModes().forEach { mode ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (viewModel.getCurrentLayerBlendMode() == mode) baseColor else Color(0xFF444444), CircleShape)
                                        .clickable {
                                            viewModel.setCurrentLayerBlendMode(mode)
                                            performHaptic(20)
                                        }
                                ) {
                                    Text(mode.name.take(3), color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Quick layer list (select)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until minOf(layerCount, 5)) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(if (i == curIdx) baseColor else Color(0xFF555555), CircleShape)
                                        .clickable {
                                            viewModel.selectLayer(i)
                                            performHaptic(15)
                                        }
                                ) {
                                    Text("${i+1}", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.align(Alignment.Center))
                                }
                            }
                            if (layerCount > 5) Text("...", color = Color.LightGray, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
                        }
                    }
                }
            }
        }

        // Visual clustering indicator: thin connector line if close to any other panel
        if (isExpanded) {
            panelIds.forEach { otherId ->
                if (otherId != id) {
                    val otherPos = positions[otherId] ?: return@forEach
                    val dist = hypot(pos.x - otherPos.x, pos.y - otherPos.y)
                    if (dist < 120f) {
                        // Draw connector (using a thin line via Canvas overlay near this panel)
                        Canvas(
                            modifier = Modifier
                                .offset { IntOffset(min(pos.x, otherPos.x).toInt() - 5, min(pos.y, otherPos.y).toInt() - 5) }
                                .size( (abs(pos.x - otherPos.x) + 20).dp , (abs(pos.y - otherPos.y) + 20).dp )
                        ) {
                            val start = Offset(
                                if (pos.x < otherPos.x) 5f else size.width - 5f,
                                if (pos.y < otherPos.y) 5f else size.height - 5f
                            )
                            val end = Offset(
                                if (pos.x < otherPos.x) size.width - 5f else 5f,
                                if (pos.y < otherPos.y) size.height - 5f else 5f
                            )
                            drawLine(
                                color = baseColor.copy(alpha = 0.35f),
                                start = start,
                                end = end,
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }
            }
        }
    }

    // Global mode toggle button (small, near bottom or fixed, for convenience)
    Box(
        modifier = Modifier
            .offset { IntOffset(980, 80) }  // top-rightish, non-intrusive
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xAA333333))
            .border(1.dp, Color.White, CircleShape)
            .clickable {
                globalSpiralMode = !globalSpiralMode
                performHaptic(45)
            }
    ) {
        Text(
            if (globalSpiralMode) "S" else "C",
            color = Color.Yellow,
            fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.align(Alignment.Center)
        )
    }

    // Optional: cluster reset hint (for pro users)
    if (lastSnapped) {
        Text(
            "Clustered",
            color = Color(0x88FFFFFF),
            fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.offset { IntOffset(500, 40) }
        )
    }
}
