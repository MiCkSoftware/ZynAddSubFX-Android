# Design QA — ADsynth Voice navigation and oscillator

## Comparison target

- Semantic source: `/Users/michael/Workspace/ZynAddSubFX/private/legacy-ux/ui05.png`
- Current implementation screenshots:
  - `/tmp/zyn-voice-tabs-current.png`
  - `/tmp/zyn-voice-filter-tab.png`
  - `/tmp/zyn-oscillator-final-preview.png`
  - `/tmp/zyn-oscillator-waveshape-current.png`
  - `/tmp/zyn-harmonics-final-current.png`
  - `/tmp/zyn-harmonics-two-axis-final.png`
- Combined comparison: `/tmp/zyn-oscillator-redesign-comparison-final.png`
- Viewport: Pixel 9 Pro emulator, portrait, 1280 × 2856 px, dark theme.
- States: Voice 1, Voice Filter anchor, oscillator preview with Triangle base and waveshaping 76, Harmonic 10 selected after horizontal swipe.

The legacy UI is used for semantic grouping only. The Android implementation intentionally serializes the desktop editor for portrait touch use.

## Full-view comparison evidence

The current editor retains the legacy concepts while reducing simultaneous density: base function and oscillator output share one graph, magnitude/randomness form Output, base type/shape form Base function, waveshaping and filter form one Shape & filter group, and harmonics use a complete overview plus focused controls. Persistent local tabs and the keyboard remain visible.

## Focused evidence and tested interactions

- Voice tabs now read `Voice`, `Oscillator`, `Amplitude`, `Frequency`, `Filter`, `Modulation`, `Unison`. Selecting Filter kept the `Voice 1` header and scrolled to the embedded Filter section.
- Oscillator tabs navigate inside the oscillator instead of returning to ADD.
- Changing Base function from Sine to Triangle visibly changed the green base curve and the red output preview.
- Dragging Waveshaping from 64 to 76 visibly changed the red output preview while leaving the green base curve stable.
- A vertical swipe changed Harmonic 1 magnitude from 127 to 73 without changing its selection. A subsequent horizontal swipe selected Harmonic 10 and loaded its magnitude 64; all three knobs updated live above the overview.

## Findings

No actionable P0, P1, or P2 visual or interaction findings remain in this scope.

- Typography: existing Android type hierarchy is preserved and local tabs use section names matching visible headings.
- Spacing: the combined graph, grouped parameter cards, harmonic knobs, overview, and fixed keyboard remain readable without horizontal content overflow.
- Colors: green consistently identifies Base, red identifies Output, teal identifies controls, and amber identifies the current harmonic.
- Assets: no external image assets are required; both curves and harmonic data are parameter-driven canvases.
- Copy: `Base + output preview` replaces the ambiguous oscilloscope interpretation.

## Comparison history

### Iteration 1

- [P1] Voice and oscillator tabs reused ADD root destinations and left the current screen.
- Fix: each sub-screen now owns its tab labels, scroll state, anchors, and section offsets.
- Evidence: `/tmp/zyn-voice-filter-tab.png` keeps `Voice 1` while showing Filter.

- [P1] Base function type and waveshaping did not affect the preview.
- Fix: the preview now reads base type, base shape, harmonic magnitude/phase, waveshaping, and oscillator filter parameters on every snapshot update.
- Evidence: `/tmp/zyn-oscillator-waveshape-current.png` shows Triangle and the shaped red output.

- [P2] Waveshaping appeared as an isolated section.
- Fix: it is grouped with oscillator Filter as `Shape & filter`, matching the semantic row in the legacy editor.

- [P1] Harmonic editing required tapping a thin bar, followed by sliders below the overview.
- Fix: three knobs moved above the overview; horizontal swipe changes the focused harmonic and vertical swipe changes its magnitude continuously.
- Evidence: `/tmp/zyn-harmonics-two-axis-final.png` shows Harmonic 10 selected after independently testing both axes.

## Residual limit

- [P3] The graph is a deterministic UI preview derived from the exposed oscillator parameters; it is not an audio-thread oscilloscope or a native sample capture. Exact audio-render parity remains a separate engine-level enhancement.

## Implementation checklist

- [x] Local, anchored tabs for Voice, Oscillator, and Resonance.
- [x] Combined Base and Output preview.
- [x] Base function and base shape reflected live.
- [x] Waveshaping and filter reflected live and semantically grouped.
- [x] Harmonic #, Magnitude, and Phase knobs above the overview.
- [x] Two-axis harmonic gesture: horizontal selection and vertical magnitude editing.

final result: passed
