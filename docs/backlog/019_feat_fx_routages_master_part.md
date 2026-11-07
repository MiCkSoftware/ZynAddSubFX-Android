# 019 — Terminer les routages FX Part et Master

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
