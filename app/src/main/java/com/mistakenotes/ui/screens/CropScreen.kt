package com.mistakenotes.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private const val MIN_CROP_PX = 60f
private const val CORNER_SIZE_PX = 44f

@Composable
fun CropScreen(
    sourceUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sourceBitmap = remember {
        context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    if (sourceBitmap == null) {
        onCancel()
        return
    }

    var containerSize by remember { mutableStateOf(IntSize(0, 0)) }
    val density = context.resources.displayMetrics.density

    // Initial crop rect: centered square, 70% of container
    val initSize = (minOf(containerSize.width, containerSize.height) * 0.7f).roundToInt()
        .coerceAtLeast(MIN_CROP_PX.roundToInt())
    var cropLeft by remember { mutableStateOf((containerSize.width - initSize) / 2f) }
    var cropTop by remember { mutableStateOf((containerSize.height - initSize) / 2f) }
    var cropRight by remember { mutableStateOf(cropLeft + initSize) }
    var cropBottom by remember { mutableStateOf(cropTop + initSize) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkStoneBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("取消", color = TextCream.copy(alpha = 0.7f), fontSize = 16.sp)
            }
            Text("裁剪图片", color = TextCream, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = {
                if (containerSize.width <= 0 || containerSize.height <= 0) return@TextButton
                val scaleX = sourceBitmap.width.toFloat() / containerSize.width
                val scaleY = sourceBitmap.height.toFloat() / containerSize.height
                val srcX = (cropLeft * scaleX).roundToInt().coerceIn(0, sourceBitmap.width)
                val srcY = (cropTop * scaleY).roundToInt().coerceIn(0, sourceBitmap.height)
                val srcW = ((cropRight - cropLeft) * scaleX).roundToInt().coerceIn(0, sourceBitmap.width - srcX)
                val srcH = ((cropBottom - cropTop) * scaleY).roundToInt().coerceIn(0, sourceBitmap.height - srcY)
                if (srcW > 0 && srcH > 0) {
                    val cropped = Bitmap.createBitmap(sourceBitmap, srcX, srcY, srcW, srcH)
                    val outFile = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    cropped.recycle()
                    onCropComplete(Uri.fromFile(outFile))
                }
            }) {
                Text("确认", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Image + crop overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { containerSize = it }
        ) {
            // Image
            if (containerSize.width > 0) {
                androidx.compose.foundation.Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = "裁剪原图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Crop overlay with drag support
            if (containerSize.width > 0) {
                val cropW = cropRight - cropLeft
                val cropH = cropBottom - cropTop
                val halfCorner = (CORNER_SIZE_PX / density).dp / 2

                // Center drag area (move)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newLeft = (cropLeft + dragAmount.x).coerceIn(0f, containerSize.width - cropW)
                                val newTop = (cropTop + dragAmount.y).coerceIn(0f, containerSize.height - cropH)
                                cropLeft = newLeft
                                cropTop = newTop
                                cropRight = newLeft + cropW
                                cropBottom = newTop + cropH
                            }
                        }
                )

                // TL corner resize handle
                Box(
                    modifier = Modifier
                        .offset(x = (cropLeft / density).dp - halfCorner, y = (cropTop / density).dp - halfCorner)
                        .size((CORNER_SIZE_PX / density).dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropRight - MIN_CROP_PX)
                                cropTop = (cropTop + dragAmount.y).coerceIn(0f, cropBottom - MIN_CROP_PX)
                            }
                        }
                        .background(AmberGold, CircleShape)
                )

                // TR corner resize handle
                Box(
                    modifier = Modifier
                        .offset(x = (cropRight / density).dp - halfCorner, y = (cropTop / density).dp - halfCorner)
                        .size((CORNER_SIZE_PX / density).dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + MIN_CROP_PX, containerSize.width.toFloat())
                                cropTop = (cropTop + dragAmount.y).coerceIn(0f, cropBottom - MIN_CROP_PX)
                            }
                        }
                        .background(AmberGold, CircleShape)
                )

                // BL corner resize handle
                Box(
                    modifier = Modifier
                        .offset(x = (cropLeft / density).dp - halfCorner, y = (cropBottom / density).dp - halfCorner)
                        .size((CORNER_SIZE_PX / density).dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropRight - MIN_CROP_PX)
                                cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + MIN_CROP_PX, containerSize.height.toFloat())
                            }
                        }
                        .background(AmberGold, CircleShape)
                )

                // BR corner resize handle
                Box(
                    modifier = Modifier
                        .offset(x = (cropRight / density).dp - halfCorner, y = (cropBottom / density).dp - halfCorner)
                        .size((CORNER_SIZE_PX / density).dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + MIN_CROP_PX, containerSize.width.toFloat())
                                cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + MIN_CROP_PX, containerSize.height.toFloat())
                            }
                        }
                        .background(AmberGold, CircleShape)
                )

                // Dim overlay + border
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cropRect = Rect(
                        Offset(cropLeft, cropTop),
                        Size(cropW, cropH)
                    )
                    drawRect(Color.Black.copy(alpha = 0.45f))
                    clipRect(
                        left = cropRect.left,
                        top = cropRect.top,
                        right = cropRect.right,
                        bottom = cropRect.bottom
                    ) {
                        drawRect(Color.Transparent)
                    }
                    drawRect(
                        color = AmberGold,
                        topLeft = cropRect.topLeft,
                        size = cropRect.size,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Hint
        Text(
            "拖动裁剪框移动位置，拖动四角调整大小",
            color = TextCream.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
}
