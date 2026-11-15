# Proposition FX — lots 016, 017 et 018

14 novembre 2026. **Prototype validé par Mick le 14 novembre 2026 : « OK comme proto on ajustera sur piece ».**
Ce document conserve la proposition acceptée pour 016 et 018. L’implémentation
et ses preuves/limites sont suivies dans le [rapport FX](qa_fx_016_018.md).

## Slots et navigation (016)

Réutiliser le scaffold ADD : fonds sombres, bannières, knobs denses, enums avec
popup sur appui long, retour en haut et clavier persistant en bas. Les numéros
affichés commencent à 1 ; les indices moteur à 0.

```text
FX · Part 1                                          ‹ Part
[Scope : Part ▾]  [Part : 1 ▾]
[FX 1 · Reverb] [FX 2 · Off] [FX 3 · Off]

Slot 1 · Reverb                           [Bypass] [C] [P] [⋮]
[Type : Reverb ▾]              [Preset : nom natif ▾]

Mix
[Dry/Wet] [Pan]
Reverb
[Time] [Initial delay] […]
Filter
[Low pass] [High pass] […]
─────────────────────────────────────────────────────────
[clavier existant]
```

- Entrer par la carte FX d'une Part ouvre ses trois slots, premier slot par
  défaut ; au retour, conserver le slot choisi dans le contexte courant.
- Le sélecteur Scope donne Part / Insert / System. Part propose le sélecteur de
  Part ; Insert et System affichent « Master / Insert » ou « Master / System »
  et respectivement huit ou quatre slots. Sur téléphone, la rangée de slots
  défile horizontalement. Un seul éditeur est utilisé dans tous les cas.
- Le Kit disparaît du contexte FX : aucun effet n'appartient à un kit. Pour
  l’audition, garder le canal Play existant et rappeler ce contexte dans
  l’éditeur, distinct du propriétaire FX.
- Tous les slots sont accessibles, même Off. Le slot Off garde Type accessible,
  affiche « Choisir un effet » et n'affiche aucun faux paramètre ni preset.
- Toucher Type/Preset ouvre un choix nommé. Changer de type initialise cet effet
  avec ses réglages natifs ; changer de preset remplace ses réglages puis
  réconcilie tout le panneau. Aucun dialogue de confirmation systématique.
- Bypass est une LED propre aux effets Part, fondée sur Pefxbypass. Le panneau
  reste éditable pendant le bypass. Master n'a pas de booléen équivalent : ne
  pas présenter Off comme un bypass qui préserverait les réglages.
- Insert affiche son assignation native en lecture seule ; un slot non assigné
  signale « Non assigné · pas de traitement audio ». System rappelle qu'un
  envoi est nécessaire. Assignations, chaînes, sends et retours complets restent
  au lot 019, avec sa propre validation de conception.
- Retour Android et retour visuel remontent au point d'entrée ; depuis un filtre
  ou Formant, ils reviennent d'abord au slot courant.

Actions : C copie l'effet complet ; P colle un effet compatible et réconcilie
le panneau. Le propriétaire/destination et le bypass Part ne sont pas copiés.
Le menu distingue « Rétablir le preset courant » (valeurs et filtre du preset)
et « Vider la mémoire audio » (cleanup, paramètres conservés). Un double tap sur
un knob rétablit sa valeur native pour le preset courant, selon le contrat ADD.
Paste reste inactif pour un clipboard d'instrument ou de module incompatible.

Un changement natif refusé conserve la dernière valeur confirmée et montre une
erreur concise. Pendant un changement structurel en cours, suspendre les
contrôles du slot ; aucune présentation optimiste d'un effet non encore créé.

## Panneaux réutilisant les contrôles ADD (017 et partie de 018)

Les sections regroupent des contrôles nommés et typés. Les réglages sans effet
dans le mode courant sont grisés et leurs valeurs conservées. Les unités sont
calculées depuis les conversions du DSP ; ne pas afficher des Hz ou des ms
inventés à partir d'une simple valeur 0..127.

