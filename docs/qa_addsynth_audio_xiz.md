# QA AddSynth — audio, clipboard et XIZ

Fiche 007 **close avec accord de Mick le 1 novembre 2026**. Les verifications restantes sont reportees a la validation finale ADD. Les tests executes ci-dessous portent sur l'etat natif et Compose ; ils ne constituent pas une validation de l'ecoute comparative ni des gestes physiques.

## Environnement et reproduction

- Base Git : `9842960`, avec les modifications locales de cette QA ; Android `alpha.20-debug`, versionCode 20.
- Instrumentation : AVD `Pixel_9_Pro`, `emulator-5554`, Android 15/API 35, ARM64. Un Pixel 7 etait connecte mais n'a pas ete utilise.
- JNI initialise en 48 kHz / 256 frames dans les tests. Aucun flux audio reel mesure.
- Compilation native debug ARM64 et x86_64 pendant Gradle ; execution JNI uniquement ARM64.
- APK debug et APK de tests installes par Gradle sur l'emulateur uniquement. Pas de commit, push, publication ou installation sur appareil physique.
- Les modifications preexistantes dans `third_party/fftw3` et `third_party/mxml` sont conservees.

Commandes :

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Sources reproductibles : `app/src/androidTest/java/com/mick/zynaddsubfx/AddSynthRoundTripInstrumentedTest.kt` et `CommonModulesInstrumentedTest.kt` ; rapports generes : `app/build/reports/androidTests/connected/debug/index.html` et `app/build/test-results/testDebugUnitTest/`.

Resultat final : **24 tests JVM passes ; 15 tests instrumentes passes, aucun echec ni test ignore**. La derniere execution instrumentee dure 24 secondes (build inclus). `git diff --check` passe.

## Presets exacts

- `presets/banks/Strings/0067-Dual Strings Oct1.xiz` : utilise pour le cas « Dual String Ct1 » de la fiche, interprete comme `Oct1` ; aucun fichier nomme exactement `Ct1` trouve dans les presets embarques.
- `presets/banks/Companion/0078-Brassy.xiz`.
- `presets/banks/Choir and Voice/0065-Vocal Morph 1.xiz`.

## Matrice executee

| Cas / actions | Attendu et observe | Limite |
|---|---|---|
| Charger chacun des trois presets en part 0, exporter en cache, recharger en part 1 | Egalite de toutes les valeurs ADD exposees et des champs kit 0 ; part source conservee | Pas de comparaison PCM ni de preuve exhaustive des champs non exposes |
| Modifier kit 1 : mode kit, ADD+PAD, mute, voix OFF, fade-in 31, bend -31, offset 23, phase random 19 | Valeurs ecrites relues exactement ; export/reload en part 1 conserve ADD et flags kit 1 | Le test ne certifie pas le rendu PAD, ni les interactions audibles mute/activation |
| Enveloppe libre du modulateur, point 1 = 73 ; LFO depth 47/start 21 ; Formant voyelle 6/frequence 87, sequence 0 = voyelle 6 ; resonance point 17 = 93 | Toutes ces valeurs et la structure ADD exposee du kit restent identiques apres XIZ | Pas d'ecoute Formant/LFO/resonance |
| Clipboard Voice, oscillateur Voice et Modulator, cinq roles d'enveloppe de voix, trois roles LFO, filtre global Formant, voyelle 1, resonance | Source et cible initialement distinctes ; copie part 0 vers part 1, valeurs exposees du module egales, source inchangee | Meme index de voix/voyelle ; pas toutes les combinaisons de destinations |
| Clipboard de type incompatible | `canPasteModule` et `pasteModule` refusent ; snapshot cible inchange | Ne couvre pas tous les indices/adresses invalides |
| Clipboard enveloppe libre modFreq du kit 1 vers kit 0 | Points et valeurs conserves ; kit source intact, champs hors enveloppe du kit cible inchanges | Autres types entre kits restent a tester |
| Recharger Brassy apres edition de modOsc et LFO d'une voix desactivee | Snapshot ADD identique au premier chargement | Pas de stress audio ni de notes maintenues |
| Tests existants CommonModules | Revision export/edition suivante, isolation du chargement XIZ entre parts, parametres ADD/voix et scopes, preview enveloppe, clipboard amplitude et enveloppe, session multiparts, actions Compose | Pas de parcours complet retour systeme/reouverture |

## Regressions corrigees

