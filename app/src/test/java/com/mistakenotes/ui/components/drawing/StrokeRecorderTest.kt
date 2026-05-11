package com.mistakenotes.ui.components.drawing

import android.graphics.Paint
import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test

class StrokeRecorderTest {

    @Test
    fun `初始状态无法撤销和重做`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        assertFalse(recorder.canUndo())
        assertFalse(recorder.canRedo())
    }

    @Test
    fun `添加笔画后可撤销`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createTestStroke())

        assertTrue(recorder.canUndo())
        assertFalse(recorder.canRedo())
    }

    @Test
    fun `撤销后可以重做`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        val stroke = createTestStroke()
        recorder.beginStroke()
        recorder.addStroke(stroke)

        val undone = recorder.undo()

        assertNotNull(undone)
        assertEquals(stroke, undone)
        assertFalse(recorder.canUndo())
        assertTrue(recorder.canRedo())
    }

    @Test
    fun `重做后可以再次撤销`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createTestStroke())

        recorder.undo()
        val redone = recorder.redo()

        assertNotNull(redone)
        assertTrue(recorder.canUndo())
        assertFalse(recorder.canRedo())
    }

    @Test
    fun `新笔画清空重做栈`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createTestStroke())
        recorder.undo() // undo之后可以redo

        recorder.beginStroke() // 开始新笔画
        recorder.addStroke(createTestStroke())

        assertFalse(recorder.canRedo()) // redo栈被清空
    }

    @Test
    fun `超过最大历史后移除最旧的`() {
        val recorder = StrokeRecorder(maxHistory = 3)
        val strokes = listOf(
            createStrokeWithIndex(1),
            createStrokeWithIndex(2),
            createStrokeWithIndex(3)
        )

        recorder.beginStroke()
        strokes.forEach { recorder.addStroke(it) }

        // 添加第4个笔画时，第1个应该被移除
        recorder.beginStroke()
        recorder.addStroke(createStrokeWithIndex(4))

        val allStrokes = recorder.getAllStrokes()
        assertEquals(3, allStrokes.size)
        // 应该包含笔画2,3,4
        assertTrue(allStrokes.any { it.timestamp.toInt() == 2 })
        assertTrue(allStrokes.any { it.timestamp.toInt() == 3 })
        assertTrue(allStrokes.any { it.timestamp.toInt() == 4 })
    }

    @Test
    fun `清空后状态重置`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createTestStroke())

        recorder.clear()

        assertFalse(recorder.canUndo())
        assertFalse(recorder.canRedo())
        assertEquals(0, recorder.undoStackSize())
        assertEquals(0, recorder.redoStackSize())
    }

    @Test
    fun `空撤销栈返回null`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        assertNull(recorder.undo())
    }

    @Test
    fun `空重做栈返回null`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createTestStroke())

        assertNull(recorder.redo())
    }

    @Test
    fun `获取所有笔画`() {
        val recorder = StrokeRecorder(maxHistory = 10)
        recorder.beginStroke()
        recorder.addStroke(createStrokeWithIndex(1))
        recorder.beginStroke()
        recorder.addStroke(createStrokeWithIndex(2))

        val allStrokes = recorder.getAllStrokes()
        assertEquals(2, allStrokes.size)
    }

    private fun createTestStroke(): Stroke {
        return Stroke(
            points = listOf(PointF(0f, 0f), PointF(10f, 10f)),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND
        )
    }

    private fun createStrokeWithIndex(index: Int): Stroke {
        return Stroke(
            points = listOf(PointF(0f, 0f), PointF(index.toFloat(), index.toFloat())),
            color = 0xFFD4A574.toInt(),
            strokeWidth = 5f,
            strokeCap = Paint.Cap.ROUND,
            strokeJoin = Paint.Join.ROUND,
            timestamp = index.toLong()
        )
    }
}