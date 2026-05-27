package com.mistakenotes.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    val density = context.resources.displayMetrics.density

    // Crop area size (fixed square, 70% of screen width)
    val screenWidthPx = context.resources.displayMetrics.widthPixels
    val cropSizePx = (screenWidthPx * 0.7f).roundToInt()
    val cropSizeDp = (cropSizePx / density).dp

    // Image display: fill width, constrained height
    val displayWidth = screenWidthPx
    val imageRatio = sourceBitmap.height.toFloat() / sourceBitmap.width
    val displayHeight = (displayWidth * imageRatio).roundToInt()

    // Image offset/scale state
    // offset: image translation (positive = image moves right/down)
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    // Crop rect in screen space (centered)
    val cropCenterX = displayWidth / 2f
    val cropCenterY = displayHeight / 2f
    val cropLeft = cropCenterX - cropSizePx / 2f
    val cropTop = cropCenterY - cropSizePx / 2f

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
                // Calculate crop region in bitmap space
                val bw = sourceBitmap.width.toFloat()
                val bh = sourceBitmap.height.toFloat()
                val displayToBitmap = bw / displayWidth

                // Image center in screen after offset
                val imgCenterX = displayWidth / 2f - offsetX
                val imgCenterY = displayHeight / 2f - offsetY

                // Crop center in image-local space (before scale)
                val cropCenterInImgX = (cropCenterX - imgCenterX) / scale + displayWidth / 2f
                val cropCenterInImgY = (cropCenterY - imgCenterY) / scale + displayHeight / 2f

                // Crop bounds in image-local space
                val halfW = (cropSizePx / scale) / 2f
                val halfH = (cropSizePx / scale) / 2f
                val srcX = ((cropCenterInImgX - halfW) * displayToBitmap).roundToInt().coerceIn(0, sourceBitmap.width)
                val srcY = ((cropCenterInImgY - halfH) * displayToBitmap).roundToInt().coerceIn(0, sourceBitmap.height)
                val srcW = ((cropSizePx / scale) * displayToBitmap).roundToInt().coerceIn(0, sourceBitmap.width - srcX)
                val srcH = ((cropSizePx / scale) * displayToBitmap).roundToInt().coerceIn(0, sourceBitmap.height - srcY)

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

        // Crop area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                // Image with transform
                androidx.compose.foundation.Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = "裁剪原图",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetX
                            translationY = offsetY
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Fit
                )

                // Crop overlay (fixed position on screen)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cropRect = Rect(
                        Offset(cropLeft, cropTop),
                        Size(cropSizePx.toFloat(), cropSizePx.toFloat())
                    )

                    // Dim outside
                    drawRect(Color.Black.copy(alpha = 0.5f))
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
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Corner brackets for visual hint
                    val cornerLen = 24.dp.toPx()
                    val c = AmberGold
                    // Top-left
                    drawLine(c, Offset(cropRect.left, cropRect.top), Offset(cropRect.left + cornerLen, cropRect.top), 3.dp.toPx())
                    drawLine(c, Offset(cropRect.left, cropRect.top), Offset(cropRect.left, cropRect.top + cornerLen), 3.dp.toPx())
                    // Top-right
                    drawLine(c, Offset(cropRect.right, cropRect.top), Offset(cropRect.right - cornerLen, cropRect.top), 3.dp.toPx())
                    drawLine(c, Offset(cropRect.right, cropRect.top), Offset(cropRect.right, cropRect.top + cornerLen), 3.dp.toPx())
                    // Bottom-left
                    drawLine(c, Offset(cropRect.left, cropRect.bottom), Offset(cropRect.left + cornerLen, cropRect.bottom), 3.dp.toPx())
                    drawLine(c, Offset(cropRect.left, cropRect.bottom), Offset(cropRect.left, cropRect.bottom - cornerLen), 3.dp.toPx())
                    // Bottom-right
                    drawLine(c, Offset(cropRect.right, cropRect.bottom), Offset(cropRect.right - cornerLen, cropRect.bottom), 3.dp.toPx())
                    drawLine(c, Offset(cropRect.right, cropRect.bottom), Offset(cropRect.right, cropRect.bottom - cornerLen), 3.dp.toPx())
                }
            }
        }

        // Hint
        Text(
            "拖动/缩放图片调整裁剪区域",
            color = TextCream.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }
}
