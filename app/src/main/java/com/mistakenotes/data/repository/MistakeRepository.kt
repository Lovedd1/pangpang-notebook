package com.mistakenotes.data.repository

import com.mistakenotes.data.local.*
import com.mistakenotes.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistakeRepository @Inject constructor(
    private val subjectDao: SubjectDao,
    private val chapterDao: ChapterDao,
    private val knowledgePointDao: KnowledgePointDao,
    private val mistakeDao: MistakeDao,
    private val reviewRecordDao: ReviewRecordDao
) {
    // Subject
    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    suspend fun getSubjectById(id: Long): Subject? = subjectDao.getById(id)?.toDomain()
    suspend fun insertSubject(subject: Subject): Long = subjectDao.insert(subject.toEntity())
    suspend fun deleteSubject(subject: Subject) = subjectDao.delete(subject.toEntity())

    // Chapter
    fun getChaptersBySubject(subjectId: Long): Flow<List<Chapter>> =
        chapterDao.getBySubject(subjectId).map { list -> list.map { it.toDomain() } }
    suspend fun insertChapter(chapter: Chapter): Long = chapterDao.insert(chapter.toEntity())

    // KnowledgePoint
    fun getKnowledgePointsByChapter(chapterId: Long): Flow<List<KnowledgePoint>> =
        knowledgePointDao.getByChapter(chapterId).map { list -> list.map { it.toDomain() } }
    suspend fun insertKnowledgePoint(kp: KnowledgePoint): Long = knowledgePointDao.insert(kp.toEntity())

    // Mistake
    fun getAllMistakes(): Flow<List<Mistake>> = mistakeDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    fun getMistakesBySubject(subjectId: Long): Flow<List<Mistake>> =
        mistakeDao.getBySubject(subjectId).map { list -> list.map { it.toDomain() } }
    suspend fun getMistakeById(id: Long): Mistake? = mistakeDao.getById(id)?.toDomain()
    suspend fun insertMistake(mistake: Mistake): Long = mistakeDao.insert(mistake.toEntity())
    suspend fun updateMistake(mistake: Mistake) = mistakeDao.update(mistake.toEntity())
    suspend fun deleteMistake(mistake: Mistake) = mistakeDao.delete(mistake.toEntity())

    // ReviewRecord
    fun getReviewRecordsByMistake(mistakeId: Long): Flow<List<ReviewRecord>> =
        reviewRecordDao.getByMistake(mistakeId).map { list -> list.map { it.toDomain() } }
    fun getAllReviewRecords(): Flow<List<ReviewRecord>> = reviewRecordDao.getAll().map { list ->
        list.map { it.toDomain() }
    }
    suspend fun insertReviewRecord(record: ReviewRecord): Long = reviewRecordDao.insert(record.toEntity())
}

// Extension functions for mapping
fun SubjectEntity.toDomain() = Subject(id, name, color)
fun Subject.toEntity() = SubjectEntity(id, name, color)

fun ChapterEntity.toDomain() = Chapter(id, subjectId, name, order)
fun Chapter.toEntity() = ChapterEntity(id, subjectId, name, order)

fun KnowledgePointEntity.toDomain() = KnowledgePoint(id, chapterId, name, isPreset)
fun KnowledgePoint.toEntity() = KnowledgePointEntity(id, chapterId, name, isPreset)

fun MistakeEntity.toDomain() = Mistake(
    id, title, subjectId, chapterId, knowledgePointId,
    QuestionType.valueOf(questionType), questionImagePath, questionText, options,
    correctAnswer, explanation, referenceAnswer, createdAt, isFavorite, isTop
)
fun Mistake.toEntity() = MistakeEntity(
    id, title, subjectId, chapterId, knowledgePointId,
    questionType.name, questionImagePath, questionText, options,
    correctAnswer, explanation, referenceAnswer, createdAt, isFavorite, isTop
)

fun ReviewRecordEntity.toDomain() = ReviewRecord(
    id, mistakeId, reviewDate, ReviewResult.valueOf(result), score, nextReviewDate, correctCount
)
fun ReviewRecord.toEntity() = ReviewRecordEntity(
    id, mistakeId, reviewDate, result.name, score, nextReviewDate, correctCount
)