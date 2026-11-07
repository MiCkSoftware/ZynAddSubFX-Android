# 010 — Etendre les modules communs a SUB et PAD

## Mission et dependances

Premier socle pour 011 a 015 : reutiliser enveloppes, filtres, LFO, previews et clipboard sans dupliquer l'implementation ADD. Ne pas refaire les ecrans ADD valides.

## Contexte verifie

- app/src/main/java/com/mick/zynaddsubfx/SynthModuleModels.kt : ModuleAddress.Envelope/Lfo/Filter/Oscillator/Resonance et les modeles resolvent actuellement les chemins add/... ; les kind/role sont deja un contrat JNI.
- SynthModuleComponents.kt : CommonSynthModules, EnvelopeUI, FreeEnvelopeEditor, LFOUI, FilterEditor et FormantFilterEditor existent avec ON/C/P, masquage OFF et gestes legers.
- FullInstrumentEditor.kt reserve CommonSynthModules au module ADD. InstrumentEditorViewModel fournit dragParameter, parameterValue et finishParameterDrag avec etat transitoire reduit.
- native/engine_adapter/ZynAndroidEngine.cpp : copyModule, pasteModule et modulePreview recuperent kit.adpars puis GlobalPar/VoicePar ; les helpers de lecture/ecriture d'enveloppe/filtre sont dans parameterSnapshot et setParameter.
- SUBnoteParameters possede AmpEnvelope, FreqEnvelope, BandWidthEnvelope, GlobalFilter et GlobalFilterEnvelope ; pas de LFO natif a inventer. PADnoteParameters possede les trois enveloppes/LFO, GlobalFilter, oscilgen et resonance.
- Le filtre ADD vient d'etre corrige : filterTypeControl factorise types/bornes selon categorie ; combHpf/combLpf sont exposes et FilterParams serialize hpf/lpf. Reutiliser ce contrat.

## Travail et criteres d'acceptation

- Ajouter le plus petit adressage moteur/module necessaire de Kotlin a JNI/native, en conservant le comportement et les codes existants ADD. Le proprietaire natif resout le module ; pas de kit ADD temporaire pour copier/previewer un SUB/PAD.
- Completer roles et metadonnees (notamment enveloppe de bandwidth SUB), flags natifs, chemins et previews. Employer le routage structure, pas les labels UI.
- Reutiliser le clipboard natif type partage et ses regles actuelles entre roles ; ajouter seulement les contrats manquants. Ne pas copier uniquement les knobs visibles.
- Garder libres multipoints, Formant/voyelles/sequence et restauration OFF/ON. Aucun faux ON pour un module sans flag natif.
- Tester l'isolation moteur/part/kit et les incompatibilites, les roles absents et une regression ADD. Les lots suivants apportent les surfaces specifiques.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
