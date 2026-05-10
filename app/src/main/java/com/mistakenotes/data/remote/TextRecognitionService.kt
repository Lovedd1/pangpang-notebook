package com.mistakenotes.data.remote

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TextRecognitionService @Inject constructor() {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )

    sealed class RecognitionResult {
        data class Success(val text: String) : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()
    }

    /**
     * 使用 ML Kit 本地识别图片中的文字
     * 完全离线，无需网络，隐私安全
     */
    suspend fun recognizeText(bitmap: Bitmap): RecognitionResult {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text
                    if (text.isNotEmpty()) {
                        continuation.resume(RecognitionResult.Success(text))
                    } else {
                        continuation.resume(RecognitionResult.Error("未识别到文字内容"))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(RecognitionResult.Error(e.message ?: "识别失败"))
                }
        }
    }
}