# CPA 错题 RAG 知识库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CPA 错题笔记应用增加"题目图片自动归类到章节 + 知识点"能力（限 CPA 会计 30 章）。流程：题目图片 → ML Kit 本地 OCR → APK 内置关键词知识库召回 top-5 → DeepSeek 精排 → 自动填充 ImportScreen 下拉。用户可手动覆盖。

**Architecture:** 接口优先（`KnowledgeClassifier`）→ Mock + 真实双实现 → DI 根据 ApiKey 切换 → UI 状态机驱动。**整链路（OCR → 召回 → LLM）任何一步失败，不抛异常，返回 `ClassifyResult.failed(reason)`；UI 端识别后 Snackbar 提示 + 下拉留空 + 不阻塞保存。**

**Tech Stack:**
- Kotlin 2.0 + Jetpack Compose (BOM 2025.02.00)
- Hilt 2.52（已有）+ Room 2.7.0（**零 schema 变更**）+ DataStore 1.1.0（已有）
- **新增依赖**：Google ML Kit 文字识别（中文端侧）、Retrofit 2.11.0、kotlinx-serialization 1.7.3
- **OCR**：Google ML Kit `text-recognition-chinese:16.0.0`（中文、离线、免费、不需要 VPN）
- **LLM**：DeepSeek（API Key 由用户在设置页填入 DataStore）
- **项目无 gradlew，验证通过 Android Studio 真机调试**（"Run" 按钮覆盖安装）。所有数据 100% 保留。

---

## File Structure

### 新增文件（按职责拆分）

| 文件 | 职责 |
|------|------|
| `data/rag/KnowledgeClassifier.kt` | 接口 + `ClassifyResult` data class |
| `data/rag/MockKnowledgeClassifier.kt` | Mock 实现（无 API Key 时用） |
| `data/rag/DeepSeekKnowledgeClassifier.kt` | 真实实现（ML Kit OCR + 召回 + DeepSeek 精排） |
| `data/rag/OcrEngine.kt` | ML Kit 端侧 OCR 包装 |
| `data/rag/KnowledgeBase.kt` | 内存中知识库数据结构 + `recall()` 方法 |
| `data/rag/KnowledgeBaseLoader.kt` | 从 assets/json/ 加载到 `KnowledgeBase` |
| `data/rag/DeepSeekApi.kt` | Retrofit 接口 + DTOs |
| `data/rag/ApiKeyProvider.kt` | DataStore 包装的 Key 存取 |
| `di/ClassifierModule.kt` | Hilt 模块（接口绑定） |
| `ui/screens/SettingsScreen.kt` | API Key 设置页 |
| `assets/json/accounting_knowledge_points.json` | 知识点知识库（150-300 条） |
| `test/data/rag/KeywordRecallTest.kt` | 召回算法测试 |
| `test/data/rag/ClassifierTest.kt` | 分类器测试 |
| `test/data/rag/ApiKeyProviderTest.kt` | ApiKey 存取测试 |
| `test/data/repository/MistakeRepositoryTest.kt` | upsertKnowledgePoint 测试 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `app/build.gradle.kts` | 加 4 个依赖（ML Kit、Retrofit、kotlinx-serialization、OkHttp）+ serialization 插件 |
| `data/repository/MistakeRepository.kt` | 加 `upsertKnowledgePoint(chapterId, name): Long` |
| `ui/screens/ImportViewModel.kt` | 加 `ragStatus` 状态机 + `_pendingClassifyJob: Job?` + 触发/取消/守护/自动 upsert 知识点到 Room |
| `ui/screens/ImportScreen.kt` | 加 LOADING Spinner + ERROR Snackbar |
| `ui/navigation/NavGraph.kt` | 加 settings 路由 |
| `CLAUDE.md` | 加 RAG 相关条目 |

### 不修改（数据保护）

- 全部 Room Entity / Dao / AppDatabase / DatabaseModule / Migration
- 数据库版本号（保持 6）
- `mistakes.knowledgePointId` 字段（已存在，只填值）

### 本地工具脚本（不入仓）

- `tools/extract_pdf.py`（PDF → 章节文本）
- `tools/gen_kb.py`（章节文本 → JSON 初稿）
- `tools/merge_kb.py`（合并 → 最终 assets JSON）

---

## Phase A — 基础架构（Mock + 状态机）

### Task 1: 添加依赖

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 加 plugin（kotlinx-serialization）**

在 `plugins { ... }` 块顶部加：

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("com.android.application")
    // ... 已有
}
```

- [ ] **Step 2: 加 dependencies（4 个新依赖）**

在 `dependencies { ... }` 块底部加：

```kotlin
    // Google ML Kit 端侧 OCR（中文）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Retrofit + OkHttp（DeepSeek API 调用）
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    // kotlinx-coroutines (Retrofit suspend 函数所需)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // kotlinx-serialization（JSON 解析）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

- [ ] **Step 3: 在 Android Studio 同步 Gradle**

操作：File → Sync Project with Gradle Files（Ctrl+Shift+O）
预期：sync 成功，无报错。`External Libraries` 里能看到 `kotlinx-serialization-core` 和 `retrofit2`。

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: 加 ML Kit OCR + Retrofit + kotlinx-serialization 依赖"
```

---

### Task 2: KnowledgeClassifier 接口 + ClassifyResult

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/rag/KnowledgeClassifier.kt`

- [ ] **Step 1: 创建目录 + 文件**

```kotlin
package com.mistakenotes.data.rag

import android.net.Uri
import com.mistakenotes.domain.model.Subject

/**
 * 题目图片 → 章节 + 知识点 分类器接口
 *
 * 实现类：
 * - [MockKnowledgeClassifier]: 无 API Key 时用，返回固定结果
 * - [DeepSeekKnowledgeClassifier]: 有 API Key 时用，ML Kit OCR + 召回 + DeepSeek 精排
 */
interface KnowledgeClassifier {
    /**
     * @param questionImage 题目图片 URI
     * @param subjectHint 已知科目时传入（用于限制召回范围到该科目下）
     * @return 分类结果；任何失败都返回 [ClassifyResult.failed]，**不抛异常**
     */
    suspend fun classify(
        questionImage: Uri,
        subjectHint: Subject? = null
    ): ClassifyResult
}

/**
 * 分类结果
 *
 * @param chapterId 章节 ID；`< 0` 表示失败（见 [isFailed]）
 * @param knowledgePointId 知识点 ID；失败时为 `-1`
 * @param confidence 0~1；`< 0.5` 时 UI 高亮"建议复核"
 * @param reasoning 分类原因（可空）；UI 可展开"为什么这么分类"
 */
data class ClassifyResult(
    val chapterId: Long,
    val knowledgePointId: Long,
    val confidence: Float,
    val reasoning: String = ""
) {
    /** 分类是否失败（OCR 空 / 网络挂 / LLM 错 / API Key 无效） */
    val isFailed: Boolean get() = chapterId < 0

    companion object {
        /** 构造一个失败结果（chapterId = -1 标识失败） */
        fun failed(reason: String): ClassifyResult = ClassifyResult(
            chapterId = -1,
            knowledgePointId = -1,
            confidence = 0f,
            reasoning = reason
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mistakenotes/data/rag/KnowledgeClassifier.kt
git commit -m "feat(rag): KnowledgeClassifier 接口 + ClassifyResult 数据类"
```

---

### Task 3: MockKnowledgeClassifier + TDD 测试

**Files:**
- Create: `app/src/test/java/com/mistakenotes/data/rag/MockClassifierTest.kt`
- Create: `app/src/main/java/com/mistakenotes/data/rag/MockKnowledgeClassifier.kt`

- [ ] **Step 1: 写失败测试（先 TDD）**

创建 `app/src/test/java/com/mistakenotes/data/rag/MockClassifierTest.kt`：

