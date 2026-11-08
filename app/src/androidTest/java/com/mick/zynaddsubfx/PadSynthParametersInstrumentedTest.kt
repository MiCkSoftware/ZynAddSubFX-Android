package com.mick.zynaddsubfx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PadSynthParametersInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeDefaultsBoundsAndPackedDetuneArePadSpecific() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.setPartPadEnabled(0, true))
        val initial = engine.parameterSnapshot(0, 0).values.associateBy { it.descriptor.path }
        val defaults = mapOf("volume" to 90.0, "velocity" to 64.0, "fadeIn" to 20.0,
            "punchStrength" to 0.0, "punchTime" to 60.0, "punchStretch" to 64.0, "punchVelocity" to 72.0,
            "detune" to 8192.0, "detuneType" to 1.0, "octave" to 0.0, "coarse" to 0.0,
            "bend" to 24.0, "offsetHz" to 0.0, "fixedFreqEt" to 0.0)
        for ((field, expected) in defaults) {
            val parameter = initial.getValue("pad/$field")
            assertEquals(field, expected, parameter.value, 0.0)
            assertEquals(field, expected, parameter.descriptor.defaultValue, 0.0)
            assertEquals(SynthEngine.ParameterModule.PAD, parameter.descriptor.module)
            assertEquals(SynthEngine.ParameterScope.GLOBAL, parameter.descriptor.scope)
            assertEquals(if (field in listOf("volume", "velocity", "fadeIn") || field.startsWith("punch"))
                SynthEngine.ParameterSection.AMPLITUDE else SynthEngine.ParameterSection.FREQUENCY,
                parameter.descriptor.section)
            for (value in listOf(-99999.0, 99999.0)) {
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/$field", value)))
                val actual = engine.parameterSnapshot(0, 0).values.first { it.descriptor.path == "pad/$field" }.value
                assertEquals(field, if (value < 0) parameter.descriptor.minimum else parameter.descriptor.maximum, actual, 0.0)
            }
        }
        for (octave in listOf(-8.0, -1.0, 0.0, 7.0)) for (coarse in listOf(-64.0, -1.0, 0.0, 63.0)) {
            assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/octave", octave)))
            assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/coarse", coarse)))
            val values = engine.parameterSnapshot(0, 0).values.associate { it.descriptor.path to it.value }
            assertEquals(octave, values.getValue("pad/octave"), 0.0)
            assertEquals(coarse, values.getValue("pad/coarse"), 0.0)
        }
        for (flag in listOf("ampEnvelopeEnabled", "freqEnvelopeEnabled", "filterEnvelopeEnabled",
            "ampLfoEnabled", "freqLfoEnabled", "filterLfoEnabled", "filterEnabled")) {
            assertFalse(initial.containsKey("pad/$flag"))
            assertFalse(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/$flag", 1.0)))
        }
        assertEquals(initial.getValue("add/volume").value, engine.parameterSnapshot(0, 0).values.first { it.descriptor.path == "add/volume" }.value, 0.0)
    }

    @Test
    fun padOnlyAndMixedKitXizRestorePlayingParametersAndModules() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        val edits = mapOf("volume" to 113.0, "panning" to 0.0, "velocity" to 91.0, "fadeIn" to 37.0,
            "punchStrength" to 82.0, "punchTime" to 31.0, "punchStretch" to 109.0, "punchVelocity" to 19.0,
            "detune" to 12345.0, "detuneType" to 4.0, "octave" to -3.0, "coarse" to -57.0,
            "bend" to -11.0, "offsetHz" to 47.0, "fixedFreq" to 1.0, "fixedFreqEt" to 93.0,
            "filterVelocity" to 31.0, "filterVelocitySense" to 112.0,
            "ampEnvelope/attackTime" to 53.0, "freqEnvelope/attackTime" to 72.0, "filterEnvelope/attackTime" to 96.0,
            "ampLfo/depth" to 41.0, "freqLfo/depth" to 58.0, "filterLfo/depth" to 69.0,
            "filter/category" to 4.0, "filter/type" to 3.0, "filter/combHpf" to 26.0, "filter/combLpf" to 102.0,
            "qualitySize" to 0.0, "qualityOctaves" to 0.0, "qualitySamplesPerOctave" to 0.0)
        val file = File(context.cacheDir, "pad-playing-parameters.xiz")
        try {
            for (mixed in listOf(false, true)) {
                fun write(kit: Int, path: String, value: Double) =
                    assertTrue(path, engine.writeParameter(0, kit, SynthEngine.ParameterWrite(path, value)))
                write(0, "part/kitMode", if (mixed) 1.0 else 0.0)
                val kits = if (mixed) listOf(0, 2) else listOf(0)
                for (kit in kits) {
                    write(kit, "kit/enabled", 1.0)
                    write(kit, "kit/padEnabled", 1.0)
                    write(kit, "kit/addEnabled", if (mixed) 1.0 else 0.0)
                    write(kit, "kit/subEnabled", if (mixed) 1.0 else 0.0)
                    for ((field, value) in edits) write(kit, "pad/$field", value)
                    if (mixed) {
                        write(kit, "add/volume", 71.0)
                        write(kit, "sub/volume", 81.0)
                    }
                }
                assertTrue(engine.exportInstrument(0, file))
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
                for (kit in kits) {
                    val restored = engine.parameterSnapshot(1, kit).values.associate { it.descriptor.path to it.value }
                    for ((field, value) in edits) assertEquals("$mixed/$kit/$field", value, restored.getValue("pad/$field"), .01)
                    assertEquals(if (mixed) 1.0 else 0.0, restored.getValue("kit/addEnabled"), 0.0)
                    assertEquals(1.0, restored.getValue("kit/padEnabled"), 0.0)
                    if (mixed) {
                        assertEquals(71.0, restored.getValue("add/volume"), .01)
                        assertEquals(81.0, restored.getValue("sub/volume"), .01)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun padModulePreviewsAndClipboardKeepTheirKitAndEngineOwner() {
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.setPartPadEnabled(0, true))
        assertTrue(engine.writeParameter(1, 2, SynthEngine.ParameterWrite("kit/enabled", 1.0)))
        assertTrue(engine.writeParameter(1, 2, SynthEngine.ParameterWrite("kit/padEnabled", 1.0)))
        val edits = mapOf("ampEnvelope/attackTime" to 53.0, "freqEnvelope/attackTime" to 72.0,
            "filterEnvelope/attackTime" to 96.0, "ampLfo/depth" to 41.0,
            "freqLfo/depth" to 58.0, "filterLfo/depth" to 69.0, "filter/cutoff" to 87.0)
        for ((field, value) in edits) assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/$field", value)))
        val peer = engine.parameterSnapshot(1, 0)
        val addVolume = engine.parameterSnapshot(1, 2).values.first { it.descriptor.path == "add/volume" }.value
        val addresses = EnvelopeRole.entries.take(3).map { ModuleAddress.Envelope(envelopeRole = it, engine = ModuleEngine.PAD) } +
            LfoRole.entries.map { ModuleAddress.Lfo(lfoRole = it, engine = ModuleEngine.PAD) } +
            listOf(ModuleAddress.Filter(engine = ModuleEngine.PAD))
        for (address in addresses) {
            val preview = engine.preview(0, 0, address, 64)
            assertTrue(preview.values.isNotEmpty())
            assertTrue(engine.copyModule(0, 0, address))
            assertTrue(engine.pasteModule(1, 2, address))
            assertEquals(preview, engine.preview(1, 2, address, 64))
        }
        val restored = engine.parameterSnapshot(1, 2).values.associate { it.descriptor.path to it.value }
        for ((field, value) in edits) assertEquals(field, value, restored.getValue("pad/$field"), .01)
        assertEquals(peer, engine.parameterSnapshot(1, 0))
        assertEquals(addVolume, restored.getValue("add/volume"), 0.0)
    }
}
