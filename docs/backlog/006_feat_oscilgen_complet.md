# 006 — Completer OscilGen pour Voice et Modulator

## Mission et dependances

Terminer la parite OscilGen, plus gros reste fonctionnel ADD. Reutiliser 001 pour les gestes et les memes primitives pour l'oscillateur de voix et de modulateur, sans deux implementations divergentes.

## Etat acquis

- FullInstrumentEditor.kt : AddOscillatorEditorScreen, OscillatorEditorTarget/Kind, HarmonicOverview et ModuleAddress.Oscillator(ownerVoice, modulator).
- ZynAndroidEngine.cpp : lambda addOscillator dans parameterSnapshot, branche osc/modOsc de setParameter et modulePreview(kind=1).
- Huit champs principaux sont exposes : magnitudeType, baseFunction, baseShape, waveshape, filterType, filter1, filter2, randomness. Toutes les magnitudes/phases harmoniques sont deja exposees.
- Attention : waveshape commande Pwaveshaping (quantite), pas Pwaveshapingfunction (algorithme). Modifier seulement la quantite ne complete pas la fonction.
- Le preview actuel utilise deja OscilGen::get cote natif, normalise et reechantillonne. Le texte residuel de design-qa.md decrivant un preview purement Kotlin est depasse ; ne pas reimplementer une approximation a partir de ce rapport.
- La source d'un oscillateur partage peut appartenir a une autre voix ; conserver ownerVoice, les libelles de provenance et les contraintes de reference aux voix precedentes.
- Le clipboard natif copie le module entier, pas seulement les controles visibles.

## Ecarts a traiter

Comparer third_party/zynaddsubfx/src/UI/OscilGenUI.fl, Synth/OscilGen.* et la serialization actuelle, puis ajouter lecture/ecriture et UI pour :

- Pwaveshapingfunction et l'ordre des traitements filtre/waveshaping selon le contrat natif.
- Pbasefuncmodulation et ses trois parametres.
- Pmodulation et ses trois parametres.
- Psatype/Psapar (ajustement du spectre).
- Pharmonicshift/Pharmonicshiftfirst.
- Padaptiveharmonics, power, basefreq et par.
- Pamprandtype/Pamprandpower.
- Les actions et vues natives encore manquantes (spectre/base, operations sur harmoniques), avec inventaire explicite des ecarts retenus ou reportes.

## Travail attendu et validation

- Etendre les chemins existants au proprietaire natif et les groupes UI existants ; conserver bornes/options/formats exacts du fork. Ne pas changer les choix harmoniques deja sauvegardes dans les presets.
- Reutiliser composants denses, preview repliable, navigation locale et clipboard type. Pas de calcul complet par mouvement.
- Verifier Voice et Modulator, source interne/partagee, changement de fonction et parametres dependants, harmoniques/phases, reset, copy/paste et sauvegarde/rechargement.
- Comparer la sortie sonore et le preview natif pour plusieurs combinaisons ; tenir compte de la normalisation et de la frequence de reference du preview, qui n'est pas un oscilloscope du flux audio.

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