1. **Export XIZ incomplet.** Reproduction : charger Dual Strings Oct1 puis Brassy en part 0 ; exporter Brassy et recharger en part 1. Le snapshot differe sur des enveloppes, LFO, voix desactivees et filtres. `Part::saveXML` utilisait le XML minimal qui omettait les donnees inactives. L'export explicite utilise desormais `xml.minimal = false` au niveau du proprietaire de la serialization. Les XIZ produits peuvent etre plus volumineux ; l'extension et le format restent XIZ.
2. **Heritage ADD d'un precedent instrument.** Reproduction deterministe : charger Brassy, modifier `add/voice/0/modOsc/harmonic/2/magnitude` a 39 et `add/voice/7/freqLfo/depth` a 47, recharger le meme Brassy. Les champs absents du preset conservaient ces editions. `Part::loadXMLinstrument` appelle maintenant `defaultsinstrument()` apres validation de la branche INSTRUMENT et avant sa lecture. Cela remplace les reglages de l'instrument cible en reutilisant le chemin de reset existant ; les controles du mixer de part ne sont pas reinitialises par ce chemin.
3. **Test de contexte obsolete.** L'assertion attendait uniquement le package release alors que le build debug utilise `.debug`. Le test accepte les deux identifiants connus.

Les erreurs initiales de chemins `oscillator/` et `modOscillator/` dans le nouveau test ont ete corrigees en `osc/` et `modOsc/` avant validation. Les tests de clipboard utilisent des marqueurs et verifient que les fixtures sont distinctes pour eviter un succes sur deux valeurs deja identiques.

## Validation finale restante

**Decision de Mick du 7 novembre 2026 : campagne differee jusqu'a SUBsynth, PADsynth et FX complets.** ADD est termine fonctionnellement dans le perimetre Android ; cette attente ne rouvre pas les features deja closes. La campagne commune est suivie dans [le backlog](backlog/README.md), lot 021. Les points ci-dessous conservent les limites des campagnes historiques ; les tests locaux des nouveaux lots continuent.

- Ecoute comparative des trois presets, avant/apres export ; envelopes standard/libres, filtres global/voix/bypass, Formant, LFO depth/start et resonance.
- Tous les modes du modulateur, sources internes/externes, references partagees et restrictions entre voix ; controles effectivement applicables.
- Isolation audible parts/kits/voix, ADD+PAD, activations et mute ; notes fantomes apres changements repetes, notes maintenues, All Notes Off/Panic.
- Clipboard : autres voix/roles de destination, changements de voyelle, tous les types entre kits, champs natifs non exposes par le snapshot.
- Conservation exhaustive des champs avances non exposes : inspection XML semantique ou assertions natives complementaires. La comparaison actuelle des snapshots ne suffit pas.
- Gestes longs/rapides/annules, retour systeme et barre, reouverture ; dirty/export suivi d'edition via parcours UI complet. Le test de revision ne prouve que le contrat d'etat.
- `defaultsinstrument()` remet notamment ADD et les kits a zero ; cette passe ne valide pas l'absence d'heritage de tous les parametres SUB/PAD du kit 0.

La fiche 007 est close et son prompt retire du backlog. Les points ci-dessus restent ouverts pour la validation finale ; leur report ne vaut pas validation audio ou tactile.


## Clôture OscilGen 006

Mick a validé les éditeurs Voice et Modulator le 6 novembre 2026. Les deux rôles partagent
les paramètres avancés, les aperçus natifs de base et de sortie (onde et spectre) et les
actions sur les harmoniques. **ONE** réinitialise le rang sélectionné et **ALL** réinitialise
la grille après confirmation. **Use as base** conserve les réglages en place. L’option
desktop **Auto clear** reste hors périmètre.

Les 24 tests JVM et 17 tests instrumentés passent sur Android 13 ARM64. Les cas OscilGen
couvrent les deux rôles, l’isolation de la voix qui partage une source, l’ordre filtre/waveshaping,
les aperçus, les actions, le clipboard et l’aller-retour XIZ d’une base personnalisée.

La reprise d’une part sans nom a aussi été vérifiée après arrêt forcé. La sérialisation et
l’écriture de la session s’exécutent sur un thread de fond. Un essai sur émulateur a confirmé
qu’une deuxième modification pendant l’écriture est conservée. La capture native reste
synchrone et a pris 92 à 99 ms avec l’audio actif dans cet essai.

## Correction du filtre global — 7 novembre 2026

Correction presente dans le code local : types/options/bornes par categorie via le meme contrat que le filtre Voice, Comb HPF/LPF exposes et sauvegardes/recharges par FilterParams XML. Le test instrumental `globalFilterCategoriesAndCombControlsSurviveXizReload` est ajoute (categories, bornes, isolation Voice, clipboard et XIZ). Le rapport instrumental disponible porte sur le 6 novembre, avant cet ajout : aucune nouvelle execution n'est affirmee ici. La validation finale audio/appareil reste differee selon la decision ci-dessus.
