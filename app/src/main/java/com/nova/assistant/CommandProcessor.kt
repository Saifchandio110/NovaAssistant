package com.nova.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed class CommandResult {
    data class Reply(val text: String) : CommandResult()
    data class OpenApp(val query: String) : CommandResult()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : CommandResult()
    data class SetTimer(val seconds: Int, val label: String) : CommandResult()
    object Unrecognized : CommandResult()
}

object CommandParser {

    const val HELP_TEXT =
        "I can run these local commands:\n" +
            "• open <app name>, e.g. \"open Chrome\"\n" +
            "• set an alarm for 7:30 am\n" +
            "• set a timer for 10 minutes\n" +
            "• remind me at 5 pm to call mom (creates a labelled alarm)\n" +
            "• what time is it / what's the date"

    private val openRegex = Regex("""^(?:please\s+)?(?:open|launch|start)\s+(.+)$""")
    private val alarmRegex =
        Regex("""\balarm\b.*?\b(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?""")
    private val timerRegex =
        Regex("""\btimer\b.*?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""")
    private val remindRegex =
        Regex("""^remind me (?:at|by)\s+(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\s*(?:to\s+)?(.*)$""")
    private val dateRegex = Regex("""\b(date|what day)\b""")
    private val greetings = setOf("hi", "hello", "hey", "hey nova", "hello nova", "hi nova")

    fun parse(raw: String): CommandResult {
        val text = raw.trim().lowercase().replace(Regex("[!?.,]+$"), "")
        if (text.isEmpty()) return CommandResult.Unrecognized

        remindRegex.find(text)?.let { m ->
            val time = toTime(m.groups[1]?.value, m.groups[2]?.value, m.groups[3]?.value)
                ?: return CommandResult.Reply("That time doesn't look valid. Try \"remind me at 5 pm to call mom\".")
            val label = m.groups[4]?.value?.trim().orEmpty().ifEmpty { "Reminder" }
            return CommandResult.SetAlarm(time.first, time.second, label)
        }
        if (text.startsWith("remind me")) {
            return CommandResult.Reply(
                "I can only set reminders with a clock time, like \"remind me at 5 pm to call mom\"."
            )
        }

        alarmRegex.find(text)?.let { m ->
            val time = toTime(m.groups[1]?.value, m.groups[2]?.value, m.groups[3]?.value)
                ?: return CommandResult.Reply("That time doesn't look valid. Try \"set an alarm for 7:30 am\".")
            return CommandResult.SetAlarm(time.first, time.second, "Nova alarm")
        }

        timerRegex.find(text)?.let { m ->
            val amount = m.groups[1]?.value?.toIntOrNull()
            val unit = m.groups[2]?.value.orEmpty()
            if (amount == null) return CommandResult.Reply("I couldn't read that timer length.")
            val multiplier = when {
                unit.startsWith("h") -> 3600
                unit.startsWith("m") -> 60
                else -> 1
            }
            val seconds = amount.toLong() * multiplier
            if (seconds < 1 || seconds > 86400) {
                return CommandResult.Reply("Timers must be between 1 second and 24 hours.")
            }
            return CommandResult.SetTimer(seconds.toInt(), "Nova timer")
        }

        openRegex.find(text)?.let { m ->
            val name = m.groups[1]?.value?.trim().orEmpty().removeSuffix(" app")
            if (name.isNotEmpty()) return CommandResult.OpenApp(name)
        }

        if (text in greetings) {
            return CommandResult.Reply("Hello! Type or tap the mic. Say \"help\" to see what I can do.")
        }
        if (text.contains("help") || text.contains("what can you do")) {
            return CommandResult.Reply(HELP_TEXT)
        }
        if (text.contains("what time") || text.contains("current time") ||
            text.contains("the time") || text.contains("time is it")
        ) {
            val now = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            return CommandResult.Reply("It's $now.")
        }
        if (dateRegex.containsMatchIn(text)) {
            val today = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            return CommandResult.Reply("Today is $today.")
        }
        return CommandResult.Unrecognized
    }

    private fun toTime(h: String?, m: String?, marker: String?): Pair<Int, Int>? {
        val hour = h?.toIntOrNull() ?: return null
        val minute = if (m.isNullOrEmpty()) 0 else m.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        val ap = marker?.replace(".", "").orEmpty()
        return when (ap) {
            "" -> if (hour in 0..23) hour to minute else null
            "am" -> if (hour in 1..12) (hour % 12) to minute else null
            "pm" -> if (hour in 1..12) ((hour % 12) + 12) to minute else null
            else -> null
        }
    }
}

data class ActionOutcome(val message: String, val success: Boolean)

class ActionRunner(context: Context) {
    private val appContext = context.applicationContext

    fun openApp(query: String): ActionOutcome {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = query.trim().lowercase()
        val match = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.filter { it.first.lowercase().contains(q) }.minByOrNull { it.first.length }
            ?: return ActionOutcome("I couldn't find an installed app called \"$query\".", false)

        val launch = pm.getLaunchIntentForPackage(match.second)
            ?: return ActionOutcome("${match.first} has no launchable screen.", false)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(launch, "Opening ${match.first}.", "I couldn't open ${match.first}.")
    }

    fun setAlarm(hour: Int, minute: Int, label: String): ActionOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shown = LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return start(
            intent,
            "Opening your clock app with an alarm for $shown (\"$label\"). Please confirm it there.",
            "No clock app on this phone accepted the alarm request."
        )
    }

    fun setTimer(seconds: Int, label: String): ActionOutcome {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(
            intent,
            "Opening your clock app with a $seconds-second timer. Please confirm it there.",
            "No clock app on this phone accepted the timer request."
        )
    }

    private fun start(intent: Intent, ok: String, fail: String): ActionOutcome {
        return try {
            appContext.startActivity(intent)
            ActionOutcome(ok, true)
        } catch (e: ActivityNotFoundException) {
            ActionOutcome(fail, false)
        } catch (e: SecurityException) {
            ActionOutcome("$fail (blocked by Android: ${e.message})", false)
        }
    }
}
