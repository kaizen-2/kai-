package com.kai.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KaiScreen(viewModel: KaiViewModel, onMicTapped: () -> Unit) {
    val state = viewModel.state
    val accent by animateColorAsState(state.color(), tween(400), label = "accent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02040C), KaiNavy, Color(0xFF0A1530))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: title and microphone indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "K A I",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state == KaiState.LISTENING) "● MIC LIVE" else " ",
                    color = KaiRed,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp
                )
            }

            // Orb, waveform and status
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KaiOrb(state = state, level = viewModel.level)
                Spacer(Modifier.height(8.dp))
                KaiWaveform(state = state, level = viewModel.level)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.label,
                    color = accent,
                    fontSize = 13.sp,
                    letterSpacing = 5.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = viewModel.caption,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).heightIn(min = 56.dp)
                )
                Text(
                    text = if (viewModel.userText.isNotBlank()) "You: ${viewModel.userText}" else " ",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).heightIn(min = 40.dp)
                )
            }

            // Mic / stop button
            val active = state == KaiState.LISTENING || state == KaiState.SPEAKING || state == KaiState.THINKING
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .border(2.dp, accent, CircleShape)
                    .clickable {
                        if (active) viewModel.stopAll() else onMicTapped()
                    },
                contentAlignment = Alignment.Center
            ) {
                MicIcon(color = accent, stop = active)
            }
        }
    }
}

@Composable
private fun MicIcon(color: Color, stop: Boolean) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        if (stop) {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                cornerRadius = CornerRadius(6f, 6f)
            )
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.34f, 0f),
                size = Size(w * 0.32f, h * 0.6f),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
            )
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.2f),
                size = Size(w * 0.64f, h * 0.55f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.75f),
                end = Offset(w * 0.5f, h),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}
