package com.kai.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import org.json.JSONObject
import java.util.Locale

data class ToolCall(val name: String, val args: JSONObject)

object ToolParser {
    private val marker = Regex("""\[\[tool:\s*([a-z_]+)\s*(\{.*?\})?\s*\]\]""", RegexOption.DOT_MATCHES_ALL)

    /** Splits a model reply into its spoken text and an optional tool call. */
    fun parse(reply: String): Pair<String, ToolCall?> {
        val match = marker.find(reply) ?: return reply.trim() to null
        val args = runCatching { JSONObject(match.groupValues[2].ifBlank { "{}" }) }.getOrNull()
        val cleaned = reply.replace(marker, "").trim()
        val call = if (args != null) ToolCall(match.groupValues[1], args) else null
        return cleaned to call
    }
}

/**
 * Every phone action goes through this fixed whitelist. Arguments are validated,
 * nothing the AI writes is ever executed as code.
 */
class ToolRunner(context: Context) {
    private val context = context.applicationContext

    data class Plan(
        val title: String,
        val description: String,
        val sensitive: Boolean,
        val speakResult: Boolean = false
    )

    private val settingsPages = mapOf(
        "main" to Settings.ACTION_SETTINGS,
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS
    )

    private fun str(call: ToolCall, key: String, max: Int): String =
        call.args.optString(key, "").trim().take(max)

    /** Returns null when the call is unknown or its arguments are invalid. */
    fun plan(call: ToolCall): Plan? = when (call.name) {
        "battery" -> Plan("Check battery", "Read your battery level.", sensitive = false, speakResult = true)
        "open_app" -> str(call, "name", 60).takeIf { it.isNotBlank() }
            ?.let { Plan("Open app", "Open the app \"$it\".", true) }
        "open_settings" -> {
            val page = str(call, "page", 20).ifBlank { "main" }.lowercase(Locale.ROOT)
            if (page in settingsPages) Plan("Open settings", "Open the $page settings page.", true) else null
        }
        "set_alarm" -> {
            val h = call.args.optInt("hour", -1)
            val m = call.args.optInt("minute", -1)
            if (h in 0..23 && m in 0..59) {
                val label = str(call, "label", 60)
                Plan("Set alarm", "Set an alarm for %02d:%02d%s.".format(h, m, if (label.isNotBlank()) " ($label)" else ""), true)
            } else null
        }
        "set_timer" -> {
            val s = call.args.optInt("seconds", -1)
            if (s in 1..86_400) Plan("Start timer", "Start a timer for ${formatDuration(s)}.", true) else null
        }
        "add_calendar_event" -> str(call, "title", 100).takeIf { it.isNotBlank() }
            ?.let { Plan("Add calendar event", "Open a new calendar event titled \"$it\" for you to review and save.", true) }
        "media" -> {
            val action = str(call, "action", 20)
            if (action in listOf("play_pause", "next", "previous")) {
                Plan("Media control", "Send \"$action\" to whatever is playing.", true)
            } else null
        }
        "share_text" -> str(call, "text", 2000).takeIf { it.isNotBlank() }
            ?.let { Plan("Share text", "Open the share sheet with: \"${it.take(80)}${if (it.length > 80) "…" else ""}\"", true) }
        "remember" -> str(call, "text", 200).takeIf { it.isNotBlank() }
            ?.let { Plan("Remember", "Save a memory: \"$it\"", false) }
        else -> null
    }

    fun execute(call: ToolCall): String {
        if (plan(call) == null) return "I can't run that action."
        return try {
            when (call.name) {
                "battery" -> batteryInfo()
                "open_app" -> openApp(str(call, "name", 60))
                "open_settings" -> {
                    val page = str(call, "page", 20).ifBlank { "main" }.lowercase(Locale.ROOT)
                    launch(Intent(settingsPages.getValue(page)))
                    "Opening $page settings."
                }
                "set_alarm" -> {
                    launch(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, call.args.getInt("hour"))
                        putExtra(AlarmClock.EXTRA_MINUTES, call.args.getInt("minute"))
                        putExtra(AlarmClock.EXTRA_MESSAGE, str(call, "label", 60))
                    })
                    "Opening your clock app to set the alarm."
                }
                "set_timer" -> {
                    launch(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, call.args.getInt("seconds"))
                        putExtra(AlarmClock.EXTRA_MESSAGE, str(call, "label", 60))
                    })
                    "Opening your clock app to start the timer."
                }
                "add_calendar_event" -> {
                    val begin = System.currentTimeMillis() +
                        call.args.optLong("minutes_from_now", 0).coerceIn(0, 525_600) * 60_000
                    launch(Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, str(call, "title", 100))
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    })
                    "Opening your calendar. Review the event and tap save."
                }
                "media" -> mediaKey(str(call, "action", 20))
                "share_text" -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, str(call, "text", 2000))
                    }
                    launch(Intent.createChooser(send, "Share with"))
                    "Opening the share sheet."
                }
                else -> "I can't run that action."
            }
        } catch (e: ActivityNotFoundException) {
            "No app on this phone can handle that."
        } catch (e: SecurityException) {
            "Android blocked that action."
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openApp(name: String): String {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0).map {
            it.activityInfo.packageName to it.loadLabel(pm).toString()
        }
        val match = apps.firstOrNull { it.second.equals(name, ignoreCase = true) }
            ?: apps.firstOrNull { it.second.startsWith(name, ignoreCase = true) }
            ?: apps.firstOrNull { it.second.contains(name, ignoreCase = true) }
            ?: return "I couldn't find an app called $name."
        val intent = pm.getLaunchIntentForPackage(match.first)
            ?: return "I couldn't open ${match.second}."
        launch(intent)
        return "Opening ${match.second}."
    }

    private fun mediaKey(action: String): String {
        val code = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Sent. This works with most media apps, but not all of them."
    }

    // ---- Read-only device information ---------------------------------

    private fun batteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryPercent(i: Intent?): Int {
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun isCharging(i: Intent?): Boolean {
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun batteryInfo(): String {
        val i = batteryIntent()
        val pct = batteryPercent(i)
        if (pct < 0) return "I couldn't read the battery level."
        return "Battery is at $pct percent${if (isCharging(i)) " and charging" else ""}."
    }

    fun deviceSummary(): List<Pair<String, String>> {
        val i = batteryIntent()
        val pct = batteryPercent(i)
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1_073_741_824.0
        val totalGb = stat.totalBytes / 1_073_741_824.0
        return listOf(
            "Battery" to if (pct >= 0) "$pct%" else "unknown",
            "Charging" to if (isCharging(i)) "Yes" else "No",
            "Storage free" to "%.1f GB of %.1f GB".format(freeGb, totalGb),
            "Network" to networkName(),
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    private fun networkName(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h hour${if (h > 1) "s" else ""}")
            if (m > 0) add("$m minute${if (m > 1) "s" else ""}")
            if (s > 0) add("$s second${if (s > 1) "s" else ""}")
        }.joinToString(" ")
    }

    companion object {
        private val sensitivePattern = Regex(
            """\d{9,}|password|passcode|\bpin\b|cvv|credit card|debit card|social security|passport|bank account|\botp\b""",
            RegexOption.IGNORE_CASE
        )

        /** Memories that look like secrets are never saved automatically. */
        fun looksSensitive(text: String): Boolean = sensitivePattern.containsMatchIn(text)
    }
}
