package com.mistakenotes.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Chapter
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.ReviewResult
import com.mistakenotes.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseItem(
    val mistake: Mistake,
    val reviewPattern: String,
    val correctCount: Int,
    val wrongCount: Int,
    val ebbinghausCount: Int = 0,
    val chapterName: String = "",
    val subjectName: String = "",
    val knowledgePointName: String = ""
)

data class BrowseUiState(
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val currentSubjectId: Long? = null,
    val currentChapterId: Long? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = true,
    val isFavoritesMode: Boolean = false
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: MistakeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val isFavoritesMode: Boolean = savedStateHandle.get<Boolean>("favorites") ?: false

    private val _uiState = MutableStateFlow(BrowseUiState(isFavoritesMode = isFavoritesMode))
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var allMistakes = listOf<Mistake>()
    private var allRecords = listOf<com.mistakenotes.domain.model.ReviewRecord>()
    private var chapterMap = mapOf<Long, Chapter>()
    private var subjectMap = mapOf<Long, Subject>()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val mistakesFlow = if (isFavoritesMode) repository.getFavoriteMistakes() else repository.getAllMistakes()
            combine(
                repository.getAllSubjects(),
                mistakesFlow,
                repository.getAllReviewRecords(),
                repository.getAllChapters(),
                repository.getAllKnowledgePoints()
            ) { subjects, mistakes, records, chapters, knowledgePoints ->
                subjectMap = subjects.associateBy { it.id }
                chapterMap = chapters.associateBy { it.id }
                val kpMap = knowledgePoints.associateBy { it.id }
                allMistakes = mistakes
                allRecords = records
                _uiState.update { it.copy(subjects = subjects, isLoading = false) }
                buildBrowseItems()
            }.collect()
        }
    }

    fun selectSubject(subjectId: Long?) {
        _uiState.update { it.copy(currentSubjectId = subjectId, currentChapterId = null) }
        if (subjectId != null) {
            viewModelScope.launch {
                repository.getChaptersBySubject(subjectId).collect { chapters ->
                    _uiState.update { it.copy(chapters = chapters) }
                }
            }
        } else {
            _uiState.update { it.copy(chapters = emptyList()) }
        }
        buildBrowseItems()
    }

    fun selectChapter(chapterId: Long?) {
        _uiState.update { it.copy(currentChapterId = chapterId) }
        buildBrowseItems()
    }

    fun toggleFavorite(mistake: Mistake) {
        viewModelScope.launch {
            repository.setFavorite(mistake.id, !mistake.isFavorite)
        }
    }

    fun toggleTop(mistake: Mistake) {
        viewModelScope.launch {
            repository.setTop(mistake.id, !mistake.isTop)
        }
    }

    private fun buildBrowseItems() {
        val state = _uiState.value
        val filtered = allMistakes.filter { mistake ->
            val matchSubject = state.currentSubjectId == null || mistake.subjectId == state.currentSubjectId
            val matchChapter = state.currentChapterId == null || mistake.chapterId == state.currentChapterId
            matchSubject && matchChapter
        }

        val items = filtered.map { mistake ->
            val records = allRecords
                .filter { it.mistakeId == mistake.id }
                .filter { it.result != ReviewResult.SKIP }
                .sortedBy { it.reviewDate }

            val pattern = records.joinToString("") { rec ->
                when (rec.result) {
                    ReviewResult.CORRECT -> "✓"
                    ReviewResult.WRONG -> "✗"
                    else -> ""
                }
            }
            val correct = records.count { it.result == ReviewResult.CORRECT }
            val wrong = records.count { it.result == ReviewResult.WRONG }

            val ebbinghaus = allRecords
                .filter { it.mistakeId == mistake.id }
                .maxByOrNull { it.reviewDate }
                ?.correctCount ?: 0

            BrowseItem(
                mistake, pattern, correct, wrong,
                ebbinghausCount = ebbinghaus,
                chapterName = chapterMap[mistake.chapterId]?.name ?: "",
                subjectName = subjectMap[mistake.subjectId]?.name ?: "",
                knowledgePointName = mistake.knowledgePointId?.let { kpMap[it]?.name } ?: ""
            )
        }.sortedWith(
            compareByDescending<BrowseItem> { it.mistake.isTop }
                .thenByDescending { it.wrongCount }
                .thenByDescending { it.mistake.createdAt }
        )

        _uiState.update { it.copy(items = items) }
    }
}