```kotlin
package com.mistakenotes.data.rag

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockClassifierTest {

    @Test
    fun `mock classifier returns chapter 1 knowledge point 1 with high confidence`() = runTest {
        val mock = MockKnowledgeClassifier()
        val result = mock.classify(Uri.EMPTY)
        assertEquals(1L, result.chapterId)
        assertEquals(1L, result.knowledgePointId)
        assertTrue(result.confidence > 0.5f)
        assertTrue(!result.isFailed)
    }

    @Test
    fun `mock classifier has 800ms delay simulating network`() = runTest {
        val mock = MockKnowledgeClassifier()
        val start = System.currentTimeMillis()
        mock.classify(Uri.EMPTY)
        val elapsed = System.currentTimeMillis() - start
        // 允许 ±100ms 误差
        assertTrue("Expected ~800ms, got ${elapsed}ms", elapsed in 700..1500)
    }
}
```

- [ ] **Step 2: 跑测试看是否失败**

操作：在 Android Studio 打开 `MockClassifierTest.kt`，右键 → Run 'MockClassifierTest'
预期：编译失败，错误 `Unresolved reference: MockKnowledgeClassifier`

- [ ] **Step 3: 创建 MockKnowledgeClassifier**

创建 `app/src/main/java/com/mistakenotes/data/rag/MockKnowledgeClassifier.kt`：

```kotlin
package com.mistakenotes.data.rag

import android.net.Uri
import com.mistakenotes.domain.model.Subject
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock 分类器：永远返回固定结果（第一章第 1 知识点）
 *
 * 用途：
 * 1. 无 API Key 时的默认实现（让用户能体验 RAG 流程）
 * 2. 单元测试 / 集成测试
 * 3. UI 开发期不依赖外部服务
 */
@Singleton
class MockKnowledgeClassifier @Inject constructor() : KnowledgeClassifier {

    override suspend fun classify(
        questionImage: Uri,
        subjectHint: Subject?
    ): ClassifyResult {
        delay(800)  // 模拟网络延迟
        return ClassifyResult(
            chapterId = 1L,
            knowledgePointId = 1L,
            confidence = 0.85f,
            reasoning = "[Mock] 这是 mock 返回值，正式实现见 DeepSeekKnowledgeClassifier"
        )
    }
}
```

- [ ] **Step 4: 重跑测试**

操作：右键 → Run 'MockClassifierTest'
预期：**2 个测试都通过**

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mistakenotes/data/rag/MockClassifierTest.kt
git add app/src/main/java/com/mistakenotes/data/rag/MockKnowledgeClassifier.kt
git commit -m "feat(rag): MockKnowledgeClassifier + 测试"
```

---

### Task 4: ClassifierModule（Hilt DI）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/di/ClassifierModule.kt`

- [ ] **Step 1: 创建模块**

```kotlin
package com.mistakenotes.di

import com.mistakenotes.data.rag.ApiKeyProvider
import com.mistakenotes.data.rag.DeepSeekKnowledgeClassifier
import com.mistakenotes.data.rag.KnowledgeClassifier
import com.mistakenotes.data.rag.MockKnowledgeClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * KnowledgeClassifier 绑定模块
 *
 * 运行时根据 [ApiKeyProvider.hasKeySync] 切换：
 * - 有 Key → [DeepSeekKnowledgeClassifier]（真实分类）
 * - 无 Key → [MockKnowledgeClassifier]（mock 返回）
 *
 * 用户在 SettingsScreen 填入 Key 后下次启动即生效。
 */
@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun provideKnowledgeClassifier(
        mock: Provider<MockKnowledgeClassifier>,
        real: Provider<DeepSeekKnowledgeClassifier>,
        keyStore: ApiKeyProvider
    ): KnowledgeClassifier = if (keyStore.hasKeySync()) real.get() else mock.get()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/mistakenotes/di/ClassifierModule.kt
git commit -m "feat(di): ClassifierModule 运行时按 Key 切换"
```

> **注**：本 Task 引用了 `DeepSeekKnowledgeClassifier`（Task 19）和 `ApiKeyProvider.hasKeySync()`（Task 9）。Android Studio 会报红——属正常，**不要**现在编译。继续做 Task 5-8，等 Task 9 + Task 19 完成后错误会消失。

---

### Task 5: ImportViewModel 加 ragStatus 状态机

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

- [ ] **Step 1: 加 RagStatus 枚举 + UI state 字段**

打开 `ImportViewModel.kt`，在文件顶部（import 后）加：

```kotlin
/** RAG 分类状态机 */
enum class RagStatus { IDLE, LOADING, DONE, ERROR }
```

把 `ImportUiState` data class 改成（在末尾加 2 个字段）：

```kotlin
data class ImportUiState(
    val imageUris: List<Uri> = emptyList(),
    // ... 已有字段保持不变 ...
    val isEditMode: Boolean = false,
    val entryDate: Long? = null,
    // ====== 新增 RAG 状态 ======
    val ragStatus: RagStatus = RagStatus.IDLE,
    val ragErrorMessage: String? = null
)
```

- [ ] **Step 2: 在 ImportViewModel 注入 classifier**

