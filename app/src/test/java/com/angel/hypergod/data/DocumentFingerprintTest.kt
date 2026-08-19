package com.angel.hypergod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFingerprintTest {
    @Test
    fun orderedSourcesProduceStableButOrderSensitiveFingerprint() {
        val first = "a".repeat(64)
        val second = "b".repeat(64)
        assertEquals(
            DocumentFingerprint.combineOrderedSourceHashes(listOf(first, second)),
            DocumentFingerprint.combineOrderedSourceHashes(listOf(first, second))
        )
        assertNotEquals(
            DocumentFingerprint.combineOrderedSourceHashes(listOf(first, second)),
            DocumentFingerprint.combineOrderedSourceHashes(listOf(second, first))
        )
    }

    @Test
    fun duplicateIdsAreSuggestedWithoutMergingUniqueRecords() {
        val hash = DocumentFingerprint.combineOrderedSourceHashes(listOf("c".repeat(64)))
        val first = DocumentFingerprint.newDocumentId(hash, "one")
        val second = DocumentFingerprint.newDocumentId(hash, "two")
        val unrelated = DocumentFingerprint.newDocumentId("d".repeat(64), "three")
        assertEquals(setOf(first, second), DocumentFingerprint.exactDuplicateIds(listOf(first, second, unrelated)))
        assertNull(DocumentFingerprint.fromDocumentId("legacy-uuid"))
    }
}
