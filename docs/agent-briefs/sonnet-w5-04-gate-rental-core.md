# w5-04 — Cœur : portes de l'essai et du mode réduit pour la boutique, partie découverte du Quiz, preuve d'activation, émission d'une location **sans maître**, plafond 60 j en ligne

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT
> **Groupe : W5a-1** (vague W5a) · prérequis : w4-01, w4-07, w1-06 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Routes*' --tests '*ActivationProof*' --tests '*Rental*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 5a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4 (a, b), § 4.2, § 6.2, § 6.3, § 9. Branche `claude/sonnet-w5-04`. Rapport : `docs/agent-reports/sonnet-w5-04.md`. Dépend de w4-01 (`RentalKeys.makeBoxV2`, `InstallKey`), w4-07 (`DegradedPolicy`, `GateState.Degraded`), w1-06 (`tools/routes/routes.txt`).

## Objectif
(1) `FeatureGate` : nouvelles fonctions `SHOP` (ouverte en essai **et** en mode réduit, en lecture) et `QUIZ_TASTER` (partie découverte, ouverte en essai seulement) ; (2) `TrialPolicy`/`DegradedPolicy` : routes de la boutique et des jetons classées ; `routes.txt` à jour ; (3) `ActivationProof` : choisir, parmi les activations installées, le jeton de **production qui compte** (ou l'essai, ou la production terminée) à envoyer comme preuve ; (4) `RentalIssuing.rightWithKey(...)` : émettre une ligne `rental` à partir d'une **clé de contrat fournie** (plus de `master`) enveloppée en boîte v2 ; (5) `RentalDurations.onlineMax = 60` et refus clair au-delà ; (6) `RentalKeys.masterFrom` documenté « tests/vecteurs v1 seulement ».

## Pourquoi (preuves)
- `C/owner/FeatureGate.kt` (`Feature.lockedAllowed`, `degradedAllowed` depuis w4-07, listes figées par `CT/owner/LicenseAndGateTest.kt:166-174`) ; `C/owner/TrialPolicy.kt:19` (`GAMES = {"sudoku"}`), `:29-43` (routes), `R/TvService.kt:258` (garde) ; `C/owner/DegradedPolicy.kt` (w4-07) ; `tools/routes/routes.txt` (w1-06, colonnes essai / réduit).
- `C/owner/LicensedIssuer.kt:36-41` (`RentalSpec`, `RentalIssuing.right(r, issuedAt, license, seat, device, master)`), `C/lots/RentalKeys.kt:26-33` (`masterFrom`, `rentalKey(master, …)`), w4-01 (`makeBoxV2(installPub, ephSeed, product, period, rentalKey)`).
- `C/lots/RentalDurations.kt` (durée exacte du catalogue) ; RENTAL-LOTS § 15 question 3 (plafond 60 j).
- `R/ActivationCenter.kt` lit `InstalledActivations` (liste des jetons installés avec leur texte) : la preuve doit être calculée en cœur, testable.

## Fichiers possédés
`C/owner/TrialPolicy.kt`, `C/owner/DegradedPolicy.kt`, `C/owner/FeatureGate.kt`, `C/owner/LicensedIssuer.kt`, nouveau `C/owner/ActivationProof.kt`, `C/lots/RentalKeys.kt`, `C/lots/RentalDurations.kt`, `tools/routes/routes.txt`, `CT/owner/{TrialRoutesTest,DegradedRoutesTest,LicenseAndGateTest,ActivationProofTest}.kt`, `CT/lots/{RentalTest,RentalDurationsTest}.kt`. **Hors zone** : `C/owner/Activation.kt` (lire), `C/shop/**`, `C/tokens/**`, `R/**`, `S/**`, `backend/`, `tools/routes/list_routes.py`.

## Étapes
1. `Feature.SHOP` (`lockedAllowed = false`, `degradedAllowed = true`, essai : vrai) ; `Feature.QUIZ_TASTER` (essai : vrai ; réduit : faux ; production : sans objet, le Quiz complet est ouvert) ; `Feature.TOKENS_SPEND` (production seulement). Mettre à jour les listes figées des tests (`LOCKED_WHITELIST` **inchangée** : rien de nouveau en `Locked`).
2. `TrialPolicy` : `GAMES` reste `{"sudoku"}` ; nouveau `quizTasterPerDay = 3`, `QUIZ_TASTER_TRACK = "general"`, `gameAllowed("quiz")` reste faux mais `tasterAllowed()` vrai ; routes autorisées en essai (ajout) : `GET /api/shop/catalog`, `GET /api/shop/state`, `POST /api/shop/catalog` (le téléphone relaie les fichiers signés), `POST /api/shop/pending-voucher`, `GET /api/tokens/report`, `GET /api/activation/proof`, `GET /api/quiz` (lecture) ; **fermées** : `POST /api/tokens/install` (pas de jetons en essai), `/quiz/*` (pas de salle), `POST /api/shop/order` ; `MESSAGE` ajoute « La boutique est consultable ; les commandes passent par le téléphone avec une clé de production. ».
3. `DegradedPolicy` : ouvre `/api/shop/*` en lecture, `POST /api/shop/catalog`, `POST /api/shop/pending-voucher` (bon de clé), `GET /api/tokens/report`, `GET /api/activation/proof` ; **fermé** : `POST /api/tokens/install`, `/api/rental/install` (déjà), `/quiz/*`.
4. `routes.txt` : lignes pour toutes les routes ci-dessus (colonnes essai / réduit) ; le test « chaque route classée » reste vert (les routes seront ajoutées au code par w5-16 ; d'ici là `list_routes.py` ne les voit pas : vérifier que la table tolère une route **classée mais absente du code** ou l'adapter en accord avec w1-06 : le dire).
5. `ActivationProof.select(installed: List<InstalledActivation(text, activation, installedAtUptime)>, nowMs, clockDoubt…): Proof?` : priorité (1) production qui compte, la plus récente ; (2) essai qui compte ; (3) production terminée (mode réduit) ; retourne `Proof(text, kind, counting: Boolean, licenseId, seatId)`. **Jamais** une activation de location (`right=rental` seul) ni un `super`. Tests : les 4 cas + aucune.
6. `RentalIssuing.rightWithKey(r: RentalSpec, issuedAt, license, seat, device: Fingerprints, rentalKey: ByteArray, installPub: ByteArray, ephSeed: ByteArray): Right.Rental` : `box = "v2:" + RentalKeys.makeBoxV2(...)` ; `period = r.period ?: issuedAt` ; bornes ; l'ancienne `right(..., master)` reste (tests, `tools/rental-test`) avec KDoc « dérivation par maître : outils de test uniquement ». `RentalKeys.randomContractKey(random: SecureRandom): ByteArray` (32 o). KDoc de `masterFrom` : « tests et vecteurs v1 ; aucune émission de production ne doit l'utiliser ».
7. `RentalDurations.ONLINE_MAX_DAYS = 60` ; `onlineDays(bundle): Int?` (null si `rentalDays > 60` : « ce bouquet n'est pas louable en ligne ») ; test.
8. `RentalTest` : vecteur local « ligne `rental` émise avec clé fournie s'ouvre sur l'installation cible et pas ailleurs » (réutiliser les clés X25519 de test de w4-01).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*' --tests 'castbridge.core.lots.*'   # vert
grep -n 'SHOP\|QUIZ_TASTER' android/core/src/main/kotlin/castbridge/core/owner/FeatureGate.kt   # ≥ 2
grep -n '/api/shop' tools/routes/routes.txt   # ≥ 4
python3 -m unittest discover -s tools/tests -p 'test_routes.py'   # vert
git diff --quiet tools/activation/*.json && echo "vecteurs intacts"
```

## Cas limites
- Essai **et** production terminée sur la même TV (W4-B § 5) : preuve = production terminée (mode réduit) ; la boutique dit « clé à renouveler ».
- Activation avec ticket (`<délégation>|<activation>`, w4-15) : la preuve est **la ligne complète** (le serveur vérifie la délégation) : `Proof.text` garde le ticket.
- `clockDoubt == AHEAD` : « qui compte » suit la règle w1-05 (reste `Activated`) : preuve de production.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `TvGate.evaluate` ni `LOCKED_WHITELIST` ; ne pas retirer `right(..., master)` ; aucun `import android` ; français.

## Rapport
`STATUT`, listes finales des routes par état, signature de `rightWithKey` (pour w5-06 via w5-05) et de `ActivationProof.select` (pour w5-16).
