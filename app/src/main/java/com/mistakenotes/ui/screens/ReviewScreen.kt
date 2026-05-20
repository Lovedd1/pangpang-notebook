package com.mistakenotes.ui.screens

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.components.CanvasBackground
import com.mistakenotes.ui.components.HandwritingCanvas
import com.mistakenotes.ui.components.PaperColor
import com.mistakenotes.ui.theme.*

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit = {},
    refreshTrigger: Int = 0,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val toolState by viewModel.toolState.collectAsState()
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
                            toolState = toolState,
                            onToggleAnswer = { viewModel.toggleAnswer(it) },
                            onSubmit = { viewModel.submitAnswer() },
                            onMarkCorrect = { viewModel.markAnswer(true) },
                            onMarkWrong = { viewModel.markAnswer(false) },
                            onSkip = { viewModel.skipQuestion() },
                            onBack = {
                                if (uiState.showResult) {
                                    viewModel.setShowResult(false)
                                } else {
                                    currentPhase = "list"
                                }
                            },
                            onSelectTool = { viewModel.selectTool(it) },
                            onPenColorChange = { viewModel.setPenColor(it) },
                            onPenThicknessChange = { viewModel.setPenThickness(it) },
                            onCanvasBgChange = { viewModel.setCanvasBackground(it) },
                            onPaperColorChange = { viewModel.setPaperColor(it) },
                            onScaleChange = { viewModel.updateScale(it) }
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
fun QuestionContent(
    question: Mistake,
    currentIndex: Int,
    totalCount: Int,
    selectedAnswers: Set<String>,
    showResult: Boolean,
    toolState: ToolState,
    onToggleAnswer: (String) -> Unit,
    onSubmit: () -> Unit,
    onMarkCorrect: () -> Unit,
    onMarkWrong: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onSelectTool: (String) -> Unit,
    onPenColorChange: (Int) -> Unit,
    onPenThicknessChange: (Float) -> Unit,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onPaperColorChange: (PaperColor) -> Unit,
    onScaleChange: (Float) -> Unit
) {
    var rightPanelMode by remember { mutableStateOf("answer") }
    var answerHandwritingView by remember { mutableStateOf<HandwritingCanvas?>(null) }
    var draftHandwritingView by remember { mutableStateOf<HandwritingCanvas?>(null) }
    val isChoice = question.questionType == QuestionType.SINGLE_CHOICE || question.questionType == QuestionType.MULTI_CHOICE

    fun activeView(): HandwritingCanvas? = if (rightPanelMode == "draft") draftHandwritingView else answerHandwritingView
    fun clearAllViews() { answerHandwritingView?.clear(); draftHandwritingView?.clear() }
    Column(
        modifier = Modifier.fillMaxSize().background(InkStoneBg)
    ) {
        // Top navigation bar
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

        // Draft toolbar
        DraftToolbar(
            modifier = Modifier.fillMaxWidth(),
            toolState = toolState,
            onUndo = { activeView()?.undo() },
            onRedo = { activeView()?.redo() },
            onClear = { activeView()?.clear() },
            onSelectTool = onSelectTool,
            onPenColorChange = onPenColorChange,
            onPenThicknessChange = onPenThicknessChange,
            onCanvasBgChange = onCanvasBgChange,
            onPaperColorChange = onPaperColorChange
        )

        // Left: Question, Right: Answer area / Draft paper
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT: Question content
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

            // RIGHT: Answer area / Draft paper (toggleable)
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = InkStoneSurface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Toggle button
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PanelModeToggle(
                            mode = rightPanelMode,
                            onToggle = { rightPanelMode = if (rightPanelMode == "answer") "draft" else "answer" }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Right panel title
                    Text(
                        text = if (rightPanelMode == "answer") "答题区" else "草稿纸",
                        color = InkStoneTextDim,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Content area
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (!isChoice) {
                            // Essay: both views always composed, toggle visibility
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    HandwritingCanvas(context).apply {
                                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                                        onScaleChangeListener = { scale -> onScaleChange(scale) }
                                    }.also { answerHandwritingView = it }
                                },
                                update = { view ->
                                    view.penColor = toolState.penColor
                                    view.penThickness = toolState.penThickness
                                    view.canvasBackground = toolState.canvasBackground
                                    view.paperColor = toolState.paperColor
                                }
                            )
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    HandwritingCanvas(context).apply {
                                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                                        onScaleChangeListener = { scale -> onScaleChange(scale) }
                                    }.also { draftHandwritingView = it }
                                },
                                update = { view ->
                                    view.penColor = toolState.penColor
                                    view.penThickness = toolState.penThickness
                                    view.canvasBackground = toolState.canvasBackground
                                    view.paperColor = toolState.paperColor
                                }
                            )
                        } else if (rightPanelMode == "draft") {
                            // Choice: draft mode HandwritingView
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    HandwritingCanvas(context).apply {
                                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                                        onScaleChangeListener = { scale -> onScaleChange(scale) }
                                    }.also { draftHandwritingView = it }
                                },
                                update = { view ->
                                    view.penColor = toolState.penColor
                                    view.penThickness = toolState.penThickness
                                    view.canvasBackground = toolState.canvasBackground
                                    view.paperColor = toolState.paperColor
                                }
                            )
                        } else {
                            // Choice: answer mode ABCD options
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("A", "B", "C", "D").forEach { option ->
                                    val isSelected = selectedAnswers.contains(option)
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { onToggleAnswer(option) },
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
                        }
                    }

                    // Submit / Result buttons (only in answer mode)
                    if (rightPanelMode == "answer") {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!showResult) {
                            if (isChoice) {
                                val isMulti = question.questionType == QuestionType.MULTI_CHOICE
                                Button(
                                    onClick = onSubmit,
                                    modifier = Modifier.fillMaxWidth(),
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = onSubmit,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("提交", color = InkStoneBg)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clearAllViews()
                                            onSkip()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneTextDim),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("跳过")
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clearAllViews()
                                        onMarkCorrect()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkStoneSuccess),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("做对了")
                                }
                                Button(
                                    onClick = {
                                        clearAllViews()
                                        onMarkWrong()
                                    },
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
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PanelModeToggle(mode: String, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onToggle),
        color = InkStoneAccent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (mode == "answer") Icons.Default.Edit else Icons.Default.Check,
                contentDescription = null,
                tint = InkStoneAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (mode == "answer") "草稿纸" else "答题区",
                color = InkStoneAccent,
                fontSize = 12.sp
            )
        }
    }
}

