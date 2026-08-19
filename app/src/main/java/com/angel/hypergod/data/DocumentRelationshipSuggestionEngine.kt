package com.angel.hypergod.data

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class DocumentSuggestionKind {
    EXACT_DUPLICATE,
    SAME_PROTOCOL,
    NEWER_VERSION,
    OLDER_VERSION,
    RELATED
}

data class DocumentRelationshipSuggestion(
    val documentId: String,
    val otherDocumentId: String,
    val kind: DocumentSuggestionKind,
    val reason: String,
    val confidence: String
)

/** Conservative, explainable relationship hints. Nothing is linked silently. */
object DocumentRelationshipSuggestionEngine {
    fun evaluate(documents: List<DocumentSummary>): List<DocumentRelationshipSuggestion> {
        if (documents.size < 2) return emptyList()
        val output = linkedSetOf<DocumentRelationshipSuggestion>()

        val exactGroups = documents.mapNotNull { document ->
            DocumentFingerprint.fromDocumentId(document.id)?.let { it to document }
        }.groupBy({ it.first }, { it.second })
        exactGroups.values.filter { it.size > 1 }.forEach { group ->
            addPairwise(group, output, DocumentSuggestionKind.EXACT_DUPLICATE, "Ίδιο κρυπτογραφικά επαληθευμένο περιεχόμενο", MetadataConfidence.HIGH)
            if (output.size >= MAX_SUGGESTIONS) return output.toList()
        }

        documents.filter { !it.protocolNumber.isNullOrBlank() }
            .groupBy { normalize(it.protocolNumber.orEmpty()) }
            .values
            .filter { group -> group.size > 1 && group.first().protocolNumber.orEmpty().length >= 4 }
            .forEach { group ->
                addPairwise(group, output, DocumentSuggestionKind.SAME_PROTOCOL, "Ίδιος αριθμός πρωτοκόλλου", MetadataConfidence.HIGH)
                if (output.size >= MAX_SUGGESTIONS) return output.toList()
            }

        documents.groupBy { document ->
            val title = normalize(document.title)
            val provider = normalize(document.provider)
            if (title.length >= 6 && provider.length >= 3) "$title|$provider" else ""
        }.filterKeys { it.isNotBlank() }.values.filter { it.size > 1 }.forEach { group ->
            for (left in group) {
                for (right in group) {
                    if (left.id == right.id) continue
                    if (output.any { it.documentId == left.id && it.otherDocumentId == right.id }) continue
                    val leftDate = left.issuedDate.toLocalDateOrNull()
                    val rightDate = right.issuedDate.toLocalDateOrNull()
                    val kind = when {
                        leftDate != null && rightDate != null && leftDate.isAfter(rightDate) -> DocumentSuggestionKind.NEWER_VERSION
                        leftDate != null && rightDate != null && leftDate.isBefore(rightDate) -> DocumentSuggestionKind.OLDER_VERSION
                        else -> DocumentSuggestionKind.RELATED
                    }
                    val reason = when (kind) {
                        DocumentSuggestionKind.NEWER_VERSION -> "Ίδιος τίτλος και φορέας, με νεότερη ημερομηνία έκδοσης"
                        DocumentSuggestionKind.OLDER_VERSION -> "Ίδιος τίτλος και φορέας, με παλαιότερη ημερομηνία έκδοσης"
                        else -> "Ίδιος τίτλος και φορέας"
                    }
                    output += DocumentRelationshipSuggestion(left.id, right.id, kind, reason, MetadataConfidence.MEDIUM)
                    if (output.size >= MAX_SUGGESTIONS) return output.toList()
                }
            }
        }

        return output.toList()
    }

    private fun addPairwise(
        group: List<DocumentSummary>,
        output: MutableSet<DocumentRelationshipSuggestion>,
        kind: DocumentSuggestionKind,
        reason: String,
        confidence: String
    ) {
        for (left in group) {
            for (right in group) {
                if (left.id == right.id) continue
                output += DocumentRelationshipSuggestion(left.id, right.id, kind, reason, confidence)
                if (output.size >= MAX_SUGGESTIONS) return
            }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("el"))
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    internal const val MAX_SUGGESTIONS = 200
}
