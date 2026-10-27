# 001 — Alleger les gestes continus AddSynth

## Mission et priorite

Appliquer la regle AGENTS.md aux derniers chemins continus couteux : liste des voix et harmoniques d'oscillateur. A faire avant les nouveaux controles OscilGen pour reutiliser un chemin fluide.

## Constats et points d'entree

- app/src/main/java/com/mick/zynaddsubfx/InstrumentEditorViewModel.kt : write(..., refresh = true) relit engine.parameterSnapshot puis incremente revision ; dragParameter differe cette lecture et finishParameterDrag/commitEdits resynchronisent a la fin.
- Attention : meme refresh=false reconstruit actuellement la liste snapshot.values via map. AGENTS.md interdit aussi la reconstruction d'une collection volumineuse a chaque mouvement ; mesurer et garder un etat transitoire reduit si necessaire.
- FullInstrumentEditor.kt : VoiceMatrix -> VoiceMatrixKnob -> TinyKnob utilise onWrite relie a model::write.
- AddOscillatorEditorScreen : les HarmonicKnobCard Magnitude/Phase et le onMagnitudeChange de HarmonicOverview appellent model.write. HarmonicOverview possede deja des valeurs de geste et rememberUpdatedState, mais ne fournit pas encore de commit dedie.
- SynthModuleComponents.kt et les DenseParameterGrid montrent les callbacks onWrite/onDrag/onCommit existants. UiControls.kt contient TinyKnob.
- Native : les ecritures d'oscillateur appellent o.prepare(), donc differer seulement le snapshot ne prouve pas que tout le chemin est leger.
- Resonance a deja un autre contrat : dessin local puis commitResonanceCurve a la fin. Ne pas retablir des snapshots par point.

## Travail attendu et validation

- Garder les ecritures necessaires au son pendant le geste, une valeur locale immediate et une seule reconciliation complete a la fin/annulation selon le contrat du controle.
- Reutiliser les primitives existantes ; si un callback de fin manque, l'ajouter au composant proprietaire plutot que dupliquer des knobs.
- Conserver le geste harmonique horizontal = selection, vertical = magnitude, ainsi que reset et edition numerique.
- Verifier que snapshots, previews natives et inspections globales ne sont pas recalcules par mouvement. Mesurer les appels avant/apres et tester des gestes longs/rapides sur appareil ou emulateur disponible.
- Verifier valeur finale, son, refresh du preview et absence de valeur perdue a l'annulation/changement de selection.

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
