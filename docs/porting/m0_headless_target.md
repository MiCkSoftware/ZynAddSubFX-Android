# M0 Headless Target Definition (Android)

## Goal
Definir une cible native Android "headless engine" pour ZynAddSubFX:
- rendu audio stereo float
- declenchement de notes et controle de parametres
- chargement/sauvegarde de presets (roadmap proche)
- sans UI desktop
- sans VST/plugin
- sans MIDI externe dans un premier temps

## Phase strategy (important)

### M1/M2 target (first sound)
Priorite: obtenir un son stable sur Android rapidement avec un minimum de couplage desktop.

Recommended path:
1. JNI + Android app lifecycle
2. Oboe audio callback (native)
3. Render audio via `zyn::Master::GetAudioOutSamples(...)`
4. Trigger notes via `zyn::Master::noteOn/noteOff(...)`
5. Avoid `MiddleWare` in first phase to avoid `liblo`/desktop OSC server complexity

### Later target (presets + broader compatibility)
- Add preset load/save wrappers (XML/OSC as needed)
- Decide if `MiddleWare` is still needed for parameter transport/automation workflows
- Add Android MIDI API bridge

## In-scope / Out-of-scope (phase initial)

### In scope (M1-M3)
- Native engine init / shutdown
- Audio render callback integration (Oboe/AAudio)
- Virtual keyboard -> `noteOn/noteOff`
- Basic parameter set/get API
- Internal patch/preset smoke tests

### Out of scope (M0-M3)
- Desktop UI (`src/UI`)
- Plugin targets (`src/Plugin`, `src/Output`, VST/LV2/DSSI)
- Desktop audio backends (`JACK/ALSA/OSS/PortAudio/Sndio`)
- Android MIDI external devices (deferred to M7)
- Exact feature parity of all parameter editors in Compose

## Proposed native architecture (draft)

```text
Android App (Kotlin / Compose)
  |
  +-- SynthEngine (Kotlin facade)
        |
        +-- JNI bridge (app/src/main/cpp/jni_bridge.cpp)  [M1]
              |
              +-- Android engine adapter (native/engine_adapter/)
                    |- ZynAndroidEngine (lifecycle + thread-safe command queue)
                    |- ZynRenderAdapter (calls zyn::Master)
                    |- PresetAdapter (M6)
                    |- ParamMap/Introspection (M4+)
              |
              +-- Vendored upstream (third_party/zynaddsubfx/)
                    |- core synth/params/dsp/effects/misc subset
                    |- rtosc submodule
                    |- tlsf
              |
              +-- Android audio backend
                    |- Oboe callback -> render float stereo buffers [M2]
```

## Native filesystem/layout blueprint (Step 1 deliverable)

```text
third_party/
  zynaddsubfx/                 # vendored upstream (kept as intact as possible)

native/
  CMakeLists.txt               # Android-native draft build orchestration (not wired yet)
  engine_adapter/
    # Android-specific wrappers/adapters around upstream engine

app/
  src/main/cpp/
    # JNI bridge files (M1)
```

## Public APIs to freeze (design only, M0)

### Kotlin facade (planned)
```kotlin
interface SynthEngine {
    fun init(sampleRate: Int, framesPerBurst: Int): Boolean
    fun startAudio(): Boolean
    fun stopAudio()
    fun noteOn(channel: Int, note: Int, velocity: Int)
    fun noteOff(channel: Int, note: Int)
    fun setParam(id: String, value: Float): Boolean
    fun getParam(id: String): Float?
}
```

### JNI surface (minimal, planned)
```c
// names illustrative; exact JNI signatures in M1
bool nativeInit(int sampleRate, int framesPerBurst);
bool nativeStartAudio(void);
void nativeStopAudio(void);
void nativeNoteOn(int channel, int note, int velocity);
void nativeNoteOff(int channel, int note);
```

Future JNI additions (not M1):
- `nativeSetParam(...)`
- `nativeGetParam(...)`
- `nativeLoadPresetFromBytes(...)`
- `nativeSavePresetToBytes(...)`
- `nativeRenderOffline(...)`

## Build strategy decisions (M0)

### Decision 1: do not build upstream desktop executable
- We will build a custom Android native library.
- Upstream `src/main.cpp` is excluded.

### Decision 2: split "first sound" from "full middleware/OSC" support
- First sound path uses `Master` directly.
- `MiddleWare.cpp` is excluded initially to avoid `liblo` and desktop OSC server complexity.

### Decision 3: keep upstream code vendored and adapt around it
- Android-specific code lives outside `third_party/zynaddsubfx/` as much as possible.
- If patches become necessary, keep them minimal and documented.

## Candidate source subset for M1/M2 (compile intent)

### Keep (expected)
- `src/DSP/*`
- `src/Effects/*`
- `src/Params/*`
- `src/Synth/*`
- Most of `src/Misc/*`
- `src/Containers/*`
- `src/globals.*`
- `tlsf/tlsf.c`
- `rtosc/*` (submodule)

### Likely exclude from first Android core target
- `src/main.cpp`
- `src/UI/*`
- `src/Plugin/*`
- `src/Output/*`
- `src/Misc/MiddleWare.*` (first pass)
- `src/Nio/*` desktop backends (`Jack*`, `Alsa*`, `Oss*`, `Pa*`, `Sndio*`)

### Likely keep temporarily from `src/Nio/` (for symbol closure / helpers)
- `Nio.cpp`, `Engine*.cpp`, `OutMgr.cpp`, `InMgr.cpp`, `NulEngine.cpp`, `WavEngine.cpp`, `AudioOut.cpp`, `MidiIn.cpp`, `SafeQueue.cpp`

Note:
- `Master.cpp` references `Nio::masterSwap` and `Nio::setAudioCompressor`.
- If this creates unnecessary dependency drag for M1, we can instead stub only the required `Nio` symbols in `native/engine_adapter/`.

## Threading & realtime rules (to carry into implementation)
- No allocations in audio callback if avoidable
- No blocking locks in audio callback
- UI -> audio commands via queue
- Preset load/save off audio thread
- Parameter snapshots to UI at throttled cadence

## Presets & historical compatibility (target behavior)
- Preserve upstream serialization logic where possible (`XMLwrapper`, `PresetsStore`, `Master::loadXML/saveXML`, etc.)
- Android-specific file access handled at Kotlin layer (SAF/URI) and bridged as bytes/path abstractions into JNI
- Maintain a regression corpus from `instruments/` and selected upstream test fixtures

## Acceptance criteria for M0 definition (met)
- [x] Headless target responsibilities documented
- [x] Out-of-scope items explicitly documented
- [x] API shape for JNI/Kotlin sketched
- [x] Native layout blueprint proposed
- [x] Source subset strategy documented for M1/M2