| Effet | Organisation proposée |
|---|---|
| Reverb | Mix ; durée/prédélai ; filtres ; algorithme/taille/bande passante selon algorithme |
| Echo | Mix ; délais ; cross/feedback/amortissement |
| Chorus / Flange | Mix ; LFO ; profondeur/délai/feedback ; flange/inversion |
| Phaser | Mix ; LFO ; stages/feedback/phase ; modes analogiques et leurs réglages |
| Alienwah | Mix ; LFO ; profondeur/feedback/délai/phase/cross |
| Distortion | Mix ; drive/niveau/waveshaping ; filtres/ordre ; stéréo/inversion/DC |
| Dynamic Filter | Mix ; LFO ; modulation/suivi d'amplitude ; carte Filter ouvrant l'éditeur partagé |
| Sympathetic | Mix ; résonance/drive/niveau ; cordes/unisson/note de base ; filtres/inversion |
| Reverse | Mix ; durée/stéréo/phase/crossfade ; mode de déclenchement |

Le libellé du paramètre 0 suit le propriétaire et l'effet : le niveau du retour
System ne se présente pas comme le mélange Dry/Wet d'une insertion. EQ garde
son contrôle de gain propre. Les presets portent les noms du fork.

Dynamic Filter réutilise FilterEditor et son parcours Formant, avec previews et
clipboard au propriétaire FX. Les filtres ADD/SUB/PAD restent indépendants.

Pour Echo, Reverse et les effets à LFO concernés : durée/fréquence libre et
rapport au tempo existant, avec tempo de référence lisible. Le choix synchronisé
désactive le réglage libre correspondant. Reverse expose Note On / Note On-Off /
Auto suivant le fork ; vérifier le déclenchement avec le clavier existant et
l'horloge du moteur. Aucun nouveau sous-système MIDI.

## EQ : huit bandes et courbe interactive (018)

```text
EQ · Master / Insert 1                              ‹ FX
[Type : EQ ▾] [Preset ▾]                       [C] [P] [⋮]

 +30 dB ┤               ③
   0 dB ┼────①────②────────④──────⑤───────────
 -30 dB ┤
        20      100      1k       10k  20k Hz
[1] [2] [3*] [4] [5] [6 Off] [7 Off] [8 Off]

Bande 3
[Type] [Stages] [Frequency] [Q] [Gain]
Output
[Gain global]
─────────────────────────────────────────────────────
[clavier existant]
```

- Courbe native globale, axe fréquentiel logarithmique 20 Hz..20 kHz, axe dB
  gradué avec repère 0 dB. Un dépassement de la plage verticale reste signalé ;
  il ne modifie pas les valeurs. Palette et tracé néon des previews ADD.
- Huit sélecteurs toujours visibles, ou défilants si nécessaire, avec numéro,
  couleur stable, état Off et sélection distinguée autrement que par la couleur.
- Toucher un marqueur sélectionne sa bande. Glisser horizontalement change sa
  fréquence dans ses bornes natives ; verticalement change son gain uniquement
  pour Peak / Low shelf / High shelf. Pour les autres types, le gain est grisé
  et seul le déplacement horizontal s'applique. Q et Stages restent des knobs.
- Un glissement commence sur un marqueur ; toucher le fond ne crée pas de bande
  et laisse le scroll de page disponible. Les marqueurs proches restent
  sélectionnables avec la rangée numérotée. Pas de pinch ou de geste caché pour Q.
- Une bande Off reste sélectionnable par son numéro. Type reste actif ; les
  autres contrôles sont grisés, valeurs conservées. Choisir un type la réactive.
- Types : Off, LP1, HP1, LP2, HP2, BP, Notch, Peak, Low shelf, High shelf.
  Stages affiche 1..5 pour le stockage natif 0..4.
- Pendant le drag, écriture des seuls paramètres concernés et déplacement local
  du marqueur. La dernière courbe native reste visible avec indication discrète
  de mise à jour différée. Au relâchement : snapshot du slot et réponse native
  recalculée. Aucune courbe approximative présentée comme réponse DSP.
- Les knobs donnent une alternative complète aux gestes du graphe, avec labels
  accessibles. Pas de double tap destructif sur la courbe ; reset via les knobs
  et le menu existants.

