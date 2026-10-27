# 004 — Simplifier la navigation de Resonance

## Mission

Traiter TODO.md « ad synth resonance : parameters tab nav useless » : donner un ecran direct avec courbe et controles accessibles sans navigation redondante.

## Constats et points d'entree

- FullInstrumentEditor.kt : resonanceEditorSections contient Curve et Parameters ; SynthEditorScaffold utilise ces ancres. AddResonanceEditorScreen affiche deja courbe, clipboard, actions et controles sur un seul ecran.
- Un Spacer de hauteur viewport est utilise pour rendre l'ancre Parameters atteignable ; reevaluer cet espace si les ancres sont retirees.
- Le dessin utilise drawingValues, interpolation des indices traverses et InstrumentEditorViewModel.commitResonanceCurve(original, values) en fin de geste.
- Le natif expose 256 points add/resonance/point/<index>. Un ancien chemin ecrivant chaque point avec refresh a provoque un ANR ; ne pas le restaurer.
- ParameterRoutingTest.resonancePointsNeverBecomeOverviewKnobs protege l'exclusion des points du grid de knobs.
- Les actions existantes (reset/lissage/randomisation), le copy/paste type, enabled, maxDb, center, octaves et protectFundamental doivent rester accessibles.

## Travail attendu et validation

- Retirer la navigation inutile et adapter uniquement le layout necessaire, en reutilisant le pattern de preview/scroll existant si utile.
- Conserver contexte Part > Kit > Resonance, retour systeme et barre vers ADD, clavier et edition de tous les parametres.
- Tester petit ecran, acces aux derniers controles, dessin rapide aller/retour, absence de trous et commit final. Ne pas confondre build reussi et resolution de l'ANR prouvee.

## Cadre de travail

- Lire AGENTS.md et respecter les modifications locales deja presentes. Ce prompt concerne une implementation locale ; il ne demande ni commit, ni push, ni installation ou publication.
- Contexte releve dans ce chat puis recontrole dans les sources le 27 octobre 2026. Verifier uniquement les zones touchees depuis ; ne pas recommencer un audit general du port.
- Les chemins de ce prompt sont relatifs a la racine du depot. Le moteur natif reste la source de verite ; l'etat Compose transitoire sert seulement a la reactivite de l'affichage.
- Reutiliser les composants et chemins de mutation existants. Identifier le proprietaire de l'etat avant de modifier le code.
- Build/tests ne prouvent ni la fluidite tactile, ni la parite audible. Rapporter exactement les verifications executees et celles restant a faire.

## Fin de tache obligatoire

Quand l'implementation et les verifications de ce perimetre sont terminees :

1. Mettre a jour TASKS.md et TODO.md : statut reel, anciens points correspondants, limites et QA restante.
2. Retirer de ces deux fichiers tous les liens vers ce prompt avant sa suppression ; conserver une note durable des resultats utiles.
3. Supprimer ce fichier prompt a la fin de l'implementation. Ne pas le supprimer pour une simple analyse ou si le travail reste incomplet ; dans ce cas, y noter le reste a faire.
