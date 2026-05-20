package com.mistakenotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Long = 0xFFD4A574
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val name: String,
    val order: Int = 0
)

@Entity(
    tableName = "knowledge_points",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chapterId")]
)
data class KnowledgePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    val name: String,
    val isPreset: Boolean = false
)

@Entity(
    tableName = "mistakes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("subjectId"),
        Index("chapterId"),
        Index("knowledgePointId")
    ]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val subjectId: Long,
    val chapterId: Long,
    val knowledgePointId: Long,
    val questionType: String = "SINGLE_CHOICE",
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

@Entity(
    tableName = "review_records",
    foreignKeys = [
        ForeignKey(
            entity = MistakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mistakeId")]
)
data class ReviewRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mistakeId: Long,
    val reviewDate: Long = System.currentTimeMillis(),
    val result: String = "SKIP",
    val score: Int? = null,
    val nextReviewDate: Long? = null,
    val correctCount: Int = 0
)