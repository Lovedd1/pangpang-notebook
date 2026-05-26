package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewRecord
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
    val lastResult: ReviewResult?
)

data class OverdueCardInfo(
    val mistake: Mistake,
    val overdueDays: Int
)

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val todayCards: List<TodayCardInfo> = emptyList(),
    val overdueCards: List<OverdueCardInfo> = emptyList(),
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
                repository.getAllReviewRecords()
            ) { subjects, mistakes, reviewRecords ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val todayEnd = cal.timeInMillis - 1

                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
                val latestByMistake = reviewRecordMap.mapValues { (_, recs) ->
                    recs.maxByOrNull { it.reviewDate }
                }

                // Today's cards: nextReviewDate within today [todayStart, todayEnd]
                val todayPairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    if (next != null && next != -1L && next in todayStart..todayEnd) {
                        // Check if reviewed today
                        val todayRecord = reviewRecordMap[mistake.id]
                            ?.find { it.reviewDate in todayStart..todayEnd }
                        val isReviewed = todayRecord != null
                        mistake to TodayCardInfo(mistake, isReviewed, todayRecord?.result)
                    } else null
                }

                // Overdue cards: nextReviewDate < todayStart
                val overduePairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    if (next != null && next != -1L && next < todayStart) {
                        val overdueDays = ((todayStart - next) / 86400000L).toInt() + 1
                        mistake to OverdueCardInfo(mistake, overdueDays)
                    } else null
                }.sortedByDescending { it.second.overdueDays }

                // Mastered: nextReviewDate == -1 or correctCount >= 4
                val mastered = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate == -1L || (rec?.correctCount ?: 0) >= 4
                }

                HomeUiState(
                    subjects = subjects,
                    currentSubjectId = _uiState.value.currentSubjectId,
                    todayCards = todayPairs.map { it.second },
                    overdueCards = overduePairs.map { it.second },
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
