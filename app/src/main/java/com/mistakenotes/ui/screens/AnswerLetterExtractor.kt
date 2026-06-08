package com.mistakenotes.ui.screens

/**
 * 从 OCR 识别的答案图片文字中，提取 A-H 的答案字母。
 *
 * 提取策略：
 * 1) 按句号/分号/换行切成多段，优先找包含"答案/正确答案"关键词的段
 * 2) 在该段中按非字母字符切分 token，把每个 token 拆成单字符，过滤出 A-H
 * 3) 找不到"答案"关键词时，fallback 到全文字母
 * 4) distinct() 去重，保持出现顺序
 *
 * Spec: docs/superpowers/specs/2026-06-08-answer-image-auto-fill-design.md §3.2
 */
object AnswerLetterExtractor {

    private val SEGMENT_DELIMITER = Regex("[。\n;；]")
    private val ANSWER_KEYWORD = Regex("正确答案|答\\s*案|答案|答[是为：:]")
    private val NON_LETTER = Regex("[^A-Za-z]+")

    fun extract(text: String): List<Char> {
        if (text.isBlank()) return emptyList()

        // 1) 找"答案"关键词所在的段
        val segments = text.split(SEGMENT_DELIMITER)
        val answerSegment = segments.firstOrNull { it.contains(ANSWER_KEYWORD) }
            ?: text  // fallback

        // 2) 在该段中按非字母切分，拆字符，过滤 A-H
        return answerSegment.split(NON_LETTER)
            .flatMap { token -> token.toList() }
            .filter { it in 'A'..'H' }
            .distinct()
    }
}
