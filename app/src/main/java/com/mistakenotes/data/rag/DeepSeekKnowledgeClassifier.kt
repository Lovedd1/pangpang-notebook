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

            // Step 2: 召回（topK=7 给跨章节更多候选）
            val candidates = knowledgeBase.recall(text, topK = 7, chapterHint = subjectHint?.id?.toLong())
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

            // Step 4: 解析 JSON（方案B：primary/secondary + 占比 + 兼容旧格式）
            val cleanJson = extractJsonBlock(rawJson)
            val rerank = json.decodeFromString(RerankResult.serializer(), cleanJson)

            // 主章节：优先取 primary，兼容旧格式
            val primary = rerank.primary
            val (chId, kpId, proportion) = if (primary != null) {
                Triple(primary.chapterId, primary.knowledgePointId, primary.proportion)
            } else {
                Triple(rerank.chapterId, rerank.knowledgePointId, 1.0)
            }

            // 次章节
            val secondary = rerank.secondary
            val secCh = secondary?.chapterId
            val secKp = secondary?.knowledgePointId

            // 校验：用知识库实际 chapterId 覆盖（防止 DeepSeek 幻觉）
            val actualCh = knowledgeBase.points.find { it.id == kpId }?.chapterId ?: chId

            ClassifyResult(
                chapterId = actualCh,
                knowledgePointId = kpId,
                confidence = rerank.confidence,
                reasoning = rerank.reasoning,
                secondaryChapterId = secCh,
                secondaryKpId = secKp,
                chapterProportion = proportion
            )
        } catch (e: Exception) {
            ClassifyResult.failed(e.message ?: e::class.simpleName ?: "未知错误")
        }
    }

    private val CHAPTER_NAMES = mapOf(
        1 to "总论(会计信息质量/基本假设/要素)", 2 to "存货", 3 to "固定资产", 4 to "无形资产", 5 to "投资性房地产",
        6 to "长期股权投资", 7 to "资产减值", 8 to "负债", 9 to "职工薪酬", 10 to "股份支付",
        11 to "借款费用", 12 to "或有事项", 13 to "金融工具", 14 to "租赁",
        15 to "持有待售/终止经营", 16 to "所有者权益(含其他综合收益)", 17 to "收入/费用/利润", 18 to "政府补助",
        19 to "所得税", 20 to "非货币性资产交换", 21 to "债务重组", 22 to "外币折算",
        23 to "财务报告/现金流量表", 24 to "会计政策/估计变更", 25 to "资产负债表日后事项",
        26 to "企业合并", 27 to "合并财务报表", 28 to "每股收益", 29 to "公允价值计量",
        30 to "政府及民间非营利组织会计"
    )

    private fun buildPrompt(
        questionText: String,
        candidates: List<KnowledgePointJson>
    ): String {
        val candidateList = candidates.joinToString("\n") { cp ->
            "- id=${cp.id} 第${cp.chapterId}章(${CHAPTER_NAMES[cp.chapterId.toInt()] ?: "?"}) ${cp.name}\n  关键词: ${cp.keywords}"
        }
        return """
            你是 CPA 会计老师。根据题目内容判断这道题**主要属于哪个章节**。
            若题目涉及跨章节，选占比>50%的章节作为 primary。

            题目：
            $questionText

            候选知识点：
            $candidateList

            JSON（只输出 JSON）：
            格式A（单章节>85%）：
            {"primary":{"knowledgePointId":<id>,"chapterId":<id>,"proportion":1.0},"secondary":null,"confidence":<0.0~1.0>,"reasoning":"<主要章节>"}

            格式B（跨章节）：
            {"primary":{"knowledgePointId":<主id>,"chapterId":<主ch>,"proportion":<0.50~0.85>},"secondary":{"knowledgePointId":<次id>,"chapterId":<次ch>,"proportion":<余>},"confidence":<0.0~1.0>,"reasoning":"<主次占比依据>"}
        """.trimIndent()
    }

    private fun extractJsonBlock(raw: String): String {
        // 容错：找 ```json ... ``` 块；找不到就当原文就是 JSON
        val match = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(raw)
        return match?.groupValues?.get(1) ?: raw.trim()
    }
}
