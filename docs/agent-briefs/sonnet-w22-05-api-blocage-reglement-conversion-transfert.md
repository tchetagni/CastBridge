# w22-05 — API portefeuille : blocage (`cbe1`), règlement par résultat signé (`cbr1`), rendu des blocages échus, conversion dans les deux sens, codes de réception et transfert entre TV, collecteur d'hôte des résultats
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (règlement unique, conservation, liaison d'appareil, codes de réception) · statut : **ATTEND w22-02 et w22-03** (le test de règlement sur résultats réels attend aussi w22-04)
> **Groupe : W22-N1** (ordre 3) · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.**'` + `bash tools/wallet/collect-results.sh --self-test`
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5-2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.3, § 1.4, § 3.3, § 3.6, § 5.3 R-E3/R-E6 niveau 1/R-E7/R-E12, § 7.6). Branche `claude/w22-05-api-operations`. Rapport : `docs/agent-reports/sonnet-w22-05.md`.

## Objectif (autonome)
Sur le grand livre persistant de w22-02 (`JdbcLedger`, `WalletPolicyService`, `SyncContributor`) et le cœur de w22-01 (constructeurs de transactions, `Settlement.check`, `StakeRules`), exposer les opérations qui **déplacent** des jetons, toutes idempotentes, toutes refusées depuis un appareil API autre que celui du compte (`BOUND_OTHER_TV`), toutes coupables par interrupteur (`wallet_policy` : `switch.*`, actifs par défaut).

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/wallet/ops/{EscrowService,SettleService,EscrowExpiryContributor,ConvertService,ReceiveCodeService,TransferService,PlayResultKeys,WalletOpsController}.java` ; tests `backend/src/test/java/castbridge/server/wallet/ops/**` ; `tools/wallet/collect-results.sh` (cron d'hôte, modèle du collecteur W21 `sonnet-w21-03b` : `docker cp castbridge-play:/data/play-state/results` puis `curl -sf -X POST http://127.0.0.1:7090/api/v1/wallet/settle` par fichier, fichiers réglés déplacés dans `done/`, `--self-test` sans réseau).
- **Zone additive** : `backend/src/main/resources/application.yml` (`castbridge.wallet.play-result-pubkeys`).
- **Interdit** : `wallet/core/**`, les classes de w22-02 (consommées ; une méthode manquante ⇒ demander, ne pas réécrire), `server-play/**`, Android.

## Spécification
1. `POST /api/v1/wallet/escrow {cur, per, k, idem}` : `StakeRules.mayStake(édition, cur)` (essai + MBOKO ⇒ `TRIAL_NO_MBOKO`), bornes de `per`, `k` 1..8, interrupteur `switch.stakes.<cur>` ; transaction `ESCROW_LOCK` (`eid` = dérivé de `idem`, 22 car. b64url) ; ligne `wallet_escrow` `OPEN`, `exp = now + 30 min` ; rend `cbe1` signé par la clé « portefeuille » (format § 3.4, domaine `castbridge-wallet-escrow-v1`).
2. `POST /api/v1/wallet/settle` (corps = `cbr1`, **aucune authentification**, 60 requêtes / min / adresse) : vérifie la signature (`play-result-pubkeys`, deux clés), `rid` neuf **ou** rejoué à l'identique (idempotent : même réponse), chaque `eid` connu, `OPEN`, même `cur` et `per`, `id` concordant ; `Settlement.check` ; **une** transaction `SETTLE` (`settle:<rid>`) qui règle tous les blocages listés et rend le non-utilisé (`amount − used`) ; `eid` déjà `REFUNDED` ⇒ refus `RESULT_AFTER_REFUND` journalisé (rien n'est réglé pour cette partie : tout ou rien) ; `kind=ABORT` ⇒ rendus.
3. `EscrowExpiryContributor` (appelé à chaque `sync` du titulaire, et par `GET /admin/wallet/reconcile`) : blocage `OPEN` dont `exp + 6 h` est passé ⇒ `ESCROW_REFUND`.
4. `POST /api/v1/wallet/convert {dir: N2M|M2N, q, idem}` : interrupteur `switch.convert` ; `Conversion` de w22-01 avec `rate` et `reverseFeeBp` **lus dans `wallet_policy` à chaque appel** ; la réponse porte le taux et les frais appliqués et un `cbw1` neuf.
5. `POST /api/v1/wallet/receive-code` : code `R` + 8 Crockford + 1 contrôle (`R7K2-M9QX`), 10 min, usage unique, ≤ 3 actifs par identité, aléa `SecureRandom` ; `GET /api/v1/wallet/receive-code/{code}` : destinataire masqué (« TV de K… · …4F2Q » : initiale du nom de TV s'il est connu, 4 derniers caractères du code d'appareil), 10 consultations / h / identité, 1 000 / h global ⇒ alerte journal ; `POST /api/v1/wallet/transfer {code, cur, amt, idem}` : code valide et non utilisé, destinataire ≠ émetteur, plafond simple de niveau 1 (10 000 NDEM / 100 MBOKO par jour et par identité émettrice, depuis `wallet_policy`), transaction `TRANSFER`, code marqué utilisé dans la **même** transaction SQL.
6. Toute réponse d'écriture porte un `cbw1` neuf ; tout refus un motif `WalletReason` et son texte.

## Critères d'acceptation (mutations au rapport)
- `SettleFixturesTest` : les `cbr1` de `tools/wallet/fixtures/` (produits par w22-04 ; en attendant, des `cbr1` signés avec la clé de test de `wallet-vectors.json`) réglés sur des blocages créés par `EscrowService` ⇒ soldes attendus ; reposter 5 fois ⇒ inchangé ; un `cbr1` à Σ pay ≠ Σ used ⇒ refus ; un `cbr1` signé par une clé inconnue ⇒ refus ; `eid` inconnu ⇒ refus total (mutation : régler partiellement ⇒ échec).
- Course : 8 fils postent le même `cbr1` en même temps ⇒ une seule transaction `SETTLE`.
- Échéance : blocage sans résultat, horloge avancée de 6 h 31 min ⇒ rendu à la synchronisation ; résultat arrivé après ⇒ `RESULT_AFTER_REFUND`.
- Conversion : N→M puis M→N ⇒ soldes de départ (`fee` = 0) ; `reverseFeeBp` = 200 dans `wallet_policy` ⇒ M→N de 5 MBOKO donne 4 900 NDEM, `SYS:FEE` +100 ; même `idem` avec l'autre sens ⇒ `IDEM_CONFLICT`.
- Transfert : code expiré, déjà utilisé, propre code, plafond dépassé, autre `deviceId` (`BOUND_OTHER_TV`) ⇒ refus ; transfert valide rejoué ⇒ une seule écriture.
- Essai : `escrow` MBOKO refusé ; `escrow` NDEM accepté ; conversion et réception de MBOKO acceptées (D-W22-15).
- Après tous les tests : `reconcile` ⇒ I-1, I-3, I-8 tenus.
- `collect-results.sh --self-test` : rejoue un dossier de fixtures contre un serveur HTTP local factice ; aucun secret, aucune adresse publique.

## À ne pas faire
- Ouvrir une route qui crédite sur la parole de la TV ; permettre un règlement partiel ; faire appeler l'API par le service de jeu.
