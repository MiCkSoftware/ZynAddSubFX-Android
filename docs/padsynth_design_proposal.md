# PADsynth — proposition UX 014/015

8 novembre 2026. **Design 014 et parcours 015 valides explicitement par Mick dans ce chat.**
Le lot 013 reutilise les composants existants et peut avancer independamment.
Mick autorise les tests instrumentes ADB ; il prend en charge les tests finaux.

## Navigation et langage visuel

Conserver les cartes denses ADD, boutons LED, knobs enum avec popup existante,
contexte Part/Kit/PAD, clavier et retour. Stereo reste au niveau du kit.
Ordre retenu apres le retour de Mick sur le caractere live de certains controles :
**Amp / PAD / Frequency / Filter**, avec **Spectrum / Quality** comme
sous-parties successives dans le groupe PAD, au meme style que l’enveloppe sous Amp,
et une action Apply commune. Aucun sous-onglet dans le contenu PAD.

```text
Instrument · Part 1 / Kit 1 / PAD                     ‹ Part
[Amp] [PAD] [Frequency] [Filter]

PAD                                           [Apply]

Spectrum
  [Bandwidth] [Scale]     [Mode]    [Oscillator graphe]
  [Resonance graphe] [Overtones graphe] [Profile graphe]

Quality
  Sample size [128k ▼]    Base note [C4 ▼]
  Octaves [4 ▼]           Samples/octave [2 ▼]
  estimation nombre de samples / duree par sample / memoire PCM

[clavier existant]
```

Spectrum et Quality sont affiches a la suite dans le meme contenu
defilant, avec les en-tetes de sous-partie existants. Le statut et Apply sont
communs au groupe ; les editeurs detailles conservent leur navigation de retour.

Le selecteur Mode est accessible meme en Discrete/Continuous. Dans ces modes,
Bandwidth/Scale et Profile sont grises, valeurs preservees. Le profil peut
rester consultable mais ses controles sont inactifs tant que le mode ne
l'utilise pas. Oscillator, Resonance et Overtones restent accessibles.

## Profile (014)

```text
Profile · Part 1 / Kit 1 / PAD                         ‹ PAD
[Preview] [Base] [Modulation] [Amplitude]
[courbe native du profil]                      C P
Base : Shape / Shape parameter / Width / Side / Auto-scale
Modulation : Frequency multiplier / Modulator amount / Modulator frequency
Amplitude : Type / Mode / Parameter 1 / Parameter 2
[clavier]
```

Preview en tete, reduite au scroll comme Oscillator/SUB. Axe horizontal :
position relative dans la largeur d'une seule harmonique (pas Hz) ; axe vertical :
amplitude du profil. Ce graphe n'est pas le spectre audio final. Pas de selection
ou dessin direct dans le graphe ; knobs en dessous, double tap reset et menus
existants. Amplitude Off grise Mode/Parameters sans perdre leurs valeurs.
Clipboard type Profile contenant seulement Php, distinct de l'oscillateur.
Les courbes natives sont recalculees apres commit du geste, jamais par frame.

## Overtones (014)

```text
Overtones · Part 1 / Kit 1 / PAD                       ‹ PAD
[Preview] [Positions]
[64 positions natives ; axe 20 Hz..20 kHz ; badge note]
Positions : Type / Parameter 1 / Parameter 2 / Force harmonics      C P
[clavier]
```

Meme principe de preview reduite au scroll que SUB ; hues deterministes par rang,
reference sur derniere note jouee, A4 initiale. Traits de positions uniquement :
aucune fausse largeur ou amplitude audio. Aucun geste de selection dans le graphe.
Controles inutilises par le mode grises, valeurs preservees. Copie limitee a Phrpos.
Les editeurs Oscillator/Resonance reutilisent les surfaces ADD avec le proprietaire
PAD ; leur clipboard garde les types deja existants.

## Qualite et Apply (015)

Les quatre choix reprennent les domaines natifs, avec valeurs lisibles :
16k..1M, dix notes de base C2/G2..C6/G6, 1..8 octaves (stockage 0..7),
0.5/1/2/3/4/6/12 samples par octave. La frequence reelle est la reference en cas
de convention differente de nommage des octaves. Estimation PCM avant Apply,
limite memoire/temps explicite avant lancement ; pas de duree de calcul devinee.

| Etat | Affichage et actions |
|---|---|
| A jour | Apply vert inactif, sans texte de statut |
| En attente | Apply orange clignotant et actif, sans texte de statut |
| Generation | Samples generes n/N, progression ; Annuler actif |
| Modifie pendant le calcul | Generation interrompue ; nouvelles valeurs en attente |
| Installation differee | Samples prets ; relacher les notes pour appliquer ; Annuler actif |
| Succes | Apply vert inactif ; nouvelle banque utilisee aux notes suivantes |
| Annule | Modifications conservees ; Apply orange clignotant |
| Erreur | Cause concise et utile ; Apply rouge clignotant ; ancienne banque conservee |

Apply est place a droite de l'en-tete PAD et commun aux reglages de generation.
Sa couleur et son clignotement remplacent les textes visibles « Pending changes »
et « Samples up to date ». Progression, attente de fin des notes et erreurs restent
visibles pendant une operation. Pas de badge de statut supplementaire ni de popup
de succes. Oscillator, Resonance, Overtones et Profile sont des petites cartes graphiques
au format des knobs, dans la continuite des trois knobs Spectrum. Leurs editeurs
complets restent accessibles au toucher. Cette disposition ne concerne que PAD ;
les apercus ADD/SUB gardent leur disposition. Les fonds restent harmonises.
Les parametres de jeu Amp/Frequency/Filter n'exigent pas Apply ; les changements
Spectrum/Profile/Quality le demandent. Stereo doit suivre le contrat natif de
generation et etre suivi depuis le kit lorsqu'il affecte les samples.

Le clavier reste utilisable sur l'ancienne banque pendant le calcul. Sortir du
sous-ecran garde le job du kit ; changer de part/kit/preset ou fermer l'editeur
annule le job concerne. Les parametres ne sont pas annules avec le calcul.
Les valeurs modifiees apres le lancement ne sont jamais ecrasees par son resultat.
La sauvegarde conserve les parametres courants ; le chargement doit regenerer une
banque correspondante. Aucune banque partiellement calculee ne devient active.

## Contraintes techniques constatees et traitees par 014/015

- `PADnoteParameters::applyparameters` detruit/remplace directement les samples :
  ne pas l'appeler sur l'objet joue pendant un calcul asynchrone.
- Generation via snapshot natif isole + `sampleGenerator`, annulation et revision
  au proprietaire. Installation complete a un point audio sur ; anciennes notes
  terminees avant liberation des buffers (etat « relacher les notes » ci-dessus).
- `applyAndInitPartWithTimeout` charge et initialise toute la Part ; ce n'est pas
  une primitive appropriee pour un simple rebuild d'un kit PAD.
- Build Android configure `FFTW3F_NATIVE`. Les tests hote LIGHT_SHIM ne qualifient
  pas le PCM/performance de production ; ceux-ci seront verifies sur ADB.

## Decisions validees explicitement par Mick

1. Organisation Spectrum/Profile et graphes decrits ci-dessus.
2. Apply explicite, statut discret dans l'en-tete, annulation et application
   differee tant que des notes utilisent encore l'ancienne banque.

La validation du backlog ne vaut pas validation de cette proposition.

Implementation et verifications : [rapport PAD 013–015](qa_padsynth_013_015.md).
