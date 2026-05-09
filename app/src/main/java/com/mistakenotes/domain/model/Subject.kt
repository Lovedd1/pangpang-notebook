package com.mistakenotes.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey val name: String,
    val parentSubject: String? = null // 用于构建科目树
)