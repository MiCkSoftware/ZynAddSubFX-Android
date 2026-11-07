# 021 — Executer la validation finale audio/appareil ADD, SUB, PAD et FX

## Condition de demarrage

Ce lot est volontairement differe par Mick le 7 novembre 2026. Ne le lancer qu'apres achevement fonctionnel de SUBsynth, PADsynth et FX (lots 010 a 020). ADD n'est pas a rouvrir pour cette seule attente.

## Contexte et references

- docs/qa_addsynth_audio_xiz.md conserve la checklist ADD : ecoute comparative avant/apres export, tous modes de modulation/routages, partage de sources, notes fantomes, clipboard croise, gestes/annulation et navigation.
- Les features ADD Voice/Resonance/OscilGen ont deja des validations utilisateur ciblees ; les tests JVM/JNI et la QA visuelle courante ne valent pas une campagne finale sur appareil.
- Rapports 020 et historiques ADD permettent de construire la matrice sans refaire les audits de contrat. Les notes dans docs/backlog/archive sont historiques, pas de nouveaux prompts d'implementation.
- Backend FFT : verifier la config effective (Gradle demande actuellement FFTW3F_NATIVE) et les versions/contenus embarques, pas une ancienne note sur le shim.

## Travail et criteres d'acceptation

- Choisir un corpus reproductible ADD/SUB/PAD seuls et combines, plusieurs parts/kits, tous les effets et routages Part/Insert/System ; appareils, versions, presets et reglages documentes.
- Ecoute avant/apres export/reload et session, rendu PAD apres regeneration, enchainements de presets, notes tenues, All Notes Off/Panic et retours FX.
- Gestes rapides/longs/annules, on/off et restauration, clipboard entre moteurs/roles lorsque permis, changements d'orientation et navigation systeme.
- Mesurer premiere note, charges PAD et polyphonie, xruns/recoveries, absence d'ANR/crash et perte d'etat au lifecycle. Reutiliser les diagnostics moteur existants.
- QA visuelle/tactile/accessibilite : petites largeurs, paysage, grandes polices, TalkBack et cibles ; couvrir aussi ON/C/P, knobs enum et liste Voice dont les controles ont ete harmonises.
- Distinguer les vrais ecarts fonctionnels des choix Android (par exemple Auto clear OscilGen volontairement hors perimetre).
- Publier un rapport local durable avec observe/attendu et anomalies reproductibles ; corriger ou reporter explicitement les problemes. Cocher uniquement les cas reellement executes. Aucun deploiement/publication dans ce lot.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
