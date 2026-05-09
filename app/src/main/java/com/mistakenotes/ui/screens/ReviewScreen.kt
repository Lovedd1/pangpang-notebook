package com.mistakenotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.*
import com.mistakenotes.ui.components.HandwritingView

data class ReviewItem(
    val id: Long,
    val title: String,
    val subject: String,
    val tags: List<String>,
    val round: Int,
    val status: String,
    val questionType: String
)

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit = {}
) {
    var currentPhase by remember { mutableStateOf("list") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var handwritingView by remember { mutableStateOf<HandwritingView?>(null) }

    val reviewList = remember {
        listOf(
            ReviewItem(1, "二次函数最值问题", "数学", listOf("二次函数"), 1, "overdue", "大题"),
            ReviewItem(2, "导数综合应用", "数学", listOf("导数"), 1, "due", "选择题"),
            ReviewItem(3, "三角函数化简", "数学", listOf("三角函数"), 2, "due", "大题"),
            ReviewItem(4, "概率分布计算", "数学", listOf("概率统计"), 1, "due", "选择题"),
            ReviewItem(5, "数列专项练习", "数学", listOf("数列"), 3, "done", "大题")
        )
    }

    when (currentPhase) {
        "list" -> {
            ReviewListContent(
                reviewList = reviewList,
                onStartReview = {
                    currentIndex = 0
                    selectedAnswer = null
                    showResult = false
                    handwritingView?.clear()
                    currentPhase = "question"
                },
                onNavigateBack = onNavigateBack
            )
        }
        "question" -> {
            val current = reviewList.getOrNull(currentIndex)
            if (current != null) {
                QuestionContent(
                    question = current,
                    currentIndex = currentIndex,
                    totalCount = reviewList.size,
                    selectedAnswer = selectedAnswer,
                    showResult = showResult,
                    onSelectAnswer = { answer -> selectedAnswer = answer },
                    onSubmit = { showResult = true },
                    onMarkCorrect = {
                        currentIndex++
                        selectedAnswer = null
                        showResult = false
                        handwritingView?.clear()
                        if (currentIndex >= reviewList.size) {
                            currentPhase = "list"
                            currentIndex = 0
                        }
                    },
                    onMarkWrong = {
                        currentIndex++
                        selectedAnswer = null
                        showResult = false
                        handwritingView?.clear()
                        if (currentIndex >= reviewList.size) {
                            currentPhase = "list"
                            currentIndex = 0
                        }
                    },
                    onSkip = {
                        currentIndex++
                        selectedAnswer = null
                        showResult = false
                        handwritingView?.clear()
                        if (currentIndex >= reviewList.size) {
                            currentPhase = "list"
                            currentIndex = 0
                        }
                    },
                    onBack = {
                        if (showResult) {
                            showResult = false
                        } else {
                            currentPhase = "list"
                        }
                    },
                    onViewRefReady = { view -> handwritingView = view }
                )
            }
        }
    }
}

@Composable
fun ReviewListContent(
    reviewList: List<ReviewItem>,
    onStartReview: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = InkStoneText)
            }
            Text(
                text = "今日复习",
                color = InkStoneText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = reviewList.count { it.status == "due" || it.status == "overdue" }.toString(),
                label = "待复习",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = reviewList.count { it.status == "overdue" }.toString(),
                label = "逾期",
                modifier = Modifier.weight(1f),
                isWarning = true
            )
            StatCard(
                value = reviewList.count { it.status == "done" }.toString(),
                label = "已完成",
                modifier = Modifier.weight(1f),
                isSuccess = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartReview,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("开始复习", fontSize = 16.sp, color = InkStoneBg)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "复习列表", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(reviewList) { _, item ->
                ReviewListItem(item = item)
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
fun ReviewListItem(item: ReviewItem) {
    val statusColor = when (item.status) {
        "overdue" -> InkStoneError
        "done" -> InkStoneSuccess
        else -> InkStoneAccent
    }
    val statusText = when (item.status) {
        "overdue" -> "逾期"
        "done" -> "完成"
        else -> "今日"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = InkStoneSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(InkStoneAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Text(text = "${item.round * 7}", color = InkStoneBg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(text = "天", color = InkStoneBg, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, color = InkStoneText, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = item.subject, color = InkStoneTextDim, fontSize = 12.sp)
                    item.tags.forEach { tag ->
                        Surface(color = InkStoneAccentSoft, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = tag,
                                color = InkStoneAccent,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun QuestionContent(
    question: ReviewItem,
    currentIndex: Int,
    totalCount: Int,
    selectedAnswer: String?,
    showResult: Boolean,
    onSelectAnswer: (String) -> Unit,
    onSubmit: () -> Unit,
    onMarkCorrect: () -> Unit,
    onMarkWrong: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onViewRefReady: (HandwritingView) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(InkStoneBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = question.title, color = InkStoneText, fontSize = 16.sp)
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
                    Text(text = "你的作答", color = InkStoneTextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (question.questionType == "选择题") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("A", "B", "C", "D").forEach { option ->
                                val isSelected = selectedAnswer == option
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { onSelectAnswer(option) },
                                    color = if (isSelected) InkStoneAccent else InkStoneBg,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option,
                                            color = if (isSelected) InkStoneBg else InkStoneText,
                                            fontSize = 16.sp
                                        )
                                        if (showResult) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Icon(
                                                imageVector = if (option == "B") Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (option == "B") InkStoneSuccess else InkStoneError,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(InkStoneBg, RoundedCornerShape(12.dp))
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    HandwritingView(context).apply {
                                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                                        strokeColor = android.graphics.Color.parseColor("#E8E4DC")
                                        strokeWidth = 4f
                                    }.also { view -> onViewRefReady(view) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showResult) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (question.questionType == "选择题") {
                                Button(
                                    onClick = onSubmit,
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedAnswer != null,
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