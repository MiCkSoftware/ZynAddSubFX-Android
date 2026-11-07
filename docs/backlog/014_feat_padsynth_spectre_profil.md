# 014 — Porter profil, spectre, oscillateur et resonance PAD

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Profil d’une harmonique PAD et graphe des positions des overtones. Les editeurs oscillateur et resonance ADD sont a reutiliser, sans nouvelle conception de leurs controles.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependances

Apres 010 et 013, terminer la structure harmonique PAD et ses vrais graphes natifs. Le commit des samples est pris en charge par 015.

## Contexte verifie

- Params/PADnoteParameters.h : Php comporte base.type/par1, freqmult, modulator.par1/freq, width, amp.mode/type/par1/par2, autoscale, onehalf. Seuls width/autoscale sont actuellement exposes.
- Phrpos.type/par1/par2/par3 regit les positions ; Pmode definit Bandwidth/Discrete/Continuous et conditionne l'applicabilite du profil.
- PAD possede son propre oscilgen et resonance ; l'oscillateur Voice ADD ne doit pas en devenir un etat parallele.
- Fonctions natives getprofile, getNhr, setPbandwidth ; UI/PADnoteUI.fl, Fl_PADnoteHarmonicProfile.h et Fl_PADnoteOvertonePosition.h sont les references des graphes.
- FullInstrumentEditor.kt a deja l'editeur OscilGen complet (parametres avances, harmoniques/phases, formes/spectres natifs, actions) et ResonanceCurve. Le contrat doit etre adresse vers PAD, sans recopier les ecrans.
- La section Spectrum PAD forcee par populatedSections aboutit encore a ComplexEditorSheet, texte reserve sans editeur.

## Travail et criteres d'acceptation

- Exposer tous les champs du profil et des positions, leurs options/defauts et domaines ; lire les valeurs natives pour produire previews fidelement, sans generer toute la banque de samples par frame.
- Rendre accessibles oscillateur, resonance, profil et positions depuis PAD, avec C/P type et provenance correcte.
- Respecter les modes qui rendent certains parametres inapplicables sans perdre les valeurs. Un graphe de profil n'est pas le spectre audio final.
- Reutiliser layout repliable/scroll, navigation et gestes locaux ; retirer les placeholders remplaces.
- Tests des modes, courbes/positions pour valeurs connues, isolation ADD/PAD, actions/clipboard et XIZ avec base personnalisee, resonance et champs caches.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
