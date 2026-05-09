package com.mistakenotes.data.local

import androidx.room.TypeConverter
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRound

class Converters {
    @TypeConverter
    fun fromQuestionType(value: QuestionType): String = value.name

    @TypeConverter
    fun toQuestionType(value: String): QuestionType = QuestionType.valueOf(value)

    @TypeConverter
    fun fromReviewRound(value: ReviewRound): String = value.name

    @TypeConverter
    fun toReviewRound(value: String): ReviewRound = ReviewRound.valueOf(value)
}