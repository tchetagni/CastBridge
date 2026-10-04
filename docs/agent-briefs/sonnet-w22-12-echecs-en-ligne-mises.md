# w22-12 — Échecs en ligne TV à TV avec mise (niveau 2) : salle d'échecs arbitrée par `castbridge-play` (TV seulement, téléphones relayés), mise NDEM/MBOKO par blocage `cbe1`, résultat `cbr1`
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (arbitrage serveur, pendule, abandon, conservation) · statut : **ATTEND la fin du niveau 1 et D-W22-10**
> **Groupe : W22-N2** · porte : `:core:test --tests 'castbridge.core.chess.*'` + `:server-play:test --tests 'castbridge.play.chess.*'`
> **Jauge : ≈ 700 k jetons entrée / 35 k sortie** (effort L, ≈ 3-4 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 1.6, § 3.3) ; règles W20 : `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (I-1…I-5). Branche `claude/w22-12-echecs-en-ligne`. Rapport : `docs/agent-reports/sonnet-w22-12.md`.

## Constat
Les échecs sont **locaux** (`android/core/src/main/kotlin/castbridge/core/chess/ChessRoom.kt` : la TV arbitre) ; le relais Internet `ChessRelayClient` (`ChessTransport.kt`) n'a **aucun serveur** (`docs/CHESS.md` § 6) et suppose un client direct, contraire à la règle « TV seulement ». Toutes les places d'une TV partagent un compte : une mise n'a de sens qu'**entre deux TV**.

## Fichiers possédés
- **Nouveaux** : `server-play/src/main/kotlin/castbridge/play/chess/{ChessServerRoom,ChessHub}.kt` (une salle = deux TV ; arbitre = `ChessGame`/`Position` du cœur, pendule serveur monotone ; chaque TV joue à la télécommande ou relaie **un** téléphone) ; tests `server-play/src/test/kotlin/castbridge/play/chess/**` ; `android/core/src/main/kotlin/castbridge/core/chess/ChessPlaySession.kt` (client TV par `PlayHttpTransport` de w20-05a).
- **Zone additive** : `PlayProtocol`/`PlayCodec` (messages `chessCreate`, `chessJoin`, `move`, `chessState`), `RoomRegistry` (même chaîne ticket + activation que le Quiz), `R/ChessActivity.kt` (`OnlineDrive` branché sur `ChessPlaySession`, derrière `quiz.online` ou un drapeau `chess.online`).
- **Interdit** : `backend/**` (le règlement est celui de w22-05, inchangé).

## Spécification
Mise : chaque TV bloque `per` (k = 1) ; gagnant : 100 % de la cagnotte ; nulle : chacun récupère sa mise (`pay = used`) ; abandon par perte de liaison ≥ 60 s : défaite de la TV perdue **si** l'autre est restée connectée, sinon `ABORT` ; abandon volontaire : défaite. MBOKO ⇒ deux identités de production. `cbr1` identique au Quiz (même clé, même dépôt, même collecteur). Budget 40 kbps : un coup ≈ 200 o, état ≈ 1 Ko.

## Critères d'acceptation
- Boucle locale : deux TV JVM jouent une partie complète (mat), une nulle, un abandon par coupure ; `cbr1` vérifié, Σ pay = Σ used ; coup illégal refusé par le serveur ; pendule tenue par le serveur.
