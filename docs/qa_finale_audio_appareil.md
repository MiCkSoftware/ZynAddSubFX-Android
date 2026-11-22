# QA finale 021 — Audio et appareil ADD, SUB, PAD et FX

Campagne ouverte le 22 novembre 2026 après la clôture des lots 010 à 020.

## Environnement vérifié

- Dépôt `kit/padsub`, révision `8dff346` avec les correctifs locaux du lot. APK debug `alpha.29-debug`, version 29.
- Nokia X20 sur USB, Android 14 (API 34), build `00WW_4_250`, écran 1080 × 2400 à 400 dpi. APK debug et APK de tests installés avec l’accord de Mick.
- Empreintes SHA-256 du build utilisé pour les 33 tests initiaux, comparées sur le Nokia et en local. App `d6378d5059940da3223c5d1b87efe262469d170e6578df09f8695546403b6fe1`, tests `a8ae3e24ee0096a049db7dd111ad42cdca3914314055b09d99fc702f8b215157`.
- Gradle a compilé les APK debug et AndroidTest avec `FFTW3F_NATIVE`. Vérification des deux `libzynbridge.so` dans l’APK (ARM64 et x86_64) par la chaîne `fftw-3.3.10` et le symbole défini `fftwf_plan_dft_r2c_1d`. Sources FFTW `93ed4c78` et mxml `e6824d8`.
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --offline` a réussi. 38 tests JVM.
- `python3 native/tests/run_common_modules.py --build-dir /tmp/zyn-fx-host` a réussi. Contrats ADD/SUB/PAD, 10 types FX dans chacun des trois périmètres, courbes EQ, routages, XML et contrôleurs de jeu. Le contrat hôte utilise `LIGHT_SHIM`.

## Corpus pour les comparaisons sur appareil

Les fichiers XIZ sont dans la banque embarquée `Cormi_Noise`. Les moteurs indiqués ont été vérifiés dans le XML de l’APK.

| XIZ | Moteurs actifs | Cas |
|---|---|---|
| `0026-beepA.xiz` | ADD seul | Référence ADD et édition de voix |
| `0018-Strano_02.xiz` | SUB seul | Référence SUB |
| `0027-beepB.xiz` | PAD seul | Référence PAD et régénération |
| `0021-Asteroide.xiz` | PAD seul | Référence PAD avec pic détecté dans Play |
| `0002-Sparo_01.xiz` | ADD + SUB | Comparaison de deux moteurs combinés |
| `0013-Passa_asteroide.xiz` | ADD + SUB + PAD | Comparaison mixte et charge |

Utiliser d’abord la Part 1, canal 1, preset intact, effets et niveau du preset, puis exporter en XIZ et recharger dans la Part 2. Jouer les mêmes notes MIDI 60, 64 et 67 à vélocité 100, d’abord séparées puis en accord tenu cinq secondes. Comparer à niveau de sortie fixe. Pour la session XMZ, mettre les deux Parts sur le même canal. Sur la Part 1, activer le mode Kit et l’item 3, avec la voie ADD par défaut séparée du kit de base par la destination FX. Sauvegarder la session de test puis comparer les Parts isolées et empilées après restauration. Les sessions de référence personnelles restent disponibles via des fichiers distincts.

Pour les FX, partir de la référence ADD. Essayer successivement Reverb, Echo, Chorus, Phaser, Alienwah, Distortion, EQ, Dynamic Filter, Sympathetic et Reverse, avec le premier preset disponible. Utiliser Part FX 1 et 2, Insert 1 et System 1 et 2. Comparer le flux vers l’effet suivant, Part Out et la séparation wet/dry, puis l’Insert sur Part 1 et Master, l’envoi Part 1 → System 1 et System 1 → System 2, avec niveau d’envoi 64 sur 127. Pour l’EQ, comparer la courbe et l’écoute au chargement du preset, après modification d’une bande, puis après Reset. Noter les valeurs de toute modification nécessaire pour rendre l’effet perceptible.

## Observations sur le Nokia X20

- **Persistance et gestes instrumentés.** 33/33 tests réussis sur les APK finaux en 143,4 s. La suite couvre les paramètres XIZ de plusieurs moteurs, kits et Parts, les données XMZ des FX Master, les collages valides et refusés, les parcours PAD et le glissé sur les deux claviers. Premier passage à 32/33 à cause du test Reverse.
- **Choix FX Reverse.** Le test échouait aussi en isolation. Dans le menu Type de System 1, le clic instrumenté atteignait mal l’option en bas du menu sur le Nokia, et Reverb restait actif. Le test fait désormais défiler jusqu’à Reverse et vérifie que l’option est visible avant le clic. Le test isolé a réussi avec ce changement, APK de l’application identique.
- **Délai du test XMZ.** Le deuxième passage a validé Reverse et a rencontré un `TimeoutException` dans le test de restauration d’une Part désactivée. L’écriture a réussi en 10 106 ms. Le test attend désormais jusqu’à 30 s pour l’écriture en arrière-plan, contre 10 s auparavant. Les assertions portent toujours sur la fidélité après remplacement du moteur. La durée d’écriture dans l’application reste à caractériser sur appareil.
- **Reprise audio après Home.** En partant d’un lancement à froid, jouer dans Play puis passer à Home et rouvrir l’application pouvait laisser AAudio arrêté. Le démarrage automatique se servait de son propre marqueur comme clé Compose. Il pouvait annuler sa coroutine avant que le résultat de démarrage soit publié. Le correctif enregistre le résultat avant de marquer la tentative, libère les notes/sustain lors de la pause et conserve la reprise même si AAudio est momentanément fermé pendant le chargement d’un preset. Après correction, deux retours Home ont fermé puis rouvert AAudio dans le même processus, sans action sur RESTART. Le preset `beepB` et la Part 1 sont restés sélectionnés au passage en paysage. Les diagnostics ont enregistré la réouverture du flux dans les deux sens de rotation. Le réglage initial du Nokia, portrait verrouillé, a été rétabli.
- **Contre-vérification sur l’APK final.** La session XMZ a restauré la Part 1 et `beepB` après redémarrage. Home a fait passer `aaudioRunning` de 1 à 0, puis à 1 en revenant dans le même processus, avec zéro xrun relevé. Le premier C4 après la reprise a produit le pic indiqué dans le tableau.
- **Stabilité pendant le parcours Play.** Aucun crash ni ANR relevé. À 48 kHz, app 192 trames, AAudio 96 trames par burst et tampon de 384 trames. `qDrops`, `aaudioErrCb`, `aaudioRecovered` et `aaudioStartFails` sont restés à zéro. Un premier cycle après démarrage à froid a relevé 52 xruns et 6 `editorSkips` à la pause, après note tenue, glissé, All Off et Panic. Un autre cycle a mesuré zéro xrun. À isoler sous charge avec un scénario chronométré. L’écriture du checkpoint XMZ après Home a pris de 10,5 à 11,3 s pour les deux cycles corrigés.
- **Police à 150 % dans Play.** En portrait, le nom `beepB` est rogné par le bas du bandeau de banque. Les rangées Part, Performance et le clavier étaient lisibles dans cette capture. Défaut reporté. Le Nokia est revenu à 100 %.

Première note dans Play, clavier hexagonal, Part 1, vélocité 110, Master 76, sortie courante du Nokia. Une mesure par entrée sauf SUB, mesuré avant et après correction. Le seuil du diagnostic est 0,0015. Les valeurs sont des mesures ponctuelles.

| Preset | Temps après appui | Pic |
|---|---:|---:|
| `0026-beepA` (ADD) | 76 ms | 0,002379 |
| `0018-Strano_02` (SUB) | 58 et 97 ms | 0,002396 et 0,006874 |
| `0021-Asteroide` (PAD) | 68 ms | 0,442744 |
| `0013-Passa_asteroide` (ADD + SUB + PAD) | 63 ms | 0,072422 |
| `0027-beepB` (PAD) | 94 ms après restauration XMZ. Après chargement XIZ, seuil non atteint dans les essais, dont un nouveau C4 sur l’APK final. | 0,034559 après restauration XMZ |

Mick confirme que `beepB` joue correctement sur son appareil. Aucun défaut audible confirmé. La comparaison ciblée, si le silence réapparaît, consiste à charger `beepA` puis `beepB` via Next et à tenir C4 (MIDI 60) sur la Part 1. Comparer au même C4 après sauvegarde de la session et relance. Le diagnostic mesure le franchissement du seuil.

Génération PAD instrumentée, un échantillon par qualité (une octave, réglage de 0,5 échantillon par octave). Le test vérifie les pics de la banque, puis détecte un pic PCM du PAD seul sur le flux AAudio, tonalité de secours désactivée. Le rechargement XIZ de huit échantillons de 1 048 576 trames a duré de 1 435 à 1 448 ms pour 33 554 592 octets. La suite vérifie aussi l’annulation d’une génération obsolète et la préservation de la banque en cas de dépassement mémoire. Les valeurs ci-dessous couvrent le premier et le dernier passage.

| Trames par échantillon | Requête | Génération |
|---:|---:|---:|
| 16 384 | 4 à 5 ms | 21 à 23 ms |
| 65 536 | 4 à 5 ms | 36 à 43 ms |
| 262 144 | 4 ms | 82 à 83 ms |
| 1 048 576 | 11 à 13 ms | 226 à 240 ms |

## Polyphonie sous charge sur Nokia X20

Scénario instrumenté `AudioPolyphonyInstrumentedTest`. Les Parts 1 et 2 chargent `0013-Passa_asteroide.xiz` (ADD + SUB + PAD), canal 1, niveau 64/127 et limite 16 notes par Part. Master 42 %, vélocité 100, tonalité de secours coupée. Trois répétitions par cas sur le même flux ouvert, avec une mesure à vide au début et à la fin. Les accords de 1, 4, 8 et 16 notes sont tenus 2 s. Les cas EQ et PAD jouent 8 notes sur deux Parts. Le cas EQ joue 40 modifications de gain via le modèle de l’éditeur, espacées de 20 ms, suivies d’un commit et de 2 s de maintien. Le cas PAD déclenche Apply pendant l’accord, avec un EQ Part déjà en place, puis vérifie l’installation de la nouvelle banque après le relâchement.

AAudio à 48 kHz, callback demandé de 192 trames, burst réel de 96 et tampon de 384. Comparaison entre l’APK Gradle debug standard (aucun `-O` dans les commandes C/C++) et le même APK debug pour ARM64 recompilé avec `-O2 -g -DNDEBUG` (options natives du build Release/RelWithDebInfo vérifiées dans les commandes CMake). Même test et même version d’Android. La variante optimisée a servi aux mesures locales. Le nombre d’xruns ne constitue pas un seuil de réussite du test, dont les assertions vérifient le rendu, la continuité du flux et la génération PAD.

| Charge | Xruns debug standard | Xruns natif optimisé |
|---|---:|---:|
| À vide et 1 note, 1 ou 2 Parts | 0 | 0 |
| 4 notes, 1 Part | 6 à 18 | 0 |
| 8 notes, 1 Part | 127 à 394 | 0 |
| 16 notes, 1 Part | 991 à 994 | 13 à 17 |
| 4 notes, 2 Parts | 394 à 439 | 0 |
| 8 notes, 2 Parts | 991 à 995 | 12 à 23 |
| 16 notes, 2 Parts | 989 à 995 | 132 à 202 |
| 8 notes, 2 Parts, édition EQ | 457 à 468 | 7 à 15 |
| 8 notes, 2 Parts, régénération PAD | 79 à 995 | 0 |

Intervalles min–max des trois répétitions du passage standard et du passage optimisé intégralement consigné. Un premier passage optimisé a également réussi et confirmé le même seuil sur les accords simples, avec 10 à 17 xruns à 8 notes sur deux Parts. Les passages ont laissé `qDrops`, erreurs AAudio et redémarrages à zéro. Le scénario de polyphonie seule a laissé `editorSkips` à zéro avec les deux builds. La reproduction des xruns sans interaction d’édition isole un manque de marge de rendu sur ce Nokia pour ce preset mixte, particulièrement en debug. Elle ne permet pas d’attribuer les 52 xruns du premier parcours Play à une opération précise.

L’édition EQ ajoute 114 à 118 `editorSkips` en debug standard et 3 à 5 dans le passage optimisé consigné. Pendant la régénération PAD, le passage optimisé laisse 5 à 8 `editorSkips` malgré zéro xrun AAudio. Le moteur produit un bloc silencieux quand son verrou empêche le rendu, ce que compte `editorSkips` séparément. La génération de huit échantillons de 1 048 576 trames a duré 1 559 à 1 580 ms en debug standard et 486 à 506 ms avec les binaires optimisés.

Pour exécuter ce relevé avec les APK debug installés, lancer `adb shell am instrument -w -e class com.mick.zynaddsubfx.AudioPolyphonyInstrumentedTest com.mick.zynaddsubfx.debug.test/androidx.test.runner.AndroidJUnitRunner` et conserver les lignes `POLYPHONY` du tag `System.out`. La comparaison ARM64 a passé les valeurs `-DCMAKE_C_FLAGS_DEBUG=-O2 -g -DNDEBUG` et `-DCMAKE_CXX_FLAGS_DEBUG=-O2 -g -DNDEBUG` via un script Gradle d’initialisation temporaire.

### Taille du bloc interne et du tampon AAudio

Comparaison sur le Nokia X20, moteur ARM64 `-O2`, même scénario et trois accords de 2 s par cas. Le rappel AAudio reste à 192 trames avec un burst matériel de 96 à 48 kHz. Les arguments AndroidJUnitRunner `-e synthBlockFrames` et `-e outputBufferFrames` permettent de changer un réglage à la fois.

Avec un tampon AAudio constant de 384 trames, passer le bloc interne de 192 à 256 trames donne 5, 20 et 13 xruns au lieu de 17, 17 et 13 à huit notes sur deux Parts. À 16 notes sur deux Parts, les plages sont 108–148 contre 132–146. Le passage à 256 ne montre pas de gain régulier. Les deux passes instrumentées ont réussi.

Bloc interne fixé à 192 trames. Les diagnostics ont confirmé les tailles de tampon AAudio appliquées.

| Tampon de sortie | Durée du tampon à 48 kHz | Xruns, 8 notes sur 2 Parts | Xruns, 16 notes sur 2 Parts | Xruns, 8 notes sur 2 Parts avec édition EQ |
|---|---:|---:|---:|---:|
| 384 trames | 8 ms | 13–17 | 132–146 | 2–10 |
| 576 trames | 12 ms | 0–5 | 72–123 | 0–6 |
| 768 trames | 16 ms | 0–1 | 64–121 | 0 |

Le tampon de sortie plus grand réduit les xruns sans résoudre la surcharge à 16 notes sur deux Parts. Il reste 1 à 3 `editorSkips` pendant l’édition EQ et 4 à 9 durant Apply PAD à 768 trames. Les durées du tableau représentent la capacité du tampon en temps audio. La latence de jeu n’a pas été mesurée. Les passes ont été effectuées dans l’ordre du tableau et chaque intervalle couvre trois répétitions sur un seul appareil et un seul preset.

Le build release demande désormais un tampon de sortie cible de **16 ms** (768 trames à 48 kHz). Il conserve la cible habituelle de deux bursts sur les appareils où cette valeur est supérieure. La taille appliquée dépend du flux AAudio et apparaît dans `appliedBuf` et `aaudioBuf`. La taille du bloc de synthèse et celle du rappel restent déterminées par la configuration audio de l’appareil. Le build debug conserve la cible habituelle.

## Campagne manuelle restante

| Cas | Attendu | Observé |
|---|---|---|
| XIZ et XMZ | Le timbre, les niveaux, l’isolation des Parts/kits et les retours FX retrouvent le comportement avant sauvegarde. Attendre la fin de la régénération PAD avant de comparer. | À écouter |
| ADD | Tous les modes de modulation et routages entre voix sont vérifiés avec la [checklist ADD](qa_addsynth_audio_xiz.md#validation-finale-restante). Le partage de sources et le presse-papiers conservent leurs destinations. Auto clear OscilGen est hors du périmètre Android retenu. | À exécuter |
| Notes et FX | Alterner les presets avec des notes tenues, puis essayer All Off et Panic. All Off relâche les notes et le sustain. Panic réinitialise aussi les voix et retours FX. | Presets enchaînés, commandes déclenchées. Absence de notes fantômes et retours FX à écouter. |
| Charge | Comparer des accords sur une puis deux Parts, avec et sans édition FX et régénération PAD. Relever les compteurs et dater les coupures perçues. | Mesures instrumentées effectuées jusqu’à 16 notes. Écoute et vérification sur d’autres presets/appareils à faire. |
| Gestes | Tester taps, gestes rapides, longs et annulés, ON/C/P, knobs enum, liste Voice, claviers piano et hexagonal. Vérifier la restauration après désactivation et les collages valides et refusés. | Parcours tactile complet à exécuter |
| Interface et accessibilité | Vérifier les cibles et le défilement en largeur réduite et paysage, puis les grandes polices, les noms et états annoncés par TalkBack, et le retour système. | Play à 150 % vérifié, titre rogné. Paysage et retour système contrôlés ponctuellement. Matrice complète et TalkBack à exécuter. |
| Lifecycle | Un aller-retour Home, un verrouillage et une rotation conservent la session et libèrent les notes. Le son redémarre quand Play repasse au premier plan. Relever les erreurs, ANR et arrêts pour cette fenêtre. | Home et rotation observés. Verrouillage, notes maintenues pendant la transition et restauration intégrale à exécuter. |

## Relevés

Comparer `qDrops`, `editorSkips`, `aaudioXruns`, `aaudioErrCb`, `aaudioRecovered` et `aaudioStartFails` dans les lignes `diag:` de `ZynAddSubFX`. Les lignes sont émises notamment au chargement, à la pause et à la reprise. La lecture des diagnostics actualise le compteur d’xruns lorsque le verrou AAudio est disponible. Le compteur reflète le flux courant et peut repartir de zéro à la réouverture. Pour la première note, utiliser `first_note_auto` après un chargement depuis Banks et relever le pic associé. Cette mesure s’arrête au pic détecté dans le moteur, par sondage, et ne mesure pas la latence acoustique.
