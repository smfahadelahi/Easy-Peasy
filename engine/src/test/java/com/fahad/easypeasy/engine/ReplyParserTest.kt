package com.fahad.easypeasy.engine

import org.junit.Assert.*
import org.junit.Test

class ReplyParserTest {
    private val valid = """{"reply_ko":"안녕하세요. 반가워요!","hint_bn":"হ্যালো","correction":"","new_word":"","step_done":true}"""
    @Test fun parsesRequiredFieldsAndTwoSentences() { assertNotNull(ReplyParser.parse(valid, emptySet())) }
    @Test fun rejectsMalformedOrIncompleteAndUnknownWords() {
        assertNull(ReplyParser.parse("{}", emptySet()))
        assertNull(ReplyParser.parse("```json$valid```", emptySet()))
        assertNull(ReplyParser.parse(valid.replace("\"new_word\":\"\"", "\"new_word\":\"unknown\""), emptySet()))
    }
    @Test fun streamsOnlyDecodedFirstSentence() {
        assertEquals("안녕하세요.", ReplyParser.firstSentence(valid))
        assertNull(ReplyParser.firstSentence("""{"reply_ko":"안녕"""))
    }
    @Test fun rejectsThreeSentences() { assertNull(ReplyParser.parse(valid.replace("안녕하세요. 반가워요!", "안녕. 좋아요. 가요."), emptySet())) }
}
