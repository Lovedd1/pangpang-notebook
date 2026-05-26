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
    val subjectName: String = "",
    val chapterName: String = ""
)

data class OverdueCardInfo(
    val mistake: Mistake,
    val overdueDays: Int,
    val subjectName: String = "",
    val chapterName: String = ""
)

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val todayCards: List<TodayCardInfo> = emptyList(),
    val overdueCards: List<OverdueCardInfo> = emptyList(),
    val todaySubjectIds: Set<Long> = emptySet(),
    val totalMistakes: Int = 0,
    val masteredCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

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

                // Today's cards: due today OR reviewed today
                val todayPairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    val isDueToday = next != null && next != -1L && next in todayStart..todayEnd
                    val todayRecord = reviewRecordMap[mistake.id]
                        ?.find { it.reviewDate in todayStart..todayEnd }
                    val isReviewed = todayRecord != null && todayRecord.result != ReviewResult.SKIP
                    if (isDueToday || isReviewed) {
                        mistake to TodayCardInfo(
                            mistake, isReviewed, todayRecord?.result,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId)
                        )
                    } else null
                }

                // Overdue cards: nextReviewDate < todayStart
                val overduePairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    if (next != null && next != -1L && next < todayStart) {
                        val overdueDays = ((todayStart - next) / 86400000L).toInt() + 1
                        mistake to OverdueCardInfo(
                            mistake, overdueDays,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId)
                        )
                    } else null
                }.sortedByDescending { it.second.overdueDays }

                val mastered = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate == -1L || (rec?.correctCount ?: 0) >= 4
                }

                val todayList = todayPairs.map { it.second }
                val overdueList = overduePairs.map { it.second }
                val todaySubjIds = todayList.map { it.mistake.subjectId }.toSet()

                val selSubject = _uiState.value.currentSubjectId
                val filteredToday = if (selSubject != null)
                    todayList.filter { it.mistake.subjectId == selSubject } else todayList
                val filteredOverdue = if (selSubject != null)
                    overdueList.filter { it.mistake.subjectId == selSubject } else overdueList

                HomeUiState(
                    subjects = subjects,
                    currentSubjectId = _uiState.value.currentSubjectId,
                    todayCards = filteredToday,
                    overdueCards = filteredOverdue,
                    todaySubjectIds = todaySubjIds,
                    totalMistakes = mistakes.size,
                    masteredCount = mastered,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.value = _uiState.value.copy(currentSubjectId = subjectId)
    }
}
