# Contrat des paramètres FX

Implémentation du 14 novembre 2026. Tables natives dans
`native/engine_adapter/ZynFxControls.h` ; décisions d'applicabilité et conversions
visibles dans `FxModels.kt`. Les valeurs par défaut proviennent de
`Effect::getpresetpar` pour le preset et le propriétaire courants.

## Adressage et mutations

- `scope=0, part=0..15, slot=0..2` : FX Part ; `kitIndex` absent.
- `scope=1, part=-1, slot=0..7` : Insert Master.
- `scope=2, part=-1, slot=0..3` : System Master.
- Snapshot : type, preset, révision monotone, assignation Insert, tempo de
  référence et descripteurs typés. Toute écriture reçoit le type attendu et
  rejette un propriétaire, type, champ, indice ou domaine invalide.
- `fx/type`, `fx/preset`, `fx/bypass` (Part uniquement) ;
  `fx/parameter/N` pour les indices ci-dessous. Off ne propose ni preset ni
  paramètre d'un effet inexistant.
- Type/preset/bypass : choix explicite, réconciliation structurelle. Paramètre
  continu : écriture et petite valeur transitoire, snapshot/preview au commit.
- `fx/numerator=0..99` et `fx/denominator=1..99` pour Echo, Chorus, Phaser,
  Alienwah, Dynamic Filter et Reverse. Numérateur 0 = libre. Le rapport représente
  une fraction de ronde ; à 120 BPM, 1/4 donne 500 ms ou 2 Hz, sous réserve des
  limites de codage de l'effet. Le réglage libre est désactivé tant que le rapport
  est actif. `EffectMgr::setTempoRatio` réalise le calcul, borne avant conversion
  en octet, et sert également aux ports natifs, presets, init et paste.

## Mapping natif vers les contrôles

Les indices non listés ne sont pas exposés. Les nombres sont des knobs ; les
options sont des enums avec appui long/popup (sélection directe pour Type/Preset).
Les booléens de slot sont des LED. Les enums binaires d'effet gardent le codage
0/1 du DSP. Le paramètre 0 est Dry/Wet en Part/Insert et Return level en System,
à l'exception du gain global propre à EQ.

### 1 — Reverb

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | Time | Reverb | 0..127 | — |
| 3 | Initial delay | Reverb | 0..127 | — |
| 4 | Initial feedback | Reverb | 0..127 | — |
| 7 | Low pass | Filter | 0..127 | — |
| 8 | High pass | Filter | 0..127 | — |
| 9 | Damping | Filter | 64..127 | — |
| 10 | Algorithm | Reverb | 0..2 | Random,Freeverb,Bandwidth |
| 11 | Room size | Reverb | 1..127 | — |
| 12 | Bandwidth | Reverb | 0..127 | — |

### 2 — Echo

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | Delay | Echo | 0..127 | — |
| 3 | L/R delay | Echo | 0..127 | — |
| 4 | L/R cross | Echo | 0..127 | — |
| 5 | Feedback | Echo | 0..127 | — |
| 6 | High damping | Echo | 0..127 | — |

### 3 — Chorus / Flange

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | LFO frequency | LFO | 0..127 | — |
| 3 | LFO randomness | LFO | 0..127 | — |
| 4 | LFO shape | LFO | 0..1 | Sine,Triangle |
| 5 | Stereo phase | LFO | 0..127 | — |
| 6 | Depth | Chorus | 0..127 | — |
| 7 | Delay | Chorus | 0..127 | — |
| 8 | Feedback | Chorus | 0..127 | — |
| 9 | L/R cross | Chorus | 0..127 | — |
| 10 | Mode | Chorus | 0..3 | Chorus,Flange,Triple,Dual |
| 11 | Subtract | Chorus | 0..1 | Off,On |

