# Phase L 整体冒烟测试说明

**前置**：
- Android Studio 已 Sync Gradle
- 真机 Android 8.0+ 已连接
- 测试人员 1 名 + CPA 会计真题 3-5 张
- （可选）DeepSeek API Key 已填设置页

## 完整流程测试

### 1. 启动验证（无 API Key 状态）
- [ ] 启动 app 不崩
- [ ] 进设置页 → 显示"未配置 API Key"
- [ ] 进录入页 → 3 个下拉（科目/章节/知识点）初始为空
- [ ] **预期**：所有原有功能（主页/复习/分析/浏览）正常

### 2. Mock 模式 RAG（无 API Key）
- [ ] 录入 → 选 1 张图
- [ ] 选图后 ~0.8s 章节下拉自动选中"第一章 总论"
- [ ] 知识点下拉显示"会计基本假设"
- [ ] **预期**：整个流程无 Snackbar，无崩溃

### 3. RAG 边界 case
- [ ] 选图后立刻点删除 → Spinner 消失，下拉未被填
- [ ] 依次选 3 张图 → 只有第 1 张触发 Spinner
- [ ] 选图后立刻手动改下拉 → RAG 完成后不覆盖
- [ ] **预期**：用户优先级生效

### 4. RAG 失败路径
- [ ] 关 WiFi → 选图 → ~10s 后 Snackbar "AI 归类失败：xxx，请手动选择"
- [ ] 3 个下拉仍可手动选
- [ ] 保存错题能成功
- [ ] **预期**：失败容错完整，不阻塞保存

### 5. 数据保护（强制）
- [ ] 跑前：记录错题数 N1、收藏数 F1、置顶数 T1
- [ ] 跑上面 1-4 步
- [ ] 跑后：错题数 = N1，收藏数 = F1，置顶数 = T1（**完全一致**）
- [ ] **预期**：**100% 数据保留**，零丢失

### 6. 真实 RAG（有 API Key）
- [ ] 设置页填入 DeepSeek API Key → 保存 → 重启 app
- [ ] 选 1 张会计真题图
- [ ] ~5-15s 后下拉被填
- [ ] **预期**：真实分类生效（用 §Phase K 测试日志模板记录）

## 故障排查

- **app 启动崩**：检查 `MistakeNotesApp` 是否继承 `Application`（应该已有）
- **编译失败**：Hilt 注入错——检查 `provideKnowledgeBase` 是否在 ClassifierModule
- **Spinner 不出现**：Logcat 看 `ImportViewModel` 的分类器调用
- **下拉不显示**：检查 `Room knowledge_points` 表是否被 upsert
- **数据丢失**：**立即停止**！检查最近 commit 是否有 schema 变更

## 验证完成标准

全部 6 个测试模块通过 + 数据 100% 保留 = RAG feature 可发布。
