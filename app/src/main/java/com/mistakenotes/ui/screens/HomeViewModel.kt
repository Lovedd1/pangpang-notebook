package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        combine(
            repository.getAllSubjects(),
            repository.getAllMistakes(),
            repository.getAllReviewRecords()
        ) { subjects, mistakes, reviewRecords ->
            val currentSubjectId = _uiState.value.currentSubjectId

            val filteredMistakes = if (currentSubjectId != null) {
                mistakes.filter { it.subjectId == currentSubjectId }
            } else {
                mistakes
            }

            val now = System.currentTimeMillis()

            // Calculate review stats based on Ebbinghaus
            val toReview = reviewRecords.filter { record ->
                record.nextReviewDate?.let { it <= now } ?: false
            }.size

            val overdue = reviewRecords.filter { record ->
                record.nextReviewDate?.let { it < now - 86400000 } ?: false
            }.size

            val mastered = reviewRecords.filter { it.correctCount >= 4 }.size

            HomeUiState(
                subjects = subjects,
                currentSubjectId = currentSubjectId,
                totalMistakes = filteredMistakes.size,
                toReviewCount = toReview,
                overdueCount = overdue,
                masteredCount = mastered,
                isLoading = false
            )
        }.launchIn(viewModelScope)
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.update { it.copy(currentSubjectId = subjectId) }
        loadData()
    }
}