# PADsynth — lots 013, 014 et 015

8 novembre 2026. Implementation locale apres validation explicite par Mick des
[designs 014 et 015](padsynth_design_proposal.md). Mick autorise les tests
instrumentes ADB et prend en charge les tests finaux manuels. Aucun commit/push.

## Parametres de jeu (013)

- Amplitude : volume, panning, velocite, fade-in et les quatre champs Punch.
  Volume respecte le gain natif `4 * 10^(-3*(1-PVolume/96))` : l'affichage dB
  inclut le facteur quatre du PAD, distinct du volume SUB. Fade-in affiche le
  facteur quadratique relatif au defaut natif 20 ; Punch time affiche 0,1..100 ms
  avant l'etirement par note. Velocite 127 desactive la dependance a la velocite.
- Frequency : detune fin/type, octave et coarse dans leurs champs binaires
  independants, bend, offset Hz, frequence fixe et temperament. Type 1..4,
  detune 0..16383, octave -8..7, coarse/bend/offset -64..63. Temperament grise
  lorsque fixed frequency est OFF, sans effacer sa valeur.
- Trois enveloppes et trois LFO PAD, filtre complet et ses deux champs velocity :
  composants, previews et C/P communs de 010 reutilises, proprietaire PAD explicite.
  Aucun faux ON de filtre, enveloppe ou LFO.
- Amplitude/Frequency/Filter reutilisent maintenant une meme section dense avec
  SUB ; Stereo reste au niveau du kit. Aucune regeneration pour ces parametres
  de jeu. Stereo affecte les notes PAD, pas la generation des samples.

## Structure, oscillateur et resonance (014)

Ordre actuel apres retour de Mick : **Amp / PAD / Frequency / Filter**.
Le groupe PAD affiche **Spectrum / Quality a la suite**, comme des sous-parties
du meme contenu defilant, sans sous-onglets. Profile rejoint les cartes graphiques
compactes d’Oscillator, Resonance et Overtones dans Spectrum ;
leur statut de generation et leur bouton Apply sont communs.

- Spectrum : mode Bandwidth/Discrete/Continuous, largeur en cents et les huit
  echelles natives. Correction du domaine bandwidthScale : 0..7, defaut 0,
  plutot que l'ancien 0..127/64. En Discrete/Continuous, largeur/echelle et les
  controles Profile sont grises, valeurs conservees.
- Profile : tous les champs Php (base type/parameter, width/side/autoscale,
  frequency multiplier, modulator amount/frequency, amplitude type/mode/par1/par2).
  Sous-ecran a preview native `getprofile`, reduite au scroll ; amplitude Off
  grise les champs non applicables. Axe relatif a une harmonique, pas spectre audio.
- Overtones : huit modes et par1/par2/par3 0..255, valeurs masquees conservees.
  Sous-ecran a 64 positions `getNhr`, preview reduite au scroll, hues par rang,
  axe fixe 20 Hz..20 kHz et derniere note jouee (A4 initiale), sans selection
  tactile. Traits de positions, sans inventer une largeur ou amplitude audio.
- Les surfaces Oscillator et Resonance ADD sont partagees avec PAD. Modeles,
  navigation sauvegardee, actions, controles avances, harmoniques/phases,
  base personnalisee et previews gardent leur moteur et leur index natifs.
- Descriptors et mutations oscillateur/resonance partagent les primitives ADD/PAD.
  Les changements ordinaires d'oscillateur ne font plus `prepare()` par mouvement :
  preparation native a la lecture/preview utile. `OscilGen::useasbase` et
  `getcurrentbasefunction` preparent maintenant les valeurs en attente au niveau
  proprietaire, pour eviter de convertir/afficher une base ancienne.
- C/P Profile et Overtones sont types (kinds 10/11), PAD uniquement et index -1.
  Serialisation XML native complete, application limitee a Php ou Phrpos par leur
  proprietaire ; incompatibilites et valeurs cachees couvertes. Oscillator et
  Resonance conservent les types partages existants, y compris ADD <-> PAD.

## Qualite et regeneration (015)

- Choix : 16k..1M frames, dix notes C2/G2..C6/G6 (C4 par defaut), 1..8 octaves
  (stockage 0..7), 0.5/1/2/3/4/6/12 samples par octave. Le compte et l'estimation
  PCM viennent du proprietaire natif, y compris le cas 0.5 et PAD_MAX_SAMPLES.
- Quality affiche compte, duree par sample et memoire PCM. Le groupe PAD porte
  Apply explicite pour Spectrum, Profile et Quality.
  Statut dans l'en-tete PAD ; progression n/N, Annuler, attente de fin des notes,
  succes discret, erreur utile et Retry. Une annulation conserve les parametres.
