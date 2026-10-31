package com.mick.zynaddsubfx

import org.junit.Assert.*
import org.junit.Test

class AddVoiceSummaryTest {
    private fun values(vararg fields: Pair<String, Double>) = fields.associate { (path, value) ->
        path to SynthEngine.ParameterValue(
            SynthEngine.ParameterDescriptor(path, path, "", SynthEngine.ParameterType.INTEGER,
                0.0, 16383.0, 0.0, emptyList(), SynthEngine.PreferredControl.KNOB), value,
        )
    }

    @Test fun originalListVibratoUsesFrequencyLfoAndKeepsActivationSeparate() {
        val parameters = values(
            "add/voice/0/enabled" to 1.0,
            "add/voice/0/vibrato" to 64.0,
            "add/voice/0/freqLfo/depth" to 40.0,
            "add/voice/0/freqLfoEnabled" to 0.0,
        )
        val voice = AddVoiceSummary.from(parameters, 0)!!
        assertEquals("add/voice/0/freqLfo/depth", voice.lfoDepth!!.descriptor.path)
        assertEquals(40.0, voice.lfoDepth.value, 0.0)
        assertFalse(voice.lfoEnabled)
        val detail = LfoModel.from(parameters.values.toList(), ModuleAddress.Lfo(0, LfoRole.FREQUENCY))!!
        assertSame(voice.lfoDepth, detail.parameter("depth"))
        assertTrue(detail.parameters.contains(parameters.getValue("add/voice/0/freqLfoEnabled")))
        assertFalse(detail.parameters.contains(parameters.getValue("add/voice/0/vibrato")))
        assertEquals(64.0, parameters.getValue("add/voice/0/vibrato").value, 0.0)
    }

    @Test fun listFieldsCannotResolveNestedOscillatorOrModulatorParameters() {
        val voice = AddVoiceSummary.from(values(
            "add/voice/1/osc/enabled" to 1.0,
            "add/voice/1/enabled" to 0.0,
            "add/voice/1/modulation/volume" to 20.0,
            "add/voice/1/volume" to 100.0,
            "add/voice/1/resonance" to 1.0,
            "add/voice/0/panning" to 12.0,
            "add/voice/1/panning" to 82.0,
        ), 1)!!
        assertEquals(0.0, voice.enabled.value, 0.0)
        assertEquals(100.0, voice.volume!!.value, 0.0)
        assertEquals(82.0, voice.panning!!.value, 0.0)
        assertEquals("add/voice/1/resonance", voice.resonance!!.descriptor.path)
    }

    @Test fun inheritedDetuneTypeUsesGlobalWhileExplicitTypeWins() {
        val parameters = values(
            "add/detuneType" to 4.0,
            "add/voice/0/enabled" to 1.0,
            "add/voice/0/detuneType" to 0.0,
            "add/voice/1/enabled" to 1.0,
            "add/voice/1/detuneType" to 2.0,
        )
        assertEquals(4, AddVoiceSummary.from(parameters, 0)!!.detuneType)
        assertEquals(2, AddVoiceSummary.from(parameters, 1)!!.detuneType)
    }

    @Test fun sharedWaveformUsesReferencedOscillatorAndOwnPhase() {
        val voice = AddVoiceSummary.from(values(
            "add/voice/3/enabled" to 1.0,
            "add/voice/3/externalOscillator" to 2.0,
            "add/voice/3/oscillatorPhase" to -32.0,
            "add/voice/3/oscillatorType" to 1.0,
        ), 3)!!
        assertEquals(1, voice.oscillatorSource)
        assertEquals(-32, voice.oscillatorPhase)
        assertEquals(1, voice.soundMode)
    }

    @Test fun draggingListLfoAndTogglingCurveDoNotChangeUnisonOrEnableLfo() {
        val nativeValues = values(
            "add/voice/0/enabled" to 0.0,
            "add/voice/0/vibrato" to 64.0,
            "add/voice/0/freqLfo/depth" to 40.0,
            "add/voice/0/freqLfoEnabled" to 0.0,
        ).toMutableMap()
        var snapshots = 0
        val engine = object : InstrumentEditorEngine {
            override fun parameterSnapshot(partIndex: Int, kitIndex: Int): SynthEngine.ParameterSnapshot {
                snapshots++
                return SynthEngine.ParameterSnapshot(partIndex, kitIndex, nativeValues.values.toList())
            }
            override fun writeParameter(partIndex: Int, kitIndex: Int, write: SynthEngine.ParameterWrite): Boolean {
                nativeValues[write.path] = nativeValues.getValue(write.path).copy(value = write.value)
                return true
            }
            override fun preview(partIndex: Int, kitIndex: Int, address: ModuleAddress, resolution: Int) = PreviewSeries.Empty
            override fun copyModule(partIndex: Int, kitIndex: Int, address: ModuleAddress) = true
            override fun canPasteModule(address: ModuleAddress) = true
            override fun pasteModule(partIndex: Int, kitIndex: Int, address: ModuleAddress) = true
        }
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        val voice = AddVoiceSummary.from(nativeValues, 0)!!
        model.dragParameter(voice.lfoDepth!!, 55.0)
        assertEquals(1, snapshots)
        model.finishParameterDrag()
        model.write(voice.enabled, 1.0)
        assertEquals(55.0, nativeValues.getValue("add/voice/0/freqLfo/depth").value, 0.0)
        assertEquals(1.0, nativeValues.getValue("add/voice/0/enabled").value, 0.0)
        assertEquals(0.0, nativeValues.getValue("add/voice/0/freqLfoEnabled").value, 0.0)
        assertEquals(64.0, nativeValues.getValue("add/voice/0/vibrato").value, 0.0)
    }

    @Test fun allEightVoicesRemainAvailableIncludingDisabledOnes() {
        val parameters = values(*(0..7).map { "add/voice/$it/enabled" to if (it == 0) 1.0 else 0.0 }.toTypedArray())
        assertEquals((0..7).toList(), (0..7).mapNotNull { AddVoiceSummary.from(parameters, it)?.voice })
        assertNull(AddVoiceSummary.from(parameters, 8))
    }
}
