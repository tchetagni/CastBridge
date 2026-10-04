# w22-04 — Service de jeu : salles misées (NDEM, MBOKO) avec bons de blocage `cbe1` vérifiés par clé publique, partage par siège, résultat `cbr1` signé par une clé dédiée, dépôt des résultats, abandon au drain, interrupteur d'exploitation
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (isolement : aucun identifiant du grand livre ; conservation ; une clé privée nouvelle seulement) · statut : **ATTEND w22-03 et w20-04b**
> **Groupe : W22-N1** (ordre 2 bis) · prérequis : w22-03 et w20-04b (siège relais, `relayedBy`) fusionnés · porte : `:server-play:test` + `:core:test --tests 'castbridge.core.quiz.online.*'` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5-2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 3.3 option B et règles 1-9, § 3.4). Branche `claude/w22-04-salles-misees`. Rapport : `docs/agent-reports/sonnet-w22-04.md`.

## Objectif (autonome)
`castbridge-play` doit arbitrer des parties dont chaque TV a **bloqué** sa mise au grand livre de l'API, **sans** jamais parler à l'API ni détenir d'identifiant du grand livre : il vérifie le bon de blocage `cbe1` (clé **publique** de l'API) joint par la TV à `create`/`join`, partage la cagnotte par siège avec `Pot.split` (`android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt:54-85`), agrège par blocage, signe le résultat `cbr1` avec **sa** clé dédiée, l'envoie aux TV de la salle et le dépose dans son volume `play-state`.

## Fichiers possédés
- **Nouveaux** : `server-play/src/main/kotlin/castbridge/play/stake/{EscrowGate,ResultKey,ResultSpool,StakeSettings}.kt` ; tests `server-play/src/test/kotlin/castbridge/play/stake/{StakedRoomLoopbackTest,EscrowGateTest,ResultSpoolTest,StakeDrainTest}.kt` ; `tools/wallet/fixtures/cbr1-*.txt` (résultats produits par le test de boucle, clés de test de `tools/wallet/wallet-vectors.json`, pour le test de règlement de w22-05) ; `tools/play/gen-result-keypair.sh` (modèle : `tools/play/gen-ticket-keypair.sh`).
- **Zone additive** : `server-play/src/main/kotlin/castbridge/play/PlayConfig.kt` (`CASTBRIDGE_PLAY_WALLET_PUBKEY`, `_2`, `CASTBRIDGE_PLAY_RESULT_KEY_FILE`, `CASTBRIDGE_PLAY_STAKES` = `on` par défaut ; ajoutés à `ENV_NAMES`) ; `RoomRegistry.kt` (`create`/`join` : champ `escrow`) ; `PlayServer.kt` (drain ⇒ ABORT) ; `android/core/src/main/kotlin/castbridge/core/quiz/online/{ServerRoom,PlayProtocol,PlayCodec,PlayReason}.kt` (champs additifs `stake{cur, per}`, `escrow`, message serveur `result{token}` ; motifs `STAKE_ESCROW_REQUIRED`, `STAKE_ESCROW_INVALID`, `STAKE_MBOKO_PRODUCTION_ONLY`, `STAKES_SUSPENDED`) ; `server-play/src/test/kotlin/castbridge/play/NoSecretsTest.kt` (autoriser **nommément** `CASTBRIDGE_PLAY_RESULT_KEY_FILE`, rien d'autre) ; `docs/PLAY-PROTOCOL.md` (section « Salles misées »).
- **Interdit** : `backend/**`, `R/`, `S/`, tout appel réseau sortant du service.

## Spécification
1. `create` avec `stake{cur, per}` : refusé si `CASTBRIDGE_PLAY_STAKES=off` (`STAKES_SUSPENDED`), si `per` hors bornes (NDEM 1..1 000, MBOKO 1..100), si `escrow` absent ; `EscrowGate.check(cbe1)` : signature (une des deux clés publiques), `aud`, `exp`, `id == HostRights.identity` de la connexion, `cur`/`per` = ceux de la salle, `eid` jamais vu (ensemble borné, durée de vie de la salle) ; MBOKO ⇒ `HostRights.edition ∈ {PROD, GRACE}` sinon `STAKE_MBOKO_PRODUCTION_ONLY`. `join` d'une salle misée : mêmes contrôles, un `cbe1` par TV.
2. `ServerRoom` : mode misé = **Duel seulement** ; mémorise par TV (siège relais ou hôte) son `eid` et son `k` ; au départ, misent les sièges présents de chaque TV dans la limite de `k` (hôte télécommande compris) ; fin normale ⇒ `Pot.split` par siège sur la cagnotte `per × sièges misants`, puis somme par `eid` ; abandon (hôte perdu ≥ 60 s, salle fermée avant la fin, drain) ⇒ `ABORT`, `pay = used`. Les TV sans siège misant ⇒ `used = 0, pay = 0`.
3. `ResultKey` : Ed25519 lue de `CASTBRIDGE_PLAY_RESULT_KEY_FILE` (absente ⇒ salles misées refusées, `STAKES_SUSPENDED`, le reste du service marche) ; `PlayResult.sign` de w22-03 ; `rid` = 128 bits aléatoires.
4. Envoi : message `result{token: cbr1}` à chaque connexion de la salle à la fin ; `ResultSpool` écrit `play-state/results/<rid>.cbr1` (écriture atomique, ≤ 7 j, ≤ 10 000 fichiers, plus anciens purgés).
5. Drain (`PlayServer`) : avant l'arrêt, chaque salle misée en cours produit son `ABORT`, envoyé et déposé.
6. Bande passante : `cbr1` ≈ 250 o + 90 o par TV ; aucun `state` supplémentaire (la cagnotte entre dans la vue existante : `stake`, `pot`).

## Critères d'acceptation (JVM ; mutations au rapport)
- `StakedRoomLoopbackTest` (vrai `PlayServer`, port aléatoire, `CASTBRIDGE_PLAY_WEB=0`, fixtures de ticket/activation existantes, clés de test de `wallet-vectors.json`) : TV A (production) crée une salle **NDEM** 20/siège avec `k=2`, TV B (essai) rejoint avec `k=2`, 2 téléphones relayés par TV, Duel de 3 questions ⇒ chaque TV reçoit le même `cbr1`, vérifié par `PlayResult.verify`, Σ pay = Σ used = 80 ; puis salle **MBOKO** : TV B (essai) refusée `STAKE_MBOKO_PRODUCTION_ONLY`, TV C (production) acceptée ; les `cbr1` produits sont écrits dans `tools/wallet/fixtures/`.
- `EscrowGateTest` : `cbe1` signé par une clé inconnue, expiré, d'une autre identité, `eid` rejoué, `cur` différent ⇒ refus avec motif (mutation : ne pas comparer l'identité ⇒ un test échoue).
- `StakeDrainTest` : drain pendant une partie misée ⇒ `ABORT` déposé, `pay = used` pour chaque blocage.
- `NoSecretsTest` : seule la variable de la clé de résultat est admise ; **aucune** variable ni configuration du service ne contient d'URL ou d'identifiant de l'API portefeuille (test de source sur `PlayConfig`).
- `k=3` déclaré avec 2 sièges présents ⇒ `used = 2 × per` (le reste sera rendu par l'API).

## À ne pas faire
- Appeler l'API ; détenir la clé privée « portefeuille » ; régler ou rembourser quoi que ce soit (le service **décrit** un résultat, l'API règle) ; changer le comportement des salles non misées.
