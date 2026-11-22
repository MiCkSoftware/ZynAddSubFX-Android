# QA 020 — Contrats et persistance ADD, SUB, PAD et FX

## Couverture du 21 novembre 2026

- Chargement XIZ après édition du même instrument. Les fichiers `presets/banks/Cormi_Noise/0018-Strano_02.xiz` (SUB), `0027-beepB.xiz` (PAD) et `0013-Passa_asteroide.xiz` (ADD, SUB et PAD) sont lus depuis les assets Android. Le test compare les paramètres et les états de moteurs à un chargement de référence. Si un moteur absent du preset reste alloué, son volume doit revenir à la valeur par défaut.
- Aller-retour XIZ sur un instrument de test à deux kits. L’export conserve le routage du kit, l’EQ Part avec sa courbe éditée et les paramètres des moteurs SUB/PAD désactivés dans un kit actif. Le PAD du premier kit revient avec une génération terminée. Pour conserver les branches des moteurs désactivés, `Part::saveXML` active `SaveFullXml` en plus de `minimal = false`.
- Presse-papiers typé. Une enveloppe SUB est copiée vers le PAD d’un autre kit et d’une autre Part. Le filtre refuse ce contenu et les autres champs et kits gardent leurs valeurs. Les tests ADD et PAD couvrent aussi leurs propres destinations et modules.
- Capture XMZ suivie d’une écriture en tâche de fond. La restauration retrouve l’Insert et sa source, l’envoi System → System, l’envoi Part → System et les paramètres SUB/PAD désactivés. Les modifications effectuées après la capture restent hors du fichier capturé.
- La suite PAD vérifie la génération FFTW, le rendu PCM et la restauration de session à plusieurs qualités. Le contrat C++ hôte complète les assertions avec l’XML et les routes FX Part, Insert et System. Il vérifie aussi les courbes des presets EQ et le retour à la courbe du preset avec R.

## Exécution

- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline` — compilation et 38 tests JVM réussis.
- `python3 native/tests/run_common_modules.py --build-dir /tmp/zyn-fx-host` — contrats natifs ADD, SUB, PAD, FX et Performance réussis.
- `AndroidJUnitRunner` sur émulateur Pixel 8a, API 37. 29 tests réussis dans `PersistenceInstrumentedTest`, `PadSynthParametersInstrumentedTest`, `PadSynthGenerationInstrumentedTest`, `PadSynthStructureInstrumentedTest`, `AddSynthRoundTripInstrumentedTest` et `CommonModulesInstrumentedTest`. Le test Compose `FxEditorInstrumentedTest` a également réussi sur le même émulateur.

La campagne [audio et appareil physique du lot 021](backlog/021_qa_finale_audio_appareil.md) reste à exécuter. Elle inclut l’écoute des courbes EQ, les mesures de coupures et les parcours tactiles.
