package com.example.scanpro.feature.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchMatchTest {
    @Test fun `a word start matches`() = assertTrue(toolSearchMatches("smal", "shrink reduce smaller size"))

    @Test fun `a whole word matches`() = assertTrue(toolSearchMatches("word", "txt copy words plain text word docx"))

    @Test fun `the middle of a word does not`() = assertFalse(toolSearchMatches("word", "remove password decrypt open"))

    @Test fun `punctuation splits words`() = assertTrue(toolSearchMatches("sign", "e-sign autograph"))

    @Test fun `non-latin scripts match by word start`() = assertTrue(toolSearchMatches("पीडीएफ", "पीडीएफ जोड़ें"))
}
