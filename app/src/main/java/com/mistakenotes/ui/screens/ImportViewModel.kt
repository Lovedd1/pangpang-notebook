package com.mistakenotes.ui.screens

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ImportUiState(
    val imageUri: Uri? = null,
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val optionEntries: List<String> = listOf("", "", "", ""),
    val correctOptionIndices: Set<Int> = emptySet(),
    val answerImageUri: Uri? = null,
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context
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
        _uiState.update {
            it.copy(
                questionType = type,
                correctOptionIndices = emptySet()
            )
        }
    }

    fun setOptionText(index: Int, text: String) {
        val newEntries = _uiState.value.optionEntries.toMutableList()
        if (index < newEntries.size) {
            newEntries[index] = text
            _uiState.update { it.copy(optionEntries = newEntries) }
        }
    }

    fun addOption() {
        _uiState.update {
            it.copy(optionEntries = it.optionEntries + "")
        }
    }

    fun removeOption(index: Int) {
        _uiState.update {
            val newEntries = it.optionEntries.toMutableList()
            if (newEntries.size > 2 && index < newEntries.size) {
                newEntries.removeAt(index)
                val newCorrect = it.correctOptionIndices
                    .filter { i -> i != index }
                    .map { i -> if (i > index) i - 1 else i }
                    .toSet()
                it.copy(optionEntries = newEntries, correctOptionIndices = newCorrect)
            } else it
        }
    }

    fun toggleCorrectOption(index: Int) {
        _uiState.update {
            val isSingle = it.questionType == QuestionType.SINGLE_CHOICE
            val newCorrect = if (isSingle) {
                setOf(index)
            } else {
                val current = it.correctOptionIndices
                if (index in current) current - index else current + index
            }
            it.copy(correctOptionIndices = newCorrect)
        }
    }

    fun setAnswerImageUri(uri: Uri?) {
        _uiState.update { it.copy(answerImageUri = uri) }
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
                val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")

                val optionsStr = if (state.questionType != QuestionType.ESSAY) {
                    state.optionEntries.joinToString("|")
                } else null

                val correctAnswerStr = if (state.questionType != QuestionType.ESSAY) {
                    state.correctOptionIndices.sorted().joinToString("") { labelLetters[it] }
                } else null

                val localImagePath = state.imageUri?.let { uri ->
                    copyImageToLocal(uri)
                }
                val localAnswerImagePath = state.answerImageUri?.let { uri ->
                    copyImageToLocal(uri, "answer")
                }

                val mistake = Mistake(
                    title = state.questionText.take(50).ifBlank { "错题" },
                    subjectId = state.subjectId,
                    chapterId = state.chapterId,
                    knowledgePointId = state.knowledgePointId,
                    questionType = state.questionType,
                    questionImagePath = localImagePath,
                    questionText = state.questionText.takeIf { it.isNotBlank() },
                    options = optionsStr,
                    correctAnswer = correctAnswerStr,
                    referenceAnswer = localAnswerImagePath
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

    private fun copyImageToLocal(uri: Uri, prefix: String = "img"): String? {
        return try {
            val imagesDir = File(context.filesDir, "question_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val destFile = File(imagesDir, "${prefix}_$timestamp.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ImportVM", "Failed to copy image", e)
            null
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