package com.example.scanpro.core.operations

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditJournalTest {
    @get:Rule val temp = TemporaryFolder()

    private fun edit(n: Int) = JSONObject().put("n", n)

    @Test
    fun `edits still waiting come back in the order they were asked for`() {
        val dir = temp.newFolder("journal")
        val first = EditJournal(dir)
        listOf(1, 2, 3).forEach { first.add(edit(it)) }
        val recovered = EditJournal(dir).recover()
        assertEquals(listOf(1, 2, 3), recovered.queued.map { it.second.getInt("n") })
        assertEquals(0, recovered.interrupted)
    }

    @Test
    fun `an edit that had started is reported, never run again`() {
        val dir = temp.newFolder("journal")
        val journal = EditJournal(dir)
        val started = journal.add(edit(1))
        journal.add(edit(2))
        journal.markStarted(started)
        val recovered = EditJournal(dir).recover()
        assertEquals(listOf(2), recovered.queued.map { it.second.getInt("n") })
        assertEquals(1, recovered.interrupted)
        // Reported once: the next launch doesn't count it again.
        assertEquals(0, EditJournal(dir).recover().interrupted)
    }

    @Test
    fun `finished edits leave nothing behind`() {
        val dir = temp.newFolder("journal")
        val journal = EditJournal(dir)
        val a = journal.add(edit(1))
        val b = journal.add(edit(2))
        journal.remove(a)
        journal.markStarted(b)
        journal.remove(b)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `unreadable records and half-written files are dropped`() {
        val dir = temp.newFolder("journal")
        File(dir, "5.json").writeText("{not json")
        File(dir, "6.tmp").writeText("{}")
        val journal = EditJournal(dir)
        journal.add(edit(7))
        val recovered = EditJournal(dir).recover()
        assertEquals(listOf(7), recovered.queued.map { it.second.getInt("n") })
        assertEquals(listOf(recovered.queued.single().first.toString() + ".json"), dir.list().orEmpty().toList())
    }

    @Test
    fun `new edits sort after recovered ones`() {
        val dir = temp.newFolder("journal")
        File(dir, "${Long.MAX_VALUE / 2}.json").writeText(edit(1).toString())
        val journal = EditJournal(dir)
        val recovered = journal.recover()
        val next = journal.add(edit(2))
        assertTrue(next > recovered.queued.single().first)
    }
}
