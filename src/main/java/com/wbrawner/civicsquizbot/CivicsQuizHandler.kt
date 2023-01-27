package com.wbrawner.civicsquizbot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CivicsQuizHandler(
    private val questionService: QuestionService,
    private val reminderService: ReminderService
) : TelegramLongPollingBot() {
    private val acknowledgementPhrases = listOf(
        "Got it",
        "Understood",
        "Done",
        "Roger roger",
        "👍",
        "✅"
    )
    private val reminderPhrases = listOf(
        "Ready to study?",
        "IITT'SSS STUDY TIMMEEEE!!!",
        "Hey. You know what to do.",
        "🦅",
        "These questions aren't going to answer themselves"
    )

    init {
        Executors.newScheduledThreadPool(1).apply {
            scheduleAtFixedRate(
                {
                    val time = LocalTime.now()
                    reminderService.getRemindersAt(time.hour, time.minute)
                        .forEach { reminder ->
                            sendReminder(reminder.user_id)
                        }
                },
                0,
                1,
                TimeUnit.MINUTES
            );
        }
    }

    override fun getBotUsername(): String = "CivicsQuizBot"

    override fun getBotToken(): String = System.getenv("TELEGRAM_TOKEN")

    override fun onUpdateReceived(update: Update) {
        logger.info("Update: $update")
        val message = update.message
        if (message == null) {
            logger.warn("No message, returning early")
            return
        }
        // TODO: Add option to enable smart reminders
        when (val command = message.text.asCommand()) {
            Command.SHOW_ANSWER -> sendAnswer(message.chatId)
            Command.NEW_QUESTION -> sendQuestion(message.chatId)
            Command.NEED_PRACTICE, Command.TOO_EASY -> handleFeedback(message.chatId, command)
            Command.REMINDER -> handleReminder(chatId = message.chatId, message.text)
            else -> sendOptions(message.chatId)
        }
    }

    private fun sendQuestion(chatId: Long) {
        val question = questionService.randomQuestionForUser(chatId)
        sendMessage(chatId, question.prompt, Command.SHOW_ANSWER)
    }

    private fun handleFeedback(chatId: Long, command: Command) {
        if (command == Command.NEED_PRACTICE) {
            questionService.increaseLastQuestionFrequency(chatId)
        } else if (command == Command.TOO_EASY) {
            questionService.decreaseLastQuestionFrequency(chatId)
        }
        sendMessage(chatId, acknowledgementPhrases.random())
        sendQuestion(chatId)
    }

    private fun handleReminder(chatId: Long, message: String) {
        if (!message.startsWith("/reminder")) {
            throw IllegalStateException("Attempted to handle non-reminder message from reminder path")
        }
        val messageParts = message.split(" ", limit = 3)
        when (messageParts[1]) {
            "set" -> try {
                val (hour, minute) = messageParts.last().parseTime()
                reminderService.setReminderForUser(Reminder(user_id = chatId, hour = hour, minute = minute))
                sendMessage(chatId, "I'll remind you every day at ${messageParts[2]}")
            } catch (e: DateTimeException) {
                sendMessage(
                    chatId,
                    "Sorry, I didn't understand your time zone. Try something like \"America/Chicago\" or \"UTC-6\"" +
                            "from here: https://en.wikipedia.org/wiki/List_of_tz_database_time_zones#List"
                )
            }

            "remove", "stop", "off" -> {
                reminderService.deleteReminderForUser(chatId)
                sendMessage(chatId, "I won't remind you anymore")
            }
        }
    }

    private fun sendReminder(chatId: Long) {
        sendMessage(chatId, reminderPhrases.random(), Command.NEW_QUESTION)
    }

    private fun sendAnswer(chatId: Long) {
        questionService.answerLastQuestion(chatId)?.let { answer ->
            sendMessage(chatId, answer, Command.NEED_PRACTICE, Command.TOO_EASY)
        } ?: run {
            sendMessage(chatId, "I can't answer a question that hasn't been asked", Command.NEW_QUESTION)
        }
    }

    private fun sendOptions(chatId: Long) = sendMessage(
        chatId,
        "I'm not sure how to respond to that. Here are a few actions I can help you with:",
        Command.NEW_QUESTION
    )

    private fun sendMessage(chatId: Long, text: String, vararg keyboardButtons: Command) {
        val keyboard = keyboardButtons.map {
            KeyboardRow(listOf(KeyboardButton(it.text.first())))
        }
        val response = SendMessage.builder()
            .text(text)
            .chatId(chatId)
            .replyMarkup(ReplyKeyboardMarkup(keyboard, true, true, false, null, true))
            .build()
        try {
            execute(response)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}

fun String.parseTime(): Pair<Int, Int> {
    val parts = split(" ", ":")
    var hour = when (val h = parts.first().toIntOrNull()) {
        is Int -> h
        else -> {
            val hourString = parts.first().lowercase()
            if (hourString.contains("am")) {
                hourString.replace("am", "").toInt()
            } else if (hourString.contains("pm")) {
                hourString.replace("pm", "").toInt().plus(12)
            } else {
                hourString.replace(Regex("^\\d+"), "").toInt()
            }
        }
    }
    logger.info("Parsed hour: $hour")
    var minutes: Int? = null
    var timeZone: TimeZone? = null
    when (val m = parts.getOrNull(1)?.toIntOrNull()) {
        is Int -> minutes = m
        else -> {
            if (parts.size > 1) {
                logger.info("second element not a number: ${parts[1]}")
                val part1 = parts[1].lowercase()
                if (part1 == "pm") {
                    if (hour < 12) {
                        hour += 12
                    }
                } else if (part1.contains("am")) {
                    minutes = part1.replace("am", "").toIntOrNull()
                } else if (part1.contains("pm")) {
                    minutes = part1.replace("pm", "").toIntOrNull()
                    if (hour < 12) {
                        hour += 12
                    }
                } else {
                    try {
                        logger.info("Trying to parse second element as time zone")
                        val zoneId = ZoneId.of(parts[1]).normalized()
                        logger.info("Parsed zoneId as $zoneId")
                        timeZone = TimeZone.getTimeZone(zoneId)
                    } catch (e: DateTimeException) {
                        logger.error("Failed to parse ${parts[1]} as time zone", e)
                    }
                }
            }
        }
    }
    if (timeZone == null && parts.size > 2) {
        logger.info("timeZone is still null, trying to parse again")
        timeZone = TimeZone.getTimeZone(ZoneId.of(parts.last()))
    }
    timeZone?.let {
        val adjustment = (it.rawOffset / TimeUnit.HOURS.toMillis(1)).toInt()
        logger.info("adjusting hour by $adjustment hours according to timezone ($it)")
        hour -= adjustment
    }
    if (hour > 23) {
        hour -= 24
    }
    return hour to (minutes ?: 0)
}

fun String.asCommand() = Command.values().firstOrNull { command ->
    command.text.contains(this) || command.text.any { this.startsWith(it) }
}

enum class Command(vararg val text: String) {
    NEW_QUESTION("New question", "/start"),
    SHOW_ANSWER("Show answer"),
    NEED_PRACTICE("I need to practice this"),
    TOO_EASY("This was easy"),
    REMINDER("/reminder"),
}

val Any.logger: Logger
    get() = LoggerFactory.getLogger(this.javaClass)