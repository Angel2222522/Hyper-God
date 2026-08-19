package com.angel.hypergod.data

import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRelationshipSuggestionEngineTest {
    @Test
    fun detectsExactDuplicateAndVersionDirectionWithExplainableRules() {
        val hash = "a".repeat(64)
        val older = document(
            id = DocumentFingerprint.newDocumentId(hash, "older"),
            title = "Βεβαίωση ανεργίας",
            provider = "ΔΥΠΑ",
            issuedDate = "2026-01-01"
        )
        val newer = document(
            id = DocumentFingerprint.newDocumentId(hash, "newer"),
            title = "Βεβαίωση ανεργίας",
            provider = "ΔΥΠΑ",
            issuedDate = "2026-08-01"
        )

        val suggestions = DocumentRelationshipSuggestionEngine.evaluate(listOf(older, newer))
        assertTrue(suggestions.any { it.documentId == older.id && it.kind == DocumentSuggestionKind.EXACT_DUPLICATE })
    }

    @Test
    fun sameTitleAndIssuerWithDifferentDatesSuggestsVersionNotFact() {
        val older = document(id = "old", title = "Άδεια διαμονής", provider = "Υπουργείο", issuedDate = "2025-01-01")
        val newer = document(id = "new", title = "Άδεια διαμονής", provider = "Υπουργείο", issuedDate = "2026-01-01")
        val suggestions = DocumentRelationshipSuggestionEngine.evaluate(listOf(older, newer))
        assertTrue(suggestions.any { it.documentId == newer.id && it.otherDocumentId == older.id && it.kind == DocumentSuggestionKind.NEWER_VERSION })
        assertTrue(suggestions.any { it.documentId == older.id && it.otherDocumentId == newer.id && it.kind == DocumentSuggestionKind.OLDER_VERSION })
    }

    private fun document(id: String, title: String, provider: String, issuedDate: String?) = DocumentEntity(
        id = id,
        title = title,
        originalFileName = "$title.pdf",
        mimeType = "application/pdf",
        encryptedPath = "/private/$id",
        pageCount = 1,
        provider = provider,
        issuedDate = issuedDate,
        createdAt = 1,
        updatedAt = 1
    ).toSummary()
}
