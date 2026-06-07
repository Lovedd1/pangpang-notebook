package com.mistakenotes.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordRecallTest {

    private val samplePoints = listOf(
        KnowledgePointJson(
            id = 1, chapterId = 1, name = "存货的初始计量",
            description = "存货成本包括采购成本、加工成本",
            keywords = listOf("存货", "初始计量", "采购成本", "加工成本"),
            formulas = emptyList(), commonPitfalls = emptyList()
        ),
        KnowledgePointJson(
            id = 2, chapterId = 6, name = "权益法下顺逆流交易",
            description = "权益法核算长期股权投资",
            keywords = listOf("长投", "权益法", "顺流", "逆流", "未实现内部交易"),
            formulas = emptyList(), commonPitfalls = emptyList()
        ),
        KnowledgePointJson(
            id = 3, chapterId = 2, name = "存货的期末计量",
            description = "成本与可变现净值孰低",
            keywords = listOf("存货", "可变现净值", "跌价准备"),
            formulas = listOf("成本 - 可变现净值 = 跌价准备"),
            commonPitfalls = emptyList()
        )
    )

    private val base = KnowledgeBase(samplePoints)

    @Test
    fun `recall returns top-K sorted by score desc`() {
        val results = base.recall("长投权益法顺逆流交易", topK = 3)
        assertEquals(3, results.size)
        assertEquals(2L, results.first().id)  // 知识点 2 最相关
    }

    @Test
    fun `recall empty text returns empty list`() {
        val results = base.recall("", topK = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `recall respects chapterHint - filters to specific chapter`() {
        val results = base.recall("存货", topK = 5, chapterHint = 1L)
        // 章节 1 下的"存货的初始计量"应排第一
        assertEquals(1L, results.first().id)
    }

    @Test
    fun `recall formulas get weight boost`() {
        val base = KnowledgeBase(listOf(
            KnowledgePointJson(1, 1, "成本计算", "desc",
                keywords = listOf("成本"),
                formulas = listOf("成本 - 可变现净值 = 跌价准备"),
                commonPitfalls = emptyList()),
            KnowledgePointJson(2, 1, "普通知识点", "desc",
                keywords = listOf("成本"),
                formulas = emptyList(),
                commonPitfalls = emptyList())
        ))
        val results = base.recall("成本 可变现净值 跌价准备", topK = 2)
        // 知识点 1 含公式，应排第一
        assertEquals(1L, results.first().id)
    }
}
