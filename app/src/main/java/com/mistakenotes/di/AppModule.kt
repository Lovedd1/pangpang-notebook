package com.mistakenotes.di

import android.content.Context
import androidx.room.Room
import com.mistakenotes.data.local.AppDatabase
import com.mistakenotes.data.local.MistakeDao
import com.mistakenotes.data.local.ReviewDao
import com.mistakenotes.data.local.SubjectDao
import com.mistakenotes.data.remote.TextRecognitionService
import com.mistakenotes.data.remote.TesseractOcrService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    @Singleton
    fun provideMistakeDao(db: AppDatabase): MistakeDao = db.mistakeDao()

    @Provides
    @Singleton
    fun provideReviewDao(db: AppDatabase): ReviewDao = db.reviewDao()

    @Provides
    @Singleton
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    @Singleton
    fun provideTextRecognitionService(): TextRecognitionService = TextRecognitionService()

    @Provides
    @Singleton
    fun provideTesseractOcrService(@ApplicationContext context: Context): TesseractOcrService =
        TesseractOcrService(context)
}