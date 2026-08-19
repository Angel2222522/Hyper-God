package com.angel.hypergod.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.angel.hypergod.security.BackupCrypto
import com.angel.hypergod.security.FileCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val password = "correct horse battery"
    private lateinit var database: AppDatabase
    private lateinit var documentId: String
    private lateinit var documentRoot: File

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.get(context)
        documentId = "backup-test-${UUID.randomUUID()}"
        documentRoot = context.filesDir.resolve("documents/$documentId").apply { mkdirs() }
        val encrypted = documentRoot.resolve("page_0.pf")
        val imageBytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
            output.toByteArray()
        }
        FileCrypto.encrypt(ByteArrayInputStream(imageBytes), encrypted)
        val now = System.currentTimeMillis()
        database.withTransaction {
            database.documentDao().insert(DocumentEntity(documentId, "Backup test", "test.png", "image/png", encrypted.absolutePath, 1, processingState = ProcessingState.PROCESSED, createdAt = now, updatedAt = now))
            database.documentPageDao().insertAll(listOf(DocumentPageEntity(documentId, 0, encrypted.absolutePath, "OCR test", "test.png", "image/png")))
        }
    }

    @After
    fun tearDown() = runBlocking {
        database.withTransaction {
            database.documentPageDao().deleteForDocument(documentId)
            database.documentDao().deleteById(documentId)
        }
        FileCrypto.deleteRecursively(documentRoot)
        context.cacheDir.resolve("share").listFiles().orEmpty().filter { it.name.startsWith("backup-test") }.forEach(FileCrypto::deleteRecursively)
    }

    @Test
    fun backupCryptoRoundTripAndWrongPasswordAreRejected() = runBlocking {
        val source = context.cacheDir.resolve("share/backup-test-source.bin").apply { parentFile?.mkdirs(); writeText("payload") }
        val encrypted = context.cacheDir.resolve("share/backup-test-encrypted.bin").apply { createNewFile() }
        val encryptedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", encrypted)
        BackupCrypto.encryptFile(source, context, encryptedUri, password.toCharArray())
        val restored = context.cacheDir.resolve("share/backup-test-restored.bin")
        BackupCrypto.decryptToFile(context, encryptedUri, restored, password.toCharArray())
        assertEquals("payload", restored.readText())
        try {
            BackupCrypto.decryptToFile(context, encryptedUri, context.cacheDir.resolve("share/backup-test-wrong.bin"), "wrong password".toCharArray())
            fail("A wrong password must be rejected")
        } catch (_: Exception) {
            // Expected authenticated-decryption failure.
        }
        source.delete(); encrypted.delete(); restored.delete()
    }

    @Test
    fun newBackupsRejectShortPasswords() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-short-password.hgb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            try {
                BackupService(context).create(backupUri, "short")
                fail("A new backup must reject a short password")
            } catch (_: IllegalArgumentException) {
                // Expected policy rejection.
            } finally {
                backup.delete()
            }
        }
    }

    @Test
    fun portableBackupRestoreRoundTripPreservesPageAndOcr() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-roundtrip.hgb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            val service = BackupService(context)
            service.create(backupUri, password)

            database.withTransaction {
                database.documentPageDao().deleteForDocument(documentId)
                database.documentDao().deleteById(documentId)
            }
            FileCrypto.deleteRecursively(documentRoot)
            service.restore(backupUri, password)

            val restored = database.documentDao().getById(documentId)
            assertNotNull(restored)
            assertEquals("OCR test", database.documentPageDao().getForDocument(documentId).single().ocrText)
            val plain = context.cacheDir.resolve("share/backup-test-plain.bin")
            FileCrypto.decryptToTemp(File(restored!!.encryptedPath), plain)
            val decoded = BitmapFactory.decodeFile(plain.absolutePath)
            assertNotNull(decoded)
            decoded?.recycle()
            plain.delete()
        }
    }

    @Test
    fun portableRoundTripPreservesAValidFarFutureReminder() {
        runBlocking {
            database.documentDao().getById(documentId)!!.let { document ->
                database.documentDao().update(
                    document.copy(
                        expiryDate = "2099-12-31",
                        expiryDateConfidence = MetadataConfidence.MANUAL,
                        expiryDateManuallyEdited = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            val dueAt = ReminderDatePolicy.dueAt(LocalDate.of(2099, 12, 31), 30)
            val deadlineAt = ReminderDatePolicy.deadlineAt(LocalDate.of(2099, 12, 31))
            database.reminderDao().insert(
                ReminderEntity(
                    id = "reminder-${UUID.randomUUID()}",
                    title = "Μελλοντική υπενθύμιση",
                    dueAt = dueAt,
                    documentId = documentId,
                    leadDays = 30,
                    deadlineAt = deadlineAt
                )
            )
            val backup = context.cacheDir.resolve("share/backup-test-future.hgb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            try {
                val service = BackupService(context)
                service.create(backupUri, password)
                database.withTransaction {
                    database.documentPageDao().deleteForDocument(documentId)
                    database.documentDao().deleteById(documentId)
                }
                FileCrypto.deleteRecursively(documentRoot)
                service.restore(backupUri, password)

                val restored = database.reminderDao().getForDocument(documentId)
                assertEquals(1, restored.size)
                assertEquals(dueAt, restored.single().dueAt)
                assertEquals(deadlineAt, restored.single().deadlineAt)
            } finally {
                backup.delete()
            }
        }
    }

    @Test
    fun corruptedPortableBackupDoesNotChangeDatabase() {
        runBlocking {
            val backup = context.cacheDir.resolve("share/backup-test-corrupt.hgb").apply { parentFile?.mkdirs(); createNewFile() }
            val backupUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
            BackupService(context).create(backupUri, password)
            val bytes = backup.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x7f).toByte()
            backup.writeBytes(bytes)
            assertThrows(Exception::class.java) { runBlocking { BackupService(context).restore(backupUri, password) } }
            assertNotNull(database.documentDao().getById(documentId))
        }
    }
}
