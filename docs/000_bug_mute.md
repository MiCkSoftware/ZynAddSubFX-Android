# 000 — Corriger le mute entre parts et kits

## Mission et priorite

Reproduire puis corriger le signalement TODO.md : « Dual String Ct1 have Pad and Add on same kit !? and mute is synched- muted part mutes all pars bug ». Priorite maximale car la jouabilite et l'isolation des instruments sont touchees. La cause n'est pas etablie.

## Contexte acquis et points d'entree

- Une part contient un instrument, lequel peut contenir plusieurs kit items ; une banque contient des presets. Un kit peut activer ADD et PAD simultanement : cette combinaison est prevue par le moteur, ne pas la supprimer pour contourner le bug.
- native/engine_adapter/ZynAndroidEngine.cpp : setParameter distingue part/enabled et kit/muted. setPartEnabled(partIndex, enabled) ecrit le Penabled de la part cible et appelle son AllNotesOff quand elle est desactivee. kit/muted ecrit uniquement kit.Pmuted.
- app/src/main/java/com/mick/zynaddsubfx/EditorScreen.kt : PartEditorCard, EngineKitSection, onMute et les callbacks onSetPartEnabled/onSoloPart. Le callback kit transmet kit.kitIndex et la part courante.
- Suivre ensuite MainActivity.kt, SynthEngine.kt, app/src/main/cpp/native_bridge.cpp et les commandes natives. Les ecritures natives ciblees ne suffisent pas a exclure une erreur d'index, de canal, de solo ou d'etat UI en amont.
- Examiner noteOn/noteOff et les canaux partages si le comportement audible differe de l'etat des boutons. Distinguer desactivation de part, mute de kit, solo et Panic global.

## Travail attendu et validation

- Reproduire sur Dual String Ct1 et un cas minimal de deux parts actives, puis deux kits dont un ADD+PAD. Relever indices, canaux, flags natifs et etat UI avant/apres.
- Corriger a la couche qui possede l'erreur ; pas de tableau Kotlin de mute parallele ni de desactivation globale de secours.
- Verifier l'isolation entre parts et kits, le retour du son apres unmute, notes tenues/nouvelles, changements de selection et presets, solo et All Notes Off.
- Ajouter une regression ciblee si la cause se prete a un test. Si aucune reproduction n'est possible, conserver le signalement avec les limites explicites.

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

## Avancement — 27 octobre 2026

- Le mode non-kit ignorait `Pmuted` lors de la creation des notes : correction locale dans `Part::NoteOn` et test de regression ajoute dans `KitTest.cpp` (test non execute).
- L'interface presente maintenant les moteurs sous chaque kit et un seul `kit/muted` par kit ; ADD et PAD partagent bien ce mute.
- Bibliotheque native Android arm64 recompilee. Build Gradle hors ligne bloque par le plugin Foojay absent du cache ; aucun appareil ADB connecte.
- A faire : reproduire Dual String Ct1 et le cas a deux parts, executer le test et verifier sur appareil l'isolation, le retour du son, les notes deja tenues, mono/legato, solo, selection, presets et All Notes Off. La cause du signalement complet reste a confirmer ; les notes deja actives ne sont pas arretees par cette correction.
