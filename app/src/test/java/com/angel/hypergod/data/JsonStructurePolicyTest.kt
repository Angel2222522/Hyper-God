package com.angel.hypergod.data

import org.junit.Assert.assertThrows
import org.junit.Test

class JsonStructurePolicyTest {
    @Test
    fun acceptsNormalManifestShape() {
        JsonStructurePolicy.validate("""{"formatVersion":4,"documents":[],"pages":[]}""")
    }

    @Test
    fun rejectsExcessiveNestingBeforeDomParsing() {
        val nested = "[".repeat(JsonStructurePolicy.MAX_DEPTH + 1) + "]".repeat(JsonStructurePolicy.MAX_DEPTH + 1)
        assertThrows(IllegalArgumentException::class.java) { JsonStructurePolicy.validate(nested) }
    }

    @Test
    fun rejectsOversizedString() {
        val value = "{\"x\":\"" + "a".repeat(JsonStructurePolicy.MAX_STRING_CHARS + 1) + "\"}"
        assertThrows(IllegalArgumentException::class.java) { JsonStructurePolicy.validate(value) }
    }
}
