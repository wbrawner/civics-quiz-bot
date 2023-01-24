package com.wbrawner

class QuestionTextParser {
    private val promptRegex = Regex("^(\\d+)\\.\\s+(.*)$")

    fun parseQuestions(text: String): Set<Question> {
        val questions = mutableSetOf<Question>()
        var number: Int? = null
        var prompt: String? = null
        var answer: MutableList<String> = mutableListOf()
        text.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val matches = promptRegex.find(line)
            matches?.let {
                number?.let { num ->
                    questions.add(Question(num, prompt!!, answer.joinToString("\n")))
                    number = null
                    prompt = null
                    answer.clear()
                }
                val (_, numberString, promptString) = it.groupValues
                number = numberString.toInt()
                prompt = promptString
            }
            if (line.startsWith(".")) {
                answer.add(line.substringAfter(" "))
            }
        }
        questions.add(Question(number!!, prompt!!, answer.joinToString("\n")))
        return questions
    }
}

data class Question(val number: Int, val prompt: String, val answer: String)