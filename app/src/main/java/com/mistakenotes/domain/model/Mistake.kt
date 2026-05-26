package com.mistakenotes.domain.model

enum class QuestionType {
    SINGLE_CHOICE,
    MULTI_CHOICE,
    ESSAY
}

data class Mistake(
    val id: Long = 0,
    val title: String = "",
    val subjectId: Long,
    val chapterId: Long,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val questionImagePath: String? = null,
    val questionText: String? = null,
    val options: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val referenceAnswer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isTop: Boolean = false
)