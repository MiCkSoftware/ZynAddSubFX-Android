package com.mick.zynaddsubfx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

class InstrumentEditorViewModel(private val engine: SynthEngine) : ViewModel() {
    var state by mutableStateOf(InstrumentEditorState())
        private set

    fun open(partIndex: Int, kitIndex: Int = state.kitIndex) {
        val safePart = partIndex.coerceIn(0, 15)
        val safeKit = kitIndex.coerceIn(0, 15)
        state = state.copy(
            partIndex = safePart,
            kitIndex = safeKit,
            snapshot = engine.parameterSnapshot(safePart, safeKit),
            operation = SynthEngine.OperationState.Idle,
        )
    }

    fun selectKit(kitIndex: Int) = open(state.partIndex, kitIndex)

    fun selectEngine(engineName: String) {
        state = state.copy(
            selectedEngine = engineName,
            selectedTab = tabsFor(engineName).first(),
        )
    }

    fun selectTab(tab: String) {
        state = state.copy(selectedTab = tab)
    }

    fun selectVoice(voice: Int) {
        state = state.copy(selectedVoice = voice.coerceIn(0, 7))
    }

    fun write(parameter: SynthEngine.ParameterValue, value: Double, refresh: Boolean = true) {
        val descriptor = parameter.descriptor
        val normalized = when (descriptor.type) {
            SynthEngine.ParameterType.BOOLEAN -> if (value >= 0.5) 1.0 else 0.0
            else -> value.coerceIn(descriptor.minimum, descriptor.maximum)
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
        val optimistic = state.snapshot?.copy(values = state.snapshot?.values.orEmpty().map {
            if (it.descriptor.path == descriptor.path) it.copy(value = normalized) else it
        })
        val refreshed = if (refresh) engine.parameterSnapshot(state.partIndex, state.kitIndex) else optimistic
        state = state.copy(
            snapshot = refreshed,
            dirty = true,
            revision = state.revision + 1,
            operation = SynthEngine.OperationState.Idle,
        )
    }

    fun commitEdits() {
        state = state.copy(snapshot = engine.parameterSnapshot(state.partIndex, state.kitIndex))
    }

    fun performPath(path: String, value: Double = 1.0) {
        if (engine.writeParameter(
                state.partIndex,
                state.kitIndex,
                SynthEngine.ParameterWrite(path, value),
            )
        ) {
            state = state.copy(
                snapshot = engine.parameterSnapshot(state.partIndex, state.kitIndex),
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
            snapshot = engine.parameterSnapshot(state.partIndex, state.kitIndex),
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
                snapshot = engine.parameterSnapshot(state.partIndex, state.kitIndex),
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

    companion object {
        fun tabsFor(engineName: String): List<String> = when (engineName) {
            "ADD" -> listOf("Amp", "Frequency", "Filter", "Voices", "Resonance")
            "SUB" -> listOf("Global", "Amp", "Frequency", "Filter", "Harmonics")
            "PAD" -> listOf("Global", "Amp", "Frequency", "Filter", "Profile", "Spectrum", "Quality")
            "FX" -> listOf("Routing", "FX 1", "FX 2", "FX 3")
            else -> listOf("Global")
        }
    }
}
