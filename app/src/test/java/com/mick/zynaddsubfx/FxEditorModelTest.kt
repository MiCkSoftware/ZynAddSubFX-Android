package com.mick.zynaddsubfx

import org.junit.Assert.*
import org.junit.Test

class FxEditorModelTest {
    private val amount = SynthEngine.ParameterValue(SynthEngine.ParameterDescriptor(
        "fx/parameter/0", "Dry/Wet", "Mix", SynthEngine.ParameterType.INTEGER,
        0.0, 127.0, 64.0, emptyList(), SynthEngine.PreferredControl.KNOB), 64.0)
    private class FakeFx(private val parameter: SynthEngine.ParameterValue) : FxEngine {
        var reads = 0
        var previews = 0
        var writes = 0
        var value = parameter.value
        var accepted = true
        override fun fxSnapshot(address: FxAddress): FxSnapshot {
            reads++
            return FxSnapshot(address, 7, 0, reads.toLong(), -1, 120.0, listOf(parameter.copy(value = value)))
        }
        override fun writeFx(snapshot: FxSnapshot, parameter: SynthEngine.ParameterValue, value: Double): Boolean {
            writes++
            if (accepted) this.value = value
            return accepted
        }
        override fun fxAction(snapshot: FxSnapshot, action: String, vowel: Int) = true
        override fun fxPreview(address: FxAddress, vowel: Int): PreviewSeries { previews++; return PreviewSeries.Empty }
        override fun clipboardType() = ""
    }

    @Test fun continuousWritesOnlyReconcileAtCommit() {
        val engine = FakeFx(amount)
        val model = FxEditorModel(engine, FxAddress())
        model.open()
        for (value in 65..110) model.write(amount, value.toDouble(), continuous = true)
        assertEquals(46, engine.writes)
        assertEquals(1, engine.reads)
        assertEquals(1, engine.previews)
        assertEquals(110.0, model.value(amount), 0.0)
        assertTrue(model.dragging)
        model.commit()
        assertEquals(2, engine.reads)
        assertEquals(2, engine.previews)
        assertFalse(model.dragging)
        assertEquals(110.0, model.snapshot!!.parameters.single().value, 0.0)
        model.commit()
        assertEquals(2, engine.reads)
    }

    @Test fun refusedWritesRestoreConfirmedValue() {
        val engine = FakeFx(amount)
        val model = FxEditorModel(engine, FxAddress())
        model.open()
        engine.accepted = false
        model.write(amount, 100.0, continuous = true)
        assertEquals(64.0, model.value(amount), 0.0)
        assertNotNull(model.error)
        assertFalse(model.dirty)
    }

    @Test fun scopeAndSlotHaveCanonicalOwners() {
        assertEquals(5, FxAddress(FxScope.PART, 5, 2).nativePart)
        assertEquals(-1, FxAddress(FxScope.INSERT, 5, 7).nativePart)
        assertEquals(-1, FxAddress(FxScope.SYSTEM, 5, 3).nativePart)
        assertThrows(IllegalArgumentException::class.java) { FxAddress(FxScope.PART, 0, 3) }
        assertThrows(IllegalArgumentException::class.java) { FxAddress(FxScope.SYSTEM, 0, 4) }
    }

    @Test fun typeKnobChangesOnlyReachNativeAtCommit() {
        val type = amount.copy(descriptor = amount.descriptor.copy(path = "fx/type", type = SynthEngine.ParameterType.ENUM,
            maximum = 10.0, options = listOf("Off", "Reverb")), value = 0.0)
        val engine = FakeFx(type)
        val model = FxEditorModel(engine, FxAddress())
        model.open()
        model.write(type, 1.0, continuous = true)
        model.write(type, 2.0, continuous = true)
        assertEquals(0, engine.writes)
        assertEquals(1, engine.reads)
        assertEquals(2.0, model.value(type), 0.0)
        model.commit()
        assertEquals(1, engine.writes)
        assertEquals(2, engine.reads)
        assertFalse(model.dragging)
    }

    @Test fun returningToOriginalPresetDoesNotReapplyIt() {
        val preset = amount.copy(descriptor = amount.descriptor.copy(path = "fx/preset",
            type = SynthEngine.ParameterType.ENUM), value = 0.0)
        val engine = FakeFx(preset)
        val model = FxEditorModel(engine, FxAddress())
        model.open()
        model.write(preset, 2.0, continuous = true)
        model.write(preset, 0.0, continuous = true)
        model.commit()
        assertEquals(0, engine.writes)
        assertFalse(model.dirty)
    }

    @Test fun timingAndEqRespectNativeCoding() {
        val delay = amount.copy(descriptor = amount.descriptor.copy(path = "fx/parameter/2"), value = 127.0)
        assertEquals("4000.00 ms", fxValueText(10, delay))
        val frequency = amount.copy(descriptor = amount.descriptor.copy(path = "fx/parameter/11"), value = 64.0)
        assertEquals("600.00 Hz", fxValueText(7, frequency))
        val off = amount.copy(descriptor = amount.descriptor.copy(path = "fx/parameter/10"), value = 0.0)
        assertFalse(fxControlEnabled(7, frequency, listOf(off, frequency)))
        assertTrue(fxControlEnabled(7, off, listOf(off, frequency)))
        val peak = off.copy(value = 7.0)
        assertTrue(fxControlEnabled(7, frequency, listOf(peak, frequency)))
    }
}
