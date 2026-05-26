package com.mistakenotes.ui.screens

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
    val wrongCount: Int
)

data class BrowseUiState(
    val subjects: List<Subject> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val currentSubjectId: Long? = null,
    val currentChapterId: Long? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: MistakeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var allMistakes = listOf<Mistake>()
    private var allRecords = listOf<com.mistakenotes.domain.model.ReviewRecord>()

    init {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjects ->
                _uiState.update { it.copy(subjects = subjects, isLoading = false) }
            }
        }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getAllMistakes(),
                repository.getAllReviewRecords()
            ) { mistakes, records ->
                allMistakes = mistakes
                allRecords = records
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
                .filter { it.result != com.mistakenotes.domain.model.ReviewResult.SKIP }
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

            BrowseItem(mistake, pattern, correct, wrong)
        }.sortedByDescending { it.mistake.createdAt }

        _uiState.update { it.copy(items = items) }
    }
}
