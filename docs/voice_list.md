# Liste des voix AddSynth — rangees compactes sur une ligne (31 octobre 2026)

## Cloture

Feature cloturee a la demande de Mick le 31 octobre 2026 apres validation de l'UX courante.
Les captures fournies par Mick montrent la version utilisee ; l'agent n'a pas installe l'application.
Cette acceptation ne vaut pas execution de toutes les verifications audio, accessibilite et multi-appareils
listees plus bas, qui restent dans la QA transversale. La feature 008 (liste sur appui long) reste ouverte.

## Disposition et interactions implementees

Les huit voix utilisent une rangee compacte dans l'ordre **courbe, Res, Vol, Pan, Detune, Vibrato**,
puis le chevron d'acces au detail. Le numero de voix est integre dans la mini-courbe, a cote de ON/OFF. La proposition a deux lignes a ete remplacee apres revue
sur capture : elle prenait trop de hauteur et ses sliders etaient trop etroits.

La courbe fait 50 x 58 dp ; les quatre reglages continus utilisent les memes knobs de 43 dp,
avec poids identiques et libelles Vol, Pan, Detune et Vibrato au-dessus, separes des knobs par 4 dp.
Res est un bouton vert eteint/allume portant directement le texte Res, sans label superieur,
avec 8 dp supplementaires entre la courbe et ce bouton. Detune affiche les cents natifs,
Vibrato la profondeur du LFO de frequence. Les legendes ct/LFO sous les knobs ont ete retirees
pour reduire la hauteur ; unite et etat du LFO restent decrits dans les semantiques accessibles. Les valeurs longues
ajustent leur police dans TinyKnob pour garder les cents lisibles.

- Tap sur la mini-courbe : bascule uniquement `add/voice/<index>/enabled` par le chemin de mutation
  existant. Courbe verte/attenuee et texte ON/OFF ; semantique Switch avec etat et numero de voix.
- Chevron : detail Voice accessible meme si la voix est desactivee.
- Voix desactivee : controles attenues et non editables ; activation et navigation restent accessibles.
- Modes White/Pink noise : indication textuelle dans l'apercu ; Detune, Vib. Depth et Resonance
  non editables, comme les controles correspondants de l'original. Volume/Pan restent accessibles
  pour une voix active. Aucune fausse courbe sinusoidale pour representer le bruit.
- Pan 0 est indique Rnd, conformement au tooltip original (panning aleatoire).
- Resonance et l'activation de la voix sont accessibles dans la section Voice du detail.

## Correspondance avec le fork original

`AddVoiceSummary` resout des chemins exacts, sans recherche par suffixe parmi les champs imbriques :

| Controle | Contrat natif | Chemin Android |
| --- | --- | --- |
| Mini-courbe ON/OFF | Enabled | add/voice/N/enabled |
| Volume | PVolume | add/voice/N/volume |
| Pan | PPanning (0 = Random) | add/voice/N/panning |
| Res | Presonance | add/voice/N/resonance |
| Detune | PDetune / detunevalue | add/voice/N/detune |
| Vib. Depth | FreqLfo/Pintensity | add/voice/N/freqLfo/depth |

### Retrouver les memes reglages dans le detail Voice

| Controle de liste | Section du detail | Controle visible |
| --- | --- | --- |
| Courbe ON/OFF | Voice | Enabled |
| Res | Voice | Resonance |
| Vol | Amplitude | Vol |
| Pan | Amplitude | Panning |
| Detune | Frequency | Detune (cents) |
| Vibrato | Frequency / Frequency LFO | Depth, avec ON independant |

Le controle Frequency LFO Depth est construit par le meme `LfoModel` que l'editeur LFO ouvert
au tap sur la courbe. Le test de routage de la liste verifie que son parametre depth est exactement
celui du detail, et qu'il ne s'agit pas du vibrato d'Unison. Tous les raccourcis sont exposes dans
le detail ; Enabled et Resonance ont ete retablis lors de la refonte de la liste.

Le partage de source se regle dans **Voice > Oscillator > Use oscillator**. Dans l'interface
actuelle c'est un knob de selection enum : glisser pour changer la valeur, 0 = Internal,
1 = Voice 1, etc. L'appui long n'ouvre pas une liste pour les enums a label vertical.
Ce choix pilote a la fois la source sonore et celle de la mini-courbe ; il ne faut pas changer
le routage musical pour seulement obtenir une autre image.

Sources : `third_party/zynaddsubfx/src/UI/ADnoteUI.fl:112-193`,
`third_party/zynaddsubfx/src/Params/ADnoteParameters.cpp:207-217`.

Le raccourci Vib. Depth pilote maintenant le LFO de frequence, et non `Unison_vibratto`.
L'activation `freqLfoEnabled` reste independante ; son etat est indique dans la description accessible du controle, sans legende sous le knob.
Le detail garde Frequency > LFO > Depth et Unison > Vibrato/Speed comme fonctions distinctes.
Modifier une profondeur ou activer une voix n'active pas implicitement le LFO.

Les cents de Detune (liste et detail) utilisent directement `zyn::getdetune(type, 0, raw)` via
une primitive JNI scalaire. Le type est celui de la voix, ou le type global si la voix herite.
Pas de conversion lineaire universelle, ni de confusion avec les octaves/coarse : l'affichage
reproduit le fine detune de `detunevalue` dans l'original. La valeur stockee reste le brut natif.
La conversion n'alloue pas de snapshot et ne consulte pas l'ensemble des parts/kits.

