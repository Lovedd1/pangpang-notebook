package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.ui.theme.*

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "错题分析",
                        color = TextCream,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextCream
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InkStoneBlack
                )
            )
        },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmberGold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Subject Mastery
                item {
                    Text(
                        text = "科目掌握度",
                        color = TextCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.subjectStats) { stat ->
                    SubjectStatCard(stat = stat)
                }

                // Section 2: Chapter Distribution (grouped by subject)
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "章节错题分布",
                        color = TextCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Group chapters by subject
                val groupedChapters = uiState.chapterStats.groupBy { it.subjectName }
                groupedChapters.forEach { (subjectName, stats) ->
                    val subjColor = if (stats.isNotEmpty()) Color(stats.first().subjectColor) else AmberGold
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(subjColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = subjectName,
                                color = subjColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(stats) { stat ->
                        ChapterStatCard(stat = stat)
                    }
                }

                // Section 3: Weak Knowledge Points
                if (uiState.topWeakKnowledgePoints.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = AmberGold
                            )
                            Text(
                                text = "重点复习知识点",
                                color = TextCream,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(uiState.topWeakKnowledgePoints) { (kp, count) ->
                        WeakKnowledgePointCard(knowledgePoint = kp, mistakeCount = count)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectStatCard(stat: SubjectStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.subject.name,
                    color = TextCream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(stat.masteryRate * 100).toInt()}%",
                    color = when {
                        stat.masteryRate >= 0.8f -> SuccessGreen
                        stat.masteryRate >= 0.5f -> AmberGold
                        else -> ErrorRed
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { stat.masteryRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    stat.masteryRate >= 0.8f -> SuccessGreen
                    stat.masteryRate >= 0.5f -> AmberGold
                    else -> ErrorRed
                },
                trackColor = InkStoneBlack,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "错题数: ${stat.totalMistakes}",
                    color = TextCream.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Text(
                    text = "正确次数: ${stat.correctCount}",
                    color = TextCream.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ChapterStatCard(stat: ChapterStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.chapter.name,
                    color = TextCream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "正确率: ${(stat.correctRate * 100).toInt()}%",
                    color = TextCream.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }

            Badge(
                containerColor = when {
                    stat.correctRate >= 0.8f -> SuccessGreen
                    stat.correctRate >= 0.5f -> AmberGold
                    else -> ErrorRed
                }
            ) {
                Text(
                    text = "${stat.mistakeCount}",
                    color = InkStoneBlack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun WeakKnowledgePointCard(
    knowledgePoint: KnowledgePoint,
    mistakeCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = knowledgePoint.name.ifEmpty { "知识点 #${knowledgePoint.id}" },
                color = TextCream,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "错 $mistakeCount 次",
                color = ErrorRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
