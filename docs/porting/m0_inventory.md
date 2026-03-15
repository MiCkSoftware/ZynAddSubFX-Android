# M0 Inventory - ZynAddSubFX Upstream for Android Headless Port

## Scope
Inventaire du depot upstream pour definir une cible Android headless (moteur de synthese + rendu audio + presets), sans UI desktop, sans VST, sans MIDI externe dans la phase initiale.

## Upstream Snapshot (captured for Step 1)
- Upstream URL: `https://github.com/zynaddsubfx/zynaddsubfx.git`
- Local vendor path: `third_party/zynaddsubfx/`
- Upstream branch (cloned): `master`
- Upstream commit (superproject): `566361f1b261faba247efa129bbfadc81899edbd`
- License (project): GPL-2.0-or-later (`third_party/zynaddsubfx/COPYING`, `README.adoc`)
- Submodules initialized for M0:
  - `rtosc` -> `41b8b0ac9151163da4dae78ba368c78dc9893984`
  - `DPF` -> `86a621bfd86922a49ce593fec2a618a1e0cc6ef3`
  - `instruments` -> `c5c912131b31df5fdf372d2f06a25aaf2375837f`

## High-level build observations (upstream CMake)
- `src/CMakeLists.txt` already builds a reusable static library: `zynaddsubfx_core`.
- `zynaddsubfx_core` includes:
  - DSP, Effects, Params, Synth, Misc, globals, tlsf, Containers
  - but links to dependencies including `fftw3f`, `mxml`, `zlib`, `rtosc`, and `liblo`
- `Nio` is built as a separate static library `zynaddsubfx_nio`.
- Desktop executable `zynaddsubfx` links `zynaddsubfx_core` + `zynaddsubfx_nio` + GUI bridge + desktop audio libs.

This is excellent for Android because we can target a custom native build around the existing `zynaddsubfx_core` split.

## Module Inventory (Android Phase Initial)

| Module | Path(s) | Role | Key deps | Android status | Notes |
|---|---|---|---|---|---|
| Core synthesis engines | `src/Synth/` | AD/PAD/SUB note synthesis, envelopes, oscillators, modulation | `DSP`, `Params`, `Misc`, `rtosc` headers | `KEEP` | Primary value of port; preserve upstream code as much as possible |
| DSP primitives | `src/DSP/` | FFT wrapper, filters, unison, utility DSP | `fftw3f` | `KEEP` | `FFTwrapper` requires `fftw3f`; Android build needs FFTW strategy |
| Effects | `src/Effects/` | Reverb/echo/phaser/etc. | `DSP`, `Params`, `Misc` | `KEEP` | Needed for parity and historical preset fidelity |
| Parameter models | `src/Params/` | Synth parameter trees, controllers, preset stores | `Misc`, `rtosc` | `KEEP` | Core for editing and preset compatibility |
| Misc core data/model | `src/Misc/Bank.*`, `Master.*`, `Part.*`, `Microtonal.*`, `Util.*`, `Recorder.*`, `XMLwrapper.*`, `Schema.*`, etc. | Master mix engine, banks, serialization, config | `mxml`, `zlib`, `rtosc`, partial `Nio` refs | `ADAPT` | `Master.cpp` references `Nio`; `MiddleWare.cpp` brings `liblo` + UI-oriented control flow |
| Containers + allocators | `src/Containers/`, `tlsf/` | RT-friendly containers / allocator infra | STL, C runtime | `KEEP` | Keep upstream to minimize regressions |
| IO abstraction (Nio core layer) | `src/Nio/AudioOut.*`, `Engine.*`, `EngineMgr.*`, `OutMgr.*`, `InMgr.*`, `Nio.*`, `NulEngine.*`, `WavEngine.*`, `SafeQueue.*` | Audio/MIDI backend abstraction and engine switching | backend-specific libs conditionally | `ADAPT` | For Android, replace sinks/sources with Oboe backend or bypass using `Master::GetAudioOutSamples` first |
| Desktop audio backends | `src/Nio/Jack*`, `Alsa*`, `Oss*`, `Pa*`, `Sndio*` | OS-specific audio and MIDI backends | JACK/ALSA/OSS/PortAudio/Sndio headers/libs | `EXCLUDE (phase initiale)` | Linux/BSD desktop backends not used on Android |
| MIDI desktop handling | `src/Nio/MidiIn.*`, backend midi paths, Win MIDI in `src/main.cpp` | External MIDI input plumbing | desktop MIDI APIs | `EXCLUDE (phase initiale)` | Android MIDI deferred to M7 |
| Desktop UI (FLTK/NTK) | `src/UI/` | Legacy GUI and OSC UI bridge | FLTK/NTK/OpenGL/X11/liblo | `EXCLUDE (phase initiale)` | Replaced by Compose UI |
| DSSI output/plugin bridge | `src/Output/` | DSSI plugin integration | liblo / plugin host APIs | `EXCLUDE (phase initiale)` | Plugin hosting out of scope |
| Plugin builds (LV2/VST/etc via DPF) | `src/Plugin/`, `DPF/` | Plugin targets and plugin UIs | DPF, plugin SDKs, libdl/pthread | `EXCLUDE (phase initiale)` | Explicitly no VST in Android app |
| CLI executable shell | `src/main.cpp` | Desktop app bootstrap, CLI options, Nio init, UI startup | signals, liblo, desktop runtime | `EXCLUDE (phase initiale)` | Android app owns lifecycle and UI |
| Tests (native upstream) | `src/Tests/` | Unit/integration/stress tests | core + test infra | `ADAPT` | Reuse selected tests later for regression corpus; not first Android milestone |
| Instrument banks / examples | `instruments/` submodule | Historical presets/banks | filesystem data | `ADAPT` | Valuable for compatibility tests and bundled content strategy |
| OSC/RT parameter infra | `rtosc/` submodule | RT OSC message/ports/thread-link/automation | standalone submodule | `KEEP` | Core architecture dependency in `Master`, params, middleware |
| Docs/build scripts | `doc/`, root scripts | Build/docs/release | desktop tooling | `EXCLUDE (phase initiale)` | Documentation only |

