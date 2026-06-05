package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.TextCream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 录入时间行：显示当前 entryDate（默认今天），点击弹出 DatePickerDialog。
 * @param entryDate 已选 epoch millis（null 表示未选 = 默认今天）
 * @param onDateSelected 选择日期回调（接收 00:00:00 归一化后的 millis）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EntryDateRow(
    entryDate: Long?,
    onDateSelected: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today00 = cal.timeInMillis

    val displayDate = entryDate ?: today00
    val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(displayDate))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "录入时间：$dateText",
            color = TextCream,
            fontSize = 15.sp
        )
        Icon(
            Icons.Default.CalendarToday,
            contentDescription = "选择日期",
            tint = AmberGold,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = displayDate
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        // 归一化为 00:00:00
                        val c = Calendar.getInstance().apply {
                            timeInMillis = selected
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        onDateSelected(c.timeInMillis)
                    }
                    showPicker = false
                }) {
                    Text("确定", color = AmberGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("取消", color = TextCream)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
