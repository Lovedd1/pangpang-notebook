# Multi-Image Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support multiple question images and answer images per mistake, stored via `||` delimiter, with horizontal pager UI in review.

**Architecture:** No database changes. Multiple image paths stored in existing `questionImagePath`/`referenceAnswer` fields with `||` separator. ImportScreen gets horizontal scrollable image row. ReviewScreen uses HorizontalPager for essay question images and answer images independently.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2025.02.00), HorizontalPager from `foundation.pager`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `domain/model/Mistake.kt` | Modify | Add `getQuestionImagePaths()` / `getAnswerImagePaths()` extension functions |
| `ui/screens/ImportViewModel.kt` | Modify | Change `imageUri: Uri?` → `imageUris: List<Uri>`, same for answer; multi-image save/load logic |
| `ui/screens/ImportScreen.kt` | Modify | Replace single image card with horizontal scrollable multi-image row |
| `ui/screens/ReviewScreen.kt` | Modify | Replace single AsyncImage with `HorizontalPager` + page indicator for essay questions |

---

### Task 1: Add image path helper extensions to Mistake.kt

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/domain/model/Mistake.kt`

- [ ] **Step 1: Add extension functions**

```kotlin
package com.mistakenotes.domain.model

enum class QuestionType {
    SINGLE_CHOICE,
    MULTI_CHOICE,
    ESSAY
}

data class Mistake(
    val id: Long = 0,
    val title: String = "",
    val subjectId: Long,
    val chapterId: Long,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val questionImagePath: String? = null,
    val questionText: String? = null,
    val options: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val referenceAnswer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isTop: Boolean = false
)

fun Mistake.getQuestionImagePaths(): List<String> =
    questionImagePath?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

fun Mistake.getAnswerImagePaths(): List<String> =
    referenceAnswer?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mistakenotes/domain/model/Mistake.kt
git commit -m "feat: add getQuestionImagePaths/getAnswerImagePaths extensions"
```

---

### Task 2: Update ImportViewModel for multi-image lists

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

- [ ] **Step 1: Change ImportUiState — replace single Uri fields with lists**

Replace `imageUri: Uri?` and `answerImageUri: Uri?` with list fields:

```kotlin
data class ImportUiState(
    val imageUris: List<Uri> = emptyList(),
    val answerImageUris: List<Uri> = emptyList(),
    val title: String = "",
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val optionEntries: List<String> = listOf("", "", "", ""),
    val correctOptionIndices: Set<Int> = emptySet(),
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false
)
```

- [ ] **Step 2: Replace setImageUri/setAnswerImageUri with list operations**

```kotlin
fun addImageUri(uri: Uri) {
    _uiState.update { it.copy(imageUris = it.imageUris + uri) }
}

fun removeImageUri(index: Int) {
    _uiState.update {
        it.copy(imageUris = it.imageUris.filterIndexed { i, _ -> i != index })
    }
}

fun addAnswerImageUri(uri: Uri) {
    _uiState.update { it.copy(answerImageUris = it.answerImageUris + uri) }
}

fun removeAnswerImageUri(index: Int) {
    _uiState.update {
        it.copy(answerImageUris = it.answerImageUris.filterIndexed { i, _ -> i != index })
    }
}
```

- [ ] **Step 3: Update loadMistakeForEditing — parse multi-image paths**

Replace the single `imageUri`/`answerImageUri` assignments:

```kotlin
// Inside loadMistakeForEditing, replace the imageUri/answerImageUri lines:
val questionImagePaths = mistake.getQuestionImagePaths()
val answerImagePaths = mistake.getAnswerImagePaths()

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
        imageUris = questionImagePaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) Uri.fromFile(file) else null
        },
        answerImageUris = answerImagePaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) Uri.fromFile(file) else null
        },
        errorMessage = null,
        isEditMode = true
    )
}
```

Note: Add `import java.io.File` if not already present (it is).

- [ ] **Step 4: Update saveMistake — multi-image copy and join**

Replace the `localImagePath` / `localAnswerImagePath` logic:

```kotlin
// Replace the image copy section:
val localImagePaths = if (state.imageUris.isNotEmpty()) {
    state.imageUris.mapNotNull { copyImageToLocal(it) }
} else if (isEdit) {
    existingMistake?.getQuestionImagePaths() ?: emptyList()
} else {
    emptyList()
}

val localAnswerPaths = if (state.answerImageUris.isNotEmpty()) {
    state.answerImageUris.mapNotNull { copyImageToLocal(it, "answer") }
} else if (isEdit) {
    existingMistake?.getAnswerImagePaths() ?: emptyList()
} else {
    emptyList()
}

