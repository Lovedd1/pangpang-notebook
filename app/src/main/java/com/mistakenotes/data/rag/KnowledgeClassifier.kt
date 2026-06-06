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
