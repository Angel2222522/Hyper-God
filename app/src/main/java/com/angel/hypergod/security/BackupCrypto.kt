package com.angel.hypergod.security

import android.net.Uri
import android.content.Context
import android.os.CancellationSignal
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private val MAGIC_V1 = "PFBK1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "PFBK2".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val LEGACY_ITERATIONS = 120_000
    private const val CURRENT_ITERATIONS = 600_000

    fun encryptFile(input: File, context: Context, destination: Uri, password: CharArray) {
        try {
            val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
            val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
            val key = key(password, salt, CURRENT_ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                writeHeader(output, salt, iv, CURRENT_ITERATIONS)
                CipherOutputStream(output, cipher).use { encrypted -> FileInputStream(input).use { it.copyTo(encrypted) } }
            } ?: error("Δεν ήταν δυνατή η δημιουργία του αντιγράφου.")
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun decryptToFile(
        context: Context,
        source: Uri,
        destination: File,
        password: CharArray,
        maxBytes: Long = MAX_DECRYPTED_BYTES
    ) {
        val temporary = File(destination.parentFile ?: destination.absoluteFile.parentFile!!, ".${destination.name}.${System.nanoTime()}.part")
        try {
            withTimeout(MAX_DECRYPT_DURATION_MS) {
                withContext(Dispatchers.IO) {
                    val cancellationSignal = CancellationSignal()
                    var assetDescriptor: android.content.res.AssetFileDescriptor? = null
                    val job = kotlin.coroutines.coroutineContext[Job]
                        ?: error("Δεν υπάρχει ενεργή εργασία επαναφοράς.")
                    val cancellationHandle = job.invokeOnCompletion {
                        cancellationSignal.cancel()
                        runCatching { assetDescriptor?.close() }
                    }
                    try {
                        assetDescriptor = context.contentResolver.openAssetFileDescriptor(source, "r", cancellationSignal)
                            ?: error("Δεν ήταν δυνατή η ανάγνωση του αντιγράφου.")
                        job.ensureActive()
                        val reportedLength = assetDescriptor!!.length
                        if (reportedLength >= 0L) {
                            require(reportedLength in MIN_ENCRYPTED_BYTES..MAX_ENCRYPTED_BYTES) {
                                "Το κρυπτογραφημένο αντίγραφο είναι υπερβολικά μεγάλο ή ελλιπές."
                            }
                        }
                        val requiredFreeBytes = if (reportedLength >= 0L) {
                            reportedLength.coerceAtMost(maxBytes) + MIN_FREE_SPACE_BYTES
                        } else {
                            MIN_FREE_SPACE_BYTES
                        }
                        require((temporary.parentFile ?: context.cacheDir).usableSpace >= requiredFreeBytes) {
                            "Δεν υπάρχει αρκετός ελεύθερος χώρος για ασφαλή επαναφορά."
                        }

                        assetDescriptor!!.createInputStream().use { providerInput ->
                            val input = BoundedInputStream(providerInput, MAX_ENCRYPTED_BYTES) { job.ensureActive() }
                            val salt = ByteArray(SALT_SIZE)
                            val iv = ByteArray(IV_SIZE)
                            val magic = ByteArray(MAGIC_V2.size)
                            input.readFully(magic)
                            val iterations = when {
                                magic.contentEquals(MAGIC_V2) -> {
                                    val encoded = ByteArray(Int.SIZE_BYTES)
                                    input.readFully(encoded)
                                    ByteBuffer.wrap(encoded).int.also {
                                        require(it in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
                                            "Το αντίγραφο δηλώνει μη ασφαλείς παραμέτρους κωδικού."
                                        }
                                    }
                                }
                                magic.contentEquals(MAGIC_V1) -> LEGACY_ITERATIONS
                                else -> error("Δεν αναγνωρίζεται το αρχείο αντιγράφου.")
                            }
                            input.readFully(salt)
                            input.readFully(iv)
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                                init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
                            }
                            temporary.parentFile?.mkdirs()
                            CipherInputStream(input, cipher).use { decrypted ->
                                FileOutputStream(temporary).use { output ->
                                    decrypted.copyLimitedTo(output, maxBytes) { job.ensureActive() }
                                }
                            }
                        }
                    } finally {
                        cancellationHandle.dispose()
                        runCatching { assetDescriptor?.close() }
                    }
                }
            }
            require(temporary.renameTo(destination)) { "Δεν ήταν δυνατή η ολοκλήρωση του αντιγράφου." }
        } finally {
            temporary.delete()
            password.fill('\u0000')
        }
    }

    private fun writeHeader(output: OutputStream, salt: ByteArray, iv: ByteArray, iterations: Int) {
        output.write(MAGIC_V2)
        output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(iterations).array())
        output.write(salt)
        output.write(iv)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            require(count >= 0) { "Ατελές αντίγραφο ασφαλείας." }
            offset += count
        }
    }

    private fun InputStream.copyLimitedTo(output: OutputStream, maxBytes: Long, ensureActive: () -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            ensureActive()
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Το αντίγραφο είναι υπερβολικά μεγάλο." }
            output.write(buffer, 0, read)
        }
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long,
        private val ensureActive: () -> Unit
    ) : InputStream() {
        private var total = 0L

        override fun read(): Int {
            ensureActive()
            val value = delegate.read()
            if (value >= 0) account(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            ensureActive()
            val read = delegate.read(buffer, offset, length)
            if (read > 0) account(read.toLong())
            return read
        }

        override fun close() = delegate.close()

        private fun account(count: Long) {
            total += count
            require(total <= maxBytes) { "Το κρυπτογραφημένο αντίγραφο είναι υπερβολικά μεγάλο." }
        }
    }

    /** Matches the mobile-safe aggregate archive budget plus encryption framing. */
    private const val MAX_DECRYPTED_BYTES = 512L * 1024 * 1024 + 16L * 1024 * 1024
    private const val MAX_ENCRYPTED_BYTES = MAX_DECRYPTED_BYTES + 64L
    private const val MIN_ENCRYPTED_BYTES = 5L + SALT_SIZE + IV_SIZE + 16L
    private const val MIN_ACCEPTED_ITERATIONS = LEGACY_ITERATIONS
    private const val MAX_ACCEPTED_ITERATIONS = 5_000_000
    private const val MIN_FREE_SPACE_BYTES = 64L * 1024 * 1024
    private const val MAX_DECRYPT_DURATION_MS = 10L * 60 * 1000
}
