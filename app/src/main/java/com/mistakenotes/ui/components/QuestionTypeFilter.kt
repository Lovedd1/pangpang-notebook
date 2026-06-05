package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream

private data class QuestionTypeOption(
    val type: QuestionType,
    val label: String
)

private val QuestionTypeOptions = listOf(
    QuestionTypeOption(QuestionType.SINGLE_CHOICE, "单选"),
    QuestionTypeOption(QuestionType.MULTI_CHOICE, "多选"),
    QuestionTypeOption(QuestionType.ESSAY, "主观题")
)

@OptIn(ExperimentalMaterial3Api::class)
/**
 * 3 chip 多选：单选/多选/主观题。
 * 空集 = 不过滤（全部显示）。调用方在过滤逻辑中需将"空集"判定为"不过滤"。
 *
 * @param selected 当前选中的题型集合
 * @param onSelectionChange 选中状态变化回调
 * @param modifier 外部 modifier
 */
@Composable
fun QuestionTypeFilter(
    selected: Set<QuestionType>,
    onSelectionChange: (Set<QuestionType>) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(QuestionTypeOptions, key = { it.type }) { option ->
            val isSelected = option.type in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectionChange(
                        if (isSelected) selected - option.type
                        else selected + option.type
                    )
                },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberGold,
                    selectedLabelColor = InkStoneBlack,
                    containerColor = CardDark,
                    labelColor = TextCream
                )
            )
        }
    }
}
