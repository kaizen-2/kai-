package com.kai.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

val KaiCyan = Color(0xFF00E5FF)
val KaiBlue = Color(0xFF2979FF)
val KaiIndigo = Color(0xFF536DFE)
val KaiRed = Color(0xFFFF5252)
val KaiNavy = Color(0xFF050A18)

fun KaiState.color(): Color = when (this) {
    KaiState.IDLE -> KaiCyan
    KaiState.LISTENING -> Color(0xFF7DF9FF)
    KaiState.THINKING -> KaiIndigo
    KaiState.SPEAKING -> KaiCyan
    KaiState.EXECUTING -> KaiBlue
    KaiState.CONFIRMATION -> Color(0xFFFFC107)
    KaiState.ERROR -> KaiRed
}

@Composable
fun KaiOrb(state: KaiState, level: Float, modifier: Modifier = Modifier, orbSize: Dp = 300.dp) {
    val color by animateColorAsState(state.color(), tween(400), label = "orbColor")

    // How alive the orb looks in each state
    val energy by animateFloatAsState(
        targetValue = when (state) {
            KaiState.IDLE -> 0.5f
            KaiState.LISTENING -> 0.9f
            KaiState.THINKING -> 0.8f
            KaiState.SPEAKING -> 1.0f
            KaiState.EXECUTING -> 0.9f
            KaiState.CONFIRMATION -> 0.7f
            KaiState.ERROR -> 0.3f
        },
        animationSpec = tween(400),
        label = "energy"
    )
    val smoothLevel by animateFloatAsState(level, tween(120), label = "level")

    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val spinSlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "spinSlow"
    )
    val spinFast by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "spinFast"
    )
    val spin = if (state == KaiState.THINKING || state == KaiState.EXECUTING) spinFast else spinSlow

    Canvas(modifier = modifier.size(orbSize)) {
        val c = center
        val r = size.minDimension / 2f
        val breathe = 1f + (pulse - 1f) * energy * 2f + smoothLevel * 0.25f

        // Outer soft glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.08f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )

        // Core
        val coreR = r * 0.46f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    color,
                    KaiBlue.copy(alpha = 0.45f),
                    Color.Transparent
                ),
                center = c,
                radius = coreR
            ),
            radius = coreR,
            center = c
        )

        // Thin static outer ring
        drawCircle(
            color = color.copy(alpha = 0.4f),
            radius = r * 0.96f,
            center = c,
            style = Stroke(width = 2f)
        )

        // Dashed ring, rotating clockwise
        rotate(degrees = spin, pivot = c) {
            drawCircle(
                color = color.copy(alpha = 0.55f),
                radius = r * 0.84f,
                center = c,
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 22f))
                )
            )
        }

        // Bright arcs, rotating counter-clockwise
        val arcCount = if (state == KaiState.THINKING) 3 else 1
        val sweep = if (state == KaiState.THINKING) 50f else 70f
        for (i in 0 until arcCount) {
            drawArc(
                color = color,
                startAngle = -spin * 1.4f + i * (360f / arcCount),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.72f),
                size = Size(r * 1.44f, r * 1.44f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }

        // Inner ring that reacts to the microphone / speech energy
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = r * (0.58f + smoothLevel * 0.08f) * (0.98f + (pulse - 1f) * energy),
            center = c,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
fun KaiWaveform(state: KaiState, level: Float, modifier: Modifier = Modifier) {
    val color by animateColorAsState(state.color(), tween(400), label = "waveColor")
    val smoothLevel by animateFloatAsState(level, tween(100), label = "waveLevel")
    val amplitude by animateFloatAsState(
        targetValue = when (state) {
            KaiState.IDLE -> 0.08f
            KaiState.LISTENING -> 0.12f + smoothLevel * 0.88f
            KaiState.THINKING -> 0.2f
            KaiState.SPEAKING -> 0.75f
            KaiState.EXECUTING -> 0.3f
            KaiState.CONFIRMATION -> 0.1f
            KaiState.ERROR -> 0.05f
        },
        animationSpec = tween(150),
        label = "amplitude"
    )

    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val bars = 32
            val step = size.width / bars
            val cy = size.height / 2f
            for (i in 0 until bars) {
                val x = step * i + step / 2f
                val shape = 0.35f + 0.65f * abs(sin(phase + i * 0.45f))
                val h = (size.height * amplitude * shape).coerceAtLeast(4f)
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = Offset(x, cy - h / 2f),
                    end = Offset(x, cy + h / 2f),
                    strokeWidth = step * 0.45f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
