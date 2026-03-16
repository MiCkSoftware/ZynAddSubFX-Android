# License / Distribution Notes (GPL) - Android ZynAddSubFX Port

## Objectif

Documenter une strategie simple pour publier une version Android basee sur ZynAddSubFX (GPL v2)
sans se perdre dans la procedure GitHub au moment de la release.

Ce document est une **checklist pratique** (pas un avis juridique).

## Point de depart (actuel)

- `third_party/zynaddsubfx/` est un fork local vendored dans ce repo
- l'APK Android embarque du code GPL (Zyn + modifications + JNI/bridge)
- si on distribue un binaire (APK/AAB), il faut fournir la **source correspondante complete**

## Recommandation (la plus simple / la plus sure)

Publier un **repo GitHub unique** contenant:
- code Android
- code natif/JNI
- `third_party/zynaddsubfx/` (vendored complet avec patches)
- fichiers de build (Gradle/CMake)
- notices de licence

Cette option evite les ambiguities "patch seulement".

---

## Option A (recommandee) - Repo GitHub complet (vendored)

### Ce qu'il faut publier

- Tout le repo ayant servi a produire la release APK/AAB (hors secrets)
- `third_party/zynaddsubfx/` complet
- tes modifications upstream (deja dans le tree)
- `COPYING` (GPL) et notices des autres deps
- instructions de build Android (README)

### Ce que tu peux garder prive (en general)

- keystore de signature
- API keys / secrets
- CI interne non necessaire au rebuild
- notes internes / roadmap

### Etapes GitHub (simples)

1. Creer un nouveau repo GitHub (public)
   - ex: `zynaddsubfx-android-port`

2. Ajouter le remote GitHub depuis ton repo local
   - `git remote add origin <URL_GITHUB>`
   - ou `git remote set-url origin <URL_GITHUB>` si un remote existe deja

3. Push la branche principale
   - `git push -u origin <ta-branche>`

4. Ajouter un `README.md` minimum
   - but du projet
   - base upstream (commit/ref)
   - statut (experimental)
   - instructions build (NDK/SDK)

5. Ajouter une release GitHub (si tu publies un APK)
   - attacher APK/AAB si besoin
   - lier clairement au commit source correspondant

6. Dans la release / README, mentionner
   - ZynAddSubFX upstream
   - licence GPL v2 (`third_party/zynaddsubfx/COPYING`)
   - que ce repo contient la source correspondante du binaire

### Bonnes pratiques (fortement recommandees)

- Tagger les releases (`v0.1.0-alpha1`, etc.)
- Noter le commit exact de la release APK
- Garder les patches upstream documentes dans `docs/porting/UPSTREAM_FORK_NOTES.md`

---

## Option B (avancee) - "Patch only" reproductible

Possible en theorie, mais plus fragile.

### Conditions minimales pour que ce soit propre

Il faut fournir **de quoi reconstruire exactement** le code distribue:
- commit upstream exact de Zyn
- patch(s) complets appliques a Zyn
- code Android/JNI
- fichiers de build/scripts
- instructions de rebuild claires

### Pourquoi on ne la recommande pas (pour ce projet)

- plus complique pour les utilisateurs
- plus facile de rater un patch / script / fichier
- moins clair pour la conformite GPL

---

## Checklist release GPL (APK/AAB)

- [ ] Le code source correspondant du binaire distribue est public et accessible
- [ ] La fiche Play / la description de diffusion mentionne explicitement l'engagement a respecter les regles Play pour les contenus familiaux, si l'alpha experimentale est publiee dans ce cadre
- [ ] `third_party/zynaddsubfx/COPYING` est inclus
- [ ] Les autres licences tierces sont preservees/mentionnees
- [ ] Les licences/notices et le lien public vers le depot source sont visibles depuis la fiche de publication et/ou l'ecran `About / Licences`
- [ ] Le commit/tag de la release est identifie
- [ ] Les instructions de build sont documentees (meme si elles sont "best effort")
- [ ] Aucun secret (keystore/API keys) n'est publie

## Rappel specifique pour une alpha experimentale Play

Avant publication, verifier au minimum:
- mention explicite de l'engagement a respecter les regles Play pour les contenus familiaux, si cette categorisation est retenue
- presence des licences tierces et de la notice GPL dans l'app et/ou la fiche de publication
- lien clair vers le code source correspondant de la build publiee (repo public + tag/commit associe)

---

## Notes pour plus tard (GUI proprietaire)

Si une partie GUI doit rester proprietaire, il faudra re-evaluer la strategie de separation
avant publication. Tant que tout est dans le meme APK lie au moteur GPL, il faut partir du
principe que publier le repo complet est la solution la plus sure.
