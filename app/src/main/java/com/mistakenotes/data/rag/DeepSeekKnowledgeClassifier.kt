package com.mistakenotes.data.rag

import android.net.Uri
import android.util.Log
import com.mistakenotes.domain.model.Subject
import kotlinx.serialization.json.Json
import retrofit2.HttpException
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
            Log.d("RAG", "OCR 提取: $text")
            if (text.isBlank()) return ClassifyResult.failed("OCR 提取为空")

            // Step 2: 召回（topK=7 给跨章节更多候选）
            val candidates = knowledgeBase.recall(text, topK = 7, chapterHint = subjectHint?.id?.toLong())
            Log.d("RAG", "召回 top-${candidates.size}: ${candidates.joinToString { "${it.name}(Ch${it.chapterId})" }}")
            if (candidates.isEmpty()) {
                return ClassifyResult.failed("知识库未匹配到候选")
            }

            // Step 3: 拼 prompt + 调 DeepSeek
            val apiKey = apiKeyProvider.get().trim()
            if (apiKey.isBlank()) {
                return ClassifyResult.failed("未设置 API Key，请在设置页填入")
            }
            val prompt = buildPrompt(text, candidates)
            Log.d("RAG", "DeepSeek 调用 (prompt=${text.length}字, cand=${candidates.size})")
            val response = try {
                deepSeekApi.chatCompletions(
                    authorization = "Bearer $apiKey",
                    request = ChatRequest(
                        messages = listOf(
                            ChatMessage("system", "你是 CPA 会计老师。根据候选知识点判断题目属于哪个章节。\n\n第13章（金融工具）的题型特征：题目在问\"分类为/确认为/属于什么\"或\"表述是否正确（关于确认和计量规则）\"。\n具体包括：金融资产三分类判断、金融负债vs权益工具的区分、优先股/永续债应分类为负债还是权益、金融工具减值（预期信用损失）、金融资产转移/终止确认、可转换债券初始拆分与转股处理（含转股时资本公积计算）、金融工具交易费用计入原则、金融资产重分类规则。\n\n第16章（所有者权益）的题型特征：题目在问\"金额是多少/入什么科目\"且涉及资本公积/其他综合收益/留存收益的核算。\n具体包括：权益工具股利分配的利润分配处理、库存股回购的权益变动、权益工具发行费用冲减资本公积、其他综合收益的类别和重分类。\n\n判别铁则：凡是问\"应该分类为什么/确认为什么类型/是否正确（关于分类与确认规则）\"→第13章。凡是问\"金额如何计算/科目如何入账（关于权益类科目的核算）\"→可能是第16章。题目中出现\"所有者权益\"≠第16章，出现\"资本公积\"≠一定是第16章，要看题目的核心是\"判断\"还是\"计算\"。"),
                            ChatMessage("user", prompt)
                        )
                    )
                )
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                Log.e("RAG", "DeepSeek HTTP ${e.code()}: $errorBody")
                return ClassifyResult.failed("DeepSeek ${e.code()}: ${errorBody.take(100)}")
            }
            val rawJson = response.choices.firstOrNull()?.message?.content
                ?: return ClassifyResult.failed("DeepSeek 返回为空")
            Log.d("RAG", "DeepSeek 返回: $rawJson")

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
        } catch (e: HttpException) {
            // 已在上面处理，这里兜底
            ClassifyResult.failed("DeepSeek HTTP ${e.code()}")
        } catch (e: Exception) {
            Log.e("RAG", "分类异常", e)
            ClassifyResult.failed("${e::class.simpleName}: ${e.message}".take(100))
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

            ⚠️ 常见跨章节陷阱（多选题/判断题更容易误判）：
            - 优先股/永续债涉及「应分类为金融负债还是权益工具」→ 第13章（金融工具），不是第16章
            - 优先股/永续债涉及「分类后的股利处理/发行费用/重分类计量」→ 第16章（所有者权益）
            - 题目中出现"所有者权益"字眼不代表题目属于第16章——如果核心是判断分类标准，仍属第13章

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
