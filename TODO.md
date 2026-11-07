## Priorite actuelle — SUBsynth, PADsynth et FX

Decision de Mick du 7 novembre 2026 : ADD est termine fonctionnellement ; la validation finale audio/appareil attend SUB+PAD+FX complets. Les tests automatises et QA ciblees de developpement restent propres a chaque lot. La checklist ADD est conservee dans [le rapport QA](docs/qa_addsynth_audio_xiz.md#validation-finale-restante).

La correction locale du filtre global ADD est presente (categories, Comb et XML) ; son nouveau test est ajoute, sans execution attestee dans le rapport disponible. Preserver ces modifications.

[Index, contexte et dependances du backlog](docs/backlog/README.md). Ordre conseille :

**Conception UX avec Mick requise avant implementation** pour les nouveaux composants des lots 014, 015, 016, 018 (EQ) et 019 : proposition concrete, echange et validation explicite. Les prompts precisent le perimetre ; reutiliser le design ADD existant.

- [x] 010 — Modules communs SUB/PAD implementes : adressage moteur, enveloppes (dont bandwidth), filtres, LFO PAD, previews et clipboard natifs ; activation des parametres par Part. 27 tests JVM et contrat natif hote OK, builds Android ARM64/x86_64 OK. [Rapport et limites](docs/qa_common_modules_sub_pad.md) ; audio/appareil differe au lot 021, persistance transverse au lot 020.
- [x] 011 — Mapping SUB complet Amp/Frequency/Bandwidth/Filter, controles ADD et enums knob/popup ; 29 tests JVM, contrat natif hote et builds ARM64/x86_64 OK. [Rapport et limites](docs/qa_subsynth_011_012.md). QA appareil/audio differee (021).
- [x] 012 — UX harmonique ADD adaptee aux 64 rangs SUB, magnitude/largeur, Overtones complet, preview natif, Clear et clipboard isole ; proposition validee par Mick le 8 novembre avec knobs/popup pour les enums. [Preuves et limites](docs/qa_subsynth_011_012.md).

Cloture de la serie SUBsynth 010–012 acceptee par Mick le 8 novembre 2026, apres les iterations UX. [Bilan final et limites](docs/qa_subsynth_011_012.md). Suite fonctionnelle : PADsynth (013–015), puis FX ; persistance transverse 020 et campagne finale audio/appareil 021 restent ouvertes.

- [ ] [013 — Completer les parametres de jeu PADsynth](docs/backlog/013_feat_padsynth_parametres.md)
- [ ] [014 — Porter profil, spectre, oscillateur et resonance PAD](docs/backlog/014_feat_padsynth_spectre_profil.md)
- [ ] [015 — Terminer qualite et regeneration des samples PAD](docs/backlog/015_feat_padsynth_generation.md)
- [ ] [016 — Brancher les slots FX et leur contrat natif](docs/backlog/016_feat_fx_contrat_slots.md)
- [ ] [017 — Porter les effets Reverb, Echo, Chorus, Phaser, Alienwah et Distortion](docs/backlog/017_feat_fx_parametres.md)
- [ ] [018 — Porter EQ, Dynamic Filter, Sympathetic et Reverse](docs/backlog/018_feat_fx_avances.md)
- [ ] [019 — Terminer les routages FX Part et Master](docs/backlog/019_feat_fx_routages_master_part.md)
- [ ] [020 — Valider les contrats et la persistance SUB, PAD et FX](docs/backlog/020_test_persistance_sub_pad_fx.md)
- [ ] [021 — Executer la validation finale audio/appareil ADD, SUB, PAD et FX](docs/backlog/021_qa_finale_audio_appareil.md)

A la fin de chaque implementation, mettre a jour TODO.md, TASKS.md et l'index, retirer les liens vers le prompt puis supprimer son fichier. Conserver les validations differees dans un rapport durable.

## Notes et backlog existants
- Les activations natives utilisent ON ; les modules globaux sans flag ne proposent pas de faux enable. QA appareil reportee a la validation finale commune (021).
- SUBsynth, PADsynth et FX : backlog executable detaille ci-dessous.


mini keyboard: octave selector, 2 rows option


MISC-UX
- Enveloppes et LFO de voix masquent leurs controles desactives ; filtre OFF masque aussi son enveloppe et son LFO, sans modifier leurs flags. Restauration et ergonomie sur appareil reportees a la validation finale commune (021).
