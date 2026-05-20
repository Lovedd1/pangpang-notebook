package com.mistakenotes.di

import android.content.Context
import androidx.room.Room
import com.mistakenotes.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mistake_notes.db"
        ).build()
    }

    @Provides
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideKnowledgePointDao(db: AppDatabase): KnowledgePointDao = db.knowledgePointDao()

    @Provides
    fun provideMistakeDao(db: AppDatabase): MistakeDao = db.mistakeDao()

    @Provides
    fun provideReviewRecordDao(db: AppDatabase): ReviewRecordDao = db.reviewRecordDao()
}