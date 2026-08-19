package com.angel.hypergod.data

data class ProfileDiscrepancy(
    val fieldLabel: String,
    val expectedMasked: String,
    val observedMasked: String,
    val reason: String
)

/** Reports only labelled, deterministic identifier conflicts. */
object ProfileDiscrepancyEngine {
    private val taxPattern = Regex("(?iu)\\b(?:Α\\.?Φ\\.?Μ\\.?|AFM|TIN)\\s*[:#-]?\\s*(\\d{8,12})\\b")
    private val socialPattern = Regex("(?iu)\\b(?:Α\\.?Μ\\.?Κ\\.?Α\\.?|AMKA)\\s*[:#-]?\\s*(\\d{10,13})\\b")

    fun evaluate(profile: PersonalProfile, document: DocumentEntity): List<ProfileDiscrepancy> = buildList {
        compareIdentifier("ΑΦΜ", profile.taxNumber, taxPattern.find(document.ocrText)?.groupValues?.getOrNull(1))?.let(::add)
        compareIdentifier("ΑΜΚΑ", profile.socialSecurityNumber, socialPattern.find(document.ocrText)?.groupValues?.getOrNull(1))?.let(::add)
    }

    private fun compareIdentifier(label: String, expectedRaw: String, observedRaw: String?): ProfileDiscrepancy? {
        val expected = expectedRaw.filter(Char::isDigit)
        val observed = observedRaw.orEmpty().filter(Char::isDigit)
        if (expected.isBlank() || observed.isBlank() || expected == observed) return null
        return ProfileDiscrepancy(
            fieldLabel = label,
            expectedMasked = mask(expected),
            observedMasked = mask(observed),
            reason = "Το έγγραφο περιέχει διαφορετικό, ρητά επισημασμένο $label από το προσωπικό μητρώο."
        )
    }

    private fun mask(value: String): String = "•".repeat((value.length - 4).coerceAtLeast(2)) + value.takeLast(4)
}
