# CPA 错题笔记 — 移除手写 & 选项按钮式录入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除手写画布全部代码，录入改为按钮式选项交互，复习改为纯文本单列布局

**Architecture:** Clean Architecture — UI 层（Compose）→ Domain 层（Model + Repository）→ Data 层（Room）。Hilt 依赖注入，单 Activity 多 Screen 导航。无手写模块。

**Tech Stack:** Kotlin + Jetpack Compose + Room + Hilt + Navigation Compose + Coil

---

### Task 1: 删除手写画布全部文件

**Files:**
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/StrokePoint.kt`
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/VectorStroke.kt`
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/VectorLayer.kt`
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/UndoRedoManager.kt`
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/StrokeRenderer.kt`
- Delete: `app/src/main/java/com/mistakenotes/ui/canvas/HandwritingCanvas.kt`

- [ ] **Step 1: 删除 canvas 目录全部文件**

```bash
rm -rf app/src/main/java/com/mistakenotes/ui/canvas/
```

- [ ] **Step 2: 提交**

```bash
git add -A
git commit -m "refactor: remove handwriting canvas module"
```

---

### Task 2: 重写 ImportViewModel — 选项按钮式管理

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

- [ ] **Step 1: 更新 ImportUiState — 添加 optionEntries 和 correctOptionIndices**

替换现有 `ImportUiState` 为：

```kotlin
data class ImportUiState(
    val imageUri: Uri? = null,
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val optionEntries: List<String> = listOf("", "", "", ""), // 4 empty options by default
    val correctOptionIndices: Set<Int> = emptySet(),         // indices of correct options
    val referenceAnswer: String = "",
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)
```

- [ ] **Step 2: 删除旧的 correctAnswer 字段，添加选项管理方法**

替换 `ImportViewModel` 中的 `setCorrectAnswer` 为以下新方法：

```kotlin
fun setOptionText(index: Int, text: String) {
    val newEntries = _uiState.value.optionEntries.toMutableList()
    if (index < newEntries.size) {
        newEntries[index] = text
        _uiState.update { it.copy(optionEntries = newEntries) }
    }
}

fun addOption() {
    _uiState.update {
        it.copy(optionEntries = it.optionEntries + "")
    }
}

fun removeOption(index: Int) {
    _uiState.update {
        val newEntries = it.optionEntries.toMutableList()
        if (newEntries.size > 2 && index < newEntries.size) {
            newEntries.removeAt(index)
            // Adjust correct indices
            val newCorrect = it.correctOptionIndices
                .filter { i -> i != index }
                .map { i -> if (i > index) i - 1 else i }
                .toSet()
            it.copy(optionEntries = newEntries, correctOptionIndices = newCorrect)
        } else it
    }
}

fun toggleCorrectOption(index: Int) {
    _uiState.update {
        val isSingle = it.questionType == QuestionType.SINGLE_CHOICE
        val newCorrect = if (isSingle) {
            setOf(index) // single choice: replace
        } else {
            val current = it.correctOptionIndices
            if (index in current) current - index else current + index
        }
        it.copy(correctOptionIndices = newCorrect)
    }
}

// 题型切换时重置正确选项
fun setQuestionType(type: QuestionType) {
    _uiState.update {
        it.copy(
            questionType = type,
            correctOptionIndices = emptySet() // reset correct selection
        )
    }
}
```

- [ ] **Step 3: 更新 saveMistake — 选项以 `|` 分隔存储，正确答案存为字母**

替换 `saveMistake` 方法中构建 mistake 的部分（约第140-151行）：

```kotlin
val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")

val optionsStr = if (state.questionType != QuestionType.ESSAY) {
    state.optionEntries.joinToString("|")
} else null

val correctAnswerStr = if (state.questionType != QuestionType.ESSAY) {
    state.correctOptionIndices.sorted().joinToString("") { labelLetters[it] }
} else null

val mistake = Mistake(
    title = state.questionText.take(50).ifBlank { "错题" },
    subjectId = state.subjectId,
    chapterId = state.chapterId,
    knowledgePointId = state.knowledgePointId,
    questionType = state.questionType,
    questionImagePath = state.imageUri?.toString(),
    questionText = state.questionText.takeIf { it.isNotBlank() },
    options = optionsStr,
    correctAnswer = correctAnswerStr,
    referenceAnswer = null // 本期不录入参考答案
)
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "refactor: rewrite ImportViewModel with button-style option management"
```

---

### Task 3: 重写 ImportScreen — 按钮式选项录入 UI

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`

- [ ] **Step 1: 完全替换文件内容**

