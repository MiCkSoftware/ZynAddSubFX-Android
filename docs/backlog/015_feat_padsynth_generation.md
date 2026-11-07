# 015 — Terminer qualite et regeneration des samples PAD

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Bloc qualite/Apply et parcours de regeneration PAD : modifications en attente, progression, annulation, succes et erreur.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependances

Apres 013/014, rendre les edits de structure PAD effectifs via un chemin de regeneration robuste et explicite. Ne pas declarer PAD termine si seuls les parametres changent.

## Contexte verifie

- PADnoteParameters::Pquality contient samplesize, basenote, oct, smpoct ; basenote manque au bridge actuel. Les plages actuelles doivent etre confrontees aux choix UI/PADnoteUI.fl, pas supposees exhaustives.
- setParameter(pad/...) modifie les champs puis retourne ; aucune regeneration PAD n'y est declenchee.
- PADnoteParameters fournit applyparameters(do_abort, max_threads), sampleGenerator(cb, do_abort, max_threads) et Sample. La generation est couteuse et peut etre multithread.
- ZynAndroidEngine.cpp contient applyAndInitPartWithTimeout et kInstrumentApplyTimeout=1200ms pour le chargement. Auditer sa reutilisation sans reinitialiser l'instrument complet pour une simple edition PAD.
- app/build.gradle.kts selectionne actuellement FFTW3F_NATIVE, meme si docs/porting/fft_backend_strategy.md decrit un ancien shim/vendor incomplet. Verifier la config CMake reellement construite et la signature native avant d'accuser FFT ; ne pas lancer une migration FFT sur la seule base de cette ancienne note.

## Travail et criteres d'acceptation

- Completer les choix de qualite et leurs unites ; definir une action Apply/rebuild ou un commit explicite conforme au natif, avec etat pending/generating/done/failed, annulation si supportee et erreurs utiles.
- Calcul hors thread UI/audio, transfert/remplacement des samples a la couche proprietaire et a un point sur ; ni tableau PCM Compose ni remplacement de buffers utilises par des notes actives.
- Une edition plus recente pendant le calcul ne doit pas etre perdue ni remplacee par un resultat obsolete. Tester changement de part/kit/preset, fermeture et generation interrompue.
- L'export/session doit conserver le vrai contrat de parametres et restaurer des samples applicables au chargement ; distinguer dirty de parametres et generation appliquee.
- Tester plusieurs qualites avec limite memoire/temps, PAD seul et kits mixtes. Mesurer absence de blocage et produire au moins un controle technique de PCM non nul ; l'ecoute comparative finale reste differee.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
