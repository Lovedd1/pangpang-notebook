package com.mistakenotes.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.rag.ApiKeyProvider
import com.mistakenotes.ui.theme.AmberGold
import com.mistakenotes.ui.theme.InkStoneBlack
import com.mistakenotes.ui.theme.TextCream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = if (apiKeyProvider.hasKey()) apiKeyProvider.get() else ""
            _uiState.update { it.copy(apiKey = current, hasKey = current.isNotBlank()) }
        }
    }

    fun setApiKey(key: String) = _uiState.update { it.copy(apiKey = key) }

    fun save() = viewModelScope.launch {
        apiKeyProvider.setKey(_uiState.value.apiKey.trim())
        _uiState.update { it.copy(hasKey = true, message = "已保存") }
    }

    fun clear() = viewModelScope.launch {
        apiKeyProvider.clearKey()
        _uiState.update { it.copy(apiKey = "", hasKey = false, message = "已清空") }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

data class SettingsUiState(
    val apiKey: String = "",
    val hasKey: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = AmberGold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = AmberGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InkStoneBlack)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = InkStoneBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "DeepSeek API Key",
                color = TextCream,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "用于 AI 自动归类题目。仅存本机，不上传。",
                color = TextCream.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.setApiKey(it) },
                label = { Text("API Key") },
                placeholder = { Text("sk-...", color = TextCream.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextCream,
                    unfocusedTextColor = TextCream
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = InkStoneBlack
                    )
                ) {
                    Icon(Icons.Filled.Save, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (uiState.hasKey) "更新" else "保存")
                }
                OutlinedButton(
                    onClick = { viewModel.clear() },
                    enabled = uiState.hasKey
                ) {
                    Text("清空", color = TextCream)
                }
            }
            if (uiState.hasKey) {
                Text(
                    "✓ API Key 已配置",
                    color = AmberGold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