在 `ImportViewModel` 构造器加参数（保持其他不变）：

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,  // ← 新增
    savedStateHandle: SavedStateHandle
) : ViewModel() {
```

- [ ] **Step 3: 加 _pendingClassifyJob 字段**

在 `_uiState` 后面加：

```kotlin
private val _pendingClassifyJob = MutableStateFlow<Job?>(null)
```

并在文件顶部 import：

```kotlin
import kotlinx.coroutines.Job
```

- [ ] **Step 4: 修改 addImageUri 触发 RAG**

替换 `addImageUri` 函数（**只在第一张图时触发**）：

```kotlin
fun addImageUri(uri: Uri) {
    val isFirst = _uiState.value.imageUris.isEmpty()
    _uiState.update { it.copy(imageUris = it.imageUris + uri) }
    if (isFirst) {
        triggerRagClassification(uri)
    }
}

private fun triggerRagClassification(uri: Uri) {
    // 取消上一个未完成的任务
    _pendingClassifyJob.value?.cancel()

    _uiState.update { it.copy(ragStatus = RagStatus.LOADING, ragErrorMessage = null) }

    _pendingClassifyJob.value = viewModelScope.launch {
        try {
            val result = classifier.classify(uri)
            // 守护：用户可能已经删除/替换了这张图
            if (_uiState.value.imageUris.firstOrNull() != uri) return@launch
            // 守护：用户可能已经手动选了下拉
            if (_uiState.value.chapterId != null || _uiState.value.knowledgePointId != null) {
                _uiState.update { it.copy(ragStatus = RagStatus.DONE) }
                return@launch
            }

            if (result.isFailed) {
                _uiState.update {
                    it.copy(
                        ragStatus = RagStatus.ERROR,
                        ragErrorMessage = "AI 归类失败：${result.reasoning}，请手动选择"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        chapterId = result.chapterId,
                        knowledgePointId = result.knowledgePointId,
                        ragStatus = RagStatus.DONE
                    )
                }
            }
        } catch (e: CancellationException) {
            // 用户取消，正常路径
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    ragStatus = RagStatus.ERROR,
                    ragErrorMessage = "AI 归类失败：${e.message ?: "未知错误"}，请手动选择"
                )
            }
        }
    }
}
```

并在文件顶部 import：

```kotlin
import com.mistakenotes.data.rag.KnowledgeClassifier
import kotlinx.coroutines.CancellationException
```

- [ ] **Step 5: 修改 removeImageUri 取消 RAG**

替换 `removeImageUri` 函数：

```kotlin
fun removeImageUri(index: Int) {
    _uiState.update {
        it.copy(imageUris = it.imageUris.filterIndexed { i, _ -> i != index })
    }
    // 如果删的是第一张图且当前有 pending RAG 任务，取消
    if (index == 0) {
        _pendingClassifyJob.value?.cancel()
        _pendingClassifyJob.value = null
        _uiState.update { it.copy(ragStatus = RagStatus.IDLE, ragErrorMessage = null) }
    }
}
```

- [ ] **Step 6: 修改 saveMistake 取消 pending**

在 `saveMistake()` 函数最开头加一行（先于 `val state = _uiState.value`）：

```kotlin
fun saveMistake() {
    _pendingClassifyJob.value?.cancel()  // ← 加这一行
    _pendingClassifyJob.value = null
    val state = _uiState.value
    // ... 后续代码不变 ...
```

- [ ] **Step 7: 加 clearRagError 方法**

在文件末尾（最后一个方法前）加：

```kotlin
fun clearRagError() {
    _uiState.update { it.copy(ragStatus = RagStatus.IDLE, ragErrorMessage = null) }
}
```

- [ ] **Step 8: Sync Gradle + 编译**

操作：File → Sync Project with Gradle Files → 等待完成
预期：编译通过（如果 ClassifierModule 引用的 `DeepSeekKnowledgeClassifier`/`ApiKeyProvider` 还没创建，会红——属正常，继续做 Task 9/Task 19）

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): 加 ragStatus 状态机 + 触发/取消/守护/失败回退"
```

---

## Phase B — UI 状态显示

### Task 6: ImportScreen 加 LOADING Spinner

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`

- [ ] **Step 1: 找到 ChapterDropdown 调用位置**

打开 `ImportScreen.kt`，定位到 `ChapterDropdown(...)` 那行（约第 149-154 行）。它长这样：

```kotlin
ChapterDropdown(
    chapters = uiState.chapters,
    selectedChapterId = uiState.chapterId,
    enabled = uiState.subjectId != null,
    onChapterSelected = { viewModel.setChapter(it) }
)
```

- [ ] **Step 2: 在 ChapterDropdown 外层包 Box 加 Spinner**

把上面那段替换为：

```kotlin
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
```

并在文件顶部 import 加：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import com.mistakenotes.ui.screens.RagStatus
```

- [ ] **Step 3: 编译验证**

操作：Build → Make Project（Ctrl+F9）
预期：编译通过

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "feat(import): ChapterDropdown 右侧加 RAG LOADING Spinner"
```

---

### Task 7: ImportScreen 加 ERROR Snackbar

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt`

- [ ] **Step 1: 找到 LaunchedEffect(errorMessage) 块**

定位到 `LaunchedEffect(uiState.errorMessage) { ... }`（约第 73-78 行），在它**后面**加新 block：

```kotlin
LaunchedEffect(uiState.ragErrorMessage) {
    uiState.ragErrorMessage?.let { message ->
        snackbarHostState.showSnackbar(message)
        viewModel.clearRagError()
    }
}
```

- [ ] **Step 2: 编译 + 跑在设备上**

操作：Build → Make Project → Run 'app'
预期：app 启动正常，录错题时 RAG 失败会弹 Snackbar（目前 DeepSeek 还没接，1.5s 后会跳 ERROR）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportScreen.kt
git commit -m "feat(import): RAG 错误时显示 Snackbar"
```

---

### Task 8: 真机验证 Phase A+B

**Files:** （无）

- [ ] **Step 1: 跑 app + 进录入页**

操作：Run 'app' → 点"录入"按钮 → 进入 ImportScreen

- [ ] **Step 2: 选 1 张图（任何图片）**

操作：点"题目图片"下方的"+"按钮 → 从相册选任意一张

- [ ] **Step 3: 观察 Spinner 出现 ~0.8s 后下拉自动填**

预期：
- 选图后 3 个下拉（科目/章节/知识点）右侧出现小转圈
- ~0.8s 后转圈消失，**章节下拉被自动选中"第一章 总论"**，**知识点下拉被自动选中第一条**（但因为 `knowledge_points` 表是空的，下拉里看不到——这是预期，§Phase E 修）

- [ ] **Step 4: 选图后立刻删除**

操作：点刚选图的缩略图右上角删除 → 立刻再选一张 → 观察
预期：上一张图的 RAG 任务被取消，Spinning 状态保留给最新一张图

- [ ] **Step 5: 验证完成 Phase A+B**

预期：上述都正常。**不通过的**回查 Task 5 的代码（特别是 `_pendingClassifyJob` 的 `cancel()` 顺序）。

- [ ] **Step 6:（无 commit，本 Task 不写代码）**

---

## Phase C — 设置页（API Key）

### Task 9: ApiKeyProvider + TDD 测试

**Files:**
- Create: `app/src/test/java/com/mistakenotes/data/rag/ApiKeyProviderTest.kt`
- Create: `app/src/main/java/com/mistakenotes/data/rag/ApiKeyProvider.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/mistakenotes/data/rag/ApiKeyProviderTest.kt`：

```kotlin
package com.mistakenotes.data.rag

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ApiKeyProviderTest {

    @Test
    fun `hasKey returns false when no key stored`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        assertFalse(provider.hasKey())
    }

    @Test
    fun `setKey and hasKey round-trip`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        provider.setKey("sk-test-12345")
        assertTrue(provider.hasKey())
        assertEquals("sk-test-12345", provider.get())
    }

    @Test
    fun `clearKey makes hasKey return false`() = runTest {
        val provider = ApiKeyProvider(ApplicationProvider.getApplicationContext())
        provider.setKey("sk-test-12345")
        provider.clearKey()
        assertFalse(provider.hasKey())
    }
}
```

- [ ] **Step 2: 跑测试看是否失败**

操作：右键 → Run 'ApiKeyProviderTest'
预期：编译失败 `Unresolved reference: ApiKeyProvider`

- [ ] **Step 3: 创建 ApiKeyProvider**

创建 `app/src/main/java/com/mistakenotes/data/rag/ApiKeyProvider.kt`：

```kotlin
package com.mistakenotes.data.rag

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore by preferencesDataStore(name = "rag_api_key")

/**
 * DeepSeek API Key 存取（DataStore 包装）
 *
 * Key 存于 DataStore（Preferences），仅本机保留，不上传。
 */
@Singleton
class ApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyFlow = context.apiKeyDataStore.data

    /** Key 是否已设置（用于 DI 切换 Mock/Real） */
    suspend fun hasKey(): Boolean = !keyFlow.first()[KEY].isNullOrBlank()

    /** 同步版 hasKey（DI 启动时不能用 suspend） */
    fun hasKeySync(): Boolean = runCatching {
        kotlinx.coroutines.runBlocking { hasKey() }
    }.getOrDefault(false)

    /** 取 Key（DI 切换时被 ClassifierModule 调用——本期为简化用 hasKey()） */
    suspend fun get(): String = keyFlow.first()[KEY] ?: ""

    suspend fun setKey(key: String) {
        context.apiKeyDataStore.edit { it[KEY] = key }
    }

    suspend fun clearKey() {
        context.apiKeyDataStore.edit { it.remove(KEY) }
    }

    companion object {
        private val KEY = stringPreferencesKey("deepseek_api_key")
    }
}
```

- [ ] **Step 4:（已不需要——Task 4 已直接用 `hasKeySync()`，本步骤仅验证）**

打开 `ClassifierModule.kt`，确认 `keyStore.hasKeySync()` 已正确引用。无需修改。

- [ ] **Step 5: 重跑测试**

操作：右键 → Run 'ApiKeyProviderTest'
预期：**3 个测试都通过**

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/mistakenotes/data/rag/ApiKeyProviderTest.kt
git add app/src/main/java/com/mistakenotes/data/rag/ApiKeyProvider.kt
git add app/src/main/java/com/mistakenotes/di/ClassifierModule.kt
git commit -m "feat(rag): ApiKeyProvider (DataStore) + 测试 + ClassifierModule 同步版 hasKey"
```

---

### Task 10: SettingsScreen UI

**Files:**
- Create: `app/src/main/java/com/mistakenotes/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.mistakenotes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.rag.ApiKeyProvider
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = if (apiKeyProvider.hasKey()) apiKeyProvider.get() else ""
            _uiState.update { it.copy(apiKey = current, hasKey = current.isNotBlank()) }
        }
    }

    fun setApiKey(key: String) = _uiState.update { it.copy(apiKey = key) }

    fun save() = viewModelScope.launch {
        apiKeyProvider.setKey(_uiState.value.apiKey.trim())
        _uiState.update { it.copy(hasKey = true, message = "已保存") }
    }

    fun clear() = viewModelScope.launch {
        apiKeyProvider.clearKey()
        _uiState.update { it.copy(apiKey = "", hasKey = false, message = "已清空") }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

data class SettingsUiState(
    val apiKey: String = "",
    val hasKey: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = AmberGold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = AmberGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = InkStoneBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "DeepSeek API Key",
                color = TextCream,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "用于 AI 自动归类题目。仅存本机，不上传。",
                color = TextCream.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.setApiKey(it) },
                label = { Text("API Key") },
                placeholder = { Text("sk-...", color = TextCream.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextCream,
                    unfocusedTextColor = TextCream
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = InkStoneBlack
                    )
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (uiState.hasKey) "更新" else "保存")
                }
                OutlinedButton(
                    onClick = { viewModel.clear() },
                    enabled = uiState.hasKey
                ) {
                    Text("清空", color = TextCream)
                }
            }
            if (uiState.hasKey) {
                Text(
                    "✓ API Key 已配置",
                    color = AmberGold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/SettingsScreen.kt
git commit -m "feat(settings): SettingsScreen + ViewModel (API Key 增删改)"
```

