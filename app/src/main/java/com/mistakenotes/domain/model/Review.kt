package com.mistakenotes.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val round: ReviewRound,        // 第几轮复习
    val isCorrect: Boolean?,      // null=跳过, true=对, false=错
    val isSkipped: Boolean = false,
    val reviewedAt: Long = System.currentTimeMillis()
)

enum class ReviewRound {
    FIRST,   // 第7天
    SECOND,  // 第14天
    THIRD    // 第21天
}