- `PADnoteParameters` possede les revisions structure/applied. Les parametres de
  jeu ne changent pas la revision structure. Les champs Spectrum/Profile/Quality,
  actions et collages structurels la changent et invalident le job correspondant.
  Dirty de sauvegarde reste independant du fait que les samples sont appliques.
- Apply capture le XML natif sur le contexte d'edition, puis genere depuis un
  objet PAD isole et son propre SYNTH_T/FFT sur un worker. `sampleGenerator` utilise
  un seul thread de calcul pour borner la memoire de travail et propager les erreurs.
  Les temporaires FFT/spectrum/sample ont des proprietaires RAII.
- Limites : 128 MiB PCM par banque, 30 secondes de calcul ; refus avant allocation
  de la banque si qualite invalide ou trop grande. Annulation controlee entre les
  samples ; une FFT deja en cours finit avant l'arret du worker.
- La banque complete et finie est publiee apres nettoyage du calcul. Au debut
  d'un bloc audio, un try-lock sans attente verifie contexte/revision puis demande
  a Part si des notes PAD du kit utilisent encore l'ancienne banque. Les buffers
  sont echanges par leur proprietaire seulement quand ces notes sont terminees
  (y compris release/sustain). Les anciens buffers restent au job pour liberation
  hors thread audio. Aucun PCM dans Compose, aucune banque partielle installee.
- Les revisions empechent un resultat obsolete de remplacer une edition plus
  recente. Annuler est non bloquant ; changer part/kit/moteur ou fermer l'editeur
  demande l'annulation. Preset, reinitialisation et fermeture du moteur joignent
  le worker avant de detruire les proprietaires natifs.
- RNG natif rendu local au thread : OscilGen sauvegarde/restaure son etat aleatoire
  et le generateur de samples utilisait le meme RNG global que l'audio ; des
  contextes independants evitent leur course pendant une regeneration asynchrone.
- XIZ/XMZ conservent les parametres courants, meme avant Apply. Au chargement, le
  chemin existant regenere les samples ; il utilise maintenant un seul thread et
  refuse le succes si une banque PAD active n'a pas ete completement appliquee.
  Le budget de chargement est porte de 1 200 ms a 30 s, coherent avec Apply.
  Le chargement des presets est deja execute via Dispatchers.IO dans MainActivity.
  Un cas XIZ de huit samples 1M (32 MiB) couvre le rechargement de grosse banque.

## Gestes et previews

Les mouvements gardent l'ecriture necessaire et les etats transitoires existants ;
les snapshots/revisions/previews sont reconcilies en fin de geste. Les collections
harmoniques/controles et les courbes sont memorisees sur le snapshot/revision.
Le polling du job a 150 ms ne relit pas de snapshot et n'avance pas la revision
parametrique. Les filtres de points du grand editeur Resonance sont aussi memorises.
Les previews Profile/Overtones ne generent jamais une banque de samples.