---

### Task 11: NavGraph 加 settings 路由

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/navigation/NavGraph.kt`

- [ ] **Step 1: 加 import**

在文件顶部 import 块加：

```kotlin
import com.mistakenotes.ui.screens.SettingsScreen
```

- [ ] **Step 2: 加 composable 路由**

找到 `NavHost(...) { ... }` 块（在 `composable("home") { ... }` 之后），加：

```kotlin
    composable("settings") {
        SettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
```

- [ ] **Step 3: 编译 + 跑**

操作：Build → Make Project → Run 'app'
预期：编译通过，app 启动后**先手动测** settings 路由：临时在 HomeScreen 的 TopAppBar 加一个 IconButton 跳转到 settings（验证完后删掉）。或者更简单——用 adb shell `am start -a android.intent.action.VIEW -d "myapp://settings"` 测试。

- [ ] **Step 4: 临时验证 + 清理**

如果上一步在 HomeScreen 加了跳转按钮，**验证完后删除**。最终用 adb 命令验证 settings 路由可达：

```bash
adb shell am start -n com.mistakenotes/.MainActivity
# 手动从主页 → 录入 → 返回 → 再找个入口
```

> **本 Task 不强制要求真机跑通跳转**——可以在 Task 11 末尾用 TODO 注释标记。**Phase 末尾**（Task 28 CLAUDE.md 同步）可以决定是否在 Home 加永久入口。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/navigation/NavGraph.kt
git commit -m "feat(nav): 加 settings 路由"
```

---

## Phase D — 知识库（内存版）

### Task 12: KnowledgeBase data class + recall() + TDD 测试

**Files:**
- Create: `app/src/test/java/com/mistakenotes/data/rag/KeywordRecallTest.kt`
- Create: `app/src/main/java/com/mistakenotes/data/rag/KnowledgeBase.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/mistakenotes/data/rag/KeywordRecallTest.kt`：

```kotlin
package com.mistakenotes.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordRecallTest {

    private val samplePoints = listOf(
        KnowledgePointJson(
            id = 1, chapterId = 1, name = "存货的初始计量",
            description = "存货成本包括采购成本、加工成本",
            keywords = listOf("存货", "初始计量", "采购成本", "加工成本"),
            formulas = emptyList(), commonPitfalls = emptyList()
        ),
        KnowledgePointJson(
            id = 2, chapterId = 6, name = "权益法下顺逆流交易",
            description = "权益法核算长期股权投资",
            keywords = listOf("长投", "权益法", "顺流", "逆流", "未实现内部交易"),
            formulas = emptyList(), commonPitfalls = emptyList()
        ),
        KnowledgePointJson(
            id = 3, chapterId = 2, name = "存货的期末计量",
            description = "成本与可变现净值孰低",
            keywords = listOf("存货", "可变现净值", "跌价准备"),
            formulas = listOf("成本 - 可变现净值 = 跌价准备"),
            commonPitfalls = emptyList()
        )
    )

    private val base = KnowledgeBase(samplePoints)

    @Test
    fun `recall returns top-K sorted by score desc`() {
        val results = base.recall("长投权益法顺逆流交易", topK = 3)
        assertEquals(3, results.size)
        assertEquals(2L, results.first().id)  // 知识点 2 最相关
    }

    @Test
    fun `recall empty text returns empty list`() {
        val results = base.recall("", topK = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `recall respects chapterHint - filters to specific chapter`() {
        val results = base.recall("存货", topK = 5, chapterHint = 1L)
        // 章节 1 下的"存货的初始计量"应排第一
        assertEquals(1L, results.first().id)
    }

    @Test
    fun `recall formulas get weight boost`() {
        val base = KnowledgeBase(listOf(
            KnowledgePointJson(1, 1, "成本计算", "desc",
                keywords = listOf("成本"),
                formulas = listOf("成本 - 可变现净值 = 跌价准备"),
                commonPitfalls = emptyList()),
            KnowledgePointJson(2, 1, "普通知识点", "desc",
                keywords = listOf("成本"),
                formulas = emptyList(),
                commonPitfalls = emptyList())
        ))
        val results = base.recall("成本 可变现净值 跌价准备", topK = 2)
        // 知识点 1 含公式，应排第一
        assertEquals(1L, results.first().id)
    }
}
```

- [ ] **Step 2: 跑测试看是否失败**

操作：右键 → Run 'KeywordRecallTest'
预期：编译失败 `Unresolved reference: KnowledgeBase` 和 `KnowledgePointJson`

- [ ] **Step 3: 创建 KnowledgeBase + KnowledgePointJson**

创建 `app/src/main/java/com/mistakenotes/data/rag/KnowledgeBase.kt`：

```kotlin
package com.mistakenotes.data.rag

import kotlinx.serialization.Serializable

/**
 * 知识库 JSON Schema（对应 assets/json/accounting_knowledge_points.json 的单条记录）
 */
@Serializable
data class KnowledgePointJson(
    val id: Long,
    val chapterId: Long,
    val name: String,
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val formulas: List<String> = emptyList(),
    val commonPitfalls: List<String> = emptyList()
)

/**
 * 知识库 JSON 顶层结构
 */
@Serializable
data class KnowledgeBaseFile(
    val version: Int,
    val knowledgePoints: List<KnowledgePointJson>
)

/**
 * 内存中加载好的知识库
 *
 * - [recall] 方法做关键词召回（TF-IDF-like 简化版）
 * - 加载一次缓存在 SingletonComponent 作用域
 */
class KnowledgeBase(val points: List<KnowledgePointJson>) {

    /**
     * 关键词召回
     *
     * @param text OCR 提取出的题目文字
     * @param topK 返回 top-K 个候选
     * @param chapterHint 已知章节时传入，只在该章节下召回
     * @return 按得分降序排列的 top-K 知识点（最多 [topK] 个，可能少于）
     */
    fun recall(text: String, topK: Int = 5, chapterHint: Long? = null): List<KnowledgePointJson> {
        if (text.isBlank()) return emptyList()
        val tokens = tokenize(text)

        return points
            .asSequence()
            .filter { chapterHint == null || it.chapterId == chapterHint }
            .map { kp -> kp to scoreOf(kp, tokens, text) }
            .filter { it.second > 0 }  // 过滤掉完全不匹配的
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
            .toList()
    }

    /**
     * 简易中文分词（按非汉字字符切分 + 单字也保留）
     * 后续可换 HanLP-Android，效果更好
     */
    private fun tokenize(text: String): List<String> {
        // 1) 按非汉字切分（保留单字粒度，召回"长""投"也能命中"长投"）
        // 2) 提取所有 2~6 字的连续汉字片段
        val tokens = mutableListOf<String>()
        val regex = Regex("[一-龥]{1,6}")
        regex.findAll(text).forEach { match ->
            val word = match.value
            // 加单词本身 + 单词内所有 2 字组合
            tokens.add(word)
            if (word.length >= 2) {
                for (i in 0..word.length - 2) {
                    tokens.add(word.substring(i, i + 2))
                }
            }
        }
        return tokens.distinct()
    }

    private fun scoreOf(
        kp: KnowledgePointJson,
        tokens: List<String>,
        rawText: String
    ): Double {
        val matchCount = kp.keywords.count { kw -> tokens.any { tok -> tok.contains(kw) || kw.contains(tok) } }
        val formulaMatch = kp.formulas.count { f -> f in rawText }
        val pitfallMatch = kp.commonPitfalls.count { p -> p in rawText }
        return matchCount * 3.0 + formulaMatch * 2.0 + pitfallMatch * 1.5
    }
}
```

并在文件顶部 import：

```kotlin
import kotlinx.serialization.Serializable
```

- [ ] **Step 4: 重跑测试**

操作：右键 → Run 'KeywordRecallTest'
预期：**4 个测试都通过**

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mistakenotes/data/rag/KeywordRecallTest.kt
git add app/src/main/java/com/mistakenotes/data/rag/KnowledgeBase.kt
git commit -m "feat(rag): KnowledgeBase 内存结构 + 关键词召回算法 + 测试"
```

---

### Task 13: KnowledgeBaseLoader（从 assets 加载）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/rag/KnowledgeBaseLoader.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.mistakenotes.data.rag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 APK assets/json/accounting_knowledge_points.json 加载知识库
 *
 * 启动时调用一次，结果缓存在 SingletonComponent 作用域。
 */
@Singleton
class KnowledgeBaseLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): KnowledgeBase {
        val text = context.assets.open("json/accounting_knowledge_points.json")
            .bufferedReader()
            .use { it.readText() }
        val file = json.decodeFromString(KnowledgeBaseFile.serializer(), text)
        return KnowledgeBase(file.knowledgePoints)
    }
}
```

- [ ] **Step 2: 编译**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/data/rag/KnowledgeBaseLoader.kt
git commit -m "feat(rag): KnowledgeBaseLoader 从 assets 加载"
```

---

### Task 14: 占位 assets JSON

**Files:**
- Create: `app/src/main/assets/json/accounting_knowledge_points.json`

> **说明**：本 Task 创建一个**仅含 3 条样例数据**的占位 JSON，用于让 Loader 跑通流程。**真实 150-300 条数据**在 Phase J（Task 25-26）由用户用 PDF + Python 脚本生成后替换。

- [ ] **Step 1: 创建目录 + 文件**

```bash
mkdir -p app/src/main/assets/json
```

创建 `app/src/main/assets/json/accounting_knowledge_points.json`：

```json
{
  "version": 1,
  "knowledgePoints": [
    {
      "id": 1,
      "chapterId": 1,
      "name": "会计基本假设",
      "description": "会计基本假设包括会计主体、持续经营、会计分期和货币计量。",
      "keywords": ["会计主体", "持续经营", "会计分期", "货币计量", "基本假设"],
      "formulas": [],
      "commonPitfalls": ["混淆会计主体和法律主体"]
    },
    {
      "id": 2,
      "chapterId": 1,
      "name": "会计信息质量要求",
      "description": "可靠性、相关性、可理解性、可比性、实质重于形式、重要性、谨慎性和及时性。",
      "keywords": ["可靠性", "相关性", "可比性", "实质重于形式", "谨慎性", "及时性", "质量要求"],
      "formulas": [],
      "commonPitfalls": ["实质重于形式 vs 谨慎性的混淆"]
    },
    {
      "id": 3,
      "chapterId": 1,
      "name": "会计要素及其确认",
      "description": "资产、负债、所有者权益、收入、费用、利润六大要素的确认条件。",
      "keywords": ["资产", "负债", "所有者权益", "收入", "费用", "利润", "会计要素", "确认条件"],
      "formulas": ["资产 = 负债 + 所有者权益"],
      "commonPitfalls": ["收入确认时点 vs 收到款项时点"]
    }
  ]
}
```

- [ ] **Step 2: 跑 app 验证加载**

操作：Run 'app' → 录错题 → 选图 → 观察
预期：OCR 抽不到文字（或 RAG 跑 mock），不影响 JSON 加载。**Phase H 后** RAG 才用上 JSON。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/json/accounting_knowledge_points.json
git commit -m "feat(rag): 占位知识库 JSON (3 条样例, 待 PDF 替换)"
```

---

## Phase E — Repository

### Task 15: upsertKnowledgePoint + TDD 测试

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt`
- Create: `app/src/test/java/com/mistakenotes/data/repository/MistakeRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/mistakenotes/data/repository/MistakeRepositoryTest.kt`：

```kotlin
package com.mistakenotes.data.repository

import androidx.test.core.app.ApplicationProvider
import com.mistakenotes.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MistakeRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MistakeRepository

    @Before fun setup() {
        db = AppDatabase.createForTest(ApplicationProvider.getApplicationContext())
        repo = MistakeRepository(
            db.subjectDao(),
            db.chapterDao(),
            db.knowledgePointDao(),
            db.mistakeDao(),
            db.reviewRecordDao()
        )
    }

    @After fun teardown() { db.close() }

    @Test fun `upsertKnowledgePoint inserts new point and returns id`() = runTest {
        val id = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        assertTrue("id should be > 0", id > 0)
    }

    @Test fun `upsertKnowledgePoint dedups - second call returns same id`() = runTest {
        val id1 = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        val id2 = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        assertEquals(id1, id2)
    }

    @Test fun `upsertKnowledgePoint allows same name in different chapters`() = runTest {
        val id1 = repo.upsertKnowledgePoint(chapterId = 1L, name = "长期股权投资")
        val id2 = repo.upsertKnowledgePoint(chapterId = 6L, name = "长期股权投资")
        // 不同章节允许重名，返回不同 id
        assertTrue(id1 != id2)
    }
}
```

- [ ] **Step 2: 加 AppDatabase.createForTest 辅助方法**

打开 `app/src/main/java/com/mistakenotes/data/local/AppDatabase.kt`，在 `companion object` 块内加：

```kotlin
        /** 测试用：在内存中创建数据库（无预置 callback） */
        fun createForTest(context: android.content.Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
```

并在文件顶部加 import：

```kotlin
import androidx.room.Room
```

- [ ] **Step 3: 跑测试看是否失败**

操作：右键 → Run 'MistakeRepositoryTest'
预期：编译失败 `Unresolved reference: upsertKnowledgePoint`

- [ ] **Step 4: 在 MistakeRepository 加 upsertKnowledgePoint**

打开 `app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt`，在 `// KnowledgePoint` 区域加：

```kotlin
    /**
     * 按 (chapterId, name) 自然键 upsert 知识点
     * - 不存在则 INSERT（name + chapterId + isPreset=false）
     * - 存在则返回已有 id
     * RAG 分类后用此方法确保下拉里能看到
     */
    suspend fun upsertKnowledgePoint(chapterId: Long, name: String): Long {
        // 1) 先查
        val existing = knowledgePointDao.getAllByChapterSync(chapterId)
            .firstOrNull { it.name == name }
        if (existing != null) return existing.id
        // 2) 再插
        return knowledgePointDao.insertKnowledgePoint(
            KnowledgePointEntity(chapterId = chapterId, name = name, isPreset = false)
        )
    }
```

并在 `KnowledgePointDao` 加 `getAllByChapterSync`：

打开 `app/src/main/java/com/mistakenotes/data/local/Dao.kt`，在 `KnowledgePointDao` 接口加：

```kotlin
    @Query("SELECT * FROM knowledge_points WHERE chapterId = :chapterId ORDER BY id ASC")
    suspend fun getAllByChapterSync(chapterId: Long): List<KnowledgePointEntity>
```

- [ ] **Step 5: 重跑测试**

操作：右键 → Run 'MistakeRepositoryTest'
预期：**3 个测试都通过**

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/mistakenotes/data/repository/MistakeRepositoryTest.kt
git add app/src/main/java/com/mistakenotes/data/local/AppDatabase.kt
git add app/src/main/java/com/mistakenotes/data/repository/MistakeRepository.kt
git add app/src/main/java/com/mistakenotes/data/local/Dao.kt
git commit -m "feat(repo): upsertKnowledgePoint (自然键去重) + 测试"
```

---

## Phase F — OCR

### Task 16: OcrEngine（ML Kit 包装）

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/rag/OcrEngine.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.mistakenotes.data.rag

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 端侧中文 OCR 包装
 *
 * 端侧运行（~5MB 模型，首次使用需联网下载模型），免费，无 API Key。
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 从图片 URI 提取文字
     * @return 提取的文字（可能为空字符串）
     * @throws Exception 图片无法读取 / 模型未下载
     */
    suspend fun recognizeText(uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}
```

- [ ] **Step 2: 编译**

操作：Build → Make Project
预期：编译通过（首次 sync 后 ML Kit 库已就位）

- [ ] **Step 3: 真机手测（仅验证不挂）**

操作：Run 'app' → 进录入 → 选图 → 不报错就行（OCR 还没接入 RAG，Phase H 才用）
预期：app 不崩，图片正常显示

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mistakenotes/data/rag/OcrEngine.kt
git commit -m "feat(rag): OcrEngine ML Kit 端侧 OCR 包装"
```

---

## Phase G — DeepSeek API

### Task 17: DeepSeekApi DTOs

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/rag/DeepSeekApi.kt`

- [ ] **Step 1: 创建文件（含 DTOs + Retrofit 接口）**

```kotlin
package com.mistakenotes.data.rag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DeepSeek Chat Completions API
 *
 * 文档：https://api-docs.deepseek.com/
 */
interface DeepSeekApi {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse
}

@Serializable
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,  // 低温度让分类更稳定
    @SerialName("response_format") val responseFormat: ResponseFormat? = null
)

@Serializable
data class ChatMessage(
    val role: String,  // "system" / "user" / "assistant"
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val message: ChatMessage
)

/**
 * RAG 精排 prompt 让 LLM 输出的 JSON 结构
 */
@Serializable
data class RerankResult(
    val chapterId: Long,
    val knowledgePointId: Long,
    val confidence: Float,
    val reasoning: String = ""
)
```

- [ ] **Step 2: 编译**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/data/rag/DeepSeekApi.kt
git commit -m "feat(rag): DeepSeekApi Retrofit 接口 + DTOs"
```

---

### Task 18: ApiClient (Retrofit + OkHttp builder)

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/di/ClassifierModule.kt`

- [ ] **Step 1: 在 ClassifierModule 加 OkHttp/Retrofit provider**

打开 `ClassifierModule.kt`，加：

```kotlin
    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient =
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: okhttp3.OkHttpClient): retrofit2.Retrofit =
        retrofit2.Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(client)
            .addConverterFactory(
                kotlinx.serialization.json.Json.asConverterFactory("application/json".toMediaType())
            )
            .build()

    @Provides
    @Singleton
    fun provideDeepSeekApi(retrofit: retrofit2.Retrofit): DeepSeekApi =
        retrofit.create(DeepSeekApi::class.java)
```

- [ ] **Step 2: 加 import 到 ClassifierModule.kt**

```kotlin
import com.mistakenotes.data.rag.DeepSeekApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
```

> 注：使用 Retrofit 2.11.0 官方 `converter-kotlinx-serialization`（自 Retrofit 2.10 起官方自带，包名 `retrofit2.converter.kotlinx.serialization`），无需 Jake Wharton 第三方 converter。

> 注：`com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0` 包名是 `retrofit2.converter.kotlinx.serialization`。

- [ ] **Step 3: 编译**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mistakenotes/di/ClassifierModule.kt
git commit -m "feat(di): OkHttp + Retrofit + DeepSeekApi providers"
```

---

## Phase H — 真实分类器

### Task 19: DeepSeekKnowledgeClassifier

**Files:**
- Create: `app/src/main/java/com/mistakenotes/data/rag/DeepSeekKnowledgeClassifier.kt`

- [ ] **Step 1: 创建文件**

```kotlin
package com.mistakenotes.data.rag

import android.net.Uri
import com.mistakenotes.domain.model.Subject
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真实分类器：ML Kit OCR + 关键词召回 + DeepSeek 精排
 *
 * 流程：
 * 1. [OcrEngine] 提取题目文字（本地 0.5-2s）
 * 2. [KnowledgeBase.recall] 召回 top-5 候选（本地 50ms）
 * 3. 拼 prompt 调 [DeepSeekApi] 精排（云端 1-3s）
 * 4. 解析 JSON 返回结果
 *
 * 整链路任何异常**不抛出**，返回 [ClassifyResult.failed]。
 */
@Singleton
class DeepSeekKnowledgeClassifier @Inject constructor(
    private val ocr: OcrEngine,
    private val knowledgeBase: KnowledgeBase,
    private val deepSeekApi: DeepSeekApi,
    private val apiKeyProvider: ApiKeyProvider
) : KnowledgeClassifier {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun classify(
        questionImage: Uri,
        subjectHint: Subject?
    ): ClassifyResult {
        return try {
            // Step 1: OCR
            val text = ocr.recognizeText(questionImage)
            if (text.isBlank()) return ClassifyResult.failed("OCR 提取为空")

            // Step 2: 召回
            val candidates = knowledgeBase.recall(text, topK = 5, chapterHint = subjectHint?.id?.toLong())
            if (candidates.isEmpty()) {
                return ClassifyResult.failed("知识库未匹配到候选")
            }

            // Step 3: 拼 prompt + 调 DeepSeek
            val prompt = buildPrompt(text, candidates)
            val apiKey = apiKeyProvider.get()
            val response = deepSeekApi.chatCompletions(
                authorization = "Bearer $apiKey",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage("system", "你是 CPA 会计老师。给定题目和候选知识点，输出最相关的。"),
                        ChatMessage("user", prompt)
                    ),
                    responseFormat = ResponseFormat(type = "json_object")
                )
            )
            val rawJson = response.choices.firstOrNull()?.message?.content
                ?: return ClassifyResult.failed("DeepSeek 返回为空")

            // Step 4: 解析 JSON（容错提取 ```json 块）
            val cleanJson = extractJsonBlock(rawJson)
            val rerank = json.decodeFromString(RerankResult.serializer(), cleanJson)
            ClassifyResult(
                chapterId = rerank.chapterId,
                knowledgePointId = rerank.knowledgePointId,
                confidence = rerank.confidence,
                reasoning = rerank.reasoning
            )
        } catch (e: Exception) {
            ClassifyResult.failed(e.message ?: e::class.simpleName ?: "未知错误")
        }
    }

    private fun buildPrompt(
        questionText: String,
        candidates: List<KnowledgePointJson>
    ): String {
        val candidateList = candidates.joinToString("\n") { cp ->
            """  - id=${cp.id} chapterId=${cp.chapterId} name="${cp.name}" keywords=${cp.keywords}"""
        }
        return """
            题目内容（OCR 提取，可能有错别字）：
            ```
            $questionText
            ```

            候选知识点（top-5）：
            $candidateList

            请选择最相关的一个，输出 JSON（**只输出 JSON，不要任何其他内容**）：
            {
              "chapterId": <章节ID，从候选中选>,
              "knowledgePointId": <知识点ID，从候选中选>,
              "confidence": <0.0~1.0>,
              "reasoning": "<为什么选这个，10~30 字>"
            }
        """.trimIndent()
    }

    private fun extractJsonBlock(raw: String): String {
        // 容错：找 ```json ... ``` 块；找不到就当原文就是 JSON
        val match = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(raw)
        return match?.groupValues?.get(1) ?: raw.trim()
    }
}
```

- [ ] **Step 2: 编译**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mistakenotes/data/rag/DeepSeekKnowledgeClassifier.kt
git commit -m "feat(rag): DeepSeekKnowledgeClassifier (OCR + 召回 + 精排三步管线)"
```

