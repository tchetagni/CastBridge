STATUT: TERMINÉ
CAHIER: sonnet-w5-02-core2 (addendum W5 porte-jetons, partie cœur) · MODÈLE: sonnet · BRANCHE: claude/sonnet-w5-02-core2 (depuis claude/sonnet-w5-02-fix) · COMMIT: voir git log
PORTE: gradle :core:test --tests 'castbridge.core.tokens.*' → VERT (37 tests : Vectors 2, Reopen 9 neufs, Wallet 19, SyncPolicy 7)
SUITE COMPLÈTE: gradle :core:test → VERT (4 min 41) ; écarts : aucun

DÉCISIONS DE L'ADDENDUM
- J1 oui : `TokenGrant.fresh` (6e ligne canonique `fresh=0|1`, parseBody exige 6 lignes, ancien format 5 lignes = BAD_GRANT) ; OK n'accepte que fresh=0 (FRESH_REFUSED sinon), EMPTY/UNREADABLE que fresh=1 (NEEDS_FRESH sinon), NO_KEY = UNREADABLE.
- J2 oui : `WalletMark` (interface), `FileWalletMark` (4 lignes + mac HMAC kWallet, SafeFile), `InMemoryWalletMark` ; table de vérité complète dans `TokenWallet.refresh` ; `WalletCause` (none|lost|broken|foreign_chain) via `cause()` et `WalletSummary.cause`.
- J3 oui : `WINDOW_MAX_MS` = 72 h, `OPENER_WINDOW_MAX_MS` = 48 h ; `issue` refuse, `verify` rend BAD_GRANT au-delà.
- J4 : borne documentée dans la KDoc (aucun code propre au cœur).
- J5 (étape 4, côté TV) oui : `credit(fresh=1)` en EMPTY/UNREADABLE = reprise : mise de côté `wallet.txt.broken-<n>` + `wallet.txt.bak.broken-<n>` (5 conservés, renumérotés 1..5, le plus ancien effacé APRÈS succès), nouvelle chaîne 3 lignes, marque, `REOPENED` ; échec d'écriture = `WRITE_FAILED` + ancien fichier remis ; `TokenSync.Applied.reopened`, ouverture traitée d'abord. Étapes 1-3 et 5 = serveur.
- J6, J7 : serveur seulement (3 vecteurs `type: "server"` ajoutés, ignorés par le cœur).

CHOIX (écarts et précisions à valider)
- `granted=` gagne un 3e champ (fp du bon d'ouverture) : sans lui, la compaction effaçait la 1re ligne `grant=` et la comparaison de la marque donnait FOREIGN_CHAIN à tort. Les miroirs Java/Python doivent le parser.
- `parse` exige désormais au moins un `grant=` ou un `granted=` (une chaîne commence toujours par son bon d'ouverture).
- Échec d'écriture de la MARQUE pendant une reprise : retour arrière complet (WRITE_FAILED), pour ne pas laisser une ancienne marque valide contredire le nouveau fichier (sinon FOREIGN_CHAIN au contact suivant). Marque absente avec fichier valide : recréée (chain=1, note `markNote` = « marque absente : recréée »).
- `reopen` remet à 0 la mémoire d'accusé (`ackedMem`) partagée : l'accusé de l'ancienne chaîne ne doit pas masquer les dépenses neuves.
- Signatures : `TokenWallet(file, keys, mark, ...)` (mark obligatoire), `TokenWallet.of(file, keys, mark, compactAbove)`, `credit(grant, fp, nowMs = now)`, `TokenSync.apply(reply, wallet, verify, nowMs)` (surcharge ; l'ancienne signature reste). `FileWalletMark(file, keys, installId)` : installId facultatif.
- Texte UNREADABLE_MESSAGE remplacé par celui du § 3 de l'addendum ; message EMPTY non ajouté (le TV w5-17 porte les textes).
- Fenêtres : vecteurs existants adaptés (fenêtre 7 j -> 48 h, « over-30-days » -> « over-72-hours »).

VECTEURS (tools/activation/tokens-vectors.json) : 54 -> 79 cas (+25 : 5 build-tokens, 7 tokens, 10 wallet, 3 server ; 14 de l'addendum couverts). Tous les jetons et lignes de fichier sont régénérés (corps à 6 lignes, premier crédit = bon d'ouverture) ; les attentes de comportement existantes sont inchangées sauf : premier crédit `OK` -> `REOPENED`, crédit sur illisible `UNREADABLE` -> `NEEDS_FRESH`. Nouveaux types d'étapes du rejeu : apply, deleteFile, markDelete, markWrite, markFlipMac, checkMark, copySave, copyRestore, snapshot, expectUnchanged, plantBroken, failWrites, allowWrites, expectFile ; `check` accepte `cause`.

RESTE À FAIRE
- w5-05 (Java/Python) : corps à 6 lignes, fenêtres 72 h / 48 h, `granted=` à 3 champs, nouveaux types/étapes de vecteurs, type `server` (à rejouer en w5-08).
- w5-08 (serveur) : tout § 4, 7 (émission fresh, premier bon fresh, refreshWindow, TokenReconciler.recover, limites J6, TOKEN_CHAIN_REPLAY/WALLET_LOST/...), migrations.
- w5-17 (TV/Android) : WalletKeys fournit FileWalletMark(`files/tokens/wallet.mark`, installId), TokenHub instancie TokenWallet(file, keys, mark) et expose cause(), textes § 3, QuizBoosts.charge(gameId, purchaseNo), passer `TvClock.now` à credit/apply. w5-10/12/16 : champs walletState/walletCause, rapport nul.

RISQUES : (1) un bon d'ouverture rejoué par root dans ses 48 h regagne <= 60 jetons (assumé, détecté au contact) ; (2) `FileWalletMark.read` rend null aussi sur mac faux : ne pas en faire un signal de sécurité ; (3) toute fenêtre d'installation > 72 h émise par un ancien serveur sera refusée (BAD_GRANT) ; (4) aucune API cœur n'existe pour la `walletState`/`walletCause` du rapport nul : le TV les lit via `state().wire` et `cause().wire`.
