package com.mistakenotes.ui.components.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * 像素图层，用于橡皮擦操作
 * 当切换到橡皮擦模式时，PathLayer 内容会渲染到此 Bitmap
 */
class BitmapLayer(
    private var width: Int,
    private var height: Int
) {
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    val erasePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    fun ensureBitmap() {
        if (bitmap == null || bitmap!!.width != width || bitmap!!.height != height) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        }
    }

    fun clear() {
        bitmap?.eraseColor(android.graphics.Color.TRANSPARENT)
    }

    fun setSize(w: Int, h: Int) {
        if (width != w || height != h) {
            width = w
            height = h
            bitmap = null
            canvas = null
        }
    }

    /**
     * 将 PathLayer 内容渲染到此 Bitmap
     */
    fun renderFromPathLayer(pathLayer: PathLayer, backgroundColor: Int) {
        ensureBitmap()
        canvas?.let { c ->
            // 填充背景
            c.drawColor(backgroundColor)
            // 绘制所有路径
            pathLayer.draw(c)
        }
    }

    /**
     * 在指定位置擦除
     */
    fun erase(x: Float, y: Float, radius: Float) {
        bitmap?.let {
            val canvas = Canvas(it)
            canvas.drawCircle(x, y, radius, erasePaint)
        }
    }

    /**
     * 获取 Bitmap
     */
    fun getBitmap(): Bitmap? = bitmap

    /**
     * 绘制到目标 Canvas
     */
    fun draw(canvas: Canvas, left: Float = 0f, top: Float = 0f) {
        bitmap?.let {
            canvas.drawBitmap(it, left, top, null)
        }
    }

    /**
     * 检查 Bitmap 是否为空
     */
    fun isEmpty(): Boolean {
        return bitmap == null
    }
}