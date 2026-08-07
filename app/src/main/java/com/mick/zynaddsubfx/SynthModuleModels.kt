package com.mick.zynaddsubfx

sealed interface ModuleAddress {
    val kind: Int
    val index: Int
    val role: Int

    data class Voice(override val index: Int) : ModuleAddress {
        override val kind = 0
        override val role = 0
    }

    data class Oscillator(override val index: Int, val modulator: Boolean = false) : ModuleAddress {
        override val kind = 1
        override val role = if (modulator) 1 else 0
    }

    data class Envelope(override val index: Int = -1, val envelopeRole: EnvelopeRole) : ModuleAddress {
        override val kind = 2
        override val role = envelopeRole.code
    }

    data class Lfo(override val index: Int = -1, val lfoRole: LfoRole) : ModuleAddress {
        override val kind = 3
        override val role = lfoRole.code
    }

    data class Filter(override val index: Int = -1, val vowel: Int = 0) : ModuleAddress {
        override val kind = 4
        override val role = vowel.coerceIn(0, 5)
    }

    data object Resonance : ModuleAddress {
        override val kind = 5
        override val index = -1
        override val role = 0
    }

    data class Vowel(override val index: Int = -1, val vowel: Int) : ModuleAddress {
        override val kind = 6
        override val role = vowel.coerceIn(0, 5)
    }
}

enum class EnvelopeRole(val code: Int, val title: String) {
    AMPLITUDE(0, "Amplitude envelope"),
    FREQUENCY(1, "Frequency envelope"),
    FILTER(2, "Filter envelope"),
    MODULATOR_AMPLITUDE(3, "Modulator amplitude envelope"),
    MODULATOR_FREQUENCY(4, "Modulator frequency envelope"),
}

enum class LfoRole(val code: Int, val title: String) {
    AMPLITUDE(0, "Amplitude LFO"),
    FREQUENCY(1, "Frequency LFO"),
    FILTER(2, "Filter LFO"),
}

data class PreviewSeries(val values: List<Float>) {
    companion object {
        val Empty = PreviewSeries(emptyList())
    }
}

data class EnvelopePoint(
    val index: Int,
    val time: SynthEngine.ParameterValue,
    val value: SynthEngine.ParameterValue,
)

data class EnvelopeModel(
    val address: ModuleAddress.Envelope,
    val prefix: String,
    val title: String,
    val parameters: List<SynthEngine.ParameterValue>,
    val freeMode: Boolean,
    val linear: Boolean,
    val sustainPoint: Int?,
    val points: List<EnvelopePoint>,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? =
        parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }

    companion object {
        fun from(
            values: List<SynthEngine.ParameterValue>,
            address: ModuleAddress.Envelope,
        ): EnvelopeModel? {
            val prefix = when {
                address.index < 0 -> when (address.envelopeRole) {
                    EnvelopeRole.AMPLITUDE -> "add/ampEnvelope"
                    EnvelopeRole.FREQUENCY -> "add/freqEnvelope"
                    EnvelopeRole.FILTER -> "add/filterEnvelope"
                    else -> return null
                }
                else -> "add/voice/${address.index}/" + when (address.envelopeRole) {
                    EnvelopeRole.AMPLITUDE -> "ampEnvelope"
                    EnvelopeRole.FREQUENCY -> "freqEnvelope"
                    EnvelopeRole.FILTER -> "filterEnvelope"
                    EnvelopeRole.MODULATOR_AMPLITUDE -> "modAmpEnvelope"
                    EnvelopeRole.MODULATOR_FREQUENCY -> "modFreqEnvelope"
                }
            }
            val enablePath = if (address.index < 0) null else "add/voice/${address.index}/" + when (address.envelopeRole) {
                EnvelopeRole.AMPLITUDE -> "ampEnvelopeEnabled"
                EnvelopeRole.FREQUENCY -> "freqEnvelopeEnabled"
                EnvelopeRole.FILTER -> "filterEnvelopeEnabled"
                EnvelopeRole.MODULATOR_AMPLITUDE -> "modAmpEnvelopeEnabled"
                EnvelopeRole.MODULATOR_FREQUENCY -> "modFreqEnvelopeEnabled"
            }
            val parameters = values.filter {
                it.descriptor.path.startsWith("$prefix/") || it.descriptor.path == enablePath
            }
            if (parameters.isEmpty()) return null
            fun raw(field: String) = parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }?.value
            val count = raw("pointCount")?.toInt()?.coerceIn(0, 40) ?: 0
            val points = (0 until count).mapNotNull { point ->
                val time = parameters.firstOrNull { it.descriptor.path == "$prefix/point/$point/time" }
                val value = parameters.firstOrNull { it.descriptor.path == "$prefix/point/$point/value" }
                if (time == null || value == null) null else EnvelopePoint(point, time, value)
            }
            return EnvelopeModel(
                address = address,
                prefix = prefix,
                title = address.envelopeRole.title,
                parameters = parameters,
                freeMode = raw("freeMode")?.let { it >= .5 } == true,
                linear = raw("linear")?.let { it >= .5 } == true,
                sustainPoint = raw("sustainPoint")?.toInt()?.takeIf { it in points.indices },
                points = points,
            )
        }
    }
}

