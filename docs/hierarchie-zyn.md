# Zyn — pense-bête

```text
Master (synthé / mixage)
├── Parts (canal, volume, activation…)
│   └── Instrument
│       ├── Kit items (plage de notes, activation, mute…)
│       │   └── Moteurs ADD, SUB et/ou PAD
│       │       └── Oscillateurs, filtres, enveloppes, LFO…
│       └── Effets de la part
└── Effets système et d'insertion
```

- **Part** : emplacement jouable ; plusieurs parts peuvent partager un canal.
- **Instrument** : son chargé dans une part, avec ses kits et effets de part.
- **Kit item** : couche de l'instrument ; peut activer plusieurs moteurs à la fois, par exemple ADD + PAD.
- **ADD / SUB / PAD** : trois moteurs de synthèse. ADD comporte en plus des voix, avec leurs propres paramètres et filtres.
- **Filtre** : façonne le timbre ; il existe à différents niveaux selon le moteur (global au moteur ou par voix ADD).
- **Enveloppe / LFO** : font évoluer un paramètre dans le temps, par exemple le volume ou le filtre.
- **Preset d'instrument** (`.xiz`) : instrument enregistré, chargeable dans une part. Une **banque** regroupe des presets ; elle n'est pas dans la hiérarchie du son en cours de jeu.

Le mute d'un kit concerne sa couche ; l'activation d'une part concerne toute la part.
