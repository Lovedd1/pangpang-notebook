package com.mistakenotes.ui.components.drawing

import android.view.MotionEvent

/**
 * 笔画采样点
 * @param x X坐标
 * @param y Y坐标
 * @param pressure 压力值 (0-1)，默认 0.5
 * @param tilt 倾斜角度，默认 0
 * @param timestamp 时间戳，用于计算速度
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromMotionEvent(event: MotionEvent, pointerIndex: Int = 0): StrokePoint {
            return StrokePoint(
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
                pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f),
                tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                timestamp = event.eventTime
            )
        }
    }
}
