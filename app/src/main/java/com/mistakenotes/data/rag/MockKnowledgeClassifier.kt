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
