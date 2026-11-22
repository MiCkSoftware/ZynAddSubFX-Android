package com.mick.zynaddsubfx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var engine: SynthEngine

    @Before
    fun initialize() {
        NativeSynthBridge.nativeStopAudio()
        assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
        engine = SynthEngine(context)
    }

    private fun values(part: Int, kit: Int = 0) =
        engine.parameterSnapshot(part, kit).values.associate { it.descriptor.path to it.value }

    private fun write(part: Int, kit: Int, path: String, value: Double) =
        assertTrue(path, engine.writeParameter(part, kit, SynthEngine.ParameterWrite(path, value)))

    private fun writeFx(address: FxAddress, path: String, value: Double) {
        val slot = checkNotNull(engine.fxSnapshot(address))
        assertTrue("$address $path", engine.writeFx(slot, checkNotNull(slot.parameter(path)), value))
    }

    @Test
    fun packagedSparsePresetsReplaceEarlierSubAndPadParameters() {
        val fixtures = listOf(
            "0018-Strano_02.xiz" to listOf(0.0, 1.0, 0.0),
            "0027-beepB.xiz" to listOf(0.0, 0.0, 1.0),
            "0013-Passa_asteroide.xiz" to listOf(1.0, 1.0, 1.0),
        )
        for ((file, flags) in fixtures) {
            val path = "presets/banks/Cormi_Noise/$file"
            assertTrue(path, engine.loadPackagedPreset(path, 1))
            val expected = values(1).filterKeys { it.startsWith("sub/") || it.startsWith("pad/") || it.startsWith("kit/") }
            assertEquals(path, flags, listOf("add", "sub", "pad").map { expected.getValue("kit/${it}Enabled") })
            for (synth in listOf("sub", "pad")) {
                write(0, 0, "kit/${synth}Enabled", 1.0)
                val old = expected["$synth/volume"]
                write(0, 0, "$synth/volume", if (old == 31.0) 92.0 else 31.0)
            }
            assertTrue(path, engine.loadPackagedPreset(path, 0))
            val loaded = engine.parameterSnapshot(0, 0).values.associateBy { it.descriptor.path }
            for ((field, value) in expected) assertEquals("$path $field", value, loaded[field]?.value)
            for (synth in listOf("sub", "pad")) {
                val field = "$synth/volume"
                if (field !in expected) {
                    loaded[field]?.let { hidden ->
                        assertEquals("$path $field", hidden.descriptor.defaultValue, hidden.value, 0.0)
                    }
                }
            }
        }
    }

    @Test
    fun xizRoundTripRetainsMultipleKitsTheirRoutingAndPartEq() {
        val eq = FxAddress(FxScope.PART, 0, 0)
        write(0, 0, "part/kitMode", 1.0)
        write(0, 2, "kit/enabled", 1.0)
        write(0, 0, "kit/subEnabled", 1.0)
        write(0, 0, "kit/padEnabled", 1.0)
        write(0, 0, "pad/qualitySize", 0.0)
        write(0, 0, "pad/qualityOctaves", 0.0)
        write(0, 0, "pad/qualitySamplesPerOctave", 0.0)
        write(0, 0, "sub/volume", 43.0)
        write(0, 0, "pad/volume", 71.0)
        write(0, 2, "kit/sendFx", 3.0)
        for (synth in listOf("sub", "pad")) {
            write(0, 2, "kit/${synth}Enabled", 1.0)
            write(0, 2, "$synth/volume", 81.0)
            write(0, 2, "kit/${synth}Enabled", 0.0)
        }
        writeFx(eq, "fx/type", 7.0)
        writeFx(eq, "fx/preset", 8.0)
        writeFx(eq, "fx/parameter/12", 78.0)
        writeFx(eq, "fx/destination", 1.0)
        val curve = engine.fxPreview(eq)
        val file = File.createTempFile("mixed-kits", ".xiz", context.cacheDir)
        try {
            assertTrue(engine.exportInstrument(0, file))
            assertTrue(engine.loadInstrument(file, 1))
            assertEquals(43.0, values(1).getValue("sub/volume"), 0.0)
            assertEquals(71.0, values(1).getValue("pad/volume"), 0.0)
            assertEquals("done", engine.padGenerationStatus(1, 0).phase)
            assertEquals(1.0, values(1, 2).getValue("kit/enabled"), 0.0)
            val secondKit = values(1, 2)
            assertEquals(3.0, secondKit.getValue("kit/sendFx"), 0.0)
            for (synth in listOf("sub", "pad")) {
                assertEquals(0.0, secondKit.getValue("kit/${synth}Enabled"), 0.0)
                assertEquals(81.0, secondKit.getValue("$synth/volume"), 0.0)
            }
            val restored = eq.copy(part = 1)
            assertEquals(8, engine.fxSnapshot(restored)?.preset)
            assertEquals(1.0, engine.fxSnapshot(restored)?.parameter("fx/destination")?.value)
            assertEquals(curve, engine.fxPreview(restored))
        } finally {
            file.delete()
        }
    }

    @Test
    fun subEnvelopeClipboardTargetsOnlyTheSelectedPadKit() {
        write(0, 0, "kit/subEnabled", 1.0)
        write(1, 0, "part/kitMode", 1.0)
        write(1, 2, "kit/enabled", 1.0)
        write(1, 2, "kit/padEnabled", 1.0)
        write(1, 0, "kit/padEnabled", 1.0)
        val source = ModuleAddress.Envelope(envelopeRole = EnvelopeRole.AMPLITUDE, engine = ModuleEngine.SUB)
        val target = source.copy(engine = ModuleEngine.PAD)
        write(0, 0, "sub/ampEnvelope/attackTime", 43.0)
        assertTrue(engine.copyModule(0, 0, source))
        val previous = values(1, 2)
        val otherKit = values(1)
        assertFalse(engine.pasteModule(1, 2, ModuleAddress.Filter(engine = ModuleEngine.PAD)))
        assertEquals(previous, values(1, 2))
        assertTrue(engine.pasteModule(1, 2, target))
        val updated = values(1, 2)
        assertEquals(43.0, updated.getValue("pad/ampEnvelope/attackTime"), 0.0)
        assertEquals(
            previous.filterKeys { !it.startsWith("pad/ampEnvelope/") },
            updated.filterKeys { !it.startsWith("pad/ampEnvelope/") },
        )
        assertEquals(otherKit, values(1))
    }

    @Test
    fun sessionCaptureRestoresFxRoutingAndInactiveEngineEditsFromBeforeLaterWrites() {
        val insert = FxAddress(FxScope.INSERT, 0, 0)
        val system = FxAddress(FxScope.SYSTEM, 0, 0)
        writeFx(insert, "fx/type", 7.0)
        writeFx(insert, "fx/preset", 9.0)
        writeFx(insert, "fx/source", 2.0)
        writeFx(system, "fx/type", 2.0)
        writeFx(system, "fx/send/1", 53.0)
        assertTrue(engine.setSystemFxSend(0, 0, 76))
        for (synth in listOf("sub", "pad")) {
            write(1, 0, "kit/${synth}Enabled", 1.0)
            write(1, 0, "$synth/volume", 71.0)
            write(1, 0, "kit/${synth}Enabled", 0.0)
        }
        val file = File.createTempFile("captured-fx", ".xmz", context.cacheDir)
        try {
            val handle = NativeSynthBridge.nativeCaptureSession()
            assertTrue(handle != 0L)
            val writer = FutureTask { NativeSynthBridge.nativeWriteSession(handle, file.absolutePath) }
            Thread(writer).start()
            writeFx(insert, "fx/preset", 2.0)
            writeFx(system, "fx/send/1", 10.0)
            assertEquals(2, engine.fxSnapshot(insert)?.preset)
            assertTrue(writer.get(30, TimeUnit.SECONDS))
            assertTrue(NativeSynthBridge.nativeInit(48_000, 256))
            assertTrue(engine.loadInstrument(file, 0))
            assertEquals(9, engine.fxSnapshot(insert)?.preset)
            assertEquals(2.0, engine.fxSnapshot(insert)?.parameter("fx/source")?.value)
            assertEquals(53.0, engine.fxSnapshot(system)?.parameter("fx/send/1")?.value)
            assertEquals(76, engine.inspectMixer().systemSends.single { it.partIndex == 0 && it.systemFxSlot == 0 }.sendValue)
            for (synth in listOf("sub", "pad")) {
                assertEquals(0.0, values(1).getValue("kit/${synth}Enabled"), 0.0)
                assertEquals(71.0, values(1).getValue("$synth/volume"), 0.0)
            }
        } finally {
            file.delete()
        }
    }
}
