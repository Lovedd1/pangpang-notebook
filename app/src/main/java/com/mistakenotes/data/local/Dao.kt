package com.mistakenotes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY id ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("UPDATE subjects SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: Long)

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Long): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY id ASC")
    fun getAllChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE subjectId = :subjectId ORDER BY `order` ASC")
    fun getChaptersBySubject(subjectId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)
}

@Dao
interface KnowledgePointDao {
    @Query("SELECT * FROM knowledge_points WHERE chapterId = :chapterId ORDER BY id ASC")
    fun getKnowledgePointsByChapter(chapterId: Long): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_points WHERE id = :id")
    suspend fun getKnowledgePointById(id: Long): KnowledgePointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgePoint(knowledgePoint: KnowledgePointEntity): Long

    @Update
    suspend fun updateKnowledgePoint(knowledgePoint: KnowledgePointEntity)

    @Delete
    suspend fun deleteKnowledgePoint(knowledgePoint: KnowledgePointEntity)
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC")
    fun getAllMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE subjectId = :subjectId ORDER BY createdAt DESC")
    fun getMistakesBySubject(subjectId: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE chapterId = :chapterId ORDER BY createdAt DESC")
    fun getMistakesByChapter(chapterId: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE knowledgePointId = :knowledgePointId ORDER BY createdAt DESC")
    fun getMistakesByKnowledgePoint(knowledgePointId: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE isTop = 1 ORDER BY createdAt DESC")
    fun getTopMistakes(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun getMistakeById(id: Long): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeEntity): Long

    @Update
    suspend fun updateMistake(mistake: MistakeEntity)

    @Delete
    suspend fun deleteMistake(mistake: MistakeEntity)
}

@Dao
interface ReviewRecordDao {
    @Query("SELECT * FROM review_records WHERE mistakeId = :mistakeId ORDER BY reviewDate DESC")
    fun getReviewRecordsByMistake(mistakeId: Long): Flow<List<ReviewRecordEntity>>

    @Query("SELECT * FROM review_records ORDER BY reviewDate DESC")
    fun getAllReviewRecords(): Flow<List<ReviewRecordEntity>>

    @Query("SELECT * FROM review_records WHERE nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime ORDER BY nextReviewDate ASC")
    fun getReviewRecordsDue(currentTime: Long): Flow<List<ReviewRecordEntity>>

    @Query("SELECT * FROM review_records WHERE id = :id")
    suspend fun getReviewRecordById(id: Long): ReviewRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewRecord(reviewRecord: ReviewRecordEntity): Long

    @Update
    suspend fun updateReviewRecord(reviewRecord: ReviewRecordEntity)

    @Delete
    suspend fun deleteReviewRecord(reviewRecord: ReviewRecordEntity)

    @Query("DELETE FROM review_records WHERE mistakeId = :mistakeId")
    suspend fun deleteByMistakeId(mistakeId: Long)
}