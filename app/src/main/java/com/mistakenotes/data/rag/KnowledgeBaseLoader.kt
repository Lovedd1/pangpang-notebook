package com.mistakenotes.data.rag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 APK assets/json/accounting_knowledge_points.json 加载知识库
 *
 * 启动时调用一次，结果缓存在 SingletonComponent 作用域。
 */
@Singleton
class KnowledgeBaseLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): KnowledgeBase {
        val text = context.assets.open("json/accounting_knowledge_points.json")
            .bufferedReader()
            .use { it.readText() }
        val file = json.decodeFromString(KnowledgeBaseFile.serializer(), text)
        return KnowledgeBase(file.knowledgePoints)
    }
}
