package com.angel.hypergod.data

/** Cheap preflight that bounds recursive JSON work before JSONObject builds a DOM. */
object JsonStructurePolicy {
    const val MAX_DEPTH = 32
    const val MAX_STRUCTURAL_TOKENS = 1_000_000
    const val MAX_STRING_CHARS = 2_100_000

    fun validate(value: String) {
        var inString = false
        var escaped = false
        var stringChars = 0
        var tokenCount = 0
        val stack = mutableListOf<Char>()

        value.forEach { character ->
            if (inString) {
                if (escaped) {
                    escaped = false
                    stringChars += 1
                } else when (character) {
                    '\\' -> escaped = true
                    '"' -> {
                        inString = false
                        require(stringChars <= MAX_STRING_CHARS) { "Το αντίγραφο περιέχει υπερβολικά μεγάλο πεδίο κειμένου." }
                        stringChars = 0
                    }
                    else -> {
                        require(character.code >= 0x20) { "Το αντίγραφο περιέχει μη έγκυρο χαρακτήρα JSON." }
                        stringChars += 1
                    }
                }
                require(stringChars <= MAX_STRING_CHARS) { "Το αντίγραφο περιέχει υπερβολικά μεγάλο πεδίο κειμένου." }
                return@forEach
            }

            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    stack.add(character)
                    tokenCount += 1
                    require(stack.size <= MAX_DEPTH) { "Το αντίγραφο περιέχει υπερβολικά βαθιά δομή JSON." }
                }
                '}' -> {
                    val opener = if (stack.isEmpty()) null else stack.removeAt(stack.lastIndex)
                    require(opener == '{') { "Το αντίγραφο περιέχει μη έγκυρη δομή JSON." }
                    tokenCount += 1
                }
                ']' -> {
                    val opener = if (stack.isEmpty()) null else stack.removeAt(stack.lastIndex)
                    require(opener == '[') { "Το αντίγραφο περιέχει μη έγκυρη δομή JSON." }
                    tokenCount += 1
                }
                ',', ':' -> tokenCount += 1
            }
            require(tokenCount <= MAX_STRUCTURAL_TOKENS) { "Το αντίγραφο περιέχει υπερβολικά σύνθετο JSON." }
        }
        require(!inString && !escaped && stack.isEmpty()) { "Το αντίγραφο περιέχει ατελή δομή JSON." }
    }
}