val questionImagePathStr = localImagePaths.joinToString("||").takeIf { it.isNotBlank() }
val answerImagePathStr = localAnswerPaths.joinToString("||").takeIf { it.isNotBlank() }
```

Then in the Mistake constructor:

```kotlin
val mistake = Mistake(
    id = if (isEdit) editingMistakeId else 0,
    title = finalTitle,
    subjectId = state.subjectId,
    chapterId = state.chapterId,
    knowledgePointId = state.knowledgePointId,
    questionType = state.questionType,
    questionImagePath = questionImagePathStr,
    questionText = state.questionText.takeIf { it.isNotBlank() },
    options = optionsStr,
    correctAnswer = correctAnswerStr,
    referenceAnswer = answerImagePathStr,
    createdAt = existingMistake?.createdAt ?: now,
    isFavorite = existingMistake?.isFavorite ?: false,
    isTop = existingMistake?.isTop ?: false
)
```

- [ ] **Step 5: Update deleteMistake — delete all image files**

Replace the single file deletion lines:

```kotlin
// Replace:
// mistake.questionImagePath?.let { File(it).delete() }
// mistake.referenceAnswer?.let { File(it).delete() }
// With:
mistake.getQuestionImagePaths().forEach { File(it).delete() }
mistake.getAnswerImagePaths().forEach { File(it).delete() }
```

- [ ] **Step 6: Update resetState**

Replace `ImportUiState(subjects = it.subjects)` for the new fields:

```kotlin
fun resetState() {
    _uiState.update {
        ImportUiState(subjects = it.subjects)
    }
}
```

(No change needed since the default values handle the new fields.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat: support multiple images in ImportViewModel"
```

---

### Task 3: Update ImportScreen for multi-image UI

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`

- [ ] **Step 1: Add necessary imports**

Add these at the top:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
```

(Many imports already exist — just add what's needed.)

- [ ] **Step 2: Replace the single image picker launcher**

Remove the old `imagePickerLauncher` and add a new one for multi-image:

```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let { viewModel.addImageUri(it) }
}
```

- [ ] **Step 3: Replace the image card section with MultiImageRow**

Replace the entire "Image card" section (lines 89-117 in original):

```kotlin
// Multi-image row
Text("题目图片", color = TextCream, style = MaterialTheme.typography.titleSmall)
MultiImageRow(
    images = uiState.imageUris,
    onAdd = { imagePickerLauncher.launch("image/*") },
    onRemove = { viewModel.removeImageUri(it) }
)
```

- [ ] **Step 4: Replace answer image card with MultiImageRow**

Replace the "答案图片" section (lines 185-225 in original). The `answerImageLauncher` block should change to:

```kotlin
if (uiState.questionType == QuestionType.ESSAY) {
    val answerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAnswerImageUri(it) }
    }

    Text("答案图片", color = TextCream, style = MaterialTheme.typography.titleSmall)
    MultiImageRow(
        images = uiState.answerImageUris,
        onAdd = { answerImageLauncher.launch("image/*") },
        onRemove = { viewModel.removeAnswerImageUri(it) }
    )
}
```

- [ ] **Step 5: Add the MultiImageRow composable at file bottom**

Add before the last closing brace of the file:

```kotlin
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
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "feat: add multi-image row UI in ImportScreen"
```

---

### Task 4: Update ReviewScreen with HorizontalPager for essay questions

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
```

- [ ] **Step 2: Replace question image block with MultiImagePager**

Replace lines 132-142 (the question image if-block):

```kotlin
// Question image (current code lines 132-142):
// if (!mistake.questionImagePath.isNullOrBlank()) {
//     AsyncImage(...)
//     Spacer(modifier = Modifier.height(12.dp))
// }

// Replace with:
val questionPaths = mistake.getQuestionImagePaths()
if (mistake.questionType == QuestionType.ESSAY && questionPaths.size > 1) {
    MultiImagePager(paths = questionPaths)
    Spacer(modifier = Modifier.height(12.dp))
} else if (questionPaths.isNotEmpty()) {
    AsyncImage(
        model = File(questionPaths.first()),
        contentDescription = "题目图片",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth
    )
    Spacer(modifier = Modifier.height(12.dp))
}
```

- [ ] **Step 3: Replace answer image block with MultiImagePager**

Replace lines 325-341 (the answer image `AnimatedVisibility` block):

```kotlin
// Answer image (current lines 325-341):
// AnimatedVisibility(visible = showAnswerImage && hasAnswer) {
//     Card(...) {
//         Column(...) {
//             Text("参考答案", ...)
//             AsyncImage(...)
//         }
//     }
// }

// Replace with:
val answerPaths = mistake.getAnswerImagePaths()
AnimatedVisibility(visible = showAnswerImage && answerPaths.isNotEmpty()) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("参考答案", color = AmberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (answerPaths.size > 1) {
                MultiImagePager(paths = answerPaths)
            } else if (answerPaths.isNotEmpty()) {
                AsyncImage(
                    model = File(answerPaths.first()),
                    contentDescription = "答案图片",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
```

- [ ] **Step 4: Add MultiImagePager composable at file bottom**

```kotlin
@Composable
private fun MultiImagePager(paths: List<String>) {
    val pagerState = rememberPagerState(pageCount = { paths.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            AsyncImage(
                model = File(paths[page]),
                contentDescription = "图片 ${page + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth
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

Also add import for `HorizontalPager` and `rememberPagerState`:

```kotlin
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt
git commit -m "feat: add HorizontalPager for multi-image essay review"
```

---
