package com.socreate.engine

import com.socreate.model.Frame
import java.util.*

interface UndoableAction {
    fun undo()
    fun redo()
    fun description(): String // For UI/debug (e.g., time-jump slider)
}

/**
 * Smart Undo: Supports multiple action types with efficient diffs where possible.
 * Limits history to prevent memory bloat (e.g., 50 steps).
 * Stroke actions store only affected tiles (sparse).
 * Supports Layer/Frame ops, Transform, etc.
 * Can be extended for command pattern with serialization for branching.
 */
class StrokeAction(
    private val frame: Frame,
    private val layerIndex: Int,
    private val affectedTiles: Map<Pair<Int, Int>, Pair<IntArray, IntArray>> // tileCoord -> (before, after)
) : UndoableAction {
    override fun undo() {
        affectedTiles.forEach { (coord, beforeAfter) ->
            frame.layers.getOrNull(layerIndex)?.setTile(coord.first, coord.second, beforeAfter.first)
        }
    }

    override fun redo() {
        affectedTiles.forEach { (coord, beforeAfter) ->
            frame.layers.getOrNull(layerIndex)?.setTile(coord.first, coord.second, beforeAfter.second)
        }
    }

    override fun description(): String = "Stroke on layer $layerIndex (${affectedTiles.size} tiles)"
}

class LayerAction(
    private val frame: Frame,
    private val beforeLayers: List<com.socreate.model.Layer>,
    private val afterLayers: List<com.socreate.model.Layer>
) : UndoableAction {
    override fun undo() {
        frame.layers.clear()
        frame.layers.addAll(beforeLayers.map { it.copy() }) // Deep copy if needed
    }

    override fun redo() {
        frame.layers.clear()
        frame.layers.addAll(afterLayers.map { it.copy() })
    }

    override fun description(): String = "Layer operation"
}

class UndoManager(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<UndoableAction>()
    private val redoStack = ArrayDeque<UndoableAction>()

    fun push(action: UndoableAction) {
        undoStack.addLast(action)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst() // Smart memory management: drop oldest
        }
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeLast()
            action.undo()
            redoStack.addLast(action)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeLast()
            action.redo()
            undoStack.addLast(action)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun getUndoHistory(): List<String> = undoStack.map { it.description() } // For time-jump UI
    fun jumpToUndo(index: Int) {
        // Stub for time-jump slider: undo/redo multiple steps
        while (undoStack.size > index && canUndo()) undo()
    }
}