### 4 — Phaser

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | LFO frequency | LFO | 0..127 | — |
| 3 | LFO randomness | LFO | 0..127 | — |
| 4 | LFO shape | LFO | 0..1 | Sine,Triangle |
| 5 | Stereo phase | LFO | 0..127 | — |
| 6 | Depth | Phaser | 0..127 | — |
| 7 | Feedback | Phaser | 0..127 | — |
| 8 | Stages | Phaser | 1..12 | — |
| 9 | L/R cross / Offset | Phaser | 0..127 | — |
| 10 | Invert | Phaser | 0..1 | Off,On |
| 11 | Phase / Width | Phaser | 0..127 | — |
| 12 | Square LFO | Analog | 0..1 | Off,On |
| 13 | Distortion | Analog | 0..127 | — |
| 14 | Analog mode | Phaser | 0..1 | Off,On |

### 5 — Alienwah

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | LFO frequency | LFO | 0..127 | — |
| 3 | LFO randomness | LFO | 0..127 | — |
| 4 | LFO shape | LFO | 0..1 | Sine,Triangle |
| 5 | Stereo phase | LFO | 0..127 | — |
| 6 | Depth | Alienwah | 0..127 | — |
| 7 | Feedback | Alienwah | 0..127 | — |
| 8 | Delay | Alienwah | 1..100 | — |
| 9 | L/R cross | Alienwah | 0..127 | — |
| 10 | Phase | Alienwah | 0..127 | — |

### 6 — Distortion

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | L/R cross | Distortion | 0..127 | — |
| 3 | Drive | Distortion | 0..127 | — |
| 4 | Output level | Distortion | 0..127 | — |
| 5 | Waveshaping | Distortion | 0..16 | Arctangent,Asymmetric,Power,Sine,Quantisize,Zigzag,Limiter,Upper limiter,Lower limiter,Inverse limiter,Clip,Asym2,Pow2,Sigmoid,Tanh,Cubic,Square |
| 6 | Invert | Distortion | 0..1 | Off,On |
| 7 | Low pass | Filter | 0..127 | — |
| 8 | High pass | Filter | 0..127 | — |
| 9 | Stereo | Distortion | 0..1 | Off,On |
| 10 | Filter first | Filter | 0..1 | Off,On |
| 11 | Shape | Distortion | 0..127 | — |
| 12 | DC offset | Distortion | 0..127 | — |

### 7 — EQ

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Output gain | Mix | 0..127 | — |
| 10 | Type | Band 1 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 11 | Frequency | Band 1 | 0..127 | — |
| 12 | Gain | Band 1 | 0..127 | — |
| 13 | Q | Band 1 | 0..127 | — |
| 14 | Stages | Band 1 | 0..4 | 1,2,3,4,5 |
| 15 | Type | Band 2 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 16 | Frequency | Band 2 | 0..127 | — |
| 17 | Gain | Band 2 | 0..127 | — |
| 18 | Q | Band 2 | 0..127 | — |
| 19 | Stages | Band 2 | 0..4 | 1,2,3,4,5 |
| 20 | Type | Band 3 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 21 | Frequency | Band 3 | 0..127 | — |
| 22 | Gain | Band 3 | 0..127 | — |
| 23 | Q | Band 3 | 0..127 | — |
| 24 | Stages | Band 3 | 0..4 | 1,2,3,4,5 |
| 25 | Type | Band 4 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 26 | Frequency | Band 4 | 0..127 | — |
| 27 | Gain | Band 4 | 0..127 | — |
| 28 | Q | Band 4 | 0..127 | — |
| 29 | Stages | Band 4 | 0..4 | 1,2,3,4,5 |
| 30 | Type | Band 5 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 31 | Frequency | Band 5 | 0..127 | — |
| 32 | Gain | Band 5 | 0..127 | — |
| 33 | Q | Band 5 | 0..127 | — |
| 34 | Stages | Band 5 | 0..4 | 1,2,3,4,5 |
| 35 | Type | Band 6 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 36 | Frequency | Band 6 | 0..127 | — |
| 37 | Gain | Band 6 | 0..127 | — |
| 38 | Q | Band 6 | 0..127 | — |
| 39 | Stages | Band 6 | 0..4 | 1,2,3,4,5 |
| 40 | Type | Band 7 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 41 | Frequency | Band 7 | 0..127 | — |
| 42 | Gain | Band 7 | 0..127 | — |
| 43 | Q | Band 7 | 0..127 | — |
| 44 | Stages | Band 7 | 0..4 | 1,2,3,4,5 |
| 45 | Type | Band 8 | 0..9 | Off,LP1,HP1,LP2,HP2,BP,Notch,Peak,Low shelf,High shelf |
| 46 | Frequency | Band 8 | 0..127 | — |
| 47 | Gain | Band 8 | 0..127 | — |
| 48 | Q | Band 8 | 0..127 | — |
| 49 | Stages | Band 8 | 0..4 | 1,2,3,4,5 |

