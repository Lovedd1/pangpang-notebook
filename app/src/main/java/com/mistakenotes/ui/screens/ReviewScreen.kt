package com.mistakenotes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*
import java.io.File

private val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showAnswerImage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习", color = TextCream, fontWeight = FontWeight.Bold) },
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
                val mistake = uiState.currentMistake!!
                val showResult = uiState.showAnswer

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
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
                            // Question image
                            if (!mistake.questionImagePath.isNullOrBlank()) {
                                AsyncImage(
                                    model = File(mistake.questionImagePath),
                                    contentDescription = "题目图片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.FillWidth
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

                            val isSelected = index in uiState.selectedOptionIndices
                            val isCorrectAnswer = showResult && index in uiState.correctIndices
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
                                    .clickable(enabled = !showResult) { viewModel.toggleOption(index) },
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
                                onClick = { viewModel.submitAnswer() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = uiState.selectedOptionIndices.isNotEmpty(),
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

                    // Essay answer toggle + self-evaluation
                    if (mistake.questionType == QuestionType.ESSAY) {
                        // Toggle answer image button
                        if (!mistake.referenceAnswer.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { showAnswerImage = !showAnswerImage },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (showAnswerImage) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showAnswerImage) "隐藏答案" else "查看答案",
                                    color = AmberGold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Answer image
                        AnimatedVisibility(visible = showAnswerImage) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("参考答案", color = AmberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AsyncImage(
                                        model = File(mistake.referenceAnswer ?: ""),
                                        contentDescription = "答案图片",
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }

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
                                onClick = { viewModel.submitEssaySelfEval(true) },
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
                                onClick = { viewModel.submitEssaySelfEval(false) },
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

                        OutlinedButton(
                            onClick = { viewModel.skipEssay() },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, TextCream.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("跳过", color = TextCream.copy(alpha = 0.6f))
                        }
                    }

                    // Result banner (after submission)
                    if (showResult) {
                        val isCorrect = uiState.isCorrect

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
                }
            }
        }
    }
}
