# 008 — Liste de choix sur appui long des knobs enum

## Statut et demande

Feature demandee le 31 octobre 2026, a implementer separement. Le drag direct reste disponible.
Un appui long sur un knob representant un enum doit ouvrir une liste de choix lisibles, par exemple
Use oscillator : Internal, Voice 1, etc. Ne pas remplacer ce geste par un reset implicite.

## Contrat et proprietaires

- `FullInstrumentEditor.kt` : `DenseParameterControl` affiche actuellement les enums a labels
  verticaux comme TinyKnob et ignore leur appui long. `DenseControlCard` possede deja combinedClickable.
- `ParameterDescriptor` possede type, options, bornes et valeur. Utiliser les indices natifs exacts ;
  ne pas inventer de remappage et ne pas montrer les options situees hors des bornes du controle.
  Exemple : Voice 2 ne peut choisir que Internal et Voice 1, meme si le tableau options est plus long.
- Reutiliser les selecteurs / feuilles d'edition existants (`ParameterEditSheet`, `CompactSelector`,
  points d'entree onLongPress/onEdit) avant d'ajouter un composant.
- L'UI orchestre l'ouverture ; la valeur selectionnee passe par `InstrumentEditorViewModel.write`.
  Aucun JNI direct depuis la feuille, aucun second chemin de mutation.
- Terminer/reconcilier un drag en attente avant l'ouverture. Un appui long seul ne modifie aucune valeur.
  Choisir ecrit une seule valeur ; annuler/fermer conserve l'etat courant.
- Garder les callbacks a jour pour ne pas appliquer un choix a une autre voix/kit apres navigation.
- L'appui long ne doit pas entrer en concurrence avec le drag vertical, le scroll parent ou le tap
  deja utilise par les controles booleens. Conserver le comportement des knobs numeriques.

## Validation attendue

- Deux choix, plusieurs choix, option courante, annulation, choix identique et bornes restreintes.
- Cas Use oscillator / external modulator, modes et types de filtre ; libelles longs et grande police.
- Aucun reset ou changement musical a l'ouverture ; aucune valeur perdue apres changement de contexte.
- Pas de snapshot/preview par mouvement et pas de collection volumineuse reconstruite pendant un geste.
- Accessibilite : action d'ouverture nommee, selection courante annoncee, fermeture et retour de focus.

## Perimetre

La correction de sensibilite des petits ranges est deja traitee dans TinyKnob via un maximum
32 dp de deplacement par pas. Cette feature concerne seulement la selection explicite par liste.
Implementation locale, sans commit/push/installation/publication demandes.
A la fin, actualiser TASKS.md/TODO.md et conserver une note durable des resultats et de la QA restante.
