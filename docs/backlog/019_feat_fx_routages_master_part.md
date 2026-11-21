# 019 — Terminer les routages FX Part et Master

## Avancement — navigation révisée le 14 novembre 2026

À la demande explicite de Mick : trois lignes FX ON/OFF dans la Part, entrée FX
à droite de Performance vers Master/System (4) et Insert (8), et Edit à droite
de Part controls. Éditeur commun sans combo Scope ; bypass Master natif conservant
les paramètres et enregistré en session. [Bilan](../qa_fx_016_018.md#itération-navigation-demandée-par-mick).
Le lot reste ouvert pour les chaînes, assignations, sends et retours éditables ;
leur conception complète demeure à valider. Cette itération ne les implémente pas.


### Assignation des Inserts — 15 novembre 2026

Proposition du knob Source (Off, Master, Part 1..16), avec popup sur appui long,
acceptée explicitement par Mick (« ok vas y »), puis implémentée dans le panneau
Routing de chaque Insert, même Off. L'affectation est appliquée au relâchement,
via Master::setInsertionFxSource, et conservée en session/XMZ. Le clipboard et
Restore ne changent pas la source de destination. Contrat natif d'isolation,
domaines/indices, lecture et persistance réussi ; builds Android/JVM réussis.
Les chaînes Part et sends/retours System restent ouverts ; cette approbation ne
vaut pas validation de leur nouvelle UX.


### Envois System → System — 15 novembre 2026

Mick a retenu leur placement dans Routing : knobs To Master N uniquement vers
les slots suivants, numérotation visible 1..4. Les six liaisons 1→2/3/4,
2→3/4 et 3→4 sont exposées ; System 4 ne propose pas d'envoi sortant.
Primitive propriétaire Master::setPsysefxsend existante, bornes/indices validés
par l'adaptateur, gains dérivés préservés et sauvegarde XMZ existante.
Contrat natif (six liens, bornes, invalides/boucles, isolation et persistance),
38 tests JVM et builds Android réussis. Ajustement visuel RECP : 5 dp entre boutons.
Les routages de chaîne Part restent ouverts ; la qualification audio/appareil
reste à faire.


### Presets EQ propres à l’application

- [ ] Créer une banque de presets EQ utiles et distincts, avec des noms explicites, à la place du choix actuel EQ1/EQ2 identique.
- Définir les courbes avec Mick et les valider à l’écoute avant intégration.
- Conserver la compatibilité des presets et sessions existants, ainsi que le reset vers le preset choisi.

Demande de Mick du 15 novembre 2026. Feature à concevoir ; non implémentée.

## Prototype du routage Part à essayer

- Dans chaque **Part FX**, réutiliser la section **Routing** avec un knob
  **Destination** et son popup sur appui long. Pour FX 1 et FX 2, proposer
  **Next FX**, **Part Out** et **Dry Out**. **Dry Out** envoie le signal sec
  vers la sortie Part et le signal traité vers l'effet suivant, pour les
  effets qui séparent ces deux signaux. Indiquer l'exception de l'EQ, qui
  transmet son signal égalisé. Le dernier slot aboutit à la sortie Part.
- Dans chaque carte **Kit**, ajouter un knob **To Part FX**. Choix proposés
  **FX 1**, **FX 2**, **FX 3** et **Part Out**. Griser ce réglage hors mode kit,
  puisque le moteur démarre alors à FX 1. En mode kit, la destination choisie
  s'applique aux nouvelles notes.
- Conserver les lignes des slots et leur bypass. Mettre à jour la destination
  au relâchement du knob ou au choix dans le popup.

Mick a donné son accord pour essayer cette disposition le 21 novembre 2026.
Le prototype est implémenté. Le contrat natif vérifie les trois destinations,
les écritures refusées sur les autres scopes et le dernier slot, l’isolation,
puis la relecture des deux réglages dans un XIZ. Build Android debug et tests
JVM réussis. L’essai ergonomique et l’écoute du chaînage restent à faire sur
appareil avant de clore le lot.

Suite à l’essai sur alpha.27, les FX Part regroupent les réglages dans
**Routing / Mix**. FX 3 y affiche **Destination · Part Out** en lecture seule.
La section **Instrument kits** expose désormais **Kit mode** (Off, Multi, Single).
Choisir Multi ou Single suffit pour dégriser **To Part FX**, même avec un seul
kit. Le réglage **Kit count** continue d’activer ou de désactiver le mode selon
le nombre de kits lorsque ce nombre change.

Après retour de Mick, **To Part FX** prend la forme de quatre boutons LED sous
PADsynth dans chaque kit. **Off** affiche l’entrée native FX 1 et désactive la
rangée. **Multi** ou **Single** permet de choisir l’entrée, même avec un seul kit.

Le mode Kit se règle avec un bouton d’état dans le bandeau **Instrument kits**.
Chaque appui passe au mode suivant dans l’ordre Off, Multi, Single.

## Conception UX avec Mick avant implementation

- **Passe de design/conception obligatoire avec Mick avant de coder la nouvelle UX.** Perimetre : Presentation et manipulation des chaines Part, assignations Insert, sends/retours System et passage entre scopes.
- Preparer une proposition concrete et reviewable (schema ou maquette, placement, interactions, etats et navigation) dans le langage visuel ADD existant, puis la discuter avec Mick et l’iterer selon ses retours.
- Attendre sa validation explicite de cette proposition avant d’implementer les nouveaux composants/parcours concernes. L’accord pour ce backlog ne constitue pas une validation du design a venir.
- Consigner la decision retenue dans ce prompt ou une note durable pour la suite. Les travaux natifs et tests independants de ces choix peuvent avancer sans attendre.

## Mission et dependances

Apres 016 a 018, terminer les parcours FX au niveau Part et Main/Master : placements, bypass, sends et retours. La presence de slots seuls ne termine pas les FX.

## Contexte verifie

- Part : trois partefx, Pefxroute, Pefxbypass et kit.Psendtoparteffect (kit/sendFx deja expose). Un effet Part appartient a la part, pas au kit.
- Master : huit insefx assignes via Pinsparts ; quatre sysefx ; Psysefxvol[fx][part] et Psysefxsend[fx][fx], avec setters natifs et stockage de gains derive. Verifier les restrictions sur les envois entre effets ; ne pas permettre de boucle non supportee.
- SynthEngine.setSystemFxSend existe deja, tout comme inspectMixer et EditorScreen « To system FX ». PartFxRouting expose certaines informations ; cela ne prouve pas que tous les reglages sont editables.
- Misc/Master.{h,cpp}, Misc/Part.{h,cpp}, UI/MasterUI.fl/PartUI.fl et EffUI.fl definissent les semantiques. Retrouver les fichiers via rg si le nom de la fenetre Master differe.

## Travail et criteres d'acceptation

- Rendre editables chaines/bypass/routages Part et destination des kits sans modifier ADD/SUB/PAD ni imposer un moteur unique par kit.
- Ajouter selection/edition des huit inserts et assignations, des quatre systemes, niveaux sends parts et inter-systemes/retours selon les fonctions natives.
- Donner un acces Main et Part coherent vers le meme editeur de slot ; supprimer les placeholders FX remplaces. Afficher scopes et cibles explicitement.
- Reutiliser les setters existants pour ne pas desynchroniser les valeurs brutes et les gains derives ; gestures locales puis inspection unique au commit.
- Tester indices internes vs affichage, changement de part et slots inactifs, isolation et valeurs persistantes. XIZ garde les FX Part ; les FX Master necessitent session/XMZ, ne pas les promettre dans un XIZ.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
