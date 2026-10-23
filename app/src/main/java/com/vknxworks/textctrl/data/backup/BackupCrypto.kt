package com.vknxworks.textctrl.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/*
 * The encryption of TextCtrl backups.
 *
 * File: "TCBAK1\n", a 16-byte salt, the PBKDF2 round count (4 bytes) and a 4-byte nonce prefix, then the
 * content in chunks of up to 1 MiB, each [4-byte length][AES-256-GCM ciphertext and tag]. A chunk's nonce
 * is the prefix plus its number, and its associated data is its number plus whether it's the last chunk,
 * so chunks can't be reordered, dropped, swapped between backups or cut off without it being noticed. The
 * key comes from the password with PBKDF2-HMAC-SHA256.
 */

enum class BackupProblem { WRONG_PASSWORD, NOT_A_BACKUP, CUT_OFF, DAMAGED }

class BackupFormatException(val problem: BackupProblem) : IOException(problem.name)

private val MAGIC = "TCBAK1\n".toByteArray()
internal const val BACKUP_ROUNDS = 600_000
private const val CHUNK = 1 shl 20

private fun deriveKey(password: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, rounds, 256)
    val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(bytes, "AES")
}

private fun nonce(prefix: ByteArray, index: Long): ByteArray = ByteBuffer.allocate(12).put(prefix).putLong(index).array()
private fun aad(index: Long, last: Boolean): ByteArray = ByteBuffer.allocate(9).putLong(index).put(if (last) 1.toByte() else 0.toByte()).array()

/** Writes the header, then seals everything written in chunks. Closing writes the last chunk. */
class SealingOutputStream(private val out: OutputStream, password: CharArray, rounds: Int = BACKUP_ROUNDS) : OutputStream() {
    private val random = SecureRandom()
    private val salt = ByteArray(16).also(random::nextBytes)
    private val prefix = ByteArray(4).also(random::nextBytes)
    private val key = deriveKey(password, salt, rounds)
    private val buffer = ByteArray(CHUNK)
    private var filled = 0
    private var index = 0L
    private val data = DataOutputStream(out)
    private var closed = false

    init {
        data.write(MAGIC)
        data.write(salt)
        data.writeInt(rounds)
        data.write(prefix)
    }

    override fun write(b: Int) {
        if (filled == CHUNK) seal(last = false)
        buffer[filled++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var left = len
        while (left > 0) {
            if (filled == CHUNK) seal(last = false)
            val n = minOf(left, CHUNK - filled)
            System.arraycopy(b, o, buffer, filled, n)
            filled += n
            o += n
            left -= n
        }
    }

    private fun seal(last: Boolean) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(prefix, index)))
        cipher.updateAAD(aad(index, last))
        val ct = cipher.doFinal(buffer, 0, filled)
        data.writeInt(ct.size)
        data.write(ct)
        index++
        filled = 0
    }

    override fun flush() = data.flush()

    override fun close() {
        if (closed) return
        closed = true
        seal(last = true)
        data.flush()
        out.close()
    }
}

/** Reads the header, then opens the chunks one by one, refusing anything changed, reordered or cut off. */
class OpeningInputStream(input: InputStream, password: CharArray) : InputStream() {
    private val data = DataInputStream(input)
    private val key: SecretKeySpec
    private val prefix = ByteArray(4)
    private var chunk = ByteArray(0)
    private var pos = 0
    private var index = 0L
    private var ended = false

    init {
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (e: EOFException) {
            throw BackupFormatException(BackupProblem.NOT_A_BACKUP)
        }
        if (!magic.contentEquals(MAGIC)) throw BackupFormatException(BackupProblem.NOT_A_BACKUP)
        val salt = ByteArray(16)
        data.readFully(salt)
        val rounds = data.readInt()
        if (rounds < 1_000 || rounds > 10_000_000) throw BackupFormatException(BackupProblem.NOT_A_BACKUP)
        data.readFully(prefix)
        key = deriveKey(password, salt, rounds)
    }

    private fun next(): Boolean {
        if (ended) return false
        val size = try {
            data.readInt()
        } catch (e: EOFException) {
            throw BackupFormatException(BackupProblem.CUT_OFF)
        }
        if (size < 16 || size > CHUNK + 16) throw BackupFormatException(BackupProblem.DAMAGED)
        val ct = ByteArray(size)
        try {
            data.readFully(ct)
        } catch (e: EOFException) {
            throw BackupFormatException(BackupProblem.CUT_OFF)
        }
        // Only the last chunk says so in its associated data.
        val plain = open(ct, last = false) ?: open(ct, last = true)?.also { ended = true }
            ?: throw BackupFormatException(if (index == 0L) BackupProblem.WRONG_PASSWORD else BackupProblem.DAMAGED)
        chunk = plain
        pos = 0
        index++
        return true
    }

    private fun open(ct: ByteArray, last: Boolean): ByteArray? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(prefix, index)))
        cipher.updateAAD(aad(index, last))
        cipher.doFinal(ct)
    } catch (e: AEADBadTagException) {
        null
    }

    override fun read(): Int {
        while (pos >= chunk.size) if (!next()) return -1
        return chunk[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pos >= chunk.size) if (!next()) return -1
        val n = minOf(len, chunk.size - pos)
        System.arraycopy(chunk, pos, b, off, n)
        pos += n
        return n
    }

    override fun close() = data.close()
}
