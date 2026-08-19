package com.angel.hypergod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSelectionPolicyTest {
    @Test
    fun filteredViewCannotExportHiddenSelection() {
        assertEquals(setOf("visible"), DocumentSelectionPolicy.retainVisible(setOf("visible", "hidden"), listOf("visible")))
    }
}