// ========== Draft Toolbar Composable ==========

@Composable
fun DraftToolbar(
    modifier: Modifier = Modifier,
    toolState: ToolState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSelectTool: (String) -> Unit,
    onPenColorChange: (Int) -> Unit,
    onPenThicknessChange: (Float) -> Unit,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onPaperColorChange: (PaperColor) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(InkStoneBg)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left 80%: Main tools
        Row(
            modifier = Modifier.weight(0.8f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, "撤销", tint = InkStoneTextDim, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRedo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, "重做", tint = InkStoneTextDim, modifier = Modifier.size(18.dp))
            }
            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = InkStoneBorder)
            // Tool buttons: pen, paper, clear
            ToolButton(tool = "pen", isSelected = toolState.selectedTool == "pen", onClick = { onSelectTool("pen") })
            ToolButton(tool = "paper", isSelected = toolState.selectedTool == "paper", onClick = { onSelectTool("paper") })
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "清空", tint = InkStoneError, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            // Scale indicator
            Text(text = "${(toolState.scale * 100).toInt()}%", color = InkStoneTextDim, fontSize = 12.sp)
        }

        // Right 20%: Options panel
        Surface(
            modifier = Modifier.weight(0.2f).fillMaxHeight(),
            color = InkStoneSurface,
            shape = RoundedCornerShape(6.dp)
        ) {
            when (toolState.selectedTool) {
                "pen" -> PenOptionsPanel(toolState, onPenColorChange, onPenThicknessChange)
                "paper" -> PaperOptionsPanel(toolState, onCanvasBgChange, onPaperColorChange)
            }
        }
    }
}

