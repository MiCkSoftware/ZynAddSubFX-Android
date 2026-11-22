package com.mick.zynaddsubfx

import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioPolyphonyInstrumentedTest {
    @Test
    fun measureSustainedChordsAndEditorLoad() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = SynthEngine(context)
        val audio = context.getSystemService(AudioManager::class.java)
        val sampleRate = audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
        val burst = audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
        val synthBlock = InstrumentationRegistry.getArguments().getString("synthBlockFrames")?.toInt() ?: burst
        val outputBuffer = InstrumentationRegistry.getArguments().getString("outputBufferFrames")?.toInt() ?: 0
        val preset = "presets/banks/Cormi_Noise/0013-Passa_asteroide.xiz"
        val notes = listOf(48, 52, 55, 59, 60, 64, 67, 71, 72, 76, 79, 83, 84, 88, 91, 95)
        val counters = listOf("aaudioXruns", "editorSkips", "qDrops", "aaudioStarts", "aaudioErrCb", "aaudioRecovered")
        val fxAddress = FxAddress()
        val fxEditor = FxEditorModel(engine, fxAddress)
        val countersPattern = Regex("""(\w+)=(-?\d+)""")
        fun readCounters() = countersPattern.findAll(engine.runtimeDiagnostics())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

        fun measure(label: String, noteCount: Int, load: () -> Unit = {}) {
            NativeSynthBridge.nativeClearRecentOutputPeak()
            val before = readCounters()
            assertTrue("AAudio xrun counter unavailable", before.getValue("aaudioXruns") >= 0)
            val chord = notes.take(noteCount)
            chord.forEach { engine.noteOn(0, it, 100) }
            load()
            Thread.sleep(2_000)
            val peak = engine.recentOutputPeak()
            val after = readCounters()
            println("POLYPHONY $label notes=$noteCount " +
                counters.joinToString(" ") { "$it=${after.getValue(it) - before.getValue(it)}" } +
                " peak=$peak")
            chord.forEach { engine.noteOff(0, it) }
            engine.panic()
            Thread.sleep(300)
            assertEquals("AAudio stream restarted during $label", before["aaudioStarts"], after["aaudioStarts"])
            assertEquals("AAudio stopped during $label", 1, after["aaudioRunning"])
            if (noteCount > 0) assertTrue("$label produced no signal", peak.isFinite() && peak > 0f)
        }

        NativeSynthBridge.nativeStopAudio()
        assertTrue(NativeSynthBridge.nativeInit(sampleRate, burst, synthBlock, outputBuffer))
        try {
            NativeSynthBridge.nativeSetTestToneEnabled(false)
            for (part in 0..1) {
                assertTrue(engine.loadPackagedPreset(preset, part))
                assertTrue(engine.setPartReceiveChannel(part, 0))
                assertTrue(engine.setPartVolume127(part, 64))
                assertTrue(engine.writeParameter(part, 0, SynthEngine.ParameterWrite("part/keyLimit", 16.0)))
            }
            engine.setMasterVolumeNormalized(.42f)
            assertTrue(engine.setPartEnabled(1, false))
            assertTrue(engine.startAudio())
            Thread.sleep(500)
            println("POLYPHONY stream synthBlock=$synthBlock ${engine.runtimeDiagnostics()}")
            measure("idle", 0)
            for (parts in 1..2) {
                assertTrue(engine.setPartEnabled(1, parts == 2))
                repeat(3) { run ->
                    for (count in listOf(1, 4, 8, 16)) {
                        measure("parts=$parts run=${run + 1}", count)
                    }
                }
            }
            val fx = checkNotNull(engine.fxSnapshot(fxAddress))
            assertTrue(engine.writeFx(fx, checkNotNull(fx.parameter("fx/type")), 7.0))
            fxEditor.open()
            val eq = checkNotNull(fxEditor.snapshot)
            fxEditor.write(checkNotNull(eq.parameter("fx/parameter/10")), 7.0)
            val gain = checkNotNull(fxEditor.snapshot?.parameter("fx/parameter/12"))
            repeat(3) { run ->
                measure("parts=2 eq-edit run=${run + 1}", 8) {
                    repeat(40) { edit ->
                        fxEditor.write(gain, if (edit % 2 == 0) 48.0 else 80.0, continuous = true)
                        Thread.sleep(20)
                    }
                    fxEditor.commit()
                    assertEquals(null, fxEditor.error)
                }
            }
            for ((path, value) in mapOf("pad/qualitySize" to 6.0, "pad/qualityOctaves" to 3.0,
                "pad/qualitySamplesPerOctave" to 2.0)) {
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite(path, value)))
            }
            repeat(3) { run ->
                assertTrue(engine.writeParameter(0, 0, SynthEngine.ParameterWrite("pad/bandwidth", 600.0 + run)))
                measure("parts=2 pad-apply run=${run + 1}", 8) {
                    assertTrue(engine.startPadGeneration(0, 0))
                }
                // PAD waits for held notes to finish before installing the new bank.
                val deadline = SystemClock.elapsedRealtime() + 15_000
                var status = engine.padGenerationStatus(0, 0)
                while (status.phase in setOf("generating", "ready") && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(20)
                    status = engine.padGenerationStatus(0, 0)
                }
                assertEquals(status.toString(), "done", status.phase)
                assertEquals(8, status.total)
                println("POLYPHONY pad-bank ms=${status.elapsedMs} frames=${status.frames} samples=${status.total}")
            }
            measure("idle-end", 0)
        } finally {
            engine.stopAudio()
            engine.panic()
            engine.cancelPadGeneration()
        }
    }
}
