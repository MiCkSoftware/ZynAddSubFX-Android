# Proposition SUBsynth — a iterer avec Mick

8 novembre 2026. Statut : **validee par Mick le 8 novembre 2026, puis implementee**. Precision utilisateur : reutiliser les knobs avec popup existants pour tous les enums. [Implementation, executions et limites](qa_subsynth_011_012.md). Le socle 010 est acquis ; cette proposition couvre les champs et surfaces des lots 011/012. Le retour de Mick demande de reutiliser l'UX harmonique du Voice Oscillator ADD et de ne laisser aucun controle d'origine au stade draft.

## Organisation proposee

Conserver Part > Kit > SUB, navigation/retour et clavier ADD. Stereo reste sur l'ecran kit. Cinq sections : **Amp / Harmonics / Frequency / Bandwidth / Filter**, sans Global vide.

Reutiliser les cartes/knobs denses, reset, choix enum et enveloppes ADD. Les controles directs precedent les enveloppes. Aucun LFO, punch ou faux ON ajoute a SUB.

- **Amp** : ligne Volume / Velocity / Panning, puis enveloppe amplitude toujours disponible.
- **Frequency** : Fine detune / Octave / Coarse ; Detune type ; Bend / Offset Hz ; Fixed frequency + Equal temperament, ce dernier actif selon le flag natif ; enveloppe frequence avec ON.
- **Bandwidth** : largeur globale / scale / nombre d'etages des filtres de bruit, puis enveloppe bandwidth avec ON. Le nombre d'etages ici est distinct des etages du filtre global.
- **Filter** : conserver exactement les composants ADD existants pour categorie/type, controles directs, Comb, Formant/voyelles/sequence et enveloppe. Un seul ON natif pour le filtre global et ses sous-modules ; OFF masque sans effacer.
- **Harmonics** : reutiliser la structure du Voice Oscillator : Harmonic # / Magnitude / Relative bandwidth / Reset ONE+ALL ; graphe des 64 rangs ; choix de grandeur editee Magnitude ou Bandwidth ; echelle de magnitude et mode de demarrage ; groupe Overtones complet. Un apercu des positions physiques peut se deplier sous Overtones.

## Edition harmonique

Le graphe principal garde les rangs 1..64 regulierement espaces, comme le selecteur harmonique ADD. Tap selectionne un rang sans changer sa valeur. Glisser horizontalement deplace la selection ; glisser verticalement edite la grandeur choisie. Les deux knobs precis restent presents, quelle que soit la grandeur dessinee. Le numero de rang identifie toujours la cible, meme lorsque les overtones changent ses positions frequentielles.

L'apercu Overtones utilise les multiplicateurs du proprietaire natif, sur une echelle de frequence relative. Il est distinct du graphe d'edition par rang pour eviter les cibles superposees et les changements de selection. Aucun graphe ne pretend representer le rendu audio complet.

Magnitude 0 = harmonique inactive. Largeur relative 64 = neutre. Les echelles de magnitude sont Linear, -40/-60/-80/-100 dB. Pas de phase SUB ni de codage ADD centre sur 64. Les conversions physiques/unites doivent venir de SUBnoteParameters ; ne pas inventer une correspondance Hz/cents dans Compose.

Reset ONE remet la magnitude et largeur du rang a leurs defauts SUB ; ALL demande confirmation et suit l'action native Clear : fondamentale 127, autres magnitudes 0, toutes les largeurs 64. Ne pas remettre a zero les autres sections, l'echelle ou les overtones.

Le contrat C/P du bloc harmonique est implemente au proprietaire natif : il doit porter les 64 magnitudes/largeurs et les reglages harmoniques associes (echelle, start, overtones), independamment du rang visible. Amp/Frequency/Bandwidth/Filter ne doivent pas etre modifies par ce collage.

## Inventaire exhaustif des controles SUB

Reference : `third_party/zynaddsubfx/src/Params/SUBnoteParameters.{h,cpp}` et `src/UI/SUBnoteUI.fl`. La colonne historique indique le mapping Android absent apres 010 ; tous ces champs sont maintenant branches par 011/012. La maquette initiale reste une proposition de disposition, distincte du code Compose implemente.