## Preview et gestes

Pour les voix Sound, l'apercu utilise le preview natif de l'oscillateur reel, y compris une source
partagee selectionnee via externalOscillator. La phase propre a la voix decale le dessin.
Il s'agit d'un apercu de 64 echantillons normalises, pas d'un vumetre ni de la sortie audio finale.
Le decalage de phase est quantifie a cette resolution d'affichage.

Le preview est memorise par revision, source et mode ; le decalage est memorise par preview/phase.
Aucune relance native ni reconstruction de listes de parametres pendant un mouvement de knob.
Les knobs reutilisent TinyKnob, son affichage transitoire local et sa terminaison/cancellation
existante, avec prise en compte explicite de l'etat non editable. Les ecritures audio restent
immediates et la reconciliation complete est differee a la fin du geste. Une reconciliation peut rafraichir les huit previews : cout borne,
effectue a la fin du geste ou lors d'un changement structurel.

## Edition Voice et petits ranges — ajustements du 31 octobre

Les six controles Enabled, Delay, Resonance, Mode, Phase et Use oscillator sont reunis dans
une meme grille Voice, avec le meme format de cartes que les autres sections. La separation
precedente provenait des groupes d'affichage natifs ; ces groupes ne justifient pas une rupture
visuelle entre six parametres de la voix. La section Oscillator conserve seulement son preview
et les actions du module interne.

Quand Use oscillator est externe, le preview du detail suit la source effective et son titre
indique la voix utilisee. L'ouverture de l'editeur interne et son Copy/Paste sont desactives.
Si le partage est choisi depuis l'editeur deja ouvert, ses controles propres (base, traitements,
harmoniques, reset) sont aussi desactives ; les champs Mode/Phase/Use oscillator de la voix restent
actifs. Un modulateur garde son contrat de partage distinct, sans etre desactive par le partage du carrier.

Les controles Enabled, Delay, Resonance, Mode et Phase restent musicalement utiles meme avec
un oscillateur externe : seul le generateur source est emprunte. Voir `Synth/ADnote.cpp:171-178`
pour le choix de generateur et `ADnote.cpp:200` pour l'offset de phase propre a la voix.

TinyKnob plafonne maintenant le deplacement a **32 dp par pas** si sa plage demanderait davantage.
Deux choix changent donc autour de 16 dp de mouvement utile (plus le seuil de detection du drag),
independamment de la hauteur d'ecran/densite. Le cap par evenement de 0,03 normalise ne s'applique
pas a ces petites plages, pour ne pas rendre le resultat dependant du nombre d'evenements.
Les grandes plages conservent leur sensibilite precedente. Ecritures et reconciliation inchangees.
Tests : deux/trois choix, densites 1..4, grandes plages et bornes de configuration degeneres.

La liste sur appui long est seulement planifiee dans `008_feat_enum_long_press.md` ; elle n'est
pas implementee dans ce changement. QA restante : partage interne/externe et retour Internal,
preview et Copy/Paste inactifs, six cartes alignees, courts drags lents/rapides des enums a 2-3 choix.

## Verifications executees

- Compilation Kotlin et 24 tests JVM passes, dont 6 tests de routage/independance de AddVoiceSummary
  et 5 tests de sensibilite des knobs.
  Ces tests couvrent LFO vs Unison, absence d'activation implicite, chemins imbriques,
  type de detune herite/explicite, oscillateur partage/phase et huit voix dont les desactivees.
- `:app:externalNativeBuildDebug` : compilation/link arm64-v8a et x86_64 reussis.
- `:app:compileDebugAndroidTestKotlin` : tests Android compiles, dont les cas des quatre conversions natives.
- Un probe natif temporaire a charge la libzynbridge arm64-v8a fraichement compilee sur emulator-5554
  et appele son entree JNI fineDetuneCents : **14 verifications passees** (centre, extremites, signe,
  quatre courbes et bornes d'entree). Fichiers temporaires retires de l'emulateur ensuite.
- `git diff --check` passe.

Commandes Gradle (avec JDK 17 ARM, mode offline) :
`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline --no-daemon`
et `./gradlew :app:externalNativeBuildDebug --offline --no-daemon`.

## Limites et QA restante

Un emulateur arm64 est disponible ; l'agent n'a pas installe cette version de l'application.
Les tests Android n'ont pas ete executes par instrumentation. Les captures fournies par Mick et sa
validation cloturent l'UX courante ; les autres parcours tactiles/audio et appareils restent a verifier.

- Verifier portrait, paysage, petite largeur et grande police : alignement de la rangee unique,
  lisibilite des cents, labels et surfaces tactiles des knobs.
- Tester les huit bascules de courbe et acces au detail, y compris voix desactivees et modes bruit.
- Ecouter les gestes longs/rapides de Volume, Pan, Detune et Vib. Depth, ainsi que Resonance.
  Confirmer le role du ON du LFO et l'independance d'Unison.
- Verifier annulation, retrait/navigation pendant un geste, dernier etat natif, refresh final des previews,
  sources partagees, phase et changement de Part/Kit ; pas de snapshot/preview par mouvement.
- Tester le parcours de lecture et de bascule au lecteur d'ecran ; aucune conformite accessibilite
  complete n'est revendiquee par les seuls controles de code.

La parite des autres champs manquants reste dans la tache 005 ; les controles OscilGen avances
restent dans la tache 006. Aucun commit, push ou installation d'application effectue.
