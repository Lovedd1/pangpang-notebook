package com.mistakenotes.domain.model

enum class ReviewResult {
    CORRECT,
    WRONG,
    SKIP
}

data class ReviewRecord(
    val id: Long = 0,
    val mistakeId: Long,
    val reviewDate: Long = System.currentTimeMillis(),
    val result: ReviewResult = ReviewResult.SKIP,
    val score: Int? = null,
    val nextReviewDate: Long? = null,
    val correctCount: Int = 0
)