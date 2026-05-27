package com.mistakenotes.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.R
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToReview: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isFavoritesMode) "收藏夹" else "错题浏览",
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextCream)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            // Subject filter
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                items(uiState.subjects) { subject ->
                    FilterChip(
                        selected = uiState.currentSubjectId == subject.id,
                        onClick = { viewModel.selectSubject(subject.id) },
                        label = { Text(subject.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(subject.color),
                            selectedLabelColor = InkStoneBlack,
                            containerColor = CardDark,
                            labelColor = Color(subject.color)
                        )
                    )
                }
            }

            // Chapter filter
            if (uiState.chapters.isNotEmpty()) {
                ChapterDropdown(
                    chapters = uiState.chapters,
                    selectedChapterId = uiState.currentChapterId,
                    onChapterSelected = { viewModel.selectChapter(it) }
                )
            }

            HorizontalDivider(color = CardDark)

            // List
            if (uiState.items.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff, null,
                            tint = TextCream.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (uiState.isFavoritesMode) "暂无收藏" else "暂无错题",
                            color = TextCream.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.items) { item ->
                        BrowseCard(
                            item = item,
                            isFavoritesMode = uiState.isFavoritesMode,
                            onClick = {
                                ReviewSession.start(queue = listOf(item.mistake))
                                onNavigateToReview()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(item.mistake) },
                            onToggleTop = { viewModel.toggleTop(item.mistake) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(
    item: BrowseItem,
    isFavoritesMode: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleTop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: type badge + title + pin indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmberGold.copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (item.mistake.questionType) {
                            QuestionType.SINGLE_CHOICE -> "单选"
                            QuestionType.MULTI_CHOICE -> "多选"
                            QuestionType.ESSAY -> "主观"
                        },
                        fontSize = 10.sp,
                        color = AmberGold
                    )
                }
                if (item.mistake.isTop) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AmberGold.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("置顶", fontSize = 10.sp, color = AmberGold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.mistake.questionText
                            ?: item.mistake.title
                            ?: "(无题目)",
                        color = TextCream,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.chapterName} · 知识点*",
                        color = TextCream.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val cc = item.ebbinghausCount
                    val (progressText, progressColor) = when {
                        cc >= 4 -> "已掌握" to SuccessGreen.copy(alpha = 0.8f)
                        else -> "第${cc + 1}次复习 · 还差${4 - cc}次掌握" to AmberGold.copy(alpha = 0.7f)
                    }
                    Text(
                        text = progressText,
                        color = progressColor,
                        fontSize = 11.sp
                    )
                }
            }

            // Bottom row: review pattern + counts + actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.reviewPattern.isNotEmpty()) {
                    item.reviewPattern.forEach { c ->
                        Image(
                            painter = painterResource(
                                id = if (c == '✓') R.drawable.ic_correct else R.drawable.ic_wrong
                            ),
                            contentDescription = c.toString(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Text(
                        text = "未复习",
                        fontSize = 13.sp,
                        color = TextCream.copy(alpha = 0.35f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✓ ${item.correctCount}", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    Text("✗ ${item.wrongCount}", fontSize = 12.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Pin button
                IconButton(
                    onClick = onToggleTop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = if (item.mistake.isTop) R.drawable.ic_pin_on else R.drawable.ic_pin_off
                        ),
                        contentDescription = "置顶",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Favorite button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = if (item.mistake.isFavorite) R.drawable.ic_fav_on else R.drawable.ic_fav_off
                        ),
                        contentDescription = if (item.mistake.isFavorite) "取消收藏" else "收藏",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = TextCream.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChapterDropdown(
    chapters: List<Chapter>,
    selectedChapterId: Long?,
    onChapterSelected: (Long?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selected = chapters.find { it.id == selectedChapterId }

    OutlinedTextField(
        value = selected?.name ?: "全部章节",
        onValueChange = {},
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextCream)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { showDialog = true },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextCream,
            unfocusedTextColor = TextCream,
            focusedBorderColor = AmberGold,
            unfocusedBorderColor = CardDark,
            focusedLabelColor = AmberGold,
            unfocusedLabelColor = TextCream.copy(alpha = 0.6f),
            cursorColor = AmberGold,
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark
        ),
        shape = RoundedCornerShape(10.dp)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择章节", color = AmberGold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = { onChapterSelected(null); showDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "全部章节",
                            color = if (selectedChapterId == null) AmberGold else TextCream,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    chapters.forEach { chapter ->
                        TextButton(
                            onClick = { onChapterSelected(chapter.id); showDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                chapter.name,
                                color = if (chapter.id == selectedChapterId) AmberGold else TextCream,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消", color = TextCream.copy(alpha = 0.6f))
                }
            },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AmberGold,
    selectedLabelColor = InkStoneBlack,
    containerColor = CardDark,
    labelColor = TextCream
)
