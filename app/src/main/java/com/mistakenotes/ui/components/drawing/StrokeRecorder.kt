package com.mistakenotes.ui.components.drawing

import android.graphics.PointF

/**
 * 笔画记录器
 * 替代 Bitmap 拷贝式撤销/重做，大幅降低内存占用
 */
class StrokeRecorder(private val maxHistory: Int = 50) {
    private val undoStack = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()

    /**
     * 开始新笔画（清除重做栈）
     */
    fun beginStroke() {
        redoStack.clear()
    }

    /**
     * 添加笔画到撤销栈
     */
    fun addStroke(stroke: Stroke) {
        undoStack.add(stroke)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
    }

    /**
     * 撤销 - 返回被撤销的笔画
     */
    fun undo(): Stroke? {
        if (undoStack.isEmpty()) return null
        val stroke = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(stroke)
        return stroke
    }

    /**
     * 重做 - 返回被重做的笔画
     */
    fun redo(): Stroke? {
        if (redoStack.isEmpty()) return null
        val stroke = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(stroke)
        return stroke
    }

    /**
     * 获取撤销栈中最后一个笔画（不弹出）
     */
    fun peekUndo(): Stroke? = undoStack.lastOrNull()

    /**
     * 获取重做栈中最后一个笔画（不弹出）
     */
    fun peekRedo(): Stroke? = redoStack.lastOrNull()

    /**
     * 检查是否可以撤销
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * 检查是否可以重做
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * 获取所有笔画（用于重绘）
     */
    fun getAllStrokes(): List<Stroke> = undoStack.toList()

    /**
     * 清空所有记录
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * 获取撤销栈大小
     */
    fun undoStackSize(): Int = undoStack.size

    /**
     * 获取重做栈大小
     */
    fun redoStackSize(): Int = redoStack.size
}
