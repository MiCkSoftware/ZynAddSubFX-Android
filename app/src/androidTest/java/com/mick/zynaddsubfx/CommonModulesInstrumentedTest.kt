package com.mick.zynaddsubfx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonModulesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nativeEnvelopePreviewAndTypedClipboardRoundTrip() {
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val address = ModuleAddress.Envelope(envelopeRole = EnvelopeRole.AMPLITUDE)
        assertTrue(engine.preview(0, 0, address, 64).values.isNotEmpty())
        assertTrue(engine.copyModule(0, 0, address))
        assertTrue(engine.canPasteModule(address))
        assertFalse(engine.canPasteModule(ModuleAddress.Envelope(envelopeRole = EnvelopeRole.FREQUENCY)))
        assertTrue(engine.pasteModule(0, 0, address))
    }

    @Test
    fun envelopeComponentExposesPreviewAndClipboardActions() {
        val prefix = "add/ampEnvelope"
        val descriptor = SynthEngine.ParameterDescriptor(
            path = "$prefix/attackTime",
            label = "Attack duration",
            group = "ADD / Amplitude / Envelope",
            type = SynthEngine.ParameterType.INTEGER,
            minimum = 0.0,
            maximum = 127.0,
            defaultValue = 0.0,
            options = emptyList(),
            preferredControl = SynthEngine.PreferredControl.KNOB,
        )
        val model = EnvelopeModel(
            ModuleAddress.Envelope(envelopeRole = EnvelopeRole.AMPLITUDE),
            prefix,
            "Amplitude envelope",
            listOf(SynthEngine.ParameterValue(descriptor, 32.0)),
            false,
            false,
            null,
            emptyList(),
        )
        composeRule.setContent {
            ZynAddSubFXTheme {
                EnvelopeUI(model, PreviewSeries(listOf(-1f, 1f, 0f)), { _, _ -> }, {}, {}, {}, false)
            }
        }
        composeRule.onNodeWithText("AMPLITUDE ENVELOPE").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Paste").assertIsDisplayed()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
    }
}
