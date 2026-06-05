package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TodayCardInfo(
    val mistake: Mistake,
    val isReviewed: Boolean,
    val lastResult: ReviewResult?,
    val correctCount: Int = 0,
    val subjectName: String = "",
    val chapterName: String = "",
    val subjectColor: Long = 0xFFD4A574,
    val skippedAt: Long = 0
)

data class OverdueCardInfo(
    val mistake: Mistake,
    val overdueDays: Int,
    val correctCount: Int = 0,
    val subjectName: String = "",
    val chapterName: String = "",
    val subjectColor: Long = 0xFFD4A574
)

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val todayCards: List<TodayCardInfo> = emptyList(),
    val overdueCards: List<OverdueCardInfo> = emptyList(),
    val cardSubjectIds: Set<Long> = emptySet(),
    val totalMistakes: Int = 0,
    val masteredCount: Int = 0,
    val isLoading: Boolean = true,
    val todayQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet(),
    val overdueQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Unfiltered data cached for re-filtering on subject change
    private var allTodayCards = listOf<TodayCardInfo>()
    private var allOverdueCards = listOf<OverdueCardInfo>()
    private var cachedSubjects = listOf<Subject>()
    private var cachedCardSubjIds = setOf<Long>()
    private var cachedTotalMistakes = 0
    private var cachedMastered = 0

    init {
        viewModelScope.launch {
            combine(
                repository.getAllSubjects(),
                repository.getAllMistakes(),
                repository.getAllReviewRecords(),
                repository.getAllChapters()
            ) { subjects, mistakes, reviewRecords, chapters ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val todayEnd = cal.timeInMillis - 1

                val subjectMap = subjects.associateBy { it.id }
                val chapterMap = chapters.associateBy { it.id }
                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
                val latestByMistake = reviewRecordMap.mapValues { (_, recs) ->
                    recs.maxByOrNull { it.reviewDate }
                }

                fun nameOf(subjectId: Long) = subjectMap[subjectId]?.name ?: ""
                fun chapterNameOf(chapterId: Long) = chapterMap[chapterId]?.name ?: ""
                fun colorOf(subjectId: Long) = subjectMap[subjectId]?.color ?: 0xFFD4A574

                val todayPairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    val isDueToday = next != null && next != -1L && next in todayStart..todayEnd
                    val todayRecord = reviewRecordMap[mistake.id]
                        ?.find { it.reviewDate in todayStart..todayEnd }
                    val isReviewed = todayRecord != null && todayRecord.result != ReviewResult.SKIP
                    val tomorrowStart = todayStart + 86400000L
                    val isSkippedToday = rec?.result == ReviewResult.SKIP && rec?.reviewDate in todayStart..todayEnd && rec?.nextReviewDate == tomorrowStart
                    val isSkipped = isSkippedToday
                    val isMastered = (rec?.correctCount ?: 0) >= 3
                    if ((isDueToday || isReviewed || isSkippedToday) && !isMastered) {
                        TodayCardInfo(
                            mistake, isReviewed,
                            if (isSkippedToday) ReviewResult.SKIP else todayRecord?.result,
                            correctCount = rec?.correctCount ?: 0,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId),
                            subjectColor = colorOf(mistake.subjectId),
                            skippedAt = if (isSkipped) rec?.reviewDate ?: 0 else 0
                        )
                    } else null
                }.sortedWith(
                    compareByDescending<TodayCardInfo> { it.skippedAt > 0 }
                        .thenBy { it.skippedAt }
                )

                val overduePairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    if (next != null && next != -1L && next < todayStart && (rec?.correctCount ?: 0) < 3) {
                        OverdueCardInfo(
                            mistake,
                            ((todayStart - next) / 86400000L).toInt() + 1,
                            correctCount = rec?.correctCount ?: 0,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId),
                            subjectColor = colorOf(mistake.subjectId)
                        )
                    } else null
                }.sortedByDescending { it.overdueDays }

                val mastered = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate == -1L || (rec?.correctCount ?: 0) >= 3
                }

                // Cache unfiltered data
                cachedSubjects = subjects
                allTodayCards = todayPairs
                allOverdueCards = overduePairs
                cachedCardSubjIds = (todayPairs.map { it.mistake.subjectId } + overduePairs.map { it.mistake.subjectId }).toSet()
                cachedTotalMistakes = mistakes.size
                cachedMastered = mastered

                emitFilteredState()
            }.collect()
        }
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.value = _uiState.value.copy(currentSubjectId = subjectId)
        emitFilteredState()
    }

    fun selectTodayQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
        _uiState.update { it.copy(todayQuestionTypes = types) }
        emitFilteredState()
    }

    fun selectOverdueQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
        _uiState.update { it.copy(overdueQuestionTypes = types) }
        emitFilteredState()
    }

    private fun emitFilteredState() {
        val sel = _uiState.value.currentSubjectId
        val todayTypeSel = _uiState.value.todayQuestionTypes
        val overdueTypeSel = _uiState.value.overdueQuestionTypes
        fun typeOk(qt: com.mistakenotes.domain.model.QuestionType, sel: Set<com.mistakenotes.domain.model.QuestionType>) =
            sel.isEmpty() || qt in sel

        _uiState.value = HomeUiState(
            subjects = cachedSubjects,
            currentSubjectId = sel,
            todayCards = allTodayCards
                .filter { sel == null || it.mistake.subjectId == sel }
                .filter { typeOk(it.mistake.questionType, todayTypeSel) },
            overdueCards = allOverdueCards
                .filter { sel == null || it.mistake.subjectId == sel }
                .filter { typeOk(it.mistake.questionType, overdueTypeSel) },
            cardSubjectIds = cachedCardSubjIds,
            totalMistakes = cachedTotalMistakes,
            masteredCount = cachedMastered,
            isLoading = false,
            todayQuestionTypes = todayTypeSel,
            overdueQuestionTypes = overdueTypeSel
        )
    }
}
