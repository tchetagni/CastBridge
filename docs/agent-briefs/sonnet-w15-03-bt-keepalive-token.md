# w15-03 — Liaison : garde-vivant Bluetooth sans éviction de jeton, émission de jeton idempotente, persistance hors verrou
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (`LinkDriver`, `TrustRegistry` : jetons, verrous) · statut : PRÊT
> **Groupe : W15-S0-a** (vague W15, tranche S0, risqué n° 2) · prérequis : aucun ; décision D-W15-8 (persistance des jetons) par défaut = « persister hors verrou » · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LinkDriver*' --tests '*LinkMachine*' --tests '*Trust*' --tests '*HelloCompat*' --tests '*ResilientCall*'`
> **Jauge : ≈ 400 k jetons entrée / 18 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S0 (cœur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-03`. Rapport : `docs/agent-reports/sonnet-w15-03.md`. Règle : test rouge d'abord.

## Objectif
Un transfert long qui a capturé son jeton au départ reste autorisé jusqu'à la fin, même en route Bluetooth-tunnel : le garde-vivant **ne fabrique plus de jeton**, la TV **réutilise** un jeton encore valide, et la persistance du registre ne gèle plus les requêtes authentifiées.

## Pourquoi (preuves)
- `C/trust/LinkDriver.kt:201-208` : la branche `else` traite `Route.BluetoothTunnel` (`C/trust/PhoneLink.kt:76`, `C/tv/BtProtocol.kt:429`) comme « Bluetooth seul » ⇒ HELLO toutes les 60 s (`btKeepAliveMs`) + à chaque `forceCheck` (`:146`).
- `C/trust/TrustRegistry.kt:95-116` : chaque HELLO émet un jeton ; `maxTokensPerPhone = 4` (`:99-100`) ⇒ le plus ancien (tri `expiresAt`) saute ⇒ 401 `bad token` sur un transfert de plus de ≈ 4 min qui a capturé `job.pin` (`S/UploadService.kt:124`).
- `TrustRegistry.kt:123-132` `save()` sous `@Synchronized` avec `fd.sync()` (`C/trust/TrustFiles.kt:22`) ; `verifyToken` (`:111`) prend le même moniteur à chaque requête.
- `LinkDriver.kt:213` renvoie `Alive` quand le limiteur bloque (puce verte sans test) ; `S/LinkAndroid.kt:91` tout code ≠ 401 `bad token` vaut `OK`.
- Tests existants : `CT/LinkDriverTest.kt` (33 ; aucune référence à `BluetoothTunnel`), `CT/HelloCompatTest.kt:175` (bornes `in 1..4`), `CT/TrustTest.kt`, fixtures `CT/LinkFixtures.kt` (`FakeTv`, `FakeEnv`, `FakeClock`).

## Fichiers possédés
`C/trust/LinkDriver.kt`, `C/trust/TrustRegistry.kt`, `C/trust/TrustFiles.kt` (écriture seulement ; la lecture `.bak` est w15-06), `C/trust/PhoneLink.kt` (champ additif), `CT/LinkDriverTest.kt`, `CT/TrustTest.kt`, `CT/HelloCompatTest.kt`, `CT/LinkFixtures.kt` (ajouts). **Hors zone** : `C/trust/LinkMachine.kt` (hystérésis gelée), `C/trust/HelloHandler.kt` (format wire), `C/tv/BtProtocol.kt`, `S/**`, `R/**`, `C/tunnel/**`.

## Signatures (additives)
```kotlin
// TrustRegistry
fun issueToken(address: String, now: Long = this.now()): Issued   // réutilise un jeton de ce téléphone s'il lui reste > ttl/2 (Issued.reused = true), sinon en émet un
data class Issued(val token: String, val expiresAt: Long, val reused: Boolean)
// LinkDriver : pour Route.BluetoothTunnel, keepalive = env.check(tunnelBase, token) ; HELLO seulement à renewAt ; Outcome.Deferred quand le limiteur bloque (jamais Alive)
```

## Étapes
1. **Rouge** : `LinkDriverTest.bluetoothTunnelKeepaliveDoesNotRotateTokens` : `FakeTv` avec `tunnelBase`, 30 min simulées par `FakeClock` à cadence 15 s ⇒ `tv.tokensIssued ≤ 2` et le jeton pris à t0 est encore `verifyToken == true` à t0+20 min. `TrustTest.issueTokenReusesAFreshOne` : deux `issueToken` à 1 min d'écart ⇒ même jeton, `reused = true` ; à 7 h ⇒ nouveau. `TrustTest.verifyIsNotBlockedBySlowPersistence` : persistance factice de 200 ms ⇒ `verifyToken` < 5 ms pendant un `issueToken` concurrent. `LinkDriverTest.limiterDoesNotFakeAlive` : limiteur bloquant ⇒ pas de transition vers `Connected` sans sonde réussie.
2. `TrustRegistry.issueToken` idempotent ; `save()` hors du moniteur (copie de l'état sous verrou, écriture sur un exécuteur unique avec regroupement 500 ms) ; si D-W15-8 = « ne plus persister », garder l'écriture mais la rendre facultative par un drapeau (défaut : persister).
3. `LinkDriver` : branche `BluetoothTunnel` ⇒ `check` via le tunnel, HELLO à mi-vie seulement ; `Outcome.Deferred` ; 5xx ⇒ `UNREACHABLE` dans la classification de `check` (le `LinkEnv` Android est hors zone : exposer la règle dans le cœur, w15-11 branchera `LinkAndroid`).
4. `HelloCompatTest` : l'ancienne TV (jeton toujours neuf) reste compatible (le téléphone accepte `reused` absent).
5. Vert : porte complète + `:core:test` complet.

## Critères d'acceptation (hors ligne)
Porte verte ; 4 nouveaux tests cités rouges puis verts ; `LinkMachine.kt` et `HelloHandler.kt` sans diff ; `grep -n "fd.sync" C/trust/TrustFiles.kt` toujours présent (fsync conservé, déplacé hors verrou) ; `:core:test` complet vert.

## Cas limites
Jeton réutilisé mais que le téléphone a oublié (réinstallation) ⇒ HELLO suivant émet un neuf ; horloge TV reculée ⇒ `expiresAt` comparé avec `TvClock` existant ; 4 téléphones simultanés.

## À ne pas faire
Ne pas changer `maxTokensPerPhone` au-dessus de 4 sans le dire (option de secours seulement) ; pas de nouveau format de `trusted_phones.txt` (champ additif toléré par `HelloCompatTest`) ; ne pas toucher à l'hystérésis ni aux textes ; ne pas brancher `LinkAndroid`.

## Rapport
`STATUT`, sorties rouge/vert, nombre de HELLO par heure avant/après dans le test, question d'audit : réutilisation d'un jeton = réduction de l'entropie par session ? (attendu : non, même jeton, même TTL, hashé).
