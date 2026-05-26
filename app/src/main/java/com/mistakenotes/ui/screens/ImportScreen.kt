package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

            // Classification dropdowns
            SubjectDropdown(
                subjects = uiState.subjects,
                selectedSubjectId = uiState.subjectId,
                onSubjectSelected = { viewModel.setSubject(it) }
            )
            ChapterDropdown(
                chapters = uiState.chapters,
                selectedChapterId = uiState.chapterId,
                enabled = uiState.subjectId != null,
                onChapterSelected = { viewModel.setChapter(it) }
            )
            KnowledgePointDropdown(
                knowledgePoints = uiState.knowledgePoints,
                selectedKnowledgePointId = uiState.knowledgePointId,
                enabled = uiState.chapterId != null,
                onKnowledgePointSelected = { viewModel.setKnowledgePoint(it) }
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

// ============================================================
// Option Entry Row
// ============================================================

@Composable
private fun OptionEntryRow(
    index: Int,
    text: String,
    isCorrect: Boolean,
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
        // Letter label badge
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

        // Text input for option content
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("输入选项内容（可为空）", color = TextCream.copy(alpha = 0.3f)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = textFieldColors()
        )

        // Mark correct toggle button
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
// Dropdown Components
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
            title = { Text("选择章节", color = AmberGold) },
            text = {
                Column {
                    if (chapters.isEmpty()) {
                        Text("请先选择科目", color = TextCream.copy(alpha = 0.6f))
                    } else {
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
            title = { Text("选择知识点", color = AmberGold) },
            text = {
                Column {
                    if (knowledgePoints.isEmpty()) {
                        Text("请先选择章节", color = TextCream.copy(alpha = 0.6f))
                    } else {
                        knowledgePoints.forEach { kp ->
                            TextButton(
                                onClick = { onKnowledgePointSelected(kp.id); showDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    kp.name,
                                    color = if (kp.id == selectedKnowledgePointId) AmberGold else TextCream,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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

// ============================================================
// Shared TextField Colors
// ============================================================

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
