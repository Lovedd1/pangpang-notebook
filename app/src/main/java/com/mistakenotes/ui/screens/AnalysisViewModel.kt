package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectStat(
    val subject: Subject,
    val totalMistakes: Int,
    val correctCount: Int,
    val masteryRate: Float
)

data class ChapterStat(
    val chapter: Chapter,
    val mistakeCount: Int,
    val correctRate: Float,
    val subjectName: String = "",
    val subjectColor: Long = 0xFFD4A574
)

data class AnalysisUiState(
    val subjectStats: List<SubjectStat> = emptyList(),
    val chapterStats: List<ChapterStat> = emptyList(),
    val topWeakKnowledgePoints: List<Pair<KnowledgePoint, Int>> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        loadAnalysis()
    }

    private fun loadAnalysis() {
        viewModelScope.launch {
            combine(
                repository.getAllSubjects(),
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { subjects, mistakes, reviewRecords ->
                val subjectStats = calculateSubjectStats(subjects, mistakes, reviewRecords)
                val chapterStats = calculateChapterStats(subjects, mistakes, reviewRecords)
                val topWeakKnowledgePoints = calculateTopWeakKnowledgePoints(mistakes, reviewRecords)

                AnalysisUiState(
                    subjectStats = subjectStats,
                    chapterStats = chapterStats,
                    topWeakKnowledgePoints = topWeakKnowledgePoints,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private suspend fun calculateSubjectStats(
        subjects: List<Subject>,
        mistakes: List<com.mistakenotes.domain.model.Mistake>,
        reviewRecords: List<com.mistakenotes.domain.model.ReviewRecord>
    ): List<SubjectStat> {
        val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
        val mistakeMap = mistakes.groupBy { it.subjectId }

        return subjects.map { subject ->
            val subjectMistakes = mistakeMap[subject.id] ?: emptyList()
            val totalMistakes = subjectMistakes.size

            var totalCorrectCount = 0
            var totalReviews = 0

            subjectMistakes.forEach { mistake ->
                val records = reviewRecordMap[mistake.id] ?: emptyList()
                totalCorrectCount += records.count { it.result == com.mistakenotes.domain.model.ReviewResult.CORRECT }
                totalReviews += records.size
            }

            val masteryRate = if (totalReviews > 0) {
                totalCorrectCount.toFloat() / totalReviews
            } else {
                0f
            }

            SubjectStat(
                subject = subject,
                totalMistakes = totalMistakes,
                correctCount = totalCorrectCount,
                masteryRate = masteryRate
            )
        }
    }

    private suspend fun calculateChapterStats(
        subjects: List<Subject>,
        mistakes: List<com.mistakenotes.domain.model.Mistake>,
        reviewRecords: List<com.mistakenotes.domain.model.ReviewRecord>
    ): List<ChapterStat> {
        val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
        val mistakeMap = mistakes.groupBy { it.chapterId }

        val chapterStatsList = mutableListOf<ChapterStat>()

        for (subject in subjects) {
            val chapters = repository.getChaptersBySubject(subject.id).first()
            for (chapter in chapters) {
                val chapterMistakes = mistakeMap[chapter.id] ?: emptyList()
                val mistakeCount = chapterMistakes.size

                var correctCount = 0
                var totalReviews = 0

                chapterMistakes.forEach { mistake ->
                    val records = reviewRecordMap[mistake.id] ?: emptyList()
                    correctCount += records.count { it.result == com.mistakenotes.domain.model.ReviewResult.CORRECT }
                    totalReviews += records.size
                }

                val correctRate = if (totalReviews > 0) {
                    correctCount.toFloat() / totalReviews
                } else {
                    0f
                }

                if (mistakeCount > 0) {
                    chapterStatsList.add(
                        ChapterStat(
                            chapter = chapter,
                            mistakeCount = mistakeCount,
                            correctRate = correctRate,
                            subjectName = subject.name,
                            subjectColor = subject.color
                        )
                    )
                }
            }
        }

        return chapterStatsList.sortedByDescending { it.mistakeCount }
    }

    private suspend fun calculateTopWeakKnowledgePoints(
        mistakes: List<com.mistakenotes.domain.model.Mistake>,
        reviewRecords: List<com.mistakenotes.domain.model.ReviewRecord>
    ): List<Pair<KnowledgePoint, Int>> {
        val mistakeCountByKp = mistakes.groupBy { it.knowledgePointId }
            .mapValues { it.value.size }

        val kpIdWithCounts = mistakeCountByKp.entries
            .sortedByDescending { it.value }
            .take(10)

        val knowledgePoints = mutableListOf<Pair<KnowledgePoint, Int>>()

        for ((kpId, count) in kpIdWithCounts) {
            val kpIdNonNull = kpId ?: continue
            val mistake = mistakes.firstOrNull { it.knowledgePointId == kpIdNonNull }
            if (mistake != null && count > 0) {
                // 从 Room 数据库查知识点名称
                val kpName = repository.getKnowledgePointById(kpIdNonNull)?.name ?: ""
                val kp = KnowledgePoint(id = kpIdNonNull, chapterId = mistake.chapterId, name = kpName)
                knowledgePoints.add(kp to count)
            }
        }

        return knowledgePoints
    }
}
