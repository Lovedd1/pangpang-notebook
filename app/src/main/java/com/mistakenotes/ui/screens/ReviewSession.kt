package com.mistakenotes.ui.screens

import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewResult

object ReviewSession {
    var queue: List<Mistake> = emptyList()
    var startIndex: Int = 0
    var isViewingResult: Boolean = false
    var lastResult: ReviewResult? = null
    var preReviewedIndices: Set<Int> = emptySet()
    var preReviewedResults: Map<Int, Boolean?> = emptyMap()

    /**
     * Persists the user's selected option indices per mistake ID across
     * review sessions so that re-entering a reviewed card still highlights
     * both correct (green) and incorrect (red) choices.
     */
    var selectedOptionsByMistakeId: Map<Long, Set<Int>> = emptyMap()

    fun start(
        queue: List<Mistake>,
        startIndex: Int = 0,
        isViewingResult: Boolean = false,
        lastResult: ReviewResult? = null,
        preReviewedIndices: Set<Int> = emptySet(),
        preReviewedResults: Map<Int, Boolean?> = emptyMap()
    ) {
        this.queue = queue
        this.startIndex = startIndex
        this.isViewingResult = isViewingResult
        this.lastResult = lastResult
        this.preReviewedIndices = preReviewedIndices
        this.preReviewedResults = preReviewedResults
    }

    fun clear() {
        queue = emptyList()
        startIndex = 0
        isViewingResult = false
        lastResult = null
        preReviewedIndices = emptySet()
        preReviewedResults = emptyMap()
        // Keep selectedOptionsByMistakeId — needed for cross-session persistence
    }

    val isEmpty: Boolean get() = queue.isEmpty()
    val size: Int get() = queue.size
}
