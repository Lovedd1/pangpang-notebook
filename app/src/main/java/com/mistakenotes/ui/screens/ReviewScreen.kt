package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.components.CanvasBackground
import com.mistakenotes.ui.components.HandwritingView
import com.mistakenotes.ui.components.PaperColor
import com.mistakenotes.ui.theme.*

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit = {},
    refreshTrigger: Int = 0,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var handwritingView by remember { mutableStateOf<HandwritingView?>(null) }
    var draftHandwritingView by remember { mutableStateOf<HandwritingView?>(null) }

    // 当 refreshTrigger 变化时，重新加载数据
    LaunchedEffect(refreshTrigger) {
        viewModel.loadMistakes()
    }

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize().background(InkStoneBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = InkStoneAccent)
            }
        }
        uiState.mistakes.isEmpty() -> {
            EmptyReviewContent(onNavigateBack = onNavigateBack)
        }
        else -> {
            var currentPhase by remember { mutableStateOf("list") }
            val currentMistake = uiState.mistakes.getOrNull(uiState.currentIndex)

            when (currentPhase) {
                "list" -> {
                    ReviewListContent(
                        reviewList = uiState.mistakes,
                        onStartReview = {
                            viewModel.setCurrentIndex(0)
                            currentPhase = "question"
                        },
                        onNavigateBack = onNavigateBack,
                        onItemClick = { index ->
                            viewModel.setCurrentIndex(index)
                            currentPhase = "question"
                        },
                        onSkipTodayInList = { mistakeId ->
                            viewModel.skipTodayReview(mistakeId)
                        }
                    )
                }
                "question" -> {
                    if (currentMistake != null) {
                        QuestionContent(
                            question = currentMistake,
                            currentIndex = uiState.currentIndex,
                            totalCount = uiState.mistakes.size,
                            selectedAnswers = uiState.selectedAnswers,
                            showResult = uiState.showResult,
                            isDraftMode = uiState.isDraftMode,
                            onToggleDraftMode = { viewModel.toggleDraftMode() },
                            onToggleAnswer = { viewModel.toggleAnswer(it) },
                            onSubmit = { viewModel.submitAnswer() },
                            onMarkCorrect = {
                                draftHandwritingView?.clear()
                                viewModel.markAnswer(true)
                            },
                            onMarkWrong = {
                                draftHandwritingView?.clear()
                                viewModel.markAnswer(false)
                            },
                            onSkip = { viewModel.skipQuestion() },
                            onBack = {
                                if (uiState.showResult) {
                                    viewModel.setShowResult(false)
                                } else {
                                    currentPhase = "list"
                                }
                            },
                            onViewRefReady = { handwritingView = it },
                            onDraftViewRefReady = { draftHandwritingView = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyReviewContent(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(InkStoneBg).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "暂无待复习题目", color = InkStoneText, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "先去录入一些错题吧", color = InkStoneTextDim, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent)
        ) {
            Text("返回", color = InkStoneBg)
        }
    }
}

@Composable
fun ReviewListContent(
    reviewList: List<Mistake>,
    onStartReview: () -> Unit,
    onNavigateBack: () -> Unit,
    onItemClick: (Int) -> Unit,
    onSkipTodayInList: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(InkStoneBg).padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = InkStoneText)
            }
            Text(text = "今日复习", color = InkStoneText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(value = reviewList.size.toString(), label = "待复习", modifier = Modifier.weight(1f))
            StatCard(value = "0", label = "逾期", modifier = Modifier.weight(1f), isWarning = true)
            StatCard(value = "0", label = "已完成", modifier = Modifier.weight(1f), isSuccess = true)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartReview,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("开始复习", fontSize = 16.sp, color = InkStoneBg)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "复习列表", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(reviewList) { index, item ->
                ReviewListItem(
                    item = item,
                    round = 1,
                    status = "due",
                    onClick = { onItemClick(index) },
                    onSkipToday = { onSkipTodayInList(item.id) }
                )
            }
        }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
    isSuccess: Boolean = false
) {
    val bgColor = when {
        isWarning -> InkStoneError.copy(alpha = 0.15f)
        isSuccess -> InkStoneSuccess.copy(alpha = 0.15f)
        else -> InkStoneSurface
    }
    val valueColor = when {
        isWarning -> InkStoneError
        isSuccess -> InkStoneSuccess
        else -> InkStoneAccent
    }

    Surface(modifier = modifier, color = bgColor, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, color = valueColor, fontSize = 28.sp, fontWeight = FontWeight.Light)
            Text(text = label, color = InkStoneTextDim, fontSize = 12.sp)
        }
    }
}

