package com.angel.hypergod.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coordinates startup recovery and short database/filesystem commit phases.
 *
 * Long OCR, rendering and export work must not hold the process-wide mutex.
 * Per-document work uses [withDocumentExclusive] so an individual document
 * cannot be deleted while its encrypted source is being read, while unrelated
 * documents remain available.
 */
object DataOperationCoordinator {
    private val mutex = Mutex()
    private val maintenanceMutex = Mutex()
    private val generationStateMutex = Mutex()
    private val documentMutexes = ConcurrentHashMap<String, Mutex>()
    private var activeGenerationReaders = 0
    private var maintenanceActive = false
    private var maintenanceFinished = CompletableDeferred(Unit)
    private var readersDrained = CompletableDeferred(Unit)
    @Volatile private var startupRecoveryGate: CompletableDeferred<Unit>? = null
    @Volatile private var userSessionRequired = false
    @Volatile private var userSessionUnlocked = true

    private val _recoveryState = MutableStateFlow(StartupRecoveryState.SAFE)
    val recoveryState: StateFlow<StartupRecoveryState> = _recoveryState.asStateFlow()

    enum class StartupRecoveryState { IN_PROGRESS, SAFE, BLOCKED }

    class RecoveryBlockedException(message: String) : IllegalStateException(message)

    /** Called synchronously by Application before any UI operation can run. */
    fun beginStartupRecovery() {
        synchronized(this) {
            if (startupRecoveryGate?.isCompleted != false) {
                startupRecoveryGate = CompletableDeferred()
            }
            _recoveryState.value = StartupRecoveryState.IN_PROGRESS
        }
    }

    /** Opens normal operations only after all recovery evidence is safe. */
    fun completeStartupRecovery(success: Boolean, message: String? = null) {
        _recoveryState.value = if (success) StartupRecoveryState.SAFE else StartupRecoveryState.BLOCKED
        startupRecoveryGate?.complete(Unit)
        if (!success) {
            blockedMessage = message?.take(500) ?: "Η ανάκτηση της βιβλιοθήκης δεν ολοκληρώθηκε με ασφάλεια."
        }
    }

    @Volatile private var blockedMessage: String = "Η ανάκτηση της βιβλιοθήκης δεν ολοκληρώθηκε με ασφάλεια."

    fun recoveryMessage(): String = blockedMessage

    fun setUserSessionState(lockEnabled: Boolean, unlocked: Boolean) {
        userSessionRequired = lockEnabled
        userSessionUnlocked = !lockEnabled || unlocked
    }

    fun requireUserSessionUnlocked() {
        if (userSessionRequired && !userSessionUnlocked) {
            throw RecoveryBlockedException("Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά για να συνεχίσεις.")
        }
    }

    fun requireRecoverySafe() {
        if (_recoveryState.value != StartupRecoveryState.SAFE) {
            throw RecoveryBlockedException(blockedMessage)
        }
    }

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        startupRecoveryGate?.await()
        requireRecoverySafe()
        if (coroutineContext[ExclusiveLease] != null) return block()
        return withLockHeld(block)
    }

    /**
     * Holds a stable document generation while long reads such as OCR, export
     * and backup creation are in progress. Nested calls in the same coroutine
     * are re-entrant so per-document locks can be used safely inside a lease.
     */
    suspend fun <T> withGenerationRead(block: suspend () -> T): T {
        startupRecoveryGate?.await()
        requireRecoverySafe()
        if (coroutineContext[GenerationReadLease] != null) return block()

        while (true) {
            val waitForMaintenance = generationStateMutex.withLock {
                if (!maintenanceActive) {
                    activeGenerationReaders += 1
                    null
                } else {
                    maintenanceFinished
                }
            }
            if (waitForMaintenance == null) break
            waitForMaintenance.await()
        }

        return try {
            withContext(GenerationReadLease()) { block() }
        } finally {
            generationStateMutex.withLock {
                activeGenerationReaders -= 1
                check(activeGenerationReaders >= 0) { "Μη έγκυρη κατάσταση γενιάς δεδομένων." }
                if (activeGenerationReaders == 0) readersDrained.complete(Unit)
            }
        }
    }

    /**
     * Gives one restore operation ownership of the complete generation and the
     * global journal until rollback/final cleanup has completed. New readers
     * wait, existing readers drain, and ordinary short mutations are excluded
     * by the process-wide mutex for the whole maintenance lifecycle.
     */
    suspend fun <T> withMaintenance(block: suspend () -> T): T {
        startupRecoveryGate?.await()
        requireRecoverySafe()
        return maintenanceMutex.withLock {
            val drain = generationStateMutex.withLock {
                maintenanceActive = true
                maintenanceFinished = CompletableDeferred()
                if (activeGenerationReaders == 0) {
                    CompletableDeferred(Unit)
                } else {
                    CompletableDeferred<Unit>().also { readersDrained = it }
                }
            }
            try {
                drain.await()
                withLockHeld(block)
            } finally {
                generationStateMutex.withLock {
                    maintenanceActive = false
                    maintenanceFinished.complete(Unit)
                    readersDrained = CompletableDeferred(Unit)
                }
            }
        }
    }

    /** Used only by startup recovery itself, before the normal gate opens. */
    suspend fun <T> withExclusiveDuringStartup(block: suspend () -> T): T = withLockHeld(block)

    /**
     * Serializes long work for one document without blocking all library
     * operations. This is also used around the final state transition.
     */
    suspend fun <T> withDocumentExclusive(documentId: String, block: suspend () -> T): T {
        return withGenerationRead {
            val lock = documentMutexes.computeIfAbsent(documentId) { Mutex() }
            lock.lock()
            try {
                block()
            } finally {
                lock.unlock()
                if (!lock.isLocked) documentMutexes.remove(documentId, lock)
            }
        }
    }

    private suspend fun <T> withLockHeld(block: suspend () -> T): T {
        if (coroutineContext[ExclusiveLease] != null) return block()
        mutex.lock()
        return try {
            withContext(ExclusiveLease()) { block() }
        } finally {
            mutex.unlock()
        }
    }

    private class GenerationReadLease :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<GenerationReadLease>
    }

    private class ExclusiveLease :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ExclusiveLease>
    }
}
