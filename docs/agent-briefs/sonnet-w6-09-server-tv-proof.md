# w6-09 — Serveur : en-tête `X-CB-TV-Proof` exigé pour les lots complets, la boutique et les jetons ; miroir Java de la preuve ; vecteurs rejoués

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w6-03)
> **Groupe : W6b-1** (vague W6b) · prérequis : w6-03 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='TvProof*Test'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 6b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w6-03 fusionné ; après w5-06/07/08 si fusionnés, sinon ne gater que les lots).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.4 (serveur), § 3.6, § 7 (4). Branche `claude/sonnet-w6-09`. Rapport : `docs/agent-reports/sonnet-w6-09.md`.

## Objectif
(1) `TvProofHeader` : lecture de `X-CB-TV-Proof: <jeton cbx1 d'activation de production>` et vérification par le chemin existant (`EnvelopeVerifier` + anneau de clés de confiance + révocations) : type `activation`, `kind=production`, `subject=tv`, plafond `usage` non dépassé, poste non révoqué ; (2) exigée par `GET /api/v1/lots/catalog?edition=full`, `GET /api/v1/lots/{fonction}/{scope}/{version}` d'un lot **complet**, toutes les routes `/api/v1/shop/**` et `/api/v1/tokens/**` ; **jamais** pour `edition=trial`, les lots `-trial`, `/api/v1/free-content`, les mises à jour, la télémétrie ; (3) 403 `{"error":"Cette fonction demande une TV activée en production","code":"TV_PROOF_REQUIRED|TV_PROOF_INVALID|TV_PROOF_TRIAL|TV_PROOF_ENDED"}` ; (4) `B/licenses/TvProof.java` : lecture de l'enveloppe `proof` (type, corps, signature par une clé **fournie**) pour rejouer `proof-vectors.json` (le serveur ne reçoit pas la preuve au défi : le miroir sert aux vecteurs et à un futur relais) ; (5) tests.

## Pourquoi (preuves)
- DESIGN-W5 § 15 : `B/licenses/EnvelopeVerifier.java` (pur), `B/tunnel/TunnelService.java:167-181` (vérifie une activation comme la TV), `B/licenses/TrustedKeys.java` (`castbridge.licenses.trusted-keys`), `ShopRequestVerifier` (w5-07, `B/shop/order/**`) ; localisez le contrôleur des lots : `grep -rn "lots/catalog\|/api/v1/lots" backend/src/main/java` et nommez-le dans le rapport ; `docs/API-SERVER.md`.
- Le même jeton sert de preuve à W5 (boutique) : **ne pas** créer un second format.

## Fichiers possédés
Nouveaux `B/licenses/TvProofHeader.java`, `B/licenses/TvProof.java`, `BT/licenses/{TvProofHeaderTest,TvProofVectorsTest}.java` ; modifiés : le contrôleur des lots (nommé dans le rapport), `B/shop/ShopController.java` et `B/shop/tokens/**` **seulement** si w5 est fusionné (sinon rien), `docs/API-SERVER.md` (§ preuve de TV). **Hors zone** : `B/licenses/EnvelopeVerifier.java` (lecture seule), `B/tunnel/**`, migrations, `application.yml`, `tools/`.

## Étapes
1. `TvProofHeader.verify(headerValue, keyring, revocations, now): Result` (`Ok(license, seat, deviceCode, endsAt)`, `Missing`, `Invalid(reason)`, `Trial`, `Ended`) ; aucun accès base pour la signature ; révocation de poste via le service existant.
2. Filtre/annotation (`@RequireTvProof`) ou appel explicite dans les routes listées ; `edition=trial` et lots d'essai (scope finissant par `-trial`) passent sans en-tête.
3. Journal d'audit : une ligne par refus (sans jeton), compteur `tv_proof_refused` dans les anomalies existantes si disponibles.
4. `TvProof.java` : parse de l'enveloppe type `proof` (corps `code=`, `state=`, …), `verify(token, nonce, pubKey, now)` ; `TvProofVectorsTest` rejoue `tools/activation/proof-vectors.json` (cas `proof` et `build-proof` : pour `build`, comparer les octets).
5. Tests : en-tête absent ⇒ 403 `TV_PROOF_REQUIRED` sur `edition=full`, 200 sur `edition=trial` ; jeton d'essai ⇒ `TV_PROOF_TRIAL` ; plafond dépassé ⇒ `TV_PROOF_ENDED` ; clé inconnue ⇒ `TV_PROOF_INVALID` ; lot `-trial` servi sans en-tête ; lot complet refusé sans en-tête.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='TvProofHeaderTest,TvProofVectorsTest'   # vert
cd backend && ./mvnw -q -o test   # tout vert
grep -rn 'X-CB-TV-Proof' backend/src/main/java | wc -l   # ≥ 3
```

## Cas limites
Jeton de plus de 8 Ko ⇒ 413 ; horloge du serveur fait foi ; `SUPER_UNLIMITED` ⇒ accepté (production permanente) ; un jeton de **téléphone** (`subject=phone`) ⇒ `TV_PROOF_INVALID` (la preuve est celle d'une TV).

## À ne pas faire
Ne rien déployer ; ne pas toucher la production ; ne pas exiger la preuve pour les contenus libres ou les lots d'essai ; aucune donnée parentale côté serveur (il n'y en a pas : ne pas en créer).

## Rapport
`STATUT`, nom du contrôleur des lots, routes gatées, format de l'erreur (pour w6-17).
