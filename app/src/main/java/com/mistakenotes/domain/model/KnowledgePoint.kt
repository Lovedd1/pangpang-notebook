package com.mistakenotes.domain.model

data class KnowledgePoint(
    val id: Long = 0,
    val chapterId: Long,
    val name: String,
    val isPreset: Boolean = false
)