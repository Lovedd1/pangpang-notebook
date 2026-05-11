# 复习草稿纸功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在复习答题页面增加草稿纸功能，用户可以切换到手写画布模式进行计算和思考，提交答案后草稿纸笔记自动清除。

**Architecture:** 新增 `isDraftMode` 状态控制右侧区域显示答题区还是草稿纸。草稿纸复用 `HandwritingView`，切换模式时 View 实例不销毁以保留笔记。提交答案后调用 `clear()` 清除。

**Tech Stack:** Kotlin + Jetpack Compose + Android View 互操作

---

## 文件清单

- 修改: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`
- 修改: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

---

## 任务分解

### 任务 1: ReviewViewModel 新增草稿纸状态

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt`

- [ ] **Step 1: 添加 isDraftMode 字段到 ReviewUiState**

在 `ReviewUiState` 的 `errorMessage: String? = null` 后面添加:
```kotlin
val isDraftMode: Boolean = false  // 是否在草稿纸模式
```

- [ ] **Step 2: 添加 toggleDraftMode 方法**

在 `skipTodayReview` 方法后面添加:
```kotlin
fun toggleDraftMode() {
    _uiState.value = _uiState.value.copy(isDraftMode = !_uiState.value.isDraftMode)
}
```

- [ ] **Step 3: 添加 clearDraft 方法**

```kotlin
fun clearDraft() {
    // 由 ReviewScreen 直接调用 HandwritingView.clear()
    // 此方法用于后续扩展或状态同步
}
```

- [ ] **Step 4: 提交答案后自动退出草稿纸模式**

在 `markAnswer` 方法中，提交后添加:
```kotlin
_uiState.value = state.copy(
    currentIndex = state.currentIndex + 1,
    selectedAnswers = emptySet(),
    showResult = false,
    isDraftMode = false  // 新增：提交后退出草稿纸模式
)
```

同样在 `skipQuestion` 方法的 state.copy 中添加 `isDraftMode = false`

---

