package com.mick.zynaddsubfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterRoutingTest {
    private fun value(
        path: String,
        scope: SynthEngine.ParameterScope,
        section: SynthEngine.ParameterSection,
        ownerIndex: Int = -1,
        family: SynthEngine.ParameterFamily = SynthEngine.ParameterFamily.BASIC,
    ) = SynthEngine.ParameterValue(
        descriptor = SynthEngine.ParameterDescriptor(
            path = path,
            label = path.substringAfterLast('/'),
            group = "Display labels are not routing metadata",
            type = SynthEngine.ParameterType.INTEGER,
            minimum = 0.0,
            maximum = 127.0,
            defaultValue = 0.0,
            options = emptyList(),
            preferredControl = SynthEngine.PreferredControl.KNOB,
            module = SynthEngine.ParameterModule.ADD,
            scope = scope,
            ownerIndex = ownerIndex,
            section = section,
            family = family,
        ),
        value = 64.0,
    )

    @Test
    fun addAmplitudeRoutesOnlyGlobalParameters() {
        val global = listOf(
            value("add/volume", SynthEngine.ParameterScope.GLOBAL, SynthEngine.ParameterSection.AMPLITUDE),
            value(
                "add/ampEnvelope/attackTime",
                SynthEngine.ParameterScope.GLOBAL,
                SynthEngine.ParameterSection.AMPLITUDE,
                family = SynthEngine.ParameterFamily.ENVELOPE,
            ),
            value(
                "add/ampLfo/depth",
                SynthEngine.ParameterScope.GLOBAL,
                SynthEngine.ParameterSection.AMPLITUDE,
                family = SynthEngine.ParameterFamily.LFO,
            ),
        )
        val voices = (0..7).map { voice ->
            value(
                "add/voice/$voice/ampEnvelope/attackTime",
                SynthEngine.ParameterScope.VOICE,
                SynthEngine.ParameterSection.AMPLITUDE,
                ownerIndex = voice,
                family = SynthEngine.ParameterFamily.ENVELOPE,
            )
        }

        val selected = parametersFor(global + voices, "ADD", "Amp", 0)

        assertEquals(global.map { it.descriptor.path }, selected.map { it.descriptor.path })
        assertTrue(selected.none { it.descriptor.path.startsWith("add/voice/") })
    }

    @Test
    fun addVoiceRoutingUsesTheStructuredOwnerIndex() {
        val voices = (0..7).map { voice ->
            value(
                "add/voice/$voice/volume",
                SynthEngine.ParameterScope.VOICE,
                SynthEngine.ParameterSection.AMPLITUDE,
                ownerIndex = voice,
            )
        }

        val selected = parametersFor(voices, "ADD", "Voices", 5)

        assertEquals(listOf("add/voice/5/volume"), selected.map { it.descriptor.path })
    }
}
