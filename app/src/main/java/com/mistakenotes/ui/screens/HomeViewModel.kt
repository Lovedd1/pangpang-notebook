package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val totalMistakes: Int = 0,
    val toReviewCount: Int = 0,
    val overdueCount: Int = 0,
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
                Triple(subjects, mistakes, reviewRecords)
            }.collect { (subjects, mistakes, reviewRecords) ->
                val now = System.currentTimeMillis()
                val latestByMistake = reviewRecords.groupBy { it.mistakeId }
                    .mapValues { (_, recs) -> recs.maxByOrNull { it.reviewDate } }

                val toReview = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate?.let { it != -1L && it <= now } == true
                }
                val overdue = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate?.let { it != -1L && it < now - 86400000 } == true
                }
                val mastered = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate == -1L || (rec?.correctCount ?: 0) >= 4
                }

                _uiState.value = HomeUiState(
                    subjects = subjects,
                    currentSubjectId = _uiState.value.currentSubjectId,
                    totalMistakes = mistakes.size,
                    toReviewCount = toReview,
                    overdueCount = overdue,
                    masteredCount = mastered,
                    isLoading = false
                )
            }
        }
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.value = _uiState.value.copy(currentSubjectId = subjectId)
    }
}