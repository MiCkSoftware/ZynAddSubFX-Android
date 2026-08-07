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
}
