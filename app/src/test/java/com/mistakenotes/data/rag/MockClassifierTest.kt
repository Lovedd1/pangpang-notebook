package com.mistakenotes.data.rag

import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockClassifierTest {

    @Test
    fun `mock classifier returns chapter 1 knowledge point 1 with high confidence`() = runTest {
        val mock = MockKnowledgeClassifier()
        val result = mock.classify(Uri.EMPTY)
        assertEquals(1L, result.chapterId)
        assertEquals(1L, result.knowledgePointId)
        assertTrue(result.confidence > 0.5f)
        assertTrue(!result.isFailed)
    }

    @Test
    fun `mock classifier has 800ms delay simulating network`() = runBlocking {
        val mock = MockKnowledgeClassifier()
        val start = System.currentTimeMillis()
        mock.classify(Uri.EMPTY)
        val elapsed = System.currentTimeMillis() - start
        // 允许 ±100ms 误差（用 runBlocking 而非 runTest，因为 runTest 用虚拟时间会让 delay 瞬间完成）
        assertTrue("Expected ~800ms, got ${elapsed}ms", elapsed in 700..1500)
    }
}
