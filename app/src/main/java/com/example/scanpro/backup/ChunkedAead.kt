package com.example.scanpro.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Streaming AES-GCM in independent chunks. Android's GCM implementation buffers a whole stream
 * until doFinal, so encrypting a large backup with one Cipher runs out of memory. Each chunk here is
 * sealed separately with a nonce built from a random prefix, the chunk counter and a final-chunk
 * flag, so reordered, dropped, truncated or extended chunks all fail authentication.
 *
 * Record layout: [flag: 1 byte][ciphertext length: int][ciphertext + 16-byte tag].
 */
internal object ChunkedAead {
    const val PREFIX_BYTES = 7
    const val CHUNK_BYTES = 64 * 1024
    private const val TAG_BITS = 128
    private const val MAX_RECORD_BYTES = CHUNK_BYTES + TAG_BITS / 8

    fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    class Writer(out: OutputStream, private val key: SecretKey, private val prefix: ByteArray) : OutputStream() {
        private val output = DataOutputStream(out)
        private val buffer = ByteArray(CHUNK_BYTES)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) {
            if (filled == buffer.size) flushChunk(last = false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                if (filled == buffer.size) flushChunk(last = false)
                val count = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, offset, buffer, filled, count)
                filled += count
                offset += count
                remaining -= count
            }
        }

        private fun flushChunk(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter, last)))
            }
            val sealed = cipher.doFinal(buffer, 0, filled)
            output.writeByte(if (last) 1 else 0)
            output.writeInt(sealed.size)
            output.write(sealed)
            counter++
            filled = 0
        }

        /** Writes the final chunk (possibly empty), which marks the end of the stream. */
        override fun close() {
            if (closed) return
            closed = true
            flushChunk(last = true)
            output.flush()
            output.close()
        }
    }

    class Reader(input: InputStream, private val key: SecretKey, private val prefix: ByteArray) : InputStream() {
        private val source = DataInputStream(input)
        private var chunk = ByteArray(0)
        private var position = 0
        private var counter = 0
        private var finished = false

        private fun nextChunk(): Boolean {
            if (finished) return false
            val flag = try {
                source.readUnsignedByte()
            } catch (_: EOFException) {
                throw IOException("Backup is truncated")
            }
            val last = flag == 1
            val length = source.readInt()
            if (flag > 1 || length < TAG_BITS / 8 || length > MAX_RECORD_BYTES) throw IOException("Corrupt backup")
            val sealed = ByteArray(length).also(source::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter, last)))
            }
            chunk = cipher.doFinal(sealed)
            position = 0
            counter++
            if (last) {
                finished = true
                if (source.read() != -1) throw IOException("Unexpected data after the end of the backup")
            }
            return true
        }

        override fun read(): Int {
            while (position >= chunk.size) if (!nextChunk()) return -1
            return chunk[position++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position >= chunk.size) if (!nextChunk()) return -1
            val count = minOf(len, chunk.size - position)
            System.arraycopy(chunk, position, b, off, count)
            position += count
            return count
        }

        override fun close() = source.close()
    }
}
