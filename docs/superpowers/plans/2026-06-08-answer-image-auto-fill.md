# 答案图片自动识别 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ImportScreen` 上传答案图片时，本地 OCR 提取字母 A-H，根据数量自动设置题目类型（单选/多选/主观题）并勾选对应选项。零 Room schema 变更，老数据 0 风险。

**Architecture:** 新增一个无状态 `AnswerLetterExtractor` 纯函数（TDD 单测覆盖），在 `ImportViewModel` 内增加 `applyAnswerInference` 状态写入逻辑 + 异步 OCR 协程。OCR 复用现有 `OcrEngine`（ML Kit 本地）。`ImportScreen` 仅加 1 个 `LaunchedEffect` snackbar。`addAnswerImageUri` 首张触发 OCR + 写入 `preInferenceSnapshot`，`removeAnswerImageUri` 列表变空时按 snapshot 回滚。

**Tech Stack:** Kotlin 2.0 + Jetpack Compose、ML Kit Text Recognition（端侧 OCR，已存在）、JUnit 4（已存在）。**项目无 gradlew，编译/单测通过 Android Studio 真机调试**（Run 按钮覆盖安装 + View > Tool Windows > Logcat 看 toast 日志）。

**Spec:** `docs/superpowers/specs/2026-06-08-answer-image-auto-fill-design.md`

---

## File Structure

### 新增（2 个文件）
| 文件 | 职责 |
|------|------|
| `app/src/main/java/com/mistakenotes/ui/screens/AnswerLetterExtractor.kt` | 纯函数：OCR 文字 → 字母 A-H 列表 |
| `app/src/test/java/com/mistakenotes/ui/screens/AnswerLetterExtractorTest.kt` | JUnit 4 单测覆盖 10 个边界场景 |

### 修改（2 个文件）
| 文件 | 变更 |
|------|------|
| `ui/screens/ImportViewModel.kt` | UiState 加 3 字段；构造注入 OcrEngine；加 3 个私有方法 + 改 2 个公有方法 |
| `ui/screens/ImportScreen.kt` | 加 1 个 `LaunchedEffect` snackbar 弹窗 |

### 不修改
- 全部 Room Entity / Dao / AppDatabase / Repository（schema 0 改动）
- `OcrEngine.kt` / `KnowledgeClassifier` / `KnowledgeBase` / 任何 Migration
- 其他 Screen（Home/Review/Browse/Analysis/Settings/NavGraph）

---

## Task 1: 纯函数 AnswerLetterExtractor + 单测（TDD）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/AnswerLetterExtractor.kt`
- Create: `app/src/test/java/com/mistakenotes/ui/screens/AnswerLetterExtractorTest.kt`

- [ ] **Step 1: 写失败的单测**

`app/src/test/java/com/mistakenotes/ui/screens/AnswerLetterExtractorTest.kt`：

```kotlin
package com.mistakenotes.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerLetterExtractorTest {

    @Test
    fun `single letter after answer keyword`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：A"))
    }

    @Test
    fun `multiple letters after answer keyword`() {
        assertEquals(listOf('A', 'B'), AnswerLetterExtractor.extract("答案：AB"))
    }

    @Test
    fun `four letters in correct order`() {
        assertEquals(
            listOf('A', 'B', 'C', 'D'),
            AnswerLetterExtractor.extract("正确答案：ABCD")
        )
    }

    @Test
    fun `no letters returns empty list`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("略"))
    }

    @Test
    fun `letters inside other words are extracted (AB型)`() {
        // AB 作为一个 token，按字符拆开
        assertEquals(listOf('A', 'B'), AnswerLetterExtractor.extract("AB型"))
    }

    @Test
    fun `answer segment in second sentence is used`() {
        assertEquals(
            listOf('A'),
            AnswerLetterExtractor.extract("解析：详见解析。答案：A")
        )
    }

    @Test
    fun `digits are ignored`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("答案：1+1=2"))
    }

    @Test
    fun `lowercase letters are ignored`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("answer: a"))
    }

    @Test
    fun `duplicate letters are deduplicated`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：AA"))
    }

    @Test
    fun `empty string returns empty list`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract(""))
    }
}
```

