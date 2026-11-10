package com.example.scanpro.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test

/** Round trips and tampering for the chunked AES-GCM stream: any damage must fail, never decrypt to something else. */
class ChunkedAeadFuzzTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val prefix = ByteArray(ChunkedAead.PREFIX_BYTES) { (it + 1).toByte() }

    private fun seal(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedAead.Writer(out, key, prefix).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray): ByteArray =
        ChunkedAead.Reader(ByteArrayInputStream(sealed), key, prefix).use { it.readBytes() }

    private fun assertRejected(sealed: ByteArray, what: String) {
        try {
            open(sealed)
            fail("$what was accepted")
        } catch (_: IOException) {
            // expected: authentication, truncation or format failure
        } catch (_: java.security.GeneralSecurityException) {
            // expected
        }
    }

    private fun data(size: Int, seed: Long) = ByteArray(size).also { Random(seed).nextBytes(it) }

    @Test fun roundTripsAroundChunkBoundaries() {
        val c = ChunkedAead.CHUNK_BYTES
        for (size in listOf(0, 1, c - 1, c, c + 1, 2 * c, 2 * c + 7, 5 * c + 123)) {
            val plain = data(size, size.toLong())
            assertArrayEquals("size $size", plain, open(seal(plain)))
        }
    }

    @Test fun everySingleBitFlipIsRejected() {
        val sealed = seal(data(300, 1))
        val random = Random(42)
        repeat(300) {
            val copy = sealed.copyOf()
            val index = random.nextInt(copy.size)
            copy[index] = (copy[index].toInt() xor (1 shl random.nextInt(8))).toByte()
            assertRejected(copy, "flip at byte $index")
        }
    }

    @Test fun truncationAtAnyLengthIsRejected() {
        val sealed = seal(data(3 * ChunkedAead.CHUNK_BYTES + 10, 2))
        val random = Random(7)
        val cuts = (listOf(0, 1, 4, 5, sealed.size - 1, sealed.size - 17) + List(40) { random.nextInt(sealed.size) })
            .filter { it in 0 until sealed.size }
        for (cut in cuts) assertRejected(sealed.copyOf(cut), "truncation to $cut bytes")
    }

    @Test fun droppingTheLastChunkIsRejected() {
        val c = ChunkedAead.CHUNK_BYTES
        val sealed = seal(data(2 * c + 5, 3))
        val record = 1 + 4 + c + 16
        // Remove the final (short) record: the stream then ends without a final-flagged chunk.
        assertRejected(sealed.copyOf(2 * record), "stream without its final chunk")
    }

    @Test fun reorderedChunksAreRejected() {
        val c = ChunkedAead.CHUNK_BYTES
        val sealed = seal(data(3 * c, 4))
        val record = 1 + 4 + c + 16
        val swapped = sealed.copyOf()
        System.arraycopy(sealed, record, swapped, 0, record)
        System.arraycopy(sealed, 0, swapped, record, record)
        assertRejected(swapped, "swapped chunks")
    }

    @Test fun dataAfterTheFinalChunkIsRejected() {
        assertRejected(seal(data(100, 5)) + byteArrayOf(0), "trailing byte")
    }

    @Test fun wrongKeyOrPrefixIsRejected() {
        val sealed = seal(data(1000, 6))
        val otherKey = SecretKeySpec(ByteArray(32) { 9 }, "AES")
        try {
            ChunkedAead.Reader(ByteArrayInputStream(sealed), otherKey, prefix).use { it.readBytes() }
            fail("wrong key accepted")
        } catch (_: IOException) {
        } catch (_: java.security.GeneralSecurityException) {
        }
        try {
            ChunkedAead.Reader(ByteArrayInputStream(sealed), key, ByteArray(prefix.size)).use { it.readBytes() }
            fail("wrong nonce prefix accepted")
        } catch (_: IOException) {
        } catch (_: java.security.GeneralSecurityException) {
        }
    }

    @Test fun randomGarbageIsRejected() {
        val random = Random(99)
        repeat(50) { assertRejected(data(random.nextInt(500), random.nextLong()), "random bytes") }
    }
}
