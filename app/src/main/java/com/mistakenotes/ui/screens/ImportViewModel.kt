package com.mistakenotes.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val imageUri: Uri? = null,
    val questionType: QuestionType = QuestionType.CHOICE,
    val correctAnswer: String = "",
    val subject: String = "数学",
    val selectedTags: Set<String> = emptySet(),
    val explanation: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState

    fun setImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(imageUri = uri)
    }

    fun setQuestionType(type: QuestionType) {
        _uiState.value = _uiState.value.copy(questionType = type)
    }

    fun setCorrectAnswer(answer: String) {
        _uiState.value = _uiState.value.copy(correctAnswer = answer)
    }

    fun setSubject(subject: String) {
        _uiState.value = _uiState.value.copy(subject = subject)
    }

    fun toggleTag(tag: String) {
        val currentTags = _uiState.value.selectedTags
        _uiState.value = _uiState.value.copy(
            selectedTags = if (currentTags.contains(tag)) {
                currentTags - tag
            } else {
                currentTags + tag
            }
        )
    }

    fun setExplanation(explanation: String) {
        _uiState.value = _uiState.value.copy(explanation = explanation)
    }

    fun saveMistake(imagePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val state = _uiState.value
                val mistake = Mistake(
                    subject = state.subject,
                    tags = state.selectedTags.joinToString(","),
                    questionImagePath = imagePath,
                    correctAnswer = state.correctAnswer,
                    explanation = state.explanation,
                    questionType = state.questionType
                )
                repository.insertMistake(mistake)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "保存失败"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState()
    }
}