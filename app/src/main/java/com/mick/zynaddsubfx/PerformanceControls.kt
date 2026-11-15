package com.mick.zynaddsubfx

// Tempo belongs to Master; the other controls target the keyboard channel.
enum class PerformanceControl(val code: Int) { TEMPO(0), PITCH(1), MODULATION(2), SUSTAIN(3) }
data class PerformanceControls(val tempo: Int = 120, val pitch: Int = 0, val modulation: Int = 64, val sustain: Boolean = false)
