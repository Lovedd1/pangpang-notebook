package com.mistakenotes.ui.components.drawing

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * 画布快照
 */
data class CanvasSnapshot(
    val pathLayerSnapshot: List<PathData>,
    val bitmapLayer: Bitmap?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 撤销/重做管理器
 * 使用快照制，50 步限制
 */
class UndoRedoManager(private val maxSteps: Int = 50) {

    private val undoStack = mutableListOf<CanvasSnapshot>()
    private val redoStack = mutableListOf<CanvasSnapshot>()

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * 保存当前状态
     */
    fun saveState(pathLayer: PathLayer, bitmapLayer: BitmapLayer?) {
        val snapshot = CanvasSnapshot(
            pathLayerSnapshot = pathLayer.snapshot(),
            bitmapLayer = bitmapLayer?.getBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        )

        undoStack.add(snapshot)
        if (undoStack.size > maxSteps) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    /**
     * 撤销
     */
    fun undo(currentPathLayer: PathLayer, currentBitmapLayer: BitmapLayer?): Boolean {
        if (!canUndo()) return false

        // 保存当前状态到 redoStack
        val currentSnapshot = CanvasSnapshot(
            pathLayerSnapshot = currentPathLayer.snapshot(),
            bitmapLayer = currentBitmapLayer?.getBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        )
        redoStack.add(currentSnapshot)

        // 恢复到上一个状态
        val previousSnapshot = undoStack.removeLast()
        currentPathLayer.restore(previousSnapshot.pathLayerSnapshot)

        // 恢复 BitmapLayer
        previousSnapshot.bitmapLayer?.let { bitmap ->
            currentBitmapLayer?.ensureBitmap()
            currentBitmapLayer?.clear()
            val canvas = Canvas(currentBitmapLayer!!.getBitmap()!!)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }

        return true
    }

    /**
     * 重做
     */
    fun redo(currentPathLayer: PathLayer, currentBitmapLayer: BitmapLayer?): Boolean {
        if (!canRedo()) return false

        // 保存当前状态到 undoStack
        val currentSnapshot = CanvasSnapshot(
            pathLayerSnapshot = currentPathLayer.snapshot(),
            bitmapLayer = currentBitmapLayer?.getBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
        )
        undoStack.add(currentSnapshot)

        // 恢复到下一个状态
        val nextSnapshot = redoStack.removeLast()
        currentPathLayer.restore(nextSnapshot.pathLayerSnapshot)

        // 恢复 BitmapLayer
        nextSnapshot.bitmapLayer?.let { bitmap ->
            currentBitmapLayer?.ensureBitmap()
            currentBitmapLayer?.clear()
            val canvas = Canvas(currentBitmapLayer!!.getBitmap()!!)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }

        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}