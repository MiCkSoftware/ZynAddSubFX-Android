## Priorite actuelle — SUBsynth, PADsynth et FX

Decision de Mick du 7 novembre 2026 : ADD est termine fonctionnellement ; la validation finale audio/appareil attend SUB+PAD+FX complets. Les tests automatises et QA ciblees de developpement restent propres a chaque lot. La checklist ADD est conservee dans [le rapport QA](docs/qa_addsynth_audio_xiz.md#validation-finale-restante).

La correction du filtre global ADD (categories, Comb et XML) est conservee ; son test instrumente passe sur Pixel 7 et Nokia X20 dans la [QA PAD et regressions ADD](docs/qa_padsynth_013_015.md).

[Index, contexte et dependances du backlog](docs/backlog/README.md). Ordre conseille :

**Conception UX avec Mick requise avant implementation** pour le lot 019. Les designs 014/015 puis le prototype FX 016/018 ont été validés explicitement ; réutiliser les composants ADD existants.

FX 016–018 : prototype accepté par Mick puis implémenté le 14 novembre. [Design et accord](docs/fx_design_proposal.md), [mapping](docs/fx_parameter_contract.md), [tests et limites](docs/qa_fx_016_018.md). 38 tests JVM, contrat natif hôte et builds ARM64/x86_64/AndroidTest réussis ; aucun essai appareil. Les éventuelles coupures pendant édition (`editorSkips`) restent à qualifier sur appareil.

Navigation FX révisée à la demande de Mick : trois lignes ON/OFF dans la Part ; boutons Edit (Part controls) et FX (Performance) dans Play ; deux encarts Master/System et Insert, sans combo Scope. Bypass Master natif persistant. [Détails et validation locale](docs/qa_fx_016_018.md#itération-navigation-demandée-par-mick).

Assignation Insert ajoutée après accord de Mick le 15 novembre : knob Source Off/Master/Part 1..16, commit au relâchement, propriétaire Master et persistance session ; tests natifs/JVM et builds réussis. Routages Part/System restants au lot 019.

Routing System → System ajouté avec accord de Mick : six envois vers les slots suivants, 0..127, setter Master existant et persistance XMZ. Tests natifs/JVM et builds réussis ; espacement RECP rétabli. Chaînes Part et QA audio/appareil restent ouvertes.

Performance A/B implémenté le 15 novembre à la demande de Mick : A Master/Channel/Velocity/Octave ; B Tempo/Pitch/Modulation/Sustain. File de commandes native, tempo global persistant, contrôleurs sur le canal Play. [Vérifications et limites](docs/qa_performance_ab.md).

- [x] 010 — Modules communs SUB/PAD implementes : adressage moteur, enveloppes (dont bandwidth), filtres, LFO PAD, previews et clipboard natifs ; activation des parametres par Part. 27 tests JVM et contrat natif hote OK, builds Android ARM64/x86_64 OK. [Rapport et limites](docs/qa_common_modules_sub_pad.md) ; audio/appareil differe au lot 021, persistance transverse au lot 020.
- [x] 011 — Mapping SUB complet Amp/Frequency/Bandwidth/Filter, controles ADD et enums knob/popup ; 29 tests JVM, contrat natif hote et builds ARM64/x86_64 OK. [Rapport et limites](docs/qa_subsynth_011_012.md). QA appareil/audio differee (021).
- [x] 012 — UX harmonique ADD adaptee aux 64 rangs SUB, magnitude/largeur, Overtones complet, preview natif, Clear et clipboard isole ; proposition validee par Mick le 8 novembre avec knobs/popup pour les enums. [Preuves et limites](docs/qa_subsynth_011_012.md).

Cloture de la serie SUBsynth 010–012 acceptee par Mick le 8 novembre 2026, apres les iterations UX. [Bilan final et limites](docs/qa_subsynth_011_012.md). PADsynth 013–015 est implemente et teste localement sur ADB ; suite fonctionnelle : FX ; persistance transverse 020 et campagne finale audio/appareil 021 restent ouvertes.

- [x] 013 — parametres de jeu PAD complets ; [rapport, tests ADB et limites](docs/qa_padsynth_013_015.md).
- [x] 014 — profil, positions, oscillateur et resonance PAD ; [rapport, tests ADB et limites](docs/qa_padsynth_013_015.md).
- [x] 015 — qualite et regeneration PAD asynchrone avec Apply/annulation ; [rapport, tests ADB et limites](docs/qa_padsynth_013_015.md).

Iteration UX PAD : Amp / PAD / Frequency / Filter ; Spectrum puis Quality dans PAD, sans sous-onglets ; Oscillator/Resonance/Overtones/Profile en cartes graphiques compactes apres les trois knobs Spectrum. Apply commun colore/clignotant a droite de l’entete PAD ; fonds harmonises. 32 tests JVM et parcours Compose Nokia passes ; Pixel conserve pendant les essais de Mick. [Details](docs/qa_padsynth_013_015.md#regroupement-des-parametres-pad--8-novembre-2026).
- [x] 016 — Contrat et éditeur de slot communs Part/Insert/System, Off, type/preset/bypass Part/reset/clipboard ; [rapport et limites](docs/qa_fx_016_018.md).
- [x] 017 — Six effets courants, contrôles/presets natifs, modes et temps/tempo ; [mapping](docs/fx_parameter_contract.md) et [vérifications](docs/qa_fx_016_018.md).
- [x] 018 — EQ huit bandes/courbe native, Dynamic Filter/Formant, Sympathetic/Reverse complets dans le prototype ; [rapport](docs/qa_fx_016_018.md). Ajustements sur pièce et écoute comparative 021 conservés.
- [ ] [019 — Terminer les routages FX Part et Master](docs/backlog/019_feat_fx_routages_master_part.md)
- [ ] [020 — Valider les contrats et la persistance SUB, PAD et FX](docs/backlog/020_test_persistance_sub_pad_fx.md)
- [ ] [021 — Executer la validation finale audio/appareil ADD, SUB, PAD et FX](docs/backlog/021_qa_finale_audio_appareil.md)

A la fin de chaque implementation, mettre a jour TODO.md, TASKS.md et l'index, retirer les liens vers le prompt puis supprimer son fichier. Conserver les validations differees dans un rapport durable.

## Bug fix
    - EQ FX Curve sometimes not refreshing when adjusting frequency


## Small issues
 - what is the correct naming of Instru, Instru Set, Babnk (XIZ, XMZ)
 - Cannot rate instru when opening an instru set (clarify what is instru and what is bank and warn before overriding)
 - How to save an instru set
 - sound glotch when playing 3-4+ notes

## Notes et backlog existants
- Les activations natives utilisent ON ; les modules globaux sans flag ne proposent pas de faux enable. QA appareil reportee a la validation finale commune (021).
- SUBsynth, PADsynth et FX : backlog executable detaille ci-dessous.
- oscillator preview padding issue

mini keyboard: octave selector, 2 rows option


MISC-UX
- Enveloppes et LFO de voix masquent leurs controles desactives ; filtre OFF masque aussi son enveloppe et son LFO, sans modifier leurs flags. Restauration et ergonomie sur appareil reportees a la validation finale commune (021).