- [ ] **Step 2: 在 Android Studio 运行单测确认失败**

操作：Android Studio → 右键 `AnswerLetterExtractorTest.kt` → Run 'AnswerLetterExtractorTest'

Expected: 编译失败 / 测试报错 `Unresolved reference: AnswerLetterExtractor`（因为还没有这个类）

- [ ] **Step 3: 实现 AnswerLetterExtractor 纯函数**

`app/src/main/java/com/mistakenotes/ui/screens/AnswerLetterExtractor.kt`：

```kotlin
package com.mistakenotes.ui.screens

/**
 * 从 OCR 识别的答案图片文字中，提取 A-H 的答案字母。
 *
 * 提取策略：
 * 1) 按句号/分号/换行切成多段，优先找包含"答案/正确答案"关键词的段
 * 2) 在该段中按非字母字符切分 token，把每个 token 拆成单字符，过滤出 A-H
 * 3) 找不到"答案"关键词时，fallback 到全文字母
 * 4) distinct() 去重，保持出现顺序
 *
 * 设计 spec: docs/superpowers/specs/2026-06-08-answer-image-auto-fill-design.md §3.2
 */
object AnswerLetterExtractor {

    private val SEGMENT_DELIMITER = Regex("[。\n;；]")
    private val ANSWER_KEYWORD = Regex("正确答案|答\\s*案|答案|答[是为：:]")
    private val NON_LETTER = Regex("[^A-Za-z]+")

    fun extract(text: String): List<Char> {
        if (text.isBlank()) return emptyList()

        // 1) 找"答案"关键词所在的段
        val segments = text.split(SEGMENT_DELIMITER)
        val answerSegment = segments.firstOrNull { it.contains(ANSWER_KEYWORD) }
            ?: text  // fallback

        // 2) 在该段中按非字母切分，拆字符，过滤 A-H
        return answerSegment.split(NON_LETTER)
            .flatMap { token -> token.toList() }
            .filter { it in 'A'..'H' }
            .distinct()
    }
}
```

- [ ] **Step 4: 重新运行单测确认全部通过**

操作：Android Studio → 右键 `AnswerLetterExtractorTest.kt` → Run 'AnswerLetterExtractorTest'

Expected: 10 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/AnswerLetterExtractor.kt \
        app/src/test/java/com/mistakenotes/ui/screens/AnswerLetterExtractorTest.kt
git commit -m "feat(import): 答案图 OCR 字母提取纯函数 + 单测"
```

---

## Task 2: 扩展 ImportUiState + 加 clearAnswerOcrFeedback()

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt:35-58`（ImportUiState data class 字段）
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt:515-521`（在 `clearRagError` 后面加新方法）

- [ ] **Step 1: 在 ImportUiState 加 3 个新字段**

`ImportViewModel.kt` 第 55-57 行（RAG 状态字段之后）追加：

```kotlin
    // ====== 新增 答案图 OCR 推断状态 ======
    val answerOcrFeedback: String? = null,          // 单次 OCR 反馈文案，LaunchedEffect 消费后置 null
    val answerInferredFromOcr: Boolean = false,     // 是否处于"OCR 推断态"（决定删图时是否回滚）
    val preInferenceSnapshot: AnswerSnapshot? = null // OCR 前的状态快照，用于删图回滚
```

- [ ] **Step 2: 在 ImportUiState 之后（data class 闭合后）加 AnswerSnapshot data class**

紧跟 `ImportUiState` 的右大括号后、`@HiltViewModel` 之前，插入：

```kotlin

/**
 * 答案图 OCR 推断前的状态快照，用于"删图回滚"
 */
data class AnswerSnapshot(
    val questionType: QuestionType,
    val correctOptionIndices: Set<Int>
)
```

- [ ] **Step 3: 加 clearAnswerOcrFeedback() 公有方法**

在第 519-521 行 `clearRagError()` 之后追加：

```kotlin
    fun clearAnswerOcrFeedback() {
        _uiState.update { it.copy(answerOcrFeedback = null) }
    }
