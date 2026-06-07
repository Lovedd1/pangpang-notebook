package com.mistakenotes.di

import com.mistakenotes.data.rag.ApiKeyProvider
import com.mistakenotes.data.rag.DeepSeekApi
import com.mistakenotes.data.rag.DeepSeekKnowledgeClassifier
import com.mistakenotes.data.rag.DynamicClassifier
import com.mistakenotes.data.rag.KnowledgeBase
import com.mistakenotes.data.rag.KnowledgeBaseLoader
import com.mistakenotes.data.rag.KnowledgeClassifier
import com.mistakenotes.data.rag.MockKnowledgeClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Provider
import javax.inject.Singleton

/**
 * KnowledgeClassifier 绑定模块
 *
 * 使用 [DynamicClassifier] 每次调用时检查 Key：
 * - 有 Key → [DeepSeekKnowledgeClassifier]（真实分类）
 * - 无 Key → [MockKnowledgeClassifier]（mock 返回）
 *
 * 用户在 SettingsScreen 填入 Key 后**立即生效**，无需重启 App。
 */
@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun provideKnowledgeClassifier(
        dynamic: DynamicClassifier
    ): KnowledgeClassifier = dynamic

    @Provides
    @Singleton
    fun provideKnowledgeBase(loader: KnowledgeBaseLoader): KnowledgeBase =
        loader.load()

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient =
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: okhttp3.OkHttpClient): retrofit2.Retrofit {
        val jsonConfig = Json {
            encodeDefaults = true       // 必须：否则 model/temperature 默认值被省略 → DeepSeek 400
            ignoreUnknownKeys = true    // 容错：忽略 DeepSeek 返回的未知字段
        }
        return retrofit2.Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(client)
            .addConverterFactory(
                jsonConfig.asConverterFactory("application/json".toMediaType())
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideDeepSeekApi(retrofit: retrofit2.Retrofit): DeepSeekApi =
        retrofit.create(DeepSeekApi::class.java)
}