### 8 — Dynamic Filter

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | LFO frequency | LFO | 0..127 | — |
| 3 | LFO randomness | LFO | 0..127 | — |
| 4 | LFO shape | LFO | 0..1 | Sine,Triangle |
| 5 | Stereo phase | LFO | 0..127 | — |
| 6 | Depth | Modulation | 0..127 | — |
| 7 | Amplitude sensitivity | Modulation | 0..127 | — |
| 8 | Invert sensitivity | Modulation | 0..1 | Off,On |
| 9 | Amplitude smoothing | Modulation | 0..127 | — |

### 9 — Sympathetic

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | Resonance | Strings | 0..127 | — |
| 3 | Input gain | Strings | 0..127 | — |
| 4 | Output gain | Strings | 0..127 | — |
| 5 | Unison detune | Strings | 0..127 | — |
| 6 | Invert | Strings | 0..1 | Off,On |
| 7 | Low pass | Filter | 0..127 | — |
| 8 | High pass | Filter | 0..127 | — |
| 9 | Unison strings | Strings | 1..3 | — |
| 10 | Strings | Strings | 0..76 | — |
| 11 | Base note | Strings | 0..127 | — |

### 10 — Reverse

| Indice | Contrôle | Groupe | Bornes natives | Options |
|---|---|---|---|---|
| 0 | Amount | Mix | 0..127 | — |
| 1 | Pan | Mix | 0..127 | — |
| 2 | Segment length | Reverse | 0..127 | — |
| 3 | Stereo | Reverse | 0..1 | Off,On |
| 4 | Phase | Reverse | 0..127 | — |
| 5 | Crossfade | Reverse | 0..127 | — |
| 6 | Trigger mode | Reverse | 0..3 | Note On,Note On-Off,Auto,Host |

## Conditions et unités

- Reverb : damping effectif 64..127 ; Room size 1..127 (0 est un ancien alias
  de 64). Bandwidth disponible uniquement pour l'algorithme Bandwidth.
- Chorus Flange : le fork utilise un délai nul dans ce mode ; contrôles LFO,
  Depth et Delay grisés, valeurs conservées. Les modes Triple et Dual utilisent
  les mêmes contrôles que Chorus.
- Phaser : Cross/Phase deviennent Offset/Width en Analog ; Hyper et Distortion
  sont grisés en mode numérique. Les deux champs partagés suivent leurs setters
  natifs qui mettent à jour les deux représentations.
- EQ : huit bandes, cinq indices à partir de `10 + 5*band`. Type Off conserve
  les valeurs ; gain actif pour Peak et les shelves. Stages stocké 0..4, affiché
  1..5. Fréquence = `600 * 30^((v-64)/64)` Hz, gain = `30*(v-64)/64` dB,
  Q = `30^((v-64)/64)`. Courbe globale en dB via `getEQfreqresponse`.