```

- [ ] **Step 4: 在 Android Studio 编译检查（Build > Make Project）**

操作：菜单 Build → Make Project

Expected: BUILD SUCCESSFUL，无错误（仅加字段和空方法不影响行为）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): UiState 加 answerOcr 3 字段 + clearAnswerOcrFeedback"
```

---

## Task 3: ImportViewModel 构造注入 OcrEngine

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt:60-67`（构造器签名）

- [ ] **Step 1: 在构造器参数列表中加 OcrEngine**

把第 60-67 行：

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,  // ← 新增
    private val knowledgeBase: KnowledgeBase,  // ← 新增
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

改为：

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,  // RAG 分类器
    private val knowledgeBase: KnowledgeBase,      // RAG 知识库
    private val ocrEngine: OcrEngine,              // 答案图 OCR 用（复用 RAG 的 ML Kit 引擎）
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

- [ ] **Step 2: 在 Hilt 模块中确认 OcrEngine 已被 Hilt 单例化**

读 `app/src/main/java/com/mistakenotes/di/ClassifierModule.kt`，确认其中 `provideOcrEngine()` 或 `@Provides @Singleton fun ocrEngine(...)` 存在。`DeepSeekKnowledgeClassifier` 已经在用 `ocr` 字段（即 OcrEngine 实例）通过 `@Inject constructor(private val ocr: OcrEngine, ...)`，所以 Hilt 单例化已就绪——**不需要修改 di 模块**。

如果 `OcrEngine` 类本身没有 `@Inject constructor()` 或 `@Singleton`，则需要在 `app/src/main/java/com/mistakenotes/data/rag/OcrEngine.kt` 加：

```kotlin
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) { ... }
```

操作：读 `OcrEngine.kt` 检查类签名

Expected: 已经有 `@Inject constructor()` 和 `@Singleton` 标注（因为 `DeepSeekKnowledgeClassifier` 注入成功）。如果是，补上；否则跳过本步

- [ ] **Step 3: 在 Android Studio 编译检查**

操作：菜单 Build → Make Project

Expected: BUILD SUCCESSFUL（Hilt 注入链路通；如果失败检查 OcrEngine 类签名）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): 构造器注入 OcrEngine 用于答案图 OCR"
```

---

## Task 4: 实现 applyAnswerInference 私有方法

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`（在 `_pendingAnswerOcrJob` 字段之后、init 块之前插入新方法）

- [ ] **Step 1: 加 LABELS 常量到 ImportViewModel 内部**

在 `_pendingClassifyJob` 字段声明附近（73 行附近）后追加：

```kotlin
    private val _pendingAnswerOcrJob = MutableStateFlow<Job?>(null)

    /** 选项字母 A~H，与 OptionEntryRow 的 LABELS 对齐 */
    private companion object {
        private val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    }
```

如果文件已有其他 `companion object`，把 `LABELS` 加进那个；否则新建。

- [ ] **Step 2: 加 applyAnswerInference 私有方法**

在 `triggerRagClassification` 方法（143-204 行）之后、`removeImageUri` 方法（206 行）之前，插入：

```kotlin
    /**
     * 把 OCR 提取的字母列表应用为 questionType + correctOptionIndices
     * 守护：用户可能已经在 OCR 期间删除/替换了第一张答案图
     */
    private fun applyAnswerInference(uri: Uri, letters: List<Char>, rawText: String) {
        val state = _uiState.value
        // 守护：用户已经删了或换了第一张图
        if (state.answerImageUris.firstOrNull() != uri) return

        val (newType, newCorrect, feedback) = when {
            letters.isEmpty() -> Triple(
                QuestionType.ESSAY,
                emptySet<Int>(),
                "未识别到答案字母，标记为主观题"
            )
            letters.size == 1 -> {
                val idx = LABELS.indexOf(letters[0].toString())
                if (idx < 0) {
                    Triple(QuestionType.ESSAY, emptySet(), "识别异常：${letters[0]}")
                } else {
                    Triple(
                        QuestionType.SINGLE_CHOICE,
                        setOf(idx),
                        "已识别为单选题，答案：${letters[0]}"
                    )
                }
            }
            else -> {
                val validIndices = letters.mapNotNull { c ->
                    LABELS.indexOf(c.toString()).takeIf { it >= 0 }
                }
                val validLetters = validIndices.map { LABELS[it][0] }
                val dropped = letters.filter { it !in validLetters }
                val msg = buildString {
                    append("已识别为多选题，答案：")
                    append(validLetters.joinToString("、"))
                    if (dropped.isNotEmpty()) {
                        append("（")
                        append(dropped.joinToString("、"))
                        append(" 超出选项范围已忽略）")
                    }
                }
                Triple(QuestionType.MULTI_CHOICE, validIndices.toSet(), msg)
            }
        }

        _uiState.update {
            it.copy(
                questionType = newType,
                correctOptionIndices = newCorrect,
                answerOcrFeedback = feedback
            )
        }
    }
