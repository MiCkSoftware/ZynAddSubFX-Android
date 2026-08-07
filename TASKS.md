# ZynAddSubFX Android Port - TASKS

## Vision
Porter ZynAddSubFX en application Android standalone (sans VST), avec UX entièrement refaite en Jetpack Compose.
Le support MIDI est differe apres le MVP sans MIDI.

## Contraintes / decisions initiales
- Platforme cible: Android standalone
- UI: Jetpack Compose (refonte complete)
- Pas de VST
- Pas de MIDI dans un premier temps
- `minSdk=29` (actuel)
- Backend audio cible: Oboe (AAudio prioritaire)
- ABI initiale: `arm64-v8a` (elargissement plus tard)

## Milestones

### M0 - Cadrage technique + extraction moteur headless (Step 1)
- [x] Importer le code source upstream ZynAddSubFX dans `third_party/zynaddsubfx/` (ou `native/upstream/zynaddsubfx/`)
- [x] Documenter la version upstream (commit/tag + licence)
- [x] Inventorier les modules:
  - [x] moteur synthese
  - [x] presets/serialization
  - [x] audio desktop backends
  - [x] MIDI desktop
  - [x] UI desktop
  - [x] VST/plugin
  - [x] dependances tierces
- [x] Classer chaque module: `KEEP`, `ADAPT`, `EXCLUDE (phase initiale)`
- [x] Identifier les points de couplage UI/desktop -> moteur
- [x] Definir la cible “headless engine” Android (rendu audio + presets + parametres)
- [x] Rediger `docs/porting/m0_inventory.md`
- [x] Rediger `docs/porting/m0_headless_target.md`
- [x] Definir l’arborescence native cible (sans implementer JNI complet)
- [x] Preparer un squelette `CMakeLists.txt` de plan (draft non branche si besoin)
- [x] Definir les risques + inconnues bloquantes avant M1
- [x] Documenter la strategie upstream pour Android (`third_party/zynaddsubfx` assume comme fork local vendored + base commit de reference)

