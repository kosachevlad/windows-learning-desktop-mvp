package ua.school.windowsdesktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AppLoadingOverlay() {
    val transition = rememberInfiniteTransition(label = "loader")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "loader rotation",
    )
    Box(Modifier.fillMaxSize().background(Color(0x18000000)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(62.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val innerRadius = size.minDimension * .28f
            val outerRadius = size.minDimension * .43f
            repeat(12) { index ->
                val angle = (index * 30f + rotation.value - 90f) * PI.toFloat() / 180f
                val start = Offset(center.x + cos(angle) * innerRadius, center.y + sin(angle) * innerRadius)
                val end = Offset(center.x + cos(angle) * outerRadius, center.y + sin(angle) * outerRadius)
                val alpha = .18f + .82f * ((index + 1) / 12f)
                drawLine(Color(0xFF19A9E6).copy(alpha = alpha), start, end, strokeWidth = size.minDimension * .12f, cap = StrokeCap.Round)
            }
        }
    }
}
