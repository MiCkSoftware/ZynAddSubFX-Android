## Prompts prioritaires Part / AddSynth

Ordre de traitement issu de l'analyse du code, recontrole le 27 octobre 2026.
Ces prompts detaillent les notes ci-dessous sans remplacer les autres sujets du backlog.
Chaque agent doit mettre a jour TODO.md et TASKS.md, retirer les liens vers son prompt puis supprimer le fichier quand son implementation est terminee.

- [x] Gestes continus AddSynth — cloture validee par Mick le 31 octobre 2026 : knobs de voix et harmoniques sur ecritures live + etat transitoire reduit, reconciliation unique en fin/annulation ; compilation Kotlin et 13 tests JVM OK. [Resultats et limites](docs/perf_gestes_addsynth.md).
- [ ] QA complementaire transversale des gestes : stress/annulation, autres appareils, parite audible approfondie et profilage du cout natif `OscilGen::prepare()`. La validation UX courante par Mick cloture la feature ; ces verifications etendues restent distinctes.
- [x] Liste Voice — cloture validee par Mick le 31 octobre 2026 : courbe/bascule, Res, Vol, Pan, Detune, Vibrato, geometrie commune, raccourcis natifs conserves, LFO corrige et detune en cents natifs ; detail Voice complete pour activation/Resonance. [Resultats et QA restante](docs/voice_list.md).
- [ ] QA complementaire liste Voice : paysage/petite largeur/grande police, lecteur d'ecran, stress des gestes, sources partagees et audio approfondi. UX courante validee par Mick ; compilation native deux ABI, 24 tests JVM et 14 verifications JNI OK.
- [x] Knobs a faible plage : deplacement plafonne a 32 dp par pas, tests de densite et sensibilite ; controles Voice reunis et edition interne desactivee en partage externe. Validation UX courante par Mick le 31 octobre 2026 ; QA etendue dans les points transverses.
- [ ] [Liste de choix sur appui long des knobs enum](docs/008_feat_enum_long_press.md) : feature separee, drag direct conserve.
- [ ] [Harmoniser les en-tetes ON/C/P, Polarity +/− et clipboard Amplitude dedie](docs/003_feat_enveloppes_01.md)
- [ ] [Simplifier la navigation de Resonance](docs/004_ux_resonance.md)
- [ ] [Completer les champs globaux et Voice manquants](docs/005_feat_parite_champs_addsynth.md)
- [ ] [Completer OscilGen pour Voice et Modulator](docs/006_feat_oscilgen_complet.md)
- [ ] [Valider audio, clipboard et aller-retour XIZ](docs/007_qa_audio_xiz_addsynth.md)

## Notes et backlog existants
- replace voice enveloppe toggles by 0 1 and apply the same to adDynth main screen
- portamento not working ?
- ADsynth Oscillator Editor incomplete ?
- [x] Alignement liste Voice : rangees compactes avec geometrie commune sur une ligne ; verification visuelle restante.
- [x] Raccourcis de la liste native Voice conserves et corriges (LFO/Unison, detune, Resonance) ; QA tactile/audio restante, voir docs/voice_list.md.
- Parts : all on same screen (selected on is unfolded)
- [x] Parts : mini clavier visible en bas, reutilise depuis les editeurs d'instrument ; verification tactile sur appareil restante.
- Parts: 3 dot menu is werid ; why save to ? remove nav to kit X
- ad synth resonance : parameters  tab nav useless

- Main
  - bouton edit save sur main à droite de stereo (et retirer save des top app bar)

- KITs
  - all parts sur le screen d'edition avec instru en header
  - [x] Kit mute utilise le bouton LED commun.
  - Screen blink during loading 
- sub synth todo
- pad synth todo
- FX todo (main and parts ...)


LIB
- keyb to library + one tap to select another ti nav back

MISC-UX
- For enveloppe, if disabled hide controle and or replace by a gfx editor or preview with on demande knobs


