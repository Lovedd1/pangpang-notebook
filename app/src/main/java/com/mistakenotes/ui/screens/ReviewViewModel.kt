package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRound
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val mistakes: List<Mistake> = emptyList(),
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val showResult: Boolean = false,
    val currentRound: ReviewRound = ReviewRound.FIRST,
    val errorMessage: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    init {
        loadMistakes()
    }

    fun loadMistakes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val mistakes = repository.getAllMistakes()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mistakes = mistakes
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }

    fun setSelectedAnswer(answer: String?) {
        _uiState.value = _uiState.value.copy(selectedAnswer = answer)
    }

    fun setShowResult(show: Boolean) {
        _uiState.value = _uiState.value.copy(showResult = show)
    }

    fun submitAnswer() {
        _uiState.value = _uiState.value.copy(showResult = true)
    }

    fun markAnswer(isCorrect: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val currentMistake = state.mistakes.getOrNull(state.currentIndex) ?: return@launch

            repository.recordReview(
                mistakeId = currentMistake.id,
                round = state.currentRound,
                isCorrect = isCorrect,
                isSkipped = false
            )

            // Move to next question
            if (state.currentIndex < state.mistakes.size - 1) {
                _uiState.value = state.copy(
                    currentIndex = state.currentIndex + 1,
                    selectedAnswer = null,
                    showResult = false
                )
            } else {
                // Review complete - reload
                loadMistakes()
                _uiState.value = _uiState.value.copy(currentIndex = 0)
            }
        }
    }

    fun skipQuestion() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentMistake = state.mistakes.getOrNull(state.currentIndex) ?: return@launch

            repository.recordReview(
                mistakeId = currentMistake.id,
                round = state.currentRound,
                isCorrect = null,
                isSkipped = true
            )

            if (state.currentIndex < state.mistakes.size - 1) {
                _uiState.value = state.copy(
                    currentIndex = state.currentIndex + 1,
                    selectedAnswer = null,
                    showResult = false
                )
            } else {
                loadMistakes()
                _uiState.value = _uiState.value.copy(currentIndex = 0)
            }
        }
    }
}