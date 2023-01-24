package com.wbrawner

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class CivicsQuizHandler : TelegramLongPollingBot() {
    private val acknowledgementPhrases = listOf(
        "Got it",
        "Understood",
        "Done",
        "Roger roger",
        "👍",
        "✅"
    )
    private val random = SecureRandom()
    private lateinit var questions: Map<Int, Question>

    // TODO: Persist this in DB
    private val buckets = ConcurrentHashMap<Long, List<MutableList<Int>>>()

    // TODO: Persist this in DB
    private val lastChatQuestions = ConcurrentHashMap<Long, Question>()
    override fun getBotUsername(): String = "CivicsQuizBot"

    override fun getBotToken(): String = System.getenv("TELEGRAM_TOKEN")

    override fun onUpdateReceived(update: Update) {
        println(update)
        val message = update.message
        if (message == null) {
            println("No message, returning early")
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
        val question = randomQuestion(chatId)
        lastChatQuestions[chatId] = question
        sendMessage(chatId, question.prompt, Command.SHOW_ANSWER)
    }

    private fun handleFeedback(chatId: Long, command: Command) {
        val userBuckets = buckets[chatId] ?: run {
            val userBuckets = listOf(
                questions.values.map { it.number }.toMutableList(),
                mutableListOf(),
                mutableListOf(),
                mutableListOf()
            )
            buckets[chatId] = userBuckets
            userBuckets
        }
        val lastQuestion = requireNotNull(lastChatQuestions[chatId])
        for (i in userBuckets.indices) {
            if (userBuckets[i].contains(lastQuestion.number)) {
                if (command == Command.TOO_EASY && i < userBuckets.lastIndex) {
                    userBuckets[i].remove(lastQuestion.number)
                    userBuckets[i + 1].add(lastQuestion.number)
                } else if (command == Command.NEED_PRACTICE && i > 0) {
                    userBuckets[i].remove(lastQuestion.number)
                    userBuckets[i - 1].add(lastQuestion.number)
                }
                break
            }
        }
        println(userBuckets)
        sendMessage(chatId, acknowledgementPhrases.random())
        sendQuestion(chatId)
    }

    private fun sendAnswer(chatId: Long) {
        lastChatQuestions[chatId]?.let {
            sendMessage(chatId, it.answer, Command.NEED_PRACTICE, Command.TOO_EASY)
        } ?: run {
            sendMessage(chatId, "I can't answer a question that hasn't been asked", Command.NEW_QUESTION)
        }
    }

    private fun sendOptions(chatId: Long) = sendMessage(
        chatId,
        "I'm not sure how to respond to that. Here are a few actions I can help you with:",
        Command.NEW_QUESTION
    )

    init {
        try {
            javaClass.getResourceAsStream("/questions.txt")
                ?.bufferedReader()
                ?.let { questionsResource ->
                    val parser = QuestionTextParser()
                    questions = parser.parseQuestions(questionsResource.readText()).associateBy { it.number }
                } ?: throw RuntimeException("Questions resource was null")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

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

    private fun randomQuestion(chatId: Long): Question {
        val userBuckets = buckets[chatId] ?: run {
            val userBuckets = listOf(
                questions.values.map { it.number }.toMutableList(),
                mutableListOf(),
                mutableListOf(),
                mutableListOf()
            )
            buckets[chatId] = userBuckets
            userBuckets
        }
        if (userBuckets.first().isNotEmpty()) {
            return requireNotNull(questions[userBuckets.first().removeFirst()]) { "Failed to retrieve a question" }
        }
        var bucket = when (random.nextInt(10)) {
            in 0..5 -> 1
            in 6..8 -> 2
            else -> 3
        }
        var question = userBuckets[bucket].shuffled().firstOrNull()
        while (question == null) {
            if (--bucket == -1) {
                bucket = 2
            }
            question = userBuckets[bucket].shuffled().firstOrNull()
        }
        return requireNotNull(questions[question]) { "Failed to retrieve a question" }
    }
}

fun String.asCommand() = Command.values().firstOrNull { it.text.contains(this) }

enum class Command(vararg val text: String) {
    NEW_QUESTION("New question", "/start"),
    SHOW_ANSWER("Show answer"),
    NEED_PRACTICE("I need to practice this"),
    TOO_EASY("This was easy"),
}