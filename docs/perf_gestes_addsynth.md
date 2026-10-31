# Gestes continus AddSynth — implementation du 31 octobre 2026

## Contrat et proprietaires

Le moteur natif reste la source des valeurs et recoit les ecritures pendant le geste.
`InstrumentEditorViewModel` garde uniquement les valeurs touchees dans une map Compose transitoire.
Un index des valeurs du snapshot est construit a la lecture complete, jamais par mouvement.
La comparaison avant ecriture tient compte du transitoire et du dernier snapshot, meme si un callback
possede encore un ancien descripteur. Les valeurs entieres identiques apres arrondi ne sont pas reecrites.
Le retour a la valeur initiale reste une vraie ecriture.

`VoiceMatrixKnob`, Magnitude et Phase utilisent `dragParameter` et `finishParameterDrag`.
`TinyKnob` affiche sa position transitoire et formate cette valeur immediatement, sans attendre un snapshot.
Ses callbacks restent a jour. Fin, annulation et arret de la coroutine de pointeur (navigation,
changement de plage ou retrait du controle) reconcilient les ecritures acceptees.
L'annulation conserve la derniere valeur envoyee au son, conformement au contrat precedent du knob.
Les terminaisons successives sans nouvelle ecriture ne relisent pas le snapshot.

`HarmonicOverview` conserve horizontal = selection et vertical = magnitude ; il lit les valeurs
transitoires des harmoniques deja touchees, y compris lorsqu'on revient sur une harmonique pendant
le meme geste. Les cartes et le dessin consomment cet etat sans reconstruire la liste du snapshot.
Le changement d'oscillateur renouvelle le traitement des pointeurs ; fin/annulation reconcilient aussi
le dessin. Les listes d'harmoniques et de phases sont memorisees par snapshot et groupe.
Reset et edition numerique continuent a utiliser les mutations immediates existantes.
La Resonance garde son contrat de dessin local puis commit de courbe.

La revision n'avance qu'a la reconciliation ou lors d'un changement structurel. Le preview natif
de l'oscillateur reste memorise par revision/adresse. Le preview LFO partage suit maintenant aussi
ce contrat. Les changements de voix/onglet/moteur terminent une edition en attente ; ouvrir un
part/kit relit sa source native et renouvelle la revision, sans conserver de transitoire du contexte precedent.
L'interface `InstrumentEditorEngine` expose seulement les operations deja consommees par l'editeur,
afin de compter les appels en tests sans JNI.

## Verification executee

Commande : `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline --no-daemon`.
Compilation Kotlin et 13 tests JVM passes, dont 6 tests de regression des gestes.
`git diff --check` passe.

Comparaison executee avec un moteur de test de 4 096 parametres et 1 000 valeurs successives :

| Appels hors ouverture initiale | Ancien chemin voix/harmonique (`write`) | Nouveau geste + terminaison |
| --- | ---: | ---: |
| Ecritures moteur | 1 000 | 1 000 |
| Snapshots pendant le geste | 1 000 | 0 |
| Snapshots a la terminaison | sans callback dedie | 1 |

L'ancien ViewModel de HEAD a ete compile temporairement contre le meme moteur de test pour
cette comparaison, puis retire. Son chemin `refresh=false` reconstruisait aussi toute la liste
avec `map` par ecriture (constat source) ; le nouveau conserve la meme instance de snapshot
pendant les 1 000 mouvements (assertion de test). Aucun preview n'est demande par le ViewModel
pendant ce test ; cela ne constitue pas une mesure des recompositions Compose sur appareil.
Les previews UI sont controles par inspection des cles `remember`.

Tests durables : retour a l'origine, suppression des doublons arrondis, valeurs de plusieurs
harmoniques, changement voix/kit, reset/edition avec ancien descripteur, echec d'ecriture et
terminaisons repetees. Les callbacks Compose et l'annulation reelle du pointeur ne sont pas executes
par les tests JVM.

## Limites et QA restante

`adb devices` ne retourne aucun appareil ni emulateur. Aucune installation effectuee.
Aucun changement C++ : le trajet JNI `nativeSetParameter` appelle seulement `setParameter`,
sans snapshot ni inspection globale. Les ecritures d'oscillateur gardent `o.prepare()` pour
appliquer les harmoniques au son : `needPrepare()` ne detecte pas seul les changements des tableaux
`Phmag`/`Phphase`. Ce calcul spectral par valeur entiere modifiee reste un cout natif a profiler ;
les mesures JVM ne prouvent pas la fluidite ni la parite audible.

QA a effectuer sur appareil avec oscillateur Voice et Modulator :

- Gestes longs/rapides sur les quatre knobs des voix, Magnitude, Phase et le graphe harmonique.
- Aller-retour horizontal entre plusieurs harmoniques apres modifications verticales ; cartes et barres coherentes.
- Fin normale, annulation, navigation pendant le geste, changement de voix/kit/oscillateur et rotation ; derniere valeur acceptee conservee.
- Son pendant le geste et apres, valeur native finale, preview rafraichi en fin de geste, reset et edition numerique.
- Trace JNI/Compose et profil du cout de `prepare()` : absence de snapshots, previews et inspections globales par mouvement.
