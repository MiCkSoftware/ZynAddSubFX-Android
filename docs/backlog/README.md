# Backlog SUBsynth, PADsynth et FX

Etat actualise le 8 novembre 2026. Les numeros donnent l'ordre conseille ; les dependances indiquent les prerequis reels. Le lot 009_misc reste un suivi UX distinct.

## Decision de perimetre

ADD est termine fonctionnellement dans le perimetre Android retenu. La correction du filtre global (types par categorie et Comb HPF/LPF, y compris XML) est conservee ; son test instrumente passe sur Pixel 7 et Nokia X20 dans la [QA PAD et regressions ADD](../qa_padsynth_013_015.md).
La validation finale audio/appareil ADD+SUB+PAD+FX est volontairement reportee par Mick apres achevement fonctionnel des trois chantiers SUB, PAD et FX. Elle est suivie par 021 : ne pas la declarer faite et ne pas rouvrir ADD pour cette seule attente.

Les tests automatises et verifications ciblees de chaque lot restent necessaires. Le lot 010 est implemente localement : [rapport, executions et limites](../qa_common_modules_sub_pad.md).

Serie SUBsynth 010–012 close avec l’accord de Mick le 8 novembre 2026 apres iterations UX. [Bilan final](../qa_subsynth_011_012.md). PAD 013–015 implemente apres validation du design, avec [tests et rapport](../qa_padsynth_013_015.md). Prochaine etape : 016 (FX). Les lots transverses 020/021 restent ouverts.

## Ordre et dependances

| Lot | Prompt executable | Prerequis |
|---|---|---|
| 010 | Termine — modules communs SUB/PAD ; [rapport et limites](../qa_common_modules_sub_pad.md) | Socle ; QA finale 021 differee |
| 011 | Termine — mapping SUB complet ; [rapport et limites](../qa_subsynth_011_012.md) | 010 ; QA finale 021 differee |
| 012 | Termine — harmoniques/overtones SUB ; [rapport et limites](../qa_subsynth_011_012.md) | 011 ; design valide le 8 novembre |
| 013 | Termine — parametres de jeu PAD ; [rapport et limites](../qa_padsynth_013_015.md) | Design 014/015 valide ; persistance 020 et QA finale 021 conservees |
| 014 | Termine — profil/positions, oscillateur/resonance PAD ; [rapport et limites](../qa_padsynth_013_015.md) | Design 014/015 valide ; persistance 020 et QA finale 021 conservees |
| 015 | Termine — qualite et regeneration PAD asynchrone ; [rapport et limites](../qa_padsynth_013_015.md) | Design 014/015 valide ; persistance 020 et QA finale 021 conservees |
| 016 | [Brancher les slots FX et leur contrat natif](016_feat_fx_contrat_slots.md) | Socle FX ; reutiliser 010 pour les filtres |
| 017 | [Porter les effets Reverb, Echo, Chorus, Phaser, Alienwah et Distortion](017_feat_fx_parametres.md) | 016 |
| 018 | [Porter EQ, Dynamic Filter, Sympathetic et Reverse](018_feat_fx_avances.md) | 016, 017 |
| 019 | [Terminer les routages FX Part et Master](019_feat_fx_routages_master_part.md) | 016 a 018 |
| 020 | [Valider les contrats et la persistance SUB, PAD et FX](020_test_persistance_sub_pad_fx.md) | 010 a 019 |
| 021 | [Executer la validation finale audio/appareil ADD, SUB, PAD et FX](021_qa_finale_audio_appareil.md) | 010 a 020 termines |

## Conception des nouvelles UX avec Mick

Decision du 7 novembre 2026, actualisee apres validation du design 012 : les lots **014, 015, 016, 018 (EQ) et 019** comportent une passe de design/conception obligatoire avec Mick avant implementation de leurs nouvelles UX.

L’agent prepare une proposition concrete (schema ou maquette, interactions, etats et navigation), la discute et l’itere avec Mick, puis attend sa validation explicite avant de coder les nouveaux composants/parcours. Consigner les decisions retenues. Les travaux natifs/tests independants peuvent avancer ; les composants deja concus pour ADD restent a reutiliser.

Les designs SUB 012 et PAD 014/015 ont ete valides explicitement par Mick le 8 novembre puis implementes ; les knobs/popup existants sont reutilises pour les enums. [Decisions PAD](../padsynth_design_proposal.md). [Decisions et bilan](../qa_subsynth_011_012.md).

## Etat verifie dans le code

- SUB : mapping complet, cinq sections, 64 harmoniques/largeurs et Overtones implementes par 010/011/012. 29 tests JVM, contrat natif hote et builds ARM64/x86_64 OK ; QA finale appareil/audio differee (021).
- PAD : lots 013–015 implementes : parametres de jeu, trois enveloppes/LFO et filtre, profil/positions natifs, surfaces oscillateur/resonance partagees, qualite complete et Apply asynchrone avec annulation/installation sure. 31 tests JVM, contrat natif, builds ARM64/x86_64 et 26 tests instrumentes par appareil (Pixel 7/Nokia X20). Iteration de regroupement PAD : 32 tests JVM et parcours Compose Nokia passes ; Apply commun ; Spectrum contient les trois knobs puis les cartes graphiques Oscillator/Resonance/Overtones/Profile, avant Quality. [Rapport et limites](../qa_padsynth_013_015.md).
- Modules communs : proprietaires ADD/SUB/PAD, composants Compose, previews et clipboard XML partages depuis 010. 27 tests JVM et contrat natif hote OK ; tests instrumentes compiles, sans execution appareil.
- FX : DSP compile, mais grilles/contrat d'edition FX absents. Le code d'inspection et le send Part -> System existent. Trois FX Part par part, huit inserts Master, quatre systemes ; dix types incluant Reverse.
- ComplexEditorSheet et les messages FX sont des placeholders, pas une parite implementee.
- Le build Gradle selectionne FFTW3F_NATIVE ; docs/porting/fft_backend_strategy.md decrit un historique qui doit etre revalide avant toute decision FFT.
- Sources : native/engine_adapter/ZynAndroidEngine.cpp ; app/src/main/java/com/mick/zynaddsubfx/{FullInstrumentEditor,SynthModuleModels,SynthModuleComponents,InstrumentEditorViewModel,SynthEngine,EditorScreen}.kt ; third_party/zynaddsubfx/src/{Params,UI,Effects,Misc}.

## Regles communes et cloture

Chaque prompt contient contexte, proprietaires, symboles et criteres d'acceptation. Reutiliser le design et les primitives ADD, avec edition native live et etat transitoire reduit ; ne jamais relire les snapshots complets par mouvement.

XIZ porte un instrument et ses FX Part ; les FX/routages Master relevent de la session/XMZ. Distinguer regeneration PAD et simple sauvegarde de parametres.

A la cloture d'un lot : mettre a jour TASKS.md, TODO.md et cet index, conserver les preuves/limites dans un rapport durable, retirer les liens vers le prompt puis supprimer son fichier. Les archives existantes sont des traces historiques et peuvent contenir des formulations depassees.
