package com.mick.zynaddsubfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthModuleModelsTest {
    private fun value(path: String, current: Double, group: String = "ADD / Amplitude / Envelope") =
        SynthEngine.ParameterValue(
            SynthEngine.ParameterDescriptor(
                path = path,
                label = path.substringAfterLast('/'),
                group = group,
                type = SynthEngine.ParameterType.INTEGER,
                minimum = 0.0,
                maximum = 127.0,
                defaultValue = 0.0,
                options = emptyList(),
                preferredControl = SynthEngine.PreferredControl.KNOB,
            ),
            current,
        )

    @Test
    fun envelopeModelBuildsFreePointsAndSustain() {
        val prefix = "add/ampEnvelope"
        val values = listOf(
            value("$prefix/freeMode", 1.0),
            value("$prefix/linear", 1.0),
            value("$prefix/pointCount", 3.0),
            value("$prefix/sustainPoint", 1.0),
        ) + (0..2).flatMap { point ->
            listOf(value("$prefix/point/$point/time", point * 20.0), value("$prefix/point/$point/value", 64.0))
        }

        val model = EnvelopeModel.from(values, ModuleAddress.Envelope(envelopeRole = EnvelopeRole.AMPLITUDE))

        assertNotNull(model)
        assertTrue(model!!.freeMode)
        assertTrue(model.linear)
        assertEquals(3, model.points.size)
        assertEquals(1, model.sustainPoint)
        val positions = envelopePositions(model.points)
        assertEquals(0f, positions.first(), 0.0001f)
        assertEquals(1f, positions.last(), 0.0001f)
        assertTrue("The longer second segment must move the middle point left", positions[1] < .5f)

        val duringDrag = envelopePositions(model.points) { if (it.index == 1) 40.0 else it.time.value }
        assertEquals(.5f, duringDrag[1], .0001f)
        assertEquals(20.0, model.points[1].time.value, 0.0)
    }

    @Test
    fun voiceModulatorEnvelopeUsesOwnedPrefix() {
        val path = "add/voice/4/modFreqEnvelope/attackTime"
        val model = EnvelopeModel.from(
            listOf(value(path, 42.0)),
            ModuleAddress.Envelope(4, EnvelopeRole.MODULATOR_FREQUENCY),
        )
        assertEquals(path, model?.parameter("attackTime")?.descriptor?.path)
    }

    @Test
    fun filterModelRecognizesFormantMatrix() {
        val group = "ADD / Filter / Formant"
        val values = listOf(
            value("add/filter/category", 1.0, group),
            value("add/filter/formant/count", 5.0, group),
            value("add/filter/formant/sequenceSize", 4.0, group),
            value("add/filter/formant/vowel/0/0/frequency", 80.0, group),
        )
        val model = FilterModel.from(values, ModuleAddress.Filter())
        assertEquals(1, model?.category)
        assertEquals(5, model?.formant?.count)
        assertEquals(4, model?.formant?.sequenceSize)
    }

    @Test
    fun commonModelsResolveEngineWithoutDependingOnLabels() {
        val values = listOf(
            value("add/freqEnvelope/attackTime", 11.0, "renamed"),
            value("sub/freqEnvelope/attackTime", 22.0, "renamed"),
            value("sub/freqEnvelopeEnabled", 0.0, "renamed"),
            value("sub/bandwidthEnvelope/attackTime", 33.0, "renamed"),
            value("pad/freqEnvelope/attackTime", 44.0, "renamed"),
            value("pad/freqLfo/depth", 55.0, "renamed"),
        )
        for ((engine, expected) in listOf(ModuleEngine.ADD to 11.0, ModuleEngine.SUB to 22.0, ModuleEngine.PAD to 44.0)) {
            val model = EnvelopeModel.from(values, ModuleAddress.Envelope(envelopeRole = EnvelopeRole.FREQUENCY, engine = engine))!!
            assertEquals(expected, model.parameter("attackTime")!!.value, 0.0)
            assertEquals(if (engine == ModuleEngine.SUB) 2 else 1, model.parameters.size)
        }
        assertEquals(33.0, EnvelopeModel.from(values,
            ModuleAddress.Envelope(envelopeRole = EnvelopeRole.BANDWIDTH, engine = ModuleEngine.SUB))!!.parameter("attackTime")!!.value, 0.0)
        assertEquals(null, EnvelopeModel.from(values,
            ModuleAddress.Envelope(envelopeRole = EnvelopeRole.BANDWIDTH, engine = ModuleEngine.PAD)))
        assertEquals(null, EnvelopeModel.from(values,
            ModuleAddress.Envelope(0, EnvelopeRole.FREQUENCY, ModuleEngine.SUB)))
        assertEquals(null, LfoModel.from(values, ModuleAddress.Lfo(lfoRole = LfoRole.FREQUENCY, engine = ModuleEngine.SUB)))
        assertEquals(55.0, LfoModel.from(values,
            ModuleAddress.Lfo(lfoRole = LfoRole.FREQUENCY, engine = ModuleEngine.PAD))!!.parameter("depth")!!.value, 0.0)
    }

    @Test
    fun subFilterOwnsEnableAndCompleteFormants() {
        val values = listOf(
            value("sub/filterEnabled", 0.0),
            value("sub/filter/category", 1.0),
            value("sub/filter/formant/count", 7.0),
            value("sub/filter/formant/sequenceSize", 6.0),
            value("sub/filter/formant/vowel/5/6/frequency", 81.0),
            value("pad/filter/category", 4.0),
            value("add/filter/category", 0.0),
        )
        val model = FilterModel.from(values, ModuleAddress.Filter(engine = ModuleEngine.SUB))!!
        assertEquals(1, model.category)
        assertEquals(7, model.formant!!.count)
        assertEquals(6, model.formant.sequenceSize)
        assertEquals(5, model.parameters.size)
        assertEquals(0.0, model.parameters.first { it.descriptor.path == "sub/filterEnabled" }.value, 0.0)
    }
}
