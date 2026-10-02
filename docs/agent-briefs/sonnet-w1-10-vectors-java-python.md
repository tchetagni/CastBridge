# w1-10 — Vecteurs de location rejoués en Java et Python ; plafond implicite aligné

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=RentalVectorsTest`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S (≈ 5 h) · Statut PRÊT.** Branche `claude/sonnet-w1-10`. Rapport : `docs/agent-reports/sonnet-w1-10.md`.

## Objectif
Les trois vérificateurs (Kotlin, Java serveur, Python indépendant) rejouent **les mêmes fichiers** de vecteurs, y compris `tools/activation/rental-vectors.json` et `server-issued.json`, et appliquent la même règle de plafond implicite de 30 jours pour une clé d'essai sans ligne `usage`.

## Pourquoi (preuves)
- `tools/activation/rental-vectors.json` (41 cas : `rental-state` 22, `build-activation` 11, `lot-policy` 4, `old-device` 2, `box`, `seal`) est rejoué par `android/core/src/test/kotlin/castbridge/core/lots/RentalVectorsTest.kt:28` et le bureau, **ni par le serveur ni par Python** (`grep -rln rental-vectors backend/src/test tools/activation/verify_vectors.py` → vide ; seul `rental_bounds_ok` l.253 de `verify_vectors.py` mime les bornes).
- `tools/activation/server-issued.json` : produit par `backend/.../ServerIssuedVectorsTest.java:120`, consommé par Kotlin, **pas par Python**.
- `android/core/src/main/kotlin/castbridge/core/owner/Activation.kt:198-203` (`implicitUsageEnd`) : une activation TRIAL sans `usage` finit à `issuedAt + 30 j` ; `backend/.../licenses/WireActivation.java` et `verify_vectors.py` : alignement **non vérifié** (audit « non vérifié »).
- `verify_vectors.py:16-17` importe `cryptography` sans `requirements` (w1-07 ajoute `tools/requirements-dev.txt` ; ne pas le créer ici).
- Audit : TE-2, item 4 de l'audit sécurité.

## Fichiers possédés
`tools/activation/verify_vectors.py`, `backend/src/main/java/castbridge/server/licenses/WireActivation.java`, `backend/src/main/java/castbridge/server/licenses/EnvelopeVerifier.java`, nouveau `backend/src/test/java/castbridge/server/licenses/RentalVectorsTest.java`, `docs/ACTIVATION-FORMAT.md` (§ vecteurs : tableau « qui rejoue quoi »). **Ne pas modifier** les fichiers JSON de vecteurs (ils sont la référence ; si un vecteur semble faux, le dire dans le rapport).

## Étapes
1. Lire `RentalVectorsTest.kt` et `castbridge.core.lots.RentalVectors` pour comprendre les familles : `build-activation` (octets du jeton attendu), `box` (enveloppe par TV), `seal` (fichier chiffré), `rental-state` (états de l'horloge : concernent la TV, **à ne pas porter** côté serveur sauf les bornes), `lot-policy`, `old-device`.
2. Java : `RentalVectorsTest` rejoue `build-activation` (parse + canonique + signature via `EnvelopeVerifier`) et vérifie le refus des cas hors bornes ; **si** un port Java de HKDF/AES-GCM existe déjà dans `backend/` (`grep -rn Hkdf backend/src/main`), rejouer aussi `box`/`seal` ; sinon le signaler (c'est le travail de w2-10).
3. Java : vérifier/implémenter `implicitUsageEnd` dans `WireActivation`/`EnvelopeVerifier` quand le serveur **vérifie** une activation (tunnel, import) : même règle que Kotlin (TRIAL sans `usage` ⇒ fin à `issuedAt`/`notBefore` + 30 j). Ajouter un test unitaire.
4. Python : `verify_vectors.py` charge aussi `rental-vectors.json` (`build-activation`, `old-device`, `lot-policy` via les règles publiées dans `docs/RENTAL-LOTS.md` § 10 ; `box`/`seal` avec `cryptography` : HKDF-SHA256 + AES-256-GCM comme spécifié § 10.4) et `server-issued.json` ; implémente `implicit_usage_end`. Imprimer le compte de contrôles par fichier.
5. `docs/ACTIVATION-FORMAT.md` : tableau fichier × implémentation.

## Critères d'acceptation
```sh
pip install cryptography && python3 tools/activation/verify_vectors.py        # vert, affiche ≥ 3 fichiers et un total > 117 contrôles
cd backend && ./mvnw -q -o test -Dtest='RentalVectorsTest,WireFormatVectorsTest,EnvelopeVectorsTest,LicenceVectorsTest,ServerIssuedVectorsTest,OrderEnvelopeTest'   # vert
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.RentalVectorsTest' --tests 'castbridge.core.owner.ActivationVectorsTest'   # vert, inchangé
git diff --stat tools/activation/*.json    # vide (vecteurs non modifiés par ce cahier)
```

## Cas limites
- `tools/activation/test-vectors.json` est **déjà modifié, non commité** dans l'arbre de travail du coordinateur (69 lignes) : partir de `origin/integration/agents` et signaler si Java/Python échouent sur la version de l'arbre.
- Les vecteurs `rental-state` dépendent d'une horloge : Python peut les rejouer avec une implémentation de `RentalEngine.judge` simplifiée seulement si c'est court (< 80 lignes) ; sinon les laisser à Kotlin et le documenter.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret, pas de modification des JSON de vecteurs, pas de `requirements-dev.txt` (w1-07).

## Rapport
`STATUT`, tableau « fichier × implémentation × nombre de cas », divergences trouvées (avec vecteur fautif éventuel).
