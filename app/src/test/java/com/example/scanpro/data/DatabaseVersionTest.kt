package com.example.scanpro.data

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseVersionTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun header(version: Int, size: Int = 100): ByteArray =
        ByteArray(size).also { ByteBuffer.wrap(it).putInt(60, version) }

    @Test fun readsTheSchemaVersionFromTheFileHeader() {
        val file = tmp.newFile("a.db").also { it.writeBytes(header(7)) }
        assertEquals(7, databaseVersion(file))
    }

    @Test fun tooShortOrMissingFilesHaveNoVersion() {
        assertNull(databaseVersion(tmp.newFile("short.db").also { it.writeBytes(ByteArray(10)) }))
        assertNull(databaseVersion(File(tmp.root, "missing.db")))
    }
}
