package com.mistakenotes.ui.components.drawing

/**
 * 橡皮擦工具配置
 */
data class EraserTool(
    val baseSize: Float = 20f,
    val pressureSensitive: Boolean = true,
    val shape: EraserShape = EraserShape.CIRCLE
) {
    enum class EraserShape {
        CIRCLE
        // 未来可以扩展为方形等
    }

    /**
     * 根据压力计算实际擦除半径
     */
    fun getRadius(pressure: Float): Float {
        return if (pressureSensitive) {
            baseSize * pressure.coerceIn(0.1f, 2f)
        } else {
            baseSize
        }
    }
}