| Natif | Controle / destination | Etat apres 010 |
|---|---|---|
| Pstereo | Stereo sur ecran kit uniquement | Present ; doublon editeur retire |
| Volume | Amp / Volume | **A ajouter**, float natif ; conversion SUB |
| AmpVelocityScaleFunction | Amp / Velocity | **A ajouter**, float natif ; conversion SUB |
| PPanning | Amp / Panning ; 0 = Random | Present, composant ADD reutilise |
| AmpEnvelope | Amp / enveloppe complete | Present, aucun flag ON natif |
| PDetune | Frequency / Fine detune + lecture musicale | **A ajouter** |
| PCoarseDetune | Frequency / Octave (-8..7) + Coarse (-64..63) | **A ajouter**, deux champs dans une valeur native |
| PDetuneType | Frequency / quatre courbes de detune | **A ajouter** |
| PBendAdjust | Frequency / Bend | **A ajouter**, transformation native |
| POffsetHz | Frequency / Offset Hz | **A ajouter**, transformation native |
| Pfixedfreq | Frequency / Fixed frequency (base 440 Hz) | Present |
| PfixedfreqET | Frequency / Equal temperament | **A ajouter**, conditionne par fixedfreq |
| PFreqEnvelopeEnabled + FreqEnvelope | Frequency / ON + enveloppe complete | Present |
| Pbandwidth | Bandwidth / largeur globale | Present, a regrouper |
| Pbwscale | Bandwidth / variation avec la frequence | Present, a regrouper |
| Pnumstages | Bandwidth / Noise filter stages (1..5) | Present, distinct du filtre global |
| PBandWidthEnvelopeEnabled + BandWidthEnvelope | Bandwidth / ON + enveloppe complete | Present, a regrouper |
| PGlobalFilterEnabled | Filter / ON | Present |
| GlobalFilter | Filter / tous les champs ci-dessous | Present |
| PGlobalFilterVelocityScale | Filter / Velocity amount | Present |
| PGlobalFilterVelocityScaleFunction | Filter / Velocity sensitivity | Present |
| GlobalFilterEnvelope | Filter / enveloppe complete, liee au ON du filtre | Present |
| Phmagtype | Harmonics / Magnitude scale (5 choix) | **A ajouter** |
| Phmag[64] | Harmonics / Magnitude par rang (0..127) | Grille draft ; graphe a implementer |
| Phrelbw[64] | Harmonics / largeur relative par rang (0..127) | Grille draft ; graphe a implementer |
| Pstart | Harmonics / Start (Zero, Random, Max.) | Present |
| POvertoneSpread.type | Harmonics / Overtones mode | **A ajouter**, 8 modes |
| POvertoneSpread.par1/par2/par3 | Harmonics / Parameter 1, Parameter 2, Force harmonics (0..255) | **A ajouter**, 3 controles |

Modes Overtones d'origine : Harmonic, ShiftU, ShiftL, PowerU, PowerL, Sine, Power, Shift. Conserver les valeurs lorsque le mode les rend inactives ; preciser les controles actifs selon les branches natives lors de l'implementation.

**Enveloppes completes** : attaque temps/valeur, decay temps/valeur lorsque pertinent, sustain, release temps/valeur, stretch, force release, linear, free mode et loop lorsqu'expose par le role ; nombre de points en lecture, edition de chaque temps/valeur, sustain point ou OFF, insertion/suppression et limites natives. Reutiliser le modele/editeur existant plutot que recreer les restrictions dans la maquette.

**Filtre complet** : categorie, type selon categorie, cutoff, Q, tracking, gain, stages (1..6), HPF/LPF Comb ; Formant count (1..12), slowness, clearness, center, octaves ; six voyelles, douze triplets frequency/amplitude/Q par voyelle ; sequence (taille 1..8, stretch, reverse et voyelle de chaque case). C/P filtre et voyelle existants a conserver, y compris champs masques et destinations hors ecran.

Les multiplicateurs `POvertoneFreqMult` sont derives, `time` et `last_update_timestamp` sont internes : aucun knob parallele. Les recalculs et mutations appartiennent au natif ; les gestes gardent les ecritures legeres et reconciliations en fin de geste.

## Maquette de discussion

La proposition interactive presente toutes les sections et controle le placement des champs manquants. Elle utilise des valeurs de demonstration, pas un instrument connecte. Le graphe de rang permet selection/edition des deux valeurs et les positions Overtones suivent les formules du source lu. Les apercus d'enveloppe sont schematiques ; les sous-editeurs communs illustrent leur emplacement sans remplacer leur implementation Compose validee. Les cartes HTML servent a discuter la disposition, pas a recreer des composants Android.

Verification locale : syntaxe JavaScript et parcours des cinq sections verifies avec JavaScriptCore ; presence des champs directs, bascule magnitude/width, selection du rang 64, Formant et Comb verifiees. Aucun rendu visuel navigateur, test audio ou validation sur appareil n'est revendique.

## Decisions retenues pour implementation

- Conserver les cinq sections et placer Overtones dans Harmonics, pres du graphe.
- Reprendre l'editeur ADD avec largeur relative a la place de phase.
- Garder le graphe d'edition par rang stable ; reserver les positions physiques a l'apercu depliable.
- Ajouter tout le mapping manquant de 011/012 ; la validation d'une disposition ne doit pas etre confondue avec l'execution de ces lots.

## Iteration validee apres premier rendu

Mick demande un sous-ecran harmonique et la meme preview en tete/reduction au scroll qu'Oscillator. Les metriques sont integrees au graphe ; les duplications gain/width et le libelle fixe logarithmic sont retires. Implementee localement : [bilan et limites](qa_subsynth_011_012.md#iteration-ux-apres-retour-sur-appareil--8-novembre-2026).

Derniere precision de Mick : aucune selection dans la preview et une hue par bande. Implemente : reference suivant la derniere note du clavier SUB, hues stables par rang partagees entre preview et editeur, points de pied pour bandes faibles sans modifier leur magnitude. [Executions et limites](qa_subsynth_011_012.md).

Essai suivant autorise par Mick : preview sur axe absolu fixe 20 Hz..20 kHz, logarithmique, pour rendre les changements de note visibles. Les rapports a f0 restent une annotation du rang selectionne. La largeur native relative/en octaves est conservee ; une largeur constante a l'ecran peut donc etre correcte. [Bilan](qa_subsynth_011_012.md).
