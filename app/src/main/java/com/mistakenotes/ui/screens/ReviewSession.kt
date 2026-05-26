package com.mistakenotes.ui.screens

import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewResult

object ReviewSession {
    var queue: List<Mistake> = emptyList()
    var startIndex: Int = 0
    var isViewingResult: Boolean = false
    var lastResult: ReviewResult? = null

    fun start(
        queue: List<Mistake>,
        startIndex: Int = 0,
        isViewingResult: Boolean = false,
        lastResult: ReviewResult? = null
    ) {
        this.queue = queue
        this.startIndex = startIndex
        this.isViewingResult = isViewingResult
        this.lastResult = lastResult
    }

    fun clear() {
        queue = emptyList()
        startIndex = 0
    }

    val isEmpty: Boolean get() = queue.isEmpty()
    val size: Int get() = queue.size
}
