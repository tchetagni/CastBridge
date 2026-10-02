# w8-02 — Cœur : santé des voies (EWMA), garde « jamais plus lent que la meilleure voie seule », plafond K par disque, plafond de débit

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w8-01 ; sinon contrat Lane)
> **Groupe : W8a-2** (vague W8a) · prérequis : w8-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LaneHealth*' --tests '*Guard*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-01 ; sinon coder contre l'interface `Lane` du cahier w8-01 § 1).** Conception : § 4.4, 4.6 (plafond), 4.7, 4.9, 8.6. Branche `claude/sonnet-w8-02`. Rapport : `docs/agent-reports/sonnet-w8-02.md`.

## Objectif
Que l'agrégat ne soit **jamais** plus lent que la meilleure voie seule, que les voies inutiles ou nuisibles (Bluetooth qui gêne le Wi-Fi 2,4 GHz, Wi-Fi Direct en même temps que le LAN) soient mises en veille ou retirées **par mesure**, et que l'on n'ouvre pas 8 connexions pour une clé à 2 Mo/s.

## Pourquoi (preuves)
- `WifiLane.sample` (fenêtre 1,5 s) et `KController` (`core/xfer/Lanes.kt:13-34, 82-88`) n'ont ni RTT, ni score, ni notion de voie « nuisible ».
- `TransferHost.mayAccept`/`writeBps` (`TransferHost.kt:64-73`) : la TV borne les octets en vol mais le téléphone continue de sonder K vers le haut (`KController.onSample`).
- Coexistence BT/Wi-Fi 2,4 GHz et radio unique LAN/Direct : § 0.1 de la conception.

## Fichiers possédés
Nouveaux `C/xfer/{LaneHealth,Guard,RateLimiter}.kt`, `CT/xfer/{LaneHealthTest,GuardTest}.kt` ; `C/xfer/Lanes.kt` (`KController.limitRate`, `WifiLane` : RTT, `idle`, `kind`, `unitBytes` ; suppression des usages de `slow`) ; `C/xfer/HttpConn.kt` (`setTrafficClass(0x48)` dans `open()`, en `runCatching`). **Hors zone** : `Scheduler.kt`/`Lane.kt`/`LaneSet.kt` (w8-01 : si une accroche manque, la demander dans le rapport ; l'ordonnanceur appelle `Guard.tick()` via le `Listener` existant ou un `onTick` fourni par w8-01).

## Étapes
1. `LaneHealth` (pur, horloge injectée) : `onSent(bytes, rttNs)`, `onError()`, `onBusy()`, `bps()` (EWMA α = 0,2 par fenêtre 1,5 s), `rttMs()` (EWMA), `errStreak`, `score() = bps × (1 − 0,25 × min(errStreak, 3))`, `state` ∈ {ACTIVE, IDLE, BENCHED, PROBING}, `bytesWasted`. Une instance par voie, exposée par `Lane.health` (champ ajouté dans `Lanes.kt` pour `WifiLane`/`WifiDirectLane` ; `BulkBtLane` de w8-09 fera pareil).
2. `Guard` (pur) : `tick(now, lanes: List<LaneView>): List<Decision>` avec `LaneView(id, kind, bps, score, state, ghz)` ; décisions `Idle(id)`, `Wake(id)`, `Remove(id)`, `Keep`. Règles, dans l'ordre : (a) jamais `DIRECT` et `WIFI` actives ensemble : si les deux, garder `WIFI` sauf si `bps(WIFI) < 1 Mo/s` pendant 20 s ; (b) `BT` en veille dès qu'une voie Wi-Fi 2,4 GHz (`ghz == 2` ou inconnu) dépasse 2 Mo/s ; réveil sous 0,5 Mo/s ou disparition ; (c) voie à `score < 5 % du total` pendant 20 s avec > 2 voies actives → `Idle` ; réadmission par `PROBING` toutes les 30 s ×2 ≤ 5 min ; (d) sondage subtractif : `total < 0,9 × best` sur 2 fenêtres de 10 s → `Remove(plus faible score)` ; (e) si `diskBps > 0 && diskBps < total × 0,8` → aucune `Wake`. Tout seuil est une constante nommée et **réglable** par constructeur (le banc les fera varier).
3. `KController.limitRate(diskBps: Long, perConnBps: Double)` : `max = min(8, ceil(diskBps × 1,3 / perConnBps))` (jamais < 1) ; appelé par `WifiLane` à chaque réponse `ok` portant `writeBps` (`ChunkClient.outcome` : extraire `writeBps` du corps comme `retryMs` l'est déjà, `Lanes.kt:44`).
4. `RateLimiter` (seau à jetons, pur) : `acquire(bytes, now)` renvoie le délai ; utilisé par les ouvriers via `SendContext` (champ ajouté par w8-01 ou, à défaut, injecté dans `WifiLane` : le dire). Plafond `0` = désactivé.
5. `WifiLane` : mesurer le RTT (nanoTime entre fin du corps et première ligne de réponse), `idle(true)` = l'ouvrier ne prend plus de bloc mais la connexion reste ouverte (keep-alive) ; `kind = WIFI` (`DIRECT` pour `WifiDirectLane`).
6. Tests : EWMA converge en ≤ 6 fenêtres ; score avec erreurs ; chaque règle (a)-(e) avec des `LaneView` scriptées ; subtractif retire bien la plus faible et pas la meilleure ; `limitRate` sur disque 2 Mo/s et 3 Mo/s par connexion → K = 1 ; seau à jetons : 5 Mo/s sur 10 s → 50 Mo ± 5 %.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.LaneHealthTest' --tests 'castbridge.core.xfer.GuardTest'   # vert, ≥ 14 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.MultipathTransferTest' --tests 'castbridge.core.MultipathServerTest'   # vert
grep -n "setTrafficClass" android/core/src/main/kotlin/castbridge/core/xfer/HttpConn.kt | wc -l   # 1
```

## Cas limites
Une seule voie : la garde ne retire jamais la dernière ; `ghz` inconnu côté TV : traiter comme 2,4 GHz (prudence) ; voie en `PROBING` qui échoue : retour `IDLE` avec délai doublé ; `diskBps = 0` (pas encore mesuré) : règle (e) inactive.

## À ne pas faire
Pas de mesure réelle supposée dans le code (tout est injecté) ; ne pas supprimer la mise à l'écart sur erreurs de `Scheduler` ; ne pas toucher aux fichiers Android ; pas de `Thread.sleep` dans les tests.

## Rapport
`STATUT`, constantes et seuils retenus, accroches demandées à w8-01, tests avant/après.
