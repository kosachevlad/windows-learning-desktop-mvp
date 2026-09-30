package ua.school.windowsdesktop

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun DesktopViewport(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val desktopWidth = if (portrait) maxOf(maxWidth, configuration.screenHeightDp.dp) else maxWidth
        val overflow = desktopWidth > viewportWidth
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f)
                    .then(if (overflow) Modifier.horizontalScroll(scroll) else Modifier)
                    .width(desktopWidth).fillMaxHeight(),
            ) { content() }
            if (overflow) {
                var trackWidth by remember { mutableFloatStateOf(1f) }
                val ratio = viewportWidth / desktopWidth
                val thumbWidth = (trackWidth * ratio).coerceAtLeast(24f).coerceAtMost(trackWidth)
                val travel = (trackWidth - thumbWidth).coerceAtLeast(1f)
                Canvas(
                    Modifier.fillMaxWidth().height(20.dp).background(Color(0xFFE4E7EB))
                        .onSizeChanged { trackWidth = it.width.toFloat().coerceAtLeast(1f) }
                        .draggable(rememberDraggableState { delta ->
                            val target = (scroll.value + delta / travel * scroll.maxValue).toInt().coerceIn(0, scroll.maxValue)
                            scope.launch { scroll.scrollTo(target) }
                        }, Orientation.Horizontal)
                        .pointerInput(travel, thumbWidth, scroll.maxValue) {
                            detectTapGestures { point ->
                                val fraction = ((point.x - thumbWidth / 2) / travel).coerceIn(0f, 1f)
                                scope.launch { scroll.scrollTo((fraction * scroll.maxValue).toInt()) }
                            }
                        },
                ) {
                    val fraction = scroll.value.toFloat() / scroll.maxValue.coerceAtLeast(1)
                    drawRoundRect(Color(0xFF8B949E), Offset(fraction * travel, 4.dp.toPx()),
                        Size(thumbWidth, size.height - 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}