## Appuis natifs vérifiés et travail restant

Vérification des sources le 14 novembre 2026 :

- `globals.h` : 3 FX Part, 8 Insert, 4 System, 8 bandes EQ,
  `MAX_FILTER_STAGES=5`.
- `EffectMgr` possède les types 0..10, ses paramètres/presets, filtre, réponse EQ,
  clipboard via paste et XML. Les appels NONREALTIME `changeeffect` et
  `changepreset` ne réalisent pas à eux seuls le changement de l'effet joué.
  `init`, `changeeffectrt`, `changepresetrt` et `paste` utilisent le contexte RT
  et l'allocateur natif ; leur orchestration doit respecter cette propriété.
- `Part::Pefxbypass` porte le bypass Part ; `Master::Pinsparts` porte
  l'assignation Insert. Aucun bypass Master équivalent n'a été identifié.
- EQ adresse les cinq champs de la bande b à `10 + 5*b + offset` :
  type/fréquence/gain/Q/stages. Les autres indices ne sont pas des knobs anonymes.
- `EffectMgr::filterpars` est le propriétaire du filtre Dynamic Filter ; les
  presets de cet effet changent aussi sa structure de filtre.
- Lors de la conception, `FullInstrumentEditor` contenait des placeholders FX ;
  ils sont maintenant remplacés par l’éditeur commun. L’inspection des effets
  actifs et les sends restent distincts de l’API du slot.

Contrat implémenté : scope/Part/slot sans kitIndex, descripteurs
par effet et révision de structure ; synchronisation des mutations au point
audio approprié ; réemploi des surfaces Compose ; tests d'isolation et invalides,
type/preset/reset/clipboard, previews et sérialisation dans les trois scopes.
XIZ conserve les FX Part ; la session conserve les FX Master. Le [mapping exhaustif](fx_parameter_contract.md) conserve les indices, bornes,
options, unités et conditions.

La validation audio/appareil comparative demeure au lot 021, et la persistance
transverse au lot 020. Les builds et tests locaux de l’implémentation sont consignés dans le
[rapport FX](qa_fx_016_018.md). Aucun commit, push ou installation.

## Décision de Mick

Le 14 novembre 2026 : « OK comme proto on ajustera sur piece ».
Accord explicite pour implémenter ce prototype ; les ajustements visuels et
les interactions se feront sur pièce. Cet accord ne constitue pas une validation
audio/appareil du résultat implémenté.

Précision du prototype : le clavier conserve le canal Play existant 

## Navigation révisée avec Mick — 14 novembre 2026

Demande explicite : remplacer Open FX dans la Part par trois sous-lignes au
style des synthés KIT (numéro, nom d'effet, ON/OFF, ouverture). Ajouter FX à droite
de Performance dans Play, vers un écran regroupant les quatre System dans
« Master / System » et les huit Insert dans « Insert ». Ajouter Edit à droite de
Part controls pour éditer la Part sélectionnée. Ces décisions remplacent le
sélecteur Scope proposé ci-dessus : le point d'entrée détermine le propriétaire.
L'éditeur partagé conserve seulement les onglets des slots de son groupe.

Un slot Off peut toujours être ouvert ; son bouton OFF ouvre le choix d'effet.
Pour un effet existant, ON/OFF utilise un bypass qui conserve ses paramètres.
Part conserve Pefxbypass ; Master possède maintenant un bypass natif par
EffectMgr, enregistré en session et non transféré par copier/coller.

## Ajustement du 15 novembre 2026 — slot unique et knobs

À la demande de Mick, les onglets FX 1/2/3 (ou 4/8 selon scope) sont supprimés
de l'éditeur : seul le slot ouvert depuis la liste est édité, avec son contexte
et le retour vers la liste. Type et Preset utilisent les mêmes knobs et popups
sur appui long que les autres enums. Pendant leur glissement, le choix affiché
reste local ; le moteur change de type/preset au relâchement, avec une seule
réconciliation. Revenir au choix initial ne réapplique pas son preset.
Le menu d'actions reste distinct des contrôles de paramètres.
