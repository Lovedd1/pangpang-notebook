# Phase K 真题测试操作说明

**前置**：
- 完成 T22（Python 工具已就位）
- 用户已提供 CPA 会计 PDF
- 用户已 review 知识库 JSON 并替换 `app/src/main/assets/json/accounting_knowledge_points.json`
- 用户已在设置页填入 DeepSeek API Key

## 操作步骤

1. **准备真题**：从历年 CPA 会计真题集找 10-20 道题，**打印或截图**到手机相册
2. **启动 app**：Run 'app' → 进设置 → 填入 DeepSeek API Key → 保存
3. **逐题测试**：
   - 录错题 → 选该题图
   - **不要手动改下拉**——等 RAG 自动填
   - 记录到 `docs/superpowers/rag-real-sample-test-log.md`：
     - 题目描述
     - 真实章/知识点（你查教材）
     - AI 填的章/知识点（看 RAG 跑完后的下拉）
     - 是否正确
4. **统计准确率**：
   - 章节准确率 = 章节正确数 / 总题数（**目标 ≥ 90%**）
   - 知识点准确率 = 知识点正确数 / 总题数（**目标 ≥ 80%**）
5. **不达标的迭代**：
   - 看错误集中在哪类知识点（如"长投权益法"反复错）
   - 改 `accounting_knowledge_points.json` 加更具体的 keywords
   - 重新跑 app 验证
6. **达标后**：commit `accounting_knowledge_points.json` 替换

## 故障排查

- **AI 总是填同一章**：知识库 keywords 太宽，给每条知识点加 5-10 个 CPA 习惯叫法
- **AI 填的章与真实章差 1-2 章**：OCR 抽字错别字导致召回偏——属于 OCR 模型问题，可换张清晰图重试
- **AI 全部返回失败**：检查 API Key 有效性 + DeepSeek 服务是否可达
- **AI 返回空字符串**：OCR 抽不到文字（图片太小/太模糊）——重拍更清晰的图

## 性能基准

- 单题 RAG 响应时间（中位数）：**目标 ≤ 8s**（OCR 1-2s + 召回 0.1s + DeepSeek 2-3s）
- 10 道题总耗时：≤ 80s
- 知识库加载时间：≤ 100ms（启动时）
