package com.wbrawner.civicsquizbot

import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

interface QuestionService {
    fun randomQuestionForUser(userId: Long): Question
    fun increaseLastQuestionFrequency(userId: Long)
    fun decreaseLastQuestionFrequency(userId: Long)
    fun answerLastQuestion(userId: Long): String?
}

private const val NUMBER_OF_BUCKETS = 3

class DatabaseQuestionService(
    private val database: Database,
    private val questions: Map<Int, Question>
) : QuestionService {
    private val random = SecureRandom()

    init {
        database.lastQuestionQueries.create()
        database.repetitionQueries.create()
    }

    override fun randomQuestionForUser(userId: Long): Question {
        if (database.repetitionQueries.countByUserId(userId).executeAsOne() == 0L) {
            questions.values.forEach {
                database.repetitionQueries.insertRepetition(Repetition(it.number, userId, 0))
            }
        }
        var question = database.repetitionQueries.selectRandomByUserIdAndBucket(0, userId)
            .executeAsOneOrNull()
            ?.question_id
        if (question != null) {
            database.lastQuestionQueries.upsertLastQuestion(userId, question)
            logger.info("Returning question from bucket 0")
            return requireNotNull(questions[question]) { "Failed to retrieve random question from bucket 0" }
        }

        var bucket = when (random.nextInt(10)) {
            in 0..7 -> 1
            else -> 2
        }
        logger.info("Trying to get question from bucket $bucket")
        question = database.repetitionQueries.selectRandomByUserIdAndBucket(bucket, userId)
            .executeAsOneOrNull()
            ?.question_id
        val initialBucket = bucket
        while (question == null) {
            if (--bucket == 0) {
                bucket = NUMBER_OF_BUCKETS
            } else if (bucket == initialBucket) {
                throw IllegalStateException("Failed to find questions in any bucket")
            }
            logger.info("Trying to get question from bucket $bucket")
            question = database.repetitionQueries.selectRandomByUserIdAndBucket(bucket, userId)
                .executeAsOneOrNull()
                ?.question_id
        }
        logger.info("Returning question from bucket $bucket")
        database.lastQuestionQueries.upsertLastQuestion(userId, question)
        return requireNotNull(questions[question]) { "Failed to retrieve random question from bucket 0" }
    }

    override fun increaseLastQuestionFrequency(userId: Long) {
        val lastQuestion = database.lastQuestionQueries.selectByUserId(userId)
            .executeAsOneOrNull()
            ?.question_id
            ?: run {
                logger.info("Ignoring feedback for user $userId since they don't have a previously asked question")
                return
            }
        logger.info("Increasing frequency of question $lastQuestion for user $userId")
        val bucket = database.repetitionQueries.selectByQuestionIdAndUserId(lastQuestion, userId)
            .executeAsOneOrNull()
            ?.bucket
            ?: 0
        val newBucket = if (bucket == 0) {
            1
        } else {
            max(bucket - 1, 1)
        }
        database.repetitionQueries.updateQuestionBucket(newBucket, lastQuestion, userId)
    }

    override fun decreaseLastQuestionFrequency(userId: Long) {
        val lastQuestion = database.lastQuestionQueries.selectByUserId(userId)
            .executeAsOneOrNull()
            ?.question_id
            ?: run {
                logger.info("Ignoring feedback for user $userId since they don't have a previously asked question")
                return
            }
        logger.info("Reducing frequency of question $lastQuestion for user $userId")
        val bucket = database.repetitionQueries.selectByQuestionIdAndUserId(lastQuestion, userId)
            .executeAsOneOrNull()
            ?.bucket
            ?: 0
        val newBucket = if (bucket == 0) {
            min(2, NUMBER_OF_BUCKETS)
        } else {
            max(bucket - 1, 1)
        }
        database.repetitionQueries.updateQuestionBucket(newBucket, lastQuestion, userId)
    }

    override fun answerLastQuestion(userId: Long): String? = database.lastQuestionQueries.selectByUserId(userId)
        .executeAsOneOrNull()
        ?.question_id
        ?.let {
            questions[it]?.answer
        }
}