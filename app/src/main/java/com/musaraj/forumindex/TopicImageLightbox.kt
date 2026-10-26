package com.musaraj.forumindex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

internal data class TopicImage(val url: URL, val label: String)

@Composable
internal fun TopicImageLightbox(image: TopicImage, loader: suspend (String) -> android.graphics.Bitmap = { withContext(Dispatchers.IO) { loadTopicBitmap(it) } }, onDismiss: () -> Unit) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var retry by remember(image) { mutableIntStateOf(0) }
    var bitmap by remember(image) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(image) { mutableStateOf(false) }
    LaunchedEffect(image, retry) {
        failed = false
        try { bitmap = loader(image.url.toString()) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("image-lightbox")) {
                Text(image.label, color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { zoom = (zoom - 1).coerceAtLeast(1f); offset = Offset.Zero }, enabled = zoom > 1) { Text("Zoom out") }
                    TextButton(onClick = { zoom = (zoom + 1).coerceAtMost(5f) }, enabled = zoom < 5,
                        modifier = Modifier.testTag("image-lightbox-zoom-in")) { Text("Zoom in") }
                    TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Fit") }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("image-lightbox-close")) { Text("Close") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                    val width = constraints.maxWidth.toFloat()
                    val height = constraints.maxHeight.toFloat()
                    when {
                        failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couldn’t load image", color = Color.White)
                            Button(onClick = { retry++ }) { Text("Retry") }
                        }
                        bitmap == null -> CircularProgressIndicator()
                        else -> Image(bitmap!!.asImageBitmap(), image.label, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().testTag("image-lightbox-image")
                                .pointerInput(image) {
                                    detectTransformGestures { _, pan, scale, _ ->
                                        zoom = (zoom * scale).coerceIn(1f, 5f)
                                        val next = offset + pan
                                        offset = Offset(next.x.coerceIn(-width * (zoom - 1) / 2, width * (zoom - 1) / 2),
                                            next.y.coerceIn(-height * (zoom - 1) / 2, height * (zoom - 1) / 2))
                                    }
                                }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y })
                    }
                }
            }
        }
    }
}
