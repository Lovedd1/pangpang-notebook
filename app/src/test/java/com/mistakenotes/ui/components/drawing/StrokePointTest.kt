package com.mistakenotes.ui.components.drawing

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StrokePointTest {

    @Test
    fun `default values are set correctly`() {
        val point = StrokePoint(x = 100f, y = 200f)

        assertEquals(100f, point.x)
        assertEquals(200f, point.y)
        assertEquals(0.5f, point.pressure)
        assertEquals(0f, point.tilt)
        assertTrue(point.timestamp > 0)
    }

    @Test
    fun `custom values are preserved`() {
        val timestamp = 1234567890L
        val point = StrokePoint(
            x = 50f,
            y = 75f,
            pressure = 0.8f,
            tilt = 15f,
            timestamp = timestamp
        )

        assertEquals(50f, point.x)
        assertEquals(75f, point.y)
        assertEquals(0.8f, point.pressure)
        assertEquals(15f, point.tilt)
        assertEquals(timestamp, point.timestamp)
    }

    @Test
    fun `pressure default value is 0_5`() {
        val point = StrokePoint(x = 0f, y = 0f)
        assertEquals(0.5f, point.pressure)
    }

    @Test
    fun `pressure can exceed valid range when passed directly`() {
        // Note: Clamping is done in fromMotionEvent, not in constructor
        val pointHigh = StrokePoint(x = 0f, y = 0f, pressure = 1.5f)
        val pointLow = StrokePoint(x = 0f, y = 0f, pressure = -0.5f)

        assertEquals(1.5f, pointHigh.pressure)
        assertEquals(-0.5f, pointLow.pressure)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = StrokePoint(x = 10f, y = 20f, pressure = 0.5f)
        val copied = original.copy(x = 30f)

        assertEquals(30f, copied.x)
        assertEquals(20f, copied.y)
        assertEquals(0.5f, copied.pressure)
    }

    @Test
    fun `equals and hashCode work correctly`() {
        val point1 = StrokePoint(x = 100f, y = 200f, pressure = 0.5f, tilt = 0f, timestamp = 1000L)
        val point2 = StrokePoint(x = 100f, y = 200f, pressure = 0.5f, tilt = 0f, timestamp = 1000L)
        val point3 = StrokePoint(x = 100f, y = 200f, pressure = 0.6f, tilt = 0f, timestamp = 1000L)

        assertEquals(point1, point2)
        assertEquals(point1.hashCode(), point2.hashCode())
        assertTrue(point1 != point3)
    }

    @Test
    fun `toString returns readable format`() {
        val point = StrokePoint(x = 100f, y = 200f, pressure = 0.5f, tilt = 10f, timestamp = 1000L)
        val str = point.toString()

        assertTrue(str.contains("x=100.0"))
        assertTrue(str.contains("y=200.0"))
        assertTrue(str.contains("pressure=0.5"))
        assertTrue(str.contains("tilt=10.0"))
        assertTrue(str.contains("timestamp=1000"))
    }
}
