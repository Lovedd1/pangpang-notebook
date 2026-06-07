package com.mistakenotes.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.rag.KnowledgeBase
import com.mistakenotes.data.rag.KnowledgeClassifier
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.KnowledgePoint
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import com.mistakenotes.domain.model.getAnswerImagePaths
import com.mistakenotes.domain.model.getQuestionImagePaths
import com.mistakenotes.domain.model.ReviewRecord
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** RAG 分类状态机 */
enum class RagStatus { IDLE, LOADING, DONE, ERROR }

data class ImportUiState(
    val imageUris: List<Uri> = emptyList(),
    val title: String = "",
    val questionText: String = "",
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val knowledgePointId: Long? = null,
    val questionType: QuestionType = QuestionType.SINGLE_CHOICE,
    val optionEntries: List<String> = listOf("", "", "", ""),
    val correctOptionIndices: Set<Int> = emptySet(),
    val answerImageUris: List<Uri> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val knowledgePoints: List<KnowledgePoint> = emptyList(),
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isEditMode: Boolean = false,
    val entryDate: Long? = null,
    // ====== 新增 RAG 状态 ======
    val ragStatus: RagStatus = RagStatus.IDLE,
    val ragErrorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    @ApplicationContext private val context: Context,
    private val classifier: KnowledgeClassifier,  // ← 新增
    private val knowledgeBase: KnowledgeBase,  // ← 新增
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    private val editingMistakeId: Long = savedStateHandle.get<Long>("mistakeId") ?: -1L
    private val _pendingClassifyJob = MutableStateFlow<Job?>(null)

    init {
        loadSubjects()
        if (editingMistakeId > 0) {
            loadMistakeForEditing(editingMistakeId)
        }
    }

    private fun loadMistakeForEditing(id: Long) {
        viewModelScope.launch {
            val mistake = repository.getMistakeById(id) ?: return@launch
            val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")

            // Parse options
            val entries = mistake.options?.split("|")?.toMutableList() ?: mutableListOf("", "", "", "")
            while (entries.size < 4) entries.add("")

            // Parse correct answer letters to indices
            val correctIndices = (mistake.correctAnswer ?: "").mapNotNull { c ->
                labelLetters.indexOf(c.toString()).takeIf { it >= 0 }
            }.toSet()

            // Load chapter and knowledge point lists
            loadChapters(mistake.subjectId)
            if (mistake.chapterId > 0) {
                loadKnowledgePoints(mistake.chapterId)
            }

            _uiState.update {
                it.copy(
                    title = mistake.title.ifBlank { "" },
                    questionText = mistake.questionText ?: "",
                    subjectId = mistake.subjectId,
                    chapterId = mistake.chapterId,
                    knowledgePointId = mistake.knowledgePointId,
                    questionType = mistake.questionType,
                    optionEntries = entries,
                    correctOptionIndices = correctIndices,
                    imageUris = mistake.getQuestionImagePaths().mapNotNull { path ->
                        val file = File(path)
                        if (file.exists()) Uri.fromFile(file) else null
                    },
                    answerImageUris = mistake.getAnswerImagePaths().mapNotNull { path ->
                        val file = File(path)
                        if (file.exists()) Uri.fromFile(file) else null
                    },
                    errorMessage = null,
                    isEditMode = true,
                    entryDate = mistake.createdAt
                )
            }
        }
    }

    private fun loadSubjects() {
        repository.getAllSubjects()
            .onEach { subjects ->
                _uiState.update { it.copy(subjects = subjects) }
            }
            .launchIn(viewModelScope)
    }

    fun addImageUri(uri: Uri) {
        val isFirst = _uiState.value.imageUris.isEmpty()
        _uiState.update { it.copy(imageUris = it.imageUris + uri) }
        if (isFirst) {
            triggerRagClassification(uri)
        }
    }

    private fun triggerRagClassification(uri: Uri) {
        // 取消上一个未完成的任务
        _pendingClassifyJob.value?.cancel()

        _uiState.update { it.copy(ragStatus = RagStatus.LOADING, ragErrorMessage = null) }

        _pendingClassifyJob.value = viewModelScope.launch {
            try {
                val result = classifier.classify(uri)
                // 守护：用户可能已经删除/替换了这张图
                if (_uiState.value.imageUris.firstOrNull() != uri) return@launch
                // 守护：用户可能已经手动选了下拉
                if (_uiState.value.chapterId != null || _uiState.value.knowledgePointId != null) {
                    _uiState.update { it.copy(ragStatus = RagStatus.DONE) }
                    return@launch
                }

                if (result.isFailed) {
                    _uiState.update {
                        it.copy(
                            ragStatus = RagStatus.ERROR,
                            ragErrorMessage = "AI 归类失败：${result.reasoning}，请手动选择"
                        )
                    }
                } else {
                    // 守护：用户可能已经手动改了下拉
                    if (_uiState.value.chapterId != null) {
                        _uiState.update { it.copy(ragStatus = RagStatus.DONE) }
                        return@launch
                    }
                    // 自动 upsert 知识点到 Room（自然键去重）
                    val kpName = lookupKnowledgePointName(result.knowledgePointId)
                    val roomKpId = if (kpName != null) {
                        repository.upsertKnowledgePoint(result.chapterId, kpName)
                    } else -1L
                    _uiState.update {
                        it.copy(
                            chapterId = result.chapterId,
                            knowledgePointId = if (roomKpId > 0) roomKpId else null,
                            ragStatus = RagStatus.DONE
                        )
                    }
                    // 刷新下拉里的知识点列表
                    loadKnowledgePoints(result.chapterId)
                }
            } catch (e: CancellationException) {
                // 用户取消，正常路径
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        ragStatus = RagStatus.ERROR,
                        ragErrorMessage = "AI 归类失败：${e.message ?: "未知错误"}，请手动选择"
                    )
                }
            }
        }
    }

    fun removeImageUri(index: Int) {
        _uiState.update {
            it.copy(imageUris = it.imageUris.filterIndexed { i, _ -> i != index })
        }
        // 如果删的是第一张图且当前有 pending RAG 任务，取消
        if (index == 0) {
            _pendingClassifyJob.value?.cancel()
            _pendingClassifyJob.value = null
            _uiState.update { it.copy(ragStatus = RagStatus.IDLE, ragErrorMessage = null) }
        }
    }

    fun setTitle(text: String) {
        _uiState.update { it.copy(title = text) }
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

    fun setEntryDate(epochMillis: Long) {
        _uiState.update { it.copy(entryDate = epochMillis) }
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

    fun addAnswerImageUri(uri: Uri) {
        _uiState.update { it.copy(answerImageUris = it.answerImageUris + uri) }
    }

    fun removeAnswerImageUri(index: Int) {
        _uiState.update {
            it.copy(answerImageUris = it.answerImageUris.filterIndexed { i, _ -> i != index })
        }
    }

    fun saveMistake() {
        _pendingClassifyJob.value?.cancel()  // ← 加这一行
        _pendingClassifyJob.value = null
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
        if (state.questionType != QuestionType.ESSAY && state.correctOptionIndices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请选择正确答案") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            try {
                val now = System.currentTimeMillis()

                // Calculate entryDate (user-selected, defaults to today)
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                val today00 = cal.timeInMillis
                val entryDateMs = state.entryDate ?: today00
                // First review is 5 days from the user-selected entry date, not from
                // the actual save time. This way backdated imports follow the schedule
                // starting from when the mistake was actually made, so an entry on
                // 6/1 with a 5-day cycle lands on 6/6, not on "today + 5".
                val finalNextReviewDate = entryDateMs + 5 * 86400000L

                val labelLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H")

                val optionsStr = if (state.questionType != QuestionType.ESSAY) {
                    state.optionEntries.joinToString("|")
                } else null

                val correctAnswerStr = if (state.questionType != QuestionType.ESSAY) {
                    state.correctOptionIndices.sorted().joinToString("") { labelLetters[it] }
                } else null

                val isEdit = state.isEditMode
                val existingMistake = if (isEdit) repository.getMistakeById(editingMistakeId) else null

                // Auto-generate title if blank
                val finalTitle = if (state.title.isBlank()) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(entryDateMs))
                    val entryCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = entryDateMs
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val entryDayStart = entryCal.timeInMillis
                    entryCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    val entryDayEnd = entryCal.timeInMillis - 1
                    val todayCount = repository.getAllMistakes().first()
                        .count { it.createdAt in entryDayStart..entryDayEnd } + 1
                    "$dateStr-${todayCount.toString().padStart(2, '0')}"
                } else state.title

                // Preserve existing images if not replaced
                val localImagePaths = if (state.imageUris.isNotEmpty()) {
                    state.imageUris.mapNotNull { copyImageToLocal(it) }
                } else if (isEdit) {
                    existingMistake?.getQuestionImagePaths() ?: emptyList()
                } else {
                    emptyList()
                }

                val localAnswerPaths = if (state.answerImageUris.isNotEmpty()) {
                    state.answerImageUris.mapNotNull { copyImageToLocal(it, "answer") }
                } else if (isEdit) {
                    existingMistake?.getAnswerImagePaths() ?: emptyList()
                } else {
                    emptyList()
                }

                val questionImagePathStr = localImagePaths.joinToString("||").takeIf { it.isNotBlank() }
                val answerImagePathStr = localAnswerPaths.joinToString("||").takeIf { it.isNotBlank() }

                val mistake = Mistake(
                    id = if (isEdit) editingMistakeId else 0,
                    title = finalTitle,
                    subjectId = state.subjectId,
                    chapterId = state.chapterId,
                    knowledgePointId = state.knowledgePointId,
                    questionType = state.questionType,
                    questionImagePath = questionImagePathStr,
                    questionText = state.questionText.takeIf { it.isNotBlank() },
                    options = optionsStr,
                    correctAnswer = correctAnswerStr,
                    referenceAnswer = answerImagePathStr,
                    // Use the (possibly user-edited) entryDateMs in BOTH new and edit
                    // modes. In edit mode loadMistakeForEditing() pre-fills state.entryDate
                    // with the original createdAt, so unchanged edits preserve the date
                    // and DatePicker changes are honored.
                    createdAt = entryDateMs,
                    isFavorite = existingMistake?.isFavorite ?: false,
                    isTop = existingMistake?.isTop ?: false
                )

                if (isEdit) {
                    repository.updateMistake(mistake)
                    // Reset review schedule — delete old records and create fresh one
                    repository.deleteReviewRecordsByMistakeId(editingMistakeId)
                    repository.insertReviewRecord(
                        ReviewRecord(
                            mistakeId = editingMistakeId,
                            reviewDate = now,
                            result = ReviewResult.SKIP,
                            nextReviewDate = finalNextReviewDate,
                            correctCount = 0
                        )
                    )
                } else {
                    val mistakeId = repository.insertMistake(mistake)
                    // Create initial review record — first review due 5 days from today
                    repository.insertReviewRecord(
                        ReviewRecord(
                            mistakeId = mistakeId,
                            reviewDate = now,
                            result = ReviewResult.SKIP,
                            nextReviewDate = finalNextReviewDate,
                            correctCount = 0
                        )
                    )
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
            val destFile = File(imagesDir, "${prefix}_${System.nanoTime()}.jpg")
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

    fun deleteMistake() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            try {
                val mistake = repository.getMistakeById(editingMistakeId)
                if (mistake != null) {
                    // Also delete local image files
                    mistake.getQuestionImagePaths().forEach { File(it).delete() }
                    mistake.getAnswerImagePaths().forEach { File(it).delete() }
                    repository.deleteMistake(mistake)
                }
                _uiState.update { it.copy(isDeleting = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDeleting = false, errorMessage = "删除失败: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearRagError() {
        _uiState.update { it.copy(ragStatus = RagStatus.IDLE, ragErrorMessage = null) }
    }

    /**
     * 从当前加载的知识库 JSON 找 JSON id 对应的 name
     * （用于 RAG 分类后 upsert 到 Room）
     */
    private fun lookupKnowledgePointName(jsonId: Long): String? {
        // 知识库已通过 KnowledgeBaseLoader 加载到内存
        // 这里通过 Hilt 注入的知识库查找
        return knowledgeBase?.points?.firstOrNull { it.id == jsonId }?.name
    }

    fun resetState() {
        _uiState.update {
            ImportUiState(subjects = it.subjects)
        }
    }
}