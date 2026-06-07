package com.mistakenotes.data.repository

import androidx.test.core.app.ApplicationProvider
import com.mistakenotes.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MistakeRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MistakeRepository

    @Before fun setup() {
        db = AppDatabase.createForTest(ApplicationProvider.getApplicationContext())
        repo = MistakeRepository(
            db.subjectDao(),
            db.chapterDao(),
            db.knowledgePointDao(),
            db.mistakeDao(),
            db.reviewRecordDao()
        )
    }

    @After fun teardown() { db.close() }

    @Test fun `upsertKnowledgePoint inserts new point and returns id`() = runTest {
        val id = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        assertTrue("id should be > 0", id > 0)
    }

    @Test fun `upsertKnowledgePoint dedups - second call returns same id`() = runTest {
        val id1 = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        val id2 = repo.upsertKnowledgePoint(chapterId = 1L, name = "存货的初始计量")
        assertEquals(id1, id2)
    }

    @Test fun `upsertKnowledgePoint allows same name in different chapters`() = runTest {
        val id1 = repo.upsertKnowledgePoint(chapterId = 1L, name = "长期股权投资")
        val id2 = repo.upsertKnowledgePoint(chapterId = 6L, name = "长期股权投资")
        // 不同章节允许重名，返回不同 id
        assertTrue(id1 != id2)
    }
}
