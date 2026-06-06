package com.mistakenotes.di

import com.mistakenotes.data.rag.ApiKeyProvider
import com.mistakenotes.data.rag.DeepSeekKnowledgeClassifier
import com.mistakenotes.data.rag.KnowledgeClassifier
import com.mistakenotes.data.rag.MockKnowledgeClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * KnowledgeClassifier 绑定模块
 *
 * 运行时根据 [ApiKeyProvider.hasKeySync] 切换：
 * - 有 Key → [DeepSeekKnowledgeClassifier]（真实分类）
 * - 无 Key → [MockKnowledgeClassifier]（mock 返回）
 *
 * 用户在 SettingsScreen 填入 Key 后下次启动即生效。
 */
@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun provideKnowledgeClassifier(
        mock: Provider<MockKnowledgeClassifier>,
        real: Provider<DeepSeekKnowledgeClassifier>,
        keyStore: ApiKeyProvider
    ): KnowledgeClassifier = if (keyStore.hasKeySync()) real.get() else mock.get()
}
