package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.ErrorRed
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.SuccessGreen
import com.mistakenotes.ui.theme.TextCream

/**
 * 跳题弹窗：题号网格 + 状态图标
 * @param total 总题数
 * @param currentIndex 当前题号 (0-based)
 * @param results Map<index, ReviewResult?> — null=未复习，CORRECT/WRONG/SKIP
 * @param onJump 跳转到指定 index 回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToQuestionDialog(
    total: Int,
    currentIndex: Int,
    results: Map<Int, ReviewResult?>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到题目", color = AmberGold) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed((0 until total).toList()) { index, _ ->
                    val result = results[index]
                    val isCurrent = index == currentIndex

                    val (bg, fg) = when {
                        isCurrent -> AmberGold to InkStoneBlack
                        result == ReviewResult.CORRECT -> SuccessGreen.copy(alpha = 0.3f) to SuccessGreen
                        result == ReviewResult.WRONG -> ErrorRed.copy(alpha = 0.3f) to ErrorRed
                        result == ReviewResult.SKIP -> AmberGold.copy(alpha = 0.2f) to AmberGold
                        else -> CardDark to TextCream.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { onJump(index); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = fg,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextCream)
            }
        },
        containerColor = CardDark,
        titleContentColor = AmberGold
    )
}
