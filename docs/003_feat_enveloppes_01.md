# 003 — Harmoniser les modules, Polarity et clipboard Amplitude

## Mission

Harmoniser les en-tetes Filter/enveloppes/LFO et reduire les controles des modules desactives ; clarifier la polarite et ajouter un clipboard limite au bloc Amplitude. Les decisions ci-dessous remplacent l'ancienne proposition d'activation par 0/1.

## Contexte acquis et points d'entree

- SynthModuleComponents.kt : CompactModuleActions utilise deja un petit LedButton ON vert et les actions C/P compactes ; EnvelopeUI et LFOUI dessinent encore leurs courbes/knobs lorsque desactives. Conserver ces primitives et harmoniser leur composition.
- SynthModuleModels.kt : EnvelopeModel et ModuleAddress.Envelope distinguent global, voix et roles amplitude/frequency/filter/modulator amplitude/modulator frequency.
- Les flags voix sont ampEnvelopeEnabled, freqEnvelopeEnabled, filterEnvelopeEnabled, modAmpEnvelopeEnabled et modFreqEnvelopeEnabled. Les enveloppes globales n'ont pas necessairement de flag natif equivalent : ne pas inventer un enable global ni detourner une valeur de profondeur.
- Reutiliser LedButton pour ON vert et C/P bleus, avec libelles accessibles. Ne pas revenir aux anciens switches ou aux activations 0/1 dans les en-tetes.
- Les enveloppes libres multipoints et FreeEnvelopeEditor existent deja. Ne pas les reimplementer. Le passage au mode libre appelle converttofree avant Pfreemode=true cote natif.
- Les previews et leurs knobs doivent rester propres au role : pas de sustain artificiel pour la frequence, pas de cellules vides ou de doublons de modules.
- CommonSynthModules et FullInstrumentEditor.kt gerent les points d'entree ; garder Frequency avant Amplitude et les controles de base avant enveloppes/LFO.

## Decisions validees — 31 octobre 2026

- **Filter, enveloppes et LFO** : meme composant d'en-tete, petit bouton **ON** vert eteint/allume,
  puis **C/P** compacts bleus. Un seul titre par module, notamment supprimer le doublon FILTER.
  N'afficher ON que si le moteur possede une activation correspondante ; pas de faux enable global.
- **Polarity** : remplacer la carte/bouton Minus par un **knob a deux valeurs affichees + et −**,
  libelle exactement **Polarity**. Reutiliser TinyKnob avec sa sensibilite corrigee pour les petites
  plages. Conserver `add/voice/N/volumeMinus` / `PVolumeminus` : 0 = +, 1 = −.
  C'est une inversion de polarite (multiplication par −1), pas un enable, un changement du niveau
  absolu ou une inversion temporelle. Aucun bouton ON ne doit la remplacer.
- **C/P Amplitude** : copier/coller uniquement son bloc de parametres directs, avec un **contrat
  de copie dedie**. Pour la voix, le bloc actuellement expose est Polarity, Vol, V.Sns et Panning.
  Les enveloppes et LFO gardent leur clipboard propre ; ne pas copier toute la voix, ses routages,
  son oscillateur, son filtre ou les autres kits/parts pour simuler un clipboard Amplitude.
  Confirmer explicitement le perimetre ADD global si ce bloc propose aussi C/P, selon ses champs
  natifs et sans lui inventer un parametre Polarity.
- Etendre au minimum le contrat type `ModuleAddress` et les primitives natives de clipboard :
  le proprietaire des parametres realise lecture/ecriture, l'UI choisit le bloc concerne.
  Ne pas reutiliser un type existant incompatible ou dupliquer la mutation dans Compose.
  Un collage incompatible est refuse ; les autres blocs restent inchanges. Respecter le contrat
  existant du clipboard partage et ses regles de compatibilite global/voix.

## Travail attendu et validation

- Choisir une presentation compacte coherente : activation disponible en permanence, preview ou ouverture de l'editeur au besoin, controles reduits quand le module est desactive.
- Ne pas effacer les valeurs en desactivant. Sur ADD principal, harmoniser le style uniquement la ou la semantique native le permet.
- Verifier chaque role global/voix/modulateur, on/off puis restauration, mode libre, copy/paste, accessibilite et absence de doublons.
- Tester Polarity +/− sur le meme chemin natif, reset et restitution de la valeur ; aucun changement du gain absolu ni de l'activation.
- Tester le round-trip du clipboard Amplitude, les types incompatibles et les scopes Part/Kit/Voice ; verifier explicitement que les enveloppes/LFO et les autres blocs ne changent pas au collage.

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
