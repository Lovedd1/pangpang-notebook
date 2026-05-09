package com.mistakenotes.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import coil.compose.AsyncImage
import com.mistakenotes.ui.theme.*

@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var questionType by remember { mutableStateOf("选择题") }
    var correctAnswer by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("数学") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var explanation by remember { mutableStateOf("") }

    val availableTags = listOf(
        "二次函数", "导数", "三角函数", "概率统计",
        "数列", "几何证明", "不等式", "函数图像"
    )

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        // TODO: 处理拍照结果
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题栏
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = InkStoneTextDim
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 图片上传区域
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
            if (selectedImageUri != null) {
                AsyncImage(
                    model = selectedImageUri,
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
                        tint = InkStoneTextDim,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击上传题目图片",
                        color = InkStoneTextDim,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 拍照按钮
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
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = InkStoneAccent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("拍照")
            }

            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkStoneAccent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("相册")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 题目类型
        Text(
            text = "题目类型",
            color = InkStoneTextDim,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = questionType == "选择题",
                onClick = { questionType = "选择题" },
                label = { Text("选择题") },
                leadingIcon = if (questionType == "选择题") {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkStoneAccent,
                    selectedLabelColor = InkStoneBg
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = questionType == "大题",
                onClick = { questionType = "大题" },
                label = { Text("大题") },
                leadingIcon = if (questionType == "大题") {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = InkStoneAccent,
                    selectedLabelColor = InkStoneBg
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 正确答案
        Text(
            text = if (questionType == "选择题") "正确答案（选项）" else "正确答案（关键词）",
            color = InkStoneTextDim,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = correctAnswer,
            onValueChange = { correctAnswer = it },
            placeholder = { Text("如：C 或 化简求值", color = InkStoneTextDim) },
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

        Spacer(modifier = Modifier.height(20.dp))

        // 科目选择
        Text(
            text = "科目",
            color = InkStoneTextDim,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subjects = listOf("数学", "英语", "物理", "化学", "生物")
            items(subjects) { subject ->
                FilterChip(
                    selected = selectedSubject == subject,
                    onClick = { selectedSubject = subject },
                    label = { Text(subject) },
                    leadingIcon = if (selectedSubject == subject) {
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

        // 知识点标签
        Text(
            text = "知识点标签（可多选）",
            color = InkStoneTextDim,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableTags.forEach { tag ->
                val isSelected = selectedTags.contains(tag)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            selectedTags = if (isSelected) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                    color = if (isSelected) InkStoneAccent else InkStoneSurface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = InkStoneBg
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = tag,
                            color = if (isSelected) InkStoneBg else InkStoneText,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 解析
        Text(
            text = "解析（可选）",
            color = InkStoneTextDim,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = explanation,
            onValueChange = { explanation = it },
            placeholder = { Text("输入题目解析...", color = InkStoneTextDim) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = InkStoneAccent,
                unfocusedBorderColor = InkStoneBorder,
                focusedTextColor = InkStoneText,
                unfocusedTextColor = InkStoneText,
                cursorColor = InkStoneAccent
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 保存按钮
        Button(
            onClick = {
                // TODO: 保存逻辑
                onSaveSuccess()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkStoneAccent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "保存并开始复习计划",
                fontSize = 16.sp,
                color = InkStoneBg
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}