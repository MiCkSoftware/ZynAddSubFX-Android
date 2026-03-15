# FFT Backend Strategy (Post-Bring-up)

## Contexte

Le backend actuel `fftw3f` est un shim local avec implementation FFT radix-2 "light"
(suffisante pour le bring-up et les scans de compat, mais pas encore consideree "production").

Objectif: choisir une solution FFT de production pour stabiliser:
- compatibilite presets
- performance CPU
- latence premiere note
- qualite sonore

## Etat actuel (baseline)

- API appelee par Zyn: interface de type `fftw3f`
- backend actuel: shim maison (radix-2) + fallback safe
- avantage: rapide a integrer, debloque le port
- limite: couverture/precision/perf a valider sur tous les cas DSP Zyn

## Etat actuel (diagnostic recent)

- Le backend `PROD_CANDIDATE` (radix-2 + Bluestein) a nettement ameliore la compatibilite presets (plusieurs cas `Silent -> OK`).
- Les presets PAD-only silencieux (ex: `Fantasy 1`) sont maintenant diagnostiques finement:
  - `NoteOn` accepte la note sur les parts actives (`broadcast ... -> p1=ok p2=ok`)
  - `peak=0.0000` sur les parts concernees
  - suspicion principale: chemin PAD / dependance FFT (et non mixer/routing/MIDI dispatch).
- Une tentative d'integration `FFTW3F_NATIVE` a ete faite via `third_party/fftw3`, mais le vendor actuel est incomplet:
  - codelets generes absents (`dft/scalar/codelets`, `rdft/scalar/r2cf`, `rdft/scalar/r2cb`, `rdft/scalar/r2r`)
  - echec au link sur les symboles `solvtab_*`.
- Un garde-fou CMake a ete ajoute pour produire une erreur explicite si `FFTW3F_NATIVE` est selectionne avec ce vendor incomplet.

## Options candidates

### Option A - FFTW3f "reel" (Android/NDK)

Avantages:
- proche de l'attente upstream
- risque fonctionnel plus faible si integration complete

Inconvenients:
- integration build/ABI plus lourde
- maintenance Android/NDK a securiser
- taille binaire potentiellement plus grande

### Option B - Lib FFT alternative (prod-friendly)

Exemples typiques (a evaluer selon licence / perf / API adaptation):
- KissFFT
- PFFFT
- Ooura / autres libs compactes

Avantages:
- souvent plus simple a embarquer
- footprint/taille maitrisables

Inconvenients:
- adaptation API `fftw3f` a maintenir
- risque de differences numeriques

### Option C - Shim actuel durci + optimisations progressives

Avantages:
- effort incremental
- pas de migration immediate

Inconvenients:
- dette technique/licence perf
- risque de plafonner avant "prod"

## Recommandation (provisoire)

1. Garder le shim actuel pour les iterations fonctionnelles courtes
2. Mettre en place un benchmark/scan cible "avant/apres FFT"
3. Integrer une option de production (FFTW3f ou alternative) derriere le meme wrapper
4. Comparer objectivement puis figer

## Criteres de decision (avant migration finale)

- Compat presets:
  - baisse des presets `Silent`
  - baisse des presets `Heavy`
- Latence:
  - `first-note ms` mediane et p95
- Stabilite:
  - pas d'ANR / crash / timeouts supplementaires
- Performance:
  - CPU sur appareil reel (quelques presets stressants)
- Maintenance:
  - build Android simple a reproduire
  - licences compatibles avec la distribution visee

## Plan de benchmark (simple)

1. Baseline actuelle (deja entamee via `ZynScan`)
2. Liste de presets "discriminants" (Silent/Heavy/OK)
3. Changer uniquement le backend FFT
4. Rejouer le meme sous-lot
5. Comparer:
   - `OK/Silent/Heavy`
   - `load_ms`
   - `first_note_ms`
   - `panic` usage
   - diagnostics `aaudioXruns`

## Notes d'implementation

- Garder l'API JNI / moteur / UI inchangee autant que possible
- Idealement introduire un switch de build (CMake option) pour alterner FFT backends
- Eviter de melanger migration FFT et refonte UI dans le meme lot