### M1 - Integration NDK/CMake + JNI minimal
- [x] Ajouter `externalNativeBuild` dans `app/build.gradle.kts`
- [x] Ajouter config NDK/ABIs (`arm64-v8a`)
- [x] Creer `app/src/main/cpp/` + `CMakeLists.txt`
- [x] Compiler une librairie native minimale `.so`
- [x] Exposer JNI test (`nativeGetVersion`, `nativeInit`)
- [x] Charger la lib depuis Kotlin
- [x] Valider appel JNI dans l’app
- [x] Ajouter ABI `x86_64` pour support emulateur/dev
- [ ] Evaluer support ABI `x86` (32-bit) selon besoin reel (legacy/emulateurs)
- [x] Ajouter une probe de compilation upstream Zyn (headers + generation `zyn-version.h`/`zyn-config.h`)
- [x] Compiler un premier fichier upstream Zyn dans `zynbridge` (`src/globals.cpp`)
- [x] Integrer `rtosc` C minimal + `Misc/Util.cpp` + `Containers/ScratchString.cpp` dans `zynbridge`
- [x] Integrer `rtosc-cpp` (lot principal) + `src/version.cpp` dans `zynbridge`
- [x] Integrer `tlsf` + `Misc/Allocator.cpp` dans `zynbridge`
- [x] Integrer `Misc/MsgParsing.cpp`, `Schema.cpp`, `CallbackRepeater.cpp`, `MemLocker.cpp`, `WavFile.cpp`
- [x] Integrer `mxml` (vendored) + `Misc/XMLwrapper.cpp` dans `zynbridge`
- [x] Compiler `Params/Controller.cpp` dans `zynbridge`
- [x] Compiler `Misc/Config.cpp` dans `zynbridge`
- [x] Compiler `DSP/FFTwrapper.cpp` dans `zynbridge` (via shim `fftw3f` temporaire pour debloquer l'integration)
- [x] Compiler `Effects/*`, `Part.cpp`, `Bank.cpp`, `BankDb.cpp`, `Recorder.cpp` et `Master.cpp` dans `zynbridge` (avec stubs Android temporaires pour `Nio`/`PresetExtractor`/`bankPorts`)

### M2 - Audio temps reel Android (premier son)
- [OPT] Integrer Oboe (optionnel pour robustesse/portabilite; non bloquant pour MIDI Android)
- [x] Mettre en place pipeline audio callback natif
- [x] Produire un son test (sinus si moteur Zyn non pret)
- [x] UI Compose minimale: init/start/test/stop
- [x] Ajouter seam natif `render(...)` + `noteOn/noteOff` (stub, pret pour Zyn)
- [x] Brancher un backend de rendu `zyn-master` experimental dans `ZynAndroidEngine` (fallback sinus conserve)
- [x] Gerer lifecycle audio (pause/resume) (auto-stop/auto-resume de base)
- [x] Valider stabilite start/stop repetee

### M3 - Jouabilite sans MIDI
- [x] Brancher `noteOn/noteOff` reels vers moteur (backend `zyn-master` experimental)
- [x] Clavier virtuel Compose (mini clavier tactile 1 octave + release)
- [x] Preset de test embarque
- [x] Presets de demo embarques (.xmz assets) + chargement local via `Master::loadXML`
- [x] Parametre de base: volume master (slider UI -> moteur)
- [x] Ajouter un bouton `Panic` (reset voix/FX) pour recuperer des notes fantomes / etats bloques
- [x] Parametres de jeu de base (velocity clavier + octave shift)
- [x] Demo jouable tactile stable

### M4 - Architecture propre moteur/UI
- [x] Creer facade Kotlin `SynthEngine`
- [x] Ajouter queue de commandes UI -> audio
- [ ] Stabiliser contrat de parametres (`id`, type, bornes)
- [x] Ajouter logs/diagnostics JNI natifs
- [ ] Eviter locks/allocations sur audio thread

### M5 - Refonte UX Compose complete
- [x] Definir une navigation Android de base (Performance / Presets / Instrument Editor / FX)
- [x] Composants synth de base (knobs, sliders, switches, selectors, clavier, courbes)
- [x] Gestion etat de l’editeur via `InstrumentEditorViewModel`
- [ ] Portrait/paysage + tablette
- [ ] Mode clavier plein ecran avec UX audio minimale
- [x] Export XIZ depuis l’editeur d’instrument
- [x] Parite ADsynth Voice ciblee : Filter, Modulator et Modulator Oscillator
- [x] QA UX rapide ADsynth Voice (navigation, sections, oscillateurs et gestes)
- [ ] Import XIZ/banques via SAF
- [ ] UX de sauvegarde/restauration d’etat complete
- [ ] UX master controls, insertion/system effects, sends, microtonal configuration, MIDI mapping, and full bank management.

## UI - Inventaire des ecrans natifs et parite Android

Cette matrice recense les surfaces UI fonctionnelles du frontend FLTK natif (`third_party/zynaddsubfx/src/UI/`) et leur equivalent Android actuel. Les statuts sont :

- **Implemente** : parcours Android utilisable et branche au moteur.
- **Partiel** : une partie de l’ecran ou des parametres est disponible.
- **Absent** : aucun equivalent Android complet.
- **Hors perimetre initial** : explicitement reporte (MIDI/VST, par exemple).

Les chemins Android de cette matrice suivent une notation stable :

- `.` represente une navigation vers un ecran.
- `/` represente une section ou un controleur embarque dans l'ecran parent.
- `[]` represente une collection et indique sa cardinalite.

Exemples :

- ecran Voice : `Main.Part.Part[1..16].Add.Voice[1..8]` ;
- cutoff du filtre : `Main.Part.Part[1..16].Add.Voice[1..8]/Filter/Cutoff` ;
- preview du modulateur : `Main.Part.Part[1..16].Add.Voice[1..8]/Modulation/OscillatorPreview` ;
- editeur ouvert au tap : `Main.Part.Part[1..16].Add.Voice[1..8].ModulatorOscillator`.

### Navigation principale et performance

| Surface native | Nature Android | Ecran parent Android | Chemin Android | Etat | Suite logique |
|---|---|---|---|---|---|
| Master / Main window | Ecran | Application | `Main` | **Partiel** | Completer effets, sends, meters, menus et etat global |
| Simple Master window | Ecran alternatif | Application | `Main.Performance` | **Absent** | Decider si Performance remplace ce mode |
| Panel window | Section | `Main` | `/Parts` | **Partiel** | Consolider mixer, VU et selection de partie |
| Virtual Keyboard | Controleur | Variable | `/Keyboard` | **Implemente** | Ajouter mode plein ecran et velocity avancee |
| Bank window / BankView | Ecran | `Main` | `.Presets` | **Partiel** | Favoris, tags, recherche et import/export |
| PresetsUI | Dialogue / actions | Variable | `/PresetActions` | **Partiel** | Copy/paste Voice et modules, reset/undo coherent |
| About / Copyright | Ecran | `Main` | `.About` | **Absent** | Ajouter About/Licences GPL Android |
| ConfigUI / Settings | Ecran | `Main` | `.Settings` | **Absent** | Definir le perimetre Settings Android |
| MicrotonalUI | Ecran | `Main` | `.Microtonal` | **Absent** | Porter apres les controles master de base |
| NioUI | Remplacement plateforme | Application | Configuration audio Android | **Hors perimetre initial** | Remplace par Android/Oboe-AAudio |
| MIDI learn / MIDI settings | Ecran | `Main` | `.Midi` | **Hors perimetre initial** | M7 - Android MIDI |

### Editeurs d’instruments et moteurs

| Surface native | Nature Android | Ecran parent Android | Chemin Android | Etat | Suite logique |
|---|---|---|---|---|---|
| PartUI / Instrument Kit | Ecran | `Main.Part` | `.Part[1..16]` | **Partiel** | Stabiliser Kit et activations ADD/SUB/PAD |
| ADnoteUI - Global Parameters | Sections | `Main.Part.Part[1..16].Add` | `/Amplitude`, `/Frequency`, `/Filter` | **Partiel** | Verifier whitelist, couleurs et parite |
| ADnoteUI - Voice list | Section / navigation | `Main.Part.Part[1..16].Add` | `/VoiceList` | **Implemente** | Affiner densite, indicateurs et libelle Resonance |
| ADnoteUI - Voice Parameters | Ecran | `Main.Part.Part[1..16].Add` | `.Voice[1..8]` | **Partiel** | Copy/paste et champs restants |
| Voice Filter | Section | `Main.Part.Part[1..16].Add.Voice[1..8]` | `/Filter` | **Implemente** | Editeur Formant avance differe |
| Voice Modulator | Section | `Main.Part.Part[1..16].Add.Voice[1..8]` | `/Modulation` | **Implemente** | QA audio approfondie des modes et routages |
| ADnoteUI - Voice Oscillator | Preview + ecran | `Main.Part.Part[1..16].Add.Voice[1..8]` | `/OscillatorPreview` -> `.Oscillator` | **Partiel** | OscilGen avance |
| ADnoteUI - Modulator Oscillator | Preview + ecran | `Main.Part.Part[1..16].Add.Voice[1..8]` | `/Modulation/OscillatorPreview` -> `.ModulatorOscillator` | **Implemente** | OscilGen avance |
| OscilGenUI / OscilEditor | Controleur d'ecran | Ecrans Oscillator | `/Parameters`, `/Harmonics`, `/Phases` | **Partiel** | Completer OscilGen et preview fidele |
| ResonanceUI | Preview + ecran | `Main.Part.Part[1..16].Add` | `/ResonancePreview` -> `.Resonance` | **Implemente** | QA valeurs et gestes |
| EnvelopeUI / EnvelopeFreeEdit | Controleur | Variable | `/Amplitude|Frequency|Filter|Modulation/Envelope` | **Partiel** | Mode libre et edition graphique |
| LFOUI | Controleur | Variable | `/Amplitude|Frequency|Filter/LFO` | **Partiel** | Parite fine des instances et labels |
| FilterUI | Section reutilisable | Variable | `/Filter` | **Partiel** | Editeur Formant avance |
| SUBnoteUI | Ecran moteur | `Main.Part.Part[1..16]` | `.Sub` | **Partiel** | Reprendre apres ADsynth/Voice |
| PADnoteUI | Ecran moteur | `Main.Part.Part[1..16]` | `.Pad` | **Partiel** | Completer Profile/Spectrum/Quality |

### Effets et outils graphiques

| Surface native | Nature Android | Ecran parent Android | Chemin Android | Etat | Suite logique |
|---|---|---|---|---|---|
| EffUI - Reverb | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Reverb` | **Partiel** | Parametres effectifs et presets |
| EffUI - Echo | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Echo` | **Partiel** | Parametres effectifs et presets |
| EffUI - Chorus/Flange | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Chorus` | **Partiel** | Parametres effectifs et presets |
| EffUI - Phaser | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Phaser` | **Partiel** | Parametres effectifs et presets |
| EffUI - Alienwah | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Alienwah` | **Partiel** | Parametres effectifs et presets |
| EffUI - Distortion/Overdrive | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Distortion` | **Partiel** | Exposer les parametres natifs |
| EffUI - EQ | Section + controleur | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Eq` | **Partiel** | Courbe EQ et bandes |
| EffUI - Dynamic Filter | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/DynamicFilter` | **Partiel** | UI specialisee et modulation |
| EffUI - Sympathetic strings | Section FX | `Main.Part.Part[1..16].Fx` | `/Fx[1..3]/Sympathetic` | **Partiel** | UI specialisee et presets |
| Formant Filter window | Ecran specialise | Section Filter concernee | `.FormantEditor` | **Absent** | Planifier avec FilterUI avance |
| PAD harmonic profile / overtone graph | Controleur graphique | `Main.Part.Part[1..16].Pad` | `/Profile`, `/Spectrum` | **Absent** | Porter apres le moteur PAD principal |

### Avancement et priorites UI


Priorites suivantes :

1. Stabiliser les composants communs `EnvelopeUI`, `LFOUI`, filtres, previews et copy/paste.
2. Completer Kit/Part et les controles globaux de l’instrument.
3. Porter SUBsynth et PADsynth avec leurs graphes natifs.
4. Porter les effets et leurs routages.
5. Ajouter Settings, About/Licences, microtonalite et gestion complete des banques.

### M6 - Presets historiques (compatibilite)
- [ ] Inventorier formats historiques supportes upstream
- [ ] Porter parsing/serialization necessaires
- [ ] Import via SAF (Storage Access Framework)
- [ ] Export presets/banks
- [ ] Gestion compat/migration + messages d’erreur
- [ ] Corpus de tests de presets

### M7 - MIDI Android
- [ ] Integrer Android MIDI API (priorite USB, puis BLE)
- [ ] Mapper note on/off, CC, pitch bend, program change
- [ ] UI de configuration MIDI
- [ ] Gerer reconnexion peripheriques
- [ ] Tests latence / notes bloquees

### M8 - Portage fonctionnel avance / parite
- [ ] Completer modules synth manquants
- [ ] Remplacer le shim `fftw3f` temporaire par une solution FFT production (FFTW Android buildable ou alternative compatible)
- [x] Tenter integration `FFTW3F_NATIVE` via `third_party/fftw3` et documenter le blocage actuel (vendor incomplet: codelets generes manquants, symboles `solvtab_*` au link)
- [x] Preparer un switch CMake pour selectionner le backend FFT (shim actuel vs futur backend prod)
- [ ] Effets / routings / multi (selon scope)
- [ ] Banque/favoris/recherche/tags
- [ ] Export audio offline (optionnel)
- [ ] Documenter ecarts restants vs desktop

### M9 - Performance, QA, release
- [ ] Profiling CPU/memoire multi-appareils
- [ ] Optimisations (buffers, NEON si utile, allocations)
- [ ] Mesurer et reduire la latence "premiere note" apres chargement de preset (ex: `Supersaw` ~300-400ms observes en debug)
- [ ] Tests stress (polyphonie, presets, lifecycle)
- [ ] Stabilisation crashes / logs
- [ ] CI Android + builds beta/release
- [ ] Definir matrice ABI release (ex: `arm64-v8a` obligatoire, `x86_64` dev/emulateur, `armeabi-v7a`/`x86` selon besoin)
- [ ] Checklist de publication
- [ ] Pour l'alpha experimentale Play, ajouter la mention d'engagement a respecter les regles pour les contenus familiaux si applicable
- [ ] Pour l'alpha experimentale Play, exposer les licences et un lien vers le code source correspondant
- [ ] Ajouter un ecran `About / Licences` dans l'app avec notice GPL adaptee Android (copyright, absence de garantie, lien vers licence et code source), au lieu de reprendre tel quel le bloc interactif type `show w` / `show c`
- [ ] Definir strategie audio release: conserver AAudio direct ou migrer vers Oboe apres profiling/stress tests

## Tests transverses (a maintenir)
- [ ] Start/stop audio repete sans crash
- [ ] Reprise apres pause/resume Android
- [ ] JNI robuste aux parametres invalides
- [ ] Chargement preset invalide sans crash
- [ ] Rotation ecran sans perte d’etat critique
- [ ] Bench CPU/polyphonie sur appareils de test
- [ ] Mesurer la latence entre `noteOn` et emission audible apres chargement de preset (first-note warmup), logguer les presets lents
- [ ] Verifier compatibilite page size 16 KB pour toutes les libs natives (`arm64-v8a`, `x86_64`) avant publication
- [ ] Verifier absence de notes fantomes / accumulation d'effets apres changements repetes de presets (tester `All Notes Off` + `Panic`)
- [ ] Exercer les stress tests manuels debug (note burst / reload preset) et analyser `aaudioErrCb`, `aaudioXruns`, recoveries

## Definition de Done par etape
- Build reproducible
- Demo manuelle documentee
- Risques / limites connus documentes
- TODOs de l’etape suivante clairement listes

## Publication / Licence (GPL)
- [ ] Verifier et documenter precisement les licences de tous les composants embarques (`third_party/zynaddsubfx`, `rtosc`, `mxml`, FFT de production, etc.)
- [ ] Ajouter une mention explicite de `FFTW3` (GPL v2) dans les notices tierces de release, meme si deja transitivement utilise via Zyn
- [ ] Ajouter une notice tierce explicite pour `Mini-XML (mxml)` (Apache 2.0 + exception GPL/LGPL) avec `LICENSE` + `NOTICE`
- [ ] Ajouter une notice tierce explicite pour `rtosc` (licence type MIT) dans la doc de distribution
- [ ] Ajouter une notice tierce explicite pour `tlsf` (BSD) dans la doc de distribution
- [ ] Produire un fichier unique `THIRD_PARTY_NOTICES` (ou `LICENSES.md`) listant composant, licence, source et texte applicable
- [ ] Definir la strategie de publication du fork Android (repo public + instructions de build + notices de licence)
- [ ] Documenter les etapes GitHub pour publier la "source correspondante complete" (option repo vendored complet recommandee, option patch-only reproductible)
- [ ] Documenter le "minimum publiable" pour distribution binaire (source correspondante + patches + scripts/build files + notices)
- [ ] Decider explicitement le perimetre proprietaire vs open-source AVANT refonte UX majeure (si GUI proprietaire souhaitee, evaluer une separation technique/licence reelle)
- [ ] Ajouter un fichier `docs/porting/LICENSE_DISTRIBUTION_NOTES.md` (obligations GPL, attribution, offre de source, checklist release)
- [ ] Verifier la compatibilite de la strategie retenue avec la distribution Android (APK/AAB) avant publication
