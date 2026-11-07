# 013 — Completer les parametres de jeu PADsynth

## Mission et dependances

Apres 010, porter les parametres Amplitude/Frequency/Filter PAD. La structure spectrale et la regeneration sont respectivement dans 014 et 015.

## Contexte verifie

- ZynAndroidEngine expose 12 champs PAD : stereo, panning, volume, bandwidth, bandwidthScale, mode, profileWidth, profileAutoScale, qualitySize, qualityOctaves, qualitySamplesPerOctave, fixedFreq.
- Source : Params/PADnoteParameters.{h,cpp}, UI/PADnoteUI.fl. Sont presents dans le natif mais absents du bridge : detune/type/octave/coarse, fixedfreqET, bend/offset, velocity, fade-in/Punch, Amp/Freq/Filter envelopes et LFO, filtre global et velocity.
- Les roles globaux PAD n'ont pas tous des flags d'activation : porter les vrais contrats et ne pas ajouter de faux ON.
- Le modele UI commun et ModuleAddress doivent distinguer PAD/ADD grace a 010 ; ne pas contourner en utilisant GlobalPar ADD.

## Travail et criteres d'acceptation

- Completer le chemin natif -> descriptors -> ecriture -> sections Compose avec les composants communs, previews et C/P.
- Utiliser les valeurs et defauts PAD exacts ; verifier unites et conversions de Volume, velocity, detune, temps et cutoff avec le fork.
- Garder les controles live separes des changements de structure qui necessitent des samples PAD regeneres. Ne pas appeler applyparameters sur chaque geste.
- Conserver le contexte du kit et les parametres des autres moteurs, navigation/clavier et le mode libre.
- Tests cibles par role, flag ou absence de flag, bornes et scopes ; export/reload d'un PAD sans ADD et kit multi-moteurs. Pas de nouvelle campagne audio finale avant les FX.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
