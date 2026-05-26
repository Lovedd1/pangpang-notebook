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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*

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

    // Show snackbar for error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Navigate back on save success
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
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = AmberGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InkStoneBlack
                )
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
            // Image Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.imageUri != null) {
                        AsyncImage(
                            model = uiState.imageUri,
                            contentDescription = "题目图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = AmberGold.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击选择图片",
                                color = TextCream.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Question Type Selection
            Text(
                text = "题目类型",
                color = TextCream,
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.questionType == QuestionType.SINGLE_CHOICE,
                    onClick = { viewModel.setQuestionType(QuestionType.SINGLE_CHOICE) },
                    label = { Text("单选题") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = InkStoneBlack,
                        containerColor = CardDark,
                        labelColor = TextCream
                    )
                )
                FilterChip(
                    selected = uiState.questionType == QuestionType.MULTI_CHOICE,
                    onClick = { viewModel.setQuestionType(QuestionType.MULTI_CHOICE) },
                    label = { Text("多选题") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = InkStoneBlack,
                        containerColor = CardDark,
                        labelColor = TextCream
                    )
                )
                FilterChip(
                    selected = uiState.questionType == QuestionType.ESSAY,
                    onClick = { viewModel.setQuestionType(QuestionType.ESSAY) },
                    label = { Text("主观题") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = InkStoneBlack,
                        containerColor = CardDark,
                        labelColor = TextCream
                    )
                )
            }

            // Classification Selection - Subject
            SubjectDropdown(
                subjects = uiState.subjects,
                selectedSubjectId = uiState.subjectId,
                onSubjectSelected = { viewModel.setSubject(it) }
            )

            // Chapter
            ChapterDropdown(
                chapters = uiState.chapters,
                selectedChapterId = uiState.chapterId,
                enabled = uiState.subjectId != null,
                onChapterSelected = { viewModel.setChapter(it) }
            )

            // Question Text
            OutlinedTextField(
                value = uiState.questionText,
                onValueChange = { viewModel.setQuestionText(it) },
                label = { Text("题目描述") },
                modifier = Modifier.fillMaxWidth(),
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
                )
            )

            // Correct Answer for choice questions
            if (uiState.questionType != QuestionType.ESSAY) {
                OutlinedTextField(
                    value = uiState.correctAnswer,
                    onValueChange = { viewModel.setCorrectAnswer(it) },
                    label = { Text("正确答案（如：A）") },
                    modifier = Modifier.fillMaxWidth(),
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
                    )
                )
            }

            // Reference Answer for essay questions
            if (uiState.questionType == QuestionType.ESSAY) {
                OutlinedTextField(
                    value = uiState.referenceAnswer,
                    onValueChange = { viewModel.setReferenceAnswer(it) },
                    label = { Text("参考答案") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
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
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveMistake() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(
    subjects: List<com.mistakenotes.domain.model.Subject>,
    selectedSubjectId: Long?,
    onSubjectSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedSubject = subjects.find { it.id == selectedSubjectId }

    OutlinedTextField(
        value = selectedSubject?.name ?: "请选择科目",
        onValueChange = {},
        readOnly = true,
        label = { Text("科目") },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextCream
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextCream,
            unfocusedTextColor = TextCream,
            focusedBorderColor = AmberGold,
            unfocusedBorderColor = CardDark,
            focusedLabelColor = AmberGold,
            unfocusedLabelColor = TextCream.copy(alpha = 0.6f),
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark
        )
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择科目", color = AmberGold) },
            text = {
                Column {
                    subjects.forEach { subject ->
                        TextButton(
                            onClick = {
                                onSubjectSelected(subject.id)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = subject.name,
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
    chapters: List<com.mistakenotes.domain.model.Chapter>,
    selectedChapterId: Long?,
    enabled: Boolean,
    onChapterSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedChapter = chapters.find { it.id == selectedChapterId }

    OutlinedTextField(
        value = selectedChapter?.name ?: if (enabled) "请选择章节" else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text("章节") },
        trailingIcon = {
            if (enabled) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextCream.copy(alpha = if (enabled) 1f else 0.4f)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextCream,
            unfocusedTextColor = if (enabled) TextCream else TextCream.copy(alpha = 0.4f),
            focusedBorderColor = AmberGold,
            unfocusedBorderColor = CardDark,
            focusedLabelColor = AmberGold,
            unfocusedLabelColor = TextCream.copy(alpha = 0.6f),
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark
        )
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
                                onClick = {
                                    onChapterSelected(chapter.id)
                                    showDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = chapter.name,
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
    knowledgePoints: List<com.mistakenotes.domain.model.KnowledgePoint>,
    selectedKnowledgePointId: Long?,
    enabled: Boolean,
    onKnowledgePointSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedKp = knowledgePoints.find { it.id == selectedKnowledgePointId }

    OutlinedTextField(
        value = selectedKp?.name ?: if (enabled) "请选择知识点" else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text("知识点") },
        trailingIcon = {
            if (enabled) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextCream.copy(alpha = if (enabled) 1f else 0.4f)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextCream,
            unfocusedTextColor = if (enabled) TextCream else TextCream.copy(alpha = 0.4f),
            focusedBorderColor = AmberGold,
            unfocusedBorderColor = CardDark,
            focusedLabelColor = AmberGold,
            unfocusedLabelColor = TextCream.copy(alpha = 0.6f),
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark
        )
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
                                onClick = {
                                    onKnowledgePointSelected(kp.id)
                                    showDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = kp.name,
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