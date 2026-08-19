package com.angel.hypergod.data

import java.security.MessageDigest

/**
 * Stable content identity used only for deterministic duplicate suggestions.
 * Each imported document still receives a unique ID, so nothing is merged or
 * deleted automatically.
 */
object DocumentFingerprint {
    private const val HASH_LENGTH = 64

    fun combineOrderedSourceHashes(sourceHashes: List<String>): String {
        require(sourceHashes.isNotEmpty()) { "Απαιτείται τουλάχιστον μία πηγή." }
        val digest = MessageDigest.getInstance("SHA-256")
        sourceHashes.forEachIndexed { index, hash ->
            require(hash.length == HASH_LENGTH && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Μη έγκυρο αποτύπωμα πηγής."
            }
            digest.update(index.toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(hash.toByteArray(Charsets.US_ASCII))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun newDocumentId(contentHash: String, uniqueSuffix: String): String {
        require(contentHash.length == HASH_LENGTH)
        val safeSuffix = uniqueSuffix.filter { it.isLetterOrDigit() || it == '-' }
        require(safeSuffix.isNotBlank())
        return "${contentHash}_$safeSuffix"
    }

    fun fromDocumentId(id: String): String? {
        if (id.length <= HASH_LENGTH || id[HASH_LENGTH] != '_') return null
        val candidate = id.take(HASH_LENGTH)
        return candidate.takeIf { it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
    }

    fun exactDuplicateIds(ids: Collection<String>): Set<String> = ids
        .mapNotNull { id -> fromDocumentId(id)?.let { it to id } }
        .groupBy({ it.first }, { it.second })
        .values
        .asSequence()
        .filter { it.size > 1 }
        .flatten()
        .toSet()
}