- Echo : `1500*v/127` ms ; LFO : `(2^(10*v/127)-1)*0.03` Hz ;
  Reverse : segment `(v+1)*4000/128` ms, crossfade `(v+1)*10` ms. Les autres
  contrôles gardent leur valeur native lorsqu'aucune unité dédiée n'est présentée.
- Reverse comprend aussi le mode Host (indice 3), même si ses trois presets
  couvrent seulement Note On, Note On-Off et Auto. Note On-Off utilise la
  détection de décroissance du signal du fork ; il n'ajoute pas de transport MIDI.
- Sympathetic : les douze paramètres joués et les cinq presets du fork sont
  exposés ; l'ancien tableau de métadonnées `freqs` ne pilote pas le calcul des
  cordes (`calcFreqs*`) et n'est pas présenté comme un accordeur de 88 cordes.

## Dynamic Filter, clipboard et persistance

`fx/filter/*` réutilise les primitives de descripteurs/écriture des filtres
ADD/SUB/PAD : category, type selon catégorie, cutoff, Q, gain, tracking, stages,
Comb HPF/LPF et Formant complet (voyelles, formants, séquence). `FilterEditor`
reçoit une adresse FX explicite et ne mute pas un filtre d'instrument.
La preview montre le filtre statique, sans LFO ni enveloppe d'amplitude :
réponse native par impulsion de faible amplitude pour Analog/SV/Moog/Comb,
formule native Formant partagée pour la voyelle choisie. Ce graphe normalisé
et borné à ±30 dB ne constitue pas une réponse dynamique audio mesurée.

Clipboard commun typé : effect, filter, vowel. Copier/coller l'effet complet
passe par XML puis `EffectMgr::paste`, y compris son filtre et son rapport au
tempo. Bypass, assignations et sends ne sont pas copiés. Le XML complet respecte
`minimal=false` pour conserver les paramètres qui sont des valeurs par défaut
uniquement dans le scope source ; le XML minimal reste compatible.
Reset réapplique le preset courant ; Cleanup vide les mémoires DSP en conservant
les paramètres. Double tap sur les knobs de l'effet rétablit la valeur du
preset courant ; appui long conserve le menu existant. Le filtre partagé garde
ses commandes existantes et le reset global de l'effet.

XIZ conserve les FX Part et leur bypass. Les FX Master appartiennent à la
session/XMZ. La qualification transverse reste suivie par le lot 020.

## Évolution du bypass et des points d'entrée

Après révision de navigation par Mick : `fx/bypass` est maintenant disponible
également en Insert/System. Il appartient à EffectMgr au Master, reste dans
Pefxbypass pour Part, et n'est pas copié avec l'effet. L'écran Part expose trois
lignes ; Play expose FX vers les deux groupes Master/System et Insert. Le choix
de scope du premier prototype est supprimé.

## Source Insert

`fx/source` : enum Off, Master, Part 1..16, indices affichés 0..17 ; uniquement
dans les Inserts, y compris lorsqu'ils sont Off. Stockage Master natif : -1 Off,
-2 Master, 0..15 Parts. Primitive de mutation Master::setInsertionFxSource.
Choix transitoire pendant le drag puis affectation au commit. Sérialisation
Master existante (Pinsparts) ; aucune modification par clipboard/reset d'effet.

## Envois System vers System

`fx/send/N` dans Routing des System : N est l'indice natif 0..3 d'une destination
strictement supérieure au slot source. Six liaisons au total ; les labels
To Master 2/3/4 utilisent la numérotation visible 1..4. Domaine natif 0..127,
0 désactive l'envoi. Les réglages sont accessibles même sur un slot Off ; un
slot Off n'applique aucun effet audio. Master::setPsysefxsend conserve la valeur
brute et son gain dérivé ; les liens sont stockés dans la session, sans être
copiés ou réinitialisés avec un effet. Gestes : écriture minimale et valeur
locale, snapshot du slot au commit.
