package com.mick.zynaddsubfx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native state checks only: these do not certify audible parity or touch performance. */
@RunWith(AndroidJUnit4::class)
class AddSynthRoundTripInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun values(engine: SynthEngine, part: Int, kit: Int = 0, prefix: String = "add/") =
        engine.parameterSnapshot(part, kit).values
            .filter { it.descriptor.path.startsWith(prefix) }
            .associate { it.descriptor.path.removePrefix(prefix) to it.value }

    private fun assertValues(label: String, expected: Map<String, Double>, actual: Map<String, Double>) {
        val differences = (expected.keys + actual.keys).filter { expected[it] != actual[it] }
        assertTrue("$label: " + differences.take(20).joinToString { "$it: ${expected[it]} -> ${actual[it]}" }, differences.isEmpty())
    }

    @Test
    fun packagedXizRoundTripsAllExposedAddValuesWithoutChangingOtherPart() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val presets = listOf(
            "presets/banks/Strings/0067-Dual Strings Oct1.xiz",
            "presets/banks/Companion/0078-Brassy.xiz",
            "presets/banks/Choir and Voice/0065-Vocal Morph 1.xiz",
        )
        val file = File(context.cacheDir, "qa-add-roundtrip.xiz")
        try {
            for (preset in presets) {
                assertTrue(preset, engine.loadPackagedPreset(preset, 0))
                val before = values(engine, 0)
                assertTrue(preset, before.isNotEmpty())
                assertTrue(preset, engine.exportInstrument(0, file))
                assertTrue(preset, NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
                assertEquals("$preset source isolation", before, values(engine, 0))
                assertValues("$preset reloaded ADD", before, values(engine, 1))
                assertEquals("$preset kit activation", values(engine, 0, prefix = "kit/"),
                    values(engine, 1, prefix = "kit/"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun clipboardModulesCrossPartsPreserveSourceAndRejectOtherKinds() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.loadPackagedPreset("presets/banks/Strings/0067-Dual Strings Oct1.xiz", 0))
        assertTrue(engine.loadPackagedPreset("presets/banks/Companion/0078-Brassy.xiz", 1))
        assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("add/filter/category", 1.0)))
        val markers = listOf(
            "add/voice/0/osc/harmonic/2/magnitude" to 43.0,
            "add/voice/0/modOsc/harmonic/2/magnitude" to 39.0,
            "add/filter/formant/vowel/0/0/frequency" to 87.0,
            "add/resonance/point/17" to 93.0,
        ) + listOf("ampEnvelope", "freqEnvelope", "filterEnvelope", "modAmpEnvelope", "modFreqEnvelope")
            .map { "add/voice/0/$it/attackTime" to 41.0 } +
            listOf("ampLfo", "freqLfo", "filterLfo").map { "add/voice/0/$it/depth" to 47.0 }
        for ((path, value) in markers) {
            assertTrue(path, engine.writeParameter(0, 0, SynthEngine.ParameterWrite(path, value)))
        }
        val cases = buildList<Pair<ModuleAddress, String>> {
            add(ModuleAddress.Voice(0) to "add/voice/0/")
            add(ModuleAddress.Oscillator(0) to "add/voice/0/osc/")
            add(ModuleAddress.Oscillator(0, true) to "add/voice/0/modOsc/")
            EnvelopeRole.entries.forEach {
                val section = listOf("ampEnvelope", "freqEnvelope", "filterEnvelope", "modAmpEnvelope", "modFreqEnvelope")[it.code]
                add(ModuleAddress.Envelope(0, it) to "add/voice/0/$section/")
            }
            LfoRole.entries.forEach {
                val section = listOf("ampLfo", "freqLfo", "filterLfo")[it.code]
                add(ModuleAddress.Lfo(0, it) to "add/voice/0/$section/")
            }
            add(ModuleAddress.Filter() to "add/filter/")
            add(ModuleAddress.Resonance to "add/resonance/")
            add(ModuleAddress.Vowel(vowel = 0) to "add/filter/formant/vowel/0/")
        }
        for ((address, prefix) in cases) {
            assertTrue(engine.loadPackagedPreset("presets/banks/Companion/0078-Brassy.xiz", 1))
            assertTrue(engine.writeParameter(1, 0, SynthEngine.ParameterWrite("add/filter/category", 1.0)))
            val source = values(engine, 0, prefix = prefix)
            assertTrue("Missing snapshot $prefix", source.isNotEmpty())
            assertNotEquals("Fixture must distinguish source and target $address", source, values(engine, 1, prefix = prefix))
            val targetBefore = values(engine, 1)
            assertTrue("Copy $address", engine.copyModule(0, 0, address))
            val incompatible = if (address is ModuleAddress.Lfo) ModuleAddress.Resonance
                else ModuleAddress.Lfo(lfoRole = LfoRole.AMPLITUDE)
            assertFalse("Compatibility $address", engine.canPasteModule(incompatible))
            assertFalse("Reject $address", engine.pasteModule(1, 0, incompatible))
            assertEquals("Rejected paste mutated target", targetBefore, values(engine, 1))
            assertTrue("Paste $address", engine.pasteModule(1, 0, address))
            assertEquals("Source isolation $address", source, values(engine, 0, prefix = prefix))
            assertValues("Destination $address", source, values(engine, 1, prefix = prefix))
        }
    }

    @Test
    fun repeatedSparsePresetLoadDoesNotInheritEarlierAddEdits() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val preset = "presets/banks/Companion/0078-Brassy.xiz"
        assertTrue(engine.loadPackagedPreset(preset, 0))
        val initial = values(engine, 0)
        assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("add/voice/0/modOsc/harmonic/2/magnitude", 39.0)))
        assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("add/voice/7/freqLfo/depth", 47.0)))
        assertTrue(engine.loadPackagedPreset(preset, 0))
        assertValues("Sparse preset reload", initial, values(engine, 0))
    }

    @Test
    fun advancedEditsAndCrossKitClipboardSurviveXizReload() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.loadPackagedPreset("presets/banks/Strings/0067-Dual Strings Oct1.xiz", 0))
        val edits = listOf(
            "part/kitMode" to 1.0, "kit/enabled" to 1.0, "kit/addEnabled" to 1.0,
            "kit/padEnabled" to 1.0, "kit/muted" to 1.0,
            "add/fadeIn" to 31.0, "add/voice/0/bend" to -31.0,
            "add/voice/0/offsetHz" to 23.0, "add/voice/0/phaseRandom" to 19.0,
            "add/voice/0/enabled" to 0.0,
            "add/voice/0/modFreqEnvelope/freeMode" to 1.0,
            "add/voice/0/modFreqEnvelope/point/1/value" to 73.0,
            "add/voice/0/ampLfo/depth" to 47.0, "add/voice/0/ampLfo/start" to 21.0,
            "add/filter/category" to 1.0, "add/filter/formant/vowel/5/0/frequency" to 87.0,
            "add/filter/formant/sequence/0" to 5.0, "add/resonance/point/17" to 93.0,
        )
        for ((path, value) in edits) {
            assertTrue(path, engine.writeParameter(0, 1, SynthEngine.ParameterWrite(path, value)))
        }
        val edited = engine.parameterSnapshot(0, 1).values.associate { it.descriptor.path to it.value }
        for ((path, value) in edits) assertEquals(path, value, edited.getValue(path), 0.0)
        val source = values(engine, 0, 1)
        val originalKit = values(engine, 0, 0)
        val envelope = ModuleAddress.Envelope(0, EnvelopeRole.MODULATOR_FREQUENCY)
        assertTrue(engine.copyModule(0, 1, envelope))
        assertTrue(engine.pasteModule(0, 0, envelope))
        assertValues("Cross-kit envelope", values(engine, 0, 1, "add/voice/0/modFreqEnvelope/"),
            values(engine, 0, 0, "add/voice/0/modFreqEnvelope/"))
        assertValues("Source kit unchanged", source, values(engine, 0, 1))
        assertEquals(originalKit.filterKeys { !it.startsWith("voice/0/modFreqEnvelope/") },
            values(engine, 0).filterKeys { !it.startsWith("voice/0/modFreqEnvelope/") })
        val file = File(context.cacheDir, "qa-add-advanced.xiz")
        try {
            assertTrue(engine.exportInstrument(0, file))
            assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
            assertValues("Advanced kit ADD", source, values(engine, 1, 1))
            assertValues("Advanced kit activation", values(engine, 0, 1, "kit/"), values(engine, 1, 1, "kit/"))
        } finally {
            file.delete()
        }
    }
}
