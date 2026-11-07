# 016 — Brancher les slots FX et leur contrat natif

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Presentation et navigation d’un slot FX : scope, type, preset, bypass et acces a l’edition, en reutilisant les controles ADD.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependances

Socle commun des lots FX 017 a 019 : adresser et editer un effet reel dans les trois scopes. Ne pas creer trois editeurs concurrents.

## Contexte verifie

- Le DSP est compile : Effects/EffectMgr et ses dix effets existent. Les ecrans FX Compose affichent encore des placeholders ; parameterSnapshot/setParameter ne fournissent aucune famille fx/ effective.
- globals.h : NUM_PART_EFX=3 par part, NUM_INS_EFX=8 au Master, NUM_SYS_EFX=4 au Master.
- Misc/Part.h possede partefx, Pefxroute et Pefxbypass ; Misc/Master.h possede insefx, sysefx, Pinsparts, Psysefxvol, Psysefxsend.
- EffectMgr propose geteffect/changeeffect[rt], getpreset/changepreset[rt], geteffectpar[rt]/seteffectparrt, cleanup, paste et serialization XML. Lire les annotations RT/NONREALTIME et le fonctionnement init/settings : ne pas choisir un appel seulement a partir de son nom.
- Types : 0 Off, 1 Reverb, 2 Echo, 3 Chorus, 4 Phaser, 5 Alienwah, 6 Distortion, 7 EQ, 8 Dynamic Filter, 9 Sympathetic, 10 Reverse. Reverse etait absent de TASKS.md.
- SynthEngine.inspectMixer/setSystemFxSend et EditorScreen « To system FX » existent ; l'inspection des effets actifs ne remplace pas une API d'edition du slot courant.

## Travail et criteres d'acceptation

- Definir le plus petit adressage scope/part/slot commun, descriptors types et revision ; ne pas utiliser kitIndex comme proprietaire d'un effet Part ou Master.
- Brancher type, preset, parametres reels, bypass/reset et actions utiles au bon proprietaire. Le choix de preset modifie plusieurs champs et demande une reconciliation structurelle.
- Garder mutations natives dans les proprietaires et la queue/point de synchronisation approprie ; pas de construction/allocation d'effet arbitraire depuis Compose ou par pixel.
- UI reutilisable de slot avec contexte lisible, clavier et retour ; selection d'un slot Off possible, pas seulement des effets actifs.
- Tests d'isolation scopes/indices, invalides, switch type/preset et reset ; les panneaux par effet suivent en 017/018, le routage complet en 019.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
