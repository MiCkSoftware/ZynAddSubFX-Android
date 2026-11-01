package com.mick.zynaddsubfx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mick.zynaddsubfx.ui.theme.ZynAddSubFXTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonModulesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nativeFineDetuneUsesOriginalCurves() {
        for (type in 1..4) assertEquals(0f, SynthEngine.fineDetuneCents(type, 8192), .0001f)
        assertEquals(-35f, SynthEngine.fineDetuneCents(1, 0), .001f)
        assertEquals(-10f, SynthEngine.fineDetuneCents(2, 0), .001f)
        assertEquals(-99.9f, SynthEngine.fineDetuneCents(3, 0), .001f)
        assertEquals(-1200f, SynthEngine.fineDetuneCents(4, 0), .001f)
        assertEquals(-35f, SynthEngine.fineDetuneCents(0, -100), .001f)
    }

    @Test
    fun nativeEnvelopePreviewAndTypedClipboardRoundTrip() {
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val address = ModuleAddress.Envelope(envelopeRole = EnvelopeRole.AMPLITUDE)
        assertTrue(engine.preview(0, 0, address, 64).values.isNotEmpty())
        val frequency = ModuleAddress.Envelope(0, EnvelopeRole.FREQUENCY)
        val source = "add/voice/0/freqEnvelope/attackTime"
        val target = "add/ampEnvelope/attackTime"
        assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite(source, 51.0)))
        assertTrue(engine.copyModule(0, 0, frequency))
        assertTrue(engine.canPasteModule(address))
        assertTrue(engine.pasteModule(0, 0, address))
        assertEquals(51.0, engine.parameterSnapshot(0, 0).values
            .first { it.descriptor.path == target }.value, 0.01)
        assertFalse(engine.canPasteModule(ModuleAddress.Lfo(0, LfoRole.FREQUENCY)))
    }

    @Test
    fun modulatorEnvelopeParametersRoundTripThroughNativePaths() {
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        listOf("modAmpEnvelope", "modFreqEnvelope").forEach { envelope ->
            listOf("attackTime" to 41.0, "releaseTime" to 78.0, "attackValue" to 52.0).forEach { (field, value) ->
                val path = "add/voice/0/$envelope/$field"
                assertTrue(path, engine.writeParameter(0, 0, SynthEngine.ParameterWrite(path, value)))
                assertEquals(path, value, engine.parameterSnapshot(0, 0).values
                    .first { it.descriptor.path == path }.value, 0.01)
            }
        }
    }

    @Test
    fun voiceAmplitudeClipboardCopiesOnlyDirectParameters() {
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        fun write(voice: Int, field: String, value: Double) = assertTrue(
            engine.writeParameter(0, 0, SynthEngine.ParameterWrite("add/voice/$voice/$field", value))
        )
        fun read(voice: Int, field: String) = engine.parameterSnapshot(0, 0).values
            .first { it.descriptor.path == "add/voice/$voice/$field" }.value
        val source = ModuleAddress.Amplitude(0)
        val target = ModuleAddress.Amplitude(1)
        listOf("volumeMinus" to 1.0, "volume" to 91.0, "velocity" to 45.0, "panning" to 72.0)
            .forEach { (field, value) -> write(0, field, value) }
        write(1, "ampEnvelope/attackTime", 37.0)
        write(1, "ampLfo/depth", 43.0)
        write(1, "detune", 8000.0)
        assertTrue(engine.copyModule(0, 0, source))
        assertTrue(engine.canPasteModule(target))
        assertFalse(engine.canPasteModule(ModuleAddress.Voice(1)))
        assertFalse(engine.canPasteModule(ModuleAddress.Envelope(1, EnvelopeRole.AMPLITUDE)))
        assertTrue(engine.pasteModule(0, 0, target))
        listOf("volumeMinus" to 1.0, "volume" to 91.0, "velocity" to 45.0, "panning" to 72.0,
            "ampEnvelope/attackTime" to 37.0, "ampLfo/depth" to 43.0, "detune" to 8000.0)
            .forEach { (field, value) -> assertEquals(field, value, read(1, field), 0.01) }
    }

    @Test
    fun nativeSnapshotExposesStructuredAddRouting() {
        val engine = SynthEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))

        val values = engine.parameterSnapshot(0, 0).values
        val globalEnvelope = values.first { it.descriptor.path == "add/ampEnvelope/attackTime" }
        val voiceEnvelope = values.first { it.descriptor.path == "add/voice/5/ampEnvelope/attackTime" }

        assertEquals(SynthEngine.ParameterModule.ADD, globalEnvelope.descriptor.module)
        assertEquals(SynthEngine.ParameterScope.GLOBAL, globalEnvelope.descriptor.scope)
        assertEquals(SynthEngine.ParameterSection.AMPLITUDE, globalEnvelope.descriptor.section)
        assertEquals(SynthEngine.ParameterFamily.ENVELOPE, globalEnvelope.descriptor.family)
        assertEquals(SynthEngine.ParameterScope.VOICE, voiceEnvelope.descriptor.scope)
        assertEquals(5, voiceEnvelope.descriptor.ownerIndex)
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
        composeRule.onNodeWithContentDescription("Copy Amplitude envelope").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Paste Amplitude envelope").assertIsDisplayed()
    }
}
