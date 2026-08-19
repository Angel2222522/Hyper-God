package com.angel.hypergod.data

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class GroundedSource(
    val documentId: String? = null,
    val caseId: String? = null,
    val label: String,
    val excerpt: String
)

data class GroundedAnswer(
    val text: String,
    val sources: List<GroundedSource>,
    val hasEvidence: Boolean
)

/** Retrieval and synthesis without a generative model or network dependency. */
object GroundedAnswerEngine {
    fun answer(
        question: String,
        documents: List<DocumentEntity>,
        cases: List<CaseEntity>,
        today: LocalDate = LocalDate.now()
    ): GroundedAnswer {
        val normalizedQuestion = normalize(question)
        val tokens = tokens(normalizedQuestion)
        if (tokens.isEmpty()) return unknown()

        if (tokens.any { it in setOf("ληγει", "ληξη", "προθεσμια", "deadline") }) {
            val expiring = documents.mapNotNull { document ->
                val date = document.expiryDate?.toDateOrNull()
                if (date != null && MetadataConfidence.isConfirmed(document.expiryDate, document.expiryDateConfidence, document.expiryDateManuallyEdited)) {
                    document to date
                } else null
            }.sortedBy { it.second }.take(8)
            if (expiring.isEmpty()) return unknown("Δεν βρέθηκε επιβεβαιωμένη ημερομηνία λήξης στα έγγραφά σου.")
            val lines = expiring.joinToString("\n") { (document, date) ->
                val relative = when {
                    date.isBefore(today) -> "έχει λήξει"
                    date == today -> "λήγει σήμερα"
                    else -> "λήγει σε ${java.time.temporal.ChronoUnit.DAYS.between(today, date)} ημέρες"
                }
                "• ${document.title}: $date — $relative"
            }
            return GroundedAnswer(
                text = "Βρήκα τις εξής επιβεβαιωμένες λήξεις:\n$lines",
                sources = expiring.map { (document, date) -> GroundedSource(documentId = document.id, label = document.title, excerpt = "Λήξη: $date") },
                hasEvidence = true
            )
        }

        if (tokens.any { it in setOf("εκκρεμει", "εκκρεμοτητες", "κανω", "επομενο") }) {
            val active = cases.filter { it.status !in setOf(CaseStatus.COMPLETED, CaseStatus.ARCHIVED, CaseStatus.APPROVED, CaseStatus.REJECTED) }
                .sortedWith(compareBy<CaseEntity> { it.deadline ?: "9999-12-31" }.thenByDescending { it.updatedAt })
                .take(8)
            if (active.isEmpty()) return unknown("Δεν βρέθηκε ενεργή υπόθεση που να απαιτεί ενέργεια.")
            val lines = active.joinToString("\n") { caseEntity ->
                val action = caseEntity.nextStep.ifBlank { caseEntity.status }
                "• ${caseEntity.title}: $action${caseEntity.deadline?.let { " — προθεσμία $it" }.orEmpty()}"
            }
            return GroundedAnswer(
                text = "Οι ενεργές εκκρεμότητες που προκύπτουν από τα αποθηκευμένα στοιχεία είναι:\n$lines",
                sources = active.map { GroundedSource(caseId = it.id, label = it.title, excerpt = it.nextStep.ifBlank { it.status }) },
                hasEvidence = true
            )
        }

        val ranked = documents.mapNotNull { document ->
            val searchable = normalize(listOf(document.title, document.originalFileName, document.provider, document.category, document.tags, document.protocolNumber.orEmpty(), document.ocrText).joinToString(" "))
            val score = tokens.sumOf { token ->
                when {
                    token.length < 2 -> 0
                    normalize(document.title).contains(token) -> 6
                    normalize(document.protocolNumber.orEmpty()).contains(token) -> 6
                    normalize(document.provider).contains(token) -> 4
                    searchable.contains(token) -> 1
                    else -> 0
                }
            }
            if (score > 0) Triple(document, score, evidenceExcerpt(document, tokens)) else null
        }.sortedByDescending { it.second }.take(5)

        if (ranked.isEmpty()) return unknown()
        val summary = ranked.joinToString("\n") { (document, _, excerpt) -> "• ${document.title}: $excerpt" }
        return GroundedAnswer(
            text = "Βρήκα σχετικά στοιχεία μόνο μέσα στα αποθηκευμένα έγγραφά σου:\n$summary",
            sources = ranked.map { (document, _, excerpt) -> GroundedSource(documentId = document.id, label = document.title, excerpt = excerpt) },
            hasEvidence = true
        )
    }

    private fun evidenceExcerpt(document: DocumentEntity, queryTokens: Set<String>): String {
        val metadata = buildList {
            document.provider.takeIf { it.isNotBlank() }?.let { add("Φορέας: $it") }
            document.protocolNumber?.let { add("Πρωτόκολλο: $it") }
            document.issuedDate?.let { add("Έκδοση: $it") }
            document.expiryDate?.let { add("Λήξη: $it") }
        }
        if (metadata.isNotEmpty()) return metadata.joinToString(" · ")
        val text = document.ocrText.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return "Σχετικός τίτλος ή κατηγορία, χωρίς διαθέσιμο OCR."
        val normalized = normalize(text)
        val firstToken = queryTokens.firstOrNull { normalized.contains(it) }
        val start = firstToken?.let { normalized.indexOf(it).coerceAtLeast(0) } ?: 0
        val safeStart = (start - 50).coerceAtLeast(0).coerceAtMost(text.length)
        return text.substring(safeStart, (safeStart + 220).coerceAtMost(text.length)).trim()
    }

    private fun unknown(text: String = "Δεν βρέθηκαν αρκετές πληροφορίες στα αποθηκευμένα έγγραφα και στις υποθέσεις σου.") =
        GroundedAnswer(text, emptyList(), false)

    private fun tokens(value: String): Set<String> = value.split(' ')
        .asSequence()
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el"))
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private val STOP_WORDS = setOf("και", "το", "τα", "τη", "την", "των", "σε", "με", "μου", "για", "απο", "που", "ποιο", "ποια", "τι")
}
