# 017 — Porter les effets Reverb, Echo, Chorus, Phaser, Alienwah et Distortion

## Mission et dependance

Apres 016, completer les six panneaux d'effet courants, presets et parametres effectifs, en reutilisant un modele de slot et les composants existants.

## Contexte et sources

- Sources : src/UI/EffUI.fl et src/Effects/{Reverb,Echo,Chorus,Phaser,Alienwah,Distortion}.{h,cpp}, ainsi que EffectLFO pour les effets concernes.
- Les listes d'options, presets et l'indexation des parametres appartiennent a chaque effet. Certains champs sont enum/bool, d'autres 0..127 avec affichage non lineaire : pas de grille anonyme de 128 knobs.
- Distortion inclut les choix de waveshaping, filtres et ordre de traitement ; Chorus couvre aussi les modes flange pertinents. Relever tous les controles du fork plutot que seulement les noms dans l'ancienne matrice.
- Les gains/dry-wet et le comportement insertion/systeme peuvent differer : EffectMgr est la reference, pas une equivalence visuelle des deux scopes.

## Travail et criteres d'acceptation

- Produire pour chaque effet la correspondance parametre natif -> type/bornes/options -> controle et conditions d'applicabilite.
- Brancher tous les parametres et presets natifs utiles, LFO/references temporelles, reset et clipboard d'effet via le proprietaire natif si le contrat upstream le permet.
- Reutiliser DenseParameterGrid, controls enum sur appui long, labels accessibles et geste onDrag/onCommit ; ne pas declencher d'inspection du mixer par mouvement.
- Tester changements de preset suivis d'edition, OFF/ON/type different, valeurs extremes et absence de contamination de slots voisins.
- Verifier chaque panneau dans Part, Insert et System selon disponibilite native. Tests de donnees et serialization locaux ; l'ecoute comparative des six effets fait partie de 021.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
