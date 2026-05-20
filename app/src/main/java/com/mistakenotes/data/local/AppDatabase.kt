package com.mistakenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SubjectEntity::class,
        ChapterEntity::class,
        KnowledgePointEntity::class,
        MistakeEntity::class,
        ReviewRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun chapterDao(): ChapterDao
    abstract fun knowledgePointDao(): KnowledgePointDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun reviewRecordDao(): ReviewRecordDao
}