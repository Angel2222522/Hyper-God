package com.angel.hypergod.security

/** Local-only strength gate for new portable backups. */
object BackupPasswordPolicy {
    const val MIN_LENGTH = 12

    fun isStrong(password: String): Boolean {
        if (password.length < MIN_LENGTH || password.distinct().size < 6) return false
        val normalized = password.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
        if (normalized in COMMON_PASSWORDS) return false
        val classes = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        ).count { it }
        val passphrase = password.length >= 16 && password.any(Char::isWhitespace)
        return classes >= 3 || passphrase || password.length >= 20
    }

    private val COMMON_PASSWORDS = setOf(
        "password1234",
        "qwerty123456",
        "123456789012",
        "letmein123456",
        "admin12345678",
        "κωδικος123456"
    )
}
