package com.mistakenotes.ui.screens

import com.mistakenotes.domain.model.Mistake

object ReviewSession {
    var queue: List<Mistake> = emptyList()
    var startIndex: Int = 0

    fun start(queue: List<Mistake>, startIndex: Int = 0) {
        this.queue = queue
        this.startIndex = startIndex
    }

    fun clear() {
        queue = emptyList()
        startIndex = 0
    }

    val isEmpty: Boolean get() = queue.isEmpty()
    val size: Int get() = queue.size
}
