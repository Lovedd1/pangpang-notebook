package com.mistakenotes.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mistakes")
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",         // 题目标题（用户输入）
    val subject: String,           // 科目
    val tags: String,              // 知识点标签，逗号分隔
    val questionImagePath: String, // 题目图片路径
    val recognizedQuestion: String = "",  // 识别出的题目文本
    val correctAnswer: String,     // 正确答案
    val explanation: String = "",    // 解析
    val questionType: QuestionType, // 题目类型
    val createdAt: Long = System.currentTimeMillis(),
    val wrongCount: Int = 0,       // 错误次数
    val skipToday: Boolean = false // 跳过今日复习（不影响后续轮次）
)

enum class QuestionType {
    SINGLE_CHOICE,  // 单选题
    MULTI_CHOICE,   // 多选题（2-4个正确答案）
    ESSAY            // 大题
}