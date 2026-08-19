package com.angel.hypergod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDiscrepancyEngineTest {
    @Test
    fun reportsLabelledMismatchWithoutChangingProfile() {
        val profile = PersonalProfile(taxNumber = "123456789")
        val document = document("ΑΦΜ: 987654321")
        val result = ProfileDiscrepancyEngine.evaluate(profile, document)
        assertEquals(1, result.size)
        assertEquals("ΑΦΜ", result.single().fieldLabel)
        assertEquals("123456789", profile.taxNumber)
    }

    @Test
    fun ignoresUnlabelledNumbersAndMatches() {
        val profile = PersonalProfile(socialSecurityNumber = "12345678901")
        assertTrue(ProfileDiscrepancyEngine.evaluate(profile, document("12345678901")).isEmpty())
        assertTrue(ProfileDiscrepancyEngine.evaluate(profile, document("ΑΜΚΑ 12345678901")).isEmpty())
    }

    private fun document(text: String) = DocumentEntity(
        id = "id",
        title = "Έγγραφο",
        originalFileName = "document.pdf",
        mimeType = "application/pdf",
        encryptedPath = "/private/id",
        pageCount = 1,
        ocrText = text,
        createdAt = 1,
        updatedAt = 1
    )
}
