package com.mistakenotes.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mistakes")
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,           // 科目
    val tags: String,              // 知识点标签，逗号分隔
    val questionImagePath: String, // 题目图片路径
    val correctAnswer: String,     // 正确答案
    val explanation: String,       // 解析
    val questionType: QuestionType, // 选择题/大题
    val createdAt: Long = System.currentTimeMillis(),
    val wrongCount: Int = 0        // 错误次数
)

enum class QuestionType {
    CHOICE,   // 选择题
    ESSAY     // 大题
}