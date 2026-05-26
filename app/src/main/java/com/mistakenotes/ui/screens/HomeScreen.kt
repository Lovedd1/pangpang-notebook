package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToImport: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    onNavigateToAnalysis: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CPA 错题笔记",
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InkStoneBlack
                )
            )
        },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Subject Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.currentSubjectId == null,
                        onClick = { viewModel.selectSubject(null) },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = InkStoneBlack,
                            containerColor = CardDark,
                            labelColor = TextCream
                        )
                    )
                }
                items(uiState.subjects) { subject ->
                    FilterChip(
                        selected = uiState.currentSubjectId == subject.id,
                        onClick = { viewModel.selectSubject(subject.id) },
                        label = { Text(subject.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = InkStoneBlack,
                            containerColor = CardDark,
                            labelColor = TextCream
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Statistics Cards - 2x2 Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "待复习",
                    value = uiState.toReviewCount.toString(),
                    color = AmberGold,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToReview
                )
                StatCard(
                    title = "逾期",
                    value = uiState.overdueCount.toString(),
                    color = ErrorRed,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "已掌握",
                    value = uiState.masteredCount.toString(),
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                StatCard(
                    title = "总错题",
                    value = uiState.totalMistakes.toString(),
                    color = TextCream,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Action Cards
            QuickActionCard(
                icon = Icons.Default.CameraAlt,
                title = "拍照录入",
                description = "拍摄题目快速录入",
                onClick = onNavigateToImport,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            QuickActionCard(
                icon = Icons.Default.Search,
                title = "搜索题目",
                description = "查找错题内容",
                onClick = onNavigateToReview,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            QuickActionCard(
                icon = Icons.Default.Analytics,
                title = "错题分析",
                description = "查看学习统计数据",
                onClick = onNavigateToAnalysis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = TextCream.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Text(
                    text = title,
                    color = TextCream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = TextCream.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}