package com.wbrawner.civicsquizbot

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ParseTimeTests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("args")
    fun `strings are parsed correctly`(time: String, parsed: Pair<Int, Int>) {
        val actual = time.parseTime()
        assert(actual == parsed) { "expected $parsed, got $actual" }
    }

    companion object {
        @JvmStatic
        fun args(): Stream<Arguments> = Stream.of(
            arguments("7", 7 to 0),
            arguments("9:30", 9 to 30),
            arguments("12:15pm", 12 to 15),
            arguments("23:45 GMT+2", 21 to 45),
            arguments("8am UTC-6", 14 to 0),
            arguments("6 am America/Chicago", 12 to 0),
            arguments("20 UTC-8", 4 to 0),
        )
    }
}