# ZynAddSubFX Android

Experimental standalone Android port of [ZynAddSubFX](https://github.com/zynaddsubfx/zynaddsubfx) with a rebuilt Jetpack Compose UI.

This repository contains:
- the Android application layer
- the JNI/native bridge
- the vendored upstream synth source under `third_party/zynaddsubfx/`
- local third-party dependencies required to build the app

## Status

- Standalone Android app, no VST host integration
- Current priority: engine/audio stability on Android, preset compatibility, then UX
- MIDI support is not the first milestone yet
- The project should be considered experimental

## Repository Layout

- `app/`: Android app, Compose UI, JNI entry point, packaged preset assets
- `native/`: native adapter layer and integration helpers
- `third_party/zynaddsubfx/`: vendored upstream ZynAddSubFX fork used by this port
- `third_party/fftw3/`, `third_party/mxml/`: pinned Git submodules for upstream third-party dependencies
- `docs/porting/`: porting notes and license/distribution checklist

## Build

Recommended setup:
- Android Studio recent stable
- Android SDK 36
- Android NDK installed from the SDK Manager
- CMake installed from the SDK Manager
- Java 11 toolchain

Typical local flow:

```bash
git clone --recurse-submodules <repo-url>
cd ZynAddSubFX
./gradlew :app:compileDebugKotlin --no-daemon
```

`local.properties` is intentionally not tracked. Let Android Studio generate it locally or point it to your SDK installation.

If you already cloned the repository without submodules:

```bash
git submodule update --init --recursive
```

## Licensing

Unless otherwise noted, this repository is distributed under the same license family as ZynAddSubFX: GNU GPL v2 or later.

This port distributes GPL-licensed synth code from ZynAddSubFX, so the repository should be treated as GPL-covered for distribution purposes.

- Upstream license text: [`third_party/zynaddsubfx/COPYING`](third_party/zynaddsubfx/COPYING)
- Additional bundled dependency notices: [`NOTICE.md`](NOTICE.md)
- Public release checklist: [`docs/porting/LICENSE_DISTRIBUTION_NOTES.md`](docs/porting/LICENSE_DISTRIBUTION_NOTES.md)

If you publish binaries built from this repository, make sure the corresponding source remains available.

## Upstream

- Upstream project: [github.com/zynaddsubfx/zynaddsubfx](https://github.com/zynaddsubfx/zynaddsubfx)
