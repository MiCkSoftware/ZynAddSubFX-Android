# 018 — Porter EQ, Dynamic Filter, Sympathetic et Reverse

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Courbe EQ interactive, selection et edition des huit bandes. Les panneaux Dynamic Filter/Sympathetic/Reverse reutilisent les composants existants ; toute interaction nouvelle decouverte doit aussi etre concertee.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependance

Apres 016/017, terminer les quatre effets specialises qui ne se reduisent pas a une grille generique. Reverse fait partie de la parite du fork.

## Contexte et sources

- Effects/{EQ,DynamicFilter,Sympathetic,Reverse}.{h,cpp}, UI/EffUI.fl, FilterUI.fl, EffectMgr.cpp et globals.h.
- MAX_EQ_BANDS=8 ; EffectMgr::getEQfreqresponse existe. Les frequences/largeurs/gains de bandes et stages doivent garder leur codage natif.
- EffectMgr::filterpars fournit le filtre du Dynamic Filter ; reutiliser FilterEditor/Formant et l'adressage proprietaire FX, sans ecrire dans le filtre d'un instrument.
- Reverse est le type 10 dans EffectMgr ; sa gestion temps/tempo et ses modes sont deja dans le DSP. Le support MIDI est toujours differe : utiliser les horloges/synchronisations existantes sans inventer un sous-systeme MIDI.
- Presets, actions et parametres Sympathetic sont propres a cet effet ; ne pas les confondre avec PAD ou une banque d'instruments.

## Travail et criteres d'acceptation

- EQ : selection des huit bandes, types/stages/freq/Q/gain et courbe native coherente avec bandes actives et echelle frequentielle.
- Dynamic Filter : controles de modulation et filtre complet, Formant inclus si applicable, avec clipboard/preview scope FX.
- Sympathetic et Reverse : controles, modes et presets complets du fork, unites temporelles et dependances lisibles.
- Garder les traitements/allocations au natif et gestes legers. Verifier isolation d'un Dynamic Filter versus filtres ADD/SUB/PAD.
- Tests des champs et previews, changements de presets/type, reset, clipboard et serialization par scope ; ne pas declarer la parite uniquement a partir d'une courbe ressemblante.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