```kotlin
package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.Subject
import com.mistakenotes.ui.theme.*

private val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setImageUri(uri)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录入错题", color = AmberGold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = AmberGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = InkStoneBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (uiState.imageUri != null) {
                        AsyncImage(
                            model = uiState.imageUri,
                            contentDescription = "题目图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image, contentDescription = null,
                                modifier = Modifier.size(48.dp), tint = AmberGold.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击选择图片", color = TextCream.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // Question type
            Text("题目类型", color = TextCream, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuestionType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.questionType == type,
                        onClick = { viewModel.setQuestionType(type) },
                        label = {
                            Text(
                                when (type) {
                                    QuestionType.SINGLE_CHOICE -> "单选题"
                                    QuestionType.MULTI_CHOICE -> "多选题"
                                    QuestionType.ESSAY -> "主观题"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = InkStoneBlack,
                            containerColor = CardDark,
                            labelColor = TextCream
                        )
                    )
                }
            }

            // Classification
            SubjectDropdown(
                subjects = uiState.subjects,
                selectedSubjectId = uiState.subjectId,
                onSubjectSelected = { viewModel.setSubject(it) }
            )
            Dropdown(
                label = "章节",
                items = uiState.chapters,
                selectedId = uiState.chapterId,
                enabled = uiState.subjectId != null,
                onSelected = { viewModel.setChapter(it) },
                emptyText = "请先选择科目"
            )
            Dropdown(
                label = "知识点",
                items = uiState.knowledgePoints,
                selectedId = uiState.knowledgePointId,
                enabled = uiState.chapterId != null,
                onSelected = { viewModel.setKnowledgePoint(it) },
                emptyText = "请先选择章节"
            )

            // Question text
            OutlinedTextField(
                value = uiState.questionText,
                onValueChange = { viewModel.setQuestionText(it) },
                label = { Text("题目描述") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )

            // Options (choice questions only)
            if (uiState.questionType != QuestionType.ESSAY) {
                Text("选项", color = TextCream, style = MaterialTheme.typography.titleSmall)

                uiState.optionEntries.forEachIndexed { index, optionText ->
                    OptionEntryRow(
                        index = index,
                        text = optionText,
                        isCorrect = index in uiState.correctOptionIndices,
                        showCorrectToggle = true,
                        onTextChange = { viewModel.setOptionText(index, it) },
                        onToggleCorrect = { viewModel.toggleCorrectOption(index) },
                        onDelete = { viewModel.removeOption(index) },
                        canDelete = uiState.optionEntries.size > 2
                    )
                }

                // Add option button
                TextButton(
                    onClick = { viewModel.addOption() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加选项", color = AmberGold)
                }
            }

            // ReferenceAnswer no longer entered

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.saveMistake() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberGold,
                    contentColor = InkStoneBlack
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = InkStoneBlack,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionEntryRow(
    index: Int,
    text: String,
    isCorrect: Boolean,
    showCorrectToggle: Boolean,
    onTextChange: (String) -> Unit,
    onToggleCorrect: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Letter label
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCorrect) SuccessGreen else CardDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = LABELS.getOrElse(index) { "${index}" },
                color = if (isCorrect) InkStoneBlack else TextCream,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("输入选项内容（可为空）", color = TextCream.copy(alpha = 0.3f)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = textFieldColors()
        )

        // Mark correct button
        IconButton(
            onClick = onToggleCorrect,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isCorrect) "已标记为正确答案" else "点击标记正确答案",
                tint = if (isCorrect) SuccessGreen else TextCream.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除选项",
                tint = if (canDelete) TextCream.copy(alpha = 0.5f) else TextCream.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================
// Dropdown components
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(
    subjects: List<Subject>,
    selectedSubjectId: Long?,
    onSubjectSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selected = subjects.find { it.id == selectedSubjectId }

    OutlinedTextField(
        value = selected?.name ?: "请选择科目",
        onValueChange = {},
        readOnly = true,
        label = { Text("科目") },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextCream)
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        colors = textFieldColors()
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择科目", color = AmberGold) },
            text = {
                Column {
                    subjects.forEach { subject ->
                        TextButton(
                            onClick = { onSubjectSelected(subject.id); showDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                subject.name,
                                color = if (subject.id == selectedSubjectId) AmberGold else TextCream,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消", color = TextCream.copy(alpha = 0.6f)) } },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(
    label: String,
    items: List<Any>,
    selectedId: Long?,
    enabled: Boolean,
    onSelected: (Long) -> Unit,
    emptyText: String
) {
    var showDialog by remember { mutableStateOf(false) }

    @Suppress("UNCHECKED_CAST")
    val selectedName = when {
        items is List<*> && items.firstOrNull() is Chapter ->
            (items as List<Chapter>).find { it.id == selectedId }?.name
        items is List<*> && items.firstOrNull() is KnowledgePoint ->
            (items as List<KnowledgePoint>).find { it.id == selectedId }?.name
        else -> null
    }

    OutlinedTextField(
        value = selectedName ?: if (enabled) "请选择$label" else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = {
            if (enabled) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, null,
                        tint = TextCream.copy(alpha = if (enabled) 1f else 0.4f)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { showDialog = true },
        colors = textFieldColors(enabled)
    )

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择$label", color = AmberGold) },
            text = {
                Column {
                    if (items.isEmpty()) {
                        Text(emptyText, color = TextCream.copy(alpha = 0.6f))
                    } else {
                        items.forEach { item ->
                            val id: Long
                            val name: String
                            when (item) {
                                is Chapter -> { id = item.id; name = item.name }
                                is KnowledgePoint -> { id = item.id; name = item.name }
                                else -> return@forEach
                            }
                            TextButton(
                                onClick = { onSelected(id); showDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    name,
                                    color = if (id == selectedId) AmberGold else TextCream,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消", color = TextCream.copy(alpha = 0.6f)) } },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }
}

@Composable
private fun textFieldColors(enabled: Boolean = true): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextCream,
        unfocusedTextColor = if (enabled) TextCream else TextCream.copy(alpha = 0.4f),
        focusedBorderColor = AmberGold,
        unfocusedBorderColor = CardDark,
        focusedLabelColor = AmberGold,
        unfocusedLabelColor = TextCream.copy(alpha = 0.6f),
        cursorColor = AmberGold,
        focusedContainerColor = CardDark,
        unfocusedContainerColor = CardDark
    )
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "refactor: rewrite ImportScreen with button-style option entry"
```

