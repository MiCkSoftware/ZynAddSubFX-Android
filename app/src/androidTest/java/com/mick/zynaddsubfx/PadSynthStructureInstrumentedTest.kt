package com.mick.zynaddsubfx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PadSynthStructureInstrumentedTest {
    @Test fun structureAndCustomBaseSurviveTypedClipboardAndXizInInactiveModes() {
        NativeSynthBridge.nativeStopAudio()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        assertTrue(engine.setPartPadEnabled(0, true))
        assertTrue(engine.setPartPadEnabled(1, true))
        val add = engine.parameterSnapshot(0, 0).values.filter { it.descriptor.module == SynthEngine.ParameterModule.ADD }
        val edits = mapOf("profile/baseType" to 2.0, "profile/baseParameter" to 91.0, "profileWidth" to 69.0,
            "profile/side" to 1.0, "profileAutoScale" to 0.0, "profile/frequencyMultiplier" to 17.0,
            "profile/modulatorAmount" to 35.0, "profile/modulatorFrequency" to 44.0,
            "profile/amplitudeType" to 0.0, "profile/amplitudeMode" to 3.0,
            "profile/amplitudeParameter1" to 83.0, "profile/amplitudeParameter2" to 115.0,
            "overtone/type" to 5.0, "overtone/par1" to 131.0, "overtone/par2" to 213.0, "overtone/par3" to 93.0,
            "qualityBaseNote" to 9.0, "bandwidthScale" to 7.0,
            "osc/baseFunction" to 3.0, "osc/harmonic/2/magnitude" to 91.0, "osc/harmonic/2/phase" to 13.0,
            "resonance/enabled" to 1.0, "resonance/point/17" to 94.0,
            "qualitySize" to 0.0, "qualityOctaves" to 0.0, "qualitySamplesPerOctave" to 0.0)
        for ((field, value) in edits) assertTrue(field, engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/$field", value)))
        for (address in listOf(ModuleAddress.PadProfile, ModuleAddress.PadOvertones)) {
            val preview = engine.preview(0, 0, address, 512)
            assertTrue(preview.values.isNotEmpty())
            assertTrue(preview.values.all { it.isFinite() })
            assertTrue(engine.copyModule(0, 0, address))
            assertFalse(engine.canPasteModule(if (address == ModuleAddress.PadProfile) ModuleAddress.PadOvertones else ModuleAddress.PadProfile))
            assertTrue(engine.pasteModule(1, 0, address))
            assertEquals(preview, engine.preview(1, 0, address, 512))
        }
        assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/osc/action/useAsBase", 1.0)))
        val oscillator = ModuleAddress.Oscillator(-1, engine = ModuleEngine.PAD)
        val baseAddress = ModuleAddress.OscillatorPreview(oscillator, OscillatorPreviewView.BASE_WAVE)
        val base = engine.preview(0, 0, baseAddress, 256)
        assertTrue(engine.copyModule(0, 0, oscillator))
        assertTrue(engine.pasteModule(1, 0, oscillator))
        assertArrayEquals(base.values.toFloatArray(), engine.preview(1, 0, baseAddress, 256).values.toFloatArray(), .0001f)
        assertEquals(add, engine.parameterSnapshot(0, 0).values.filter { it.descriptor.module == SynthEngine.ParameterModule.ADD })
        val file = File(context.cacheDir, "pad-structure.xiz")
        try {
            for (mode in listOf(0.0, 1.0, 2.0)) {
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/mode", mode)))
                assertTrue(engine.exportInstrument(0, file))
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 1))
                val restored = engine.parameterSnapshot(1, 0).values.associateBy { it.descriptor.path }
                for ((field, value) in edits.filterKeys { !it.startsWith("osc/") })
                    assertEquals(field, value, restored.getValue("pad/$field").value, .01)
                assertEquals(SynthEngine.ParameterSection.PROFILE, restored.getValue("pad/profile/amplitudeMode").descriptor.section)
                assertEquals(SynthEngine.ParameterSection.SPECTRUM, restored.getValue("pad/overtone/type").descriptor.section)
                assertArrayEquals(base.values.toFloatArray(), engine.preview(1, 0, baseAddress, 256).values.toFloatArray(), .0001f)
            }
        } finally { file.delete() }
    }
}