---

## Phase I — 接线真实分类器

### Task 20: ImportViewModel 加 upsert 到 Room

**Files:**
- Modify: `app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt`

> **注**：Task 5 已加触发/取消/守护逻辑。本 Task 加 RAG 分类后**自动把知识点 upsert 到 Room**（这样下拉里能看到）。

- [ ] **Step 1: 修改 triggerRagClassification 的成功分支**

打开 `ImportViewModel.kt`，找到 `triggerRagClassification` 函数（Task 5 加的），把成功分支改为：

```kotlin
            if (result.isFailed) {
                _uiState.update {
                    it.copy(
                        ragStatus = RagStatus.ERROR,
                        ragErrorMessage = "AI 归类失败：${result.reasoning}，请手动选择"
                    )
                }
            } else {
                // 守护：用户可能已经手动改了下拉
                if (_uiState.value.chapterId != null) {
                    _uiState.update { it.copy(ragStatus = RagStatus.DONE) }
                    return@launch
                }
                // 自动 upsert 知识点到 Room（自然键去重）
                val kpName = lookupKnowledgePointName(result.knowledgePointId)
                val roomKpId = if (kpName != null) {
                    repository.upsertKnowledgePoint(result.chapterId, kpName)
                } else -1L
                _uiState.update {
                    it.copy(
                        chapterId = result.chapterId,
                        knowledgePointId = if (roomKpId > 0) roomKpId else null,
                        ragStatus = RagStatus.DONE
                    )
                }
                // 刷新下拉里的知识点列表
                loadKnowledgePoints(result.chapterId)
            }
```

