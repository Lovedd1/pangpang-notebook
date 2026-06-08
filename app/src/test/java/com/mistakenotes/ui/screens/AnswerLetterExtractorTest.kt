package com.mistakenotes.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerLetterExtractorTest {

    @Test
    fun `single letter after 答案：`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：A"))
    }

    @Test
    fun `multiple letters after 答案：`() {
        assertEquals(listOf('A', 'B'), AnswerLetterExtractor.extract("答案：AB"))
    }

    @Test
    fun `four letters after 正确答案：`() {
        assertEquals(
            listOf('A', 'B', 'C', 'D'),
            AnswerLetterExtractor.extract("正确答案：ABCD")
        )
    }

    @Test
    fun `【答案】bracket format`() {
        assertEquals(
            listOf('A', 'B'),
            AnswerLetterExtractor.extract("【答案】AB")
        )
    }

    @Test
    fun `答案 with space separator`() {
        assertEquals(listOf('C'), AnswerLetterExtractor.extract("答案 C"))
    }

    @Test
    fun `no answer marker returns empty`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("略"))
    }

    @Test
    fun `text without marker returns empty (no fallback)`() {
        // v2 不再 fallback 到全文扫字母
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("AB型"))
    }

    @Test
    fun `answer marker in second sentence`() {
        assertEquals(
            listOf('A'),
            AnswerLetterExtractor.extract("解析：详见解析。答案：A")
        )
    }

    @Test
    fun `digits after marker are ignored`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("答案：1+1=2"))
    }

    @Test
    fun `english text without chinese marker returns empty`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("answer: a"))
    }

    @Test
    fun `duplicate letters after marker are deduplicated`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：AA"))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract(""))
    }

    @Test
    fun `letters far from marker are within 30-char window`() {
        // 30 字符窗口内的字母才被提取
        val text = "答案：A" + "x".repeat(35) + "B"
        assertEquals(listOf('A'), AnswerLetterExtractor.extract(text))
    }

    @Test
    fun `正确答案 with colon only`() {
        assertEquals(listOf('D'), AnswerLetterExtractor.extract("正确答案:D"))
    }

    @Test
    fun `marker with trailing text after letters`() {
        // "答案：AC 解析：详见教材" → 只取 A、C，不取后面的
        assertEquals(
            listOf('A', 'C'),
            AnswerLetterExtractor.extract("答案：AC 解析：详见教材P123")
        )
    }
}
