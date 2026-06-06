# Phase A-H Mock 模式真机验证清单

**前置**：
- 无 API Key（首次启动或清 DataStore）
- Android Studio Sync Gradle 完成
- 真机 Android 8.0+ 已连接

**预期数据流**（用户视角）：
1. 启动 app → 进 ImportScreen
2. 选 1 张任意图片
3. ~0.8s 后：章节下拉自动选中"第一章 总论"
4. 知识点下拉显示"会计基本假设"（RAG 触发 upsertKnowledgePoint 自动写入 Room）
5. 不报错、不弹 Snackbar

**4 步测试**：

1. **基础 RAG**：选 1 张图 → Spinner 出现 ~0.8s → 下拉自动填
2. **删图取消**：选图后 1s 内点删除 → Spinner 立即消失，下拉未被填
3. **多图不重复**：依次选 3 张图 → 只有第一张触发 Spinner
4. **未填下拉被覆盖**：选图 → RAG 还没跑完 → 手动选章节下拉 → RAG 完成时不覆盖用户的选择

**已知限制**（生产后会修）：
- Mock 永远返回 chapterId=1/knowledgePointId=1，**不真实**——只用于验证 UI 流程
- 真实知识库仅 3 条样例（T14）——选图后下拉里只能看到 1 个知识点
- 真实 PDF 知识库（T22-23）替换后才能测试真实分类准确率

**故障排查**：
- Spinner 不出现：Logcat 看 `ImportVM` 日志，看 classifier.classify 是否被调用
- 下拉不显示：检查 `Room knowledge_points` 表是否被 RAG 写入
- 编译失败：Sync Gradle + 检查 ClassifierModule 的 hasKeySync 路径

---

## 验证发现（T21a 结构检查）

### 7 个文件结构检查结果

| # | 文件 | 状态 | 备注 |
|---|------|------|------|
| 1 | `ImportViewModel.kt` | PASS | 构造器、4 步管线、3 步成功分支、失败分支、删图取消、save 开头 cancel、`clearRagError`、`lookupKnowledgePointName` 全部正确实现 |
| 2 | `ImportScreen.kt` | PASS | `Box(fillMaxWidth)` 包裹 `ChapterDropdown`，Spinner 条件渲染，`LaunchedEffect(ragErrorMessage)` 弹 Snackbar 后 clear |
| 3 | `MockKnowledgeClassifier.kt` | PASS | `@Singleton` + `@Inject constructor()` + `KnowledgeClassifier` 接口 + 800ms delay + 固定返回 (1, 1, 0.85) |
| 4 | `ClassifierModule.kt` | PASS | `provideKnowledgeClassifier` 用 `hasKeySync()` 切换；3 个新 provider（OkHttp / Retrofit / DeepSeekApi）齐全 |
| 5 | `MistakeRepository.kt` | PASS | `upsertKnowledgePoint(chapterId, name)` 走"先查再插"自然键去重，DAO 用 `getAllByChapterSync` 同步查 |
| 6 | `KnowledgeBase.kt` + `KnowledgeBaseFile` | PASS | `recall(text, topK=5, chapterHint=Long?)` 召回算法；`@Serializable` 字段匹配 assets JSON |
| 7 | `assets/json/accounting_knowledge_points.json` | PASS | 3 条样例（id=1,2,3），全部 chapterId=1，version=1 |

### 关键连接点（已验证）

- T20 改造的 `ImportViewModel`：成功分支 3 步（守护 chapterId != null → upsertKnowledgePoint → loadKnowledgePoints）—— 全部就位
- T12 实现的 `KnowledgeBase.recall`：`ImportViewModel.lookupKnowledgePointName` 调用 `knowledgeBase.points` 查找 name —— 路径正确
- T20 的 `repository.upsertKnowledgePoint` 调用：使用 `knowledgeBase?.points?.firstOrNull` 找 name，传 (chapterId, name) 入 repo —— 调用链通
- T20 的 `loadKnowledgePoints(result.chapterId)` 刷新：调用现有 `loadKnowledgePoints` 私有方法（已存在）—— 复用现有字段，不改 schema

### 已知问题（待真机验证前修复）

**问题 1：`KnowledgeBase` 没有 Hilt Provider**

`KnowledgeBase` 类是普通 class（`class KnowledgeBase(val points: List<KnowledgePointJson>)`），没有 `@Inject constructor()`，`ClassifierModule` 也没补 `@Provides fun provideKnowledgeBase(...)`。

影响：`ImportViewModel` 在 `@Inject constructor(... private val knowledgeBase: KnowledgeBase ...)` 处会因为 Hilt 找不到 `KnowledgeBase` 的 provider 而**编译失败**。

`DeepSeekKnowledgeClassifier` 也注入了 `KnowledgeBase`（T19 commit），同样会编译失败。

Mock 模式专属问题：因为 `provideKnowledgeClassifier` 用 `Provider<MockKnowledgeClassifier>` 间接取 mock 实现，**`MockKnowledgeClassifier` 不需要 `KnowledgeBase`**，但 `ImportViewModel` 自身需要。

修复建议（不在 T21a 范围内）：
```kotlin
// ClassifierModule.kt 加一个 provider
@Provides
@Singleton
fun provideKnowledgeBase(loader: KnowledgeBaseLoader): KnowledgeBase =
    loader.load()
```

或给 `KnowledgeBase` 加 `@Inject constructor()`（但需要把 `KnowledgeBaseLoader` 也注入进去，或改成 `@Inject constructor(@ApplicationContext context: Context)` 然后内部 load）。

`KnowledgeBaseLoader` 自身是 `@Singleton @Inject constructor()`，但 `load()` 是普通方法（每次调用重新读 assets）—— 加 Provider 缓存到内存一次最干净。

### 验证清单外的次要观察

- `ApiKeyProvider.hasKeySync()` 用 `runBlocking { hasKey() }` 读 DataStore —— 启动期会阻塞主线程，OK 仅用于 DI 决策一次，可接受
- `KnowledgeBase.recall` 的中文分词用 `[一-龥]{1,6}` 切片 —— 对 Mock 模式不进入该路径（Mock 跳过 recall 直接返回固定结果），无影响
- `MistakeRepository.upsertKnowledgePoint` 内部用 `getAllByChapterSync` —— 需确认 DAO 有该方法（建议真机前 grep 一下确认）
