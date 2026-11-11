package com.example.scanpro.data

import android.app.Application
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SetAsideNewerDatabaseTest {
    private lateinit var context: Context
    private val name = "test-room.db"

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(name).parentFile?.mkdirs()
    }

    private fun bytes(version: Int) = ByteArray(100).also { ByteBuffer.wrap(it).putInt(60, version) }

    private fun write(suffix: String, content: ByteArray) =
        File(context.getDatabasePath(name).path + suffix).also { it.writeBytes(content) }

    @Test fun aNewerDatabaseAndItsJournalFilesAreMovedAsideUnchanged() {
        val main = bytes(5)
        write("", main)
        write("-wal", byteArrayOf(1, 2, 3))
        write("-shm", byteArrayOf(4, 5))

        setAsideNewerDatabase(context, name, currentVersion = 3)

        val base = context.getDatabasePath(name).path
        assertFalse(File(base).exists())
        assertFalse(File("$base-wal").exists())
        assertFalse(File("$base-shm").exists())
        assertArrayEquals(main, File("$base.newer-v5").readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), File("$base.newer-v5-wal").readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File("$base.newer-v5-shm").readBytes())
    }

    @Test fun aDatabaseOfTheSameOrAnOlderVersionIsLeftAlone() {
        for (version in listOf(3, 2)) {
            val file = write("", bytes(version))
            setAsideNewerDatabase(context, name, currentVersion = 3)
            assertTrue("v$version must stay", file.exists())
            assertFalse(File("${file.path}.newer-v$version").exists())
        }
    }

    @Test fun aMissingOrUnreadableFileIsIgnored() {
        setAsideNewerDatabase(context, name, currentVersion = 3)
        val tooShort = write("", ByteArray(10))
        setAsideNewerDatabase(context, name, currentVersion = 3)
        assertTrue(tooShort.exists())
    }

    @Test fun aMissingWalOrShmFileIsNotAnError() {
        write("", bytes(9))
        setAsideNewerDatabase(context, name, currentVersion = 3)
        assertTrue(File(context.getDatabasePath(name).path + ".newer-v9").exists())
    }
}
