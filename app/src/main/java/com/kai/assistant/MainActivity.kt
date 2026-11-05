package com.kai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaiHome() }
    }
}

private val Cyan = Color(0xFF00E5FF)
private val Blue = Color(0xFF2979FF)
private val Navy = Color(0xFF050A18)

@Composable
fun KaiHome() {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing)
        ),
        label = "spin"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Navy),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Cyan, Blue.copy(alpha = 0.45f), Color.Transparent),
                    center = c,
                    radius = r * 0.7f * pulse
                ),
                radius = r * 0.7f * pulse,
                center = c
            )
            drawCircle(
                color = Cyan.copy(alpha = 0.4f),
                radius = r * 0.95f,
                center = c,
                style = Stroke(width = 2f)
            )
            drawArc(
                color = Cyan,
                startAngle = spin,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.85f, c.y - r * 0.85f),
                size = Size(r * 1.7f, r * 1.7f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Hello, sir. Ready to get to work?",
            color = Color.White,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(text = "IDLE", color = Cyan, fontSize = 12.sp, letterSpacing = 4.sp)
    }
}
