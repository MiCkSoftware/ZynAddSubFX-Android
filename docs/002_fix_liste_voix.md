# 002 — Aligner la liste des voix et retrouver sa semantique native

## Mission et dependance

Traiter TODO.md « addsynth voices colmun not aligned » et « finish parity with legacy voices list ». Reutiliser le chemin de geste corrige par 001 ; ne pas reintroduire model.write avec snapshot par mouvement.

## Constats et points d'entree

- FullInstrumentEditor.kt : VoiceMatrix et VoiceMatrixKnob, huit voix. L'entete No./Vol/Pan/Res/Detune/Vibrato ne reserve pas la meme geometrie que les lignes, qui ajoutent numero et checkbox Enabled. Les poids/espaces ne peuvent donc pas aligner toutes les colonnes.
- La colonne Vibrato utilise value("vibrato"), exposee dans ZynAndroidEngine.cpp depuis v.Unison_vibratto.
- La liste native third_party/zynaddsubfx/src/UI/ADnoteUI.fl utilise FreqLfo/Pintensity pour son Vibrato. Le chemin Android correspondant est add/voice/<index>/freqLfo/depth ; ne pas confondre avec le vibrato Unison qui doit rester disponible dans sa propre section.
- Les autres points natifs de cette liste incluent Enabled, PVolume, PPanning, Presonance, PDetune/detunevalue. Confirmer formats et valeurs avec le fork vendored avant de revendiquer une parite complete.
- L'entree vers le detail Voice et le contexte Part > Kit > Voice existent deja.

## Travail attendu et validation

- Partager la definition des colonnes entre entete et lignes, en conservant des cibles tactiles utilisables.
- Raccorder et nommer les controles selon leur fonction native ; distinguer valeur brute detune et affichage musical, sans inventer une conversion.
- Clarifier Res/Resonance et l'activation de voix, conserver l'acces au detail pour chacune des huit voix.
- Verifier portrait et petite largeur, alignement reel, voix desactivees, modifications puis reouverture du detail. Confirmer que Vibrato de liste modifie bien le LFO de frequence et pas Unison.

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
