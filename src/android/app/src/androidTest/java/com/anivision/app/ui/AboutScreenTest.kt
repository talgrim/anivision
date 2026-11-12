package com.anivision.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anivision.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun showsTheApproximationDisclaimerAndTheVersion() {
        compose.setContent { AboutScreen(versionName = "1.2.3", onClose = {}) }

        compose.onNodeWithText(resources.getString(R.string.about_disclaimer_title)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.about_disclaimer)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.about_version, "1.2.3")).assertIsDisplayed()
    }

    @Test
    fun theCloseButtonCallsOnClose() {
        var closed = 0
        compose.setContent { AboutScreen(versionName = "1.0", onClose = { closed++ }) }

        compose.onNodeWithText(resources.getString(R.string.close)).performClick()

        assertEquals(1, closed)
    }

    @Test
    fun theAboutButtonCallsItsCallback() {
        var clicks = 0
        compose.setContent { AboutButton(onClick = { clicks++ }) }

        compose.onNodeWithText(resources.getString(R.string.about)).performClick()

        assertEquals(1, clicks)
    }
}
