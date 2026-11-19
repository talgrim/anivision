package com.anivision.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the wording the About screen must keep: the output is an approximation, not what a dog sees. */
class AboutStringsTest {
    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String {
        val match = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(strings)
        return requireNotNull(match) { "string $name is missing from strings.xml" }.groupValues[1]
    }

    @Test
    fun theDisclaimerSaysTheOutputIsAnApproximation() {
        assertTrue(string("about_disclaimer_title").contains("approximation", ignoreCase = true))
        val body = string("about_disclaimer")
        assertTrue(body.contains("approximation", ignoreCase = true))
        assertTrue(body.contains("cannot show what a dog actually sees"))
    }

    @Test
    fun everyAboutSectionHasATitleAndABody() {
        for (section in listOf("colour", "blur", "night", "cat", "privacy")) {
            assertTrue("about_${section}_title is empty", string("about_${section}_title").isNotBlank())
            assertTrue("about_$section is empty", string("about_$section").isNotBlank())
        }
    }

    @Test
    fun nightBoostIsNotPresentedAsDogOrCatNightVision() {
        assertTrue(string("about_night").contains("not a model", ignoreCase = true))
    }

    @Test
    fun theCatSectionSaysTheCatAndDogViewsAreSimilarAndTheExtrasAreNotMeasurements() {
        val body = string("about_cat")
        assertTrue(body.contains("very similar"))
        assertTrue(body.contains("not measurements"))
    }
}
