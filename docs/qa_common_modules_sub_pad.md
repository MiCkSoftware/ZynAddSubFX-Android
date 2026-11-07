# Modules communs ADD / SUB / PAD — lot 010

Cloture fonctionnelle locale du 7 novembre 2026. La validation finale comparative audio/appareil reste suivie par le lot 021, apres SUB, PAD et FX complets.

## Implementation

- `ModuleEngine` ajoute un argument moteur explicite Kotlin/JNI/native : ADD=0 (defaut), SUB=1, PAD=2. Les codes kind/role ADD restent inchanges ; bandwidth SUB utilise le nouveau role enveloppe 5. La navigation sauvegardee conserve aussi le moteur, avec compatibilite des anciennes destinations ADD.
- Le resolveur natif lit directement le proprietaire Part/kit et ses parametres ADD, SUB ou PAD. Copy, paste et preview partagent ce resolveur et refusent les roles/index absents. Aucun kit ADD temporaire.
- SUB : enveloppes amplitude, frequence, filtre et bandwidth ; filtre complet. Seuls frequence, bandwidth et filtre ont leurs flags natifs. Aucun LFO SUB ni faux ON d'enveloppe amplitude.
- PAD : trois enveloppes, trois LFO et filtre complet, sans faux ON. Oscillateur et resonance ont aussi leur adressage natif pour previews/clipboard ; leurs surfaces specifiques restent au lot 014.
- Lecture/ecriture des enveloppes, LFO, formants et filtres reutilise les primitives existantes. Types/bornes par categorie et Comb HPF/LPF ADD restent partages ; groupes ADD conserves. Routage Compose par metadonnees module/section, pas par labels.
- `CommonSynthModules`, les editeurs multipoints, formants/voyelles/sequence et le clipboard XML type complet sont reutilises. Les flags d'activation de destination ne sont pas copies par le clipboard de contenu. Les gestes conservent le chemin `dragParameter` / `finishParameterDrag`, sans snapshot ou preview global a chaque mouvement.
- Correction necessaire de l'activation : `Part::setKitSynthEnabled` alloue les parametres manquants lors de l'activation, notamment pour le kit zero neuf dont seul ADD existe initialement. Les entrees kit et les activations par part passent par ce proprietaire. OFF conserve les parametres ; reactiver ne les reinitialise pas.

## Executions locales

Commandes executees avec succes :

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:externalNativeBuildDebug :app:compileDebugAndroidTestKotlin --offline
python3 native/tests/run_common_modules.py --build-dir /tmp/zyn-common-modules-010
```

- 27 tests JVM, aucun echec/erreur/skip : modeles, routage independant des labels, roles absents et regressions existantes dont gestes ADD.
- Compilation native Android ARM64 et x86_64 avec le backend configure FFTW3F_NATIVE ; compilation des tests instrumentes, sans installation ni execution de ceux-ci.
- Test executable C++ sur macOS ARM64 : moteur reel, backend FFT LIGHT_SHIM, pas de pont JNI ni de rendu Compose. Le runner reprend la liste des sources du build Android et adapte uniquement la configuration hote ; il ne modifie pas le vendor.
- Contrats natifs verifies : activation initiale SUB/PAD, enveloppe bandwidth libre (insertion, temps/valeur, sustain OFF), copie SUB -> PAD entre parts/kits et isolation des voisins ; maintien OFF/ON ; refus des modules/roles/index absents et destinations incompatibles ; LFO PAD -> voix ADD ; matrice Formant complete incluant une entree masquee et sequence ; copie d'une voyelle PAD -> ADD avec isolation de sa voisine ; categories/limites Comb et clipboard HPF/LPF ; adressage oscillateur/resonance PAD ; regression enveloppe ADD entre roles ; restauration du mode simple ADD/SUB/PAD ; metadonnees/absence de faux flags ; XIZ du filtre SUB dans un moteur active.

## Limites et suites conservees

- Aucune ecoute, installation, interaction Compose ou validation JNI sur appareil/emulateur n'est revendiquee. Les tests instrumentes existants, dont la nouvelle regression XIZ du filtre ADD, sont compiles mais pas executes. Campagne finale au lot 021.
- L'hote utilise le FFT shim ; il ne qualifie pas les samples ou performances PAD de production. Profil/graphes et generation restent aux lots 014/015.
- Constat cible pour 020 : `Part::add2XMLinstrument` omet les branches des moteurs entierement OFF lorsque `SaveFullXml` est faux, meme avec `minimal=false`. Le maintien OFF/ON en memoire est teste ici ; l'export XIZ des moteurs OFF et la matrice complete XIZ/XMZ doivent etre traites/verifies au lot 020. Ne pas assimiler cette cloture a une validation globale de persistance.
- Aucune modification commitee, poussee ou publiee par cette implementation.

## Retour visuel de Mick apres 010

Les captures SUB montrent les surfaces draft encore presentes hors modules communs. Correction locale : Stereo retire de l'editeur SUB/PAD (reste dans l'ecran kit), controles directs rendus par les composants denses ADD et places avant les modules en Amp/Frequency. Compilation Kotlin et 27 tests JVM reverifies ; aucun deploiement/appareil.

Le socle 010 ne constitue pas une parite SUB/PAD complete : amplitude SUB manque encore Volume/velocity et Frequency manque les parametres de jeu du lot 011. SUB possede naturellement moins de modules amplitude qu'ADD (pas de LFO/punch natifs equivalents), mais cela ne justifie pas les champs omis. Les 64 couples magnitude/largeur restent une grille draft jusqu'a la conception du lot 012.

Reference d'origine verifiee : `third_party/zynaddsubfx/src/UI/SUBnoteUI.fl`. Amplitude = Volume, V.Sns, Pan et enveloppe ; Bandwidth = largeur globale, scale et enveloppe optionnelle ; harmoniques = 64 colonnes numerotees, chacune avec deux sliders verticaux superposes (magnitude en haut, largeur relative en bas), defilement horizontal. Overtones et options de magnitude/start/stages sont des groupes distincts. La nouvelle UX doit distinguer largeur globale, enveloppe et largeur par harmonique, et faire valider son design au lot 012.

Comparaison ciblee au source natif apres retour utilisateur :

| Famille SUB | Present apres 010 | Reste a couvrir |
|---|---|---|
| Amplitude | Panning, enveloppe complete | Volume et AmpVelocityScaleFunction (011) |
| Frequency | Fixed frequency, enveloppe et activation | Fine/type/coarse/octave detune, bend, offset Hz, fixedfreqET (011) |
| Bandwidth | Largeur, scale, enveloppe et activation | Organisation de la surface (011/012) |
| Filter | Filtre complet, velocity, enveloppe et activation | Validation transverse du mapping en 011/020 |
| Harmoniques | 64 magnitudes + 64 largeurs relatives en grille | Phmagtype, Overtones type/par1/par2/par3, Clear et UX graphique (012) |
