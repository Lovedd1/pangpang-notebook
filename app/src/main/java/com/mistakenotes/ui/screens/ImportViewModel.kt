package com.mistakenotes.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mistakenotes.data.remote.TesseractOcrService
import com.mistakenotes.data.repository.MistakeRepository
import com.mistakenotes.domain.model.Mistake
import com.mistakenotes.domain.model.QuestionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ImportUiState(
    val imageUri: Uri? = null,
    val questionType: QuestionType = QuestionType.CHOICE,
    val recognizedQuestion: String = "",   // 识别出的题目（只读显示）
    val correctAnswer: String = "",        // 正确答案（手动输入）
    val subject: String = "数学",
    val selectedTags: Set<String> = emptySet(),
    val explanation: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    // 文字识别相关
    val isRecognizing: Boolean = false,
    val recognizedText: String = "",
    val recognizedTextVisible: Boolean = false,
    // 裁剪相关
    val croppedFileUri: Uri? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: MistakeRepository,
    private val tesseractOcrService: TesseractOcrService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState

    fun setImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(
            imageUri = uri,
            recognizedTextVisible = false,
            recognizedText = ""
        )
    }

    fun setQuestionType(type: QuestionType) {
        _uiState.value = _uiState.value.copy(questionType = type)
    }

    fun setCorrectAnswer(answer: String) {
        _uiState.value = _uiState.value.copy(correctAnswer = answer)
    }

    fun setSubject(subject: String) {
        _uiState.value = _uiState.value.copy(subject = subject)
    }

    fun toggleTag(tag: String) {
        val currentTags = _uiState.value.selectedTags
        _uiState.value = _uiState.value.copy(
            selectedTags = if (currentTags.contains(tag)) {
                currentTags - tag
            } else {
                currentTags + tag
            }
        )
    }

    fun setExplanation(explanation: String) {
        _uiState.value = _uiState.value.copy(explanation = explanation)
    }

    // 最近一次裁剪文件路径
    private var lastCroppedFile: File? = null

    // 临时文件URI（用于裁剪）
    var tempImageUri: Uri? = null

    /**
     * 启动系统裁剪 - 使用 CROP intent
     */
    fun startCrop(originalUri: Uri): android.content.Intent? {
        return try {
            // 创建临时文件用于裁剪输出
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "CROP_TEMP_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val cropFile = File(imagesDir, fileName)
            lastCroppedFile = cropFile
            tempImageUri = Uri.fromFile(cropFile)

            // 尝试使用系统裁剪功能
            val cropIntent = android.content.Intent("com.android.camera.action.CROP")
            cropIntent.setDataAndType(originalUri, "image/*")
            cropIntent.putExtra("crop", "true")
            cropIntent.putExtra("aspectX", 0)
            cropIntent.putExtra("aspectY", 0)
            cropIntent.putExtra("output", Uri.fromFile(cropFile))
            cropIntent.putExtra("return-data", false)
            cropIntent.putExtra("noFaceDetection", true)
            cropIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cropIntent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY)
            cropIntent
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 启动快速裁剪（使用 ACTION_VIEW）
     */
    fun startQuickCrop(originalUri: Uri): android.content.Intent {
        return android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(originalUri, "image/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 刷新裁剪后的图片
     */
    fun refreshCroppedImage() {
        val croppedUri = lastCroppedFile?.let { Uri.fromFile(it) } ?: tempImageUri
        if (croppedUri != null) {
            _uiState.value = _uiState.value.copy(imageUri = croppedUri, recognizedTextVisible = false, recognizedText = "")
        }
    }

    /**
     * 使用 Tesseract OCR 识别图片文字
     */
    fun recognizeText() {
        val bitmap = loadBitmapFromUri(_uiState.value.imageUri)
        if (bitmap == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "无法加载图片，请重新选择"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecognizing = true, errorMessage = null)
            try {
                val result = tesseractOcrService.recognizeText(bitmap)
                when (result) {
                    is TesseractOcrService.RecognitionResult.Success -> {
                        android.util.Log.d("ImportViewModel", "识别结果: ${result.text}")
                        _uiState.value = _uiState.value.copy(
                            isRecognizing = false,
                            recognizedText = result.text,
                            recognizedTextVisible = true,
                            recognizedQuestion = result.text,
                            correctAnswer = ""
                        )
                    }
                    is TesseractOcrService.RecognitionResult.Error -> {
                        android.util.Log.e("ImportViewModel", "识别失败: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isRecognizing = false,
                            errorMessage = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ImportViewModel", "异常: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isRecognizing = false,
                    errorMessage = e.message ?: "识别失败"
                )
            }
        }
    }

    fun dismissRecognizedText() {
        _uiState.value = _uiState.value.copy(
            recognizedTextVisible = false,
            recognizedQuestion = ""
        )
    }

    private fun loadBitmapFromUri(uri: Uri?): Bitmap? {
        if (uri == null) return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // 旋转图片（根据 EXIF 信息）
            val rotatedBitmap = rotateImageIfRequired(originalBitmap, uri)
            rotatedBitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees)
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    fun saveMistake(imagePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val state = _uiState.value
                val mistake = Mistake(
                    subject = state.subject,
                    tags = state.selectedTags.joinToString(","),
                    questionImagePath = imagePath,
                    correctAnswer = state.correctAnswer,
                    explanation = state.explanation,
                    questionType = state.questionType
                )
                repository.insertMistake(mistake)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "保存失败"
                )
            }
        }
    }

    /**
     * 将 Bitmap 保存到应用私有目录，返回 Uri
     */
    fun saveBitmapToFile(bitmap: Bitmap): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "TEMP_IMG_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val imageFile = File(imagesDir, fileName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            Uri.fromFile(imageFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 Bitmap 保存为正式图片（用于最终保存）
     */
    fun saveFinalBitmap(bitmap: Bitmap): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "IMG_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val imageFile = File(imagesDir, fileName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            Uri.fromFile(imageFile)
        } catch (e: Exception) {
            null
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState()
    }
}