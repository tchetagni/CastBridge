# w1-09 — Trois tests instables rendus déterministes

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : À VÉRIFIER : HANDOFF dit déjà fait (port 0, horloge/aléa injectés)
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sshd:test :core:test --tests '*Trust*' --tests '*ChessRelay*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S (≈ 5 h) · Statut PRÊT.** Branche `claude/sonnet-w1-09`. Rapport : `docs/agent-reports/sonnet-w1-09.md`.

## Objectif
`TvSshServerTest.unknownKeyIsRefusedAndAddressGetsLocked`, `TrustTest.onlyTrustedPhonesGetTokensAndRevocationKillsThem` et `ChessRelayTest.availabilityProbe` passent **20 fois de suite** sans relance.

## Pourquoi (preuves, `docs/HANDOFF.md:13`)
- `android/sshd/src/test/kotlin/castbridge/sshd/TvSshServerTest.kt:59` : (a) port choisi par `ServerSocket(0)` puis fermé et relié (course TOCTOU) ; (b) `FailureTracker(3, 60_000)` sur `System.currentTimeMillis` (`android/core/src/main/kotlin/castbridge/core/ssh/SshPolicy.kt:54-66`) ; (c) l'échec est compté dans l'authentificateur côté serveur (`TvSshServer.kt:113-117`) alors que `assertFails { auth().verify(5 s) }` passe aussi sur un **timeout** client : le 3ᵉ échec peut ne pas être enregistré quand la bonne clé se présente ; (d) `SshClient.setUpDefaultClient()` charge `~/.ssh/id_*` du poste → nombre d'essais variable.
- `android/core/src/test/kotlin/castbridge/core/TrustTest.kt:27` : `TrustRegistry` tire `SecureRandom()` (`android/core/src/main/kotlin/castbridge/core/trust/TrustRegistry.kt:38`) ; HANDOFF:74 : « jeton altéré tiré au hasard ».
- `android/core/src/test/kotlin/castbridge/core/ChessRelayTest.kt:97-103` : `ServerSocket(0).use { localPort }` puis `FakeRelay(port).start(...)` (TOCTOU) ; `available()` fait un vrai HTTP avec `connectTimeout 5 s` (`android/core/src/main/kotlin/castbridge/core/chess/ChessTransport.kt:55,130`) ; la sonde `/absent` tombe sur un chemin non prévu du faux relais (500 par accident).
- Audit : TE-4.

## Fichiers possédés
`android/sshd/**` (main + test), `C/ssh/SshPolicy.kt`, `C/trust/TrustRegistry.kt`, `C/chess/ChessTransport.kt`, `android/core/src/test/kotlin/castbridge/core/TrustTest.kt`, `android/core/src/test/kotlin/castbridge/core/ChessRelayTest.kt`, `docs/HANDOFF.md` (**ligne 13 seulement** : retirer les tests devenus stables). **Hors zone** : `TvSshServer` côté shell/exec (w2-02 le durcira : ne pas toucher `shellFactory`/`commandFactory`).

## Étapes
1. `SshPolicy.FailureTracker` : constructeur `(max, windowMs, now: () -> Long = System::currentTimeMillis)`.
2. `TvSshServer` : accepter `port = 0` et exposer `boundPort` après `start()` (lire `sshd.port` de MINA après démarrage) ; exposer un compteur/`isLocked(addr)` consultable par le test.
3. `TvSshServerTest` : horloge factice, `client.keyIdentityProvider = KeyIdentityProvider.EMPTY_KEYS_PROVIDER`, attendre `server.isLocked("127.0.0.1")` (polling 50 ms, max 10 s) avant la 4ᵉ session, asserter la **raison** (refus publickey) plutôt qu'un `assertFails` générique ; délais client 30 s.
4. `TrustRegistry` : paramètre `random: SecureRandom = SecureRandom()` ; dans le test, `SecureRandom.getInstance("SHA1PRNG")` semé ; altérer un **octet du milieu** du jeton et asserter `altered != original`.
5. `ChessRelayTest` : `FakeRelay(0)` et lecture de `listeningPort` après `start()` ; `FakeRelay.serve` répond explicitement 404 sur un chemin inconnu ; `ChessTransport.available()` accepte un `HttpJson` injectable (ou `connectTimeout` paramétrable) pour que le test ne dépende pas d'un délai de 5 s.
6. Boucle de stabilité locale : `for i in $(seq 20); do gradle --offline :sshd:test --tests '*TvSshServerTest*' -q || echo FAIL $i; done` (idem pour les deux autres).

## Critères d'acceptation
```sh
cd android && for i in $(seq 20); do gradle --offline :sshd:test --tests 'castbridge.sshd.TvSshServerTest' -q >/dev/null || echo "FAIL $i"; done   # aucune ligne FAIL
cd android && for i in $(seq 20); do gradle --offline :core:test --tests 'castbridge.core.TrustTest' --tests 'castbridge.core.ChessRelayTest' -q >/dev/null || echo "FAIL $i"; done
cd android && gradle --offline :core:test :sshd:test    # suites complètes vertes
```

## Cas limites
- `TrustRegistry` est instancié par le receiver (`TvService.kt:204` via `TrustFile`) : garder le constructeur par défaut.
- Ne pas changer le comportement de verrouillage (3 échecs / 60 s).

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas toucher au shell SSH ni à `ReceiverServer`.

## Rapport
`STATUT`, cause racine confirmée pour chacun, résultat des 20 itérations, modifications d'API (signatures).
