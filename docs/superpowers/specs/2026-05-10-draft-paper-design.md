# 复习草稿纸功能设计

## 概述

在复习答题页面增加草稿纸功能，允许用户在答题过程中切换到手写草稿纸模式，方便计算和思考。草稿纸笔记在提交答案后清除。

## 功能描述

### 交互流程

1. 用户在答题页面点击"草稿纸"按钮
2. 右侧答题区切换为手写画布（与主页 HandwritingView 相同）
3. 用户可在草稿纸上书写、缩放、切换模式
4. 用户点击"返回答题"切回答题模式，笔记保留
5. 用户提交答案后，草稿纸笔记自动清除

### UI 布局

**答题模式**（默认）:
```
┌─────────────────┬─────────────────┐
│     题目区       │      答题区      │
│                 │  [A][B][C][D]   │
│                 │                 │
├─────────────────┴─────────────────┤
│  [提交答案]    [跳过]    [草稿纸]  │
└───────────────────────────────────┘
```

**草稿纸模式**:
```
┌─────────────────┬─────────────────┐
│     题目区       │     草稿纸       │
│                 │  (手写画布)      │
│                 │                 │
├─────────────────┴─────────────────┤
│     [返回答题]    [撤销] [清空]    │
└───────────────────────────────────┘
```

### 手写画布功能

与主页 HandwritingView 保持一致：
- **笔写模式/手写模式切换**
- **双指缩放**（100%-500%）
- **纸张底色**：黑色 / 白色 / 肉色
- **纸张线型**：空白 / 网格 / 横线
- **撤销 / 清空**

## 实现方案

### 代码修改

#### 1. ReviewUiState (ReviewViewModel.kt)
新增字段：
```kotlin
data class ReviewUiState(
    // ... 现有字段
    val isDraftMode: Boolean = false  // 新增：是否在草稿纸模式
)
```

#### 2. ReviewViewModel
新增方法：
```kotlin
fun toggleDraftMode() {
    _uiState.value = _uiState.value.copy(isDraftMode = !_uiState.value.isDraftMode)
}

fun clearDraft() {
    // 清除草稿纸内容（在提交答案时调用）
}
```

#### 3. QuestionContent 参数调整
```kotlin
@Composable
fun QuestionContent(
    // ... 现有参数
    val isDraftMode: Boolean,           // 是否草稿纸模式
    val onToggleDraftMode: () -> Unit,  // 切换到草稿纸
    val onClearDraft: () -> Unit,       // 清除草稿
    val onViewRefReady: (HandwritingView) -> Unit,  // 草稿纸 View 回调
    val draftViewRef: HandwritingView?  // 草稿纸 View 引用
)
```

#### 4. 布局调整

**答题模式按钮区**:
```kotlin
if (!showResult) {
    Row(...) {
        Button(onClick = onSubmit, ...) { Text("提交答案") }
        OutlinedButton(onClick = onSkip, ...) { Text("跳过") }
        OutlinedButton(onClick = onToggleDraftMode, ...) { Text("草稿纸") }  // 新增
    }
}
```

**草稿纸模式按钮区**:
```kotlin
Row(...) {
    Button(onClick = onToggleDraftMode, ...) { Text("返回答题") }
    OutlinedButton(onClick = onUndoDraft, ...) { Text("撤销") }
    OutlinedButton(onClick = onClearDraft, ...) { Text("清空") }
}
```

#### 5. 草稿纸 HandwritingView 管理
- `ReviewScreen` 中维护 `var draftHandwritingView by remember { mutableStateOf<HandwritingView?>(null) }`
- 切换模式时 `HandwritingView` 实例不销毁，内容保留
- 提交答案后调用 `draftHandwritingView?.clear()`

## 文件清单

- `app/src/main/java/com/mistakenotes/ui/screens/ReviewViewModel.kt` - 新增 isDraftMode 状态和切换方法
- `app/src/main/java/com/mistakenotes/ui/screens/ReviewScreen.kt` - UI 调整，添加草稿纸按钮和布局

## 测试用例

1. 点击"草稿纸"按钮，右侧切换为手写画布，左侧题目不变
2. 在草稿纸上书写后点击"返回答题"，笔记保留
3. 再次点击"草稿纸"，之前写的笔记仍在
4. 点击"撤销"撤销上一笔
5. 点击"清空"清除所有笔记
6. 提交答案后，草稿纸笔记自动清除
7. 缩放、切换底色、切换线型功能正常
8. 笔写/手写模式切换正常