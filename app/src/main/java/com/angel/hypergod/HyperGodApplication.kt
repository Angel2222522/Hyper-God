package com.angel.hypergod

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import com.angel.hypergod.data.AppDatabase
import com.angel.hypergod.data.BackupService
import com.angel.hypergod.data.DataOperationCoordinator
import com.angel.hypergod.data.DocumentDeletionRecovery
import com.angel.hypergod.data.HyperGodRepository
import com.angel.hypergod.data.ReminderScheduler
import com.angel.hypergod.processing.DocumentProcessor
import com.angel.hypergod.security.StartupRecoveryStateStore
import com.angel.hypergod.security.TempFileCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HyperGodApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        DataOperationCoordinator.beginStartupRecovery()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var succeeded = false
            var failureMessage: String? = null
            try {
                StartupRecoveryStateStore.markInProgress(this@HyperGodApplication)
                BackupService(this@HyperGodApplication).recoverInterruptedRestore()
                DocumentDeletionRecovery.recover(this@HyperGodApplication, database)
                DocumentProcessor.reconcileInterruptedProcessing(database)
                HyperGodRepository(this@HyperGodApplication).apply {
                    reconcileImportStorage()
                    reconcileQueuedOcr()
                }
                runCatching { TempFileCleaner.recover(this@HyperGodApplication) }
                    .onFailure { android.util.Log.w("HyperGod", "Temporary-file cleanup was deferred", it) }
                runCatching { ReminderScheduler.rescheduleAll(this@HyperGodApplication) }
                    .onFailure { android.util.Log.w("HyperGod", "Reminder rescheduling was deferred", it) }
                StartupRecoveryStateStore.markSafe(this@HyperGodApplication)
                succeeded = true
            } catch (error: Throwable) {
                failureMessage = error.message?.take(500) ?: "Η ανάκτηση της βιβλιοθήκης απέτυχε."
                android.util.Log.e("HyperGod", "Startup recovery blocked normal operations", error)
                runCatching { StartupRecoveryStateStore.markBlocked(this@HyperGodApplication, failureMessage!!) }
            } finally {
                DataOperationCoordinator.completeStartupRecovery(succeeded, failureMessage)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "document_reminders",
                getString(com.angel.hypergod.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(com.angel.hypergod.R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
