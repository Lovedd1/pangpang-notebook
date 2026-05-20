package com.mistakenotes.domain.model

data class Chapter(
    val id: Long = 0,
    val subjectId: Long,
    val name: String,
    val order: Int = 0
)