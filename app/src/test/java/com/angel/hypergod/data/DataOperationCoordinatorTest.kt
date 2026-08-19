package com.angel.hypergod.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataOperationCoordinatorTest {
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