## ADD SYNTH Fields
https://wiki.linuxaudio.org/wiki/zynaddsubfx_manual
https://zynaddsubfx.sourceforge.io/doc_2.html
  | Filter | Global | Enable | Enable voice filter | Checkbox | on |
  | Filter | Global | Bypass Global Filter | Bypass global filter | Checkbox | off |
  | Filter | Filter Parameters | Category | Filter category | Combo | Analog |
  | Filter | Filter Parameters | Filter Type | Filter type | Combo | LPF2 |
  | Filter | Filter Parameters | C.freq | Center / cutoff frequency | Knob / potentiomètre | — |
  | Filter | Filter Parameters | Q | Resonance / bandwidth | Knob / potentiomètre | — |
  | Filter | Filter Parameters | V.SnsA. | Velocity sensing amount | Knob / potentiomètre | — |
  | Filter | Filter Parameters | V.Sns. | Velocity sensing | Knob / potentiomètre | — |
  | Filter | Filter Parameters | freq.tr. | Frequency tracking | Knob / potentiomètre | — |
  | Filter | Filter Parameters | gain | Filter gain | Knob / potentiomètre | — |
  | Filter | Filter Parameters | St. | Filter stages / steepness multiplier | Spinner int +/- | 1x |
  | Filter | Filter Envelope | Enable | Enable filter envelope | Checkbox | on |
  | Filter | Filter Envelope | A.val | Attack value | Knob / potentiomètre | — |
  | Filter | Filter Envelope | A.dt | Attack duration | Knob / potentiomètre | — |
  | Filter | Filter Envelope | D.val | Decay value | Knob / potentiomètre | — |
  | Filter | Filter Envelope | D.dt | Decay duration | Knob / potentiomètre | — |
  | Filter | Filter Envelope | R.dt | Release duration | Knob / potentiomètre | — |
  | Filter | Filter Envelope | R.val | Release value | Knob / potentiomètre | — |
  | Filter | Filter Envelope | Stretch | Envelope stretch | Knob / potentiomètre | — |
  | Filter | Filter Envelope | frcR | Force release | Checkbox | off |
  | Filter | Filter LFO | Enable | Enable filter LFO | Checkbox | on |
  | Filter | Filter LFO | Freq. | LFO frequency | Knob / potentiomètre | — |
  | Filter | Filter LFO | Depth | LFO depth | Knob / potentiomètre | — |
  | Filter | Filter LFO | Start | LFO start phase | Knob / potentiomètre | — |
  | Filter | Filter LFO | Delay | LFO delay | Knob / potentiomètre | — |
  | Filter | Filter LFO | Str. | LFO stretch | Knob / potentiomètre | — |
  | Filter | Filter LFO | C. | LFO continuous | Checkbox | — |
  | Filter | Filter LFO | A.R. | LFO amplitude random | Knob / potentiomètre | — |
  | Filter | Filter LFO | F.R. | LFO frequency random | Knob / potentiomètre | — |
  | Filter | Filter LFO | Type | LFO waveform/type | Combo | SINE |
- | Voice Oscillator | Global | Phase | Oscillator phase | Slider | — |
  | Voice Oscillator | Global | Use Oscil. | External oscillator source | Combo | Internal |
  | Voice Oscillator | Global | Sound | Oscillator mode | Combo | Sound |
  | Voice Oscillator | Waveform display | Waveform | Oscillator waveform preview | Graph display | sine-like |
  | Unison | Global | Unison | Unison mode | Combo | OFF |
  | Unison | Global | Frequency Spread | Unison frequency spread | Slider | — |
  | Unison | Global | cents | Frequency spread amount | Float input | 44.6 |
  | Unison | Global | Stereo | Stereo spread | Knob / potentiomètre | — |
  | Unison | Global | Vibratto | Vibrato amount | Knob / potentiomètre | — |
  | Unison | Global | Vib.speed | Vibrato speed | Knob / potentiomètre | — |
  | Unison | Global | Invert | Unison inversion mode | Combo | None |
  | Modulator | Global | Type | Modulator type | Combo | PM |
  | Modulator | Global | External Mod. | External modulator source | Combo | OFF |
  | Modulator | Mod. Amplitude | Vol | Modulator volume | Slider | 90 |
  | Modulator | Mod. Amplitude | V.Sns | Modulator velocity sensing | Slider | 64 |
  | Modulator | Mod. Amplitude | F.Damp | Frequency damping | Slider | 0 |
  | Modulator | Mod. Amplitude Envelope | Enable | Enable modulator amplitude envelope | Checkbox | on |
  | Modulator | Mod. Amplitude Envelope | A.dt | Attack duration | Knob / potentiomètre | — |
  | Modulator | Mod. Amplitude Envelope | D.dt | Decay duration | Knob / potentiomètre | — |
  | Modulator | Mod. Amplitude Envelope | S.val | Sustain value | Knob / potentiomètre | — |
  | Modulator | Mod. Amplitude Envelope | R.dt | Release duration | Knob / potentiomètre | — |
  | Modulator | Mod. Amplitude Envelope | Stretch | Envelope stretch | Knob / potentiomètre | — |
  | Modulator | Mod. Amplitude Envelope | frcR | Force release | Checkbox | on |
  | Modulator | Mod. Frequency | Detune | Modulator detune | Float input | 0.00 |
  | Modulator | Mod. Frequency | Frequency slider | Modulator fine tuning | Slider | — |
  | Modulator | Mod. Frequency | Detune Type | Detune behavior | Combo | Default |
  | Modulator | Mod. Frequency | Octave | Octave shift | Spinner int +/- | 0 |
  | Modulator | Mod. Frequency | Coarse Det. | Coarse detune | Spinner int +/- | 0 |
  | Modulator | Mod. Frequency Envelope | Enable | Enable modulator frequency envelope | Checkbox | on |
  | Modulator | Mod. Frequency Envelope | A.val | Attack value | Knob / potentiomètre | — |
  | Modulator | Mod. Frequency Envelope | A.dt | Attack duration | Knob / potentiomètre | — |
  | Modulator | Mod. Frequency Envelope | R.dt | Release duration | Knob / potentiomètre | — |
  | Modulator | Mod. Frequency Envelope | R.val | Release value | Knob / potentiomètre | — |
  | Modulator | Mod. Frequency Envelope | Stretch | Envelope stretch | Knob / potentiomètre | — |
  | Modulator | Mod. Frequency Envelope | frcR | Force release | Checkbox | off |
  | Modulator Oscillator | Global | Use | Modulator oscillator source | Combo | Internal |
  | Modulator Oscillator | Global | Phase | Modulator oscillator phase | Slider | — |
  | Modulator Oscillator | Waveform display | Waveform | Modulator oscillator waveform preview | Graph

