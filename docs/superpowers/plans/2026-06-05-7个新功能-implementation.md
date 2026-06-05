# 7 个新功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CPA 错题笔记应用增加 7 个新功能（选择题答案图片 / 题号弹窗 / 已掌握按钮 / 已掌握列表复习 / 录入时间 / 题型筛选 / 图片放大），全部复用现有字段不修改 schema。

**Architecture:** 5 阶段实施。先建可复用组件，再分别改造录入页/复习页/列表页，最后更新文档与真机验证。数据保护原则：零 schema 变更。

**Tech Stack:** Kotlin 2.0 + Jetpack Compose (BOM 2025.02.00)、Material3、Coil 2.6.0、Room 2.7.0（不改 schema）。**项目无 gradlew，验证通过 Android Studio 真机调试**（"Run" 按钮覆盖安装）。

---

## File Structure

### 新增（7 个组件）
| 文件 | 职责 |
|------|------|
| `ui/components/QuestionTypeFilter.kt` | 3 chip 多选，单选/多选/主观题 |
| `ui/components/ZoomableImage.kt` | 双指缩放 + 双击放大 + 拖动 |
| `ui/components/ImagePreviewDialog.kt` | 全屏黑色背景预览 |
| `ui/components/AdaptiveImage.kt` | 按图片实际比例自适应大小，点击触发预览 |
| `ui/components/EntryDateRow.kt` | "录入时间"行 + DatePickerDialog |
| `ui/components/JumpToQuestionDialog.kt` | 题号网格弹窗 |
| `ui/components/RescheduleDialog.kt` | 1~10 步进器，默认 7 |

### 修改（9 个文件）
| 文件 | 变更 |
|------|------|
| `ui/screens/ImportViewModel.kt` | 新增 `entryDate` 状态；保存时 `createdAt/nextReviewDate` 用 `entryDate` |
| `ui/screens/ImportScreen.kt` | 答案图片对所有题型开放；新增 `EntryDateRow` |
| `ui/screens/ReviewViewModel.kt` | 新增 `jumpTo(index)` + `markAsMastered(id)`；抽取 `loadMistakeAtCurrentIndex()` |
| `ui/screens/ReviewScreen.kt` | 顶栏 `X/N` 题号 + 已掌握按钮 + `AdaptiveImage` + `ImagePreviewDialog` |
| `ui/screens/HomeViewModel.kt` | 新增 `selectedQuestionTypes` + 过滤 |
| `ui/screens/HomeScreen.kt` | 今日/逾期标题旁加 `QuestionTypeFilter` |
| `ui/screens/BrowseViewModel.kt` | 新增 `selectedQuestionTypes` + `reschedule(id, days)` |
| `ui/screens/BrowseScreen.kt` | 加 `QuestionTypeFilter` + 已掌握模式 🕐 按钮 + `RescheduleDialog` |
| `CLAUDE.md` | "数据保护（强制）"段落 |

### 不修改
- 全部 Room Entity / Dao / AppDatabase / DatabaseModule
- 任何 Migration 文件
- 数据库版本号（保持 6）

---

## Phase 1 — 可复用组件

### Task 1: QuestionTypeFilter 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/QuestionTypeFilter.kt`

- [ ] **Step 1: 创建组件文件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream

/**
 * 3 chip 多选：单选/多选/主观题
 * @param selected 当前选中的题型集合（空集 = 不过滤，全部显示）
 * @param onSelectionChange 选中状态变化回调
 */
