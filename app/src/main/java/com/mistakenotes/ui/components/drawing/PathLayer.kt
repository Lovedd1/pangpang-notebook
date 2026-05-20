package com.mistakenotes.ui.components.drawing

import android.graphics.Paint
import android.graphics.Path

/**
 * 笔画数据
 */
data class PathData(
    val path: Path,
    val paint: Paint,
    val tool: DrawingTool = DrawingTool.PEN
)

/**
 * 矢量图层，管理所有笔画 Path
 */
class PathLayer {

    private val paths = mutableListOf<PathData>()
    private val redoStack = mutableListOf<PathData>()

    fun addPath(path: Path, paint: Paint, tool: DrawingTool = DrawingTool.PEN) {
        paths.add(PathData(path, Paint(paint), tool))
        redoStack.clear()
    }

    fun removePath(index: Int) {
        if (index in paths.indices) {
            paths.removeAt(index)
        }
    }

    fun getPaths(): List<PathData> = paths.toList()

    fun clear() {
        paths.clear()
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (paths.isEmpty()) return false
        redoStack.add(paths.removeLast())
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        paths.add(redoStack.removeLast())
        return true
    }

    fun canUndo(): Boolean = paths.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * 创建快照用于撤销/重做
     */
    fun snapshot(): List<PathData> {
        return paths.map { PathData(Path(it.path), Paint(it.paint), it.tool) }
    }

    /**
     * 恢复快照
     */
    fun restore(snapshot: List<PathData>) {
        paths.clear()
        paths.addAll(snapshot.map { PathData(Path(it.path), Paint(it.paint), it.tool) })
    }

    /**
     * 渲染到 Canvas
     */
    fun draw(canvas: android.graphics.Canvas) {
        paths.forEach { pathData ->
            canvas.drawPath(pathData.path, pathData.paint)
        }
    }
}