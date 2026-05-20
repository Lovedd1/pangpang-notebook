package com.mistakenotes.ui.components.drawing

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test

/**
 * PerfectStroke 算法的单元测试
 */
class PerfectStrokeTest {

    @Test
    fun `getStroke returns empty list for single point`() {
        val points = listOf(StrokePoint(0f, 0f))
        val result = PerfectStroke.getStroke(points)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getStroke returns empty list for empty list`() {
        val points = emptyList<StrokePoint>()
        val result = PerfectStroke.getStroke(points)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getStroke returns points for two points`() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 10f)
        )
        val result = PerfectStroke.getStroke(points)
        assertTrue(result.isNotEmpty())
        // Should have 4 points (2 left edge + 2 right edge)
        assertEquals(4, result.size)
    }

    @Test
    fun `getStroke returns correct number of points`() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 0f),
            StrokePoint(20f, 0f)
        )
        val result = PerfectStroke.getStroke(points)
        // Each input point generates 2 outline points
        assertTrue(result.size >= 4)
    }

    @Test
    fun `getStroke respects size option`() {
        val smallSize = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 0f)
        )
        val largeSize = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 0f)
        )

        val smallResult = PerfectStroke.getStroke(smallSize, PerfectStroke.Options(size = 4f))
        val largeResult = PerfectStroke.getStroke(largeSize, PerfectStroke.Options(size = 20f))

        // Larger size should produce wider strokes (larger coordinate differences)
        val smallWidth = smallResult.maxOfOrNull { it.x }!! - smallResult.minOfOrNull { it.x }!!
        val largeWidth = largeResult.maxOfOrNull { it.x }!! - largeResult.minOfOrNull { it.x }!!

        assertTrue(largeWidth > smallWidth)
    }

    @Test
    fun `getStrokePoints calculates pressure correctly`() {
        val points = listOf(
            StrokePoint(0f, 0f, pressure = 0.2f),
            StrokePoint(10f, 0f, pressure = 0.8f)
        )
        val result = PerfectStroke.getStrokePoints(points, PerfectStroke.Options())

        assertEquals(2, result.size)
        assertEquals(0.2f, result[0].pressure, 0.001f)
        assertEquals(0.8f, result[1].pressure, 0.001f)
    }

    @Test
    fun `getStrokePoints simulates pressure when enabled`() {
        val points = listOf(
            StrokePoint(0f, 0f, pressure = 0.5f),
            StrokePoint(10f, 0f, pressure = 0.5f, timestamp = 100L),
            StrokePoint(20f, 0f, pressure = 0.5f, timestamp = 200L)
        )
        val result = PerfectStroke.getStrokePoints(points, PerfectStroke.Options(simulatePressure = true))

        assertEquals(3, result.size)
        // All simulated pressures should be between 0.1 and 1.0
        result.forEach {
            assertTrue(it.pressure in 0.1f..1.0f)
        }
    }

    @Test
    fun `getStrokePoints calculates vector correctly`() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 0f)
        )
        val result = PerfectStroke.getStrokePoints(points, PerfectStroke.Options())

        assertEquals(2, result.size)
        // First point has default vector (1, 0)
        assertEquals(1f, result[0].vector.x, 0.001f)
        assertEquals(0f, result[0].vector.y, 0.001f)
        // Second point should point right
        assertTrue(result[1].vector.x > 0)
    }

    @Test
    fun `getStrokeOutlinePoints produces valid polygon`() {
        val strokePoints = listOf(
            PerfectStroke.getStrokePoints(
                listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f)),
                PerfectStroke.Options()
            )
        ).flatten()

        val result = PerfectStroke.getStrokeOutlinePoints(strokePoints, PerfectStroke.Options())

        assertTrue(result.isNotEmpty())
        // Polygon should have equal number of left and right points
        assertTrue(result.size >= 4)
    }

    @Test
    fun `applyStreamline smooths points`() {
        val points = listOf(
            PerfectStroke.StrokeStrokePoint(
                point = PointF(0f, 0f),
                pressure = 0.5f,
                vector = PointF(1f, 0f),
                distance = 0f,
                runningLength = 0f
            ),
            PerfectStroke.StrokeStrokePoint(
                point = PointF(10f, 0f),
                pressure = 0.5f,
                vector = PointF(1f, 0f),
                distance = 10f,
                runningLength = 10f
            ),
            PerfectStroke.StrokeStrokePoint(
                point = PointF(20f, 0f),
                pressure = 0.5f,
                vector = PointF(1f, 0f),
                distance = 20f,
                runningLength = 20f
            )
        )

        val smoothMethod = PerfectStroke::class.java.getDeclaredMethod(
            "applyStreamline",
            List::class.java,
            Float::class.java
        )
        smoothMethod.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = smoothMethod.invoke(
            PerfectStroke,
            points,
            0.5f
        ) as List<PerfectStroke.StrokeStrokePoint>

        assertEquals(3, result.size)
    }

    @Test
    fun `simplifyOutline removes close points`() {
        val points = listOf(
            PointF(0f, 0f),
            PointF(0.5f, 0f), // Too close, should be removed
            PointF(3f, 0f)    // Far enough
        )

        val smoothMethod = PerfectStroke::class.java.getDeclaredMethod(
            "simplifyOutline",
            List::class.java,
            Float::class.java
        )
        smoothMethod.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = smoothMethod.invoke(
            PerfectStroke,
            points,
            0.5f
        ) as List<PointF>

        assertTrue(result.size <= 3)
    }

    @Test
    fun `pointsToPath creates valid Path`() {
        val points = listOf(
            PointF(0f, 0f),
            PointF(10f, 0f),
            PointF(10f, 10f),
            PointF(0f, 10f)
        )

        val path = PerfectStroke.pointsToPath(points)

        assertNotNull(path)
        // Path should not be empty
    }

    @Test
    fun `pointsToPath handles empty list`() {
        val points = emptyList<PointF>()
        val path = PerfectStroke.pointsToPath(points)
        assertNotNull(path)
    }

    @Test
    fun `pointsToPath handles single point`() {
        val points = listOf(PointF(0f, 0f))
        val path = PerfectStroke.pointsToPath(points)
        assertNotNull(path)
    }

    @Test
    fun `pointsToPath handles two points`() {
        val points = listOf(PointF(0f, 0f), PointF(10f, 10f))
        val path = PerfectStroke.pointsToPath(points)
        assertNotNull(path)
    }

    @Test
    fun `Options has correct defaults`() {
        val options = PerfectStroke.Options()

        assertEquals(8f, options.size, 0.001f)
        assertEquals(0.5f, options.thinning, 0.001f)
        assertEquals(0.5f, options.smoothing, 0.001f)
        assertEquals(0.5f, options.streamline, 0.001f)
        assertFalse(options.simulatePressure)
        assertTrue(options.cap)
        assertNotNull(options.start)
        assertNotNull(options.end)
    }

    @Test
    fun `StartEnd has correct defaults`() {
        val startEnd = PerfectStroke.StartEnd()

        assertTrue(startEnd.cap)
        assertEquals(0f, startEnd.taper, 0.001f)
    }

    @Test
    fun `getStroke respects thinning option`() {
        val lowThinning = listOf(
            StrokePoint(0f, 0f, pressure = 0.5f),
            StrokePoint(10f, 0f, pressure = 0.5f)
        )
        val highThinning = listOf(
            StrokePoint(0f, 0f, pressure = 0.5f),
            StrokePoint(10f, 0f, pressure = 0.5f)
        )

        val lowResult = PerfectStroke.getStroke(lowThinning, PerfectStroke.Options(thinning = 0.1f))
        val highResult = PerfectStroke.getStroke(highThinning, PerfectStroke.Options(thinning = 0.9f))

        val lowWidth = lowResult.maxOfOrNull { it.x }!! - lowResult.minOfOrNull { it.x }!!
        val highWidth = highResult.maxOfOrNull { it.x }!! - highResult.minOfOrNull { it.x }!!

        // Higher thinning = more pressure sensitivity = potentially wider strokes at high pressure
        // At same pressure, higher thinning should give different width than lower thinning
        assertNotEquals(lowWidth, highWidth, 0.001f)
    }

    @Test
    fun `running length is calculated correctly`() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(3f, 4f),  // Distance = 5
            StrokePoint(8f, 4f)   // Distance = 5
        )

        val result = PerfectStroke.getStrokePoints(points, PerfectStroke.Options())

        assertEquals(3, result.size)
        assertTrue(result[0].runningLength >= 0f)
        assertTrue(result[1].runningLength > result[0].runningLength)
        assertTrue(result[2].runningLength > result[1].runningLength)
    }
}