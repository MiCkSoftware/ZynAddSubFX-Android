# ZynAddSubFX Upstream Fork Notes (Android Port)

## Decision (pragmatic)

For this Android port, `third_party/zynaddsubfx/` is treated as a **local maintained fork** (vendored upstream),
not as a strictly read-only mirror.

Rationale:
- upstream appears effectively dormant (low/no recent movement)
- Android standalone port requires non-trivial adaptations
- some upstream patches are already required for stability in standalone mode

We still keep:
- upstream origin URL
- upstream base commit
- a small, documented list of local fork patches

This preserves provenance while optimizing for product progress.

## Upstream base reference

- Path: `third_party/zynaddsubfx/`
- Base commit (current): `566361f1b261faba247efa129bbfadc81899edbd`

## Local fork patches currently present (inside upstream tree)

### 1) `src/Misc/Master.cpp`

Reason:
- Android standalone build does not initialize the desktop `MiddleWare`/`rtosc::ThreadLink` (`bToU`, `uToB`)
- Some `Master` code paths (`broadcast`, `reply`, etc.) dereferenced these pointers unconditionally
- This caused crashes during preset loading/rendering

Patch intent:
- Guard `bToU` / `uToB` usage when absent (standalone headless/Android mode)
- Keep desktop behavior unchanged when middleware is present

Status:
- Critical stability patch for Android standalone bring-up

## Maintenance workflow (recommended)

### Short term (current project phase)
- Keep upstream/fork changes in `third_party/zynaddsubfx/`
- Commit Android-facing upstream patches in dedicated commits with clear messages
- Document each new upstream patch in this file

### If upstream sync is attempted later
- Record the target upstream commit/tag first
- Rebase/merge and re-validate Android bring-up
- Reapply only documented patches that are still needed

## Commit message style (recommended)

- `third_party(zyn): guard Master middleware thread-links for standalone Android`
- `third_party(zyn): ...`

## Notes

- Android-specific wrappers should continue to live outside upstream when practical:
  - `native/engine_adapter/`
  - `app/src/main/cpp/`
- But upstream edits are acceptable when they remove hard crashes or deep coupling blockers.
