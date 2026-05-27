package com.mistakenotes.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
private const val EDGE_BAR_LENGTH_PX = 56f
private const val EDGE_BAR_THICKNESS_PX = 8f

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

    // Crop rect — initialized once to cover full image
    var cropLeft by remember { mutableStateOf(-1f) }
    var cropTop by remember { mutableStateOf(-1f) }
    var cropRight by remember { mutableStateOf(-1f) }
    var cropBottom by remember { mutableStateOf(-1f) }

    // Image display bounds within container (for ContentScale.Fit)
    var imageLeft by remember { mutableStateOf(0f) }
    var imageTop by remember { mutableStateOf(0f) }
    var imageRight by remember { mutableStateOf(0f) }
    var imageBottom by remember { mutableStateOf(0f) }

    // One-time init: compute image bounds + set crop rect to cover image area
    if (cropLeft < 0f && containerSize.width > 0) {
        val imgW = sourceBitmap.width.toFloat()
        val imgH = sourceBitmap.height.toFloat()
        val imgAspect = imgW / imgH
        val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()

        val displayedW: Float
        val displayedH: Float
        if (imgAspect > containerAspect) {
            displayedW = containerSize.width.toFloat()
            displayedH = containerSize.width.toFloat() / imgAspect
        } else {
            displayedH = containerSize.height.toFloat()
            displayedW = containerSize.height.toFloat() * imgAspect
        }

        val offsetX = (containerSize.width - displayedW) / 2f
        val offsetY = (containerSize.height - displayedH) / 2f

        imageLeft = offsetX
        imageTop = offsetY
        imageRight = offsetX + displayedW
        imageBottom = offsetY + displayedH

        cropLeft = offsetX
        cropTop = offsetY
        cropRight = offsetX + displayedW
        cropBottom = offsetY + displayedH
    }

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

                val displayedW = imageRight - imageLeft
                val scale = sourceBitmap.width.toFloat() / displayedW

                val cropInImageLeft = (cropLeft - imageLeft).coerceIn(0f, displayedW)
                val cropInImageTop = (cropTop - imageTop).coerceIn(0f, imageBottom - imageTop)
                val cropInImageRight = (cropRight - imageLeft).coerceIn(0f, displayedW)
                val cropInImageBottom = (cropBottom - imageTop).coerceIn(0f, imageBottom - imageTop)

                val srcX = (cropInImageLeft * scale).roundToInt()
                val srcY = (cropInImageTop * scale).roundToInt()
                val srcW = ((cropInImageRight - cropInImageLeft) * scale).roundToInt().coerceAtLeast(1)
                val srcH = ((cropInImageBottom - cropInImageTop) * scale).roundToInt().coerceAtLeast(1)

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
                val halfCorner = (CORNER_SIZE_PX / density).dp / 2

                // Center drag area (move only, size preserved)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val w = cropRight - cropLeft
                                val h = cropBottom - cropTop
                                val newLeft = (cropLeft + dragAmount.x).coerceIn(imageLeft, imageRight - w)
                                val newTop = (cropTop + dragAmount.y).coerceIn(imageTop, imageBottom - h)
                                cropLeft = newLeft
                                cropTop = newTop
                                cropRight = newLeft + w
                                cropBottom = newTop + h
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
                                cropLeft = (cropLeft + dragAmount.x).coerceIn(imageLeft, cropRight - MIN_CROP_PX)
                                cropTop = (cropTop + dragAmount.y).coerceIn(imageTop, cropBottom - MIN_CROP_PX)
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
                                cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + MIN_CROP_PX, imageRight)
                                cropTop = (cropTop + dragAmount.y).coerceIn(imageTop, cropBottom - MIN_CROP_PX)
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
                                cropLeft = (cropLeft + dragAmount.x).coerceIn(imageLeft, cropRight - MIN_CROP_PX)
                                cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + MIN_CROP_PX, imageBottom)
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
                                cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + MIN_CROP_PX, imageRight)
                                cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + MIN_CROP_PX, imageBottom)
                            }
                        }
                        .background(AmberGold, CircleShape)
                )

                // --- Edge resize handles (bars at midpoints) ---
                val edgeBarW = (EDGE_BAR_LENGTH_PX / density).dp
                val edgeBarH = (EDGE_BAR_THICKNESS_PX / density).dp
                val edgeRadius = edgeBarH / 2

                // Top edge
                Box(
                    modifier = Modifier
                        .offset(
                            x = ((cropLeft + cropRight) / 2f / density).dp - edgeBarW / 2,
                            y = (cropTop / density).dp - edgeBarH / 2
                        )
                        .size(edgeBarW, edgeBarH)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropTop = (cropTop + dragAmount.y).coerceIn(imageTop, cropBottom - MIN_CROP_PX)
                            }
                        }
                        .background(AmberGold, RoundedCornerShape(edgeRadius))
                )

                // Bottom edge
                Box(
                    modifier = Modifier
                        .offset(
                            x = ((cropLeft + cropRight) / 2f / density).dp - edgeBarW / 2,
                            y = (cropBottom / density).dp - edgeBarH / 2
                        )
                        .size(edgeBarW, edgeBarH)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + MIN_CROP_PX, imageBottom)
                            }
                        }
                        .background(AmberGold, RoundedCornerShape(edgeRadius))
                )

                // Left edge
                Box(
                    modifier = Modifier
                        .offset(
                            x = (cropLeft / density).dp - edgeBarH / 2,
                            y = ((cropTop + cropBottom) / 2f / density).dp - edgeBarW / 2
                        )
                        .size(edgeBarH, edgeBarW)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropLeft = (cropLeft + dragAmount.x).coerceIn(imageLeft, cropRight - MIN_CROP_PX)
                            }
                        }
                        .background(AmberGold, RoundedCornerShape(edgeRadius))
                )

                // Right edge
                Box(
                    modifier = Modifier
                        .offset(
                            x = (cropRight / density).dp - edgeBarH / 2,
                            y = ((cropTop + cropBottom) / 2f / density).dp - edgeBarW / 2
                        )
                        .size(edgeBarH, edgeBarW)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + MIN_CROP_PX, imageRight)
                            }
                        }
                        .background(AmberGold, RoundedCornerShape(edgeRadius))
                )

                // Dim overlay + border
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cropRect = Rect(
                        Offset(cropLeft, cropTop),
                        Size(cropRight - cropLeft, cropBottom - cropTop)
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
            "拖动裁剪框移动位置，拖动四角或四边调整大小",
            color = TextCream.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
}
