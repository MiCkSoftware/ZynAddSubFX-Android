# AGENTS.md

## Workflow

- Gradle builds/tests peuvent etre lances par l'agent quand c'est utile pour valider une modification locale.
- Eviter les commandes destructives Git (`reset --hard`, etc.) sauf demande explicite.
- Preferer des changements incrementaux + verification rapide.

## Interactions continues et controles Compose

- Pour un controle modifie en continu (knob, slider, geste de glissement), le chemin execute a chaque mouvement doit rester leger : ecrire la valeur necessaire et mettre a jour un etat local/reduit pour l'affichage.
- Ne pas relire un snapshot natif complet, reconstruire une collection volumineuse, ni declencher une inspection globale a chaque mouvement d'un geste.
- Effectuer la resynchronisation complete (snapshot, inspection des kits/parts, recalcul couteux) a la fin du geste ou lors d'un changement structurel explicite.
- Si l'affichage doit rester immediat pendant le geste, utiliser une valeur transitoire locale puis reconciler cet etat avec la source native a la fin ; ne pas sacrifier la fluidite pour obtenir une lecture complete par frame.

## Projet (port Android ZynAddSubFX)

- Objectif: port standalone Android de ZynAddSubFX (sans VST), UI Jetpack Compose refaite.
- MIDI differe dans un premier temps.
- Priorite actuelle: stabilite moteur/audio Android, compat presets, puis UX.

## Notes

- `third_party/zynaddsubfx/` est un fork local vendored assume dans ce repo.
- Garder les secrets (clefs API, keystore, etc.) hors du repo.