```

- [ ] **Step 3: 编译检查**

操作：Build → Make Project

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): applyAnswerInference 写 state + 算反馈文案"
```

---

## Task 5: 实现 runOcrAndInfer 异步协程方法

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`（在 applyAnswerInference 之后）

- [ ] **Step 1: 加 runOcrAndInfer 私有方法**

紧跟 `applyAnswerInference` 方法后插入：

```kotlin
    /**
     * 跑 OCR + 提取字母 + 应用推断。任何异常 fail-soft，仅写 snackbar 不抛。
     */
    private suspend fun runOcrAndInfer(uri: Uri) {
        val text = try {
            ocrEngine.recognizeText(uri)
        } catch (e: CancellationException) {
            throw e  // 协程取消正常传递
        } catch (e: Exception) {
            _uiState.update {
                it.copy(answerOcrFeedback = "OCR 识别失败：${e.message ?: "未知错误"}，请手动设置")
            }
            return
        }
        if (text.isBlank()) {
            _uiState.update {
                it.copy(answerOcrFeedback = "OCR 识别为空，请手动设置")
            }
            return
        }
        val letters = AnswerLetterExtractor.extract(text)
        applyAnswerInference(uri, letters, text)
        _uiState.update { it.copy(answerInferredFromOcr = true) }
    }
```

- [ ] **Step 2: 编译检查**

操作：Build → Make Project

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): runOcrAndInfer 异步 OCR + 字母提取"
```

---

## Task 6: 改 addAnswerImageUri 触发 OCR + 写 snapshot

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt:324-326`（addAnswerImageUri 方法）

- [ ] **Step 1: 重写 addAnswerImageUri**

把第 324-326 行：

```kotlin
    fun addAnswerImageUri(uri: Uri) {
        _uiState.update { it.copy(answerImageUris = it.answerImageUris + uri) }
    }
```

改为：

```kotlin
    fun addAnswerImageUri(uri: Uri) {
        val isFirst = _uiState.value.answerImageUris.isEmpty()
        _uiState.update {
            it.copy(
                answerImageUris = it.answerImageUris + uri,
                // 清掉上一次 OCR 反馈，避免新 OCR 前显示旧文
                answerOcrFeedback = null
            )
        }
        if (isFirst) {
            // Snapshot 当前状态，OCR 跑完会改 state，删图时用此回滚
            val current = _uiState.value
            _uiState.update {
                it.copy(
                    preInferenceSnapshot = AnswerSnapshot(
                        questionType = current.questionType,
                        correctOptionIndices = current.correctOptionIndices
                    )
                )
            }
            // 异步 OCR（可被后续 add/remove 取消）
            _pendingAnswerOcrJob.value?.cancel()
            _pendingAnswerOcrJob.value = viewModelScope.launch {
                runOcrAndInfer(uri)
            }
        }
    }
```

- [ ] **Step 2: 编译检查**

操作：Build → Make Project

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): addAnswerImageUri 首张触发 OCR + snapshot"
```

---

## Task 7: 改 removeAnswerImageUri 加回滚逻辑

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt:328-332`（removeAnswerImageUri 方法）

- [ ] **Step 1: 重写 removeAnswerImageUri**

把第 328-332 行：

```kotlin
    fun removeAnswerImageUri(index: Int) {
        _uiState.update {
            it.copy(answerImageUris = it.answerImageUris.filterIndexed { i, _ -> i != index })
        }
    }
