o on knob header tap add more details description in popup (harmonyx style)
- [x] Style des knobs inspire de Binaural : fond sombre, piste ouverte, repere mobile, accents par famille, degrades discrets sur cadrans/cartes/bandeaux ; labels accessibles et reglage TalkBack ajoutes. Cibles de 48 dp selon la largeur disponible, nombre de colonnes conserve. Compilation Kotlin OK ; validation visuelle/tactile/TalkBack sur appareil restante (7 novembre 2026).

- [x] Effet neon sur les courbes (LFO, enveloppes, oscillateurs, filtres/formants, resonance) : halo diffus renforce, coeur lumineux et nappe coloree sous la courbe, couleurs conservees. APK debug compile et installe ; rendu LFO verifie visuellement en apercu et en detail sur emulateur (7 novembre 2026).

- [x] Headers de sections avec icones adaptees, titres/traits lumineux et halo discret ; clavier harmonise. APK debug compile et installe, ecrans Part et ADD verifies visuellement sur emulateur, actions C/P conservees (7 novembre 2026).

- [x] Harmonisation des labels ADsynth/SUBsynth/PADsynth ; Last note/MIN/RESET/MAX dans une carte sur deux colonnes ; titre des points et actions Previous/Next/Add/Delete/Sustain harmonises avec les boutons LED. Compilation et 24 tests JVM OK, rendu Part/enveloppe verifie sur emulateur (7 novembre 2026).

- [x] Bugs enveloppes : sortie du mode libre reconstruit les points depuis les parametres simples ; Sustain point limite aux points existants avec choix Off explicite. Compilation native deux ABI et deux tests Android de regression OK sur emulateur ; APK debug installe (7 novembre 2026).
