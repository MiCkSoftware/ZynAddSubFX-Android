# Performance A/B — 15 novembre 2026

Disposition demandée explicitement par Mick à partir de l'écran Play : un bouton
A/B immédiatement à gauche de FX alterne la même rangée de quatre contrôles.
Aucune hauteur ni rangée de paramètres supplémentaire.

- A : Master, Channel, Velocity, Octave, contrôles existants conservés.
- B : Tempo (40..200 BPM), Pitch (-100..100 %, centre au relâchement),
  Modulation (0..127), Sustain (LED ON/OFF dans une carte de même taille).
- Le bouton affiche toujours A/B : mauve en A, jaune citron en B, teintes
  reprises de la palette des courbes harmoniques. Il reste éclairé dans les
  deux états ; son libellé accessible indique la banque courante.
- Changer de banque ne modifie pas les réglages. Sustain peut rester actif
  pendant l'utilisation de A. Les contrôleurs suivent le canal Play, Auto inclus.
- Pitch/Sustain sont relâchés en quittant le canal ; All Off et la libération
  des notes lors d'une pause relâchent aussi Sustain. Panic réconcilie les valeurs.

## Propriétaires et synchronisation

Le tempo appartient à Master : `setTempo` centralise la borne 40..200 et la
réapplication des rapports temporels des effets Part/Insert/System. Les LFO de
synthèse synchronisés suivent déjà `time.tempo`. Le tempo est désormais enregistré
et relu dans le XML Master ; une ancienne session sans tempo utilise 120 BPM.

Pitch/Modulation/Sustain passent par `Master::setController`, donc les contrôleurs
natifs des Parts activées sur le canal du clavier. La profondeur de modulation,
la réception Sustain et la plage de bend restent définies par les contrôleurs
natifs du preset ; les modifier ne fait pas partie de cette interface.

Le JNI envoie les quatre contrôles dans la file de commandes existante. Pendant
le drag : commande réduite et état local d'affichage, sans snapshot de synthèse
ni inspection du mixer. Une lecture de quatre valeurs réconcilie au commit ou
lors d'un changement de contexte. Cette lecture peut vider la file au point
exclusif de synchronisation existant, y compris lorsque l'audio est arrêté.
Il ne s'agit pas d'un nouveau transport MIDI externe.

## Vérifications locales et limites

- Contrat natif hôte ADD/SUB/PAD/FX réussi ; cas Performance ajoutés : pitch signé
  et recentrage, modulation, seuil Sustain, isolation de canaux, domaines et
  indices refusés, tempo 90 BPM → Echo synchronisé 1/4 à 56/127, sauvegarde et
  relecture du tempo avec recalcul du délai.
- 38 tests JVM réussis ; APK debug ARM64/x86_64 et APK AndroidTest compilés.
- Aucun test visuel/tactile ou écoute comparative sur appareil pour cette rangée,
  aucune installation ni commit/push. Les contrôles de jeu ne sont pas restaurés
  comme des notes tenues ; le tempo est une donnée de session.
- La synchronisation et le compteur `editorSkips` du prototype moteur restent
  ceux décrits dans la QA FX ; aucune qualification audio sans coupure revendiquée.

Ajustement visuel : les arcs des quatre knobs et les bandeaux de leurs cartes
sont assortis au bouton A/B (mauve en A, citron en B), avec la même palette
des courbes harmoniques. La LED Sustain suit aussi la teinte B. APK debug compilé.

Les fonds dégradés et bordures de DenseControlCard suivent désormais sa teinte
de label, déjà partagée avec le knob. Le cache du dégradé dépend de cette couleur :
le passage A/B met donc aussi le fond à jour. Ce comportement est commun aux
cartes de paramètres, sans fond ajouté indépendamment dans chaque écran.
