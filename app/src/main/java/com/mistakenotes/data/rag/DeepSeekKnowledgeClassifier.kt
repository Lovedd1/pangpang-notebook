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
                            ChatMessage("system", "你是 CPA 会计老师。根据候选知识点判断题目属于哪个章节。\n\n⚠️ 核心原则：题目的章节归属由「题干预交易的会计处理」决定，不由「选项中出现的术语」决定。\n例：选项说\"作为或有事项披露\"≠题目属于第12章——要看题干的核心交易是什么。\n\n【Ch6 长期股权投资 vs Ch13 金融工具的边界铁则】（重要！2026-06-10 修复）\n- Ch6 长期股权投资处置的核心场景：子公司/联营/合营企业股权出售、持股比例变化、丧失控制/共同控制/重大影响、个别报表层面核算方法转换（成本法↔权益法↔金融资产三向转换）、分次转让+临时过户+无表决权或利润分配权（实质重于形式）、出售部分股权后剩余股权按金融资产分类。\n- Ch13 金融资产终止确认的核心场景：应收/应付账款保理、应收票据贴现、信贷资产证券化、过手安排测试、风险报酬转移的金融工具判断。\n- ⚠️ '题干是长期股权投资处置（出售联营/子公司股权）+ 选项中有非交易性权益工具投资/其他综合收益转入留存收益/金融资产分类原则'→ 核心交易是 Ch6 长期股权投资处置，选项中 Ch13 概念是辅助细节——选 Ch6，不选 Ch13 KP[122]（非交易性权益工具投资指定）。\n- 题面同时出现'股权转让/过户/表决权/利润分配权/风险报酬/未满足终止确认条件'时，先判断核心主体：\n  - 主体是股权（长期股权投资，子公司/联营/合营）→ Ch6\n  - 主体是金融工具（应收/应付/票据/信贷资产/保理）→ Ch13\n- ⚠️ '临时过户但无表决权或利润分配权'是 Ch6 实质重于形式的核心考点，**不是** Ch13 终止确认。'出售部分股权+剩余股权按金融资产计量'是 Ch6 核算方法转换的核心考点，不是 Ch13 终止确认。\n\n第13章（金融工具）的题型特征：题目在问\"分类为/确认为/属于什么\"或\"确认和计量规则是否正确\"。\n包括：金融资产三分类、金融负债vs权益工具区分、金融工具初始计量、摊余成本/FVOCI/FVTPL 后续计量、预期信用损失减值、可转债拆分与转股、交易费用计入原则。\n\n第12章（或有事项）的题型特征：题目在问\"是否确认预计负债/是否披露或有事项\"。\n包括：预计负债确认条件（现时义务+很可能+可靠计量）、未决诉讼/产品质量保证/亏损合同、或有资产不予确认仅披露。\n\n第16章（所有者权益）的题型特征：题目在问\"金额计算/科目入账\"且涉及资本公积/其他综合收益/留存收益。\n\n判别铁则：题干问\"会计处理正确/错误的是\"→看主要交易的章节归属，不被选项中的术语带偏。题干中涉及应收/应付/票据/信贷资产/保理/过手安排→Ch13；涉及子公司/联营/合营股权出售/持股比例变化/控制权变化→Ch6。\n\n【Ch6 vs Ch26 vs Ch27 边界铁则】（2026-06-10 修复）\n- Ch6 KP[47]（非同一控制下企业合并初始计量）关注个别财务报表层面：长期股权投资的确认时点、控制权取得判断（董事会改组/派出董事/监管批文/财产交接/工商登记/股东变更≠控制权取得日）、初始投资成本。\n- Ch26（企业合并）关注合并层面：购买日条件/合并成本与商誉/反向购买/或有对价。\n- Ch27（合并财务报表）关注合并抵消与合并工作底稿：合并抵消分录/少数股东权益/内部交易抵消。\n- 判别：题干问'长期股权投资的确认时点/控制权取得日'且包含董事会改组/派出过半数/工商变更/过渡期损益→Ch6 KP[47]；题干问'购买日条件/合并成本与商誉/反向购买/合并抵消'→Ch26/Ch27。\n\n【Ch6 权益法内部交易 vs Ch27 合并报表内部交易的边界铁则】（2026-06-10 修复）\n- Ch6 KP[50]（权益法后续计量-损益调整）关注联营/合营企业投资方的内部交易抵销：顺流交易（投资方→被投资方存货出资/出售）、逆流交易（被投资方→投资方购入作为固定资产/存货）。关键词：权益法/顺流/逆流/未实现内部交易损益/合并报表抵销。\n- Ch27（内部商品/固定资产交易的合并处理）关注合并工作底稿层面的母子/子子公司间全额抵销：营业收入×营业成本互相抵销、未实现损益在控股股东与少数股东间分摊。\n- 判别铁则：题干核心主体是'联营/合营企业（持股20%-50%）采用权益法核算'→ Ch6 KP[50]；题干核心主体是'母子公司（持股>50%）合并工作底稿'→ Ch27。题干出现'存货出资设立联营企业/从联营企业购入产品作为固定资产'→ Ch6 权益法内部交易抵销。"),
                            ChatMessage("user",prompt)
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
