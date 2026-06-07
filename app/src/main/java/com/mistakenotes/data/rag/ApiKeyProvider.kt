package com.mistakenotes.data.rag

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore by preferencesDataStore(name = "rag_api_key")

/**
 * DeepSeek API Key 存取（DataStore 包装）
 *
 * Key 存于 DataStore（Preferences），仅本机保留，不上传。
 */
@Singleton
class ApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyFlow = context.apiKeyDataStore.data

    /** Key 是否已设置（用于 DI 切换 Mock/Real） */
    suspend fun hasKey(): Boolean = !keyFlow.first()[KEY].isNullOrBlank()

    /** 同步版 hasKey（DI 启动时不能用 suspend） */
    fun hasKeySync(): Boolean = runCatching {
        kotlinx.coroutines.runBlocking { hasKey() }
    }.getOrDefault(false)

    /** 取 Key（DI 切换时被 ClassifierModule 调用——本期为简化用 hasKey()） */
    suspend fun get(): String = keyFlow.first()[KEY] ?: ""

    suspend fun setKey(key: String) {
        context.apiKeyDataStore.edit { it[KEY] = key }
    }

    suspend fun clearKey() {
        context.apiKeyDataStore.edit { it.remove(KEY) }
    }

    companion object {
        private val KEY = stringPreferencesKey("deepseek_api_key")
    }
}
