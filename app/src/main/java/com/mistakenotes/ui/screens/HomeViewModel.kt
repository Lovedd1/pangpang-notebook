package com.mistakenotes.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
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
    val lastResult: ReviewResult?,
    val correctCount: Int = 0,
    val subjectName: String = "",
    val chapterName: String = "",
    val subjectColor: Long = 0xFFD4A574,
    val skippedAt: Long = 0
)

data class OverdueCardInfo(
    val mistake: Mistake,
    val overdueDays: Int,
    val correctCount: Int = 0,
    val subjectName: String = "",
    val chapterName: String = "",
    val subjectColor: Long = 0xFFD4A574
)

data class HomeUiState(
    val subjects: List<Subject> = emptyList(),
    val currentSubjectId: Long? = null,
    val todayCards: List<TodayCardInfo> = emptyList(),
    val overdueCards: List<OverdueCardInfo> = emptyList(),
    val cardSubjectIds: Set<Long> = emptySet(),
    val totalMistakes: Int = 0,
    val masteredCount: Int = 0,
    val isLoading: Boolean = true,
    val todayQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet(),
    val overdueQuestionTypes: Set<com.mistakenotes.domain.model.QuestionType> = emptySet()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Unfiltered data cached for re-filtering on subject change
    private var allTodayCards = listOf<TodayCardInfo>()
    private var allOverdueCards = listOf<OverdueCardInfo>()
    private var cachedSubjects = listOf<Subject>()
    private var cachedCardSubjIds = setOf<Long>()
    private var cachedTotalMistakes = 0
    private var cachedMastered = 0

    init {
        // ====================================================================
        // One-shot data repair: fix schedules corrupted by the race condition
        // where viewModelScope coroutines were cancelled before writing review
        // records (fixed in ReviewViewModel with NonCancellable, but past data
        // may already be corrupted).
        // ====================================================================
        viewModelScope.launch {
            val mistakes = repository.getAllMistakes().first()
            val reviews = repository.getAllReviewRecords().first()
            val reviewByMistake = reviews.groupBy { it.mistakeId }

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            val yesterdayStart = todayStart - 86400000L
            val yesterdayEnd = todayStart - 1L

            var repairedCount = 0
            for (mistake in mistakes) {
                val records = reviewByMistake[mistake.id] ?: continue

                // Find a non-SKIP review record from yesterday
                val yesterdayReview = records.find {
                    it.reviewDate in yesterdayStart..yesterdayEnd &&
                        it.result != ReviewResult.SKIP
                } ?: continue

                // Check if the latest record (by reviewDate) correctly reflects
                // yesterday's review. If not, the schedule data is corrupted.
                val latest = records.maxByOrNull { it.reviewDate }

                // If latest record IS yesterday's review, schedule is fine
                if (latest != null && latest.id == yesterdayReview.id) continue

                // If latest record has a different nrd but it's correct for
                // the review (reviewDate + 5d), schedule is also fine
                val expectedNrd = yesterdayReview.reviewDate + 5 * 86400000L
                if (latest != null && latest.nextReviewDate == expectedNrd) continue

                // Schedule corrupted: overwrite the yesterday review record
                // with the correct nextReviewDate. Use REPLACE on same ID.
                repository.insertReviewRecord(
                    ReviewRecord(
                        id = yesterdayReview.id,
                        mistakeId = mistake.id,
                        reviewDate = yesterdayReview.reviewDate,
                        result = yesterdayReview.result,
                        score = yesterdayReview.score,
                        nextReviewDate = expectedNrd,
                        correctCount = yesterdayReview.correctCount
                    )
                )
                repairedCount++
                Log.w("HomeVM",
                    "[REPAIR] Fixed corrupted schedule: id=${mistake.id} " +
                        "title=${mistake.title} " +
                        "correctCount=${yesterdayReview.correctCount} " +
                        "nrd=$expectedNrd")
            }
            if (repairedCount > 0) {
                Log.w("HomeVM", "[REPAIR] Total repaired: $repairedCount cards")
            }
        }

        // ====================================================================
        // Main data flow: combine all four tables and compute today/overdue
        // lists reactively.
        // ====================================================================
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
                val yesterdayStart = todayStart - 86400000L
                val yesterdayEnd = todayStart - 1L

                val subjectMap = subjects.associateBy { it.id }
                val chapterMap = chapters.associateBy { it.id }
                val reviewRecordMap = reviewRecords.groupBy { it.mistakeId }
                val latestByMistake = reviewRecordMap.mapValues { (_, recs) ->
                    recs.maxByOrNull { it.reviewDate }
                }

                fun nameOf(subjectId: Long) = subjectMap[subjectId]?.name ?: ""
                fun chapterNameOf(chapterId: Long) = chapterMap[chapterId]?.name ?: ""
                fun colorOf(subjectId: Long) = subjectMap[subjectId]?.color ?: 0xFFD4A574

                val todayPairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    val isDueToday = next != null && next != -1L && next in todayStart..todayEnd
                    val todayRecord = reviewRecordMap[mistake.id]
                        ?.find { it.reviewDate in todayStart..todayEnd }
                    val isReviewed = todayRecord != null && todayRecord.result != ReviewResult.SKIP
                    val tomorrowStart = todayStart + 86400000L
                    val recordCount = reviewRecordMap[mistake.id]?.size ?: 0

                    // ── 跳过 today ──────────────────────────────────────────
                    val isSkippedToday = rec?.result == ReviewResult.SKIP &&
                        rec?.reviewDate in todayStart..todayEnd &&
                        rec?.nextReviewDate == tomorrowStart &&
                        recordCount >= 2

                    // ── 跳过 carryover（昨日 SKIP → 今天 "已跳过"）────────
                    val isSkippedCarryover = rec?.result == ReviewResult.SKIP &&
                        rec.reviewDate < todayStart &&
                        rec.nextReviewDate in todayStart..todayEnd &&
                        recordCount >= 2

                    val isSkipped = isSkippedToday || isSkippedCarryover
                    val isMastered = (rec?.correctCount ?: 0) >= 3

                    // ── 安全网：昨天已做的题目不入今日列表 ──────────────────
                    // 如果卡片昨天有 CORRECT/WRONG 记录（确实做了），且今天
                    // 没有新的 CORRECT/WRONG 记录，则它不应该出现在今日。
                    // 这防御两种场景：
                    //   a) 协程竞态导致 CORRECT/WRONG 记录丢失 → latest 回退
                    //      到初始 SKIP → isDueToday 可能误判为 TRUE
                    //   b) 用户编辑录入日期后复习 → 首次复习 date 漂移
                    val yesterdayReviewRecord = reviewRecordMap[mistake.id]
                        ?.find { it.reviewDate in yesterdayStart..yesterdayEnd &&
                            it.result != ReviewResult.SKIP }
                    val wasReviewedYesterday = yesterdayReviewRecord != null
                    val wasReviewedToday = isReviewed
                    if (wasReviewedYesterday && !wasReviewedToday) {
                        Log.d("HomeVM",
                            "[FILTER] Excluding yesterday-reviewed card: " +
                                "id=${mistake.id} title=${mistake.title} " +
                                "latestNrd=${rec?.nextReviewDate}")
                        return@mapNotNull null
                    }

                    if ((isDueToday || isReviewed || isSkipped) && !isMastered) {
                        val reason = when {
                            isSkipped -> "SKIP"
                            isReviewed -> "REVIEWED_TODAY"
                            isDueToday -> "DUE_TODAY(nrd=${rec?.nextReviewDate})"
                            else -> "UNKNOWN"
                        }
                        Log.d("HomeVM",
                            "[TODAY] id=${mistake.id} title=${mistake.title} " +
                                "reason=$reason correctCount=${rec?.correctCount ?: 0} " +
                                "records=$recordCount")
                        TodayCardInfo(
                            mistake, isReviewed,
                            if (isSkipped) ReviewResult.SKIP else todayRecord?.result,
                            correctCount = rec?.correctCount ?: 0,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId),
                            subjectColor = colorOf(mistake.subjectId),
                            skippedAt = if (isSkipped) rec?.reviewDate ?: 0 else 0
                        )
                    } else null
                }.sortedWith(
                    compareByDescending<TodayCardInfo> { it.skippedAt > 0 }
                        .thenBy { it.skippedAt }
                )

                val overduePairs = mistakes.mapNotNull { mistake ->
                    val rec = latestByMistake[mistake.id]
                    val next = rec?.nextReviewDate
                    if (next != null && next != -1L && next < todayStart && (rec?.correctCount ?: 0) < 3) {
                        OverdueCardInfo(
                            mistake,
                            ((todayStart - next) / 86400000L).toInt(),
                            correctCount = rec?.correctCount ?: 0,
                            subjectName = nameOf(mistake.subjectId),
                            chapterName = chapterNameOf(mistake.chapterId),
                            subjectColor = colorOf(mistake.subjectId)
                        )
                    } else null
                }.sortedByDescending { it.overdueDays }

                val mastered = latestByMistake.count { (_, rec) ->
                    rec?.nextReviewDate == -1L || (rec?.correctCount ?: 0) >= 3
                }

                // Cache unfiltered data
                cachedSubjects = subjects
                allTodayCards = todayPairs
                allOverdueCards = overduePairs
                cachedCardSubjIds = (todayPairs.map { it.mistake.subjectId } +
                    overduePairs.map { it.mistake.subjectId }).toSet()
                cachedTotalMistakes = mistakes.size
                cachedMastered = mastered

                emitFilteredState()
            }.collect()
        }
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.value = _uiState.value.copy(currentSubjectId = subjectId)
        emitFilteredState()
    }

    fun selectTodayQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
        _uiState.update { it.copy(todayQuestionTypes = types) }
        emitFilteredState()
    }

    fun selectOverdueQuestionTypes(types: Set<com.mistakenotes.domain.model.QuestionType>) {
        _uiState.update { it.copy(overdueQuestionTypes = types) }
        emitFilteredState()
    }

    private fun emitFilteredState() {
        val sel = _uiState.value.currentSubjectId
        val todayTypeSel = _uiState.value.todayQuestionTypes
        val overdueTypeSel = _uiState.value.overdueQuestionTypes
        fun typeOk(qt: com.mistakenotes.domain.model.QuestionType,
                    sel: Set<com.mistakenotes.domain.model.QuestionType>) =
            sel.isEmpty() || qt in sel

        _uiState.value = HomeUiState(
            subjects = cachedSubjects,
            currentSubjectId = sel,
            todayCards = allTodayCards
                .filter { sel == null || it.mistake.subjectId == sel }
                .filter { typeOk(it.mistake.questionType, todayTypeSel) },
            overdueCards = allOverdueCards
                .filter { sel == null || it.mistake.subjectId == sel }
                .filter { typeOk(it.mistake.questionType, overdueTypeSel) },
            cardSubjectIds = cachedCardSubjIds,
            totalMistakes = cachedTotalMistakes,
            masteredCount = cachedMastered,
            isLoading = false,
            todayQuestionTypes = todayTypeSel,
            overdueQuestionTypes = overdueTypeSel
        )
    }
}
