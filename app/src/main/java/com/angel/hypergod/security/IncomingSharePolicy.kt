package com.angel.hypergod.security

/** Pure validation boundary for values supplied to the exported share activity. */
object IncomingSharePolicy {
    const val MAX_URIS = 20
    const val MAX_SERIALIZED_URI_LENGTH = 2_048

    fun rejectionReason(candidates: List<Candidate>): String? = when {
        candidates.isEmpty() -> "Δεν βρέθηκε αρχείο για εισαγωγή."
        candidates.size > MAX_URIS -> "Η εισαγωγή από άλλη εφαρμογή υποστηρίζει έως $MAX_URIS αρχεία κάθε φορά."
        candidates.any { it.scheme != "content" } -> "Η άλλη εφαρμογή έστειλε μη ασφαλή τύπο συνδέσμου."
        candidates.any { it.serializedLength !in 1..MAX_SERIALIZED_URI_LENGTH } -> "Η άλλη εφαρμογή έστειλε υπερβολικά μεγάλο σύνδεσμο αρχείου."
        else -> null
    }

    data class Candidate(val scheme: String?, val serializedLength: Int)
}
