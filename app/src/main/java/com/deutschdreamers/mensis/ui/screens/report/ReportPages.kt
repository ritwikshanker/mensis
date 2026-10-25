package com.deutschdreamers.mensis.ui.screens.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.report.PdfPages
import com.deutschdreamers.mensis.ui.components.MsIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PaperShape = RoundedCornerShape(4.dp)

/** The full-screen view draws pages at twice the screen width, so text stays sharp when zoomed. */
private const val VIEWER_RENDER_SCALE = 2f
private const val VIEWER_MAX_RENDER_PX = 2400
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Page [index] drawn [widthPx] wide off the main thread; null until it is ready. */
@Composable
private fun pageBitmap(
    pages: PdfPages,
    index: Int,
    widthPx: Int,
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, pages, index, widthPx) {
        value =
            if (widthPx >
                0
            ) {
                withContext(Dispatchers.Default) { pages.render(index, widthPx)?.asImageBitmap() }
            } else {
                null
            }
    }
    return bitmap
}

/** One page of the preview list: "Page n of N" over a white sheet; tapping it opens [PdfPageViewer]. */
@Composable
fun PdfPageCard(
    pages: PdfPages,
    index: Int,
    onOpen: () -> Unit,
) {
    val label = stringResource(R.string.report_page, index + 1, pages.pageCount)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(pages.aspectRatios[index])
                    .shadow(2.dp, PaperShape)
                    .background(Color.White, PaperShape)
                    .clickable(onClickLabel = stringResource(R.string.report_page_open), onClick = onOpen)
                    .semantics { contentDescription = label },
        ) {
            pageBitmap(pages, index, constraints.maxWidth)?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Page [index] on its own, full screen, with pinch and double-tap zoom; back or the close button leave. */
@Composable
fun PdfPageViewer(
    pages: PdfPages,
    index: Int,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val label = stringResource(R.string.report_page, index + 1, pages.pageCount)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                // Taps that miss the page must not reach the screen underneath.
                .pointerInput(Unit) { detectTapGestures() }
                .safeDrawingPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                MsIcon(resId = R.drawable.ms_close, contentDescription = stringResource(R.string.report_page_close))
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
        ZoomablePage(pages, index, label, Modifier.weight(1f).fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun ZoomablePage(
    pages: PdfPages,
    index: Int,
    label: String,
    modifier: Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                            offset = Offset.Zero
                        },
                    )
                }.pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        // The page may move until its edge reaches the edge of the view, no further.
                        val maxX = size.width * (scale - 1f) / 2f
                        val maxY = size.height * (scale - 1f) / 2f
                        offset =
                            Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                    }
                },
    ) {
        val widthPx = (constraints.maxWidth * VIEWER_RENDER_SCALE).toInt().coerceAtMost(VIEWER_MAX_RENDER_PX)
        Box(
            modifier =
                Modifier
                    .aspectRatio(pages.aspectRatios[index])
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }.background(Color.White)
                    .semantics { contentDescription = label },
        ) {
            pageBitmap(pages, index, widthPx)?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
