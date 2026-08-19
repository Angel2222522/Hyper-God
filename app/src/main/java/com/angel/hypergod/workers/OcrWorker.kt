package com.angel.hypergod.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.angel.hypergod.data.AppDatabase
import com.angel.hypergod.processing.DocumentProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OcrWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = processSemaphore.withPermit {
        activeWorkers.incrementAndGet()
        idle.value = false
        return@withPermit try {
            val id = inputData.getString(KEY_DOCUMENT_ID) ?: return@withPermit Result.failure()
            val result = try {
                withTimeout(MAX_OCR_DURATION_MS) {
                    DocumentProcessor(applicationContext, AppDatabase.get(applicationContext)).process(id)
                }
            } catch (_: TimeoutCancellationException) {
                return@withPermit Result.failure()
            }
            when {
                result.isSuccess -> Result.success()
                result.exceptionOrNull() is IOException -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            if (activeWorkers.decrementAndGet() == 0) idle.value = true
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        private val activeWorkers = AtomicInteger(0)
        private val idle = MutableStateFlow(true)
        private val processSemaphore = Semaphore(1)
        private const val MAX_OCR_DURATION_MS = 30L * 60 * 1000

        suspend fun awaitIdle() = idle.first { it }
    }
}
