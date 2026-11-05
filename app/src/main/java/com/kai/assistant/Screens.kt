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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Muted = Color(0x99FFFFFF)
val PanelBg = Color(0xFF0B1630)
val Amber = Color(0xFFFFC107)

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelBg)
            .border(1.dp, KaiCyan.copy(alpha = 0.25f), shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun KaiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KaiCyan,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (filled) 0.9f else 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = KaiCyan,
        fontSize = 11.sp,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = KaiRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun MicIcon(color: Color, stop: Boolean) {
    Canvas(modifier = Modifier.size(28.dp)) {
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

@Composable
private fun SendIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.15f), 3f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.22f, h * 0.43f), 3f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.78f, h * 0.43f), 3f, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// 1. Home
// ---------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: KaiViewModel, onMicTapped: () -> Unit) {
    val state = vm.state
    val accent by animateColorAsState(state.color(), tween(400), label = "accent")
    var draft by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val busy = state == KaiState.LISTENING || state == KaiState.SPEAKING ||
        state == KaiState.THINKING || state == KaiState.EXECUTING

    fun send() {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            vm.sendText(text)
            draft = ""
            focus.clearFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "K A I",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp
        )
        Text(
            text = if (state == KaiState.LISTENING) "● MIC LIVE" else " ",
            color = KaiRed,
            fontSize = 11.sp,
            letterSpacing = 3.sp
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KaiOrb(state = state, level = vm.level, orbSize = 210.dp)
            KaiWaveform(state = state, level = vm.level)
            Spacer(Modifier.height(6.dp))
            Text(text = state.label, color = accent, fontSize = 12.sp, letterSpacing = 5.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = vm.caption,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)
            )
            Text(
                text = if (vm.userText.isNotBlank()) "You: ${vm.userText}" else " ",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type to Kai…", color = Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Spacer(Modifier.width(10.dp))
            val sending = draft.isNotBlank()
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(2.dp, accent, CircleShape)
                    .clickable {
                        when {
                            sending -> send()
                            busy -> vm.stopAll()
                            else -> onMicTapped()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (sending) SendIcon(accent) else MicIcon(accent, stop = busy)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Conversations
// ---------------------------------------------------------------------------

@Composable
fun ChatsScreen(vm: KaiViewModel, onGoHome: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    val current by vm.currentConversation.collectAsState()
    val messages by vm.messages.collectAsState()
    var showList by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (current != null && !showList) {
            ScreenTitle("CONVERSATION")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KaiButton("All chats", { showList = true }, Modifier.weight(1f))
                KaiButton("Continue", onGoHome, Modifier.weight(1f), filled = true)
                KaiButton("New chat", { vm.closeConversation(); onGoHome() }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (messages.isEmpty()) {
                Text("No messages yet.", color = Muted)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    val mine = m.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        val shape = RoundedCornerShape(14.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .clip(shape)
                                .background(if (mine) KaiCyan.copy(alpha = 0.12f) else PanelBg)
                                .border(1.dp, KaiCyan.copy(alpha = if (mine) 0.5f else 0.2f), shape)
                                .padding(12.dp)
                        ) {
                            Text(if (mine) "You" else "Kai", color = KaiCyan, fontSize = 11.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(m.content, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        } else {
            ScreenTitle("CONVERSATIONS")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KaiButton("New chat", { vm.closeConversation(); showList = false; onGoHome() }, Modifier.weight(1f), filled = true)
                KaiButton("Clear all", { confirmClear = true }, Modifier.weight(1f), color = KaiRed)
            }
            Spacer(Modifier.height(12.dp))
            if (conversations.isEmpty()) {
                Text("Nothing here yet. Talk to Kai on the Home screen and your chats will appear.", color = Muted)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations, key = { it.id }) { c ->
                    Panel(modifier = Modifier.clickable { vm.openConversation(c.id); showList = false }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.title, color = Color.White, fontSize = 15.sp, maxLines = 2)
                                Text(formatTime(c.updatedAt), color = Muted, fontSize = 12.sp)
                            }
                            Text(
                                "Delete",
                                color = KaiRed,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { vm.deleteConversation(c.id) }.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all conversations?",
            text = "This permanently deletes every saved conversation on this phone.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 3. Device dashboard
// ---------------------------------------------------------------------------

@Composable
fun DeviceScreen(vm: KaiViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    val info = remember(tick) { vm.deviceSummary() }

    val actions: List<Pair<String, () -> Unit>> = listOf(
        "Say battery level" to { vm.runToolDirect("battery") },
        "Wi-Fi settings" to { vm.runToolDirect("open_settings", JSONObject().put("page", "wifi")) },
        "Bluetooth settings" to { vm.runToolDirect("open_settings", JSONObject().put("page", "bluetooth")) },
        "Battery saver" to { vm.runToolDirect("open_settings", JSONObject().put("page", "battery")) },
        "Play / Pause" to { vm.runToolDirect("media", JSONObject().put("action", "play_pause")) },
        "Next track" to { vm.runToolDirect("media", JSONObject().put("action", "next")) },
        "Previous track" to { vm.runToolDirect("media", JSONObject().put("action", "previous")) },
        "Sound settings" to { vm.runToolDirect("open_settings", JSONObject().put("page", "sound")) }
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenTitle("DEVICE")
        Panel {
            info.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                    Text(value, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(0.6f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        KaiButton("Refresh", { tick++ }, Modifier.fillMaxWidth())

        SectionLabel("QUICK ACTIONS")
        actions.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { (label, run) -> KaiButton(label, run, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "You can also just ask Kai, for example: open YouTube, set an alarm for 6:30, or start a 10 minute timer. " +
                "Kai asks before doing anything that leaves the app. Notifications, contacts and calendar reading are not supported yet.",
            color = Muted,
            fontSize = 13.sp
        )
    }
}

// ---------------------------------------------------------------------------
// 4. Memory
// ---------------------------------------------------------------------------

@Composable
fun MemoryScreen(vm: KaiViewModel) {
    val s by vm.settings.collectAsState()
    val memories by vm.memories.collectAsState()
    var newMemory by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("MEMORY")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory", color = Color.White, fontSize = 16.sp)
                    Text(
                        if (s.memoryEnabled) "Kai remembers what you ask him to." else "Off. Kai won't save or use memories.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Switch(checked = s.memoryEnabled, onCheckedChange = { vm.setMemoryEnabled(it) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newMemory,
                onValueChange = { newMemory = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a memory…", color = Muted) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            KaiButton("Add", { vm.addMemory(newMemory); newMemory = "" }, filled = true)
        }
        Spacer(Modifier.height(10.dp))
        if (memories.isEmpty()) {
            Text(
                "No memories yet. Tell Kai something like \"remember that I prefer short answers\".",
                color = Muted,
                fontSize = 14.sp
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memories, key = { it.id }) { m ->
                Panel {
                    Text(m.text, color = Color.White, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Edit",
                            color = KaiCyan,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { editing = m; editText = m.text }.padding(8.dp)
                        )
                        Text(
                            "Delete",
                            color = KaiRed,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { vm.deleteMemory(m.id) }.padding(8.dp)
                        )
                    }
                }
            }
        }
        if (memories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            KaiButton("Clear all memories", { confirmClear = true }, Modifier.fillMaxWidth(), color = KaiRed)
        }
    }

    editing?.let { item ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }) },
            confirmButton = {
                TextButton(onClick = { vm.editMemory(item, editText); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all memories?",
            text = "Kai will forget everything he has saved about you.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearMemories(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 5. Activity history
// ---------------------------------------------------------------------------

private fun statusColor(status: String): Color = when (status) {
    "done", "ok", "saved" -> KaiCyan
    "failed", "blocked", "denied", "invalid", "cancelled" -> KaiRed
    "awaiting confirmation" -> Amber
    else -> Muted
}

@Composable
fun ActivityScreen(vm: KaiViewModel) {
    val entries by vm.activity.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle("ACTIVITY")
        Text(
            "Everything Kai does on your phone is logged here, including actions you refused.",
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        if (entries.isNotEmpty()) {
            KaiButton("Clear history", { vm.clearActivity() }, Modifier.fillMaxWidth(), color = KaiRed)
            Spacer(Modifier.height(10.dp))
        }
        if (entries.isEmpty()) {
            Text("Nothing yet.", color = Muted)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { a ->
                Panel {
                    Row {
                        Text(a.title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(a.status.uppercase(Locale.ROOT), color = statusColor(a.status), fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    if (a.detail.isNotBlank()) {
                        Text(a.detail, color = Muted, fontSize = 13.sp)
                    }
                    Text(formatTime(a.createdAt), color = Muted.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Settings
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: KaiViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    var url by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var key by remember { mutableStateOf("") }
    var showVoices by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ScreenTitle("SETTINGS")

        SectionLabel("PERSONALITY")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Personality.values().forEach { p ->
                KaiButton(p.label, { vm.setPersonality(p) }, Modifier.weight(1f), filled = s.personality == p)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (s.personality) {
                Personality.PROFESSIONAL -> "Polished and precise."
                Personality.BALANCED -> "Warm and witty, serious when it matters."
                Personality.SARCASTIC -> "Dry and playful. Still gives real answers."
            },
            color = Muted,
            fontSize = 13.sp
        )

        SectionLabel("VOICE")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speak replies aloud", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = s.speakReplies, onCheckedChange = { vm.setSpeakReplies(it) })
            }
        }
        Spacer(Modifier.height(8.dp))
        KaiButton(
            text = if (s.voiceName.isBlank()) "Choose a voice (default)" else "Voice: ${s.voiceName}",
            onClick = { showVoices = true },
            modifier = Modifier.fillMaxWidth()
        )

        SectionLabel("AI PROVIDER")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KaiButton("Groq", { url = Providers.GROQ_URL; model = Providers.GROQ_MODEL }, Modifier.weight(1f), filled = url == Providers.GROQ_URL)
            KaiButton("Google Gemini", { url = Providers.GEMINI_URL; model = Providers.GEMINI_MODEL }, Modifier.weight(1f), filled = url == Providers.GEMINI_URL)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Provider URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        KaiButton("Save provider", { vm.setProvider(url, model) }, Modifier.fillMaxWidth())

        SectionLabel("API KEY")
        Text(
            if (vm.hasKey) "A key is saved on this phone (encrypted)." else "No key yet. Kai can't answer until you add one.",
            color = if (vm.hasKey) KaiCyan else Amber,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Paste API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KaiButton("Save key", { vm.saveApiKey(key); key = "" }, Modifier.weight(1f), filled = true)
            KaiButton("Test", { vm.testConnection() }, Modifier.weight(1f))
            KaiButton("Remove", { vm.clearApiKey() }, Modifier.weight(1f), color = KaiRed)
        }
        if (vm.notice.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(vm.notice, color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Both Groq (console.groq.com) and Google AI Studio (aistudio.google.com) offer a free tier with daily limits that " +
                "can change. Create a key there, copy it, then paste it above. Your messages are sent to the provider you choose. " +
                "The key is never inside the app file.",
            color = Muted,
            fontSize = 12.sp
        )

        SectionLabel("ABOUT")
        Text("Kai version $version", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }

    if (showVoices) {
        AlertDialog(
            onDismissRequest = { showVoices = false },
            title = { Text("Choose a voice") },
            text = {
                if (vm.voices.isEmpty()) {
                    Text("No extra English voices were found on this phone.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(vm.voices, key = { it.name }) { v ->
                            Text(
                                text = v.label,
                                color = if (v.name == s.voiceName) KaiCyan else Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectVoice(v); showVoices = false }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoices = false }) { Text("Close") } }
        )
    }
}
