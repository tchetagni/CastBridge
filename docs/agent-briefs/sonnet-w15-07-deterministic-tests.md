# w15-07 — Tests déterministes : ports liés directement, bornes, keep-alive, contenu local divergent
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** (effort faible) · escalade : aucune · statut : PRÊT
> **Groupe : W15-S1** (vague W15, tranche S1) · prérequis : w15-04 fusionné (borne de `giveUpAfter`) · porte : `cd android && for i in $(seq 1 20); do tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*DeviceTest*' --tests '*ReceiverTest*' --tests '*ParentalHttpTest*' --tests '*LearnLotsTest*' || exit 1; done`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 15 S1 (tests du cœur) · Effort S · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-07`. Rapport : `docs/agent-reports/sonnet-w15-07.md`.

## Objectif
Les quatre tests connus instables ou faussement rouges deviennent déterministes : plus de course sur le port, plus de boucle sans délai, plus de rouge quand le contenu local diverge du dépôt.

## Pourquoi (preuves)
- Q-02 `CT/DeviceTest.kt:23,27,31,59-64` : `ServerSocket(0).use { it.localPort }` puis `ReceiverServer.start(5000,false)` ⇒ TOCTOU ⇒ `BindException` quand deux Gradle tournent (mémoire du dépôt). Modèle correct : `android/sshd/src/test/.../TvSshServerTest.kt:22-23` (`port = 0`, `boundPort`).
- Q-01 `CT/ReceiverTest.kt:81-88` : relance `small` sur le **même** port, `sleep = {}`, `giveUpAfter` illimité ⇒ boucle serrée si la réponse n'est pas 507 (503 ou connexion refusée).
- Q-03 `CT/ParentalTest.kt:484-540` (`ParentalHttpTest`) : même motif de port ; deux POST de suite sur connexions keep-alive du JDK ; corps rejeté avant lecture (hyp.).
- `CT/LearnLotsTest.kt:63-70,72,115,420-426` : compare `content/learn/lots.json` et `docs/LEARN-REVIEW.md` (1 Mo) au contenu local ⇒ rouge dès qu'un worktree ou un fichier non commité diverge (10 échecs vus par plusieurs agents).
- `ReceiverServer` : vérifier si `start(port = 0)` expose un port lié (`boundPort`) ; sinon l'ajouter (additif).

## Fichiers possédés
`CT/DeviceTest.kt`, `CT/ReceiverTest.kt`, `CT/ParentalTest.kt` (classe `ParentalHttpTest` seulement), `CT/LearnLotsTest.kt`, `C/tv/ReceiverServer.kt` (**zone** `start()`/`boundPort` seulement, additif), `docs/HANDOFF.md` (ligne « Tests instables connus », § « Reprendre »). **Hors zone** : tout le reste.

## Étapes
1. `ReceiverServer.start(port = 0)` ⇒ `boundPort` (si absent) ; `DeviceTest`, `ReceiverTest`, `ParentalHttpTest` : port 0, lecture de `boundPort`, plus jamais de `ServerSocket(0).use`.
2. `ReceiverTest.insufficientStorageIsFatal` : `giveUpAfter = 20`, `sleep` injecté comptant les appels, délai global de 10 s (`assertTimeoutPreemptively` ou thread + `join(10_000)`), assertion : `Failed` avec « place » dans le texte.
3. `ParentalHttpTest` : client de test avec `Connection: close` et lecture complète du corps d'erreur ; les deux POST ne partagent pas de connexion.
4. `LearnLotsTest` : si `content/learn` diffère de `git ls-files` (fichiers non suivis ou modifiés, détectés par `git status --porcelain -- content/learn` quand `git` est disponible, sinon par la propriété `learn.content`), les 4 tests de concordance sont **ignorés avec un message** (`Assumptions.assumeTrue`) : « contenu local divergent : relancer `gradle :core:buildLearnLots --update` » ; en CI (arbre propre) ils restent actifs.
5. 20 passes vertes consécutives (porte).

## Critères d'acceptation (hors ligne)
Porte (20 passes) verte ; `grep -rn "ServerSocket(0).use" android/core/src/test` vide ; `grep -n "Int.MAX_VALUE" CT/ReceiverTest.kt` vide ; ligne HANDOFF « Tests instables connus » mise à jour dans le même commit.

## À ne pas faire
Ne pas affaiblir une assertion de produit ; ne pas supprimer un test ; ne pas toucher aux autres zones de `ReceiverServer`.

## Rapport
`STATUT`, les 20 passes (durée), diff de `boundPort`, tests ignorés et leur message.

## Ajout du coordinateur (2026-10-02, incident de blocage)
`ByteRelayTest.noServerMeansRefused` (castbridge.core.ssh.ByteRelay.join, Tunnel.kt:59 ← TcpTunnel.serve:158 ← TunnelTest.kt:90) peut BLOQUER indéfiniment (52 min observées, a tenu le verrou Gradle et bloqué tous les agents) : `Thread.join()` sans délai quand la connexion est refusée. À corriger : `join(timeout)` borné dans ByteRelay et un délai maximal par test (`@Timeout` JUnit ou `failOnTimeout`) sur toutes les classes réseau (TunnelTest, ReceiverTest.insufficientStorageIsFatal, DeviceTest.withoutDeviceReports501 : port libre réutilisé avec ServerSocket(0), ParentalHttpTest). Règle : aucun test de la suite ne doit pouvoir dépasser 60 s ; ajouter un test-garde qui échoue si une classe de test réseau n'a pas de délai.

## Ajout du coordinateur (2026-10-02, soir) : autre test instable observé
`MultiVolumeServerTest.uploadClientFailsFastWithAClearMessageForAFatDriveWithoutFallback` a échoué une fois (`java.net.SocketException`, MultiVolumeTest.kt:215) lors d'une suite complète lancée pendant que d'autres Gradle tournaient, puis a passé deux fois de suite seul : instabilité réseau (port local, connexions réutilisées), pas une régression. À rendre déterministe avec les autres (ports éphémères réservés, nouvelle tentative bornée, ou serveur en mémoire).
