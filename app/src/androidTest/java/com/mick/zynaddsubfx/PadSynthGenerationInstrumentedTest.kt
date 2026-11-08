package com.mick.zynaddsubfx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PadSynthGenerationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun write(engine: SynthEngine, path: String, value: Double, kit: Int = 0) =
        assertTrue(path, engine.writeParameter(0, kit, SynthEngine.ParameterWrite(path, value)))

    private fun await(engine: SynthEngine, phase: String, kit: Int = 0): PadGenerationStatus {
        val deadline = android.os.SystemClock.elapsedRealtime() + 35_000
        var status = engine.padGenerationStatus(0, kit)
        while (status.phase != phase && android.os.SystemClock.elapsedRealtime() < deadline) {
            assertFalse(status.toString(), status.phase == "failed")
            Thread.sleep(10)
            status = engine.padGenerationStatus(0, kit)
        }
        assertEquals(status.toString(), phase, status.phase)
        return status
    }

    @Test
    fun fftwProducesFiniteNonzeroPcmAtSeveralQualitiesAndRestoresSession() {
        NativeSynthBridge.nativeStopAudio()
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        write(engine, "kit/padEnabled", 1.0)
        write(engine, "kit/addEnabled", 0.0)
        write(engine, "pad/qualityOctaves", 0.0)
        write(engine, "pad/qualitySamplesPerOctave", 0.0)
        try {
            for (quality in listOf(0.0, 2.0, 4.0, 6.0)) {
                write(engine, "pad/qualitySize", quality)
                val start = android.os.SystemClock.elapsedRealtime()
                assertTrue(engine.startPadGeneration(0, 0))
                val requestMs = android.os.SystemClock.elapsedRealtime() - start
                assertTrue("Generation request blocked for $requestMs ms", requestMs < 1000)
                val status = await(engine, "done")
                assertEquals(1, status.total)
                assertEquals(1 shl (quality.toInt() + 14), status.frames)
                assertTrue(status.toString(), status.peak.isFinite() && status.peak > 0)
                assertTrue(status.toString(), status.elapsedMs < 30_000)
                println("PAD FFTW quality=$quality request=${requestMs}ms generate=${status.elapsedMs}ms bytes=${status.bytes} peak=${status.peak}")
            }
            // Technical audio check: real AAudio output, PAD only, fallback tone disabled.
            NativeSynthBridge.nativeSetTestToneEnabled(false)
            assertTrue(engine.setPartEnabled(0, true))
            NativeSynthBridge.nativeClearRecentOutputPeak()
            assertTrue(NativeSynthBridge.nativeStartAudio())
            NativeSynthBridge.nativeNoteOn(0, 69, 100)
            val deadline = android.os.SystemClock.elapsedRealtime() + 3000
            while (NativeSynthBridge.nativeGetRecentOutputPeak() <= 0 && android.os.SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(10)
            assertTrue("PAD produced no output PCM", NativeSynthBridge.nativeGetRecentOutputPeak() > 0)
            NativeSynthBridge.nativeNoteOff(0, 69)
            NativeSynthBridge.nativeStopAudio()
            // Unsaved structural edits must be exported even before Apply.
            write(engine, "pad/qualitySize", 0.0)
            write(engine, "pad/bandwidth", 613.0)
            assertEquals("pending", engine.padGenerationStatus(0, 0).phase)
            val file = File(context.cacheDir, "pad-generation-session.xmz")
            try {
                val capture = NativeSynthBridge.nativeCaptureSession()
                assertTrue(capture != 0L)
                assertTrue(NativeSynthBridge.nativeWriteSession(capture, file.absolutePath))
                assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(file.absolutePath, 0))
                assertEquals("done", engine.padGenerationStatus(0, 0).phase)
                assertEquals(613.0, engine.parameterSnapshot(0, 0).values.first { it.descriptor.path == "pad/bandwidth" }.value, 0.0)
            } finally { file.delete() }
            // This bank exceeds the former 1.2 s load budget on slower devices.
            write(engine, "pad/qualitySize", 6.0)
            write(engine, "pad/qualityOctaves", 3.0)
            write(engine, "pad/qualitySamplesPerOctave", 2.0)
            val largeXiz = File(context.cacheDir, "pad-generation-large.xiz")
            try {
                assertTrue(engine.exportInstrument(0, largeXiz))
                val started = android.os.SystemClock.elapsedRealtime()
                assertTrue(NativeSynthBridge.nativeLoadPresetFile(largeXiz.absolutePath, 1))
                val status = engine.padGenerationStatus(1, 0)
                assertEquals("done", status.phase)
                assertEquals(8, status.total)
                assertEquals(1 shl 20, status.frames)
                println("PAD large XIZ reload: ${android.os.SystemClock.elapsedRealtime() - started}ms bytes=${status.bytes}")
            } finally { largeXiz.delete() }
        } finally {
            NativeSynthBridge.nativeStopAudio()
            NativeSynthBridge.nativePanic()
        }
    }

    @Test
    fun editsCancelStaleJobsAndMemoryLimitPreservesThePreviousBank() {
        NativeSynthBridge.nativeStopAudio()
        val engine = SynthEngine(context)
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        write(engine, "kit/padEnabled", 1.0)
        write(engine, "pad/qualitySize", 0.0)
        write(engine, "pad/qualityOctaves", 0.0)
        write(engine, "pad/qualitySamplesPerOctave", 0.0)
        assertTrue(engine.startPadGeneration(0, 0))
        await(engine, "done")
        write(engine, "pad/qualitySize", 6.0)
        write(engine, "pad/qualityOctaves", 7.0)
        write(engine, "pad/qualitySamplesPerOctave", 6.0)
        assertFalse(engine.startPadGeneration(0, 0))
        assertEquals("failed", engine.padGenerationStatus(0, 0).phase)
        assertTrue(engine.padGenerationStatus(0, 0).error.contains("128 MiB"))
        write(engine, "pad/qualityOctaves", 3.0)
        write(engine, "pad/qualitySamplesPerOctave", 2.0)
        assertTrue(engine.startPadGeneration(0, 0))
        write(engine, "pad/bandwidth", 777.0)
        await(engine, "cancelled")
        assertEquals(777.0, engine.parameterSnapshot(0, 0).values.first { it.descriptor.path == "pad/bandwidth" }.value, 0.0)
        write(engine, "pad/qualitySize", 0.0)
        assertTrue(engine.startPadGeneration(0, 0))
        engine.cancelPadGeneration() // Same operation used when closing/changing the editor context.
        await(engine, "cancelled")
        assertTrue(engine.startPadGeneration(0, 0))
        await(engine, "done")
        // A job for a second kit must never replace the first kit's result.
        write(engine, "part/kitMode", 1.0)
        write(engine, "kit/enabled", 1.0, 2)
        write(engine, "kit/padEnabled", 1.0, 2)
        write(engine, "pad/qualitySize", 0.0, 2)
        write(engine, "pad/qualityOctaves", 0.0, 2)
        write(engine, "pad/qualitySamplesPerOctave", 0.0, 2)
        assertTrue(engine.startPadGeneration(0, 2))
        engine.cancelPadGeneration()
        await(engine, "cancelled", 2)
        assertEquals("done", engine.padGenerationStatus(0, 0).phase)
    }
}
