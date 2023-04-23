package com.wbrawner.civicsquizbot

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val hikariLogger = LoggerFactory.getLogger("com.zaxxer.hikari") as Logger
        hikariLogger.level = Level.ERROR

        val questions = try {
            javaClass.getResourceAsStream("/questions.txt")
                ?.bufferedReader()
                ?.let { questionsResource ->
                    val parser = QuestionTextParser()
                    parser.parseQuestions(questionsResource.readText()).associateBy { it.number }
                } ?: throw RuntimeException("Questions resource was null")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        val dataSource = HikariDataSource(HikariConfig().apply {
            val host = System.getenv("CIVICS_DB_HOST") ?: "localhost"
            val port = System.getenv("CIVICS_DB_PORT") ?: 5432
            val name = System.getenv("CIVICS_DB_NAME") ?: "postgres"
            jdbcUrl = "jdbc:postgresql://$host:$port/$name"
            username = System.getenv("CIVICS_DB_USER") ?: "postgres"
            password = System.getenv("CIVICS_DB_PASSWORD") ?: "postgres"
        })
        val driver = dataSource.asJdbcDriver()
        val database = Database(driver)
        val questionService = DatabaseQuestionService(database, questions)
        val reminderService = DatabaseReminderService(database)
        try {
            val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
            telegramBotsApi.registerBot(CivicsQuizHandler(questionService, reminderService))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}
