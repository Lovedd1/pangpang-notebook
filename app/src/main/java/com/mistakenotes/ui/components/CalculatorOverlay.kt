package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream
import kotlin.math.*

// ── Calculator button data ──────────────────────────────────────────

private data class CalcKey(
    val label: String,
    val isOperator: Boolean = false,
    val isFunc: Boolean = false,
    val isEquals: Boolean = false,
    val span: Int = 1
)

// 4-column × 7-row scientific keypad
private val KEY_ROWS = listOf(
    listOf(CalcKey("C"), CalcKey("⌫"), CalcKey("π", isFunc = true), CalcKey("e", isFunc = true)),
    listOf(CalcKey("sin", isFunc = true), CalcKey("cos", isFunc = true), CalcKey("tan", isFunc = true), CalcKey("÷", isOperator = true)),
    listOf(CalcKey("√", isFunc = true), CalcKey("x²", isFunc = true), CalcKey("xʸ", isOperator = true), CalcKey("×", isOperator = true)),
    listOf(CalcKey("7"), CalcKey("8"), CalcKey("9"), CalcKey("−", isOperator = true)),
    listOf(CalcKey("4"), CalcKey("5"), CalcKey("6"), CalcKey("+", isOperator = true)),
    listOf(CalcKey("1"), CalcKey("2"), CalcKey("3"), CalcKey(".", isFunc = true)),
    listOf(CalcKey("0"), CalcKey("±", isFunc = true), CalcKey("%", isFunc = true), CalcKey("=", isEquals = true))
)

// ── Calculator overlay ─────────────────────────────────────────────

