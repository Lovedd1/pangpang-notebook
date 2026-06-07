package com.mistakenotes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mistakenotes.R
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.domain.model.getAnswerImagePaths
import com.mistakenotes.domain.model.getQuestionImagePaths
import com.mistakenotes.ui.components.AdaptiveImage
import com.mistakenotes.ui.components.ImagePreviewDialog
import com.mistakenotes.ui.components.JumpToQuestionDialog
import com.mistakenotes.ui.theme.*
import java.io.File

private val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentIndexValue by viewModel.currentIndexFlow.collectAsState()
    val reviewQueue by viewModel.reviewQueueFlow.collectAsState()
    val maxImageHeight = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
    // Per-question answer image visibility (independent per question)
    val answerImageVisible = remember { mutableStateMapOf<Int, Boolean>() }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showQuestionList by remember { mutableStateOf(false) }
    var showMasteredConfirm by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<java.io.File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val displayText = if (uiState.currentMistake != null) {
                        "${currentIndexValue + 1} / ${viewModel.queueSize}"
                    } else "复习"
                    Text(
                        text = displayText,
                        color = TextCream,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showJumpDialog = true }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextCream)
                    }
                },
                actions = {
                    if (uiState.currentMistake != null) {
                        // Question list button
                        IconButton(onClick = { showQuestionList = true }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "选题",
                                tint = AmberGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Favorite button
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Image(
                                painter = painterResource(
                                    id = if (uiState.currentMistake!!.isFavorite) R.drawable.ic_fav_on else R.drawable.ic_fav_off
                                ),
                                contentDescription = if (uiState.currentMistake!!.isFavorite) "取消收藏" else "收藏",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Mastered button — always visible
                        IconButton(onClick = { showMasteredConfirm = true }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已掌握",
                                tint = AmberGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberGold)
                }
            }
            uiState.reviewComplete -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("今日复习完成", color = AmberGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("所有待复习题目已完成", color = TextCream.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
            uiState.currentMistake != null -> {
                val mistake = reviewQueue[page]
                val qState = uiState.perQuestionState[page]
                val showResult = qState?.showAnswer ?: false

                val pagerState = rememberPagerState(
                    initialPage = currentIndexValue,
                    pageCount = { viewModel.queueSize }
                )

                // Sync pager when external navigation happens (bottom sheet, next btn)
                LaunchedEffect(currentIndexValue) {
                    if (pagerState.currentPage != currentIndexValue) {
                        pagerState.scrollToPage(currentIndexValue)
                    }
                }

                // Sync ViewModel when user swipes pager
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != currentIndexValue) {
                        viewModel.jumpTo(pagerState.currentPage)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(remember(page) { ScrollState(0) })
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: type badge
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = when (mistake.questionType) {
                                QuestionType.SINGLE_CHOICE -> "单选题"
                                QuestionType.MULTI_CHOICE -> "多选题"
                                QuestionType.ESSAY -> "主观题"
                            },
                            color = AmberGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Question card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("题目", color = AmberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            // Question images
                            val questionPaths = mistake.getQuestionImagePaths()
                            if (questionPaths.size > 1) {
                                QuestionImagePager(
                                    paths = questionPaths,
                                    onImageClick = { path -> previewFile = File(path) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            } else if (questionPaths.isNotEmpty()) {
                                AdaptiveImage(
                                    file = File(questionPaths.first()),
                                    onClick = { previewFile = File(questionPaths.first()) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (!mistake.questionText.isNullOrBlank()) {
                                Text(
                                    mistake.questionText,
                                    color = TextCream,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }
                            if (!mistake.title.isNullOrBlank() && mistake.title != mistake.questionText) {
                                Text(
                                    mistake.title,
                                    color = TextCream.copy(alpha = 0.9f),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // Options for choice questions
                    if (mistake.questionType != QuestionType.ESSAY) {
                        val options = mistake.options?.split("|") ?: emptyList()

                        Text("选项", color = TextCream.copy(alpha = 0.6f), fontSize = 14.sp)

                        options.forEachIndexed { index, optionText ->
                            val label = LABELS.getOrElse(index) { "${index}" }
                            val displayText = if (optionText.isBlank()) label else "$label. $optionText"

                            val isSelected = index in (qState?.selectedOptionIndices ?: emptySet())
                            val isCorrectAnswer = showResult && index in (qState?.correctIndices ?: emptySet())
                            val isWrongSelection = showResult && isSelected && !isCorrectAnswer

                            val bgColor = when {
                                isCorrectAnswer -> SuccessGreen.copy(alpha = 0.2f)
                                isWrongSelection -> ErrorRed.copy(alpha = 0.2f)
                                isSelected -> AmberGold.copy(alpha = 0.15f)
                                else -> CardDark
                            }

                            val borderColor = when {
                                isCorrectAnswer -> SuccessGreen
                                isWrongSelection -> ErrorRed
                                isSelected -> AmberGold
                                else -> Color.Transparent
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !showResult) { viewModel.toggleOption(page, index) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = bgColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Letter badge
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isCorrectAnswer -> SuccessGreen
                                                    isWrongSelection -> ErrorRed
                                                    isSelected -> AmberGold
                                                    else -> CardDark
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected || isCorrectAnswer || isWrongSelection)
                                                Color(0xFF111111) else TextCream,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Text(
                                        text = displayText,
                                        color = if (optionText.isBlank()) TextCream.copy(alpha = 0.4f) else TextCream,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Selection indicator
                                    if (mistake.questionType == QuestionType.SINGLE_CHOICE) {
                                        // Radio circle
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .border(
                                                    2.dp,
                                                    if (isSelected) AmberGold else TextCream.copy(alpha = 0.3f),
                                                    RoundedCornerShape(50)
                                                )
                                                .clip(RoundedCornerShape(50))
                                                .then(
                                                    if (isSelected) Modifier.background(AmberGold) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFF111111), RoundedCornerShape(50))
                                            )
                                        }
                                    } else {
                                        // Checkbox square
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .border(
                                                    2.dp,
                                                    if (isSelected) AmberGold else TextCream.copy(alpha = 0.3f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (isSelected) Modifier.background(AmberGold) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) Text(
                                                "",
                                                color = Color(0xFF111111),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Submit button (before answer revealed)
                        if (!showResult) {
                            Button(
                                onClick = { viewModel.submitAnswer(page) },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = (qState?.selectedOptionIndices?.isNotEmpty() ?: false),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberGold,
                                    contentColor = InkStoneBlack
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("提交答案", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Answer images section (all question types)
                    // Key answer visibility by `page` (this pager page's own index),
                    // NOT by currentIndexValue — the latter is global and changes during
                    // swipe, which would leak one question's "show answer" state onto
                    // adjacent pages. Per-page key keeps each question independent.
                    val answerPaths = mistake.getAnswerImagePaths()
                    val hasAnswer = answerPaths.isNotEmpty()
                    val isAnswerShown = answerImageVisible[page] ?: false
                    if (hasAnswer) {
                        OutlinedButton(
                            onClick = { answerImageVisible[page] = !isAnswerShown },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isAnswerShown) "隐藏答案" else "查看答案",
                                color = AmberGold,
                                fontSize = 14.sp
                            )
                        }

                        AnimatedVisibility(visible = isAnswerShown) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (mistake.questionType == QuestionType.ESSAY) "参考答案" else "答案/解析",
                                        color = AmberGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (answerPaths.size > 1) {
                                        QuestionImagePager(
                                            paths = answerPaths,
                                            onImageClick = { path -> previewFile = File(path) }
                                        )
                                    } else {
                                        AdaptiveImage(
                                            file = File(answerPaths.first()),
                                            onClick = { previewFile = File(answerPaths.first()) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Essay self-evaluation (essay only)
                    if (mistake.questionType == QuestionType.ESSAY) {

                        if (!showResult) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("主观题不评判", color = TextCream.copy(alpha = 0.5f), fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("请自行对比答案后自评", color = TextCream.copy(alpha = 0.7f), fontSize = 15.sp)
                                }
                            }
                        }

                        if (!showResult) {
                            Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.submitEssaySelfEval(page, true) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SuccessGreen,
                                    contentColor = Color(0xFF111111)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("答对了", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.submitEssaySelfEval(page, false) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ErrorRed,
                                    contentColor = TextCream
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("答错了", fontWeight = FontWeight.Bold)
                            }
                        }
                        }

                        if (!showResult) {
                            OutlinedButton(
                                onClick = { viewModel.skipEssay(page) },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, TextCream.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("跳过", color = TextCream.copy(alpha = 0.6f))
                            }
                        }
                    }

                    // Result banner (after submission)
                    if (showResult) {
                        val isCorrect = qState?.isCorrect

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (isCorrect) {
                                    true -> SuccessGreen.copy(alpha = 0.15f)
                                    false -> ErrorRed.copy(alpha = 0.15f)
                                    null -> CardDark
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (isCorrect) {
                                        true -> "✅"
                                        false -> "❌"
                                        null -> ""
                                    },
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = when (isCorrect) {
                                            true -> "回答正确！"
                                            false -> if (mistake.questionType == QuestionType.ESSAY) "已标记为错误" else "回答错误"
                                            null -> "已跳过"
                                        },
                                        color = when (isCorrect) {
                                            true -> SuccessGreen
                                            false -> ErrorRed
                                            null -> TextCream.copy(alpha = 0.7f)
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    if (isCorrect == false && mistake.questionType != QuestionType.ESSAY) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "正确答案: ${mistake.correctAnswer ?: "无"}",
                                            color = TextCream.copy(alpha = 0.7f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Next or Back button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.5.dp, TextCream.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("返回", color = TextCream.copy(alpha = 0.6f), fontSize = 16.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.nextMistake() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.5.dp, AmberGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("下一题 →", color = AmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } // Column
                } // HorizontalPager
            }
        }
    }

    // Jump to question dialog
    if (showJumpDialog && uiState.currentMistake != null) {
        JumpToQuestionDialog(
            total = viewModel.queueSize,
            currentIndex = currentIndexValue,
            results = viewModel.reviewedResultsMap.mapValues {
                if (it.value) ReviewResult.CORRECT else ReviewResult.WRONG
            },
            onJump = { viewModel.jumpTo(it) },
            onDismiss = { showJumpDialog = false }
        )
    }

    // Mark as mastered confirmation
    if (showMasteredConfirm) {
        AlertDialog(
            onDismissRequest = { showMasteredConfirm = false },
            title = { Text("标记为已掌握？", color = AmberGold) },
            text = { Text("明天起该题不再进入今日/逾期列表。", color = TextCream) },
            confirmButton = {
                TextButton(onClick = {
                    showMasteredConfirm = false
                    uiState.currentMistake?.id?.let { viewModel.markAsMastered(it) }
                }) {
                    Text("确认", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMasteredConfirm = false }) {
                    Text("取消", color = TextCream)
                }
            },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }

    // Bottom sheet: question list for quick jump — grouped by type
    if (showQuestionList && uiState.currentMistake != null) {
        ModalBottomSheet(
            onDismissRequest = { showQuestionList = false },
            containerColor = CardDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextCream.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "选择题目",
                    color = AmberGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Group by question type: single → multi → essay
                val indexedQueue = reviewQueue.mapIndexed { i, m -> i to m }
                val typeOrder = listOf(QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.ESSAY)
                val typeLabels = mapOf(
                    QuestionType.SINGLE_CHOICE to "单选",
                    QuestionType.MULTI_CHOICE to "多选",
                    QuestionType.ESSAY to "主观题"
                )

                for (qt in typeOrder) {
                    val group = indexedQueue.filter { it.second.questionType == qt }
                    if (group.isEmpty()) continue

                    // Type header
                    Text(
                        text = "${typeLabels[qt]} (${group.size}题)",
                        color = TextCream.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )

                    // Grid: 6 columns, small blocks
                    val columns = 6
                    val rows = (group.size + columns - 1) / columns

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until columns) {
                                    val idx = row * columns + col
                                    if (idx < group.size) {
                                        val (originalIndex, _) = group[idx]
                                        val result = viewModel.reviewedResultsMap[originalIndex]
                                        val isCurrent = originalIndex == currentIndexValue

                                        val (bg, fg) = when {
                                            isCurrent -> AmberGold to InkStoneBlack
                                            result == true -> SuccessGreen.copy(alpha = 0.3f) to SuccessGreen
                                            result == false -> ErrorRed.copy(alpha = 0.3f) to ErrorRed
                                            else -> InkStoneBlack to TextCream.copy(alpha = 0.5f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(bg)
                                                .clickable {
                                                    viewModel.jumpTo(originalIndex)
                                                    showQuestionList = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${originalIndex + 1}",
                                                color = fg,
                                                fontSize = 28.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
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

    // Fullscreen image preview
    previewFile?.let { file ->
        ImagePreviewDialog(file = file, onDismiss = { previewFile = null })
    }
}

@Composable
private fun QuestionImagePager(
    paths: List<String>,
    onImageClick: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { paths.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            AdaptiveImage(
                file = File(paths[page]),
                onClick = { onImageClick(paths[page]) }
            )
        }

        if (paths.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(paths.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (pagerState.currentPage == index) AmberGold
                                else TextCream.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}
