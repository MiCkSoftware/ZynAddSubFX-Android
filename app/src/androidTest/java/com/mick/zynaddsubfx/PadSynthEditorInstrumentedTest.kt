package com.mick.zynaddsubfx

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadSynthEditorInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun padNavigationReusesOscillatorAndShowsProfileAndApply() {
        NativeSynthBridge.nativeStopAudio()
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.setPartPadEnabled(0, true))
        for (field in listOf("qualitySize", "qualityOctaves", "qualitySamplesPerOctave"))
            assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/$field", 0.0)))
        composeRule.setContent {
            ZynAddSubFXTheme {
                FullInstrumentEditor(engine, 0, 0, "PAD", emptySet(), 0, {}, {}, {}, {})
            }
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("PAD") and tab).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        composeRule.onNode(hasText("PAD") and tab).performClick()
        for (section in listOf("Spectrum", "Profile", "Quality")) {
            composeRule.onNode(hasText(section) and tab).assertDoesNotExist()
            if (section == "Profile") composeRule.onNodeWithText("PROFILE").assertDoesNotExist()
            else composeRule.onNodeWithText(section.uppercase()).assertExists()
        }
        composeRule.onNodeWithContentDescription("Apply PAD samples").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Pending changes").assertDoesNotExist()
        composeRule.onNodeWithText("Samples up to date").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("PAD resonance").performScrollTo().assertIsDisplayed()
        val previewFile = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa-pad-graph-knobs.png")
        previewFile.outputStream().use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
        composeRule.onNodeWithContentDescription("PAD overtones").performScrollTo().performClick()
        composeRule.onNodeWithText("Overtones").assertIsDisplayed()
        composeRule.onNodeWithText("‹ PAD").performClick()
        composeRule.onNodeWithContentDescription("PAD oscillator").performScrollTo().performClick()
        composeRule.onNodeWithText("PAD oscillator").assertIsDisplayed()
        composeRule.onNodeWithText("‹ PAD").performClick()
        composeRule.onNodeWithContentDescription("PAD harmonic profile").performScrollTo().performClick()
        composeRule.onNodeWithText("Harmonic profile").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Copy Profile").assertIsDisplayed()
        composeRule.onNodeWithText("‹ PAD").performClick()
        composeRule.onNodeWithText("QUALITY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PROFILE").assertDoesNotExist()
        composeRule.onNodeWithText("SPECTRUM").assertExists()
        composeRule.onNodeWithContentDescription("Apply PAD samples").performScrollTo().assertIsDisplayed().performClick()
        try {
            composeRule.waitUntil(10_000) {
                composeRule.onNodeWithContentDescription("Apply PAD samples").fetchSemanticsNode()
                    .config[SemanticsProperties.StateDescription] == "Samples up to date"
            }
        } catch (failure: Throwable) {
            throw AssertionError("PAD status: ${engine.padGenerationStatus(0, 0)}\n${composeRule.onRoot().printToString()}", failure)
        }
        composeRule.onNodeWithContentDescription("Apply PAD samples").assertIsNotEnabled()
        composeRule.onNodeWithText("Pending changes").assertDoesNotExist()
        composeRule.onNodeWithText("Samples up to date").assertDoesNotExist()
    }
}
