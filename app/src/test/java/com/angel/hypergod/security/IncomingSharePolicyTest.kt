package com.angel.hypergod.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingSharePolicyTest {
    @Test
    fun validContentUrisAreAccepted() {
        assertNull(
            IncomingSharePolicy.rejectionReason(
                listOf(IncomingSharePolicy.Candidate("content", 120))
            )
        )
    }

    @Test
    fun overlongOrNonContentUrisAreRejectedWithoutThrowing() {
        assertNotNull(
            IncomingSharePolicy.rejectionReason(
                listOf(IncomingSharePolicy.Candidate("content", IncomingSharePolicy.MAX_SERIALIZED_URI_LENGTH + 1))
            )
        )
        assertNotNull(
            IncomingSharePolicy.rejectionReason(
                listOf(IncomingSharePolicy.Candidate("file", 20))
            )
        )
    }

    @Test
    fun oversizedBatchesAreRejected() {
        val candidates = List(IncomingSharePolicy.MAX_URIS + 1) {
            IncomingSharePolicy.Candidate("content", 40)
        }
        assertNotNull(IncomingSharePolicy.rejectionReason(candidates))
    }
}
