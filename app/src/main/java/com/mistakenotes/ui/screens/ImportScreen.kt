package com.mistakenotes.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.*

@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit = {},
    onSaveSuccess: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showCropConfirmDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setImageUri(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            // 保存临时照片
            val uri = viewModel.saveBitmapToFile(it)
            if (uri != null) {
                tempPhotoUri = uri
                showCropConfirmDialog = true
            }
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        showCropConfirmDialog = false
        tempPhotoUri = null
        // 编辑完成，刷新图片（ ACTION_EDIT 会直接修改原文件）
        viewModel.refreshCroppedImage()
    }

    // 拍照后裁剪确认对话框
    if (showCropConfirmDialog && tempPhotoUri != null) {
        AlertDialog(
            onDismissRequest = {
                showCropConfirmDialog = false
                tempPhotoUri?.let { viewModel.setImageUri(it) }
            },
            title = { Text("裁剪图片") },
            text = {
                Column {
                    Text("是否裁剪后再使用？")
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = tempPhotoUri,
                        contentDescription = "预览",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCropConfirmDialog = false
                    tempPhotoUri?.let { uri ->
                        // 使用系统图库编辑（小米支持）
                        val intent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        try {
                            cropLauncher.launch(intent)
                        } catch (e: Exception) {
                            // 编辑功能不可用，直接使用原图
                            viewModel.setImageUri(uri)
                        }
                    }
                }) {
                    Text("裁剪")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCropConfirmDialog = false
                    tempPhotoUri?.let { viewModel.setImageUri(it) }
                }) {
                    Text("直接使用")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSaveSuccess()
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "录入新题",
                color = InkStoneText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, "关闭", tint = InkStoneTextDim)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkStoneSurface)
                .border(2.dp, InkStoneBorder, RoundedCornerShape(12.dp))
                .clickable { imagePickerLauncher.launch("image/*") },
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = InkStoneTextDim,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "点击上传题目图片", color = InkStoneTextDim, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneAccent)
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("拍照")
            }

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent)
            ) {
                Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("相册")
            }
        }

        // 裁剪按钮（当有图片时显示，且不在裁剪确认对话框流程中）
        if (uiState.imageUri != null && tempPhotoUri == null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    uiState.imageUri?.let { uri ->
                        // 尝试使用系统图库编辑
                        val intent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }
                        try {
                            cropLauncher.launch(intent)
                        } catch (e: Exception) {
                            // 编辑功能不可用
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneAccent)
            ) {
                Icon(Icons.Default.Crop, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("裁剪")
            }
        }

        // 自动文字识别（当有图片且未识别过时自动触发）
        LaunchedEffect(uiState.imageUri) {
            if (uiState.imageUri != null && !uiState.recognizedTextVisible && !uiState.isRecognizing) {
                viewModel.recognizeText()
            }
        }

        // 文字识别状态
        if (uiState.imageUri != null && uiState.isRecognizing) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = InkStoneSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("文字识别中...", color = InkStoneText, fontSize = 14.sp)
                }
            }
        }

        // 显示识别题目（可编辑）
        if (uiState.recognizedTextVisible && uiState.recognizedQuestion.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = InkStoneSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "识别题目（可编辑）",
                            color = InkStoneAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { viewModel.dismissRecognizedText() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "关闭",
                                tint = InkStoneTextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.recognizedQuestion,
                        onValueChange = { viewModel.setRecognizedQuestion(it) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = InkStoneAccent,
                            unfocusedBorderColor = InkStoneBorder,
                            focusedTextColor = InkStoneText,
                            unfocusedTextColor = InkStoneText,
                            cursorColor = InkStoneAccent
                        ),
                        minLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "题目类型", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.questionType == QuestionType.SINGLE_CHOICE,
                onClick = { viewModel.setQuestionType(QuestionType.SINGLE_CHOICE) },
                label = { Text("单选") },
                leadingIcon = if (uiState.questionType == QuestionType.SINGLE_CHOICE) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkStoneAccent,
                    selectedLabelColor = InkStoneBg
                )
            )
            FilterChip(
                selected = uiState.questionType == QuestionType.MULTI_CHOICE,
                onClick = { viewModel.setQuestionType(QuestionType.MULTI_CHOICE) },
                label = { Text("多选") },
                leadingIcon = if (uiState.questionType == QuestionType.MULTI_CHOICE) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkStoneAccent,
                    selectedLabelColor = InkStoneBg
                )
            )
            FilterChip(
                selected = uiState.questionType == QuestionType.ESSAY,
                onClick = { viewModel.setQuestionType(QuestionType.ESSAY) },
                label = { Text("大题") },
                leadingIcon = if (uiState.questionType == QuestionType.ESSAY) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkStoneAccent,
                    selectedLabelColor = InkStoneBg
                )
            )
        }

        // 题目标题
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "题目标题", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.title,
            onValueChange = { viewModel.setTitle(it) },
            placeholder = { Text("输入题目标题（用于区分列表中的题目）", color = InkStoneTextDim) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = InkStoneAccent,
                unfocusedBorderColor = InkStoneBorder,
                focusedTextColor = InkStoneText,
                unfocusedTextColor = InkStoneText,
                cursorColor = InkStoneAccent
            ),
            singleLine = true
        )

        // 单选题和多选题答案选项
        if (uiState.questionType == QuestionType.SINGLE_CHOICE || uiState.questionType == QuestionType.MULTI_CHOICE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (uiState.questionType == QuestionType.SINGLE_CHOICE) "选择正确答案" else "选择正确答案（2-4个）",
                color = InkStoneTextDim, fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("A", "B", "C", "D").forEach { option ->
                    val isSelected = if (uiState.questionType == QuestionType.SINGLE_CHOICE) {
                        uiState.correctAnswer == option
                    } else {
                        uiState.selectedAnswers.contains(option)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (uiState.questionType == QuestionType.SINGLE_CHOICE) {
                                viewModel.setCorrectAnswer(option)
                            } else {
                                viewModel.toggleAnswer(option)
                            }
                        },
                        label = { Text(option) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InkStoneAccent,
                            selectedLabelColor = InkStoneBg
                        )
                    )
                }
            }
            if (uiState.questionType == QuestionType.SINGLE_CHOICE && uiState.correctAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已选：${uiState.correctAnswer}",
                    color = InkStoneAccent,
                    fontSize = 12.sp
                )
            } else if (uiState.questionType == QuestionType.MULTI_CHOICE && uiState.selectedAnswers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已选：${uiState.selectedAnswers.sorted().joinToString(", ")}",
                    color = InkStoneAccent,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "科目", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val subjects = listOf("数学", "英语", "物理", "化学", "生物")
            items(subjects) { subject ->
                FilterChip(
                    selected = uiState.subject == subject,
                    onClick = { viewModel.setSubject(subject) },
                    label = { Text(subject) },
                    leadingIcon = if (uiState.subject == subject) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InkStoneAccent,
                        selectedLabelColor = InkStoneBg
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "知识点标签（可多选）", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val availableTags = listOf("二次函数", "导数", "三角函数", "概率统计", "数列", "几何证明", "不等式", "函数图像")
            availableTags.forEach { tag ->
                val isSelected = uiState.selectedTags.contains(tag)
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { viewModel.toggleTag(tag) },
                    color = if (isSelected) InkStoneAccent else InkStoneSurface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = InkStoneBg)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(text = tag, color = if (isSelected) InkStoneBg else InkStoneText, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "解析（可选）", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.explanation,
            onValueChange = { viewModel.setExplanation(it) },
            placeholder = { Text("输入题目解析...", color = InkStoneTextDim) },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = InkStoneAccent,
                unfocusedBorderColor = InkStoneBorder,
                focusedTextColor = InkStoneText,
                unfocusedTextColor = InkStoneText,
                cursorColor = InkStoneAccent
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // 保存时使用图片路径（实际应该先保存图片到本地）
                viewModel.saveMistake(uiState.imageUri?.toString() ?: "")
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
            shape = RoundedCornerShape(12.dp),
            enabled = !uiState.isSaving && uiState.imageUri != null && when (uiState.questionType) {
                QuestionType.SINGLE_CHOICE -> uiState.correctAnswer.isNotBlank()
                QuestionType.MULTI_CHOICE -> uiState.selectedAnswers.size >= 2
                QuestionType.ESSAY -> uiState.correctAnswer.isNotBlank()
            }
        ) {
            if (uiState.isSaving) {
                Text("保存中...", fontSize = 16.sp, color = InkStoneBg)
            } else {
                Text("保存并开始复习计划", fontSize = 16.sp, color = InkStoneBg)
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = InkStoneError, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}