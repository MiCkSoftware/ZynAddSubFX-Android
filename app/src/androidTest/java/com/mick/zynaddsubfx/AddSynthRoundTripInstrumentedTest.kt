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
    fun advancedOscillatorControlsSurviveClipboardAndXizForBothRoles() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val preset = "presets/banks/Companion/0078-Brassy.xiz"
        assertTrue(engine.loadPackagedPreset(preset, 0))
        assertTrue(engine.loadPackagedPreset(preset, 1))
        val advanced = mapOf(
            "baseFunction" to 16.0, "baseModType" to 4.0,
            "baseMod1" to 71.0, "baseMod2" to 47.0, "baseMod3" to 39.0,
            "waveshapeType" to 17.0, "waveshape" to 81.0, "filterType" to 14.0,
            "filterBeforeShape" to 1.0, "modulationType" to 3.0,
            "modulation1" to 48.0, "modulation2" to 33.0, "modulation3" to 21.0,
            "spectrumType" to 2.0, "spectrumAmount" to 57.0,
            "harmonicShift" to -3.0, "harmonicShiftFirst" to 1.0,
            "adaptiveMode" to 8.0, "adaptivePower" to 200.0,
            "adaptiveBaseFreq" to 255.0, "adaptiveAmount" to 100.0,
            "ampRandomType" to 2.0, "ampRandomPower" to 29.0,
            "harmonic/3/magnitude" to 87.0, "harmonic/3/phase" to 53.0,
        )
        val file = File(context.cacheDir, "qa-advanced-oscillators.xiz")
        try {
            for (modulator in listOf(false, true)) {
                val role = if (modulator) "modOsc" else "osc"
                val prefix = "add/voice/0/$role/"
                val address = ModuleAddress.Oscillator(0, modulator)
                val otherPrefix = "add/voice/0/${if (modulator) "osc" else "modOsc"}/"
                val otherBefore = values(engine, 0, prefix = otherPrefix)
                for ((field, value) in advanced) {
                    val path = prefix + field
                    assertTrue(path, engine.writeParameter(0, 0, SynthEngine.ParameterWrite(path, value)))
                }
                val source = values(engine, 0, prefix = prefix)
                assertValues(role, advanced, source.filterKeys(advanced::containsKey))
                assertValues("Other role", otherBefore, values(engine, 0, prefix = otherPrefix))

                assertTrue(engine.copyModule(0, 0, address))
                assertTrue(engine.pasteModule(1, 0, address))
                assertValues("Clipboard $role", source, values(engine, 1, prefix = prefix))
                assertTrue(engine.exportInstrument(0, file))
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
                assertValues("XIZ $role", source, values(engine, 1, prefix = prefix))

                val shift = engine.parameterSnapshot(0, 0).values
                    .first { it.descriptor.path == prefix + "harmonicShift" }.descriptor
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite(shift.path, shift.defaultValue)))
                assertEquals(0.0, values(engine, 0, prefix = prefix).getValue("harmonicShift"), 0.0)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun oscillatorActionsAndNativePreviewsTargetTheSelectedGenerator() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.loadPackagedPreset("presets/banks/Companion/0078-Brassy.xiz", 0))
        val file = File(context.cacheDir, "qa-custom-oscillator-base.xiz")
        try {
            for (modulator in listOf(false, true)) {
                val role = if (modulator) "modOsc" else "osc"
                val prefix = "add/voice/0/$role/"
                val address = ModuleAddress.Oscillator(0, modulator)
                fun write(field: String, value: Double) {
                    assertTrue(field, engine.writeParameter(0, 0, SynthEngine.ParameterWrite(prefix + field, value)))
                }
                fun preview(target: ModuleAddress = address) = engine.preview(0, 0, target, 128).values
                    .also { result ->
                        assertEquals(128, result.size)
                        assertTrue(result.all { it.isFinite() && it in -1f..1f })
                        assertTrue(result.any { kotlin.math.abs(it) > .1f })
                    }

                val external = "external" + if (modulator) "ModOscillator" else "Oscillator"
                val sourcePath = "add/voice/1/$external"
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite(sourcePath, 7.0)))
                assertEquals("Only earlier voices can be referenced", 1.0, values(engine, 0).getValue("voice/1/$external"), 0.0)
                val consumerBefore = values(engine, 0, prefix = "add/voice/1/$role/")
                write("action/convertToSine", 1.0)
                write("action/clearHarmonics", 1.0)
                val sine = preview()
                write("baseFunction", 3.0)
                write("baseShape", 82.0)
                write("harmonic/2/magnitude", 107.0)
                write("harmonic/2/phase", 49.0)
                assertNotEquals(sine, preview())
                write("waveshape", 103.0)
                val dry = preview()
                write("waveshapeType", 17.0)
                assertNotEquals(dry, preview())
                write("filterType", 1.0)
                write("filter1", 95.0)
                val shapeFirst = preview()
                write("filterBeforeShape", 1.0)
                assertNotEquals(shapeFirst, preview())
                OscillatorPreviewView.entries.forEach { preview(ModuleAddress.OscillatorPreview(address, it)) }

                write("action/useAsBase", 1.0)
                val custom = values(engine, 0, prefix = prefix)
                assertEquals(127.0, custom.getValue("baseFunction"), 0.0)
                val baseView = ModuleAddress.OscillatorPreview(address, OscillatorPreviewView.BASE_WAVE)
                val customWave = preview(baseView)
                fun assertCustomWave(label: String) {
                    val roundTrip = engine.preview(1, 0, baseView, 128).values
                    assertEquals(customWave.size, roundTrip.size)
                    // XIZ normalizes the spectrum and omits coefficients below 1e-6.
                    customWave.zip(roundTrip).forEachIndexed { index, (expected, actual) ->
                        assertEquals("$label sample $index", expected, actual, .00001f)
                    }
                }
                assertTrue(engine.copyModule(0, 0, address))
                assertTrue(engine.pasteModule(1, 0, address))
                assertValues("Custom clipboard $role", custom, values(engine, 1, prefix = prefix))
                assertCustomWave("Clipboard $role")
                assertEquals(customWave, preview(baseView))
                assertTrue(engine.exportInstrument(0, file))
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
                assertValues("Custom XIZ $role", custom, values(engine, 1, prefix = prefix))
                assertCustomWave("XIZ $role")
                assertEquals(customWave, preview(baseView))

                write("action/clearHarmonics", 1.0)
                val cleared = values(engine, 0, prefix = prefix)
                assertEquals(107.0, custom.getValue("harmonic/2/magnitude"), 0.0)
                assertEquals(64.0, cleared.getValue("harmonic/2/magnitude"), 0.0)
                assertEquals(64.0, cleared.getValue("harmonic/2/phase"), 0.0)
                assertEquals(127.0, cleared.getValue("harmonic/0/magnitude"), 0.0)
                assertEquals(127.0, cleared.getValue("baseFunction"), 0.0)
                write("action/convertToSine", 1.0)
                val converted = values(engine, 0, prefix = prefix)
                assertEquals(0.0, converted.getValue("baseFunction"), 0.0)
                assertEquals(0.0, converted.getValue("waveshapeType"), 0.0)
                assertValues("Shared consumer generator untouched", consumerBefore,
                    values(engine, 0, prefix = "add/voice/1/$role/"))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun globalFilterCategoriesAndCombControlsSurviveXizReload() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.loadPackagedPreset("presets/banks/Companion/0078-Brassy.xiz", 0))
        val voiceBefore = values(engine, 0, prefix = "add/voice/0/")
        val options = listOf(
            listOf("LPF1", "HPF1", "LPF2", "HPF2", "BPF1", "Notch1", "Peak1", "Low shelf", "High shelf"),
            emptyList(),
            listOf("Low pass", "High pass", "Band pass", "Notch"),
            listOf("Low pass", "Band pass", "High pass"),
            listOf("Feedback", "Feedforward", "Both", "Negative feedback", "Negative feedforward", "Negative both"),
        )
        fun write(field: String, value: Double) {
            assertTrue(field, engine.writeParameter(0, 0, SynthEngine.ParameterWrite("add/filter/$field", value)))
        }
        for ((category, expected) in options.withIndex()) {
            write("category", category.toDouble())
            val snapshot = engine.parameterSnapshot(0, 0).values.associateBy { it.descriptor.path }
            assertEquals(category.toDouble(), snapshot.getValue("add/filter/category").value, 0.0)
            assertEquals(category == 4, "add/filter/combHpf" in snapshot)
            assertEquals(category == 4, "add/filter/combLpf" in snapshot)
            if (expected.isEmpty()) {
                assertFalse("Formant has dedicated controls", "add/filter/type" in snapshot)
                assertTrue("add/filter/formant/count" in snapshot)
            } else {
                val type = snapshot.getValue("add/filter/type").descriptor
                assertEquals(expected, type.options)
                assertEquals((expected.size - 1).toDouble(), type.maximum, 0.0)
                for (index in expected.indices) {
                    write("type", index.toDouble())
                    assertEquals(index.toDouble(), values(engine, 0, prefix = "add/filter/").getValue("type"), 0.0)
                }
                write("type", 127.0)
                assertEquals(type.maximum, values(engine, 0, prefix = "add/filter/").getValue("type"), 0.0)
                write("type", -1.0)
                assertEquals(0.0, values(engine, 0, prefix = "add/filter/").getValue("type"), 0.0)
            }
        }
        write("combHpf", -1.0)
        write("combLpf", 128.0)
        assertEquals(0.0, values(engine, 0, prefix = "add/filter/").getValue("combHpf"), 0.0)
        assertEquals(127.0, values(engine, 0, prefix = "add/filter/").getValue("combLpf"), 0.0)
        write("combHpf", 37.0)
        write("combLpf", 91.0)
        write("type", 5.0)
        write("category", 0.0)
        write("category", 4.0)
        val source = values(engine, 0, prefix = "add/filter/")
        assertEquals(37.0, source.getValue("combHpf"), 0.0)
        assertEquals(91.0, source.getValue("combLpf"), 0.0)
        assertEquals(voiceBefore, values(engine, 0, prefix = "add/voice/0/"))
        val file = File(context.cacheDir, "qa-global-comb-filter.xiz")
        try {
            assertTrue(engine.copyModule(0, 0, ModuleAddress.Filter()))
            assertTrue(engine.pasteModule(1, 0, ModuleAddress.Filter()))
            assertValues("Comb clipboard", source, values(engine, 1, prefix = "add/filter/"))
            assertTrue(engine.exportInstrument(0, file))
            assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
            assertValues("Comb XIZ", source, values(engine, 1, prefix = "add/filter/"))
        } finally {
            file.delete()
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
