package com.mistakenotes.ui.screens

/**
 * 从 OCR 识别的答案图片文字中，提取 A-H 的答案字母。
 *
 * 提取策略（v3——答案区截断）：
 * 1) 找"正确答案：""【答案】""答案："等明确标记
 * 2) 取标记后的文字，遇到"解析"/"。"/"\n"立即截断（防止解析区的字母混入）
 * 3) 截断后取最多 30 个字符，过滤 A-H 字母
 * 4) 找不到明确标记 → 返回空列表
 * 5) distinct() 去重，保持出现顺序
 */
object AnswerLetterExtractor {

    // 明确的答案标记
    private val ANSWER_MARKER = Regex("""正确答案[：:]\s*|【答案】\s*|答案[：:。，,]\s*|答案\s+""")
    // 截断边界：遇到这些就认为答案区结束（后面的可能是解析/说明）
    private val SECTION_END = Regex("""[。\n]|解析""")
    private val A_H_LETTER = Regex("[A-H]")

    fun extract(text: String): List<Char> {
        if (text.isBlank()) return emptyList()

        // 1) 找答案标记
        val match = ANSWER_MARKER.find(text) ?: return emptyList()

        // 2) 取标记后的文字
        val after = text.substring(match.range.last + 1)

        // 3) 遇到"解析"/"。" / 换行 立即截断
        val endMatch = SECTION_END.find(after)
        val answerOnly = if (endMatch != null) after.substring(0, endMatch.range.first) else after

        // 4) 截断后取最多 30 个字符，提取 A-H
        val window = if (answerOnly.length <= 30) answerOnly else answerOnly.take(30)

        return A_H_LETTER.findAll(window)
            .map { it.value[0] }
            .distinct()
            .toList()
    }
}
