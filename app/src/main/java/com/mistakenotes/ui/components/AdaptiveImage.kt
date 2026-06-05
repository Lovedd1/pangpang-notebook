package com.mistakenotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 自适应大小图片：根据原图宽高比计算 displayHeight，
 * 宽度 = 容器宽，高度 = 容器宽 / ratio，限 maxImageHeight（屏幕 2/3），
 * 始终 ContentScale.Fit，不裁剪图片。
 *
 * @param file 图片文件
 * @param onClick 点击图片回调（一般弹 ImagePreviewDialog）
 * @param maxHeight 上限高度（默认屏幕 2/3）
 * @param modifier 外部 modifier
 */
@Composable
fun AdaptiveImage(
    file: File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp? = null
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    // 默认上限 = 屏幕高度 * 0.66
    val resolvedMaxHeight = maxHeight
        ?: with(density) { (configuration.screenHeightDp * 0.66f).dp }

    var ratio by remember(file.path) { mutableStateOf<Float?>(null) }

    LaunchedEffect(file.path) {
        ratio = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(file)
                    .allowHardware(false)
                    .build()
                val result = ImageLoader(context).execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val w = drawable.intrinsicWidth
                    val h = drawable.intrinsicHeight
                    if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        val containerWidth = maxWidth

        if (ratio == null) {
            // 比例未知：兜底显示 Loading + 占位高度
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resolvedMaxHeight)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中…")
            }
        } else {
            val r = ratio!!
            // displayWidth = containerWidth（限宽）
            // displayHeight = containerWidth / r
            val computedHeight = containerWidth / r
            // 限高：height 不超过 resolvedMaxHeight
            val finalHeight = if (computedHeight > resolvedMaxHeight) {
                resolvedMaxHeight
            } else {
                computedHeight
            }
            // 限高时宽度同步缩
            val finalWidth = if (computedHeight > resolvedMaxHeight) {
                resolvedMaxHeight * r
            } else {
                containerWidth
            }

            Box(
                modifier = Modifier
                    .width(finalWidth)
                    .height(finalHeight)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