```

改为：

```kotlin
    fun removeAnswerImageUri(index: Int) {
        val current = _uiState.value
        val newList = current.answerImageUris.filterIndexed { i, _ -> i != index }

        // 回滚判定：仅当 list 变空 + 处于 OCR 推断态 + 有 snapshot
        val shouldRollback = newList.isEmpty() &&
                             current.answerInferredFromOcr &&
                             current.preInferenceSnapshot != null

        if (shouldRollback) {
            val snap = current.preInferenceSnapshot!!
            // 取消可能正在跑的 OCR 协程
            _pendingAnswerOcrJob.value?.cancel()
            _pendingAnswerOcrJob.value = null
            _uiState.update {
                it.copy(
                    answerImageUris = newList,
                    questionType = snap.questionType,
                    correctOptionIndices = snap.correctOptionIndices,
                    answerInferredFromOcr = false,
                    preInferenceSnapshot = null,
                    answerOcrFeedback = "已恢复题目类型"
                )
            }
        } else {
            _uiState.update { it.copy(answerImageUris = newList) }
        }
    }
```

- [ ] **Step 2: 编译检查**

操作：Build → Make Project

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): removeAnswerImageUri 加回滚逻辑到 pre-inference snapshot"
```

---

## Task 8: ImportScreen 加 LaunchedEffect snackbar

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt:81-86`（在 RAG error snackbar 后追加）

- [ ] **Step 1: 加 LaunchedEffect 处理 answerOcrFeedback**

紧跟第 81-86 行的 `LaunchedEffect(uiState.ragErrorMessage)` 之后，插入：

```kotlin
    LaunchedEffect(uiState.answerOcrFeedback) {
        uiState.answerOcrFeedback?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearAnswerOcrFeedback()
        }
    }
```

- [ ] **Step 2: 编译检查**

操作：Build → Make Project

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "feat(import): snackbar 弹窗显示答案图 OCR 反馈"
```

---

## Task 9: 真机覆盖安装 + 手动验证 10 个场景

**Files:** 无（仅手动验证）

- [ ] **Step 1: 在 Android Studio 真机调试运行**

操作：连接 Android 8.0+ 真机 → Run 按钮（覆盖安装）

Expected: APK 安装成功，老数据保留（Room schema 0 改动）

- [ ] **Step 2: 验证单选识别**

操作：
1. 主页 → 点 + 录入错题
2. 选 1 张题目图（任意内容）
3. 在"答案/解析"区域点 + → 选 1 张写有"答案：A"的图 → 裁剪 → 完成
4. 观察 snackbar + 题目类型 chip + 选项 A 勾选

Expected: snackbar "已识别为单选题，答案：A"，题目类型自动切到"单选题"，A 选项金色高亮

- [ ] **Step 3: 验证多选识别**

操作：同 Step 2，但答案图写"答案：AB"

Expected: snackbar "已识别为多选题，答案：A、B"，题目类型切到"多选题"，A 和 B 同时金色高亮

- [ ] **Step 4: 验证主观题识别**

操作：同 Step 2，但答案图只写"略"或"详见解析"

Expected: snackbar "未识别到答案字母，标记为主观题"，题目类型切到"主观题"

- [ ] **Step 5: 验证超范围字母警告**

操作：
1. 录入题，**删掉 C 和 D 选项**（保留 A、B）
2. 答案图写"答案：ACD" → 裁剪 → 完成
3. 观察 snackbar + 勾选状态

Expected: snackbar "已识别为多选题，答案：A、C（D 超出选项范围已忽略）"，A 和 C 勾选

- [ ] **Step 6: 验证多张图只触发首张**

操作：
1. 录入题，先传"答案：A"图，再传"解析：xxxxxx"图
2. 观察两次上传后状态

Expected: 第一张触发 OCR → SINGLE_CHOICE + A 勾选；第二张加入列表但不触发 OCR，状态保持 SINGLE

- [ ] **Step 7: 验证删图回滚**

操作：紧接 Step 2，删掉那张答案图（点 ✕）

Expected: snackbar "已恢复题目类型"，题目类型回到上传前（默认 SINGLE_CHOICE 但勾选清空，或用户原设置）

- [ ] **Step 8: 验证 OCR 失败**

