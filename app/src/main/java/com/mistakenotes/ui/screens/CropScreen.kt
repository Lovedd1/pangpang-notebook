package com.mistakenotes.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mistakenotes.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

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

    // Scale image to fit screen while maintaining aspect ratio
    val density = context.resources.displayMetrics.density
    val maxWidth = context.resources.displayMetrics.widthPixels
    val maxHeight = (context.resources.displayMetrics.heightPixels * 0.55f).roundToInt()
    val imageScale = minOf(
        maxWidth.toFloat() / sourceBitmap.width,
        maxHeight.toFloat() / sourceBitmap.height,
        1f
    )
    val displayWidth = (sourceBitmap.width * imageScale).roundToInt()
    val displayHeight = (sourceBitmap.height * imageScale).roundToInt()

    // Crop rectangle is square, centered, sized to 80% of min display dimension
    val cropSizeDp = minOf(displayWidth, displayHeight) * 0.8f
    val cropSizePx = cropSizeDp.roundToInt()

    var offsetX by remember { mutableStateOf((displayWidth - cropSizePx) / 2f) }
    var offsetY by remember { mutableStateOf((displayHeight - cropSizePx) / 2f) }
    var scale by remember { mutableStateOf(1f) }

    val cropLeft = offsetX
    val cropTop = offsetY
    val cropRight = offsetX + cropSizePx
    val cropBottom = offsetY + cropSizePx

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
                // Crop and save
                val bitmapWidth = sourceBitmap.width
                val bitmapHeight = sourceBitmap.height
                val scaleX = bitmapWidth.toFloat() / displayWidth * scale
                val scaleY = bitmapHeight.toFloat() / displayHeight * scale

                val srcX = (cropLeft * scaleX).roundToInt().coerceIn(0, bitmapWidth)
                val srcY = (cropTop * scaleY).roundToInt().coerceIn(0, bitmapHeight)
                val srcW = (cropSizePx * scaleX).roundToInt().coerceIn(0, bitmapWidth - srcX)
                val srcH = (cropSizePx * scaleY).roundToInt().coerceIn(0, bitmapHeight - srcY)

                val cropped = Bitmap.createBitmap(sourceBitmap, srcX, srcY, srcW, srcH)
                val outFile = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                cropped.recycle()
                onCropComplete(Uri.fromFile(outFile))
            }) {
                Text("确认", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Image + crop overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = (displayWidth / density).dp,
                        height = (displayHeight / density).dp
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3f)
                            offsetX = (offsetX + pan.x).coerceIn(
                                (displayWidth - cropSizePx) * (1f - scale),
                                0f
                            )
                            offsetY = (offsetY + pan.y).coerceIn(
                                (displayHeight - cropSizePx) * (1f - scale),
                                0f
                            )
                        }
                    }
            ) {
                // Source image
                androidx.compose.foundation.Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = "裁剪原图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Crop overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cropRect = Rect(
                        Offset(cropLeft, cropTop),
                        Size(cropSizePx.toFloat(), cropSizePx.toFloat())
                    )

                    // Dim areas outside crop
                    drawRect(Color.Black.copy(alpha = 0.55f))
                    clipRect(
                        left = cropRect.left,
                        top = cropRect.top,
                        right = cropRect.right,
                        bottom = cropRect.bottom
                    ) {
                        drawRect(Color.Transparent)
                    }

                    // Crop border
                    drawRect(
                        color = AmberGold,
                        topLeft = cropRect.topLeft,
                        size = cropRect.size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Bottom hint
        Text(
            "拖动图片调整裁剪区域",
            color = TextCream.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
}