- [ ] **Step 2: 加 lookupKnowledgePointName 辅助方法**

在文件末尾加：

```kotlin
/**
 * 从当前加载的知识库 JSON 找 JSON id 对应的 name
 * （用于 RAG 分类后 upsert 到 Room）
 */
private fun lookupKnowledgePointName(jsonId: Long): String? {
    // 知识库已通过 KnowledgeBaseLoader 加载到内存
    // 这里通过 Hilt 注入的知识库查找
    return knowledgeBase?.points?.firstOrNull { it.id == jsonId }?.name
}
```

- [ ] **Step 3: 注入 KnowledgeBase 到 ImportViewModel**

构造器加参数：

```kotlin
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,
    private val knowledgeBase: KnowledgeBase,  // ← 新增
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _pendingClassifyJob = MutableStateFlow<Job?>(null)
```

并在文件顶部 import 加：

```kotlin
import com.mistakenotes.data.rag.KnowledgeBase
```

- [ ] **Step 4: 编译**

操作：Build → Make Project
预期：编译通过

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mistakenotes/ui/screens/ImportViewModel.kt
git commit -m "feat(import): RAG 分类后自动 upsert 知识点到 Room"
```

---

### Task 21: 真机端到端验证（mock 模式）

**Files:** （无）

- [ ] **Step 1: 清空 app 数据**

操作：adb shell pm clear com.mistakenotes

- [ ] **Step 2: 跑 app + 进录入 + 选 1 张含文字的图片**

操作：Run 'app' → 录错题 → 选张含明显文字的图（如"存货"两个大字）

- [ ] **Step 3: 观察**

预期：
- 选图后 3 个下拉右侧出现小转圈（~0.8s）
- 转圈消失后，**章节下拉自动选中"第一章 总论"**（mock 返回值）
- **知识点下拉**显示刚 upsert 的"会计基本假设"（mock 用了 id=1）

- [ ] **Step 4: 选 3 张图**

操作：依次选 3 张图
预期：只有第一张触发 RAG，后两张不触发

- [ ] **Step 5: 验证 Phase I 基础完成**

预期：上述都正常。Phase I 完成。

- [ ] **Step 6:（无 commit）**

---

## Phase J — 知识库构建工具

### Task 22: Python 工具（PDF → JSON）

**Files:**
- Create: `tools/extract_pdf.py`
- Create: `tools/gen_kb.py`
- Create: `tools/merge_kb.py`

> **注**：这些脚本**不入仓**（在 `tools/` 目录加 `.gitignore`）。用户本地运行，产物手工复制到 `app/src/main/assets/json/`。

- [ ] **Step 1: 创建 tools 目录 + .gitignore**

```bash
mkdir -p tools
cat > tools/.gitignore <<'EOF'
*
!.gitignore
!extract_pdf.py
!gen_kb.py
!merge_kb.py
EOF
```

- [ ] **Step 2: 创建 extract_pdf.py**

```python
#!/usr/bin/env python3
"""extract_pdf.py: 从 CPA 会计 PDF 抽取 30 章文本

用法：
  python extract_pdf.py <input.pdf> <output_dir>

输出：output_dir/ch01.txt ~ ch30.txt
"""
import sys
from pathlib import Path
import pdfplumber

