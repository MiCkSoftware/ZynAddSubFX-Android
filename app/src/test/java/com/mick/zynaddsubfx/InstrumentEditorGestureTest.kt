package com.mick.zynaddsubfx

import org.junit.Assert.*
import org.junit.Test

class InstrumentEditorGestureTest {
    private class Engine : InstrumentEditorEngine {
        val parameters = (0 until 4096).map { index ->
            SynthEngine.ParameterValue(
                SynthEngine.ParameterDescriptor(
                    "add/voice/0/osc/harmonic/$index/magnitude", "Magnitude", "Harmonics",
                    SynthEngine.ParameterType.INTEGER, 0.0, 127.0, 64.0, emptyList(),
                    SynthEngine.PreferredControl.KNOB,
                ), 64.0,
            )
        }
        val values = parameters.associate { it.descriptor.path to it.value }.toMutableMap()
        var snapshots = 0
        var writes = 0
        var previews = 0
        var failWrites = false
        override fun parameterSnapshot(partIndex: Int, kitIndex: Int): SynthEngine.ParameterSnapshot {
            snapshots++
            return SynthEngine.ParameterSnapshot(partIndex, kitIndex,
                parameters.map { it.copy(value = values.getValue(it.descriptor.path)) })
        }
        override fun writeParameter(partIndex: Int, kitIndex: Int, write: SynthEngine.ParameterWrite): Boolean {
            writes++
            if (failWrites) return false
            values[write.path] = write.value
            return true
        }
        override fun preview(partIndex: Int, kitIndex: Int, address: ModuleAddress, resolution: Int): PreviewSeries {
            previews++
            return PreviewSeries.Empty
        }
        override fun copyModule(partIndex: Int, kitIndex: Int, address: ModuleAddress) = true
        override fun canPasteModule(address: ModuleAddress) = true
        override fun pasteModule(partIndex: Int, kitIndex: Int, address: ModuleAddress) = true
    }

    @Test fun longGestureKeepsSnapshotAndRevisionUntilSingleReconciliation() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        val snapshot = model.state.snapshot
        val revision = model.state.revision
        repeat(1000) { model.dragParameter(engine.parameters[0], (it % 128).toDouble()) }
        assertSame(snapshot, model.state.snapshot)
        assertEquals(1, engine.snapshots)
        assertEquals(1000, engine.writes)
        assertEquals(0, engine.previews)
        assertEquals(revision, model.state.revision)
        assertEquals(103.0, model.parameterValue(engine.parameters[0]), 0.0)
        assertTrue(model.state.dirty)
        model.finishParameterDrag()
        model.finishParameterDrag() // End/cancel/dispose callbacks may overlap.
        assertEquals(2, engine.snapshots)
        assertEquals(revision + 1, model.state.revision)
        assertEquals(103.0, model.state.snapshot!!.values[0].value, 0.0)
        println("1000 moves: writes=${engine.writes}, snapshots during=0, at finish=1, previews=${engine.previews}")
    }

    @Test fun returningToOriginalValueIsWrittenAndDuplicatesAreSkipped() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        val parameter = engine.parameters[0]
        model.dragParameter(parameter, 80.1)
        model.dragParameter(parameter, 80.2)
        model.dragParameter(parameter, 64.0)
        assertEquals(2, engine.writes)
        model.finishParameterDrag()
        assertEquals(64.0, model.parameterValue(parameter), 0.0)
    }

    @Test fun revisitingHarmonicDuringGestureRetainsEveryTouchedValue() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        model.dragParameter(engine.parameters[0], 90.0)
        model.dragParameter(engine.parameters[1], 20.0)
        assertEquals(90.0, model.parameterValue(engine.parameters[0]), 0.0)
        model.dragParameter(engine.parameters[0], model.parameterValue(engine.parameters[0]) + 1)
        model.finishParameterDrag() // Cancellation accepts the last live audio write, like TinyKnob.
        assertEquals(91.0, model.state.snapshot!!.values[0].value, 0.0)
        assertEquals(20.0, model.state.snapshot!!.values[1].value, 0.0)
        assertEquals(2, engine.snapshots)
    }

    @Test fun voiceAndKitChangesDoNotLoseLiveWrites() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        model.dragParameter(engine.parameters[0], 90.0)
        model.selectVoice(1)
        assertEquals(90.0, model.state.snapshot!!.values[0].value, 0.0)
        model.dragParameter(engine.parameters[0], 80.0)
        model.selectKit(1)
        val snapshots = engine.snapshots
        model.finishParameterDrag()
        assertEquals(snapshots, engine.snapshots)
        assertEquals(80.0, engine.values[engine.parameters[0].descriptor.path]!!, 0.0)
    }

    @Test fun discreteEditsAndResetUseLatestSnapshotEvenWithAnOlderDescriptor() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        val parameter = engine.parameters[0]
        model.write(parameter, 100.0)
        model.write(parameter, 64.0)
        assertEquals(64.0, model.state.snapshot!!.values[0].value, 0.0)
        model.dragParameter(parameter, 90.0)
        model.reset(parameter)
        assertEquals(64.0, model.parameterValue(parameter), 0.0)
        val snapshots = engine.snapshots
        model.finishParameterDrag()
        assertEquals(snapshots, engine.snapshots)
    }

    @Test fun failedWriteDoesNotReplaceLastAcceptedValue() {
        val engine = Engine()
        val model = InstrumentEditorViewModel(engine)
        model.open(0)
        model.dragParameter(engine.parameters[0], 80.0)
        engine.failWrites = true
        model.dragParameter(engine.parameters[0], 90.0)
        assertEquals(80.0, model.parameterValue(engine.parameters[0]), 0.0)
        assertTrue(model.state.operation is SynthEngine.OperationState.Failed)
        model.finishParameterDrag()
        assertEquals(80.0, model.state.snapshot!!.values[0].value, 0.0)
    }
}