@Composable
fun ToolButton(tool: String, isSelected: Boolean, onClick: () -> Unit) {
    val icon = when (tool) {
        "pen" -> Icons.Default.Edit
        "paper" -> Icons.Default.GridOn
        else -> Icons.Default.Edit
    }
    Surface(
        modifier = Modifier.size(32.dp).clickable(onClick = onClick),
        color = if (isSelected) InkStoneAccent else InkStoneSurface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tool,
                tint = if (isSelected) InkStoneBg else InkStoneTextDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PenOptionsPanel(
    toolState: ToolState,
    onPenColorChange: (Int) -> Unit,
    onPenThicknessChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color buttons
        ColorButton(color = 0xFF1E88E5.toInt(), onClick = { onPenColorChange(0xFF1E88E5.toInt()) })
        ColorButton(color = 0xFF000000.toInt(), onClick = { onPenColorChange(0xFF000000.toInt()) })
        ColorButton(color = 0xFFE53935.toInt(), onClick = { onPenColorChange(0xFFE53935.toInt()) })
        Spacer(modifier = Modifier.width(4.dp))
        // Thickness buttons
        ThicknessButton(thickness = 0.1f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.1f) })
        ThicknessButton(thickness = 0.3f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.3f) })
        ThicknessButton(thickness = 0.5f, current = toolState.penThickness, onClick = { onPenThicknessChange(0.5f) })
    }
}

@Composable
fun ColorButton(color: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(16.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(color))
            .clickable(onClick = onClick)
    )
}

@Composable
fun ThicknessButton(thickness: Float, current: Float, onClick: () -> Unit) {
    val label = when (thickness) {
        0.1f -> "细"
        0.3f -> "中"
        0.5f -> "粗"
        else -> "?"
    }
    Surface(
        modifier = Modifier.size(18.dp).clickable(onClick = onClick),
        color = if (thickness == current) InkStoneAccent else InkStoneSurface,
        shape = RoundedCornerShape(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, color = if (thickness == current) InkStoneBg else InkStoneTextDim, fontSize = 7.sp)
        }
    }
}

@Composable
fun PaperOptionsPanel(
    toolState: ToolState,
    onCanvasBgChange: (CanvasBackground) -> Unit,
    onPaperColorChange: (PaperColor) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Background buttons
        CanvasBgButton(bg = CanvasBackground.BLANK, isSelected = toolState.canvasBackground == CanvasBackground.BLANK, onClick = { onCanvasBgChange(CanvasBackground.BLANK) })
        CanvasBgButton(bg = CanvasBackground.GRID, isSelected = toolState.canvasBackground == CanvasBackground.GRID, onClick = { onCanvasBgChange(CanvasBackground.GRID) })
        CanvasBgButton(bg = CanvasBackground.LINES, isSelected = toolState.canvasBackground == CanvasBackground.LINES, onClick = { onCanvasBgChange(CanvasBackground.LINES) })
        Spacer(modifier = Modifier.width(4.dp))
        // Paper color buttons
        PaperColorButton(color = PaperColor.BLACK, isSelected = toolState.paperColor == PaperColor.BLACK, onClick = { onPaperColorChange(PaperColor.BLACK) })
        PaperColorButton(color = PaperColor.WHITE, isSelected = toolState.paperColor == PaperColor.WHITE, onClick = { onPaperColorChange(PaperColor.WHITE) })
        PaperColorButton(color = PaperColor.SKIN, isSelected = toolState.paperColor == PaperColor.SKIN, onClick = { onPaperColorChange(PaperColor.SKIN) })
    }
}

@Composable
fun CanvasBgButton(bg: CanvasBackground, isSelected: Boolean, onClick: () -> Unit) {
    val label = when (bg) {
        CanvasBackground.BLANK -> "空"
        CanvasBackground.GRID -> "格"
        CanvasBackground.LINES -> "线"
    }
    Surface(
        modifier = Modifier.size(18.dp).clickable(onClick = onClick),
        color = if (isSelected) InkStoneAccent else InkStoneSurface,
        shape = RoundedCornerShape(3.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, color = if (isSelected) InkStoneBg else InkStoneTextDim, fontSize = 7.sp)
        }
    }
}

@Composable
fun PaperColorButton(color: PaperColor, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(12.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(color.colorInt))
            .border(width = if (isSelected) 1.dp else 0.dp, color = if (isSelected) InkStoneAccent else androidx.compose.ui.graphics.Color.Transparent, shape = CircleShape)
            .clickable(onClick = onClick)
    )
}