@Composable
fun QuestionTypeFilter(
    selected: Set<QuestionType>,
    onSelectionChange: (Set<QuestionType>) -> Unit
) {
    val allTypes = QuestionType.entries

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(allTypes) { type ->
            val isSelected = type in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSet = if (isSelected) selected - type else selected + type
                    // 兜底：全不选 = 不过滤（用空集表示"全部"）
                    onSelectionChange(newSet)
                },
                label = {
                    Text(
                        when (type) {
                            QuestionType.SINGLE_CHOICE -> "单选"
                            QuestionType.MULTI_CHOICE -> "多选"
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
}
```

> 注：上方用了 `items(allTypes)` —— 需补 import `androidx.compose.foundation.lazy.items`。

修正 import（覆盖 Step 1 内容）：

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream

/**
 * 3 chip 多选：单选/多选/主观题
 * @param selected 当前选中的题型集合（空集 = 不过滤，全部显示）
 * @param onSelectionChange 选中状态变化回调
 */
@Composable
fun QuestionTypeFilter(
    selected: Set<QuestionType>,
    onSelectionChange: (Set<QuestionType>) -> Unit
) {
    val allTypes = QuestionType.entries

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(allTypes) { type ->
            val isSelected = type in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSet = if (isSelected) selected - type else selected + type
                    onSelectionChange(newSet)
                },
                label = {
                    Text(
                        when (type) {
                            QuestionType.SINGLE_CHOICE -> "单选"
                            QuestionType.MULTI_CHOICE -> "多选"
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
}
```

- [ ] **Step 2: Android Studio Sync 验证**

操作：File → Sync Project with Gradle Files。期望：编译通过，无 import 错误。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/QuestionTypeFilter.kt
git commit -m "feat: add QuestionTypeFilter component (multi-select chips)"
```

---

### Task 2: ZoomableImage 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/ZoomableImage.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import java.io.File

private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 5f

/**
 * 可缩放图片：双指缩放/拖动 + 双击切换 1x ↔ 2.5x
 * 用于全屏预览（容器一般 fillMaxSize）
 */
@Composable
fun ZoomableImage(
    file: File,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    // 缩放回 1x 时重置偏移
                    if (newScale == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/ZoomableImage.kt
git commit -m "feat: add ZoomableImage component (pinch-zoom + double-tap)"
```

---

### Task 3: ImagePreviewDialog 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/ImagePreviewDialog.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream
import java.io.File

/**
 * 全屏图片预览：黑色背景 + ZoomableImage + 右上角关闭按钮
 * @param file 要显示的图片文件
 * @param onDismiss 关闭回调（点击关闭按钮或物理返回键）
 */
@Composable
fun ImagePreviewDialog(
    file: File,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(InkStoneBlack)
        ) {
            ZoomableImage(
                file = file,
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = TextCream
                )
            }
        }
    }
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/ImagePreviewDialog.kt
git commit -m "feat: add ImagePreviewDialog component (fullscreen preview)"
```

---

### Task 4: AdaptiveImage 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/AdaptiveImage.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.platform.LocalContext

/**
 * 自适应大小图片：根据原图宽高比计算 displayHeight，
 * 宽度 = 容器宽，高度 = 容器宽 / ratio，限 maxImageHeight（屏幕 2/3），
 * 始终 ContentScale.Fit，不裁剪图片。
 *
 * @param file 图片文件
 * @param onClick 点击图片回调（一般弹 ImagePreviewDialog）
 * @param maxHeight 上限高度（默认屏幕 2/3）
 * @param modifier 外部 modifier
 */
@Composable
fun AdaptiveImage(
    file: File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    // 默认上限 = 屏幕高度 * 0.66
    val resolvedMaxHeight = maxHeight
        ?: with(density) { (configuration.screenHeightDp * 0.66f).dp }

    var ratio by remember(file.path) { mutableStateOf<Float?>(null) }

    LaunchedEffect(file.path) {
        ratio = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(file)
                    .allowHardware(false)
                    .build()
                val result = coil.ImageLoader(context).execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val w = drawable.intrinsicWidth
                    val h = drawable.intrinsicHeight
                    if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        val containerWidth = maxWidth

        if (ratio == null) {
            // 比例未知：兜底显示 Loading + 占位高度
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resolvedMaxHeight)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中…")
            }
        } else {
            val r = ratio!!
            // displayWidth = containerWidth（限宽）
            // displayHeight = containerWidth / r
            val computedHeight = containerWidth / r
            // 限高：height 不超过 resolvedMaxHeight
            val finalHeight = if (computedHeight > resolvedMaxHeight) {
                resolvedMaxHeight
            } else {
                computedHeight
            }
            // 限高时宽度同步缩
            val finalWidth = if (computedHeight > resolvedMaxHeight) {
                resolvedMaxHeight * r
            } else {
                containerWidth
            }

            Box(
                modifier = Modifier
                    .width(finalWidth)
                    .height(finalHeight)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/AdaptiveImage.kt
git commit -m "feat: add AdaptiveImage component (auto-size + click to preview)"
```

---

### Task 5: EntryDateRow 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/EntryDateRow.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.TextCream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 录入时间行：显示当前 entryDate（默认今天），点击弹出 DatePickerDialog。
 * @param entryDate 已选 epoch millis（null 表示未选 = 默认今天）
 * @param onDateSelected 选择日期回调（接收 00:00:00 归一化后的 millis）
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EntryDateRow(
    entryDate: Long?,
    onDateSelected: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today00 = cal.timeInMillis

    val displayDate = entryDate ?: today00
    val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(displayDate))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "录入时间：$dateText",
            color = TextCream,
            fontSize = 15.sp
        )
        Icon(
            Icons.Default.CalendarToday,
            contentDescription = "选择日期",
            tint = AmberGold,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = displayDate
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        // 归一化为 00:00:00
                        val c = Calendar.getInstance().apply {
                            timeInMillis = selected
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        onDateSelected(c.timeInMillis)
                    }
                    showPicker = false
                }) {
                    Text("确定", color = AmberGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("取消", color = TextCream)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/EntryDateRow.kt
git commit -m "feat: add EntryDateRow component (DatePickerDialog for entry date)"
```

---

### Task 6: JumpToQuestionDialog 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/JumpToQuestionDialog.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.ErrorRed
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.SuccessGreen
import com.mistakenotes.ui.theme.TextCream

/**
 * 跳题弹窗：题号网格 + 状态图标
 * @param total 总题数
 * @param currentIndex 当前题号 (0-based)
 * @param results Map<index, ReviewResult?> — null=未复习，CORRECT/WRONG/SKIP
 * @param onJump 跳转到指定 index 回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToQuestionDialog(
    total: Int,
    currentIndex: Int,
    results: Map<Int, ReviewResult?>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到题目", color = AmberGold) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed((0 until total).toList()) { index, _ ->
                    val result = results[index]
                    val isCurrent = index == currentIndex

                    val (bg, fg) = when {
                        isCurrent -> AmberGold to InkStoneBlack
                        result == ReviewResult.CORRECT -> SuccessGreen.copy(alpha = 0.3f) to SuccessGreen
                        result == ReviewResult.WRONG -> ErrorRed.copy(alpha = 0.3f) to ErrorRed
                        result == ReviewResult.SKIP -> AmberGold.copy(alpha = 0.2f) to AmberGold
                        else -> CardDark to TextCream.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { onJump(index); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = fg,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextCream)
            }
        },
        containerColor = CardDark,
        titleContentColor = AmberGold
    )
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/JumpToQuestionDialog.kt
git commit -m "feat: add JumpToQuestionDialog component (question grid)"
```

---

### Task 7: RescheduleDialog 组件

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/components/RescheduleDialog.kt`

- [ ] **Step 1: 创建组件**

```kotlin
package com.mistakenotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.CardDark
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream

/**
 * 重新安排复习：1~10 天步进器，默认 7
 * @param initialDays 初始值（默认 7）
 * @param onConfirm 确认回调，参数为天数（1-10）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    initialDays: Int = 7,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var days by remember { mutableStateOf(initialDays.coerceIn(1, 10)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("N 天后复习", color = AmberGold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (days > 1) days-- },
                    enabled = days > 1
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "减少",
                        tint = if (days > 1) AmberGold else TextCream.copy(alpha = 0.3f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$days 天",
                    color = TextCream,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { if (days < 10) days++ },
                    enabled = days < 10
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "增加",
                        tint = if (days < 10) AmberGold else TextCream.copy(alpha = 0.3f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days); onDismiss() }) {
                Text("确认", color = AmberGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextCream)
            }
        },
        containerColor = CardDark,
        titleContentColor = AmberGold
    )
}
```

- [ ] **Step 2: Sync 验证编译通过**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/components/RescheduleDialog.kt
git commit -m "feat: add RescheduleDialog component (1-10 day stepper)"
```

---

## Phase 2 — 录入页（功能 1 + 功能 5）

### Task 8: ImportViewModel — entryDate 状态

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

- [ ] **Step 1: ImportUiState 新增 entryDate 字段**

找到 `data class ImportUiState`（约 28-47 行），在 `isEditMode: Boolean = false,` 之后新增：

```kotlin
val entryDate: Long? = null,
```

完整 ImportUiState 参考（确保 isEditMode 在最后）：

```kotlin
data class ImportUiState(
    val imageUris: List<Uri> = emptyList(),
    val title: String = "",
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val optionEntries: List<String> = listOf("", "", "", ""),
    val correctOptionIndices: Set<Int> = emptySet(),
    val answerImageUris: List<Uri> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false,
    val entryDate: Long? = null
)
```

- [ ] **Step 2: 新增 setEntryDate 方法**

在 `setQuestionType` 方法之前添加：

```kotlin
fun setEntryDate(epochMillis: Long) {
    _uiState.update { it.copy(entryDate = epochMillis) }
}
```

- [ ] **Step 3: loadMistakeForEditing 初始化 entryDate**

在 `loadMistakeForEditing` 的 `_uiState.update` 块中（`loadMistakeForEditing(id)` 方法，约 87-108 行），追加：

```kotlin
entryDate = mistake.createdAt,
```

修改后的 update 块（仅末尾追加一行）：

```kotlin
_uiState.update {
    it.copy(
        title = mistake.title.ifBlank { "" },
        questionText = mistake.questionText ?: "",
        subjectId = mistake.subjectId,
        chapterId = mistake.chapterId,
        knowledgePointId = mistake.knowledgePointId,
        questionType = mistake.questionType,
        optionEntries = entries,
        correctOptionIndices = correctIndices,
        imageUris = mistake.getQuestionImagePaths().mapNotNull { path ->
            val file = File(path)
            if (file.exists()) Uri.fromFile(file) else null
        },
        answerImageUris = mistake.getAnswerImagePaths().mapNotNull { path ->
            val file = File(path)
            if (file.exists()) Uri.fromFile(file) else null
        },
        errorMessage = null,
        isEditMode = true,
        entryDate = mistake.createdAt
    )
}
```

- [ ] **Step 4: saveMistake 使用 entryDate**

修改 `saveMistake()` 方法（约 263-326 行）：

找到 `val now = System.currentTimeMillis()` 之后（约 264 行），在 `val optionsStr` 之前添加：

```kotlin
// 计算 entryDate（用户选定的录入时间，null = 今天）
val cal = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}
val today00 = cal.timeInMillis
val entryDateMs = state.entryDate ?: today00
val finalNextReviewDate = entryDateMs + 5 * 86400000L
```

找到 `createdAt = existingMistake?.createdAt ?: now,` 改为：

```kotlin
createdAt = existingMistake?.createdAt ?: entryDateMs,
```

找到 `// Auto-generate title if blank` 块（`if (state.title.isBlank())`），把内层 `val dateStr = SimpleDateFormat(...)` 改为：

```kotlin
val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(entryDateMs))
```

把 `val todayCount = repository.getAllMistakes().first().count { it.createdAt in todayStart..todayEnd } + 1` 改为：

```kotlin
val entryCal = java.util.Calendar.getInstance().apply {
    timeInMillis = entryDateMs
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    add(java.util.Calendar.DAY_OF_MONTH, 1)
    val entryEnd = timeInMillis - 1
}
val todayCount = repository.getAllMistakes().first()
    .count { it.createdAt in entryDateMs..entryEnd } + 1
```

找到 `val fiveDaysMs = 5 * 86400000L` 行，改为：

```kotlin
// nextReviewDate = entryDate + 5天（不是 now + 5天）
```

在两个分支（isEdit / else）中，`nextReviewDate = now + fiveDaysMs` 改为 `nextReviewDate = finalNextReviewDate`。

具体：

`isEdit` 分支：
```kotlin
repository.insertReviewRecord(
    ReviewRecord(
        mistakeId = editingMistakeId,
        reviewDate = now,
        result = ReviewResult.SKIP,
        nextReviewDate = finalNextReviewDate,
        correctCount = 0
    )
)
```

`else` 分支：
```kotlin
repository.insertReviewRecord(
    ReviewRecord(
        mistakeId = mistakeId,
        reviewDate = now,
        result = ReviewResult.SKIP,
        nextReviewDate = finalNextReviewDate,
        correctCount = 0
    )
)
```

- [ ] **Step 5: Sync 验证编译通过**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): add entryDate state, save uses entryDate for createdAt/nextReview"
```

---

### Task 9: ImportScreen — 答案图片全题型 + EntryDateRow

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`

- [ ] **Step 1: 答案图片对所有题型开放**

找到 `// Answer images (essay only)` 注释（约 172 行），改为：

```kotlin
// Answer images (all question types)
```

删除 `if (uiState.questionType == QuestionType.ESSAY) {` 包裹（保留内部全部内容）。把 `Text("答案图片", color = TextCream, style = MaterialTheme.typography.titleSmall)` 改为：

```kotlin
Text(
    text = if (uiState.questionType == QuestionType.ESSAY) "参考答案" else "答案/解析",
    color = TextCream,
    style = MaterialTheme.typography.titleSmall
)
```

找到对应的 `val answerImageLauncher = rememberLauncherForActivityResult(...)` 块（嵌套在 if 内），把它移到 if 外面（因为不再需要条件）。在 `// Answer images` 之前声明 `cropIsAnswer` 已存在。Launcher 块保留并外移。

修改后结构：

```kotlin
val answerImageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let {
        cropTarget = it
        cropIsAnswer = true
    }
}

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
```

- [ ] **Step 2: 在"标题"前添加 EntryDateRow**

找到 `// Title` 注释（约 152 行）。在 `// Title` 之前添加：

```kotlin
// Entry date
EntryDateRow(
    entryDate = uiState.entryDate,
    onDateSelected = { viewModel.setEntryDate(it) }
)
```

- [ ] **Step 3: 添加 import**

在文件顶部 import 块追加：

```kotlin
import com.mistakenotes.ui.components.EntryDateRow
```

- [ ] **Step 4: Sync 验证编译通过**

- [ ] **Step 5: 真机验证（手动）**

1. 启动 App → 录入错题
2. 选择单选/多选题型 → 应能看到"答案/解析"图片上传块
3. 选择主观题 → 应能看到"参考答案"图片上传块
4. 上传几张答案图片，保存
5. 重新录入 → 看到"录入时间"行，默认今天
6. 点击"录入时间" → 弹出 DatePicker
7. 选 5 天前 → 看到显示新日期
8. 保存 → 跳到主页

期望：所有 UI 正常，图片上传、日期选择都工作。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "feat(import): answer images for all types + entry date row"
```

---

## Phase 3 — 复习页（功能 2 + 3 + 7）

### Task 10: ReviewViewModel — jumpTo + markAsMastered + 暴露 StateFlow

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`

**前置改造**：把现有的 `private var currentIndex` 和 `private var reviewQueue` 包装为 StateFlow，让 ReviewScreen 能响应式地显示题号。

- [ ] **Step 0: 改造 currentIndex 和 reviewQueue 为 StateFlow**

找到 `private var reviewQueue = mutableListOf<Mistake>()` 和 `private var currentIndex = 0`，改为：

```kotlin
private val _reviewQueue = MutableStateFlow<List<Mistake>>(emptyList())
private val _currentIndex = MutableStateFlow(0)
val reviewQueueFlow: StateFlow<List<Mistake>> = _reviewQueue.asStateFlow()
val currentIndexFlow: StateFlow<Int> = _currentIndex.asStateFlow()
val queueSize: Int get() = _reviewQueue.value.size
val reviewedResultsMap: Map<Int, Boolean> get() = reviewedResults.toMap()
```

并把所有 `currentIndex` 读取改为 `_currentIndex.value`，写入改为 `_currentIndex.value = X`。把所有 `reviewQueue` 读取改为 `_reviewQueue.value`，写入改为 `_reviewQueue.value = X`。

具体涉及位置（这些行需要调整）：

- `loadReviewQueue()` 中：
  - `reviewQueue = ReviewSession.queue.toMutableList()` → `_reviewQueue.value = ReviewSession.queue`
  - `currentIndex = ReviewSession.startIndex.coerceIn(...)` → `_currentIndex.value = ...`
  - `reviewQueue = queue.toMutableList()` → `_reviewQueue.value = queue`
  - `reviewQueue.shuffle()` → `_reviewQueue.value = _reviewQueue.value.shuffled()`
  - `currentIndex = 0` → `_currentIndex.value = 0`
  - `reviewQueue.first()` → `_reviewQueue.value.first()`
  - `reviewQueue.isNotEmpty()` → `_reviewQueue.value.isNotEmpty()`

- `loadMistakeAtCurrentIndex()`（Step 1 抽取的方法）内：
  - `if (reviewQueue.isEmpty()) return` → `if (_reviewQueue.value.isEmpty()) return`
  - `val mistake = reviewQueue[currentIndex]` → `val mistake = _reviewQueue.value[_currentIndex.value]`

- `nextMistake()`：
  - `currentIndex++` → `_currentIndex.value++`
  - `if (currentIndex >= reviewQueue.size)` → `if (_currentIndex.value >= _reviewQueue.value.size)`
  - `currentIndex = 0` → `_currentIndex.value = 0`

- `jumpTo()`（Step 3 新增）：
  - `if (reviewQueue.isEmpty()) return` → `if (_reviewQueue.value.isEmpty()) return`
  - `currentIndex = index.coerceIn(0, reviewQueue.size - 1)` → `_currentIndex.value = index.coerceIn(0, _reviewQueue.value.size - 1)`

- `submitAnswer()`、`submitEssaySelfEval()`、`skipEssay()`：
  - `reviewedIndices.add(currentIndex)` → `reviewedIndices.add(_currentIndex.value)`
  - `reviewedResults[currentIndex] = ...` → `reviewedResults[_currentIndex.value] = ...`

- `toggleFavorite()`：
  - `mistake.copy(...)` 引用 mistake 直接从 state 取，不变

- [ ] **Step 1: 抽取 loadMistakeAtCurrentIndex 私有方法**

将 `nextMistake()` 中"加载当前题"逻辑提取为 `loadMistakeAtCurrentIndex()`。新方法：

```kotlin
private fun loadMistakeAtCurrentIndex() {
    if (reviewQueue.isEmpty()) return
    val mistake = reviewQueue[currentIndex]
    val isPreReviewed = currentIndex in ReviewSession.preReviewedIndices
    if (currentIndex in reviewedIndices || isPreReviewed) {
        // Already reviewed — show result
        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val correctIndices = (mistake.correctAnswer ?: "").map { c ->
            labelLetters.indexOf(c.toString())
        }.filter { it >= 0 }.toSet()
        _uiState.update {
            it.copy(
                currentMistake = mistake,
                isLoading = false,
                showAnswer = true,
                correctIndices = correctIndices,
                isCorrect = reviewedResults[currentIndex]
                    ?: ReviewSession.preReviewedResults[currentIndex]
            )
        }
    } else {
        // Fresh card
        _uiState.update {
            ReviewUiState(currentMistake = mistake, isLoading = false)
        }
    }
}
```

- [ ] **Step 2: 改造 nextMistake 用新方法**

把 `nextMistake()` 中从 `if (reviewQueue.isNotEmpty()) {` 开始的 if 块替换为：

```kotlin
fun nextMistake() {
    currentIndex++
    if (currentIndex >= reviewQueue.size) {
        currentIndex = 0
    }
    loadMistakeAtCurrentIndex()
}
```

完整 nextMistake：

```kotlin
fun nextMistake() {
    currentIndex++
    if (currentIndex >= reviewQueue.size) {
        currentIndex = 0
    }
    loadMistakeAtCurrentIndex()
}
```

- [ ] **Step 3: 新增 jumpTo 方法**

在 nextMistake 之后添加：

```kotlin
fun jumpTo(index: Int) {
    if (reviewQueue.isEmpty()) return
    currentIndex = index.coerceIn(0, reviewQueue.size - 1)
    loadMistakeAtCurrentIndex()
}
```

- [ ] **Step 4: 新增 markAsMastered 方法**

在 jumpTo 之后添加：

```kotlin
fun markAsMastered(mistakeId: Long) {
    viewModelScope.launch {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val tomorrowStart = cal.timeInMillis
        repository.insertReviewRecord(
            ReviewRecord(
                id = 0,
                mistakeId = mistakeId,
                reviewDate = System.currentTimeMillis(),
                result = ReviewResult.CORRECT,
                nextReviewDate = tomorrowStart,
                correctCount = 3
            )
        )
    }
}
```

- [ ] **Step 5: Sync 验证编译通过**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
git commit -m "feat(review): add jumpTo + markAsMastered; refactor loadMistakeAtCurrentIndex"
```

---

### Task 11: ReviewScreen — 顶栏题号可点击 + 已掌握按钮

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

- [ ] **Step 1: ReviewScreen 顶部 composable 体添加 state 与 currentIndexFlow 收集**

在 `val uiState by viewModel.uiState.collectAsState()` 之后添加：

```kotlin
val currentIndexValue by viewModel.currentIndexFlow.collectAsState()
var showJumpDialog by remember { mutableStateOf(false) }
var showMasteredConfirm by remember { mutableStateOf(false) }
var previewFile by remember { mutableStateOf<java.io.File?>(null) }
```

- [ ] **Step 2: 替换 TopAppBar 的 title**

找到 `title = { Text("复习", color = TextCream, fontWeight = FontWeight.Bold) },`

替换为：

```kotlin
title = {
    val displayText = if (uiState.currentMistake != null) {
        "${currentIndexValue + 1} / ${viewModel.queueSize}"
    } else "复习"
    Text(
        text = displayText,
        color = TextCream,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable { showJumpDialog = true }
    )
},
```

- [ ] **Step 3: 顶栏 actions 添加已掌握按钮**

找到 actions 块（仅收藏按钮的），在收藏 IconButton 之后、actions 的 `}` 之前添加：

```kotlin
if (uiState.showAnswer) {
    IconButton(onClick = { showMasteredConfirm = true }) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "已掌握",
            tint = AmberGold,
            modifier = Modifier.size(24.dp)
        )
    }
}
```

- [ ] **Step 4: 添加 imports**

文件顶部 import 块追加：

```kotlin
import com.mistakenotes.ui.components.JumpToQuestionDialog
import com.mistakenotes.ui.components.ImagePreviewDialog
import androidx.compose.material.icons.filled.CheckCircle
```

- [ ] **Step 5: 末尾添加 JumpToQuestionDialog + 已掌握确认 + ImagePreviewDialog**

在 `ReviewScreen` composable 末尾（最后一个 `}` 之前）添加：

```kotlin
if (showJumpDialog && uiState.currentMistake != null) {
    JumpToQuestionDialog(
        total = viewModel.queueSize,
        currentIndex = currentIndexValue,
        results = viewModel.reviewedResultsMap.mapValues {
            if (it.value) ReviewResult.CORRECT else ReviewResult.WRONG
        },
        onJump = { viewModel.jumpTo(it) },
        onDismiss = { showJumpDialog = false }
    )
}

if (showMasteredConfirm) {
    AlertDialog(
        onDismissRequest = { showMasteredConfirm = false },
        title = { Text("标记为已掌握？", color = AmberGold) },
        text = { Text("明天起该题不再进入今日/逾期列表。", color = TextCream) },
        confirmButton = {
            TextButton(onClick = {
                showMasteredConfirm = false
                uiState.currentMistake?.id?.let { viewModel.markAsMastered(it) }
            }) {
                Text("确认", color = AmberGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showMasteredConfirm = false }) {
                Text("取消", color = TextCream)
            }
        },
        containerColor = CardDark,
        titleContentColor = AmberGold
    )
}

previewFile?.let { file ->
    ImagePreviewDialog(file = file, onDismiss = { previewFile = null })
}
```

- [ ] **Step 6: Sync 验证编译通过**

- [ ] **Step 7: 真机验证（手动）**

1. 启动 App → 主页 → 进入今日复习（至少 3 题）
2. 顶栏显示 "1 / 3" 可点击
3. 点击 → 弹题号网格，绿色 ✓ / 红色 ✗ / 灰色 / 橙色 ⏸ 状态正确
4. 跳到第 1 题 → 显示已复习结果
5. 答第 2 题 → 提交 → 顶栏出现金色 ✓ 按钮
6. 点击"已掌握" → 弹确认 → 确认
7. 回主页 → 该题仍显示在今日，标签"✓ 正确"
8. （修改系统时间到明天 或 直接看已掌握列表）→ 题目出现在已掌握列表

期望：所有交互正常，状态正确。

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
git commit -m "feat(review): top-bar X/N jump dialog + mastered button"
```

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt
git commit -m "feat(review): top-bar X/N jump dialog + mastered button"
```

---

### Task 12: ReviewScreen — 题目/答案图片替换为 AdaptiveImage

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

- [ ] **Step 1: 单图替换为 AdaptiveImage**

找到题目图片块（`else if (questionPaths.isNotEmpty()) {` 约 142-153 行），替换为：

```kotlin
} else if (questionPaths.isNotEmpty()) {
    AdaptiveImage(
        file = File(questionPaths.first()),
        onClick = { previewFile = File(questionPaths.first()) }
    )
    Spacer(modifier = Modifier.height(12.dp))
}
```

- [ ] **Step 2: MultiImagePager 中每个 page 替换**

找到 `MultiImagePager(paths = questionPaths, maxHeight = maxImageHeight)` 替换为：

```kotlin
// 用本地内联 pager，因为需要 onClick 触发预览
QuestionImagePager(
    paths = questionPaths,
    onImageClick = { path -> previewFile = File(path) }
)
```

> 由于 MultiImagePager 是私有函数且不支持 onClick，下面新增本地 QuestionImagePager 组件（Step 3）。

- [ ] **Step 3: 新增 QuestionImagePager 私有组件**

在文件末尾（MultiImagePager 之后）添加：

```kotlin
@Composable
private fun QuestionImagePager(
    paths: List<String>,
    onImageClick: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { paths.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            AdaptiveImage(
                file = File(paths[page]),
                onClick = { onImageClick(paths[page]) }
            )
        }

        if (paths.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(paths.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (pagerState.currentPage == index) AmberGold
                                else TextCream.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: 答案图片同样替换**

找到 `if (answerPaths.size > 1) { MultiImagePager(...) } else if (answerPaths.isNotEmpty()) { AsyncImage(...) }`（约 346-355 行）：

```kotlin
if (answerPaths.size > 1) {
    QuestionImagePager(
        paths = answerPaths,
        onImageClick = { path -> previewFile = File(path) }
    )
} else if (answerPaths.isNotEmpty()) {
    AdaptiveImage(
        file = File(answerPaths.first()),
        onClick = { previewFile = File(answerPaths.first()) }
    )
}
```

- [ ] **Step 5: 删除现有 MultiImagePager 私有函数**

删除文件末尾原 `MultiImagePager` 私有函数（被 QuestionImagePager 替代）。

- [ ] **Step 6: 添加 imports**

```kotlin
import com.mistakenotes.ui.components.AdaptiveImage
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
```

- [ ] **Step 7: Sync 验证编译通过**

- [ ] **Step 8: 真机验证（手动）**

1. 启动 App → 主页 → 选 1 张竖长图（如 500x1500 像素）的题目 → 复习
2. 图片区域高度自适应（不超 2/3 屏），宽度按比例缩，整图不裁剪
3. 点击图片 → 全屏黑色背景预览
4. 双指缩放：缩放范围 0.5x ~ 5x
5. 双击图片：1x ↔ 2.5x
6. 关闭按钮 / 物理返回 → 退出预览

期望：图片自适应大小不裁剪，全屏预览各种手势工作。

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
git commit -m "feat(review): replace single image with AdaptiveImage + tap-to-zoom"
```

---

## Phase 4 — 列表页（功能 4 + 功能 6）

### Task 13: HomeViewModel — selectedQuestionTypes 状态

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/HomeViewModel.kt`

- [ ] **Step 1: HomeUiState 新增字段**

找到 `data class HomeUiState`（约 36-45 行），添加：

```kotlin
val selectedQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet()
```

完整 HomeUiState：

```kotlin
data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val todayCards: List<TodayCardInfo> = emptyList(),
    val overdueCards: List<OverdueCardInfo> = emptyList(),
    val cardSubjectIds: Set<Long> = emptySet(),
    val totalMistakes: Int = 0,
    val masteredCount: Int = 0,
    val isLoading: Boolean = true,
    val selectedQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet()
)
```

- [ ] **Step 2: 新增 selectQuestionTypes 方法 + 缓存 filtered types**

在 `selectSubject` 之后添加：

```kotlin
fun selectQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
    _uiState.update { it.copy(selectedQuestionTypes = types) }
    emitFilteredState()
}
```

- [ ] **Step 3: emitFilteredState 应用题型过滤**

找到 `emitFilteredState()` 方法（约 155-167 行），把过滤逻辑改造：

```kotlin
private fun emitFilteredState() {
    val sel = _uiState.value.currentSubjectId
    val typeSel = _uiState.value.selectedQuestionTypes
    fun typeOk(qt: com.mistakenotes.domain.model.QuestionType) =
        typeSel.isEmpty() || qt in typeSel

    _uiState.value = HomeUiState(
        subjects = cachedSubjects,
        currentSubjectId = sel,
        todayCards = allTodayCards
            .filter { sel == null || it.mistake.subjectId == sel }
            .filter { typeOk(it.mistake.questionType) },
        overdueCards = allOverdueCards
            .filter { sel == null || it.mistake.subjectId == sel }
            .filter { typeOk(it.mistake.questionType) },
        cardSubjectIds = cachedCardSubjIds,
        totalMistakes = cachedTotalMistakes,
        masteredCount = cachedMastered,
        isLoading = false,
        selectedQuestionTypes = typeSel
    )
}
```

- [ ] **Step 4: Sync 验证编译通过**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/HomeViewModel.kt
git commit -m "feat(home): add selectedQuestionTypes state + filter logic"
```

---

### Task 14: HomeScreen — 题型筛选 chip

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/HomeScreen.kt`

- [ ] **Step 1: 在"今日待复习"标题后插入 chip**

找到 `SectionHeader(title = "今日待复习", ...)` 块，在它后面（item 块内）的下一行前加一个 item：

```kotlin
item {
    QuestionTypeFilter(
        selected = uiState.selectedQuestionTypes,
        onSelectionChange = { viewModel.selectQuestionTypes(it) }
    )
}
```

- [ ] **Step 2: 在"逾期"标题后插入 chip**

同样，找到 `SectionHeader(title = "逾期", ...)`，在它后面加：

```kotlin
item {
    QuestionTypeFilter(
        selected = uiState.selectedQuestionTypes,
        onSelectionChange = { viewModel.selectQuestionTypes(it) }
    )
}
```

- [ ] **Step 3: 添加 import**

```kotlin
import com.mistakenotes.ui.components.QuestionTypeFilter
```

- [ ] **Step 4: Sync 验证编译通过**

- [ ] **Step 5: 真机验证**

1. 主页 → 看到"今日待复习"标题下方有 3 个 chip（单选/多选/主观题）
2. 默认 3 个全选（高亮）
3. 点击"主观题" → 该 chip 取消高亮
4. 列表中所有主观题消失
5. 再点击"主观题" → 恢复显示
6. 同时"逾期"段的 chip 与"今日"独立操作（共享同一 selectedQuestionTypes state）

期望：chip 切换列表过滤生效，与科目 chip 是 AND 关系。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/HomeScreen.kt
git commit -m "feat(home): QuestionTypeFilter in today/overdue sections"
```

---

### Task 15: BrowseViewModel — selectedQuestionTypes + reschedule

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/BrowseViewModel.kt`

- [ ] **Step 1: BrowseUiState 新增字段**

找到 `data class BrowseUiState`（约 26-35 行），添加：

```kotlin
val selectedQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet()
```

- [ ] **Step 2: 新增 selectQuestionTypes 方法**

在 `selectChapter` 之后添加：

```kotlin
fun selectQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
    _uiState.update { it.copy(selectedQuestionTypes = types) }
    buildBrowseItems()
}
```

- [ ] **Step 3: buildBrowseItems 增加 type 过滤**

找到 `buildBrowseItems()` 内的 `val filtered = allMistakes.filter { ... }` 块（约 109-118 行），在 `matchSubject && matchChapter && isMastered` 后追加：

```kotlin
val matchType = state.selectedQuestionTypes.isEmpty() ||
    mistake.questionType in state.selectedQuestionTypes
matchSubject && matchChapter && isMastered && matchType
```

- [ ] **Step 4: 新增 reschedule 方法**

在 `toggleTop` 之后添加：

```kotlin
fun reschedule(mistakeId: Long, days: Int) {
    viewModelScope.launch {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val target = cal.timeInMillis + days * 86400000L
        repository.insertReviewRecord(
            com.mistakenotes.domain.model.ReviewRecord(
                id = 0,
                mistakeId = mistakeId,
                reviewDate = System.currentTimeMillis(),
                result = com.mistakenotes.domain.model.ReviewResult.SKIP,
                nextReviewDate = target,
                correctCount = 0
            )
        )
    }
}
```

- [ ] **Step 5: Sync 验证编译通过**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/BrowseViewModel.kt
git commit -m "feat(browse): add selectedQuestionTypes filter + reschedule method"
```

---

### Task 16: BrowseScreen — 筛选 chip + 已掌握模式复习按钮

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/BrowseScreen.kt`

- [ ] **Step 1: 在章节下拉后插入筛选 chip**

找到 `if (uiState.chapters.isNotEmpty()) { ChapterDropdown(...) }`，在它之后、`HorizontalDivider(color = CardDark)` 之前加：

```kotlin
QuestionTypeFilter(
    selected = uiState.selectedQuestionTypes,
    onSelectionChange = { viewModel.selectQuestionTypes(it) }
)
```

- [ ] **Step 2: BrowseCard 增加 onReschedule 参数**

找到 `private fun BrowseCard(...)` 签名，把 `onToggleTop: () -> Unit` 后添加：

```kotlin
onReschedule: (() -> Unit)? = null
```

- [ ] **Step 3: 已掌握模式显示 🕐 按钮**

在 BrowseCard 函数内、置顶 IconButton 之前添加：

```kotlin
if (isMasteredMode && onReschedule != null) {
    IconButton(
        onClick = onReschedule,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "重新安排复习",
            tint = AmberGold,
            modifier = Modifier.size(20.dp)
        )
    }
}
```

- [ ] **Step 4: 调用 BrowseCard 时传 onReschedule**

在 BrowseScreen 主体的 `BrowseCard(...)` 调用处（约 140-153 行），找到 `onToggleTop = { viewModel.toggleTop(item.mistake) }` 后添加：

```kotlin
onReschedule = if (uiState.isMasteredMode) {
    { rescheduleTarget = item.mistake.id }
} else null,
```

- [ ] **Step 5: 添加 rescheduleTarget 状态 + RescheduleDialog**

在 BrowseScreen composable 顶部 `val uiState by viewModel.uiState.collectAsState()` 后加：

```kotlin
var rescheduleTarget by remember { mutableStateOf<Long?>(null) }
```

在 BrowseScreen composable 末尾（最后 `}` 之前）加：

```kotlin
rescheduleTarget?.let { mistakeId ->
    RescheduleDialog(
        initialDays = 7,
        onConfirm = { days ->
            viewModel.reschedule(mistakeId, days)
            rescheduleTarget = null
        },
        onDismiss = { rescheduleTarget = null }
    )
}
```

- [ ] **Step 6: 添加 imports**

```kotlin
import com.mistakenotes.ui.components.QuestionTypeFilter
import com.mistakenotes.ui.components.RescheduleDialog
import androidx.compose.material.icons.filled.Schedule
```

- [ ] **Step 7: Sync 验证编译通过**

- [ ] **Step 8: 真机验证**

1. 主页 → 错题浏览 → 看到章节下方 3 个 chip
2. 取消"主观题" → 主观题消失
3. 主页 → 已掌握 → 看到每张卡片有 🕐 按钮
4. 点击 → 弹窗显示 7 天（默认），可调 1-10
5. 选 5 天 → 确认
6. 卡片立即从已掌握列表消失
7. （修改系统时间 +5 天 或 直接看逾期/今日）→ 该题出现在 5 天后的今日/逾期

期望：所有交互正常。

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/BrowseScreen.kt
git commit -m "feat(browse): QuestionTypeFilter + reschedule button in mastered mode"
```

---

## Phase 5 — 文档 + 最终验证

### Task 17: 更新 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 在"注意事项"末尾追加"数据保护（强制）"段落**

找到 "## 待开发功能" 标题。在它之前的所有"注意事项"项目结束后追加：

```markdown
- **数据保护（强制）**：所有新功能必须**复用现有字段**，不得修改数据库 schema；不得引入 `fallbackToDestructiveMigration()` 触发的变更；如确需新增字段，必须写 Migration 6→N（明确迁移步骤）并保持 `fallbackToDestructiveMigration()` 不生效。新功能 PR 必须在 Android 真机覆盖安装后验证数据不丢失。
```

- [ ] **Step 2: 从"待开发功能"表中移除已完成项**

找到表格中"**高** | 知识点管理"和"**中** | 搜索题目"，保留它们（尚未实现）。

找到可能已经完成的功能（OCR 识别、AI 评分、AI 得分点等）—— 这些是 v2 未做，保留。

> 实际上 7 个新功能未列入"待开发"表，不需删除。但要确认以下条目可以删除（如有）：
> - 没有可以直接删除的，因为 CLAUDE.md 中待开发表只有 v2 OCR/AI 之类。

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add data-protection principle (mandatory for all new features)"
```

---

### Task 18: 真机全量回归测试

**Files:** 无（验证任务）

- [ ] **Step 1: 真机覆盖安装**

操作：Android Studio → 设备连接 → Run 'app'。

期望：覆盖安装成功，不报错。**关键**：覆盖安装后打开 App，所有旧错题/复习记录/收藏/置顶数据应保留。

- [ ] **Step 2: 全功能冒烟测试**

按以下顺序验证：

**录入**：
- [ ] 录入单选题 + 答案图片 + 选 5-25 录入时间 → 保存
- [ ] 录入多选题 + 答案图片 → 保存
- [ ] 录入主观题（答案图片已能上传）→ 保存
- [ ] 编辑现有错题 → 录入时间默认 = 原 createdAt

**复习**：
- [ ] 进入今日复习（至少 3 题）
- [ ] 顶栏 "1/3" 可点击 → 弹题号网格
- [ ] 跳到第 1 题显示已复习结果
- [ ] 答第 2 题 → 提交 → 顶栏出现 ✓ 按钮
- [ ] 点击"已掌握" → 确认 → 回主页题目仍显示"✓ 正确"
- [ ] （改时间到明天）→ 题目出现在已掌握列表
- [ ] 题目图片：竖长图不裁剪，点击全屏预览，双指缩放/双击放大

**主页**：
- [ ] 题型筛选 chip 工作
- [ ] 与科目 chip 联用

**错题浏览**：
- [ ] 章节下方的题型 chip 工作
- [ ] 已掌握模式卡片有 🕐 按钮
- [ ] 弹窗 1-10 天，默认 7，确认后卡片消失

**数据保护**：
- [ ] 覆盖安装前后的错题数量、复习记录数、收藏数、置顶数一致
- [ ] 录入时间选 5-25 → 5-30 出现在今日 / 6-1 出现在逾期
- [ ] 已掌握题目被点错 → 归 0 回到复习循环

- [ ] **Step 3: 最终 commit (如有微调)**

```bash
git status
# 如有未提交的修改
git add -A
git commit -m "chore: post-implementation cleanup"
```

- [ ] **Step 4: 推送**

```bash
git push origin main
```

---

## 总结

| 阶段 | 任务数 | 新增文件 | 修改文件 |
|------|--------|----------|----------|
| Phase 1 组件 | 7 | 7 | 0 |
| Phase 2 录入 | 2 | 0 | 2 |
| Phase 3 复习 | 3 | 0 | 2 |
| Phase 4 列表 | 4 | 0 | 3 |
| Phase 5 文档/验证 | 2 | 0 | 1 |
| **合计** | **18** | **7** | **9** |

**数据保护保证**：
- 零 Room schema 变更
- 数据库版本号保持 6
- `fallbackToDestructiveMigration()` 不触发
- 现有 MIGRATION_2_3 ~ MIGRATION_5_6 不受影响
- 用户数据 100% 保留
