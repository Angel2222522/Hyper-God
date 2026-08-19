package com.angel.hypergod.data

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundedAnswerEngineTest {
    @Test
    fun expiryAnswerUsesOnlyConfirmedDatesAndReturnsSource() {
        val confirmed = document("confirmed", "Βεβαίωση", expiry = "2026-09-01", confidence = MetadataConfidence.HIGH)
        val suggestion = document("suggested", "Πρόταση", expiry = "2026-08-21", confidence = MetadataConfidence.LOW)
        val answer = GroundedAnswerEngine.answer(
            "Τι λήγει σύντομα;",
            listOf(confirmed, suggestion),
            emptyList(),
            LocalDate.of(2026, 8, 19)
        )
        assertTrue(answer.hasEvidence)
        assertTrue(answer.text.contains("Βεβαίωση"))
        assertFalse(answer.text.contains("Πρόταση"))
        assertTrue(answer.sources.single().documentId == "confirmed")
    }

    @Test
    fun unknownQuestionDoesNotFabricateAnswer() {
        val answer = GroundedAnswerEngine.answer("Πού είναι το διαβατήριο;", emptyList(), emptyList())
        assertFalse(answer.hasEvidence)
        assertTrue(answer.sources.isEmpty())
    }

    @Test
    fun pendingCaseAnswerIsGroundedInCaseState() {
        val caseEntity = CaseEntity(
            id = "case",
            title = "Ανανέωση άδειας",
            status = CaseStatus.ACTION,
            nextStep = "Κατάθεση φωτογραφίας",
            createdAt = 1,
            updatedAt = 1
        )
        val answer = GroundedAnswerEngine.answer("Τι εκκρεμεί;", emptyList(), listOf(caseEntity))
        assertTrue(answer.hasEvidence)
        assertTrue(answer.text.contains("Κατάθεση φωτογραφίας"))
        assertTrue(answer.sources.single().caseId == "case")
    }

    @Test
    fun genericRetrievalRanksDocumentAndReturnsItsEvidence() {
        val protocolDocument = document("protocol", "Απόφαση επιδόματος", expiry = null, confidence = MetadataConfidence.NONE)
            .copy(provider = "Δήμος Αθηναίων", protocolNumber = "ΑΠ-42")
        val unrelated = document("other", "Λογαριασμός ρεύματος", expiry = null, confidence = MetadataConfidence.NONE)

        val answer = GroundedAnswerEngine.answer(
            "Ποια απόφαση έχει πρωτόκολλο ΑΠ-42;",
            listOf(unrelated, protocolDocument),
            emptyList()
        )

        assertTrue(answer.hasEvidence)
        assertTrue(answer.sources.first().documentId == "protocol")
        assertTrue(answer.sources.first().excerpt.contains("ΑΠ-42"))
    }

    private fun document(id: String, title: String, expiry: String?, confidence: String) = DocumentEntity(
        id = id,
        title = title,
        originalFileName = "$title.pdf",
        mimeType = "application/pdf",
        encryptedPath = "/private/$id",
        pageCount = 1,
        expiryDate = expiry,
        expiryDateConfidence = confidence,
        createdAt = 1,
        updatedAt = 1
    )
}
