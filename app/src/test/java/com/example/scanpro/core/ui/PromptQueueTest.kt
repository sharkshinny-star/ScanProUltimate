package com.example.scanpro.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptQueueTest {
    private data class Ask(val file: String, val wrong: Boolean = false)

    private fun queue() = PromptQueue<Ask> { a, b -> a.file == b.file }

    @Test
    fun `three protected files each get their prompt, in order`() {
        val q = queue()
        q.offer(Ask("a.pdf"))
        q.offer(Ask("b.pdf"))
        q.offer(Ask("c.pdf"))
        // The old single slot showed only c.pdf.
        assertEquals(Ask("a.pdf"), q.head.value)
        q.dropHead()
        assertEquals(Ask("b.pdf"), q.head.value)
        q.dropHead()
        assertEquals(Ask("c.pdf"), q.head.value)
        q.dropHead()
        assertNull(q.head.value)
    }

    @Test
    fun `answering a prompt removes only that file`() {
        val q = queue()
        q.offer(Ask("a.pdf"))
        q.offer(Ask("b.pdf"))
        q.remove { it.file == "a.pdf" }
        assertEquals(Ask("b.pdf"), q.head.value)
    }

    @Test
    fun `a wrong password asks again for that file without a duplicate`() {
        val q = queue()
        q.offer(Ask("a.pdf"))
        q.offer(Ask("b.pdf"))
        q.remove { it.file == "a.pdf" }
        q.offer(Ask("a.pdf", wrong = true))
        q.offer(Ask("a.pdf", wrong = true))
        assertEquals(Ask("b.pdf"), q.head.value)
        q.dropHead()
        assertEquals(Ask("a.pdf", wrong = true), q.head.value)
        q.dropHead()
        assertNull(q.head.value)
    }
}
