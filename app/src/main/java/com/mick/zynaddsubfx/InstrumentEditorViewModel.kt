package com.mick.zynaddsubfx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel

data class InstrumentEditorState(
    val partIndex: Int = 0,
    val kitIndex: Int = 0,
    val selectedEngine: String = "Part",
    val selectedTab: String = "Global",
    val selectedVoice: Int = 0,
    val snapshot: SynthEngine.ParameterSnapshot? = null,
    val dirty: Boolean = false,
    val revision: Long = 0,
    val operation: SynthEngine.OperationState = SynthEngine.OperationState.Idle,
)

interface InstrumentEditorEngine {
    fun parameterSnapshot(partIndex: Int, kitIndex: Int): SynthEngine.ParameterSnapshot
    fun writeParameter(partIndex: Int, kitIndex: Int, write: SynthEngine.ParameterWrite): Boolean
    fun preview(partIndex: Int, kitIndex: Int, address: ModuleAddress, resolution: Int = 128): PreviewSeries
    fun copyModule(partIndex: Int, kitIndex: Int, address: ModuleAddress): Boolean
    fun canPasteModule(address: ModuleAddress): Boolean
    fun pasteModule(partIndex: Int, kitIndex: Int, address: ModuleAddress): Boolean
}

class InstrumentEditorViewModel(private val engine: InstrumentEditorEngine) : ViewModel() {
    companion object {
        fun tabsFor(engineName: String): List<String> = when (engineName) {
            "ADD" -> listOf("Frequency", "Amp", "Filter", "Voices", "Resonance")
            "SUB" -> listOf("Global", "Amp", "Frequency", "Filter", "Harmonics")
            "PAD" -> listOf("Global", "Amp", "Frequency", "Filter", "Profile", "Spectrum", "Quality")
            "FX" -> listOf("Routing", "FX 1", "FX 2", "FX 3")
            else -> listOf("Global")
        }
    }

    var state by mutableStateOf(InstrumentEditorState())
        private set

    // Only touched parameters are retained during a gesture; the native engine owns the values.
    private val dragValues = mutableStateMapOf<String, Double>()

    private var snapshotValues = emptyMap<String, SynthEngine.ParameterValue>()

    fun parameterValue(parameter: SynthEngine.ParameterValue): Double =
        dragValues[parameter.descriptor.path] ?: snapshotValues[parameter.descriptor.path]?.value ?: parameter.value

    private fun readSnapshot(partIndex: Int, kitIndex: Int): SynthEngine.ParameterSnapshot {
        val snapshot = engine.parameterSnapshot(partIndex, kitIndex)
        snapshotValues = snapshot.values.associateBy { it.descriptor.path }
        dragValues.clear()
        return snapshot
    }

    fun open(partIndex: Int, kitIndex: Int = state.kitIndex) {
        val safePart = partIndex.coerceIn(0, 15)
        val safeKit = kitIndex.coerceIn(0, 15)
        state = state.copy(
            partIndex = safePart,
            kitIndex = safeKit,
            revision = state.revision + 1,
            snapshot = readSnapshot(safePart, safeKit),
            operation = SynthEngine.OperationState.Idle,
        )
    }

    fun selectKit(kitIndex: Int) = open(state.partIndex, kitIndex)

    fun selectEngine(engineName: String) {
        finishParameterDrag()
        state = state.copy(
            selectedEngine = engineName,
            selectedTab = tabsFor(engineName).first(),
        )
    }

    fun selectTab(tab: String) {
        finishParameterDrag()
        state = state.copy(selectedTab = tab)
    }

    fun selectVoice(voice: Int) {
        finishParameterDrag()
        state = state.copy(selectedVoice = voice.coerceIn(0, 7))
    }

    fun write(parameter: SynthEngine.ParameterValue, value: Double, refresh: Boolean = true) {
        val descriptor = parameter.descriptor
        val normalized = when (descriptor.type) {
            SynthEngine.ParameterType.BOOLEAN -> if (value >= 0.5) 1.0 else 0.0
            else -> value.roundToInt().toDouble().coerceIn(descriptor.minimum, descriptor.maximum)
        }
        if (parameterValue(parameter) == normalized) {
            if (refresh) finishParameterDrag()
            return
        }
        if (!engine.writeParameter(
                state.partIndex,
                state.kitIndex,
                SynthEngine.ParameterWrite(descriptor.path, normalized)
            )
        ) {
            state = state.copy(operation = SynthEngine.OperationState.Failed("Could not update ${descriptor.label}"))
            return
        }
        dragValues[descriptor.path] = normalized
        if (refresh) {
            state = state.copy(
                snapshot = readSnapshot(state.partIndex, state.kitIndex),
                dirty = true,
                revision = state.revision + 1,
                operation = SynthEngine.OperationState.Idle,
            )
        } else if (!state.dirty || state.operation != SynthEngine.OperationState.Idle) {
            state = state.copy(dirty = true, operation = SynthEngine.OperationState.Idle)
        }
    }

