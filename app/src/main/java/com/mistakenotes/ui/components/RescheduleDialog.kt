package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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

/**
 * 重新安排复习：1~10 天步进器，默认 7
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
    var days by remember { mutableStateOf(initialDays.coerceIn(1, 10)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("N 天后复习", color = AmberGold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (days > 1) days-- },
                    enabled = days > 1
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "减少",
                        tint = if (days > 1) AmberGold else TextCream.copy(alpha = 0.3f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$days 天",
                    color = TextCream,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { if (days < 10) days++ },
                    enabled = days < 10
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "增加",
                        tint = if (days < 10) AmberGold else TextCream.copy(alpha = 0.3f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days); onDismiss() }) {
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
