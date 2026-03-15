# M0 Risks & Unknowns (before M1)

## Summary
Le Step 1 est faisable et bien engage, mais plusieurs points techniques doivent etre traites tot pour eviter un blocage en M1/M2.

## Major risks

### R1. `liblo` is linked by upstream core target via `MiddleWare`
- Observation:
  - Upstream `zynaddsubfx_core` links `LIBLO_LIBRARIES`
  - `src/Misc/MiddleWare.cpp` includes `<lo/lo.h>`
- Risk:
  - Android NDK build complexity increases unnecessarily for "premier son"
- Mitigation:
  - Build a custom Android core subset that excludes `MiddleWare.*` in M1/M2
  - Reintroduce later only if needed

### R2. `Master.cpp` references `Nio` symbols
- Observation:
  - `Master.cpp` references `Nio::masterSwap` and `Nio::setAudioCompressor`
- Risk:
  - "Direct Master render" path still needs some `Nio` symbols or stubs
- Mitigation:
  - Option A (recommended first): compile minimal `Nio` subset without desktop backends
  - Option B: provide local stubs for required symbols in Android adapter layer

### R3. FFTW (`fftw3f`) availability on Android
- Observation:
  - `DSP/FFTwrapper.*` depends on `fftw3f`
- Risk:
  - Slows M1 if no easy Android package/prebuilt is available
- Mitigation:
  - Decide early: prebuilt per ABI vs static build from source via CMake
  - Start with `arm64-v8a` only

### R4. `mxml` availability and API version differences
- Observation:
  - `XMLwrapper.cpp` has compatibility code for `mxml` variants
- Risk:
  - Build friction and runtime parsing regressions on Android
- Mitigation:
  - Vendor/build known-good mxml version for Android
  - Add preset XML smoke tests before broad compatibility claims

### R5. Large codebase with RT + non-RT coupling
- Observation:
  - Core relies on `rtosc`, threads, queues, automation, serialization, and audio abstractions
- Risk:
  - Accidental allocations/locks on audio thread during adapter implementation
- Mitigation:
  - Keep Android adapter thin
  - Explicit queue boundaries and RT-safe coding rules in M2/M4

## Open technical questions (implementation-facing, not blocking M0)

### Q1. Build source for dependencies
- `fftw3f`: prebuilt, vcpkg-for-android, or vendored CMake build?
- `mxml`: same question
- `liblo`: ideally deferred beyond M2 by excluding `MiddleWare`

### Q2. Render strategy in M2
- Preferred: Oboe callback -> `Master::GetAudioOutSamples`
- Fallback: temporary sine generator under same Oboe backend if engine subset build is delayed

### Q3. Preset ingestion API shape for Android SAF
- Path-based JNI or byte-array based JNI?
- Recommendation: support bytes first for SAF compatibility and easier tests

## M1 readiness checklist (from risk perspective)
- [ ] Decide `fftw3f` integration strategy
- [ ] Decide `mxml` integration strategy
- [ ] Implement Android-specific source subset (exclude `MiddleWare`)
- [ ] Confirm minimal `Nio` subset vs stubs for `Master.cpp`
- [ ] Create JNI smoke library and prove load in app

