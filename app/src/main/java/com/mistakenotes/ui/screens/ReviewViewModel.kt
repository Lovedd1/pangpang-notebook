package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Per-question state keyed by review queue index (page) */
data class QuestionState(
    val selectedOptionIndices: Set<Int> = emptySet(),
    val showAnswer: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctIndices: Set<Int> = emptySet()
)

data class ReviewUiState(
    val currentMistake: Mistake? = null,
    val perQuestionState: Map<Int, QuestionState> = emptyMap(),
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

    private val reviewedIndices = mutableSetOf<Int>()
    // 改为 StateFlow，便于和 DB 历史合并成响应式的 combinedResultsFlow，
    // 让选题弹窗在用户提交时立即反映新的对/错颜色。
    private val _reviewedResults = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val reviewedResultsMap: Map<Int, Boolean> get() = _reviewedResults.value

    // HomeScreen 在用户点进复习时传进来的"队列中已复习过的题"信息。
    // 必须存在 ViewModel 字段里——因为 loadReviewQueue 会 clear() 掉
    // ReviewSession 里的 preReviewedIndices，导致后续 loadMistakeAtCurrentIndex
    // 读不到这个集合，滑动到做过的题时会落入"Fresh card"分支、永远显示空白。
    private var preReviewedIndices: Set<Int> = emptySet()
    private var preReviewedResults: Map<Int, Boolean?> = emptyMap()

    /**
     * 合并"当前 session 已提交"和"DB 历史最近一次"的结果。
     * - 当前 session 结果优先级最高（用户刚提交就要立刻反映）
     * - 历史取该 mistakeId 最新一条记录；SKIP/无记录 → 不上色（弹窗用原色）
     * 输出按 reviewQueue 索引 keyed，方便弹窗按原 index 查色。
     */
    val combinedResultsFlow: StateFlow<Map<Int, ReviewResult>> = combine(
        _reviewedResults,
        _reviewQueue,
        repository.getAllReviewRecords()
    ) { sessionMap, queue, allRecords ->
        val byMistakeId = allRecords.groupBy { it.mistakeId }
        val resultMap = mutableMapOf<Int, ReviewResult>()
        queue.forEachIndexed { idx, mistake ->
            when (sessionMap[idx]) {
                true -> resultMap[idx] = ReviewResult.CORRECT
                false -> resultMap[idx] = ReviewResult.WRONG
                null -> {
                    val latest = byMistakeId[mistake.id]?.maxByOrNull { it.reviewDate }
                    when (latest?.result) {
                        ReviewResult.CORRECT -> resultMap[idx] = ReviewResult.CORRECT
                        ReviewResult.WRONG -> resultMap[idx] = ReviewResult.WRONG
                        else -> { /* SKIP / 无记录 → 不上色 */ }
                    }
                }
            }
        }
        resultMap
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        loadReviewQueue()
    }

    // region: bitmask helpers (stored in ReviewRecord.score, no schema change)
    private fun encodeOptions(indices: Set<Int>): Int {
        var bitmap = 0
        for (i in indices) {
            if (i in 0..7) bitmap = bitmap or (1 shl i)
        }
        return bitmap
    }

    private fun decodeOptions(bitmap: Int?): Set<Int> {
        if (bitmap == null || bitmap == 0) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 0..7) {
            if ((bitmap and (1 shl i)) != 0) result.add(i)
        }
        return result
    }
    // endregion

    private fun loadReviewQueue() {
        viewModelScope.launch {
            if (!ReviewSession.isEmpty) {
                _reviewQueue.value = ReviewSession.queue.toMutableList()
                _currentIndex.value = ReviewSession.startIndex.coerceIn(0, _reviewQueue.value.size - 1)
                val viewResult = ReviewSession.isViewingResult
                // Capture before clear() — clear() only resets queue/startIndex,
                // but capture defensively in case that changes
                val capturedLastResult = ReviewSession.lastResult
                val capturedPreReviewedIndices = ReviewSession.preReviewedIndices
                val capturedPreReviewedResults = ReviewSession.preReviewedResults
                // 提升到 ViewModel 字段，否则后续滑动 loadMistakeAtCurrentIndex
                // 读不到队列里其他"已复习"题的状态
                preReviewedIndices = capturedPreReviewedIndices
                preReviewedResults = capturedPreReviewedResults
                ReviewSession.clear()

                if (_reviewQueue.value.isNotEmpty()) {
                    val idx = _currentIndex.value
                    val mistake = _reviewQueue.value[idx]
                    if (viewResult) {
                        // Show previous result — load actual user selection from DB
                        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                        val correctIndices = (mistake.correctAnswer ?: "").map { c ->
                            labelLetters.indexOf(c.toString())
                        }.filter { it >= 0 }.toSet()
                        val resultIsCorrect = when (capturedLastResult) {
                            ReviewResult.CORRECT -> true
                            ReviewResult.WRONG -> false
                            else -> null
                        }
                        // Load user's actual selected options from DB (bitmask in score)
                        val records = repository.getReviewRecordsByMistake(mistake.id).first()
                        val latestNonSkip = records.lastOrNull { it.result != ReviewResult.SKIP }
                        val userSelected = decodeOptions(latestNonSkip?.score)
                        val qState = QuestionState(
                            selectedOptionIndices = userSelected.ifEmpty { correctIndices },
                            showAnswer = true,
                            isCorrect = resultIsCorrect,
                            correctIndices = correctIndices
                        )
                        _uiState.update {
                            it.copy(
                                currentMistake = mistake,
                                isLoading = false,
                                perQuestionState = it.perQuestionState + (idx to qState)
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
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val todayEnd = cal.timeInMillis - 1

                val queue = combine(
                    repository.getAllMistakes(),
                    repository.getAllReviewRecords()
                ) { mistakes, reviewRecords ->
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
        val idx = _currentIndex.value
        val mistake = _reviewQueue.value[idx]
        // 用 ViewModel 字段而不是 ReviewSession.preReviewedIndices——
        // 后者已在 loadReviewQueue 里 clear() 掉，滑动到这里时永远为 emptySet。
        val isPreReviewed = idx in preReviewedIndices
        if (idx in reviewedIndices || isPreReviewed) {
            // Already reviewed — load actual user selection from DB
            viewModelScope.launch {
                val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
                val correctIndices = (mistake.correctAnswer ?: "").map { c ->
                    labelLetters.indexOf(c.toString())
                }.filter { it >= 0 }.toSet()
                val isCorrect = _reviewedResults.value[idx]
                    ?: preReviewedResults[idx]
                // Load user's actual selected options from DB (bitmask in score)
                val records = repository.getReviewRecordsByMistake(mistake.id).first()
                val latestNonSkip = records.lastOrNull { it.result != ReviewResult.SKIP }
                val userSelected = decodeOptions(latestNonSkip?.score)
                val qState = QuestionState(
                    selectedOptionIndices = if (userSelected.isNotEmpty()) userSelected
                        else if (isCorrect == true) correctIndices
                        else emptySet(),
                    showAnswer = true,
                    isCorrect = isCorrect,
                    correctIndices = correctIndices
                )
                _uiState.update {
                    it.copy(
                        currentMistake = mistake,
                        isLoading = false,
                        perQuestionState = it.perQuestionState + (idx to qState)
                    )
                }
            }
        } else {
            // Fresh card
            _uiState.update {
                ReviewUiState(
                    currentMistake = mistake,
                    isLoading = false,
                    perQuestionState = it.perQuestionState
                )
            }
        }
    }

    fun toggleOption(page: Int, index: Int) {
        _uiState.update { state ->
            val mistake = _reviewQueue.value.getOrNull(page) ?: return@update state
            val currentQs = state.perQuestionState[page] ?: QuestionState()
            val newSelected = if (mistake.questionType == QuestionType.SINGLE_CHOICE) {
                setOf(index)
            } else {
                val cur = currentQs.selectedOptionIndices
                if (index in cur) cur - index else cur + index
            }
            state.copy(
                perQuestionState = state.perQuestionState + (page to currentQs.copy(selectedOptionIndices = newSelected))
            )
        }
    }

    fun submitAnswer(page: Int) {
        val state = _uiState.value
        val mistake = _reviewQueue.value.getOrNull(page) ?: return
        val correctAnswer = mistake.correctAnswer ?: return
        val currentQs = state.perQuestionState[page] ?: QuestionState()

        reviewedIndices.add(page)  // use page, not _currentIndex.value

        val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val correctIndices = correctAnswer.map { c ->
            labelLetters.indexOf(c.toString())
        }.filter { it >= 0 }.toSet()

        val userIndices = currentQs.selectedOptionIndices
        val isCorrect = userIndices == correctIndices
        _reviewedResults.value = _reviewedResults.value + (page to isCorrect)  // use page, not _currentIndex.value

        val newQs = currentQs.copy(
            showAnswer = true,
            isCorrect = isCorrect,
            correctIndices = correctIndices
        )
        _uiState.update {
            it.copy(perQuestionState = it.perQuestionState + (page to newQs))
        }

        // Persist user's selected options in ReviewRecord.score (bitmask, no schema change)
        val userBitmap = encodeOptions(userIndices)
        updateReviewRecord(mistake.id, isCorrect, userBitmap)
    }

    fun submitEssaySelfEval(page: Int, isCorrect: Boolean) {
        val mistake = _reviewQueue.value.getOrNull(page) ?: return
        val currentQs = _uiState.value.perQuestionState[page] ?: QuestionState()

        reviewedIndices.add(page)  // use page
        _reviewedResults.value = _reviewedResults.value + (page to isCorrect)  // use page

        val newQs = currentQs.copy(showAnswer = true, isCorrect = isCorrect)
        _uiState.update {
            it.copy(perQuestionState = it.perQuestionState + (page to newQs))
        }
        updateReviewRecord(mistake.id, isCorrect, null)
    }

    fun skipEssay(page: Int) {
        val mistake = _reviewQueue.value.getOrNull(page) ?: return
        val currentQs = _uiState.value.perQuestionState[page] ?: QuestionState()

        reviewedIndices.add(page)  // use page
        _reviewedResults.value = _reviewedResults.value + (page to false)  // mark as reviewed (skip = not correct)
        val newQs = currentQs.copy(showAnswer = true, isCorrect = null)
        _uiState.update {
            it.copy(perQuestionState = it.perQuestionState + (page to newQs))
        }
        skipReviewRecord(mistake.id)
    }

    private fun skipReviewRecord(mistakeId: Long) {
        // NonCancellable: ensure the DB write survives ViewModel teardown when
        // the user navigates back before the coroutine completes. Without this,
        // a race condition can silently drop review records, causing reviewed
        // cards to reappear in the home list the next day with stale schedule.
        viewModelScope.launch(NonCancellable) {
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
                correctCount = latestRecord?.correctCount ?: 0,
                score = latestRecord?.score  // preserve previous option selection
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

    private fun updateReviewRecord(mistakeId: Long, isCorrect: Boolean, userAnswerBitmap: Int?) {
        // NonCancellable: same rationale as skipReviewRecord — the review
        // result MUST be persisted even if the user immediately navigates away.
        viewModelScope.launch(NonCancellable) {
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
                correctCount = newCorrectCount,
                score = userAnswerBitmap
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
        // NonCancellable: same rationale as above — the "mastered" record
        // must be written regardless of navigation timing.
        viewModelScope.launch(NonCancellable) {
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
