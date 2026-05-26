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
                title = { Text("错题浏览", color = AmberGold, fontWeight = FontWeight.Bold) },
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
                        colors = filterChipColors()
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
                        Text("暂无错题", color = TextCream.copy(alpha = 0.4f), fontSize = 15.sp)
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
                            onClick = {
                                ReviewSession.start(queue = listOf(item.mistake))
                                onNavigateToReview()
                            }
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
    onClick: () -> Unit
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
            // Top row: type badge + title
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.mistake.questionText
                        ?: item.mistake.title
                        ?: "(无题目)",
                    color = TextCream,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom row: review pattern + counts
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
@OptIn(ExperimentalMaterial3Api::class)
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

private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AmberGold,
    selectedLabelColor = InkStoneBlack,
    containerColor = CardDark,
    labelColor = TextCream
)
