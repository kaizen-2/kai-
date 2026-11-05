package com.kai.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Screen(val label: String) {
    HOME("Home"),
    CHATS("Chats"),
    DEVICE("Device"),
    MEMORY("Memory"),
    ACTIVITY("Log"),
    SETTINGS("Settings")
}

@Composable
fun KaiApp(viewModel: KaiViewModel, onMicTapped: () -> Unit) {
    val colors = darkColorScheme(
        primary = KaiCyan,
        onPrimary = KaiNavy,
        secondary = KaiBlue,
        background = KaiNavy,
        onBackground = Color.White,
        surface = PanelBg,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xB3FFFFFF),
        outline = KaiCyan.copy(alpha = 0.4f),
        error = KaiRed
    )

    MaterialTheme(colorScheme = colors) {
        var screen by remember { mutableStateOf(Screen.HOME) }

        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF02040C), KaiNavy, Color(0xFF0A1530))))
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(viewModel, onMicTapped)
                    Screen.CHATS -> ChatsScreen(viewModel, onGoHome = { screen = Screen.HOME })
                    Screen.DEVICE -> DeviceScreen(viewModel)
                    Screen.MEMORY -> MemoryScreen(viewModel)
                    Screen.ACTIVITY -> ActivityScreen(viewModel)
                    Screen.SETTINGS -> SettingsScreen(viewModel)
                }
            }
            NavBar(current = screen, onSelect = { screen = it })
        }

        // Anything that leaves the app needs an explicit yes.
        viewModel.confirmation?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.denyTool() },
                title = { Text(pending.plan.title) },
                text = { Text("Kai wants to: ${pending.plan.description}") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmTool() }) { Text("Allow", color = KaiCyan) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.denyTool() }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun NavBar(current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF03060F))
            .border(1.dp, KaiCyan.copy(alpha = 0.15f))
    ) {
        Screen.values().forEach { s ->
            val selected = s == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(s) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) KaiCyan else Color.Transparent)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = s.label,
                    color = if (selected) KaiCyan else Muted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
