# 003 — Uniformiser les activations et reduire les enveloppes desactivees

## Mission

Traiter les deux notes TODO.md : remplacer les toggles d'enveloppe Voice par 0/1 avec une presentation coherente sur ADD principal ; masquer/reduire les controles inutiles d'une enveloppe desactivee en gardant un acces clair a son edition.

## Contexte acquis et points d'entree

- SynthModuleComponents.kt : CompactModuleActions utilise encore un Switch ; EnvelopeUI dessine ensuite la courbe et les knobs sans condition d'activation.
- SynthModuleModels.kt : EnvelopeModel et ModuleAddress.Envelope distinguent global, voix et roles amplitude/frequency/filter/modulator amplitude/modulator frequency.
- Les flags voix sont ampEnvelopeEnabled, freqEnvelopeEnabled, filterEnvelopeEnabled, modAmpEnvelopeEnabled et modFreqEnvelopeEnabled. Les enveloppes globales n'ont pas necessairement de flag natif equivalent : ne pas inventer un enable global ni detourner une valeur de profondeur.
- Reutiliser LedButton et la presentation 0/1 deja employee dans EditorScreen.kt si appropriee, avec libelles accessibles.
- Les enveloppes libres multipoints et FreeEnvelopeEditor existent deja. Ne pas les reimplementer. Le passage au mode libre appelle converttofree avant Pfreemode=true cote natif.
- Les previews et leurs knobs doivent rester propres au role : pas de sustain artificiel pour la frequence, pas de cellules vides ou de doublons de modules.
- CommonSynthModules et FullInstrumentEditor.kt gerent les points d'entree ; garder Frequency avant Amplitude et les controles de base avant enveloppes/LFO.

## Travail attendu et validation

- Choisir une presentation compacte coherente : activation disponible en permanence, preview ou ouverture de l'editeur au besoin, controles reduits quand le module est desactive.
- Ne pas effacer les valeurs en desactivant. Sur ADD principal, harmoniser le style uniquement la ou la semantique native le permet.
- Verifier chaque role global/voix/modulateur, on/off puis restauration, mode libre, copy/paste, accessibilite et absence de doublons.

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
