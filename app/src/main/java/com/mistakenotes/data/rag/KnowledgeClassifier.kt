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
 * 分类结果（方案B：支持跨章节占比）
 *
 * @param chapterId 主章节 ID；`< 0` 表示失败
 * @param knowledgePointId 主知识点 ID
 * @param confidence 0~1；`< 0.5` 时 UI 高亮"建议复核"
 * @param reasoning 分类原因
 * @param secondaryChapterId 次章节 ID（跨章节时有值，null 表示纯单章）
 * @param secondaryKpId 次知识点 ID
 * @param chapterProportion 主章节占比 0.5~1.0（1.0=纯单章）
 */
data class ClassifyResult(
    val chapterId: Long,
    val knowledgePointId: Long,
    val confidence: Float,
    val reasoning: String = "",
    val secondaryChapterId: Long? = null,
    val secondaryKpId: Long? = null,
    val chapterProportion: Double = 1.0
) {
    val isFailed: Boolean get() = chapterId < 0
    /** 是否为跨章节分类（有次章节且主章占比 < 0.85） */
    val isCrossChapter: Boolean get() = secondaryChapterId != null && chapterProportion < 0.85

    companion object {
        fun failed(reason: String): ClassifyResult = ClassifyResult(
            chapterId = -1,
            knowledgePointId = -1,
            confidence = 0f,
            reasoning = reason
        )
    }
}
