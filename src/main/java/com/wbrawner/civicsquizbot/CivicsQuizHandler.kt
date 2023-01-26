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

class CivicsQuizHandler(
    private val questionService: QuestionService
) : TelegramLongPollingBot() {
    private val acknowledgementPhrases = listOf(
        "Got it",
        "Understood",
        "Done",
        "Roger roger",
        "👍",
        "✅"
    )

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

fun String.asCommand() = Command.values().firstOrNull { it.text.contains(this) }

enum class Command(vararg val text: String) {
    NEW_QUESTION("New question", "/start"),
    SHOW_ANSWER("Show answer"),
    NEED_PRACTICE("I need to practice this"),
    TOO_EASY("This was easy"),
}

val Any.logger: Logger
    get() = LoggerFactory.getLogger(this.javaClass)