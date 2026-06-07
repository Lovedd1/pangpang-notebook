package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.Subject
import com.mistakenotes.ui.components.EntryDateRow
import com.mistakenotes.ui.theme.*
import com.mistakenotes.ui.screens.RagStatus

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

    // Crop overlay state
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    var cropIsAnswer by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cropTarget = it
            cropIsAnswer = false
        }
    }

    val answerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cropTarget = it
            cropIsAnswer = true
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.ragErrorMessage) {
        uiState.ragErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearRagError()
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
                title = { Text(if (uiState.isEditMode) "编辑错题" else "录入错题", color = AmberGold) },
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
            // Multi-image row
            Text("题目图片", color = TextCream, style = MaterialTheme.typography.titleSmall)
            MultiImageRow(
                images = uiState.imageUris,
                onAdd = { imagePickerLauncher.launch("image/*") },
                onRemove = { viewModel.removeImageUri(it) }
            )

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
            Box(modifier = Modifier.fillMaxWidth()) {
                ChapterDropdown(
                    chapters = uiState.chapters,
                    selectedChapterId = uiState.chapterId,
                    enabled = uiState.subjectId != null,
                    onChapterSelected = { viewModel.setChapter(it) }
                )
                if (uiState.ragStatus == RagStatus.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(20.dp),
                        color = AmberGold,
                        strokeWidth = 2.dp
                    )
                }
            }
            KnowledgePointDropdown(
                knowledgePoints = uiState.knowledgePoints,
                selectedKnowledgePointId = uiState.knowledgePointId,
                enabled = uiState.chapterId != null,
                onKnowledgePointSelected = { viewModel.setKnowledgePoint(it) }
            )

            // Entry date
            EntryDateRow(
                entryDate = uiState.entryDate,
                onDateSelected = { viewModel.setEntryDate(it) }
            )

            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.setTitle(it) },
                label = { Text("标题") },
                placeholder = { Text(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + "-01", color = TextCream.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
            )

            // Question text
            OutlinedTextField(
                value = uiState.questionText,
                onValueChange = { viewModel.setQuestionText(it) },
                label = { Text("题目描述") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )

            // Answer images (all question types)
            Text(
                text = if (uiState.questionType == QuestionType.ESSAY) "参考答案" else "答案/解析",
                color = TextCream,
                style = MaterialTheme.typography.titleSmall
            )
            MultiImageRow(
                images = uiState.answerImageUris,
                onAdd = { answerImageLauncher.launch("image/*") },
                onRemove = { viewModel.removeAnswerImageUri(it) }
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

            // Delete button (edit mode only)
            if (uiState.isEditMode) {
                var showDeleteConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !uiState.isDeleting,
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ErrorRed,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("删除错题", color = ErrorRed, fontSize = 15.sp)
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("确认删除", color = AmberGold) },
                        text = { Text("删除后无法恢复，确定要删除这条错题吗？", color = TextCream) },
                        confirmButton = {
                            TextButton(onClick = {
                                showDeleteConfirm = false
                                viewModel.deleteMistake()
                            }) { Text("删除", color = ErrorRed) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("取消", color = TextCream.copy(alpha = 0.6f))
                            }
                        },
                        containerColor = CardDark,
                        titleContentColor = AmberGold
                    )
                }
            }

            // Save / Update button
            Button(
                onClick = { viewModel.saveMistake() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSaving && !uiState.isDeleting,
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
                        if (uiState.isEditMode) "更新" else "保存",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Crop overlay
    cropTarget?.let { sourceUri ->
        CropScreen(
            sourceUri = sourceUri,
            onCropComplete = { croppedUri ->
                if (cropIsAnswer) viewModel.addAnswerImageUri(croppedUri)
                else viewModel.addImageUri(croppedUri)
                cropTarget = null
            },
            onCancel = { cropTarget = null }
        )
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
// Multi-Image Row
// ============================================================

@Composable
private fun MultiImageRow(
    images: List<Uri>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(images) { index, uri ->
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardDark)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "图片 ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(
                            InkStoneBlack.copy(alpha = 0.8f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除",
                        tint = ErrorRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加图片",
                        tint = AmberGold.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "添加",
                        color = AmberGold.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
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