CHAPTER_PATTERN = r"第[一二三四五六七八九十百零〇]+章\s+[一-龥]+"

def split_chapters(text: str) -> list[str]:
    import re
    matches = list(re.finditer(CHAPTER_PATTERN, text))
    if len(matches) < 1:
        return [text]
    chapters = []
    for i, m in enumerate(matches):
        start = m.start()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        chapters.append(text[start:end])
    return chapters

def main():
    if len(sys.argv) != 3:
        print("用法: python extract_pdf.py <input.pdf> <output_dir>")
        sys.exit(1)
    pdf_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    with pdfplumber.open(pdf_path) as pdf:
        full_text = "\n".join(p.extract_text() or "" for p in pdf.pages)
    chapters = split_chapters(full_text)
    for i, ch in enumerate(chapters[:30], 1):
        (out_dir / f"ch{i:02d}.txt").write_text(ch, encoding="utf-8")
    print(f"已抽取 {len(chapters[:30])} 章到 {out_dir}/")

if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 创建 gen_kb.py**

```python
#!/usr/bin/env python3
"""gen_kb.py: 用 DeepSeek 把每章文本生成知识点 JSON

用法：
  DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>

需要：pip install openai
"""
import json
import os
import sys
from pathlib import Path
from openai import OpenAI

PROMPT = """你是 CPA 会计老师。从下面章节内容里抽取 5~15 个**核心知识点**。
对每个知识点输出 JSON 数组（**只输出 JSON 数组，不要任何其他内容**），
每个元素结构：
{{
  "name": "不超过 15 字",
  "description": "200~400 字, 讲清是什么/怎么做/与什么相关",
  "keywords": ["5~10 个高频术语"],
  "formulas": ["出现的核心公式, 没有就空数组"],
  "commonPitfalls": ["考生常错点, 没有就空数组"]
}}
章节内容：
{chapter_text}"""

def extract_json(raw: str) -> list:
    import re
    m = re.search(r"\[\s*\{[\s\S]*\}\s*\]", raw)
    if not m:
        raise ValueError(f"未找到 JSON 数组：\n{raw[:500]}")
    return json.loads(m.group(0))

def main():
    if len(sys.argv) != 3:
        print("用法: DEEPSEEK_KEY=sk-xxx python gen_kb.py <input_dir> <output_dir>")
        sys.exit(1)
    api_key = os.environ.get("DEEPSEEK_KEY")
    if not api_key:
        print("请设置环境变量 DEEPSEEK_KEY")
        sys.exit(1)
    in_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com")

    for ch_file in sorted(in_dir.glob("ch*.txt")):
        text = ch_file.read_text(encoding="utf-8")
        print(f"生成 {ch_file.name} ...")
        resp = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": PROMPT.format(chapter_text=text)}]
        )
        raw = resp.choices[0].message.content
        try:
            points = extract_json(raw)
        except Exception as e:
            print(f"  ⚠️ 解析失败：{e}，跳过此章")
            continue
        ch_num = int(ch_file.stem[2:])
        for p in points:
            p["chapterId"] = ch_num
        (out_dir / f"{ch_file.stem}.json").write_text(
            json.dumps(points, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"  ✓ {len(points)} 个知识点")
    print(f"完成，输出到 {out_dir}/")

if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 创建 merge_kb.py**

```python
#!/usr/bin/env python3
"""merge_kb.py: 合并 30 个章节 JSON 为单个 knowledge_points.json

用法：
  python merge_kb.py <input_dir> <output_file>

输出格式：
{
  "version": 1,
  "knowledgePoints": [
    {"id": 1, "chapterId": 1, "name": "...", ...},
    ...
  ]
}
"""
import json
import sys
from pathlib import Path

def main():
    if len(sys.argv) != 3:
        print("用法: python merge_kb.py <input_dir> <output_file>")
        sys.exit(1)
    in_dir, out_file = Path(sys.argv[1]), Path(sys.argv[2])
    merged = []
    for ch_file in sorted(in_dir.glob("ch*.json")):
        merged.extend(json.loads(ch_file.read_text(encoding="utf-8")))
    for i, item in enumerate(merged, 1):
        item["id"] = i
    out = {"version": 1, "knowledgePoints": merged}
    out_file.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"合并 {len(merged)} 个知识点到 {out_file}")

if __name__ == "__main__":
    main()
```

- [ ] **Step 5: 跑通流程（用占位 PDF 测试）**

```bash
# 找一份 CPA 会计 PDF
cp /path/to/cpa_accounting.pdf ./cpa_accounting.pdf

# Step 1: 抽章节
python tools/extract_pdf.py cpa_accounting.pdf ./out_txt