## External Dependencies - Feasibility (Phase Initial)

| Dependency | Where used | Initial Android stance | Rationale / Notes |
|---|---|---|---|
| `zlib` | core serialization/compression paths | `KEEP` (NDK/system or bundled) | Standard, available in Android NDK ecosystem |
| `fftw3f` | `src/DSP/FFTwrapper.*` and oscillator generation | `KEEP` (needs Android build strategy) | Critical for sound generation fidelity; verify NDK build/prebuilt availability |
| `mxml` | `src/Misc/XMLwrapper.*`, preset XML | `KEEP` (build for Android) | Required for historical XML preset compatibility |
| `rtosc` (submodule) | parameters, automation, ports, middleware | `KEEP` | Strongly coupled to core parameter architecture |
| `liblo` | `MiddleWare.cpp`, desktop GUI/plugin OSC paths | `ADAPT/REMOVE from phase-initial headless target` | Upstream `zynaddsubfx_core` links `liblo`, but Android first pass should avoid `MiddleWare`/desktop OSC server |
| `FLTK` / `NTK` / `OpenGL` / `X11` | legacy UI | `EXCLUDE` | Desktop UI stack only |
| `JACK`, `ALSA`, `OSS`, `PortAudio`, `Sndio` | `src/Nio/*` desktop backends | `EXCLUDE` | Replaced by Android audio backend (Oboe/AAudio) |
| `DPF` | plugins (LV2/VST/etc) | `EXCLUDE` | No plugin support in Android standalone |

## Couplings that matter for Android headless extraction

### 1) `Master.cpp` -> `Nio` coupling
- `src/Misc/Master.cpp` includes `../Nio/Nio.h`
- Direct references observed:
  - `Nio::masterSwap(...)`
  - `Nio::setAudioCompressor(...)`
- Impact:
  - Even if Android renders audio directly via `Master::GetAudioOutSamples`, build/link still needs either:
    - a minimal `Nio` library (recommended short term), or
    - adapter stubs / upstream patching for these calls.

### 2) `MiddleWare.cpp` -> `liblo` coupling (desktop/OSC server path)
- `src/Misc/MiddleWare.cpp` includes `<lo/lo.h>` and manages OSC server/client traffic.
- Upstream `zynaddsubfx_core` links `liblo`, likely due to `MiddleWare` being included in `Misc` sources.
- Impact:
  - Android headless target should exclude `MiddleWare.*` from the first native build target (or isolate behind compile flag) to remove `liblo` dependency from M1/M2.

### 3) `main.cpp` hardwires desktop lifecycle and UI/Nio startup
- Includes `Nio/Nio.h`, `UI/Connection.h`, `MiddleWare.h`
- Uses POSIX signals and desktop CLI option parsing
- Impact:
  - Entire bootstrap must be replaced by Android app lifecycle + JNI + Compose.

### 4) Serialization and presets are in `Misc`/`Params` and reusable
- `XMLwrapper.*`, `PresetExtractor.*`, `PresetsStore.*`, `Presets.*`, `Master::{loadXML,saveXML,loadOSC,saveOSC}`
- Impact:
  - Good candidate to preserve for historical preset compatibility, but will need Android filesystem/SAF adapter at Kotlin/JNI boundary.

## Recommended Android headless extraction strategy (M0 conclusion)

### Keep upstream code mostly intact
- Prefer vendoring upstream under `third_party/zynaddsubfx/`
- Add Android-specific adapter/build files outside upstream tree first
- Minimize patches to upstream (keep a small patch queue if unavoidable)

### Build a custom Android target (not upstream desktop target)
- Do **not** try to build upstream `zynaddsubfx` desktop executable on Android.
- Create an Android-specific CMake target that compiles:
  - core synth/DSP/effects/params
  - required misc/containers/tlsf
  - minimal Nio subset (or stubs) only as needed for symbol closure
  - `rtosc`
  - `zlib`, `fftw3f`, `mxml`
- Exclude:
  - `src/main.cpp`
  - `src/UI/*`
  - `src/Plugin/*`
  - `src/Output/*`
  - desktop audio backend files in `src/Nio/*`
  - `MiddleWare.cpp` in the first phase (to drop `liblo`)

## Step-1 Exit Gate Check
- [x] Upstream depot imported and versioned locally
- [x] Modules inventoried with keep/adapt/exclude decision
- [x] Key dependencies and portability impact documented
- [x] Desktop/UI/audio couplings identified
- [x] Headless extraction direction documented for M1/M2
- [x] Major unknowns captured (see `m0_risks.md`)

