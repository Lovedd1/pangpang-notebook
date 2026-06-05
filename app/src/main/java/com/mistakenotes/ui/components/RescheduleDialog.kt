package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream
import kotlin.math.roundToInt

/**
 * 重新安排复习：1~10 天滑动选择，默认 7
 * @param initialDays 初始值（默认 7）
 * @param onConfirm 确认回调，参数为天数（1-10）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    initialDays: Int = 7,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableStateOf(initialDays.coerceIn(1, 10).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("N 天后复习", color = AmberGold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current value display
                Text(
                    text = "${sliderValue.roundToInt()} 天",
                    color = TextCream,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..10f,
                    steps = 8, // 10 - 1 - 1 = 8 tick marks
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AmberGold,
                        activeTrackColor = AmberGold,
                        inactiveTrackColor = CardDark,
                        activeTickColor = AmberGold,
                        inactiveTickColor = TextCream.copy(alpha = 0.3f)
                    )
                )

                // Min/max labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 天", color = TextCream.copy(alpha = 0.4f), fontSize = 12.sp)
                    Text("10 天", color = TextCream.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue.roundToInt()); onDismiss() }) {
                Text("确认", color = AmberGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextCream)
            }
        },
        containerColor = CardDark,
        titleContentColor = AmberGold
    )
}
