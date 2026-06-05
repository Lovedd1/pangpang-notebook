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

    private val _reviewQueue = MutableStateFlow<List<Mistake>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)
    val reviewQueueFlow: StateFlow<List<Mistake>> = _reviewQueue.asStateFlow()
    val currentIndexFlow: StateFlow<Int> = _currentIndex.asStateFlow()
    val queueSize: Int get() = _reviewQueue.value.size
    val reviewedResultsMap: Map<Int, Boolean> get() = reviewedResults.toMap()

    private val reviewedIndices = mutableSetOf<Int>()
    private val reviewedResults = mutableMapOf<Int, Boolean>() // index -> isCorrect

    init {
        loadReviewQueue()
    }

    private fun loadReviewQueue() {
        viewModelScope.launch {
            if (!ReviewSession.isEmpty) {
                // Use pre-set review session (from HomeScreen)
                _reviewQueue.value = ReviewSession.queue.toMutableList()
                _currentIndex.value = ReviewSession.startIndex.coerceIn(0, _reviewQueue.value.size - 1)
                val viewResult = ReviewSession.isViewingResult
                ReviewSession.clear()

                if (_reviewQueue.value.isNotEmpty()) {
                    val mistake = _reviewQueue.value[_currentIndex.value]
                    if (viewResult) {
                        // Show previous result directly
                        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                        val correctIndices = (mistake.correctAnswer ?: "").map { c ->
                            labelLetters.indexOf(c.toString())
                        }.filter { it >= 0 }.toSet()
                        val resultIsCorrect = when (ReviewSession.lastResult) {
                            ReviewResult.CORRECT -> true
                            ReviewResult.WRONG -> false
                            else -> null
                        }
                        _uiState.update {
                            it.copy(
                                currentMistake = mistake,
                                isLoading = false,
                                showAnswer = true,
                                correctIndices = correctIndices,
                                isCorrect = resultIsCorrect
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(currentMistake = mistake, isLoading = false)
                        }
                    }
                }
            } else {
                // Fallback: load all today's cards
                val queue = combine(
                    repository.getAllMistakes(),
                    repository.getAllReviewRecords()
                ) { mistakes, reviewRecords ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val todayStart = cal.timeInMillis
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    val todayEnd = cal.timeInMillis - 1

                    val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }

                    mistakes.filter { mistake ->
                        val records = reviewRecordMap[mistake.id] ?: emptyList()
                        val latestRecord = records.maxByOrNull { it.reviewDate }
                        val nextReview = latestRecord?.nextReviewDate
                        nextReview != null && nextReview != -1L && nextReview in todayStart..todayEnd
                    }
                }.first()

                _reviewQueue.value = queue.toMutableList()
                _reviewQueue.value = _reviewQueue.value.shuffled()
                _currentIndex.value = 0
                if (_reviewQueue.value.isNotEmpty()) {
                    _uiState.update {
                        it.copy(currentMistake = _reviewQueue.value.first(), isLoading = false, reviewComplete = false)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, reviewComplete = true) }
                }
            }
        }
    }

    private fun loadMistakeAtCurrentIndex() {
        if (_reviewQueue.value.isEmpty()) return
        val mistake = _reviewQueue.value[_currentIndex.value]
        val isPreReviewed = _currentIndex.value in ReviewSession.preReviewedIndices
        if (_currentIndex.value in reviewedIndices || isPreReviewed) {
            // Already reviewed — show result
            val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
            val correctIndices = (mistake.correctAnswer ?: "").map { c ->
                labelLetters.indexOf(c.toString())
            }.filter { it >= 0 }.toSet()
            _uiState.update {
                it.copy(
                    currentMistake = mistake,
                    isLoading = false,
                    showAnswer = true,
                    correctIndices = correctIndices,
                    isCorrect = reviewedResults[_currentIndex.value]
                        ?: ReviewSession.preReviewedResults[_currentIndex.value]
                )
            }
        } else {
            // Fresh card
            _uiState.update {
                ReviewUiState(currentMistake = mistake, isLoading = false)
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

        reviewedIndices.add(_currentIndex.value)

        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val correctIndices = correctAnswer.map { c ->
            labelLetters.indexOf(c.toString())
        }.filter { it >= 0 }.toSet()

        val userIndices = state.selectedOptionIndices
        val isCorrect = userIndices == correctIndices
        reviewedResults[_currentIndex.value] = isCorrect

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
        reviewedIndices.add(_currentIndex.value)
        reviewedResults[_currentIndex.value] = isCorrect
        _uiState.update { it.copy(showAnswer = true, isCorrect = isCorrect) }
        updateReviewRecord(mistake.id, isCorrect)
    }

    fun skipEssay() {
        val mistake = _uiState.value.currentMistake ?: return
        reviewedIndices.add(_currentIndex.value)
        _uiState.update { it.copy(showAnswer = true, isCorrect = null) }
        skipReviewRecord(mistake.id)
    }

    private fun skipReviewRecord(mistakeId: Long) {
        viewModelScope.launch {
            val records = repository.getReviewRecordsByMistake(mistakeId).first()
            val latestRecord = records.maxByOrNull { it.reviewDate }

            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            val tomorrowStart = cal.timeInMillis

            val newRecord = ReviewRecord(
                id = 0,
                mistakeId = mistakeId,
                reviewDate = System.currentTimeMillis(),
                result = ReviewResult.SKIP,
                nextReviewDate = tomorrowStart,
                correctCount = latestRecord?.correctCount ?: 0
            )
            repository.insertReviewRecord(newRecord)
        }
    }

    fun toggleFavorite() {
        val mistake = _uiState.value.currentMistake ?: return
        viewModelScope.launch {
            repository.setFavorite(mistake.id, !mistake.isFavorite)
        }
        _uiState.update {
            it.copy(currentMistake = mistake.copy(isFavorite = !mistake.isFavorite))
        }
    }

    private fun updateReviewRecord(mistakeId: Long, isCorrect: Boolean) {
        viewModelScope.launch {
            val records = repository.getReviewRecordsByMistake(mistakeId).first()
            val latestRecord = records.maxByOrNull { it.reviewDate }

            val newCorrectCount = if (isCorrect) (latestRecord?.correctCount ?: 0) + 1 else 0
            val nextReviewDate = calculateNextReviewDate(newCorrectCount, isCorrect)

            val newRecord = ReviewRecord(
                id = 0,
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
        val fiveDays = 5 * 86400000L
        return when {
            !isCorrect -> now + fiveDays
            correctCount in 1..2 -> now + fiveDays
            correctCount >= 3 -> -1
            else -> now + fiveDays
        }
    }

    fun nextMistake() {
        _currentIndex.value++
        if (_currentIndex.value >= _reviewQueue.value.size) {
            _currentIndex.value = 0
        }
        loadMistakeAtCurrentIndex()
    }

    fun jumpTo(index: Int) {
        if (_reviewQueue.value.isEmpty()) return
        _currentIndex.value = index.coerceIn(0, _reviewQueue.value.size - 1)
        loadMistakeAtCurrentIndex()
    }

    fun markAsMastered(mistakeId: Long) {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val tomorrowStart = cal.timeInMillis
            repository.insertReviewRecord(
                ReviewRecord(
                    id = 0,
                    mistakeId = mistakeId,
                    reviewDate = System.currentTimeMillis(),
                    result = ReviewResult.CORRECT,
                    nextReviewDate = tomorrowStart,
                    correctCount = 3
                )
            )
        }
    }
}
