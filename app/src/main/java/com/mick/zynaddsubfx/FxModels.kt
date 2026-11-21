package com.mick.zynaddsubfx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Locale

enum class FxScope(val code: Int, val slots: Int, val label: String) {
    PART(0, 3, "Part"), INSERT(1, 8, "Insert"), SYSTEM(2, 4, "System"),
}

data class FxAddress(val scope: FxScope = FxScope.PART, val part: Int = 0, val slot: Int = 0) {
    init { require(part in 0..15 && slot in 0 until scope.slots) }
    val nativePart get() = if (scope == FxScope.PART) part else -1
    val context get() = if (scope == FxScope.PART) "Part ${part + 1} / FX ${slot + 1}" else "Master / ${scope.label} ${slot + 1}"
}

data class FxSnapshot(
    val address: FxAddress,
    val type: Int,
    val preset: Int,
    val revision: Long,
    val assignedPart: Int,
    val tempo: Double,
    val parameters: List<SynthEngine.ParameterValue>,
) {
    fun parameter(path: String) = parameters.firstOrNull { it.descriptor.path == path }
    val typeName get() = parameter("fx/type")?.descriptor?.options?.getOrNull(type) ?: "Off"
}

interface FxEngine {
    fun fxSnapshot(address: FxAddress): FxSnapshot?
    fun writeFx(snapshot: FxSnapshot, parameter: SynthEngine.ParameterValue, value: Double): Boolean
    fun fxAction(snapshot: FxSnapshot, action: String, vowel: Int = 0): Boolean
    fun fxPreview(address: FxAddress, vowel: Int = 0): PreviewSeries
    fun clipboardType(): String
}

// Reduced gesture state; structure and confirmed values are owned by the native slot.
class FxEditorModel(private val engine: FxEngine, initial: FxAddress) {
    var snapshot by mutableStateOf<FxSnapshot?>(null)
        private set
    var address by mutableStateOf(initial)
        private set
    var preview by mutableStateOf(PreviewSeries.Empty)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var clipboard by mutableStateOf("")
        private set
    var dirty by mutableStateOf(false)
        private set
    val dragging get() = drafts.isNotEmpty()
    private val drafts = mutableStateMapOf<String, Double>()
    private var pendingChoice: SynthEngine.ParameterValue? = null

    fun open(next: FxAddress = address) {
        if (drafts.isNotEmpty()) commit()
        address = next
        error = null
        refresh()
    }

    fun refresh() {
        drafts.clear()
        pendingChoice = null
        snapshot = engine.fxSnapshot(address)
        preview = if (snapshot?.type in setOf(7, 8)) engine.fxPreview(address) else PreviewSeries.Empty
        clipboard = engine.clipboardType()
        if (snapshot == null) error = "Could not read ${address.context}"
    }

    fun value(parameter: SynthEngine.ParameterValue) = drafts[parameter.descriptor.path] ?: parameter.value

    fun write(parameter: SynthEngine.ParameterValue, value: Double, continuous: Boolean = false) {
        val current = snapshot ?: return
        val normalized = value.roundToInt().toDouble().coerceIn(parameter.descriptor.minimum, parameter.descriptor.maximum)
        if (this.value(parameter) == normalized) { if (!continuous) commit(); return }
        if (continuous && parameter.descriptor.path in setOf("fx/type", "fx/preset", "fx/source", "fx/destination")) {
            pendingChoice = parameter.copy(value = normalized)
            drafts[parameter.descriptor.path] = normalized
            return
        }
        if (!engine.writeFx(current, parameter, normalized)) {
            error = "Could not update ${parameter.descriptor.label}"
            refresh()
            return
        }
        drafts[parameter.descriptor.path] = normalized
        dirty = true
        error = null
        // A type, preset or mode can change the descriptors/applicability of multiple fields.
        if (!continuous || parameter.descriptor.type != SynthEngine.ParameterType.INTEGER) commit()
    }

    fun commit() {
        pendingChoice?.let { choice ->
            val current = snapshot ?: return
            pendingChoice = null
            if (current.parameter(choice.descriptor.path)?.value != choice.value) {
                if (engine.writeFx(current, choice, choice.value)) {
                    dirty = true
                    error = null
                } else error = "Could not update ${choice.descriptor.label}"
            }
        }
        if (drafts.isNotEmpty()) refresh()
    }

    fun action(action: String, vowel: Int = 0) {
        commit()
        val current = snapshot ?: return
        if (engine.fxAction(current, action, vowel)) {
            error = null
            if (!action.startsWith("copy")) dirty = true
        } else error = if (action.startsWith("copy")) "Could not copy effect data" else if (action.startsWith("paste")) "Could not paste effect data" else "Could not reset effect"
        refresh()
    }
}

internal fun fxControlEnabled(type: Int, parameter: SynthEngine.ParameterValue, values: List<SynthEngine.ParameterValue>): Boolean {
    fun p(index: Int) = values.firstOrNull { it.descriptor.path == "fx/parameter/$index" }?.value?.toInt() ?: 0
    val path = parameter.descriptor.path
    val index = path.removePrefix("fx/parameter/").toIntOrNull() ?: return true
    if (index == 2 && type in setOf(2, 3, 4, 5, 8, 10) && values.firstOrNull { it.descriptor.path == "fx/numerator" }?.value != 0.0) return false
    return when (type) {
        1 -> index != 12 || p(10) == 2
        3 -> index !in 2..7 || p(10) != 1
        4 -> index !in setOf(12, 13) || p(14) != 0
        7 -> if (index < 10) true else {
            val base = 10 + (index - 10) / 5 * 5
            val bandType = p(base)
            index == base || (bandType != 0 && (index != base + 2 || bandType in 7..9))
        }
        else -> true
    }
}

internal fun fxValueText(type: Int, parameter: SynthEngine.ParameterValue): String {
    val n = parameter.descriptor.path.removePrefix("fx/parameter/").toIntOrNull()
    val v = parameter.value
    fun number(value: Double, unit: String) = String.format(Locale.ROOT, "%.2f %s", value, unit)
    return when {
        parameter.descriptor.options.isNotEmpty() -> parameter.descriptor.options.getOrNull(v.toInt()) ?: v.toInt().toString()
        type == 7 && n != null && n >= 10 -> when ((n - 10) % 5) {
            1 -> number(600.0 * 30.0.pow((v - 64.0) / 64.0), "Hz")
            2 -> number(30.0 * (v - 64.0) / 64.0, "dB")
            3 -> number(30.0.pow((v - 64.0) / 64.0), "Q")
            else -> v.toInt().toString()
        }
        n == 2 && type in setOf(3, 4, 5, 8) -> number((2.0.pow(v / 127.0 * 10.0) - 1.0) * .03, "Hz")
        n == 2 && type == 2 -> number(v / 127.0 * 1500.0, "ms")
        n == 2 && type == 10 -> number((v + 1.0) / 128.0 * 4000.0, "ms")
        n == 5 && type == 10 -> number((v + 1.0) * 10.0, "ms")
        else -> v.toInt().toString()
    }
}