# Step 2: 生成 JSON
DEEPSEEK_KEY=sk-xxx python tools/gen_kb.py ./out_txt ./out_kb

# Step 3: 合并
python tools/merge_kb.py ./out_kb ./final.json
```

预期：`final.json` 含 150-300 个知识点。

- [ ] **Step 6: 人工 review + 替换占位 JSON**

操作：
1. 打开 `final.json`，**逐章 review**（~5 小时）
2. 改错的、补漏的、调 keywords
3. 替换 `app/src/main/assets/json/accounting_knowledge_points.json`
4. commit

- [ ] **Step 7: Commit（替换 JSON）**

```bash
git add app/src/main/assets/json/accounting_knowledge_points.json
git commit -m "feat(rag): 真实会计知识点知识库 (~N 条)"
```

---

## Phase K — 真题验证

### Task 23: 10-20 道真题端到端测试

**Files:** （无代码修改；用 docs/superpowers/test-log.md 记录结果）

- [ ] **Step 1: 准备 10-20 道会计真题图**

来源：历年 CPA 会计真题 / 模拟题。打印或截图。

- [ ] **Step 2: 录入 + 记录**

操作：对每道题：
1. 启动 app
2. 录错题 → 选该题图
3. **不要手动改下拉**——观察 RAG 自动填了什么
4. 记录到 `docs/superpowers/test-log.md`：

```markdown
| # | 题目 | 真实章/知识点 | AI 填的章/知识点 | 章节正确 | 知识点正确 |
|---|------|---------------|------------------|----------|------------|
| 1 | 存货初始计量 | 1.存货的初始计量 | 1.会计基本假设 | ❌ | ❌ |
| 2 | ... | ... | ... | ... | ... |
```

- [ ] **Step 3: 计算准确率**

- 章节准确率 = 章节正确数 / 总题数（目标 ≥ 90%）
- 知识点准确率 = 知识点正确数 / 总题数（目标 ≥ 80%）

- [ ] **Step 4: 不达标的回 review JSON**

如果知识点准确率 < 80%：
- 看错误集中在哪类知识点（如"长投权益法"反复错）
- 在 JSON 里给那类知识点**加 keywords 改写得更具体**
- 例如把 `keywords: ["长投"]` 改为 `keywords: ["长期股权投资", "权益法", "顺流", "逆流", "未实现内部交易损益"]`

- [ ] **Step 5: 重新跑（迭代）**

重复 Step 1-4 直到准确率达标。

- [ ] **Step 6: Commit test-log**

```bash
git add docs/superpowers/test-log.md
git commit -m "test: 10-20 道真题端到端验证 (章节 X%, 知识点 Y%)"
```

---

## Phase L — 文档

### Task 24: 更新 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 在"目录结构"加 RAG 相关条目**

找到 `data/repository/` 区块，**之后**加：

```markdown
│   ├── rag/
│   │   ├── KnowledgeClassifier.kt     # 分类器接口 + ClassifyResult
│   │   ├── MockKnowledgeClassifier.kt # 无 API Key 时用
│   │   ├── DeepSeekKnowledgeClassifier.kt # OCR + 召回 + DeepSeek 精排
│   │   ├── OcrEngine.kt               # ML Kit 端侧 OCR
│   │   ├── KnowledgeBase.kt           # 内存知识库 + recall()
│   │   ├── KnowledgeBaseLoader.kt     # 从 assets 加载
│   │   ├── DeepSeekApi.kt             # Retrofit + DTOs
│   │   └── ApiKeyProvider.kt          # DataStore 包装
```

找到 `ui/screens/` 区块，**之后**加：

```markdown
│   │   └── SettingsScreen.kt          # DeepSeek API Key 设置
```

- [ ] **Step 2: 在"核心功能"加 RAG 章节**

找到"## 核心功能"标题，**之后**加：

```markdown
- **AI 自动归类（会计）**：录入选图后自动跑 RAG（ML Kit OCR + 关键词召回 + DeepSeek 精排）→ 自动填科目/章节/知识点下拉；用户可手动覆盖；失败时 Snackbar 提示不影响保存。设置页填入 DeepSeek API Key 后启用。**当前仅限 CPA 会计 30 章**。
```

- [ ] **Step 3: 在"待开发功能"加 RAG 后续 Phase**

找到"## 待开发功能"表格，**更新"知识点评管理"行**：

```markdown
| **高** | RAG 扩展到其他科目 | 当前仅会计 30 章；后续扩展到审计/财管/税法/经济法/战略 |
| 高 | 知识点评 UI 增删改 | knowledge_points 表已支持，但 RAG 自动 upsert 是主要入口 |
```

- [ ] **Step 4: 在"注意事项"加 RAG 相关**

找到"## 注意事项"，**之后**加：

```markdown
- **RAG 知识库数据保护**：APK assets/json/accounting_knowledge_points.json 是只读资源；用户已录入错题 knowledgePointId 字段**绝不受 RAG 重跑影响**——RAG 只在 `addImageUri` 触发时填充；用户手动修改下拉后 RAG 不覆盖
- **DeepSeek API Key 存储**：DataStore Preferences，仅本机保留，不上传任何服务器
- **ML Kit 端侧 OCR**：首次使用需联网下载 ~5MB 中文模型，下载后完全离线
- **RAG 触发只对第一张图**：多张题图时只对 `imageUris[0]` 触发一次，避免 token 浪费
- **RAG 失败容错**：整链路（OCR → 召回 → LLM）任何异常不抛，返回 `ClassifyResult.failed(reason)`；UI 端识别后 Snackbar + 下拉留空 + 不阻塞保存
- **RAG 与用户优先级**：RAG 回来时若 `chapterId != null`（用户已手动选），则丢弃 RAG 结果——用户优先
```

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: 同步 CLAUDE.md RAG 知识库章节"
```

---

### Task 25: 整体冒烟测试

**Files:** （无）

- [ ] **Step 1: 清数据 + Run**

操作：adb shell pm clear com.mistakenotes → Run 'app'

- [ ] **Step 2: 完整录入流程**

操作：录错题 → 选图 → 等 RAG 填下拉 → 选正确选项 → 选/不选答案图片 → 改/不改下拉 → 保存

- [ ] **Step 3: 验证**

预期：
- ✅ 录错题流程完整
- ✅ 选图后 RAG 自动填（有时正确有时需要手动改——这是预期）
- ✅ 选错 API Key 时 Snackbar 提示
- ✅ 关 WiFi 时 OCR 跑但 LLM 失败
- ✅ 复习页能正常用
- ✅ 主页能正常显示

- [ ] **Step 4: 验证数据 100% 保留**

操作：对比清数据前后的错题数/复习记录数/收藏数
预期：均**完全一致**——零数据丢失

- [ ] **Step 5:（无 commit，本 Task 不写代码）**

---

## 总结

**完成检查清单**：

- [ ] Phase A: 基础架构（Tasks 1-5）
- [ ] Phase B: UI 状态显示（Tasks 6-8）
- [ ] Phase C: 设置页（Tasks 9-11）
- [ ] Phase D: 知识库内存版（Tasks 12-14）
- [ ] Phase E: Repository upsert（Task 15）
- [ ] Phase F: OCR（Task 16）
- [ ] Phase G: DeepSeek API（Tasks 17-18）
- [ ] Phase H: 真实分类器（Task 19）
- [ ] Phase I: 接线真实分类器（Tasks 20-21）
- [ ] Phase J: 知识库构建工具（Task 22）
- [ ] Phase K: 真题验证（Task 23）
- [ ] Phase L: 文档 + 冒烟（Tasks 24-25）

**关键里程碑**：
- Task 8 完成后 → **不依赖任何外部服务**的 RAG demo 跑通（Mock 模式）
- Task 19 + 21 完成后 → **接真实 DeepSeek** 的 RAG 跑通
- Task 22 + 23 完成后 → **真实知识库 + 真实题目**的端到端验证完成
- Task 25 完成后 → **正式可发布**

**预估总工时**：
- 代码（Tasks 1-21）：3-4 天
- 知识库生成 + review（Task 22）：5 小时
- 真题验证迭代（Task 23）：1-2 天
- 文档（Task 24-25）：2 小时
- **总计**：~6-7 天
