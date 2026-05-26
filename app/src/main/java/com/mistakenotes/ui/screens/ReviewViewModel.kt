package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.ui.canvas.VectorStroke
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val currentMistake: Mistake? = null,
    val showQuestion: Boolean = true,
    val showAnswer: Boolean = false,
    val showReference: Boolean = false,
    val selectedAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val isLoading: Boolean = true,
    val reviewComplete: Boolean = false,
    val canvasStrokes: List<VectorStroke> = emptyList()
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
        android.util.Log.d("ReviewVM", "ReviewViewModel created, starting loadReviewQueue")
        loadReviewQueue()
    }

    private fun loadReviewQueue() {
        android.util.Log.d("ReviewVM", "loadReviewQueue started")
        viewModelScope.launch {
            combine(
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { mistakes, reviewRecords ->
                android.util.Log.d("ReviewVM", "combine transform started")
                val now = System.currentTimeMillis()
                android.util.Log.d("ReviewVM", "now=$now")
                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
                android.util.Log.d("ReviewVM", "reviewRecordMap has ${reviewRecordMap.size} entries")

                val filtered = mistakes.filter { mistake ->
                    val records = reviewRecordMap[mistake.id] ?: emptyList()
                    val latestRecord = records.maxByOrNull { it.reviewDate }
                    val nextReview = latestRecord?.nextReviewDate
                    val shouldInclude = nextReview != null && nextReview != -1L && nextReview <= now
                    android.util.Log.d("ReviewVM", "mistake ${mistake.id}: nextReview=${nextReview}, shouldInclude=$shouldInclude")
                    shouldInclude
                }.also { queue ->
                    android.util.Log.d("ReviewVM", "filtered queue size: ${queue.size}")
                    reviewQueue = queue.toMutableList()
                    reviewQueue.shuffle()
                    currentIndex = 0
                    android.util.Log.d("ReviewVM", "reviewQueue shuffled, first item: ${reviewQueue.firstOrNull()?.id}")
                }
                android.util.Log.d("ReviewVM", "combine transform returning queue of size ${filtered.size}")
                filtered
            }.collect { queue ->
                android.util.Log.d("ReviewVM", "COLLECT called with queue size: ${queue.size}")
                if (queue.isNotEmpty()) {
                    android.util.Log.d("ReviewVM", "setting currentMistake to ${queue.first().id}")
                    _uiState.update {
                        it.copy(
                            currentMistake = queue.first(),
                            isLoading = false,
                            reviewComplete = false
                        )
                    }
                    android.util.Log.d("ReviewVM", "uiState after update: ${_uiState.value.currentMistake?.id}, loading=${_uiState.value.isLoading}")
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, reviewComplete = true)
                    }
                }
            }
        }
    }

    fun submitAnswer(answer: String) {
        val currentMistake = _uiState.value.currentMistake ?: return
        val correctAnswer = currentMistake.correctAnswer ?: return
        val isCorrect = answer.equals(correctAnswer, ignoreCase = true)

        _uiState.update {
            it.copy(
                selectedAnswer = answer,
                isCorrect = isCorrect,
                showAnswer = true
            )
        }

        updateReviewRecord(currentMistake.id, isCorrect)
    }

    fun submitEssayAnswer(score: Int?) {
        val currentMistake = _uiState.value.currentMistake ?: return
        _uiState.update {
            it.copy(
                showReference = true,
                showAnswer = true
            )
        }
        updateReviewRecord(currentMistake.id, score != null && score >= 60)
    }

    private fun updateReviewRecord(mistakeId: Long, isCorrect: Boolean) {
        viewModelScope.launch {
            val records = repository.getReviewRecordsByMistake(mistakeId).first()
            val latestRecord = records.maxByOrNull { it.reviewDate }

            val newCorrectCount = if (isCorrect) {
                (latestRecord?.correctCount ?: 0) + 1
            } else {
                0
            }

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

    fun calculateNextReviewDate(correctCount: Int, isCorrect: Boolean): Long {
        val now = System.currentTimeMillis()
        val oneDay = 86400000L

        return when {
            !isCorrect -> now + oneDay // Wrong: 1 day
            correctCount == 1 -> now + oneDay // First correct: 1 day
            correctCount == 2 -> now + (3 * oneDay) // Second correct: 3 days
            correctCount == 3 -> now + (7 * oneDay) // Third correct: 7 days
            correctCount >= 4 -> -1 // Mastered
            else -> now + oneDay
        }
    }

    fun nextMistake() {
        currentIndex++
        if (currentIndex < reviewQueue.size) {
            _uiState.update {
                it.copy(
                    currentMistake = reviewQueue[currentIndex],
                    showQuestion = true,
                    showAnswer = false,
                    showReference = false,
                    selectedAnswer = null,
                    isCorrect = null,
                    canvasStrokes = emptyList()
                )
            }
        } else {
            _uiState.update {
                it.copy(reviewComplete = true, currentMistake = null)
            }
        }
    }

    fun onStrokeCompleted(stroke: VectorStroke) {
        _uiState.update {
            it.copy(canvasStrokes = it.canvasStrokes + stroke)
        }
    }
}