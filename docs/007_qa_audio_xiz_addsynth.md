# 007 — Valider AddSynth : audio, clipboard et XIZ

## Mission et dependances

Apres les corrections 000 a 006 pertinentes, executer une validation transversale ADD et consigner les resultats durables. Ce prompt ne demande pas de reecrire les fonctions deja presentes.

## Contexte acquis et points d'entree

- SynthEngine.kt et InstrumentEditorViewModel.kt : snapshot, ecriture, copyModule/pasteModule/canPasteModule, export et revision dirty.
- ZynAndroidEngine.cpp : clipboard natif type, modulePreview, parametres et serialization ; app/src/main/cpp/native_bridge.cpp relie les appels JNI.
- Tests existants : ParameterRoutingTest, SynthModuleModelsTest et CommonModulesInstrumentedTest. Le test instrumental couvre notamment preview/clipboard d'une enveloppe, compatibilite de type et metadonnees de routage ; il ne couvre pas toute la parite audible.
- design-qa.md rapporte une QA historique ciblee navigation/oscillateur sur Pixel 9 Pro, pas une validation audio globale. Sa limite sur le preview Kotlin est obsolete : modulePreview utilise desormais OscilGen::get.
- Enveloppes libres, Formant/voyelles/sequence, LFO et clipboard de modules sont deja implementes. Ne pas les presenter comme absents dans TASKS.md.
- L'import general XIZ/banques via SAF reste une tache distincte. Pour cette QA, utiliser les chemins de chargement/tests existants ou un harnais local cible ; ne pas etendre l'UX d'import sans necessite.

## Matrice de verification attendue

- Isolation entre parts/kits/voix, ADD+PAD, activations et mute ; absence de notes fantomes apres changements repetes de presets.
- Modes du modulateur, source interne/externe, references partagees et restrictions entre voix ; rendu audible et controles effectivement applicables.
- Filtres global/voix/bypass, Formant, enveloppes standard/libres, LFO (depth/start inclus), resonance.
- Clipboard Voice, oscillateur Voice/Modulator, enveloppe par role, LFO, filtre, voyelle et resonance : aller-retour, compatibilite/incompatibilite, changement de kit/part, conservation des champs non visibles.
- Export XIZ puis rechargement : valeurs, structure, activation, forme sonore et donnees avancees. Utiliser plusieurs presets existants dont Dual String Ct1 ; documenter les cas exacts.
- Gestes longs et rapides, retour systeme/barre, reouverture, dirty/export puis nouvelle edition.

## Livrable et validation

- Rapport durable dans docs/ avec appareil/emulateur, version/build, presets, actions, attendu/observe, limites et anomalies.
- Corriger les regressions trouvees dans le perimetre ; conserver les autres en TODO avec preuve reproductible.
- Mettre les statuts de la matrice TASKS.md en accord avec les preuves. Un test non execute ne devient pas coche ; ne pas remplacer l'ecoute et les gestes par une compilation.

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
