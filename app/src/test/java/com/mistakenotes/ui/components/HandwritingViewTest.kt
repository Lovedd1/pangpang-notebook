package com.mistakenotes.ui.components

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Display
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class HandwritingViewTest {

    private lateinit var view: HandwritingView

    @Before
    fun setup() {
        view = HandwritingView(Mockito.mock(Context::class.java))
    }

    @Test
    fun mmToPx_convertsMillimetersToPixels() {
        // Given density is typically 160, 240, 320, 480 etc
        val density = 160f
        val context = Mockito.mock(Context::class.java)
        val display = Mockito.mock(Display::class.java)
        val windowManager = Mockito.mock(WindowManager::class.java)

        Mockito.`when`(context.display).thenReturn(display)
        Mockito.`when`(display.getMetrics(Mockito.any())).thenReturn(android.util.DisplayMetrics().apply {
            density = density
        })

        val viewWithContext = HandwritingView(context)
        val result = viewWithContext.mmToPx(1f)
        assertEquals(density, result, 0.01f)
    }

    @Test
    fun canUndo_returnsFalseWhenEmpty() {
        assertFalse(view.canUndo())
    }

    @Test
    fun canRedo_returnsFalseWhenEmpty() {
        assertFalse(view.canRedo())
    }

    @Test
    fun eraserSize_enforcesMinBoundary() {
        view.eraserSize = 1f
        assertEquals(HandwritingView.ERASER_SIZE_MIN, view.eraserSize, 0.01f)
    }

    @Test
    fun eraserSize_enforcesMaxBoundary() {
        view.eraserSize = 100f
        assertEquals(HandwritingView.ERASER_SIZE_MAX, view.eraserSize, 0.01f)
    }

    @Test
    fun eraserSize_acceptsValidValues() {
        view.eraserSize = 30f
        assertEquals(30f, view.eraserSize, 0.01f)
    }

    @Test
    fun penColor_updatesCorrectly() {
        view.penColor = Color.RED
        assertEquals(Color.RED, view.penColor)
    }

    @Test
    fun penThickness_updatesCorrectly() {
        view.penThickness = HandwritingView.PEN_THICK
        assertEquals(HandwritingView.PEN_THICK, view.penThickness, 0.01f)
    }

    @Test
    fun defaultPenColors_areDefined() {
        assertEquals(Color.parseColor("#1E88E5"), HandwritingView.PEN_BLUE)
        assertEquals(Color.parseColor("#000000"), HandwritingView.PEN_BLACK)
        assertEquals(Color.parseColor("#E53935"), HandwritingView.PEN_RED)
    }

    @Test
    fun togglePenMode_switchesState() {
        val initial = view.isPenMode
        view.togglePenMode()
        assertEquals(!initial, view.isPenMode)
    }

    @Test
    fun toggleEraserMode_switchesState() {
        val initial = view.isEraserMode
        view.toggleEraserMode()
        assertEquals(!initial, view.isEraserMode)
    }

    @Test
    fun clear_clearsPathHistory() {
        view.clear()
        assertFalse(view.canUndo())
        assertFalse(view.canRedo())
    }
}