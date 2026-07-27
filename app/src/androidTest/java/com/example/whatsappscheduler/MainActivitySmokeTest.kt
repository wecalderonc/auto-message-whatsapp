package com.example.whatsappscheduler

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsRiskOrTitle() {
        // First launch shows the risk dialog; either surface proves Compose started.
        val risk = composeRule.onNodeWithText("Personal / sideload only")
        val title = composeRule.onNodeWithText("WhatsApp Scheduler")
        try {
            risk.assertIsDisplayed()
        } catch (_: AssertionError) {
            title.assertIsDisplayed()
        }
    }
}