### 任务 2: ReviewScreen 添加草稿纸按钮和布局

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt`

- [ ] **Step 1: 在 ReviewScreen 中添加 draftHandwritingView 状态**

在现有的 `var handwritingView by remember { mutableStateOf<HandwritingView?>(null) }` 后面添加:
```kotlin
var draftHandwritingView by remember { mutableStateOf<HandwritingView?>(null) }
```

- [ ] **Step 2: 传递草稿纸相关参数给 QuestionContent**

修改 `QuestionContent` 调用处，添加参数:
```kotlin
QuestionContent(
    question = currentMistake,
    currentIndex = uiState.currentIndex,
    totalCount = uiState.mistakes.size,
    selectedAnswers = uiState.selectedAnswers,
    showResult = uiState.showResult,
    isDraftMode = uiState.isDraftMode,
    onToggleDraftMode = { viewModel.toggleDraftMode() },
    onSubmit = { viewModel.submitAnswer() },
    onMarkCorrect = { viewModel.markAnswer(true) },
    onMarkWrong = { viewModel.markAnswer(false) },
    onSkip = { viewModel.skipQuestion() },
    onBack = {
        if (uiState.showResult) {
            viewModel.setShowResult(false)
        } else {
            currentPhase = "list"
        }
    },
    onViewRefReady = { handwritingView = it },
    onDraftViewRefReady = { draftHandwritingView = it }
)
```

- [ ] **Step 3: 修改 QuestionContent 函数签名**

在 `QuestionContent` 参数中添加:
```kotlin
@Composable
fun QuestionContent(
    question: Mistake,
    currentIndex: Int,
    totalCount: Int,
    selectedAnswers: Set<String>,
    showResult: Boolean,
    isDraftMode: Boolean,                    // 新增
    onToggleDraftMode: () -> Unit,           // 新增
    onSubmit: () -> Unit,
    onMarkCorrect: () -> Unit,
    onMarkWrong: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onViewRefReady: (HandwritingView) -> Unit,
    onDraftViewRefReady: (HandwritingView) -> Unit  // 新增
) {
```

- [ ] **Step 4: 修改右侧区域布局，根据 isDraftMode 显示不同内容**

将右侧 Surface 中的内容改为:
```kotlin
Surface(
    modifier = Modifier.weight(1f).fillMaxHeight(),
    color = InkStoneSurface,
    shape = RoundedCornerShape(16.dp)
) {
    if (isDraftMode) {
        // 草稿纸模式
        DraftModeContent(
            onToggleDraftMode = onToggleDraftMode,
            onDraftViewRefReady = onDraftViewRefReady
        )
    } else {
        // 答题模式
        AnswerModeContent(
            question = question,
            selectedAnswers = selectedAnswers,
            showResult = showResult,
            onToggleAnswer = onToggleAnswer,
            handwritingView = handwritingView,
            onViewRefReady = onViewRefReady
        )
    }
}
```

- [ ] **Step 5: 新增 DraftModeContent Composable**

在 `QuestionContent` 函数外面（同级）添加:
```kotlin
@Composable
fun DraftModeContent(
    onToggleDraftMode: () -> Unit,
    onDraftViewRefReady: (HandwritingView) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "草稿纸", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(InkStoneBg, RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    HandwritingView(context).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                        fingerColor = android.graphics.Color.parseColor("#E8E4DC")
                        fingerStrokeWidth = 4f
                    }.also { onDraftViewRefReady(it) }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onToggleDraftMode,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = InkStoneAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("返回答题", color = InkStoneBg)
            }
        }
    }
}
```

- [ ] **Step 6: 新增 AnswerModeContent Composable（将原有答题区逻辑提取）**

在 `QuestionContent` 函数外面添加:
```kotlin
@Composable
fun AnswerModeContent(
    question: Mistake,
    selectedAnswers: Set<String>,
    showResult: Boolean,
    onToggleAnswer: (String) -> Unit,
    handwritingView: HandwritingView?,
    onViewRefReady: (HandwritingView) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "你的作答", color = InkStoneTextDim, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        val isChoice = question.questionType == QuestionType.SINGLE_CHOICE || question.questionType == QuestionType.MULTI_CHOICE
        if (isChoice) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("A", "B", "C", "D").forEach { option ->
                    val isSelected = selectedAnswers.contains(option)
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onToggleAnswer(option)
                        },
                        color = if (isSelected) InkStoneAccent else InkStoneBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = option, color = if (isSelected) InkStoneBg else InkStoneText, fontSize = 16.sp)
                            if (showResult) {
                                Spacer(modifier = Modifier.width(12.dp))
                                val correctSet = question.correctAnswer.split(",").toSet()
                                val isCorrect = correctSet.contains(option)
                                Icon(
                                    imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (isCorrect) InkStoneSuccess else InkStoneError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(InkStoneBg, RoundedCornerShape(12.dp))
            ) {
                handwritingView?.let { view ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { view }
                    )
                } ?: AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        HandwritingView(context).apply {
                            setBackgroundColor(android.graphics.Color.parseColor("#242424"))
                            fingerColor = android.graphics.Color.parseColor("#E8E4DC")
                            fingerStrokeWidth = 4f
                        }.also { onViewRefReady(it) }
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 7: 修改按钮区布局，添加草稿纸按钮**

将原来的按钮区改为（根据 isDraftMode 显示不同按钮）:
```kotlin
if (!showResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isDraftMode) {
            // 草稿纸模式按钮已在 DraftModeContent 中
        } else {
            // 答题模式按钮
            if (isChoice) {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = if (question.questionType == QuestionType.MULTI_CHOICE) selectedAnswers.size >= 2 else selectedAnswers.isNotEmpty(),
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
            OutlinedButton(
                onClick = onToggleDraftMode,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkStoneAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("草稿纸")
            }
        }
    }
} else {
    // showResult = true 时的按钮（做对了/做错了）
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
```

注意：需要在文件顶部 import `Icons.Default.Edit`

- [ ] **Step 8: 提交答案时清除草稿纸**

在 `onMarkCorrect` 和 `onMarkWrong` 调用前清除草稿纸。由于 `onToggleDraftMode` 在 `viewModel.toggleDraftMode()` 中只是切换模式，清除需要单独处理。

可以在 `ReviewViewModel` 中添加 `clearDraftOnSubmit()` 方法，或在 `ReviewScreen` 中直接调用 `draftHandwritingView?.clear()`。

最简单的方式是在 `markAnswer` 被调用前，在 `ReviewScreen` 的 `onMarkCorrect` 和 `onMarkWrong` 回调中先清除草稿纸。修改 Step 2 中的调用:
```kotlin
onMarkCorrect = {
    draftHandwritingView?.clear()
    viewModel.markAnswer(true)
},
onMarkWrong = {
    draftHandwritingView?.clear()
    viewModel.markAnswer(false)
},
```

---

## 验证清单

- [ ] 点击"草稿纸"按钮，右侧切换为手写画布，左侧题目不变
- [ ] 在草稿纸上书写后点击"返回答题"，笔记保留
- [ ] 再次点击"草稿纸"，之前写的笔记仍在
- [ ] 提交答案后，草稿纸笔记自动清除
- [ ] 缩放、切换底色、切换线型功能正常
- [ ] 笔写/手写模式切换正常