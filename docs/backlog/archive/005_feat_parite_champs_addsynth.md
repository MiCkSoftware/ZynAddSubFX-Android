# 005 — Completer les champs globaux et Voice manquants

## Avancement du 1 novembre 2026

Implementation locale : `Fadein_adjustment` → `add/fadeIn` → ADD / Amp ; `PBendAdjust` → `add/voice/<index>/bend` et `POffsetHz` → `add/voice/<index>/offsetHz` → Voice / Frequency ; `Unison_phase_randomness` → `add/voice/<index>/phaseRandom` → Voice / Unison. Le fade-in est un entier natif 0–127, defaut 20 ; bend et offset exposent la valeur native moins 64, plage -64–63, defauts 24 et 0. Le moteur serialize ces champs en XIZ. Le controle phase reste le chemin existant. Stereo et Random grouping restent au niveau ADD du kit.

Compilation debug et APK de tests Android, tests JVM : OK. Un test Android de lecture/ecriture et metadonnees a ete ajoute mais pas execute. Reste a verifier sur appareil la visibilite, l'edition, le reset, l'isolation part/kit/voice, l'aller-retour XIZ et le rendu audio ; aucune installation n'est demandee par cette fiche. Conserver ce prompt tant que ces verifications ne sont pas terminees.

## Mission et limites

Fermer les ecarts ponctuels de parite ADD/Voice sans refaire OscilGen (prompt 006) ni les composants communs deja implementes. L'objectif est la parite du fork vendored, pas une copie de l'interface desktop.

## Constats et points d'entree

- native/engine_adapter/ZynAndroidEngine.cpp expose deja add/voice/<index>/bend et offsetHz, et sait les ecrire dans PBendAdjust et POffsetHz.
- FullInstrumentEditor.kt : AddVoiceDetailScreen filtre Frequency par une whitelist detune/fixedFreq/fixedFreqEt/octave/detuneType/coarse, ce qui exclut ces deux champs pourtant disponibles.
- ADnoteUI.fl expose Fadein_adjustment dans les parametres globaux ; ce champ n'apparait pas dans le snapshot ADD actuel. Retrouver son stockage et sa serialization dans Params/ADnoteParameters avant d'ajouter le plus petit contrat lecture/ecriture.
- Unison_phase_randomness est deja expose sous phaseRandom : ce n'est pas un manque natif. Verifier sa visibilite dans la selection de la section Unison plutot que creer un deuxieme parametre.
- Punch, detune global, filtres, enveloppes et LFO sont deja branches. FormantFilterEditor, FreeEnvelopeEditor et le copy/paste natif existent.
- Le contrat ParameterDescriptor porte module/scope/section/family/ownerIndex ; ne pas remplacer le routage structure par des libelles d'affichage.
- Source de comparaison : third_party/zynaddsubfx/src/UI/ADnoteUI.fl et Params/ADnoteParameters.*. TODO.md contient un inventaire partiel de champs, pas une preuve de leur absence.

## Travail attendu et validation

- Exposer les deux champs caches, puis completer le fade-in et les autres ecarts precis identifies dans ce perimetre. Produire une courte correspondance native -> chemin -> ecran pour les ajouts.
- Verifier bornes, valeurs par defaut, unites/conversions, scopes part/kit/voice et serialization XIZ. Ne pas deplacer Stereo ou Random grouping vers une propriete de part.
- Reutiliser DenseParameterGrid et ses callbacks de geste. Ajouter les regressions utiles pour le routage/les conversions ; verifier visibilite et edition sur ecran.

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
