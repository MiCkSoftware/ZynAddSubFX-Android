# 012 — Terminer les harmoniques et overtones SUB

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Graphe SUB de magnitude/largeur de bande, positions des overtones, selection et edition des harmoniques. Reutiliser les gestes ADD lorsque pertinents.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependances

Apres 011, remplacer les longs grids de valeurs par une edition harmonique SUB complete et tactile, avec repartition des overtones et largeur de bande.

## Contexte verifie

- MAX_SUB_HARMONICS = 64 dans src/globals.h ; ne pas reprendre les 128 harmoniques d'ADD.
- Le bridge expose deja sub/harmonic/<index>/magnitude et /bandwidth ; pas encore Phmagtype ni POvertoneSpread.type/par1/par2/par3.
- SUBnoteParameters fournit convertBandwidth, convertHarmonicMag, updateFrequencyMultipliers et POvertoneFreqMult. Ce sont les references pour les unites et positions.
- UI/SUBnoteUI.fl definit les types de magnitude, l'action Clear, les huit modes d'overtones et les trois parametres dont ForceH. Relever la vraie action Clear et les defauts, sans copier aveuglement le reset ADD.
- FullInstrumentEditor.kt contient HarmonicOverview/HarmonicKnobCard, gestures et focus reutilisables ; une magnitude SUB vaut 0 pour silence, ce qui differe du codage ADD centre sur 64.

## Travail et criteres d'acceptation

- Exposer magnitude scale et overtone spread au niveau natif, recalculer via updateFrequencyMultipliers, fournir un graphe fidele du spectre/positions et de la largeur relative avec focus harmonique.
- Reutiliser les controles continus existants avec valeurs transitoires reduites, reconciliation en fin/annulation ; aucun snapshot complet par pixel ni 128 knobs visibles.
- Implementer Clear/reset et C/P du module concerne selon les primitives natives ; ne pas effacer les autres sections ou moteurs.
- Verifier les 64 rangs, zero/max, largeur relative, changements d'echelle et de repartition, cibles tactiles et retour depuis le detail.
- Tests cibles des conversions/positions et persistance des 64 valeurs, sans confondre rendu graphique et preuve audio.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
