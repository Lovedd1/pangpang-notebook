package com.mistakenotes.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerLetterExtractorTest {

    @Test
    fun `single letter after answer keyword`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：A"))
    }

    @Test
    fun `multiple letters after answer keyword`() {
        assertEquals(listOf('A', 'B'), AnswerLetterExtractor.extract("答案：AB"))
    }

    @Test
    fun `four letters in correct order`() {
        assertEquals(
            listOf('A', 'B', 'C', 'D'),
            AnswerLetterExtractor.extract("正确答案：ABCD")
        )
    }

    @Test
    fun `no letters returns empty list`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("略"))
    }

    @Test
    fun `letters inside other words are extracted (AB型)`() {
        assertEquals(listOf('A', 'B'), AnswerLetterExtractor.extract("AB型"))
    }

    @Test
    fun `answer segment in second sentence is used`() {
        assertEquals(
            listOf('A'),
            AnswerLetterExtractor.extract("解析：详见解析。答案：A")
        )
    }

    @Test
    fun `digits are ignored`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("答案：1+1=2"))
    }

    @Test
    fun `lowercase letters are ignored`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract("answer: a"))
    }

    @Test
    fun `duplicate letters are deduplicated`() {
        assertEquals(listOf('A'), AnswerLetterExtractor.extract("答案：AA"))
    }

    @Test
    fun `empty string returns empty list`() {
        assertEquals(emptyList<Char>(), AnswerLetterExtractor.extract(""))
    }
}
