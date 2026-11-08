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
            value("add/fadeIn", SynthEngine.ParameterScope.GLOBAL, SynthEngine.ParameterSection.AMPLITUDE),
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

    @Test
    fun resonancePointsNeverBecomeOverviewKnobs() {
        val resonance = listOf(
            value(
                "add/resonance/enabled",
                SynthEngine.ParameterScope.GLOBAL,
                SynthEngine.ParameterSection.RESONANCE,
            ),
            value(
                "add/resonance/point/42",
                SynthEngine.ParameterScope.GLOBAL,
                SynthEngine.ParameterSection.RESONANCE,
                family = SynthEngine.ParameterFamily.RESONANCE,
            ),
        )

        assertTrue(parametersFor(resonance, "ADD", "Resonance", 0).isEmpty())
    }

    @Test
    fun subAndPadUseStructuredModuleAndSectionRouting() {
        val all = listOf("SUB", "PAD").flatMap { module ->
            listOf(
                "Amp" to SynthEngine.ParameterSection.AMPLITUDE,
                "Frequency" to SynthEngine.ParameterSection.FREQUENCY,
                "Filter" to SynthEngine.ParameterSection.FILTER,
                "Harmonics" to SynthEngine.ParameterSection.HARMONICS,
            ).map { (_, section) ->
                val parameter = value("${module.lowercase()}/field", SynthEngine.ParameterScope.GLOBAL, section)
                parameter.copy(descriptor = parameter.descriptor.copy(module = SynthEngine.ParameterModule.valueOf(module)))
            }
        }
        for (module in listOf("SUB", "PAD")) {
            for ((tab, section) in listOf("Amp" to SynthEngine.ParameterSection.AMPLITUDE,
                "Frequency" to SynthEngine.ParameterSection.FREQUENCY,
                "Filter" to SynthEngine.ParameterSection.FILTER, "Harmonics" to SynthEngine.ParameterSection.HARMONICS)) {
                val selected = parametersFor(all, module, tab, 0)
                assertEquals(1, selected.size)
                assertEquals(SynthEngine.ParameterModule.valueOf(module), selected.single().descriptor.module)
                assertEquals(section, selected.single().descriptor.section)
            }
        }
    }

    @Test
    fun subBandwidthIsIndependentOfHarmonicsAndStereoStaysAtKitLevel() {
        fun sub(path: String, section: SynthEngine.ParameterSection) =
            value(path, SynthEngine.ParameterScope.GLOBAL, section).let {
                it.copy(descriptor = it.descriptor.copy(module = SynthEngine.ParameterModule.SUB))
            }
        val bandwidth = listOf(sub("sub/bandwidth", SynthEngine.ParameterSection.BANDWIDTH),
            sub("sub/bandwidthEnvelope/attackTime", SynthEngine.ParameterSection.BANDWIDTH))
        val harmonics = listOf(sub("sub/harmonic/63/bandwidth", SynthEngine.ParameterSection.HARMONICS),
            sub("sub/overtone/par1", SynthEngine.ParameterSection.HARMONICS))
        val stereo = sub("sub/stereo", SynthEngine.ParameterSection.GLOBAL)
        val all = bandwidth + harmonics + stereo
        assertEquals(bandwidth, parametersFor(all, "SUB", "Bandwidth", 0))
        assertEquals(harmonics, parametersFor(all, "SUB", "Harmonics", 0))
        assertTrue(parametersFor(all, "SUB", "Global", 0).isEmpty())
        assertEquals(listOf("Amp", "Harmonics", "Frequency", "Bandwidth", "Filter"),
            InstrumentEditorViewModel.tabsFor("SUB"))
    }
    @Test
    fun padGroupContainsOnlyParametersThatRequireSampleGeneration() {
        val all = SynthEngine.ParameterSection.entries.map { section ->
            value("pad/${section.name.lowercase()}", SynthEngine.ParameterScope.GLOBAL, section).let {
                it.copy(descriptor = it.descriptor.copy(module = SynthEngine.ParameterModule.PAD))
            }
        }
        assertEquals(listOf("Amp", "PAD", "Frequency", "Filter"), InstrumentEditorViewModel.tabsFor("PAD"))
        assertEquals(listOf("Spectrum", "Quality"), InstrumentEditorViewModel.padSubsections)
        assertEquals(setOf(SynthEngine.ParameterSection.SPECTRUM, SynthEngine.ParameterSection.PROFILE,
            SynthEngine.ParameterSection.QUALITY, SynthEngine.ParameterSection.OSCILLATOR,
            SynthEngine.ParameterSection.RESONANCE), parametersFor(all, "PAD", "PAD", 0).map { it.descriptor.section }.toSet())
    }

}
