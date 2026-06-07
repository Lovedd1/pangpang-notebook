# Phase A+B 真机验证清单

**前置条件**：
- Android Studio 已 Sync Gradle
- 真机已连接（Android 8.0+ / API 26+）
- 已有错题可参考（看 HomeScreen）

**测试步骤**：

1. **基础功能**：启动 app → 点"录入" → 进 ImportScreen
2. **选图触发**：点"题目图片"+"+" → 从相册选任意一张图
   - 预期：选图后 3 个下拉右侧出现小转圈（AmberGold 颜色）
   - 预期：~0.8s 后转圈消失，**章节下拉自动选中"第一章 总论"**
   - 预期：知识点下拉显示"会计基本假设"（如果有）
3. **删除取消**：选图后 2s 内点图片右上角删除
   - 预期：Spinner 消失，下拉未被自动填
4. **多图不重复触发**：依次选 3 张图
   - 预期：只有第 1 张触发 Spinner，后续选图无 Spinner

**故障排查**：
- 如果 Spinner 不出现：检查 Android Studio Logcat 看 classifier 调用是否启动
- 如果下拉不自动填：检查 `triggerRagClassification` 守护条件（imageUris[0] == uri）
- 如果编译失败：先 Sync Gradle（File → Sync Project with Gradle Files）

**数据保护验证**：
- 跑完后检查主页/错题浏览，确认旧数据 100% 保留