    fun commitEdits() {
        state = state.copy(
            snapshot = readSnapshot(state.partIndex, state.kitIndex),
            revision = state.revision + 1,
        )
    }

    fun dragParameter(parameter: SynthEngine.ParameterValue, value: Double) =
        write(parameter, value, refresh = false)

    fun finishParameterDrag() {
        if (dragValues.isNotEmpty()) commitEdits()
    }

    fun commitResonanceCurve(original: List<SynthEngine.ParameterValue>, values: List<Int>) {
        if (original.size != values.size) return
        var changed = false
        for (index in values.indices) {
            val point = original[index]
            val value = values[index].toDouble()
            if (point.value == value) continue
            if (!engine.writeParameter(
                    state.partIndex,
                    state.kitIndex,
                    SynthEngine.ParameterWrite(point.descriptor.path, value),
                )
            ) {
                state = state.copy(operation = SynthEngine.OperationState.Failed("Could not update resonance"))
                return
            }
            changed = true
        }
        if (changed) {
            state = state.copy(
                snapshot = readSnapshot(state.partIndex, state.kitIndex),
                dirty = true,
                revision = state.revision + 1,
                operation = SynthEngine.OperationState.Idle,
            )
        }
    }

    fun performPath(path: String, value: Double = 1.0) {
        if (engine.writeParameter(
                state.partIndex,
                state.kitIndex,
                SynthEngine.ParameterWrite(path, value),
            )
        ) {
            state = state.copy(
                snapshot = readSnapshot(state.partIndex, state.kitIndex),
                dirty = true,
                revision = state.revision + 1,
                operation = SynthEngine.OperationState.Idle,
            )
        } else {
            state = state.copy(operation = SynthEngine.OperationState.Failed("Could not update module"))
        }
    }

    fun preview(address: ModuleAddress, resolution: Int = 128): PreviewSeries =
        engine.preview(state.partIndex, state.kitIndex, address, resolution)

    fun copyModule(address: ModuleAddress) {
        state = state.copy(operation = if (engine.copyModule(state.partIndex, state.kitIndex, address)) {
            SynthEngine.OperationState.Succeeded
        } else SynthEngine.OperationState.Failed("Could not copy module"))
    }

    fun canPasteModule(address: ModuleAddress): Boolean = engine.canPasteModule(address)

    fun pasteModule(address: ModuleAddress) {
        if (!engine.pasteModule(state.partIndex, state.kitIndex, address)) {
            state = state.copy(operation = SynthEngine.OperationState.Failed("Clipboard type is not compatible"))
            return
        }
        state = state.copy(
            snapshot = readSnapshot(state.partIndex, state.kitIndex),
            dirty = true,
            revision = state.revision + 1,
            operation = SynthEngine.OperationState.Succeeded,
        )
    }

    fun reset(parameter: SynthEngine.ParameterValue) =
        write(parameter, parameter.descriptor.defaultValue)

    fun writePath(path: String, value: Double) {
        val parameter = state.snapshot?.values?.firstOrNull { it.descriptor.path == path }
        if (parameter != null) {
            write(parameter, value)
        } else if (engine.writeParameter(
                state.partIndex,
                state.kitIndex,
                SynthEngine.ParameterWrite(path, value)
            )
        ) {
            state = state.copy(
                snapshot = readSnapshot(state.partIndex, state.kitIndex),
                dirty = true,
                revision = state.revision + 1,
                operation = SynthEngine.OperationState.Idle,
            )
        } else {
            state = state.copy(operation = SynthEngine.OperationState.Failed("Could not update resonance"))
        }
    }

    fun beginExport() {
        state = state.copy(operation = SynthEngine.OperationState.Running)
    }

    fun finishExport(success: Boolean, exportedRevision: Long) {
        state = if (success) {
            state.copy(
                dirty = state.dirty && state.revision != exportedRevision,
                operation = SynthEngine.OperationState.Succeeded,
            )
        } else {
            state.copy(operation = SynthEngine.OperationState.Failed("Instrument export failed"))
        }
    }

}
