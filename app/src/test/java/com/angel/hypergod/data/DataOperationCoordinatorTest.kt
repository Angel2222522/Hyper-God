package com.angel.hypergod.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataOperationCoordinatorTest {
    @Test
    fun filesystemAndDatabaseGenerationOperationsDoNotOverlap() = runTest {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val first = async {
            DataOperationCoordinator.withExclusive {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(20)
                active.decrementAndGet()
            }
        }
        val second = async {
            DataOperationCoordinator.withExclusive {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(20)
                active.decrementAndGet()
            }
        }
        first.await()
        second.await()
        assertEquals(1, peak.get())
    }

    @Test
    fun normalOperationsWaitForStartupRecovery() = runTest {
        DataOperationCoordinator.beginStartupRecovery()
        try {
            val entered = CompletableDeferred<Unit>()
            val operation = async {
                DataOperationCoordinator.withExclusive { entered.complete(Unit) }
            }
            delay(20)
            assertFalse(entered.isCompleted)
            DataOperationCoordinator.completeStartupRecovery(success = true)
            operation.await()
        } finally {
            DataOperationCoordinator.completeStartupRecovery(success = true)
        }
    }

    @Test
    fun failedStartupRecoveryBlocksNewMutations() = runTest {
        DataOperationCoordinator.beginStartupRecovery()
        DataOperationCoordinator.completeStartupRecovery(false, "δοκιμασμένη αποτυχία")
        try {
            var blocked = false
            try {
                DataOperationCoordinator.withExclusive { Unit }
            } catch (_: DataOperationCoordinator.RecoveryBlockedException) {
                blocked = true
            }
            assertTrue(blocked)
            assertEquals(DataOperationCoordinator.StartupRecoveryState.BLOCKED, DataOperationCoordinator.recoveryState.value)
            assertEquals("δοκιμασμένη αποτυχία", DataOperationCoordinator.recoveryMessage())
        } finally {
            DataOperationCoordinator.completeStartupRecovery(true)
        }
    }

    @Test
    fun maintenanceWaitsForExistingGenerationReader() = runTest {
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()
        val maintenanceEntered = CompletableDeferred<Unit>()
        val reader = async {
            DataOperationCoordinator.withGenerationRead {
                readerEntered.complete(Unit)
                releaseReader.await()
            }
        }
        readerEntered.await()
        val maintenance = async {
            DataOperationCoordinator.withMaintenance { maintenanceEntered.complete(Unit) }
        }
        yield()
        assertFalse(maintenanceEntered.isCompleted)
        releaseReader.complete(Unit)
        reader.await()
        maintenance.await()
        assertTrue(maintenanceEntered.isCompleted)
    }

    @Test
    fun nestedGenerationReadIsReentrant() = runTest {
        var reached = false
        DataOperationCoordinator.withGenerationRead {
            DataOperationCoordinator.withGenerationRead { reached = true }
        }
        assertTrue(reached)
    }
}
