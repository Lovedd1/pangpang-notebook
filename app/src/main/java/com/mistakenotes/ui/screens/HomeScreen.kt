package com.mistakenotes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.R
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.ui.components.QuestionTypeFilter
import com.mistakenotes.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToImport: (Long?) -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    onNavigateToAnalysis: () -> Unit = {},
    onNavigateToBrowse: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToMastered: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTodaySection by remember { mutableStateOf(true) }
    var showOverdueSection by remember { mutableStateOf(true) }
    val overExpandStates = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("CPA 错题笔记", color = AmberGold, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Subject filter chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.currentSubjectId == null,
                            onClick = { viewModel.selectSubject(null) },
                            label = { Text("全部") },
                            colors = filterChipColors()
                        )
                    }
                    val filteredSubjects = uiState.subjects.filter { it.id in uiState.cardSubjectIds }
                    items(filteredSubjects) { subject ->
                        val subjColor = Color(subject.color)
                        FilterChip(
                            selected = uiState.currentSubjectId == subject.id,
                            onClick = { viewModel.selectSubject(subject.id) },
                            label = { Text(subject.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = subjColor,
                                selectedLabelColor = InkStoneBlack,
                                containerColor = CardDark,
                                labelColor = subjColor
                            )
                        )
                    }
                }
            }

            // Stats summary row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStat(label = "总错题", value = "${uiState.totalMistakes}", color = TextCream, modifier = Modifier.weight(1f))
                    MiniStat(label = "已掌握", value = "${uiState.masteredCount}", color = SuccessGreen, modifier = Modifier.weight(1f))
                }
            }

            // ============================================================
            // Today's review section
            // ============================================================
            item {
                SectionHeader(
                    title = "今日待复习",
                    count = uiState.todayCards.size,
                    expanded = showTodaySection,
                    onToggle = { showTodaySection = !showTodaySection }
                )
            }

            item {
                QuestionTypeFilter(
                    selected = uiState.todayQuestionTypes,
                    onSelectionChange = { viewModel.selectTodayQuestionTypes(it) }
                )
            }

            item {
                AnimatedVisibility(
                    visible = showTodaySection,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.todayCards.isEmpty()) {
                            EmptyHint("暂无今日待复习题目")
                        } else {
                            val allMistakes = uiState.todayCards.map { it.mistake }
                            val preReviewedSet = uiState.todayCards
                                .mapIndexedNotNull { i, c -> if (c.isReviewed) i else null }
                                .toSet()
                            val preReviewedResultMap = uiState.todayCards
                                .mapIndexedNotNull { i, c ->
                                    if (c.isReviewed) i to when (c.lastResult) {
                                        ReviewResult.CORRECT -> true
                                        ReviewResult.WRONG -> false
                                        else -> null
                                    } else null
                                }.toMap()
                            uiState.todayCards.forEachIndexed { index, card ->
                                TodayCardItem(
                                    info = card,
                                    onClick = {
                                        ReviewSession.start(
                                            queue = allMistakes,
                                            startIndex = index,
                                            isViewingResult = card.isReviewed,
                                            lastResult = card.lastResult,
                                            preReviewedIndices = preReviewedSet,
                                            preReviewedResults = preReviewedResultMap
                                        )
                                        onNavigateToReview()
                                    },
                                    onEdit = { onNavigateToImport(card.mistake.id) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // "Start review" — all today's cards in list order, start from first unreviewed
                            val unreviewed = uiState.todayCards.filter { !it.isReviewed }
                            if (unreviewed.isNotEmpty()) {
                                val firstUnreviewedIdx = uiState.todayCards.indexOfFirst { !it.isReviewed }
                                Button(
                                    onClick = {
                                        ReviewSession.start(
                                            queue = allMistakes,
                                            startIndex = firstUnreviewedIdx.coerceAtLeast(0),
                                            preReviewedIndices = preReviewedSet,
                                            preReviewedResults = preReviewedResultMap
                                        )
                                        onNavigateToReview()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberGold,
                                        contentColor = InkStoneBlack
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("开始今日复习 (${unreviewed.size})", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ============================================================
            // Overdue section
            // ============================================================
            item {
                SectionHeader(
                    title = "逾期",
                    count = uiState.overdueCards.size,
                    expanded = showOverdueSection,
                    onToggle = { showOverdueSection = !showOverdueSection }
                )
            }

            item {
                QuestionTypeFilter(
                    selected = uiState.overdueQuestionTypes,
                    onSelectionChange = { viewModel.selectOverdueQuestionTypes(it) }
                )
            }

            item {
                AnimatedVisibility(
                    visible = showOverdueSection,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (uiState.overdueCards.isEmpty()) {
                            EmptyHint("暂无逾期题目")
                        } else {
                            // Group by overdueDays, descending
                            val grouped = uiState.overdueCards.groupBy { it.overdueDays }
                            val sortedDays = grouped.keys.sortedDescending()
                            // Build flat indexed list for startIndex lookup
                            val flatList = uiState.overdueCards.map { it.mistake }

                            sortedDays.forEach { days ->
                                val dayGroup = grouped[days] ?: return@forEach
                                val isExpanded = overExpandStates[days] ?: false

                                // Day-group header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark)
                                        .clickable { overExpandStates[days] = !isExpanded }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(ErrorRed.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "逾期${days}天",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "(${dayGroup.size}题)",
                                        color = TextCream.copy(alpha = 0.4f),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = TextCream.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Expanded: type sub-groups
                                AnimatedVisibility(visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Sub-group by question type
                                        val typeOrder = listOf(QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.ESSAY)
                                        val typeLabels = mapOf(
                                            QuestionType.SINGLE_CHOICE to "单选",
                                            QuestionType.MULTI_CHOICE to "多选",
                                            QuestionType.ESSAY to "主观题"
                                        )
                                        // Running counter across type groups within this day group
                                        var blockNumber = 1

                                        for (qt in typeOrder) {
                                            val typeGroup = dayGroup.filter { it.mistake.questionType == qt }
                                            if (typeGroup.isEmpty()) continue

                                            // Type label
                                            Text(
                                                text = "${typeLabels[qt]} (${typeGroup.size})",
                                                color = TextCream.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )

                                            // Question number grid (6 cols, small blocks)
                                            val cols = 6
                                            val rows = (typeGroup.size + cols - 1) / cols
                                            for (row in 0 until rows) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    for (col in 0 until cols) {
                                                        val idx = row * cols + col
                                                        if (idx < typeGroup.size) {
                                                            val card = typeGroup[idx]
                                                            // Find original index in flat overdue list
                                                            val originalIdx = flatList.indexOf(card.mistake)

                                                            Box(
                                                                modifier = Modifier
                                                                    .size(100.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(CardDark)
                                                                    .clickable {
                                                                        ReviewSession.start(
                                                                            queue = flatList,
                                                                            startIndex = originalIdx
                                                                        )
                                                                        onNavigateToReview()
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "$blockNumber",
                                                                    color = TextCream.copy(alpha = 0.7f),
                                                                    fontSize = 28.sp
                                                                )
                                                            }
                                                            blockNumber++
                                                        }
                                                        // Fixed-size blocks: empty cells just skipped
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
            }

            // ============================================================
            // Quick actions
            // ============================================================
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                QuickActionCard(
                    icon = Icons.Default.CameraAlt,
                    title = "拍照录入",
                    description = "拍摄题目快速录入",
                    onClick = { onNavigateToImport(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.List,
                    title = "错题浏览",
                    description = "按科目章节查看全部错题",
                    onClick = onNavigateToBrowse,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.Star,
                    title = "收藏夹",
                    description = "查看收藏的错题",
                    onClick = onNavigateToFavorites,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.CheckCircle,
                    title = "已掌握",
                    description = "查看已掌握的错题",
                    onClick = onNavigateToMastered,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                QuickActionCard(
                    icon = Icons.Default.Analytics,
                    title = "错题分析",
                    description = "查看学习统计数据",
                    onClick = onNavigateToAnalysis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ============================================================
// Components
// ============================================================

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextCream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text("($count)", color = TextCream.copy(alpha = 0.5f), fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = TextCream.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TodayCardItem(
    info: TodayCardInfo,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (info.isReviewed) CardDark.copy(alpha = 0.6f) else CardDark
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Review status badge
            if (info.lastResult == ReviewResult.SKIP) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberGold.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("已跳过", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold.copy(alpha = 0.6f))
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else if (info.isReviewed && info.lastResult != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (info.lastResult) {
                                ReviewResult.CORRECT -> SuccessGreen.copy(alpha = 0.2f)
                                ReviewResult.WRONG -> ErrorRed.copy(alpha = 0.2f)
                                else -> CardDark
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (info.lastResult) {
                            ReviewResult.CORRECT -> "✓ 正确"
                            ReviewResult.WRONG -> "✗ 错误"
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (info.lastResult) {
                            ReviewResult.CORRECT -> SuccessGreen
                            ReviewResult.WRONG -> ErrorRed
                            else -> TextCream.copy(alpha = 0.5f)
                        }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                // Unreviewed tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberGold.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("未复习", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AmberGold.copy(alpha = 0.3f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (info.mistake.questionType) {
                        QuestionType.SINGLE_CHOICE -> "单选"
                        QuestionType.MULTI_CHOICE -> "多选"
                        QuestionType.ESSAY -> "主观"
                    },
                    fontSize = 10.sp,
                    color = AmberGold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title + subject/chapter (single line)
            val titleText = buildString {
                append(info.mistake.questionText ?: info.mistake.title ?: "(无题目)")
                if (info.subjectName.isNotEmpty()) {
                    append(" · ")
                    append(info.subjectName)
                    append(" · ")
                    append(info.chapterName)
                }
            }
            Text(
                text = titleText,
                color = TextCream,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))
            ReviewProgressText(correctCount = info.correctCount)

            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = TextCream.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ReviewProgressText(correctCount: Int) {
    val (text, color) = when {
        correctCount >= 3 -> "已掌握" to SuccessGreen.copy(alpha = 0.8f)
        else -> "第${correctCount + 1}次复习 · 还差${3 - correctCount}次掌握" to AmberGold.copy(alpha = 0.7f)
    }
    Text(
        text = text,
        color = color,
        fontSize = 11.sp
    )
}

@Composable
private fun OverdueCardItem(
    info: OverdueCardInfo,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Overdue badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ErrorRed.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "逾期${info.overdueDays}天",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ErrorRed.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (info.mistake.questionType) {
                        QuestionType.SINGLE_CHOICE -> "单选"
                        QuestionType.MULTI_CHOICE -> "多选"
                        QuestionType.ESSAY -> "主观"
                    },
                    fontSize = 10.sp,
                    color = ErrorRed.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title + subject/chapter (single line)
            val overdueTitleText = buildString {
                append(info.mistake.questionText ?: info.mistake.title ?: "(无题目)")
                if (info.subjectName.isNotEmpty()) {
                    append(" · ")
                    append(info.subjectName)
                    append(" · ")
                    append(info.chapterName)
                }
            }
            Text(
                text = overdueTitleText,
                color = TextCream,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))
            ReviewProgressText(correctCount = info.correctCount)

            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = TextCream.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = TextCream.copy(alpha = 0.4f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = TextCream.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AmberGold,
    selectedLabelColor = InkStoneBlack,
    containerColor = CardDark,
    labelColor = TextCream
)

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = AmberGold
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = TextCream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(description, color = TextCream.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
