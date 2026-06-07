package com.mistakenotes.data.rag

import kotlin.math.ln
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
 * 内存中加载好的知识库（带 IDF 权重）
 *
 * - [recall] 方法做关键词召回（IDF 加权 + 子串匹配）
 * - 加载一次缓存在 SingletonComponent 作用域
 * - IDF 使罕见关键词（如"谨慎性"）得高分，通用词（如"资产"）得低分
 */
class KnowledgeBase(val points: List<KnowledgePointJson>) {

    /** IDF 权重表：关键词 → 逆文档频率。基于全文本（name+desc+keywords）计算 */
    private val idf: Map<String, Double>

    init {
        val N = points.size.toDouble()
        // 1) 为每个 KP 构建全文本（用于子串统计）
        val fullTexts = points.map { kp ->
            buildString {
                append(kp.name).append(' ')
                append(kp.description).append(' ')
                append(kp.keywords.joinToString(" "))
            }
        }
        // 2) 收集所有独特关键词
        val allKeywords = points.flatMap { it.keywords }.toSet()
        // 3) 对每个关键词，统计它作为子串出现在多少个 KP 的全文本中
        idf = allKeywords.associateWith { kw ->
            val df = fullTexts.count { kw in it }
            // IDF = ln((N + 0.5) / (df + 0.5))，+0.5 平滑防止罕见词过拟合
            ln((N + 0.5) / (df + 0.5))
        }
    }

    /**
     * 关键词召回（IDF 加权）
     *
     * @param text OCR 提取出的题目文字
     * @param topK 返回 top-K 个候选
     * @param chapterHint 已知章节时传入，只在该章节下召回
     * @return 按得分降序排列的 top-K 知识点
     */
    fun recall(text: String, topK: Int = 5, chapterHint: Long? = null): List<KnowledgePointJson> {
        if (text.isBlank()) return emptyList()

        return points
            .asSequence()
            .filter { chapterHint == null || it.chapterId == chapterHint }
            .map { kp -> kp to scoreOf(kp, text) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
            .toList()
    }

    /**
     * IDF 加权得分：对 KP 的每个关键词，若在题目文本中出现，则累加其 IDF 权重。
     * 罕见关键词（如"谨慎性"IDF≈4.8）贡献远大于通用词（如"资产"IDF≈0.4）。
     */
    private fun scoreOf(kp: KnowledgePointJson, questionText: String): Double {
        var score = 0.0
        for (kw in kp.keywords) {
            if (kw in questionText) {
                score += idf[kw] ?: 3.0  // 回退：无 IDF 时用默认权重
            }
        }
        // formula / pitfall 不加 IDF（它们在知识库中本身是全文匹配，权重适中即可）
        for (f in kp.formulas) {
            if (f.isNotBlank() && f in questionText) {
                score += 1.6  // formula 基础权重 (原 2.0 × 0.8)
            }
        }
        for (p in kp.commonPitfalls) {
            if (p.isNotBlank() && p in questionText) {
                score += 0.9  // pitfall 基础权重 (原 1.5 × 0.6)
            }
        }
        return score
    }
}
