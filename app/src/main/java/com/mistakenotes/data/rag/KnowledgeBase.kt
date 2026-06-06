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
