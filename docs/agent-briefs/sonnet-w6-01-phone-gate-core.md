# w6-01 — `PhoneGate` (cœur) : fonctions du téléphone, listes blanches figées, états, matrice fonction × état de la TV

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W6a-1** (vague W6a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PhoneGate*' --tests '*PhoneMatrix*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 6a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.1, § 3.2, § 3.4, § 3.7, § 3.9. Branche `claude/sonnet-w6-01`. Rapport : `docs/agent-reports/sonnet-w6-01.md`. Protocole : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (jamais `main`, jamais le serveur, jamais de secret, français, « CastBridge » / « CastBridge-TV »).

## Objectif
Une porte **pure et testée** pour le téléphone, miroir de `FeatureGate`/`TvGate` : (1) `enum class PhoneFeature(minimalAllowed, agentAllowed)` ; (2) `MINIMAL_WHITELIST` et `AGENT_WHITELIST` figées par un test ; (3) `ProofRequirement(required = false, graceDays = 14)` + réutilisation de `FleetMigration` ; (4) `sealed class PhoneGateState { NotRequired, Grace, Minimal(reason), Linked(proofs), Agent, Super }` ; (5) `PhoneGate.state(...)` et `PhoneGate.canUse(feature, state)` ; (6) **la matrice** § 3.7 : `PhoneGate.cell(feature, tvState: TvEditionState, sync: SyncClass, superSession: Boolean): Cell` (`OPEN`, `CLOSED(messageId)`, `PARTIAL(messageId)`, `TV_DECIDES`) avec la table `EXPECTED_MATRIX` figée par un test ; (7) règle « au moins une TV prouvée » pour les fonctions propres au téléphone et « TV cible » pour les fonctions tournées vers une TV.

## Pourquoi (preuves)
- `C/owner/FeatureGate.kt:8-20` (`Feature.lockedAllowed`, `LOCKED_WHITELIST`), `:52-82` (`GateState`, `FeatureGate.state/canUse`), `:39-50` (`FleetMigration` réutilisable tel quel) ; `C/owner/Activation.kt:170-176` (`TvAccess` : `keyInstalled`, `trial`, `superUnlimited`, `suspended`) ; DESIGN-W4-MODE-DEGRADE § 3 (`TvAccess.degraded`, si w4-07 fusionné : le lire ; sinon modéliser `DEGRADED` dans `TvEditionState` sans dépendre du champ).
- Décision du 2026-10-01 (TRIAL-EDITION § 14 : téléphone ouvert) **remplacée** par W6 ; interrupteur `REQUIRE_TV_PROOF` éteint par défaut (branché par w6-11).

## Fichiers possédés
Nouveaux `C/owner/PhoneGate.kt`, `CT/owner/PhoneGateTest.kt`, `CT/owner/PhoneMatrixTest.kt`. **Hors zone** : `C/owner/FeatureGate.kt`, `C/owner/Activation.kt`, `C/owner/PhoneSync.kt` et `PhoneGateTexts.kt` (w6-02 : il consomme vos identifiants de message **comme des constantes `String`** définies ici dans `object PhoneMessages { const val NO_TV = "M-NO-TV" … }`), tout `S/`, `R/`, `backend/`.

## Étapes
1. `PhoneFeature` (liste exacte, § 3.2 et § 3.7) : minimal = `USAGE_NOTICE, PRIVACY_SCREEN, DISPLAY_LANGUAGE, TV_PAIRING, SHARE_DEVICE_CODE, CARRY_ACTIVATION_FOR_TV, FREE_CONTENT_DOWNLOAD, CAST_TO_LINKED_TV, LEARN_REMOTE, TRIAL_LOTS_SYNC, INTERNET_GATEWAY_FOR_TV, TELEMETRY_CONSENT, HELP, UPDATES_PHONE, SUPER_ADMIN_ENTRY, FOCAL_ENTRY, SHOP_BROWSE` ; fermés = `PHONE_LIBRARY_PLAYER, SEND_FILES_TO_TV, TV_LIBRARY_BROWSE, TV_LIBRARY_MANAGE, TV_ADMIN, LEARN_PHONE, QUIZ_PHONE, CHESS_PHONE, GAMES_PHONE, DOWNLOADS, LOTS_SYNC_FULL, SHOP_ORDER, TOKENS, PARENTAL_DASHBOARD, PARENTAL_RULES, REMOTE_TUNNEL_GATEWAY, ASSISTANT_IA, TRANSFER_MULTIPATH`. `agentAllowed` = minimal + `AGENT_SELL_KEYS, AGENT_SELL_VOUCHERS, AGENT_CONFIRM_ORDERS, AGENT_READ_TV_REQUEST` (nouvelles entrées, fermées sinon). Chaque entrée porte `tvFacing: Boolean` (famille (i) de § 3.7).
2. `TvEditionState` (ce que le téléphone sait d'une TV) : `NONE_PAIRED, NEVER_SYNCED, TRIAL, GRACE, LOCKED, PRODUCTION, DEGRADED, SUSPENDED` ; `SyncClass` : `FRESH, STALE, EXPIRED, UNREACHABLE` ; `Cell` sealed.
3. `PhoneGate.state(req, proofs: List<ProofSummary>, nowMs, migration: FleetMigration?, superActive: Boolean, agentActive: Boolean): PhoneGateState` dans cet ordre : `!req.required → NotRequired` ; `superActive → Super` ; `proofs.any { it.validAt(nowMs) } → Linked` ; `agentActive → Agent` ; grâce (`migration.graceUntil`) → `Grace` ; sinon `Minimal(reason)`. `ProofSummary(tvCode, tvName, verifiedAt, validUntil, endsAt?)` est une **donnée simple** (la vraie preuve est w6-03) ; `validAt(now) = now < min(validUntil, endsAt ?: MAX)`.
4. `canUse(feature, state)` : `NotRequired/Super/Grace → true` ; `Linked → true` ; `Agent → agentAllowed` ; `Minimal → minimalAllowed`.
5. `cell(feature, tv, sync, superActive)` : implémente la matrice § 3.7 **cellule par cellule** (colonnes A-H) ; `EXPECTED_MATRIX` = `Map<Pair<PhoneFeature, Column>, Cell>` littérale dans le test ; `H` ne renvoie jamais `CLOSED` sauf pour une fonction `tvFacing` dont la TV est injoignable (`TV_DECIDES` sinon) ; grâce du téléphone lue comme D.
6. `targetRule(feature, tvs: List<Pair<TvEditionState, SyncClass>>, active: Int?)` : `tvFacing` → cellule de la TV active ; sinon → `OPEN` si une TV quelconque est `PRODUCTION`+`FRESH|STALE` (ou `UNREACHABLE` avec preuve valide), sinon la cellule la **moins mauvaise** (priorité `PARTIAL` > `CLOSED`) avec son identifiant.
7. `PhoneMessages` : constantes des identifiants (§ 3.8) ; aucune phrase ici.
8. Tests : `theMinimalSurfaceIsExactlyTheListedOne` (jeu figé), `theAgentSurfaceIsExactlyTheListedOne`, ordre des états (super > linked > agent > grâce > minimal), grâce absolue (réinstallation sans effet), preuve expirée / au-delà de `endsAt`, `EXPECTED_MATRIX` complète (chaque fonction × 8 colonnes : une cellule manquante fait échouer), `targetRule` (essai + production : lecteur ouvert, copie vers l'essai `CLOSED(M-TV-TRIAL)`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.PhoneGateTest' --tests 'castbridge.core.owner.PhoneMatrixTest'   # vert, ≥ 25 cas
grep -c 'PhoneFeature\.' android/core/src/test/kotlin/castbridge/core/owner/PhoneMatrixTest.kt   # ≥ 30 (matrice littérale)
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/owner/PhoneGate.kt   # 0 hit
```

## Cas limites
`proofs` vide + `agentActive` ⇒ `Agent` (pas `Minimal`) ; `superActive` sans preuve ⇒ `Super` ; `graceDays = 0` ⇒ jamais `Grace` ; `now` reculé : `validAt` ne « ressuscite » pas une preuve dont `validUntil` est passé (le cache w6-03 fournit un `now` monotone : ici on compare simplement) ; `FREE_CONTENT_DOWNLOAD` est `OPEN` dans **toutes** les colonnes, y compris A.

## À ne pas faire
Ne pas modifier `FeatureGate`/`Feature` (TV) ; aucune phrase française de refus ici (w6-02) ; pas d'Android ; ne pas « deviner » une cellule : la matrice du § 3.7 est la référence, une divergence se signale dans le rapport.

## Rapport
`STATUT`, API publique (signatures) pour w6-02, w6-16, w6-17, nombre de cas, cellules dont vous doutez.
