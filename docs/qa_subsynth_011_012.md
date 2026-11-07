# SUBsynth — cloture fonctionnelle des lots 011/012

8 novembre 2026. Proposition validee par Mick dans ce chat : implementation autorisee, avec reutilisation des **knobs enum et popups existants**, plutot que des combos. [Proposition et inventaire d'origine](subsynth_design_proposal.md).

## Comportement implemente

Cinq sections Amp / Harmonics / Frequency / Bandwidth / Filter. Stereo reste au niveau du moteur dans l'ecran kit ; aucun Global vide ni LFO artificiel. Clavier, contexte Part/Kit/SUB et navigation existants conserves.

- Amp : Volume et velocite ajoutes avec les conversions du fork SUB (floats natifs, anciennes commandes d'origine 0..127), panning avec Random a 0, enveloppe complete.
- Frequency : detune fin/type, octave/coarse dans leurs champs binaires natifs independants, bend, offset Hz, frequence fixe et temperament (controle inactif si frequence fixe OFF), enveloppe et activation. Valeurs musicales du detune via le convertisseur natif existant ; affichage du bend/offset selon les transformations UI d'origine.
- Bandwidth : largeur globale, scale, nombre d'etages des filtres de bruit (1..5) et enveloppe optionnelle. Section/metadonnees propres, distinctes des largeurs par harmonique et des etages du filtre global (1..6). Defaut de largeur corrige a 40, comme le source SUB.
- Filter : reutilisation du filtre/enveloppe et des editeurs Formant/Comb communs implementes dans 010, avec leurs flags et champs complets.
- Harmonics : selection de rang 1..64, magnitude, largeur relative et reset ONE/ALL dans les composants ADD ; reutilisation de HarmonicOverview avec un rendu unipolaire SUB. Tap selectionne sans ecrire ; gestes horizontaux de selection et verticaux d'edition conserves. Bascule Magnitude/Bandwidth ; zero = silence, largeur 64 = neutre. Les 128 cartes draft disparaissent.
- Magnitude scale : Linear, -40/-60/-80/-100 dB. Start : Zero/Random/Max. Overtones : huit modes d'origine et trois parametres (0..255). Les controles non utilises par le mode sont inactifs sans effacement. Tous les enums passent par DenseParameterControl, TinyKnob et EnumChoiceMenu existants.
- Sous-ecran Harmonics avec preview des positions/bandes en tete, reduite au scroll via CollapsingPreviewLayout comme Oscillator. Axe logarithmique fixe, sans libelle redondant. Le natif fournit 64 triplets : multiplicateur frequentiel, magnitude convertie (silence force a zero), largeur en octaves a la note de reference (derniere note du clavier SUB, A4 par defaut). Les formes sont des bandes de parametres, pas une mesure du signal audio ou la reponse complete des filtres.

## Proprietaires, clipboard et reset

Le nouveau ModuleAddress.SubHarmonics utilise le moteur SUB, kind=9, index=-1, role=0 ; les codes precedents restent inchanges. Preview/clipboard refusent ADD/PAD et les index de voix.

La copie utilise le serialiseur XML natif complet SUB, avec minimal=false pour conserver les largeurs des harmoniques silencieuses. Au collage, un SUBnoteParameters source lit ce XML et le proprietaire destination applique seulement pasteHarmonics : les 64 magnitudes/largeurs, magnitude scale, start et Overtones. Ni amplitude, ni detune/enveloppes, ni filtre, ni bandwidth globale/stages ne sont modifies. Les multiplicateurs sont recalcules au niveau natif.

SUBnoteParameters::resetHarmonics est partage par defaults, la commande Clear d'origine et l'entree Android : fondamentale 127, autres magnitudes 0, toutes les largeurs 64. ALL demande confirmation. Les autres sections, Overtones et magnitude scale sont preserves ; ONE ecrit les defauts des deux champs du rang selectionne.

Part::defaultsinstrument remet maintenant aussi a leurs defauts les parametres SUB/PAD deja alloues au kit zero. Sans cette correction, un chargement de preset partiel pouvait heriter des champs du moteur precedent. Le reset reste au proprietaire Part.

## Chemin continu

Les gestes passent par dragParameter et son etat transitoire reduit ; snapshot et revision restent stables pendant le mouvement. Les collections des 64 rangs/bandes sont memorisees sur le snapshot/preview, pas reconstruites pour chaque valeur ou selection.

Les previews natifs sont lus a la revision de commit pour la vignette de lancement et le sous-ecran ouvert ; le defilement et la selection seuls ne les recalculent pas. Les mutations ordinaires SUB ne recalculent plus les positions de tous les overtones ; updateFrequencyMultipliers est reserve aux changements de repartition et collages. Aucun snapshot ou preview couteux par mouvement.

## Validation executee

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:externalNativeBuildDebug :app:compileDebugAndroidTestKotlin --offline
python3 native/tests/run_common_modules.py --build-dir /tmp/zyn-common-modules-010
```

- **29 tests JVM passes**, aucun echec/erreur/skip. Ajouts : separation Bandwidth/Harmonics et exclusion Stereo, navigation SUB, geste SUB de 256 mouvements sans snapshot/preview intermediaire, reconciliation unique et contexte part/kit conserve. Regressions ADD et socle 010 toujours executees.
- **Builds natifs ARM64/x86_64 avec FFTW3F_NATIVE passes**, compilation Kotlin et tests instrumentes passes. Avertissements C++ vendor existants ; aucun echec.
- **Contrat natif hote passe** (macOS ARM64, FFT LIGHT_SHIM) : tous les nouveaux champs, bornes, octave/coarse aux extremes et sans ecrasement mutuel ; copie des 64 rangs dont valeurs masquees et du rang 64, champs de repartition, incompatibilites/isolation moteurs/parts/kits ; Clear sans mutation des autres modules ; XIZ SUB seul puis kit mixte, enveloppes/filtre OFF dans un moteur actif ; huit modes de repartition et ForceH, cinq conversions de magnitude, largeur relative, champs/metadonnees ; chargement d'un preset ADD partiel apres SUB sans heritage du kit zero.

## Limites conservees

Aucune installation, interaction Compose ou ecoute sur appareil/emulateur effectuee. Les tests instrumentes sont compiles, pas executes. L'approbation de la proposition de design ne constitue pas une validation du rendu Compose final. QA visuelle, gestes tactiles, accessibilite, stress et audio restent au lot 021.

Le runner hote utilise le shim FFT et ne qualifie pas les performances/FFT de production ; les builds Android compilent le backend de production. L'apercu des bandes n'inclut pas les envelopes en cours de note, controleurs ou reponses audio completes.

La matrice complete de persistance et l'export XIZ des moteurs entierement OFF restent au lot 020 (limite SaveFullXml documentee dans le rapport 010). Le reset de preset partiel traite ici n'affirme pas que toute la matrice PAD/session a ete validee.

Aucun commit, push, publication ou installation effectue par cette implementation.

## Iteration UX apres retour sur appareil — 8 novembre 2026

Retour de Mick : le bloc harmonique doit avoir son sous-ecran, l'ancien bloc depliable joue le role de preview et doit suivre le modele Oscillator ; integrer les metriques dans le graphe et retirer les doublons gain/width ainsi que le libelle invariant logarithmic.

Correction locale : destination sauvegardable SubHarmonics avec retour vers SUB, clavier et actions C/P ; vignette cliquable dans la vue SUB ; sous-ecran Preview/Harmonics/Overtones avec ancres et CollapsingPreviewLayout existant (210 -> 104 dp). Preview toujours en tete avec fond opaque, sans toggle collapsible. Rang selectionne et rapport a f0 dans le bandeau du graphe, repere A4/440 Hz en badge et graduations de rapports sur l'axe. Suppression des deux lignes de metriques sous le graphe et des duplications gain/width. Logarithmique est un choix fixe de l'axe et non un parametre du son ; pas de libelle permanent.

Compilation Kotlin et 29 tests JVM reverifies avec succes. Aucune installation/QA visuelle du rendu Compose final de cette iteration ; rendu, navigation et reduction au scroll restent a verifier sur appareil. Backend natif inchange dans cette iteration.

## Note de reference et couleurs des bandes — 8 novembre 2026

Decision de Mick : suivi de note accepte ; aucune selection dans la preview ; attribuer une hue a chaque bande.

La reference suit maintenant la derniere touche jouee sur le clavier des ecrans SUB, conservee apres relachement et entre sous-ecrans ; A4 est seulement la valeur initiale. Le badge affiche le nom de note et sa frequence de reference en temperament egal. Cette reference est un etat de presentation, sans mutation de l'instrument ; elle ne pretend pas inclure detune, temperament/frequence fixe ou controleurs du signal joue.

SubHarmonicsPreview porte la note via Kotlin/JNI jusqu'au calcul natif de largeur ; ratios et magnitudes restent independants de la reference. Les changements de note declenchent une preview bornee a 64 bandes, sans snapshot ni recalcul de l'instrument. Les gestes de parametres continuent a reconcilier en fin de geste.

Chaque rang recoit une hue deterministe, partagee avec son rang dans l'editeur du bas. La selection conserve la hue et utilise une epaisseur/ligne de repere distincte. Des points discrets au pied du graphe indiquent les bandes actives meme faibles ; les hauteurs de magnitude ne sont pas gonflees. Graduations par octave lorsque la largeur le permet. La preview du sous-ecran ne gere aucun geste de selection ; la vignette de la vue SUB conserve seulement son action de navigation.

Validation : compilation Kotlin/JNI/ARM64/x86_64 et tests instrumentes compiles ; 29 tests JVM passes. Contrat natif hote passe, incluant A4 par defaut, reference A4/A5, invariance a bandwidthScale neutre, dependance de largeur selon l'echelle native, note minimale/maximale, rejet des notes hors bornes et absence de mutation des parametres par la preview. QA visuelle/appareil non executee pour cette iteration.

## Essai de preview sur axe absolu — 8 novembre 2026

Apres constat que les rapports a f0 masquaient le mouvement a chaque note, Mick autorise l'essai d'un axe fixe en Hz. La preview utilise desormais 20 Hz..20 kHz, toujours logarithmique, sans recadrage automatique. Le centre d'une bande est le multiplicateur natif fois la frequence de reference ; le bandeau conserve le rang, sa frequence et son rapport a f0. Les centres hors champ ne sont pas empiles artificiellement sur les bords ; les portions de bandes qui intersectent la fenetre restent dessinees. Les hues et l'absence de selection tactile sont conserves.

Verification du soupcon de largeur lineaire : SUBnoteParameters::convertBandwidth fournit une largeur en octaves, utilisee par SUBnote::computefiltercoefs. A bandwidthScale neutre et largeurs relatives identiques, les largeurs en octaves sont identiques mais celles en Hz croissent avec la frequence. Le dessin d'egale largeur sur axe log n'est donc pas un bug. Exemple : 0,1 octave couvre environ 6,933 Hz autour de 100 Hz, contre 69,329 Hz autour de 1000 Hz. La largeur du remplissage n'est plus forcee a un pixel ; les reperes centraux/pieds assurent la lisibilite des bandes tres fines.

Compilation Kotlin et 29 tests JVM passes ; calculs de projection verifies pour A4/A5 (fractions 0,4475 et 0,5478 de l'axe fixe). Aucun backend audio modifie, aucune installation/QA visuelle sur appareil pour cette iteration.

## Dernier ajustement de navigation — 8 novembre 2026

A la demande de Mick, Harmonics est place entre Amp et Frequency dans l'ordre des onglets et du contenu SUB. Le texte « Edit harmonics » est retire ; la vignette conserve son action d'ouverture du sous-ecran. Compilation Kotlin et 29 tests JVM reverifies. SUB est termine fonctionnellement pour les lots 010/011/012 ; restent les verifications/corrections de persistance transverse 020 (notamment XIZ moteur entierement OFF) et la validation finale audio/appareil 021 deja differee.

## Cloture de la serie — 8 novembre 2026

Mick accepte la cloture de cette serie apres implementation et iterations sur captures. Les lots 010/011/012 sont termines fonctionnellement dans le perimetre Android retenu.

Derniers ajustements : ordre Amp / Harmonics / Frequency / Bandwidth / Filter ; retrait du lien redondant Edit harmonics ; suppression des textes d'aide autour du graphe SUB puis du texte equivalent des harmoniques ADD Voice/Modulator. Les gestes et controles restent presents. Les decisions de preview (sous-ecran, reduction au scroll, axe fixe en Hz, note de reference, hues par rang, absence de selection tactile) sont conservees.

Le preset fourni Organ/0067-Sub_Organ.xiz a ete inspecte comme candidat au controle : kit zero actif, SUB seul, ADD/PAD OFF, quatre harmoniques actives. Ce constat de contenu ne constitue pas une validation audio du preset sur appareil.

La cloture utilisateur porte sur la serie fonctionnelle/UX ; elle ne declare ni une campagne audio/appareil comparative complete ni la matrice complete de persistance. Le cas connu d'export XIZ avec moteur entierement OFF reste au lot 020 ; la campagne finale commune reste au lot 021.

Verification de cloture : compilation Kotlin et 29 tests JVM passes sur l’etat final, apres suppression des textes d’aide SUB/ADD.
