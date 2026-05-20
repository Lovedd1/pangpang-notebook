package com.mistakenotes.ui.screens

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRound
import com.mistakenotes.ui.components.CanvasBackground
import com.mistakenotes.ui.components.PaperColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val mistakes: List<Mistake> = emptyList(),
    val currentIndex: Int = 0,
    val selectedAnswers: Set<String> = emptySet(),
    val showResult: Boolean = false,
    val currentRound: ReviewRound = ReviewRound.FIRST,
    val errorMessage: String? = null
)

data class ToolState(
    val selectedTool: String = "pen",
    val penColor: Int = Color.parseColor("#000000"),
    val penThickness: Float = 0.3f,
    val canvasBackground: CanvasBackground = CanvasBackground.BLANK,
    val paperColor: PaperColor = PaperColor.BLACK,
    val scale: Float = 1f
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    private val _toolState = MutableStateFlow(ToolState())
    val toolState: StateFlow<ToolState> = _toolState

    init {
        loadMistakes()
    }

    fun selectTool(tool: String) {
        _toolState.value = _toolState.value.copy(selectedTool = tool)
    }

    fun setPenColor(color: Int) {
        _toolState.value = _toolState.value.copy(penColor = color)
    }

    fun setPenThickness(thickness: Float) {
        _toolState.value = _toolState.value.copy(penThickness = thickness)
    }

    fun setCanvasBackground(bg: CanvasBackground) {
        _toolState.value = _toolState.value.copy(canvasBackground = bg)
    }

    fun setPaperColor(color: PaperColor) {
        _toolState.value = _toolState.value.copy(paperColor = color)
    }

    fun updateScale(scale: Float) {
        _toolState.value = _toolState.value.copy(scale = scale)
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

    fun refreshMistakes() {
        loadMistakes()
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }

    fun toggleAnswer(answer: String) {
        val current = _uiState.value.selectedAnswers
        val isMulti = _uiState.value.mistakes.getOrNull(_uiState.value.currentIndex)?.questionType == QuestionType.MULTI_CHOICE

        _uiState.value = _uiState.value.copy(
            selectedAnswers = if (isMulti) {
                // 多选：toggle
                if (current.contains(answer)) current - answer else current + answer
            } else {
                // 单选：只保留一个
                if (current.contains(answer)) emptySet() else setOf(answer)
            }
        )
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

            if (state.currentIndex < state.mistakes.size - 1) {
                _uiState.value = state.copy(
                    currentIndex = state.currentIndex + 1,
                    selectedAnswers = emptySet(),
                    showResult = false
                )
            } else {
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

            repository.skipTodayReview(currentMistake.id)

            if (state.currentIndex < state.mistakes.size - 1) {
                _uiState.value = state.copy(
                    currentIndex = state.currentIndex + 1,
                    selectedAnswers = emptySet(),
                    showResult = false
                )
            } else {
                loadMistakes()
                _uiState.value = _uiState.value.copy(currentIndex = 0)
            }
        }
    }

    fun skipTodayReview(mistakeId: Long) {
        viewModelScope.launch {
            repository.skipTodayReview(mistakeId)
            val state = _uiState.value
            val newList = state.mistakes.filterNot { it.id == mistakeId }
            _uiState.value = state.copy(mistakes = newList)
        }
    }
}