data class LfoModel(
    val address: ModuleAddress.Lfo,
    val prefix: String,
    val title: String,
    val parameters: List<SynthEngine.ParameterValue>,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? =
        parameters.firstOrNull { it.descriptor.path == "$prefix/$field" }

    companion object {
        fun from(values: List<SynthEngine.ParameterValue>, address: ModuleAddress.Lfo): LfoModel? {
            val prefix = if (address.index < 0) {
                "add/" + when (address.lfoRole) {
                    LfoRole.AMPLITUDE -> "ampLfo"
                    LfoRole.FREQUENCY -> "freqLfo"
                    LfoRole.FILTER -> "filterLfo"
                }
            } else {
                "add/voice/${address.index}/" + when (address.lfoRole) {
                    LfoRole.AMPLITUDE -> "ampLfo"
                    LfoRole.FREQUENCY -> "freqLfo"
                    LfoRole.FILTER -> "filterLfo"
                }
            }
            val enablePath = if (address.index < 0) null else "add/voice/${address.index}/" + when (address.lfoRole) {
                LfoRole.AMPLITUDE -> "ampLfoEnabled"
                LfoRole.FREQUENCY -> "freqLfoEnabled"
                LfoRole.FILTER -> "filterLfoEnabled"
            }
            val parameters = values.filter {
                it.descriptor.path.startsWith("$prefix/") || it.descriptor.path == enablePath
            }
            return parameters.takeIf { it.isNotEmpty() }?.let {
                LfoModel(address, prefix, address.lfoRole.title, it)
            }
        }
    }
}

data class FormantModel(
    val count: Int,
    val sequenceSize: Int,
    val parameters: List<SynthEngine.ParameterValue>,
)

data class FilterModel(
    val address: ModuleAddress.Filter,
    val prefix: String,
    val parameters: List<SynthEngine.ParameterValue>,
    val category: Int,
    val formant: FormantModel?,
) {
    fun parameter(field: String): SynthEngine.ParameterValue? = parameters.firstOrNull {
        it.descriptor.path == if (address.index < 0) "$prefix/$field" else "$prefix${field.replaceFirstChar(Char::uppercase)}"
    }

    companion object {
        fun from(values: List<SynthEngine.ParameterValue>, address: ModuleAddress.Filter): FilterModel? {
            val prefix = if (address.index < 0) "add/filter" else "add/voice/${address.index}/filter"
            val voiceFilterFields = setOf(
                "filter", "bypassGlobalFilter", "filterCategory", "filterType", "filterCutoff",
                "filterQ", "filterStages", "filterTracking", "filterGain", "filterCombHpf",
                "filterCombLpf", "filterVelocityAmount", "filterVelocity",
            )
            val parameters = values.filter { value ->
                val path = value.descriptor.path
                if (address.index < 0) {
                    path.startsWith("$prefix/") || path in setOf("add/filterVelocity", "add/filterVelocitySense")
                } else {
                    path.startsWith("$prefix/formant/") ||
                        path.substringAfter("add/voice/${address.index}/") in voiceFilterFields
                }
            }
            if (parameters.isEmpty()) return null
            val categoryPath = if (address.index < 0) "$prefix/category" else "${prefix}Category"
            val category = parameters.firstOrNull { it.descriptor.path == categoryPath }?.value?.toInt() ?: 0
            val formantPrefix = "$prefix/formant/"
            val formantParameters = parameters.filter { it.descriptor.path.startsWith(formantPrefix) }
            return FilterModel(
                address,
                prefix,
                parameters,
                category,
                formantParameters.takeIf { it.isNotEmpty() }?.let {
                    FormantModel(
                        count = it.firstOrNull { value -> value.descriptor.path == "${prefix}/formant/count" }
                            ?.value?.toInt()?.coerceIn(1, 12) ?: 3,
                        sequenceSize = it.firstOrNull { value -> value.descriptor.path == "${prefix}/formant/sequenceSize" }
                            ?.value?.toInt()?.coerceIn(1, 8) ?: 3,
                        parameters = it,
                    )
                },
            )
        }
    }
}
