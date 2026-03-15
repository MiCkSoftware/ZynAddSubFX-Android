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
- [ ] Integrer Oboe (optionnel pour robustesse/portabilite; non bloquant pour MIDI Android)
- [x] Mettre en place pipeline audio callback natif
- [x] Produire un son test (sinus si moteur Zyn non pret)
- [x] UI Compose minimale: init/start/test/stop
- [x] Ajouter seam natif `render(...)` + `noteOn/noteOff` (stub, pret pour Zyn)
- [x] Brancher un backend de rendu `zyn-master` experimental dans `ZynAndroidEngine` (fallback sinus conserve)
- [x] Gerer lifecycle audio (pause/resume) (auto-stop/auto-resume de base)
- [ ] Valider stabilite start/stop repetee

### M3 - Jouabilite sans MIDI
- [x] Brancher `noteOn/noteOff` reels vers moteur (backend `zyn-master` experimental)
- [x] Clavier virtuel Compose (mini clavier tactile 1 octave + release)
- [ ] Preset de test embarque
- [x] Presets de demo embarques (.xmz assets) + chargement local via `Master::loadXML`
- [x] Parametre de base: volume master (slider UI -> moteur)
- [x] Ajouter un bouton `Panic` (reset voix/FX) pour recuperer des notes fantomes / etats bloques
- [x] Parametres de jeu de base (velocity clavier + octave shift)
- [ ] Demo jouable tactile stable

### M4 - Architecture propre moteur/UI
- [x] Creer facade Kotlin `SynthEngine`
- [x] Ajouter queue de commandes UI -> audio
- [ ] Stabiliser contrat de parametres (`id`, type, bornes)
- [x] Ajouter logs/diagnostics JNI natifs
- [ ] Eviter locks/allocations sur audio thread

### M5 - Refonte UX Compose complete
- [ ] Definir navigation et ecrans (Library / Synth / Performance / Settings)
- [ ] Composants synth (knobs/sliders/enveloppes)
- [ ] Gestion etat via `ViewModel`
- [ ] Portrait/paysage + tablette
- [ ] UX de sauvegarde/restauration d’etat

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
