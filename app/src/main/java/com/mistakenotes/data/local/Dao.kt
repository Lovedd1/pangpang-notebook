package com.mistakenotes.data.local

import androidx.room.*
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.Review
import com.mistakenotes.domain.model.Subject

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes ORDER BY createdAt DESC")
    suspend fun getAllMistakes(): List<Mistake>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun getMistakeById(id: Long): Mistake?

    @Query("SELECT * FROM mistakes WHERE subject = :subject ORDER BY createdAt DESC")
    suspend fun getMistakesBySubject(subject: String): List<Mistake>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: Mistake): Long

    @Update
    suspend fun updateMistake(mistake: Mistake)

    @Delete
    suspend fun deleteMistake(mistake: Mistake)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE mistakeId = :mistakeId ORDER BY reviewedAt DESC")
    suspend fun getReviewsForMistake(mistakeId: Long): List<Review>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review): Long

    @Query("SELECT * FROM reviews WHERE mistakeId IN (SELECT id FROM mistakes WHERE subject = :subject)")
    suspend fun getReviewsForSubject(subject: String): List<Review>
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    suspend fun getAllSubjects(): List<Subject>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)
}