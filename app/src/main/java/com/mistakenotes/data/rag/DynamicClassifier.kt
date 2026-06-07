package com.mistakenotes.data.rag

import android.net.Uri
import com.mistakenotes.domain.model.Subject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态分类器：每次 classify 时根据当前 Key 状态选择 Mock 或 Real
 *
 * 解决原 ClassifierModule 只在 DI 初始化时检查一次 Key 的 bug：
 * - 启动时无 Key → Mock 永久生效，之后填 Key 也不会切到 Real
 * - 现在每次调用 classify 都重新检查，填 Key 后立即生效（无需重启）
 */
@Singleton
class DynamicClassifier @Inject constructor(
    private val mock: MockKnowledgeClassifier,
    private val real: DeepSeekKnowledgeClassifier,
    private val keyStore: ApiKeyProvider
) : KnowledgeClassifier {

    override suspend fun classify(
        questionImage: Uri,
        subjectHint: Subject?
    ): ClassifyResult {
        val useReal = keyStore.hasKey()
        return if (useReal) {
            real.classify(questionImage, subjectHint)
        } else {
            mock.classify(questionImage, subjectHint)
        }
    }
}
