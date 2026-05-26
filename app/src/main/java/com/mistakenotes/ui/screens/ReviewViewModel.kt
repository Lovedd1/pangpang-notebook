package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val currentMistake: Mistake? = null,
    val selectedOptionIndices: Set<Int> = emptySet(),
    val showAnswer: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctIndices: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val reviewComplete: Boolean = false
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var reviewQueue = mutableListOf<Mistake>()
    private var currentIndex = 0

    init {
        loadReviewQueue()
    }

    private fun loadReviewQueue() {
        viewModelScope.launch {
            combine(
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { mistakes, reviewRecords ->
                val now = System.currentTimeMillis()
                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }

                mistakes.filter { mistake ->
                    val records = reviewRecordMap[mistake.id] ?: emptyList()
                    val latestRecord = records.maxByOrNull { it.reviewDate }
                    val nextReview = latestRecord?.nextReviewDate
                    nextReview != null && nextReview != -1L && nextReview <= now
                }
            }.collect { queue ->
                reviewQueue = queue.toMutableList()
                reviewQueue.shuffle()
                currentIndex = 0
                if (queue.isNotEmpty()) {
                    _uiState.update {
                        it.copy(currentMistake = queue.first(), isLoading = false, reviewComplete = false)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, reviewComplete = true) }
                }
            }
        }
    }

    fun toggleOption(index: Int) {
        _uiState.update { state ->
            val mistake = state.currentMistake ?: return@update state
            val newSelected = if (mistake.questionType == QuestionType.SINGLE_CHOICE) {
                setOf(index)
            } else {
                val current = state.selectedOptionIndices
                if (index in current) current - index else current + index
            }
            state.copy(selectedOptionIndices = newSelected)
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val mistake = state.currentMistake ?: return
        val correctAnswer = mistake.correctAnswer ?: return

        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val correctIndices = correctAnswer.toCharArray()
            .mapNotNull { c -> labelLetters.indexOf(c.toString()).takeIf { it >= 0 } }
            .toSet()

        val userIndices = state.selectedOptionIndices
        val isCorrect = userIndices == correctIndices

        _uiState.update {
            it.copy(
                showAnswer = true,
                isCorrect = isCorrect,
                correctIndices = correctIndices
            )
        }

        updateReviewRecord(mistake.id, isCorrect)
    }

    fun submitEssaySelfEval(isCorrect: Boolean) {
        val mistake = _uiState.value.currentMistake ?: return
        _uiState.update { it.copy(showAnswer = true, isCorrect = isCorrect) }
        updateReviewRecord(mistake.id, isCorrect)
    }

    fun skipEssay() {
        val mistake = _uiState.value.currentMistake ?: return
        _uiState.update { it.copy(showAnswer = true, isCorrect = null) }
        updateReviewRecord(mistake.id, false)
    }

    private fun updateReviewRecord(mistakeId: Long, isCorrect: Boolean) {
        viewModelScope.launch {
            val records = repository.getReviewRecordsByMistake(mistakeId).first()
            val latestRecord = records.maxByOrNull { it.reviewDate }

            val newCorrectCount = if (isCorrect) (latestRecord?.correctCount ?: 0) + 1 else 0
            val nextReviewDate = calculateNextReviewDate(newCorrectCount, isCorrect)

            val newRecord = ReviewRecord(
                id = latestRecord?.id ?: 0,
                mistakeId = mistakeId,
                reviewDate = System.currentTimeMillis(),
                result = if (isCorrect) ReviewResult.CORRECT else ReviewResult.WRONG,
                nextReviewDate = nextReviewDate,
                correctCount = newCorrectCount
            )
            repository.insertReviewRecord(newRecord)
        }
    }

    private fun calculateNextReviewDate(correctCount: Int, isCorrect: Boolean): Long {
        val now = System.currentTimeMillis()
        val oneDay = 86400000L
        return when {
            !isCorrect -> now + oneDay
            correctCount == 1 -> now + oneDay
            correctCount == 2 -> now + (3 * oneDay)
            correctCount == 3 -> now + (7 * oneDay)
            correctCount >= 4 -> -1
            else -> now + oneDay
        }
    }

    fun nextMistake() {
        currentIndex++
        if (currentIndex < reviewQueue.size) {
            _uiState.update {
                ReviewUiState(currentMistake = reviewQueue[currentIndex], isLoading = false)
            }
        } else {
            _uiState.update { it.copy(reviewComplete = true, currentMistake = null) }
        }
    }
}
