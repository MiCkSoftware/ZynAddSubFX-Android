# 011 — Completer les parametres SUBsynth

## Mission et dependances

Apres 010, terminer Amplitude/Frequency/Bandwidth/Filter de SUB. Les harmoniques et overtones graphiques sont dans 012.

## Contexte verifie

- ZynAndroidEngine::parameterSnapshot expose seulement sub/stereo, panning, bandwidth, bandwidthScale, stages, start, fixedFreq, filterEnabled, plus les 64 magnitudes/bandwidths harmoniques.
- setParameter a une branche sub/ symetrique et appelle updateFrequencyMultipliers apres chaque ecriture.
- Source precise : third_party/zynaddsubfx/src/Params/SUBnoteParameters.{h,cpp} et src/UI/SUBnoteUI.fl. Volume et AmpVelocityScaleFunction sont des float dans ce fork : ne pas copier les conversions d'un autre moteur.
- Manquent notamment Volume/velocity, AmpEnvelope, detune/type/octave/coarse, bend/offset, fixedfreqET, FreqEnvelope et son enable, BandWidthEnvelope et son enable, filtre global complet/velocity/enveloppe.
- FullInstrumentEditor.kt : les sections SUB viennent de InstrumentEditorViewModel.tabsFor ; elles affichent aujourd'hui une grille generique. Les modules riches ADD sont reutilisables apres 010.

## Travail et criteres d'acceptation

- Completer snapshot/ecriture, bornes/options/defauts/conversions et chaque section, avec controles directs puis modules. Respecter les flags reels et masquer sans effacer.
- Garder Stereo au moteur du kit et ne pas confondre avec les parametres de part. Conserver clavier, contexte Part > Kit > SUB et retour systeme/barre coherent.
- Verifier les cas linear/log, frequence fixe/temperament, detune musical et filtres par categorie y compris Formant et Comb.
- Pas de LFO ajoute artificiellement a SUB. Les recalculs structurels restent au proprietaire natif.
- Tests cibles lecture/ecriture et metadonnees, bornes/reset, isolation ADD/PAD et export/rechargement d'un SUB seul puis kit mixte. Validation finale audio differee.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
