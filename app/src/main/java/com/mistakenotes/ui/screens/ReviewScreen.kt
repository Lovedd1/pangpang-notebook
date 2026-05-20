package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.canvas.DrawingTool
import com.mistakenotes.ui.canvas.HandwritingCanvas
import com.mistakenotes.ui.canvas.HandwritingToolbar
import com.mistakenotes.ui.canvas.VectorStroke
import com.mistakenotes.ui.theme.*

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "复习",
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
                actions = {
                    IconButton(onClick = { /* fullscreen toggle */ }) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "全屏",
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberGold)
                }
            }
            uiState.reviewComplete -> {
                ReviewCompleteContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            uiState.currentMistake != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Left side - Question (35%)
                    QuestionPanel(
                        mistake = uiState.currentMistake!!,
                        selectedAnswer = uiState.selectedAnswer,
                        isCorrect = uiState.isCorrect,
                        showAnswer = uiState.showAnswer,
                        onAnswerSelected = { answer ->
                            viewModel.submitAnswer(answer)
                        },
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                    )

                    // Right side - Canvas (65%)
                    CanvasPanel(
                        strokes = uiState.canvasStrokes,
                        onStrokeCompleted = { stroke ->
                            viewModel.onStrokeCompleted(stroke)
                        },
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        showSubmitButton = uiState.currentMistake?.questionType == QuestionType.ESSAY && !uiState.showAnswer,
                        onSubmitEssay = { score ->
                            viewModel.submitEssayAnswer(score)
                        },
                        showReference = uiState.showReference,
                        referenceAnswer = uiState.currentMistake?.referenceAnswer,
                        onNextMistake = { viewModel.nextMistake() },
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    mistake: Mistake,
    selectedAnswer: String?,
    isCorrect: Boolean?,
    showAnswer: Boolean,
    onAnswerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CardDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = mistake.title.ifEmpty { "题目" },
            color = TextCream,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        mistake.questionText?.let { questionText ->
            Text(
                text = questionText,
                color = TextCream.copy(alpha = 0.9f),
                fontSize = 14.sp
            )
        }

        if (showAnswer && isCorrect != null) {
            // Result feedback card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isCorrect) "回答正确" else "回答错误",
                        color = if (isCorrect) SuccessGreen else ErrorRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isCorrect) {
                        Text(
                            text = "正确答案: ${mistake.correctAnswer}",
                            color = TextCream,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Options for choice questions
        if (mistake.questionType == QuestionType.SINGLE_CHOICE ||
            mistake.questionType == QuestionType.MULTI_CHOICE) {
            val options = mistake.options?.split("\n") ?: emptyList()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options) { option ->
                    OptionCard(
                        option = option,
                        isSelected = selectedAnswer == option,
                        isCorrectAnswer = showAnswer && option == mistake.correctAnswer,
                        showResult = showAnswer,
                        onClick = { onAnswerSelected(option) },
                        enabled = !showAnswer
                    )
                }
            }
        }

        // Essay question reference answer
        if (mistake.questionType == QuestionType.ESSAY && showAnswer && mistake.referenceAnswer != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "参考答案",
                        color = AmberGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mistake.referenceAnswer,
                        color = TextCream.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    option: String,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    showResult: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = when {
        showResult && isCorrectAnswer -> SuccessGreen.copy(alpha = 0.2f)
        showResult && isSelected && !isCorrectAnswer -> ErrorRed.copy(alpha = 0.2f)
        isSelected -> AmberGold.copy(alpha = 0.3f)
        else -> CardDark
    }

    val borderColor = when {
        showResult && isCorrectAnswer -> SuccessGreen
        showResult && isSelected && !isCorrectAnswer -> ErrorRed
        isSelected -> AmberGold
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Text(
            text = option,
            color = TextCream,
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun CanvasPanel(
    strokes: List<VectorStroke>,
    onStrokeCompleted: (VectorStroke) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    showSubmitButton: Boolean,
    onSubmitEssay: (Int?) -> Unit,
    showReference: Boolean,
    referenceAnswer: String?,
    onNextMistake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(InkStoneBlack)
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = CardDark,
            contentColor = TextCream
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { onTabSelected(0) },
                text = { Text("答题区") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { onTabSelected(1) },
                text = { Text("草稿纸") }
            )
        }

        // Canvas area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HandwritingCanvas(
                modifier = Modifier.fillMaxSize(),
                backgroundColor = Color.White,
                onStrokeCompleted = onStrokeCompleted
            )
        }

        // Toolbar
        HandwritingToolbar(
            currentTool = DrawingTool.PEN,
            penColor = Color.Blue,
            penThickness = 0.3f,
            canUndo = false,
            canRedo = false,
            onToolChange = {},
            onColorChange = {},
            onThicknessChange = {},
            onUndo = {},
            onRedo = {},
            onClear = {}
        )

        // Submit button for essay
        if (showSubmitButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSubmitEssay(null) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("提交", color = InkStoneBlack)
                }
            }
        }

        // Navigation button
        if (showReference || (strokes.isNotEmpty())) {
            Button(
                onClick = onNextMistake,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
            ) {
                Text("下一题", color = InkStoneBlack)
            }
        }
    }
}

@Composable
private fun ReviewCompleteContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎉",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "今日复习完成",
            color = AmberGold,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "所有待复习题目已完成",
            color = TextCream.copy(alpha = 0.7f),
            fontSize = 16.sp
        )
    }
}