操作：飞行模式 → 录入题 → 答案图传任意图

Expected: snackbar "OCR 识别失败：xxx，请手动设置"，题目类型和勾选不变

> 注意：飞行模式可能让 ML Kit 模型无法下载。如果是首次使用 ML Kit，需先连网下载模型再测。可以先正常模式下传一张模糊/手写答案图（OCR 识别为空）来测试："OCR 识别为空，请手动设置"。

- [ ] **Step 9: 验证编辑模式不触发 OCR**

操作：
1. 录入 1 条错题（手动设 SINGLE_CHOICE + 答案 B）
2. 保存
3. 主页 → 浏览 → 点 ✏️ 编辑该题
4. **不新上传答案图**，直接点保存

Expected: 老数据原样保留，questionType/correctAnswer 不被改动

- [ ] **Step 10: 验证编辑模式新传图触发 OCR**

操作：紧接 Step 9，在编辑界面**新上传** 1 张"答案：A"图

Expected: 触发 OCR，题目类型切 SINGLE_CHOICE + A 勾选（覆盖原 B）

- [ ] **Step 11: 验证数据保护（覆盖安装不丢数据）**

操作：
1. 录入 5 条错题（不同题型）
2. **不卸载**，直接 Run 覆盖安装新 APK
3. 主页 → 浏览 → 验证 5 条数据完整

Expected: 5 条数据完整，无丢失

- [ ] **Step 12: 回归测试 — RAG 题目图自动归类**

操作：录入 1 题，**只传题目图**（不传答案图）

Expected: 现有 RAG 行为不变（自动填科目/章节/知识点）

- [ ] **Step 13: 如有 bug 修复后单独 commit**

如果 12 步中有任何不符合预期：

```bash
git add <fixed-files>
git commit -m "fix(import): <description>"
```

---

## Task 10: 更新 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md:9`（"7 个新功能" 列表）

- [ ] **Step 1: 改写"已实现"段落第一行**

把第 9 行：

```markdown
CPA 错题笔记应用 — 核心复习流程（录入→复习→分析）已完成。多图上传、图片裁剪、收藏夹、置顶、复习进度显示、7 个新功能（答案图片全题型、题号弹窗、已掌握按钮、已掌握复习、录入时间、题型筛选、图片自适应预览）已实现。功能稳定，可日常使用。
```

改为：

```markdown
CPA 错题笔记应用 — 核心复习流程（录入→复习→分析）已完成。多图上传、图片裁剪、收藏夹、置顶、复习进度显示、7 个新功能（答案图片全题型、题号弹窗、已掌握按钮、已掌握复习、录入时间、题型筛选、图片自适应预览）、**答案图自动识别（OCR 提取字母 → 自动设题型+勾选项）**已实现。功能稳定，可日常使用。
```

- [ ] **Step 2: 在"录入"段落追加 1 行新功能**

找到"录入"段落（约第 97 行），在末尾加：

```markdown
- **答案图自动识别**：上传第一张答案/解析图片时本地 OCR 提取 A-H 字母 → 1 字母=单选题+勾对应 / 2+=多选+多勾 / 0=主观题；超出选项范围时 snackbar 警告；删除该图后回滚到上传前状态。详见 `docs/superpowers/specs/2026-06-08-answer-image-auto-fill-design.md`
```

- [ ] **Step 3: 提交**

```bash
git add CLAUDE.md
git commit -m "docs: 答案图自动识别功能说明"
```

---

## Self-Review（执行完所有任务后自审）

- [ ] 跑 `AnswerLetterExtractorTest` 10 个用例全过
- [ ] Build Project 无错
- [ ] 真机 12 步验证全过
- [ ] 数据保护 0 丢失
- [ ] CLAUDE.md 已更新
- [ ] 所有 commit 信息清晰

---

## 执行选项

Plan 已保存到 `docs/superpowers/plans/2026-06-08-answer-image-auto-fill.md`，请选择执行方式：

**1. Subagent-Driven（推荐）** — 我每个 Task 派一个新 subagent 实施，Task 间做代码 review + 集成验证
**2. Inline Execution** — 当前 session 串行执行所有 Task，关键节点停下 review
