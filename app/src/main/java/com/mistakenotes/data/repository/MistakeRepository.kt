package com.mistakenotes.data.repository

import com.mistakenotes.data.local.MistakeDao
import com.mistakenotes.data.local.ReviewDao
import com.mistakenotes.data.local.SubjectDao
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.Review
import com.mistakenotes.domain.model.ReviewRound
import com.mistakenotes.domain.model.Subject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistakeRepository @Inject constructor(
    private val mistakeDao: MistakeDao,
    private val reviewDao: ReviewDao,
    private val subjectDao: SubjectDao
) {
    // 错题 CRUD
    suspend fun getAllMistakes(): List<Mistake> = mistakeDao.getAllMistakes()
    suspend fun getMistakeById(id: Long): Mistake? = mistakeDao.getMistakeById(id)
    suspend fun insertMistake(mistake: Mistake): Long = mistakeDao.insertMistake(mistake)
    suspend fun updateMistake(mistake: Mistake) = mistakeDao.updateMistake(mistake)
    suspend fun deleteMistake(mistake: Mistake) = mistakeDao.deleteMistake(mistake)

    // 复习记录
    suspend fun recordReview(mistakeId: Long, round: ReviewRound, isCorrect: Boolean?, isSkipped: Boolean = false) {
        reviewDao.insertReview(Review(
            mistakeId = mistakeId,
            round = round,
            isCorrect = isCorrect,
            isSkipped = isSkipped
        ))
        // 更新错误次数
        if (isCorrect == false) {
            mistakeDao.getMistakeById(mistakeId)?.let { mistake ->
                mistakeDao.updateMistake(mistake.copy(wrongCount = mistake.wrongCount + 1))
            }
        }
    }

    // 跳过今日复习（不影响后续轮次）
    suspend fun skipTodayReview(mistakeId: Long) {
        mistakeDao.getMistakeById(mistakeId)?.let { mistake ->
            mistakeDao.updateMistake(mistake.copy(skipToday = true))
        }
    }

    // 科目管理
    suspend fun getAllSubjects(): List<Subject> = subjectDao.getAllSubjects()
    suspend fun insertSubject(subject: Subject) = subjectDao.insertSubject(subject)
}