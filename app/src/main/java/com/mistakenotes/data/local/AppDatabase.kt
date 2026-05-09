package com.mistakenotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.Review
import com.mistakenotes.domain.model.Subject

@Database(
    entities = [Mistake::class, Review::class, Subject::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mistakeDao(): MistakeDao
    abstract fun reviewDao(): ReviewDao
    abstract fun subjectDao(): SubjectDao
}