@Composable
fun ReviewListItem(item: Mistake, round: Int, status: String, onClick: () -> Unit, onSkipToday: () -> Unit) {
    val statusColor = when (status) {
        "overdue" -> InkStoneError
        "done" -> InkStoneSuccess
        else -> InkStoneAccent
    }
    val statusText = when (status) {
        "overdue" -> "逾期"
        "done" -> "完成"
        else -> "今日"
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = InkStoneSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(InkStoneAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Text(text = "${round * 7}", color = InkStoneBg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(text = "天", color = InkStoneBg, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title.ifBlank { item.questionImagePath.substringAfterLast("/") }, color = InkStoneText, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = item.subject, color = InkStoneTextDim, fontSize = 12.sp)
                    item.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
                        Surface(color = InkStoneAccentSoft, shape = RoundedCornerShape(4.dp)) {
                            Text(text = tag, color = InkStoneAccent, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(text = statusText, color = statusColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onSkipToday, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "跳过今日",
                    tint = InkStoneTextDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DraftModeContent(
    onDraftViewRefReady: (HandwritingView) -> Unit,
    paperColor: PaperColor,
    canvasBackground: CanvasBackground,
    isEraserMode: Boolean,
    isPenMode: Boolean,
    eraserRadius: Float,
    onPaperColorChange: (PaperColor) -> Unit,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onEraserModeToggle: () -> Unit,
    onEraserRadiusChange: (Float) -> Unit,
    onPenModeToggle: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var currentScale by remember { mutableFloatStateOf(1f) }
    val handwritingView = remember {
        HandwritingView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#242424"))
            fingerColor = android.graphics.Color.parseColor("#E8E4DC")
            fingerStrokeWidth = 4f
            onScaleChangeListener = { scale -> currentScale = scale }
        }.also { onDraftViewRefReady(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 倍率显示
            Surface(
                color = InkStoneBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${(currentScale * 100).toInt()}%",
                    color = InkStoneText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 底色选择
            PaperColor.entries.forEach { color ->
                val isSelected = paperColor == color
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color.colorInt))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) InkStoneAccent else InkStoneBorder,
                            shape = CircleShape
                        )
                        .clickable { onPaperColorChange(color) }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 线型选择
            CanvasBackground.entries.forEach { bg ->
                val isSelected = canvasBackground == bg
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCanvasBgChange(bg) },
                    color = if (isSelected) InkStoneAccent else InkStoneBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (bg) {
                            CanvasBackground.BLANK -> "空白"
                            CanvasBackground.GRID -> "网格"
                            CanvasBackground.LINES -> "横线"
                        },
                        color = if (isSelected) InkStoneBg else InkStoneTextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 橡皮擦按钮
            IconButton(
                onClick = onEraserModeToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "橡皮擦",
                    tint = if (isEraserMode) InkStoneAccent else InkStoneTextDim,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 笔写/手写模式切换
            Surface(
                modifier = Modifier.clickable { onPenModeToggle() },
                color = if (isPenMode) InkStoneAccent else InkStoneSurface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (isPenMode) "笔写" else "手写",
                    color = if (isPenMode) InkStoneBg else InkStoneText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // 橡皮擦大小
            if (isEraserMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(20.dp).clickable { onEraserRadiusChange(15f) },
                        color = if (eraserRadius == 15f) InkStoneAccent else InkStoneBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "S",
                                color = if (eraserRadius == 15f) InkStoneBg else InkStoneTextDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(20.dp).clickable { onEraserRadiusChange(30f) },
                        color = if (eraserRadius == 30f) InkStoneAccent else InkStoneBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "M",
                                color = if (eraserRadius == 30f) InkStoneBg else InkStoneTextDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(20.dp).clickable { onEraserRadiusChange(50f) },
                        color = if (eraserRadius == 50f) InkStoneAccent else InkStoneBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "L",
                                color = if (eraserRadius == 50f) InkStoneBg else InkStoneTextDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // 清空按钮
            IconButton(
                onClick = {
                    handwritingView.clear()
                    onClear()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "清空",
                    tint = InkStoneTextDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // HandwritingView
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(InkStoneBg, RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { handwritingView },
                update = { view ->
                    view.paperColor = paperColor
                    view.canvasBackground = canvasBackground
                    view.isEraserMode = isEraserMode
                    view.isPenMode = isPenMode
                    view.eraserRadius = eraserRadius
                }
            )
        }
    }
}

@Composable
fun QuestionContent(
    question: Mistake,
    currentIndex: Int,
    totalCount: Int,
    selectedAnswers: Set<String>,
    showResult: Boolean,
    isDraftMode: Boolean,
    onToggleDraftMode: () -> Unit,
    onToggleAnswer: (String) -> Unit,
    onSubmit: () -> Unit,
    onMarkCorrect: () -> Unit,
    onMarkWrong: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onViewRefReady: (HandwritingView) -> Unit,
    onDraftViewRefReady: (HandwritingView) -> Unit
) {
    // 草稿纸状态
    var draftPaperColor by remember { mutableStateOf(PaperColor.BLACK) }
    var draftCanvasBg by remember { mutableStateOf(CanvasBackground.BLANK) }
    var draftEraserMode by remember { mutableStateOf(false) }
    var draftEraserRadius by remember { mutableFloatStateOf(30f) }
    var draftIsPenMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().background(InkStoneBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = InkStoneText)
            }
            Text(text = "第 ${currentIndex + 1} 题", color = InkStoneText, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            LinearProgressIndicator(
                progress = (currentIndex + 1).toFloat() / totalCount,
                modifier = Modifier.width(120.dp).height(4.dp),
                color = InkStoneAccent,
                trackColor = InkStoneBorder,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = InkStoneSurface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "题目", color = InkStoneTextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).background(InkStoneBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = question.recognizedQuestion.ifBlank { "（无识别题目）" }, color = InkStoneText, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "（题目图片）", color = InkStoneTextDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = InkStoneSurface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = if (isDraftMode) "草稿纸" else "你的作答", color = InkStoneTextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isChoice = question.questionType == QuestionType.SINGLE_CHOICE || question.questionType == QuestionType.MULTI_CHOICE

                    if (isDraftMode) {
                        // 草稿纸模式 - 显示 HandwritingView + 工具栏
                        DraftModeContent(
                            onDraftViewRefReady = onDraftViewRefReady,
                            paperColor = draftPaperColor,
                            canvasBackground = draftCanvasBg,
                            isEraserMode = draftEraserMode,
                            isPenMode = draftIsPenMode,
                            eraserRadius = draftEraserRadius,
                            onPaperColorChange = { draftPaperColor = it },
                            onCanvasBgChange = { draftCanvasBg = it },
                            onEraserModeToggle = { draftEraserMode = !draftEraserMode },
                            onEraserRadiusChange = { draftEraserRadius = it },
                            onPenModeToggle = { draftIsPenMode = !draftIsPenMode },
                            onClear = { }
                        )
                    } else {
                        // 答题模式 - 原有的 A/B/C/D 选项或 HandwritingView
                        if (isChoice) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("A", "B", "C", "D").forEach { option ->
                                    val isSelected = selectedAnswers.contains(option)
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            onToggleAnswer(option)
                                        },
                                        color = if (isSelected) InkStoneAccent else InkStoneBg,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = option, color = if (isSelected) InkStoneBg else InkStoneText, fontSize = 16.sp)
                                            if (showResult) {
                                                Spacer(modifier = Modifier.width(12.dp))
                                                val correctSet = question.correctAnswer.split(",").toSet()
                                                val isCorrect = correctSet.contains(option)
                                                Icon(
                                                    imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = if (isCorrect) InkStoneSuccess else InkStoneError,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f).background(InkStoneBg, RoundedCornerShape(12.dp))
                            ) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        HandwritingView(context).apply {
                                            setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                                            fingerColor = android.graphics.Color.parseColor("#E8E4DC")
                                            fingerStrokeWidth = 4f
                                        }.also { onViewRefReady(it) }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showResult) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onToggleDraftMode,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isDraftMode) "返回答题" else "草稿纸")
                            }
                            if (isChoice) {
                                val isMulti = question.questionType == QuestionType.MULTI_CHOICE
                                Button(
                                    onClick = onSubmit,
                                    modifier = Modifier.weight(1f),
                                    enabled = if (isMulti) selectedAnswers.size >= 2 else selectedAnswers.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InkStoneAccent,
                                        disabledContainerColor = InkStoneBorder
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("提交答案", color = InkStoneBg)
                                }
                            } else {
                                Button(
                                    onClick = onSubmit,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("提交", color = InkStoneBg)
                                }
                            }
                            OutlinedButton(
                                onClick = onSkip,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneTextDim),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("跳过")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onMarkCorrect,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = InkStoneSuccess),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("做对了")
                            }
                            Button(
                                onClick = onMarkWrong,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = InkStoneError),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("做错了")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}