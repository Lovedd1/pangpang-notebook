package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val imageUri: Uri? = null,
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val correctAnswer: String = "",
    val referenceAnswer: String = "",
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        loadSubjects()
    }

    private fun loadSubjects() {
        repository.getAllSubjects()
            .onEach { subjects ->
                _uiState.update { it.copy(subjects = subjects) }
            }
            .launchIn(viewModelScope)
    }

    fun setImageUri(uri: Uri?) {
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun setQuestionText(text: String) {
        _uiState.update { it.copy(questionText = text) }
    }

    fun setSubject(subjectId: Long) {
        _uiState.update {
            it.copy(
                subjectId = subjectId,
                chapterId = null,
                knowledgePointId = null,
                chapters = emptyList(),
                knowledgePoints = emptyList()
            )
        }
        loadChapters(subjectId)
    }

    private fun loadChapters(subjectId: Long) {
        repository.getChaptersBySubject(subjectId)
            .onEach { chapters ->
                _uiState.update { it.copy(chapters = chapters) }
            }
            .launchIn(viewModelScope)
    }

    fun setChapter(chapterId: Long) {
        _uiState.update {
            it.copy(
                chapterId = chapterId,
                knowledgePointId = null,
                knowledgePoints = emptyList()
            )
        }
        loadKnowledgePoints(chapterId)
    }

    private fun loadKnowledgePoints(chapterId: Long) {
        repository.getKnowledgePointsByChapter(chapterId)
            .onEach { knowledgePoints ->
                _uiState.update { it.copy(knowledgePoints = knowledgePoints) }
            }
            .launchIn(viewModelScope)
    }

    fun setKnowledgePoint(knowledgePointId: Long) {
        _uiState.update { it.copy(knowledgePointId = knowledgePointId) }
    }

    fun setQuestionType(type: QuestionType) {
        _uiState.update { it.copy(questionType = type) }
    }

    fun setCorrectAnswer(answer: String) {
        _uiState.update { it.copy(correctAnswer = answer) }
    }

    fun setReferenceAnswer(answer: String) {
        _uiState.update { it.copy(referenceAnswer = answer) }
    }

    fun saveMistake() {
        val state = _uiState.value

        // Validation
        if (state.subjectId == null) {
            _uiState.update { it.copy(errorMessage = "请选择科目") }
            return
        }
        if (state.chapterId == null) {
            _uiState.update { it.copy(errorMessage = "请选择章节") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            try {
                val now = System.currentTimeMillis()
                val pastTime = now - 10000 // 10 seconds ago
                android.util.Log.d("ImportVM", "Creating mistake with now=$now, past=$pastTime")
                val mistake = Mistake(
                    title = state.questionText.take(50).ifBlank { "错题" },
                    subjectId = state.subjectId,
                    chapterId = state.chapterId,
                    knowledgePointId = state.knowledgePointId,
                    questionType = state.questionType,
                    questionImagePath = state.imageUri?.toString(),
                    questionText = state.questionText.takeIf { it.isNotBlank() },
                    options = null,
                    correctAnswer = state.correctAnswer.takeIf { it.isNotBlank() },
                    referenceAnswer = state.referenceAnswer.takeIf { it.isNotBlank() }
                )

                val mistakeId = repository.insertMistake(mistake)
                android.util.Log.d("ImportVM", "inserted mistake with id: $mistakeId")

                // Create initial review record
                try {
                    val pastTime = now - 10000
                    val reviewRec = ReviewRecord(
                        mistakeId = mistakeId,
                        reviewDate = pastTime,
                        result = ReviewResult.SKIP,
                        nextReviewDate = pastTime,
                        correctCount = 0
                    )
                    android.util.Log.d("ImportVM", "Inserting review record for mistakeId: $mistakeId")
                    repository.insertReviewRecord(reviewRec)
                    android.util.Log.d("ImportVM", "Review record inserted successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ImportVM", "Failed to insert review record", e)
                }

                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "保存失败: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetState() {
        _uiState.update {
            ImportUiState(subjects = it.subjects)
        }
    }
}