@Composable
fun CalculatorOverlay(onDismiss: () -> Unit) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx  = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val screenHeightDp = config.screenHeightDp

    val isCompact = screenHeightDp < 500

    // ── Responsive sizes ──────────────────────────────────────
    val calcW        = if (isCompact) 210.dp else 250.dp
    val pad          = if (isCompact) 5.dp   else 8.dp
    val gap          = if (isCompact) 4.dp   else 5.dp
    val displayH     = if (isCompact) 30.dp  else 44.dp
    val displayFont  = if (isCompact) 18.sp  else 22.sp
    val keyFont      = if (isCompact) 11.sp  else 15.sp
    val funcFont     = if (isCompact) 8.sp   else 10.sp
    val eqFont       = if (isCompact) 15.sp  else 18.sp
    val dragH        = if (isCompact) 12.dp  else 18.dp
    val dotSize      = if (isCompact) 3.dp   else 3.dp
    val closeF       = if (isCompact) 10.sp  else 12.sp
    val tabW         = if (isCompact) 20.dp  else 24.dp
    val tabH         = if (isCompact) 48.dp  else 64.dp
    val tabFont      = if (isCompact) 9.sp   else 11.sp
    val keyRatio     = if (isCompact) 0.80f else 0.88f

    val calcWidthPx  = with(density) { calcW.toPx() }
    val tabWidthPx   = with(density) { tabW.toPx() }
    val marginPx     = with(density) { 8.dp.toPx() }
    val clampBotPx   = with(density) { if (isCompact) 30.dp.toPx() else 100.dp.toPx() }

    // ── Position ──────────────────────────────────────────────
    val defaultX = screenWidthPx - calcWidthPx - marginPx
    // Top-align in compact so all rows are reachable
    val defaultY = if (isCompact) with(density) { 4.dp.toPx() } else screenHeightPx * 0.12f

    var offsetX by remember { mutableStateOf(defaultX) }
    var offsetY by remember { mutableStateOf(defaultY) }
    var snappedRight by remember { mutableStateOf(false) }
    var snappedLeft  by remember { mutableStateOf(false) }

    // ── Calculator state ──────────────────────────────────────
    var display    by remember { mutableStateOf("0") }
    var storedOp   by remember { mutableStateOf<Double?>(null) }
    var pendingOp  by remember { mutableStateOf<String?>(null) }
    var reset      by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("") }
    var lastFunc  by remember { mutableStateOf("") }

    // ── Helpers ───────────────────────────────────────────────
    fun curVal(): Double = display.toDoubleOrNull() ?: 0.0

    fun setResult(v: Double) {
        display = formatResult(v); reset = true
    }

    fun inputDigit(d: String) {
        if (reset) { display = ""; reset = false }
        if (display == "0") display = d else display += d
    }

    fun inputDot() {
        if (reset) { display = "0"; reset = false }
        if (!display.contains(".")) display += "."
    }

    fun backspace() {
        if (display.length > 1) display = display.dropLast(1) else display = "0"
    }

    fun clear() { display = "0"; storedOp = null; pendingOp = null; reset = false; expression = "" }

    fun applyOp(op: String) {
        val v = curVal()
        if (storedOp != null && pendingOp != null && !reset) {
            storedOp = compute(storedOp!!, v, pendingOp!!)
            pendingOp = op; display = formatResult(storedOp!!)
            expression = "$display $op "; reset = true
        } else {
            storedOp = v; pendingOp = op
            expression = "$display $op "; reset = true
        }
    }

    fun evaluate() {
        val v = curVal()
        if (storedOp != null && pendingOp != null) {
            val r = compute(storedOp!!, v, pendingOp!!)
            expression = "$expression$display ="; display = formatResult(r)
            storedOp = null; pendingOp = null
        }
        reset = true
    }

    fun applyUnary(op: (Double) -> Double) {
        val v = curVal()
        expression = "$lastFunc$display"
        setResult(op(v))
    }

    fun setConstant(c: Double) {
        if (reset) { reset = false }; display = formatResult(c); reset = true
    }

    fun toggleSign() {
        val v = curVal()
        if (v != 0.0) display = formatResult(-v)
    }

    // ── Snap helpers ─────────────────────────────────────────
    fun snapR() { snappedRight = true; snappedLeft = false; offsetX = screenWidthPx - tabWidthPx }
    fun snapL() { snappedLeft = true; snappedRight = false; offsetX = tabWidthPx - calcWidthPx }
    fun unsnap() {
        if (snappedRight) offsetX = screenWidthPx - calcWidthPx - marginPx
        else if (snappedLeft) offsetX = marginPx
        snappedRight = false; snappedLeft = false
    }
    fun clampX(x: Float) = x.coerceIn(-calcWidthPx + tabWidthPx, screenWidthPx - tabWidthPx)
    fun clampY(y: Float) = y.coerceIn(0f, screenHeightPx - clampBotPx)

    // ── UI ──────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().zIndex(Float.MAX_VALUE)) {

        // Pull tab — right
        if (snappedRight) {
            Box(Modifier
                .offset { IntOffset((screenWidthPx - tabWidthPx).roundToInt(), offsetY.roundToInt()) }
                .width(tabW).height(tabH).zIndex(Float.MAX_VALUE)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(AmberGold).clickable { unsnap() }
                .pointerInput(Unit) { detectDragGestures { _, da ->
                    if (offsetX + da.x < screenWidthPx - calcWidthPx * 0.5f) unsnap()
                } },
                contentAlignment = Alignment.Center
            ) { Text("◀", color = InkStoneBlack, fontSize = tabFont, fontWeight = FontWeight.Bold) }
        }

        // Pull tab — left
        if (snappedLeft) {
            Box(Modifier
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .width(tabW).height(tabH).zIndex(Float.MAX_VALUE)
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(AmberGold).clickable { unsnap() }
                .pointerInput(Unit) { detectDragGestures { _, da ->
                    if (offsetX + calcWidthPx + da.x > calcWidthPx * 0.5f) unsnap()
                } },
                contentAlignment = Alignment.Center
            ) { Text("▶", color = InkStoneBlack, fontSize = tabFont, fontWeight = FontWeight.Bold) }
        }

        // Calculator card
        if (!snappedRight && !snappedLeft) {
            Box(Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(calcW).zIndex(Float.MAX_VALUE)
                .clip(RoundedCornerShape(14.dp)).background(CardDark)
            ) {
                Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
                    // ── Drag handle ────────────────────────
                    Row(Modifier.fillMaxWidth().height(dragH)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = {
                                val cx = offsetX + calcWidthPx / 2
                                if (cx > screenWidthPx * 0.8f) snapR()
                                else if (cx < screenWidthPx * 0.2f) snapL()
                            }) { ch, da -> ch.consume(); offsetX = clampX(offsetX + da.x); offsetY = clampY(offsetY + da.y) }
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(start = 6.dp)) {
                            repeat(3) { Box(Modifier.size(dotSize).clip(CircleShape).background(TextCream.copy(alpha = 0.3f))) }
                        }
                        Text("✕", color = TextCream.copy(alpha = 0.6f), fontSize = closeF,
                            modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
                    }

                    // ── Expression line (hidden in compact) ──
                    if (!isCompact) {
                        Text(expression.ifBlank { " " }, color = TextCream.copy(alpha = 0.4f),
                            fontSize = 11.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }

                    // ── Display ─────────────────────────────
                    Box(Modifier.fillMaxWidth().height(displayH).clip(RoundedCornerShape(8.dp))
                        .background(InkStoneBlack).padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(display, color = TextCream, fontSize = displayFont, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    // ── Keypad ──────────────────────────────
                    KEY_ROWS.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { key ->
                                if (key.label.isEmpty()) {
                                    Spacer(Modifier.weight(key.span.toFloat()))
                                } else {
                                    val w = if (key.span >= 4) 1f else key.span.toFloat()
                                    Box(Modifier.weight(w).aspectRatio(if (key.span >= 4) 4.2f * keyRatio else keyRatio)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(when {
                                            key.isEquals -> AmberGold
                                            key.isOperator -> AmberGold.copy(alpha = 0.3f)
                                            key.label == "C" -> Color(0xFFD94848)
                                            key.label == "⌫" -> AmberGold.copy(alpha = 0.2f)
                                            key.isFunc -> Color(0xFF2F4050)
                                            else -> InkStoneBlack
                                        })
                                        .clickable {
                                            lastFunc = key.label
                                            when (key.label) {
                                                "C" -> clear()
                                                "⌫" -> backspace()
                                                "÷", "×", "−", "+", "xʸ" -> applyOp(key.label)
                                                "=" -> evaluate()
                                                "." -> inputDot()
                                                "sin" -> applyUnary { sin(Math.toRadians(it)) }
                                                "cos" -> applyUnary { cos(Math.toRadians(it)) }
                                                "tan" -> applyUnary { tan(Math.toRadians(it)) }
                                                "√" -> applyUnary { sqrt(it) }
                                                "x²" -> applyUnary { it * it }
                                                "π" -> setConstant(PI)
                                                "e" -> setConstant(E)
                                                "±" -> toggleSign()
                                                "%" -> applyUnary { it / 100.0 }
                                                else -> inputDigit(key.label)
                                            }
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val isSci = key.isFunc && key.label.length > 1
                                        Text(key.label,
                                            color = when {
                                                key.isEquals -> InkStoneBlack
                                                key.isFunc -> TextCream.copy(alpha = 0.8f)
                                                key.isOperator -> AmberGold
                                                else -> TextCream
                                            },
                                            fontSize = if (isSci) funcFont else if (key.isEquals) eqFont else keyFont,
                                            fontWeight = if (key.isFunc || key.isOperator || key.isEquals) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Arithmetic ──────────────────────────────────────────────────────

private fun compute(a: Double, b: Double, op: String): Double = when (op) {
    "+" -> a + b; "−" -> a - b; "×" -> a * b
    "÷" -> if (b != 0.0) a / b else Double.NaN
    "xʸ" -> a.pow(b)
    else -> b
}

private fun formatResult(v: Double): String {
    if (v.isNaN()) return "错误"
    if (v.isInfinite()) return "错误"
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.10f".format(v).trimEnd('0').trimEnd('.')
}
