package com.angel.hypergod.data

/** Resolves the same conservative type gate used before an import is committed. */
object ImportTypePolicy {
    fun resolveMime(reportedMime: String, displayName: String): String =
        reportedMime.trim().takeUnless { it.isBlank() || it.equals("application/octet-stream", ignoreCase = true) }
            ?: guessMime(displayName)

    fun isSupported(mime: String): Boolean = mime.equals("application/pdf", ignoreCase = true) || mime.startsWith("image/", ignoreCase = true)

    fun resolvePageMime(pageMime: String, sourceName: String, documentMime: String): String {
        val page = resolveMime(pageMime, sourceName)
        return if (isSupported(page)) page else resolveMime(documentMime, sourceName)
    }

    fun isPdf(pageMime: String, sourceName: String, documentMime: String): Boolean =
        resolvePageMime(pageMime, sourceName, documentMime)
            .equals("application/pdf", ignoreCase = true)

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "application/octet-stream"
    }
}
