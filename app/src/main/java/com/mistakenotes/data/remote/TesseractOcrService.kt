package com.mistakenotes.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TesseractOcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TesseractOcrService"
        private const val TESSERACT_LANG = "chi_sim"  // 中文简体
        private const val DATA_PATH = "tesseract"
        private const val TESSDATA_DIR = "tessdata"
    }

    private var tessBaseApi: TessBaseAPI? = null

    sealed class RecognitionResult {
        data class Success(val text: String) : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()
    }

    /**
     * 初始化 Tesseract（需要中文语言包）
     */
    private suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Tesseract 期望目录结构: <dataPath>/tessdata/chi_sim.traineddata
            val dataPath = File(context.filesDir, DATA_PATH)
            val tessdataDir = File(dataPath, TESSDATA_DIR)

            if (!tessdataDir.exists()) {
                tessdataDir.mkdirs()
            }

            val langFile = File(tessdataDir, "$TESSERACT_LANG.traineddata")

            // 检查语言包是否存在
            if (!langFile.exists()) {
                // 尝试从 assets 复制
                copyLangFromAssets(langFile, TESSERACT_LANG)
            }

            if (!langFile.exists()) {
                Log.e(TAG, "语言包不存在: ${langFile.absolutePath}")
                return@withContext false
            }

            tessBaseApi = TessBaseAPI()
            tessBaseApi?.init(dataPath.absolutePath, TESSERACT_LANG)
            Log.d(TAG, "Tesseract 初始化成功, 语言包路径: ${langFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract 初始化失败: ${e.message}")
            false
        }
    }

    private fun copyLangFromAssets(langFile: File, lang: String) {
        try {
            val assetManager = context.assets
            // 尝试多种可能的 asset 路径
            val possiblePaths = listOf(
                "$lang.traineddata",
                "tessdata/$lang.traineddata",
                "tesseract/$lang.traineddata"
            )

            for (assetPath in possiblePaths) {
                try {
                    val inputStream = assetManager.open(assetPath)
                    val parentDir = langFile.parentFile
                    if (!parentDir.exists()) parentDir.mkdirs()
                    val outputStream = FileOutputStream(langFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    Log.d(TAG, "语言包已从 assets/$assetPath 复制到: ${langFile.absolutePath}")
                    return
                } catch (e: Exception) {
                    Log.d(TAG, "尝试 assets/$assetPath 失败: ${e.message}")
                }
            }
            Log.e(TAG, "无法从 assets 复制语言包")
        } catch (e: Exception) {
            Log.e(TAG, "复制语言包失败: ${e.message}")
        }
    }

    /**
     * 使用 Tesseract OCR 识别图片中的文字（中文）
     */
    suspend fun recognizeText(bitmap: Bitmap): RecognitionResult = withContext(Dispatchers.IO) {
        try {
            val api = tessBaseApi
            if (api == null) {
                if (!init()) {
                    return@withContext RecognitionResult.Error("Tesseract 未初始化，请确保 chi_sim 语言包已正确放置")
                }
            }

            val tessApi = tessBaseApi!!
            tessApi.setImage(bitmap)
            val text = tessApi.utF8Text
            tessApi.clear()

            if (text.isNotEmpty()) {
                Log.d(TAG, "Tesseract 识别成功: ${text.take(100)}")
                RecognitionResult.Success(text.trim())
            } else {
                RecognitionResult.Error("未识别到文字内容")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract 识别失败: ${e.message}")
            RecognitionResult.Error(e.message ?: "识别失败")
        }
    }

    fun release() {
        tessBaseApi?.end()
        tessBaseApi = null
    }
}