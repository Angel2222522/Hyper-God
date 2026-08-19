package com.angel.hypergod.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.angel.hypergod.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainDestinationsRenderWithGreekCopy() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("Η διοικητική σου μνήμη")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Έγγραφα").performClick()
        composeRule.onNodeWithText("Αναζήτηση σε τίτλους, OCR και στοιχεία").assertExists()

        composeRule.onNodeWithText("Ρώτα").performClick()
        composeRule.onNodeWithText("Ρώτα τον φάκελό σου").assertExists()

        composeRule.onNodeWithText("Υποθέσεις").performClick()
        composeRule.onNodeWithText("Νέα υπόθεση").assertExists()

        composeRule.onNodeWithText("Ρυθμίσεις").performClick()
        composeRule.onNodeWithText("Προσωπικό μητρώο").assertExists()
    }
}
