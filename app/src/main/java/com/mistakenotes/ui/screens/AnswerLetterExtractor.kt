package com.mistakenotes.ui.screens

/**
 * 从 OCR 识别的答案图片文字中，提取 A-H 的答案字母。
 *
 * 提取策略（v2——严格关键词匹配）：
 * 1) 找"正确答案：""【答案】""答案："等明确标记
 * 2) 只取标记**之后紧接着**的 30 个字符，从里面过滤 A-H 字母
 * 3) 找不到明确标记 → 返回空列表（不 fallback 到全文扫字母）
 * 4) distinct() 去重，保持出现顺序
 */
object AnswerLetterExtractor {

    // 必须是明确的答案标记："正确答案："、"【答案】"、"答案："、"答案。"、"答案 "
    private val ANSWER_MARKER = Regex("""正确答案[：:]\s*|【答案】\s*|答案[：:。，,]\s*|答案\s+""")
    private val A_H_LETTER = Regex("[A-H]")

    fun extract(text: String): List<Char> {
        if (text.isBlank()) return emptyList()

        // 1) 找答案标记
        val match = ANSWER_MARKER.find(text) ?: return emptyList()

        // 2) 取标记后的窗口（最多 30 个字符）
        val after = text.substring(match.range.last + 1)
        val window = if (after.length <= 30) after else after.take(30)

        // 3) 提取窗口内的 A-H 字母，去重
        return A_H_LETTER.findAll(window)
            .map { it.value[0] }
            .distinct()
            .toList()
    }
}