---

### Task 4: 重写 ReviewViewModel — 移除手写依赖，支持多选

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`

- [ ] **Step 1: 完全替换文件内容**

```kotlin
package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val currentMistake: Mistake? = null,
    val selectedOptionIndices: Set<Int> = emptySet(),
    val showAnswer: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctIndices: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val reviewComplete: Boolean = false
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var reviewQueue = mutableListOf<Mistake>()
    private var currentIndex = 0

    init {
        loadReviewQueue()
    }

    private fun loadReviewQueue() {
        viewModelScope.launch {
            combine(
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { mistakes, reviewRecords ->
                val now = System.currentTimeMillis()
                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }

                mistakes.filter { mistake ->
                    val records = reviewRecordMap[mistake.id] ?: emptyList()
                    val latestRecord = records.maxByOrNull { it.reviewDate }
                    val nextReview = latestRecord?.nextReviewDate
                    nextReview != null && nextReview != -1L && nextReview <= now
                }
            }.collect { queue ->
                reviewQueue = queue.toMutableList()
                reviewQueue.shuffle()
                currentIndex = 0
                if (queue.isNotEmpty()) {
                    _uiState.update {
                        it.copy(currentMistake = queue.first(), isLoading = false, reviewComplete = false)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, reviewComplete = true) }
                }
            }
        }
    }

    fun toggleOption(index: Int) {
        _uiState.update { state ->
            val mistake = state.currentMistake ?: return@update state
            val newSelected = if (mistake.questionType == QuestionType.SINGLE_CHOICE) {
                setOf(index)
            } else {
                val current = state.selectedOptionIndices
                if (index in current) current - index else current + index
            }
            state.copy(selectedOptionIndices = newSelected)
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val mistake = state.currentMistake ?: return
        val correctAnswer = mistake.correctAnswer ?: return

        // Parse correct answer letters to indices
        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val correctIndices = correctAnswer.toCharArray()
            .mapNotNull { c -> labelLetters.indexOf(c.toString()).takeIf { it >= 0 } }
            .toSet()

        val userIndices = state.selectedOptionIndices
        val isCorrect = userIndices == correctIndices

        _uiState.update {
            it.copy(
                showAnswer = true,
                isCorrect = isCorrect,
                correctIndices = correctIndices
            )
        }

        updateReviewRecord(mistake.id, isCorrect)
    }

    fun submitEssaySelfEval(isCorrect: Boolean) {
        val mistake = _uiState.value.currentMistake ?: return
        _uiState.update { it.copy(showAnswer = true, isCorrect = isCorrect) }
        updateReviewRecord(mistake.id, isCorrect)
    }

    fun skipEssay() {
        val mistake = _uiState.value.currentMistake ?: return
        _uiState.update { it.copy(showAnswer = true, isCorrect = null) }
        updateReviewRecord(mistake.id, false)
    }

    private fun updateReviewRecord(mistakeId: Long, isCorrect: Boolean) {
        viewModelScope.launch {
            val records = repository.getReviewRecordsByMistake(mistakeId).first()
            val latestRecord = records.maxByOrNull { it.reviewDate }

            val newCorrectCount = if (isCorrect) (latestRecord?.correctCount ?: 0) + 1 else 0
            val nextReviewDate = calculateNextReviewDate(newCorrectCount, isCorrect)

            val newRecord = ReviewRecord(
                id = latestRecord?.id ?: 0,
                mistakeId = mistakeId,
                reviewDate = System.currentTimeMillis(),
                result = if (isCorrect) ReviewResult.CORRECT else ReviewResult.WRONG,
                nextReviewDate = nextReviewDate,
                correctCount = newCorrectCount
            )
            repository.insertReviewRecord(newRecord)
        }
    }

    private fun calculateNextReviewDate(correctCount: Int, isCorrect: Boolean): Long {
        val now = System.currentTimeMillis()
        val oneDay = 86400000L
        return when {
            !isCorrect -> now + oneDay
            correctCount == 1 -> now + oneDay
            correctCount == 2 -> now + (3 * oneDay)
            correctCount == 3 -> now + (7 * oneDay)
            correctCount >= 4 -> -1
            else -> now + oneDay
        }
    }

    fun nextMistake() {
        currentIndex++
        if (currentIndex < reviewQueue.size) {
            _uiState.update {
                ReviewUiState(currentMistake = reviewQueue[currentIndex], isLoading = false)
            }
        } else {
            _uiState.update { it.copy(reviewComplete = true, currentMistake = null) }
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
git commit -m "refactor: rewrite ReviewViewModel without handwriting, add multi-select support"
```

---

### Task 5: 重写 ReviewScreen — 纯文本单列布局

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/AnalysisViewModel.kt` (fix if it references canvas types)

- [ ] **Step 1: 完全替换 ReviewScreen.kt**

```kotlin
package com.mistakenotes.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*

private val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
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
                    // Question type badge
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
                        Text(
                            text = "第 ${(uiState.hashCode() % 10) + 1} / 10 题",
                            color = TextCream.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }

                    // Question text
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("题目", color = AmberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!mistake.questionText.isNullOrBlank()) {
                                Text(mistake.questionText, color = TextCream, fontSize = 16.sp, lineHeight = 24.sp)
                            }
                            if (!mistake.title.isNullOrBlank() && mistake.title != mistake.questionText) {
                                Text(mistake.title, color = TextCream.copy(alpha = 0.9f), fontSize = 15.sp)
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
                                            color = if (isSelected || isCorrectAnswer || isWrongSelection) Color(0xFF111111) else TextCream,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Text(
                                        text = optionText.ifBlank { "(未填写)" },
                                        color = if (optionText.isBlank()) TextCream.copy(alpha = 0.4f) else TextCream,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Selection indicator
                                    if (mistake.questionType == QuestionType.SINGLE_CHOICE) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .border(2.dp, if (isSelected) AmberGold else TextCream.copy(alpha = 0.3f), RoundedCornerShape(50))
                                                .clip(RoundedCornerShape(50))
                                                .then(
                                                    if (isSelected) Modifier.background(AmberGold) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) Box(
                                                modifier = Modifier.size(8.dp).background(Color(0xFF111111), RoundedCornerShape(50))
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .border(2.dp, if (isSelected) AmberGold else TextCream.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (isSelected) Modifier.background(AmberGold) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) Text("", color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Submit button
                        if (!showResult) {
                            Button(
                                onClick = { viewModel.submitAnswer() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = uiState.selectedOptionIndices.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = InkStoneBlack),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("提交答案", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Essay self-evaluation
                    if (mistake.questionType == QuestionType.ESSAY && !showResult) {
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.submitEssaySelfEval(true) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color(0xFF111111)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("答对了", fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = { viewModel.submitEssaySelfEval(false) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = TextCream),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("答错了", fontWeight = FontWeight.Bold) }
                        }

                        OutlinedButton(
                            onClick = { viewModel.skipEssay() },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TextCream.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("跳过", color = TextCream.copy(alpha = 0.6f))
                        }
                    }

                    // Result banner after submission
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

                        // Next button
                        OutlinedButton(
                            onClick = { viewModel.nextMistake() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberGold),
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
```

- [ ] **Step 2: 检查 AnalysisViewModel 是否有 import canvas 类型**

```bash
grep -rn "canvas\|VectorStroke\|DrawingTool" app/src/main/java/com/mistakenotes/ui/screens/AnalysisViewModel.kt || echo "No canvas references found"
```

如果有 canvas 引用，清除相关 import 和类型引用。确认 AnalysisViewModel 中无 `VectorStroke`、`canvas` 等相关引用。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt app/src/main/java/com/mistakenotes/ui/screens/
git commit -m "refactor: rewrite ReviewScreen with text-only single column layout"
```

---

### Task 6: 验证构建

- [ ] **Step 1: Gradle Sync 并编译**

在 Android Studio 中执行 Sync Gradle，确保无编译错误。

或命令行运行：
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

预期：BUILD SUCCESSFUL，无编译错误。

- [ ] **Step 2: 修复可能的编译问题**

若编译有错误，检查：
- `ReviewViewModel` 的 `calculateNextReviewDate` 私有方法调用处需改为 `this.calculateNextReviewDate(...)`
- `import com.mistakenotes.ui.canvas.*` 引用已全部删除
- `ImportUiState` 和 `ReviewUiState` 字段名与 Screen 中引用一致
- `Dropdown` 泛型参数 `items: List<Any>` 可能导致类型问题：可改为分别保留 `ChapterDropdown` 和 `KnowledgePointDropdown` 两个独立 Composable

若 `Dropdown` 泛型有编译问题，替换为两个独立函数：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterDropdown(
    chapters: List<Chapter>,
    selectedChapterId: Long?,
    enabled: Boolean,
    onChapterSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selected = chapters.find { it.id == selectedChapterId }

    OutlinedTextField(
        value = selected?.name ?: if (enabled) "请选择章节" else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text("章节") },
        trailingIcon = {
            if (enabled) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextCream.copy(alpha = if (enabled) 1f else 0.4f))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { showDialog = true },
        colors = textFieldColors(enabled)
    )

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择章节", color = AmberGold) },
            text = {
                Column {
                    if (chapters.isEmpty()) Text("请先选择科目", color = TextCream.copy(alpha = 0.6f))
                    else chapters.forEach { chapter ->
                        TextButton(
                            onClick = { onChapterSelected(chapter.id); showDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(chapter.name, color = if (chapter.id == selectedChapterId) AmberGold else TextCream, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消", color = TextCream.copy(alpha = 0.6f)) } },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgePointDropdown(
    knowledgePoints: List<KnowledgePoint>,
    selectedKnowledgePointId: Long?,
    enabled: Boolean,
    onKnowledgePointSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selected = knowledgePoints.find { it.id == selectedKnowledgePointId }

    OutlinedTextField(
        value = selected?.name ?: if (enabled) "请选择知识点" else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text("知识点") },
        trailingIcon = {
            if (enabled) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextCream.copy(alpha = if (enabled) 1f else 0.4f))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { showDialog = true },
        colors = textFieldColors(enabled)
    )

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择知识点", color = AmberGold) },
            text = {
                Column {
                    if (knowledgePoints.isEmpty()) Text("请先选择章节", color = TextCream.copy(alpha = 0.6f))
                    else knowledgePoints.forEach { kp ->
                        TextButton(
                            onClick = { onKnowledgePointSelected(kp.id); showDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(kp.name, color = if (kp.id == selectedKnowledgePointId) AmberGold else TextCream, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("取消", color = TextCream.copy(alpha = 0.6f)) } },
            containerColor = CardDark,
            titleContentColor = AmberGold
        )
    }
}
```

- [ ] **Step 3: 提交（如有修复）**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/
git commit -m "fix: compilation fixes after handwriting removal"
```

---

## 实现检查清单

完成所有任务后验证：

- [ ] `ui/canvas/` 目录已删除
- [ ] `grep -r "canvas\|VectorStroke\|DrawingTool\|HandwritingCanvas" app/src/main/java/` 无结果
- [ ] 录入：选项按钮式录入，可添加/删除选项，可标记正确答案
- [ ] 单选：只能标记 1 个正确选项
- [ ] 多选：可标记多个正确选项
- [ ] 选项内容为空时，复习界面仍显示 A/B/C/D 标签
- [ ] 复习：选择题选项按钮交互正常
- [ ] 复习：提交后正确选项绿色、错误选项红色
- [ ] 复习：主观题显示自评按钮（答对了/答错了/跳过）
- [ ] 艾宾浩斯复习算法正常
- [ ] 首页和分析无影响
- [ ] Gradle 编译通过
