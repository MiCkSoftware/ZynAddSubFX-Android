# 020 — Valider les contrats et la persistance SUB, PAD et FX

## Mission et dependances

Apres les lots fonctionnels 010 a 019, completer les tests automatises transverses et la documentation des preuves. Ce lot precede la campagne finale audio/appareil 021.

## Contexte acquis

- AddSynthRoundTripInstrumentedTest et CommonModulesInstrumentedTest couvrent deja des allers-retours ADD, clipboard et XIZ ; les reutiliser comme patterns sans limiter les assertions aux champs deja exposes.
- docs/qa_addsynth_audio_xiz.md documente les regressions corrigees : export Part::saveXML en XML complet (minimal=false), loadXMLinstrument remet l'instrument a ses defauts avant lecture pour eviter l'heritage de champs absents.
- Cette QA ADD ne certifie pas l'absence d'heritage de tout SUB/PAD. Tester les presets partiels et modules desactives expressivement.
- SynthEngine et les chemins de session/chargement dans MainActivity.kt et NativeSynthBridge sont deja presents ; ne pas ajouter une nouvelle couche de stockage.
- Le test ajoute globalFilterCategoriesAndCombControlsSurviveXizReload couvre categories, Comb, clipboard et XIZ. Sa presence dans le code ne prouve pas son execution ; rapport disponible lors de la preparation : 17 tests instrumentes du 6 novembre, anterieur a cet ajout.

- Constat du lot 010 : `Part::add2XMLinstrument` omet les branches des moteurs entierement OFF si `SaveFullXml` est faux, meme avec `minimal=false`. Traiter/tester explicitement ce cas ; [preuves et limites du socle commun](../qa_common_modules_sub_pad.md).

## Travail et criteres d'acceptation

- Corpus identifie par vrais chemins assets : SUB seul, PAD seul, instrument ADD+SUB+PAD et plusieurs kits, FX des trois scopes. Pas de noms de presets inventes.
- Comparer valeurs, structures et XML semantique lorsque le snapshot ne suffit pas ; tester modules OFF et champs caches apres chargement d'un autre preset.
- Clipboard par module/moteur/part/kit et destinations incompatibles ; verifier les valeurs hors module non modifiees.
- XIZ : instrument/kit/FX Part. Session/XMZ : parties, inserts, systemes et routages Master. Tester restauration et edition plus recente pendant une sauvegarde asynchrone.
- PAD : s'assurer que les parametres restaures produisent des samples applicables, sans serialiser des buffers generes dans Compose.
- Produire un rapport durable de tests, environnement, executions exactes et limites ; mettre les matrices a jour sans declarer l'ecoute finale faite.

## Regles d'implementation et cloture

- Lire AGENTS.md. Etat analyse le 7 novembre 2026 ; verifier les symboles de ce lot plutot que refaire l'audit general. Chemins relatifs a la racine du depot.
- Preserver les modifications locales preexistantes, notamment correction du filtre ADD et tests associes. Ne pas les reimplementer ni les ecraser.
- Source de verite : proprietaires natifs Part/kit/parametres et EffectMgr/Master. UI orchestre, pas de mutation croisee ni d'etat moteur parallele.
- Reutiliser les composants Compose ADD, clavier, navigation et contrats de gestes. Chemin continu : ecriture necessaire + etat local reduit ; snapshot, preview couteux et inspection globale a la fin/au changement structurel.
- Faire les tests locaux cibles utiles et documenter leurs limites. La validation finale comparative audio/appareil est differee jusqu'a SUB+PAD+FX complets ; elle ne bloque pas la cloture fonctionnelle de chaque lot. Une QA ciblee de developpement reste possible.
- Ne pas committer, pousser, publier ou installer sur appareil sans autorisation correspondante.
- En fin d'implementation : reporter les resultats et limites durables dans TASKS.md/TODO.md (et un rapport docs/ si necessaire), mettre a jour l'index docs/backlog/README.md, retirer tous les liens vers ce prompt puis supprimer ce fichier.
- Si le travail reste incomplet, conserver le prompt avec un etat precis et le reste a faire. Ne jamais supprimer la seule trace d'une validation differee.