## Verifications locales

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline
python3 native/tests/run_common_modules.py --build-dir /tmp/zyn-common-modules-010
```

- 31 tests JVM passes, aucun echec/erreur/skip : routage Amp/Frequency/Filter
  structure, gestes SUB/PAD sans snapshot intermediaire, polling Apply sans
  snapshot/revision parametrique, regressions ADD.
- Builds Android ARM64/x86_64 avec FFTW3F_NATIVE, Kotlin/JNI et APK instrumente OK.
- Contrat natif macOS ARM64 LIGHT_SHIM passe : defauts/bornes et champs binaires
  PAD, absence de faux flags, isolation moteurs/parts/kits, previews et clipboard
  des trois enveloppes/LFO/filtre, XIZ PAD seul et kit mixte, profil/positions et
  valeurs masquees, huit modes de positions et force harmonics, base personnalisee
  C/P/XIZ, attente d'une note active avant echange, revision obsolete, limite memoire,
  preset pendant interruption et regressions communes ADD/SUB.

## Verifications ADB

Suites executees via `adb -s DEVICE shell am instrument -w -r -e class ...
com.mick.zynaddsubfx.debug.test/androidx.test.runner.AndroidJUnitRunner` :

| Appareil | Systeme lu via ADB | Contrats PAD/communs/ADD | Parcours Compose PAD |
|---|---|---|---|
| Pixel 7 | Android 17, API 37 | 25 passes, 0 echec (86,659 s) | 1 passe, 0 echec (7,055 s) |
| Nokia X20 | Android 14, API 34 | 25 passes, 0 echec (111,987 s) | 1 passe, 0 echec (11,718 s) |

Classes : PadSynthParametersInstrumentedTest, PadSynthStructureInstrumentedTest,
PadSynthGenerationInstrumentedTest, CommonModulesInstrumentedTest,
AddSynthRoundTripInstrumentedTest ; PadSynthEditorInstrumentedTest execute
separement apres reveil/deverrouillage de l'ecran.

Les tests verifies sur chaque appareil couvrent : bornes/defauts et isolation
PAD/ADD, XIZ PAD seul et kit mixte, toutes les familles de modules PAD, copie
profile/positions avec valeurs masquees, les trois modes PAD, base personnalisee,
qualites 16k/64k/256k/1M, PCM fini non nul, controle AAudio PAD seul (fallback OFF),
annulation/revision obsolete, refus memoire, restauration XMZ d'edits avant Apply,
regressions ADD dont categories du filtre/Comb XML et clipboard, composant commun
enveloppe, navigation PAD Spectrum -> Oscillator, Profile -> C/P, Quality -> Apply
et affichage du succes. Le test de viewport fait defiler Apply avant le tap.

Mesures natives relevees dans logcat sur Nokia X20, une seule sample par banque
(oct=0, smpoct=0), FFTW3F_NATIVE :

| Frames | Appel Apply (capture/lancement) | Calcul/nettoyage worker | PCM (octets, garde incluse) | Pic absolu |
|---|---|---|---|---|
| 16 384 | 7 ms | 53 ms | 65 556 | 0,195428 |
| 65 536 | 12 ms | 48 ms | 262 164 | 0,175456 |
| 262 144 | 29 ms | 96 ms | 1 048 596 | 0,254540 |
| 1 048 576 | 35 ms | 255 ms | 4 194 324 | 0,431013 |

Le rechargement XIZ 32 MiB a pris **1 394 ms** sur Nokia X20 ; ce cas
depassait effectivement le budget historique de 1 200 ms. Il passe avec 30 s,
ainsi que sur Pixel 7.

Sur Pixel 7, ces quatre cas et les assertions lancement <1 s/calcul <30 s ont
passe ; les lignes stdout de mesures n'etaient pas disponibles dans logcat lors
de la collecte. Aucun temps exact Pixel n'est invente. Ces petits bancs techniques
ne constituent pas un benchmark de toutes les qualites ou de la pression audio.

Le dernier etat Kotlin/JNI est recompile apres ajout de l'annulation lors d'un
changement de kit interne ; 31 tests JVM et le contrat natif (dont suppression/
recreation d'un kit pendant un job) passes. La passe finale ADB inclut aussi le
rechargement XIZ de huit samples 1M (32 MiB), avec le budget de chargement corrige.

Premiere execution : deux nouveaux tests d'isolation supposaient a tort Volume
ADD=96 ; ils comparent maintenant l'etat reel initial (90 sur Android). La boucle
instrumentee ADD exclut le role enveloppe Bandwidth specifique SUB, ajoute depuis
010. Le test Compose commun a ete relance ecran reveille/deverrouille. Un test
Pixel 7 a expose la publication trop precoce de la fin de generation ; elle est
corrigee au niveau worker avant la passe de 25 tests reussie sur les deux appareils.
Le premier test du nouveau parcours UI confondait libelle d’action et description
semantique ; les previews PAD ont maintenant une description accessible explicite.
Sur Pixel, Apply doit etre amene dans le viewport avant l’injection tactile.

## Limites conservees

- Les tests techniques PCM/AAudio et Compose cibles ne sont pas la validation
  finale comparative audio, UX tactile, accessibilite et stress. Mick prend en
  charge les tests finaux ; le suivi commun reste 021 apres achevement des FX.
- Aucun profilage general des xruns/audio sous charge, aucune garantie de temps
  pour toutes les combinaisons de qualite. La limite est par banque ; l'ancienne
  et la nouvelle banque peuvent coexister pendant le calcul/l'attente d'application.
- Generation et chargement restent bornes dans le temps (30 s) et par la limite
  PCM ; la session complete partage son budget de chargement entre ses parts.
  La matrice complete de persistance reste 020, dont XIZ moteurs entierement OFF
  et sessions avec parts desactivees.
- L'hote LIGHT_SHIM ne qualifie pas le FFT de production ; les tests ADB utilisent
  FFTW3F_NATIVE. Les graphes de parametres ne mesurent pas le signal audio final.
- APK debug installe pour les tests autorises sur Pixel 7 et Nokia X20. Aucun
  commit, push, publication ni installation release. Les sous-modules FFTW/mxml
  preexistants n'ont pas ete modifies par ce travail.

## Regroupement des parametres PAD — 8 novembre 2026

Apres l'essai sur Pixel, Mick souligne que l'apparence identique des controles
live et des parametres qui demandent Apply prete a confusion. Il demande un groupe
principal PAD contenant Spectrum, Profile et Quality.

Implementation finale precisee par Mick : onglets principaux Amp / PAD / Frequency /
Filter ; **Spectrum, Profile et Quality affiches a la suite dans PAD**, sans
sous-onglets. Les en-tetes de sous-partie utilisent le meme composant et le meme
traitement sans fond de grande section que les enveloppes/LFO sous Amp. Le groupe
PAD conserve son en-tete principal et un seul bloc commun statut/progression et
Apply/Cancel. Le badge « PAD pending » initial a ete retire lors de l’ajustement
suivant, au profit du bouton Apply dans l’en-tete PAD.

La premiere interpretation avec sous-onglets a ete retiree apres correction de
Mick ; aucun etat de selection de sous-categorie n'est conserve. La barre d'onglets
principale reprend son implementation existante.

Validation de l'iteration finale : assembleDebug/assembleDebugAndroidTest et
**32 tests JVM passes** ; parcours Compose Nokia X20 passe (9,94 s).
Le test verifie l'absence de role Tab pour
Spectrum/Profile/Quality, leur presence simultanee comme en-tetes dans le contenu,
le defilement jusqu'a Profile puis Quality, le retour des editeurs Oscillator et
Profile, et l'action Apply commune.

APK debug mis a jour sur Nokia pour ce test. Pixel laisse sur la version precedente
pendant les essais manuels de Mick afin de ne pas interrompre ses reglages en cours.

## Presentation des graphes et Apply — 8 novembre 2026

Demande de Mick : Oscillator et Resonance chacun sur une ligne, fonds des graphes
harmonises, aucun texte visible « Pending changes »/« Samples up to date » ; Apply
colore/clignotant a droite de l'en-tete PAD.

Les deux previews sont maintenant pleine largeur (100 dp de haut chacune).
ModulePreview (dont enveloppes/LFO), graphes d'oscillateur, resonance et positions
partagent SynthGraphBackground, avec le meme fond sombre 0xFF050A0C. La resonance
reprend aussi les coins arrondis des autres previews.

Apply et Cancel sont les actions de SectionBanner("PAD"). Apply est orange et
pulse entre 0,35 et 1 d'opacite (700 ms par demi-cycle) lorsque des changements
attendent leur generation ; vert et inactif quand applique ; rouge clignotant en
cas d'erreur ; cyan et inactif pendant le calcul. L'animation est lue dans la couche
graphique du bouton, sans recomposition par frame du groupe/previews ni lecture
native par frame. Elle disparait lorsqu'aucune generation n'est attendue.
Les messages de calcul, attente des anciennes notes, annulation en cours et erreur
restent utiles ; les statuts de repos sont seulement accessibles via la semantique
du bouton. Le badge supplementaire « PAD pending » est retire.

Validation : builds debug/test et 32 tests JVM passes ; parcours Compose Nokia X20
passe (10,277 s), avec previews Oscillator/Resonance accessibles, Apply actif puis
inactif apres generation, statut accessible mis a jour et absence des deux textes
visibles. APK debug mis a jour sur Nokia ; Pixel conserve en l'etat pendant les
reglages manuels de Mick. Aucun code natif modifie dans cette iteration.

## Cartes graphiques compactes PAD — 8 novembre 2026

Mick demande finalement des apercus compacts au format des knobs, a la suite des
trois controles Spectrum, puis propose d'y joindre Profile. Profile represente la
forme d'une seule harmonique : ce role justifie son editeur detaille, mais pas une
sous-partie separee dans l'apercu de l'instrument. Tous ces champs participent a la
meme generation explicite des samples.

Le groupe PAD contient maintenant Spectrum puis Quality. Spectrum dispose d'une
grille locale de quatre colonnes : ses trois knobs existants, puis Oscillator,
Resonance, Overtones et Profile dans des DenseControlCard de meme taille. Premier
rang : trois knobs + Oscillator ; second : Resonance, Overtones, Profile et une
place vide. Chaque carte ouvre son editeur. Profile conserve son apparence grisee
lorsque son mode est inapplicable et reste consultable.

Les cartes reemploient les courbes natives memorisees, le fond commun et les
composants existants. La miniature Overtones montre seulement les positions sur
l'axe fixe en Hz, sans badges/axes textes illisibles a cette taille. Aucun layout
ou composant ADD/SUB partage n'a ete modifie pour cette iteration. Les grands
apercus/editeurs ADD et SUB gardent leur disposition.

Verification : builds debug/instrumentes et 32 tests JVM passes ; parcours Compose
Nokia X20 passe (13,182 s), incluant ouverture d'Overtones/Oscillator/Profile,
absence de section Profile separee, defilement vers Quality et Apply. Capture de
controle visuel lue apres le test (cache de l'APK de test, theme clair du fixture) :
sept cartes alignees sur deux rangs, avec le bouton Apply dans l'en-tete PAD.
APK debug mis a jour sur Nokia ; Pixel toujours conserve pendant les essais de
Mick. Aucun code natif modifie.
