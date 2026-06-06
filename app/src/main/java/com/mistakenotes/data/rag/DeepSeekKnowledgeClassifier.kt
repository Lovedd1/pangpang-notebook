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

            // Step 2: 召回
            val candidates = knowledgeBase.recall(text, topK = 5, chapterHint = subjectHint?.id?.toLong())
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

            // Step 4: 解析 JSON（容错提取 ```json 块）
            val cleanJson = extractJsonBlock(rawJson)
            val rerank = json.decodeFromString(RerankResult.serializer(), cleanJson)
            ClassifyResult(
                chapterId = rerank.chapterId,
                knowledgePointId = rerank.knowledgePointId,
                confidence = rerank.confidence,
                reasoning = rerank.reasoning
            )
        } catch (e: Exception) {
            ClassifyResult.failed(e.message ?: e::class.simpleName ?: "未知错误")
        }
    }

    private fun buildPrompt(
        questionText: String,
        candidates: List<KnowledgePointJson>
    ): String {
        val candidateList = candidates.joinToString("\n") { cp ->
            """  - id=${cp.id} chapterId=${cp.chapterId} name="${cp.name}" keywords=${cp.keywords}"""
        }
        return """
            题目内容（OCR 提取，可能有错别字）：
            ```
            $questionText
            ```

            候选知识点（top-5）：
            $candidateList

            请选择最相关的一个，输出 JSON（**只输出 JSON，不要任何其他内容**）：
            {
              "chapterId": <章节ID，从候选中选>,
              "knowledgePointId": <知识点ID，从候选中选>,
              "confidence": <0.0~1.0>,
              "reasoning": "<为什么选这个，10~30 字>"
            }
        """.trimIndent()
    }

    private fun extractJsonBlock(raw: String): String {
        // 容错：找 ```json ... ``` 块；找不到就当原文就是 JSON
        val match = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(raw)
        return match?.groupValues?.get(1) ?: raw.trim()
    }
}
