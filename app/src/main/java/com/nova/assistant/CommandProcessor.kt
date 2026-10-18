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
        Regex("""\balarm\b.*?\b(\d{1,2})(?:[: ](\d{2}))?\s*(a\.?\s?m\.?|p\.?\s?m\.?)?""")
    private val timerRegex =
        Regex("""\btimer\b.*?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""")
    private val remindRegex =
        Regex("""^remind me (?:at|by)\s+(\d{1,2})(?:[: ](\d{2}))?\s*(a\.?\s?m\.?|p\.?\s?m\.?)?\s*(?:to\s+)?(.*)$""")
    private val dateRegex = Regex("""\b(date|what day)\b""")
    private val greetings = setOf("hi", "hello", "hey", "hey nova", "hello nova", "hi nova")

    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    fun parse(raw: String): CommandResult {
        val text = raw.trim().lowercase().replace(Regex("[!?.,]+$"), "")
        if (text.isEmpty()) return CommandResult.Unrecognized
        val numText = normalizeNumbers(text)

        remindRegex.find(numText)?.let { m ->
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

        alarmRegex.find(numText)?.let { m ->
            val time = toTime(m.groups[1]?.value, m.groups[2]?.value, m.groups[3]?.value)
                ?: return CommandResult.Reply("That time doesn't look valid. Try \"set an alarm for 7:30 am\".")
            return CommandResult.SetAlarm(time.first, time.second, "Nova alarm")
        }

        val timerText = numText.replace(Regex("""\b(?:a|an)\s+(second|minute|hour)"""), "1 \$1")
        timerRegex.find(timerText)?.let { m ->
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
            return CommandResult.Reply("Hello! Say a command, or say help to see what I can do.")
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

    private fun unitDigit(token: String?): Int? {
        val n = units[token ?: return null] ?: return null
        return if (n in 1..9) n else null
    }

    private fun normalizeNumbers(input: String): String {
        val tokens = input.split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val nextDigit = unitDigit(next)
            if (t == "oh" && nextDigit != null) {
                out.add("0$nextDigit")
                i += 2
            } else if (tens.containsKey(t)) {
                val base = tens.getValue(t)
                if (nextDigit != null) {
                    out.add((base + nextDigit).toString())
                    i += 2
                } else {
                    out.add(base.toString())
                    i += 1
                }
            } else if (units.containsKey(t)) {
                out.add(units.getValue(t).toString())
                i += 1
            } else {
                out.add(t)
                i += 1
            }
        }
        return out.joinToString(" ")
    }

    private fun toTime(h: String?, m: String?, marker: String?): Pair<Int, Int>? {
        val hour = h?.toIntOrNull() ?: return null
        val minute = if (m.isNullOrEmpty()) 0 else m.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        val ap = marker?.replace(Regex("[. ]"), "").orEmpty()
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

    private fun squash(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun distance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    fun openApp(query: String): ActionOutcome {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = query.trim().lowercase().removeSuffix(" app")
        val qs = squash(q)

        val match = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.filter { squash(it.first).contains(qs) && qs.isNotEmpty() }
                .minByOrNull { it.first.length }
            ?: apps.map { it to distance(squash(it.first), qs) }
                .minByOrNull { it.second }
                ?.takeIf { qs.isNotEmpty() && it.second <= maxOf(2, qs.length / 3) }
                ?.first
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
