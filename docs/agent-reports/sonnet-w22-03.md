STATUT: TERMINÉ (audit Opus obligatoire à lancer)
CAHIER: w22-03 · MODÈLE: sonnet · BRANCHE: claude/w22-03-formats-signes · COMMIT: voir `git log -1`
JETONS: inconnu
PORTE: `:core:test --tests 'castbridge.core.wallet.*'` → VERT (30 tests)
SUITE COMPLÈTE: `:core:test :server-play:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` (commande du coordinateur, -q) → VERT (aucune sortie d'échec, code 0 ; sortie filtrée par grep) ; écarts : aucun (tests instables non rencontrés)
FICHIERS: android/core/src/main/kotlin/castbridge/core/wallet/{WalletFormats,Snapshot,EscrowTicket,PlayResult,Voucher,VoucherCode,WalletCache,WalletReason,WalletView}.kt ; android/core/src/test/kotlin/castbridge/core/wallet/{WalletFormatsTest,VoucherCodeTest,WalletCacheTest,WalletViewTest,WalletVectorsTest}.kt ; tools/wallet/wallet-vectors.json ; docs/agent-reports/sonnet-w22-03.md ; docs/agent-briefs/SONNET-WAVE22-INDEX.md (ligne w22-03)
ROUGE (R1) : premier test écrit avant tout code, `:core:test --tests 'castbridge.core.wallet.*'` → `e: …/wallet/WalletFormatsTest.kt:29:9 Unresolved reference 'Snapshot'.` puis `:35:25 Unresolved reference 'Verdict'.` (compilation des tests en échec). VERT ensuite.
CHOIX :
- Format commun `<préfixe>.<b64url charge>.<b64url signature>`, signature sur `<domaine>\n<préfixe>.<charge b64url>` (ASCII), comme `cbp1` ; vérification par `Ed25519.verify` du cœur et `KeyRing`/`TrustedKey` existants (aucune KeyScope ajoutée : un anneau SÉPARÉ par usage portefeuille / résultat / bons).
- Lecture stricte : base64url canonique sans bourrage, UTF-8 valide, réécriture JSON compacte identique octet pour octet (refuse espaces, clés en double, zéros de tête, décimaux), clés exactes, types exacts. Ordre : forme → clé connue/non révoquée → signature → champs/bornes.
- Seul `PlayResult.sign` signe en production (service de jeu) ; `cbw1`/`cbe1`/`cbv1` ne se signent que dans les tests (`TestMint`). `sign` refuse un résultat incohérent ou un `kid` ≠ signataire. ABORT ⇒ used = pay = 0 (rendu intégral), imposé par `check()`.
- Bon `cbv1` : signature sur `castbridge-wallet-voucher-v1\n` + 27 octets ; index de clé → `kid` par `VoucherKeys` (compilé) ; valable jusqu'à la fin (UTC) du jour d'échéance ; nonce hex 20 car. ; 183 caractères en 37 groupes (dernier groupe 2+1), contrôle `Base32C.check` avec sel = rang du groupe (même règle que `GroupedText`) ; bits de bourrage non nuls refusés.
- `WalletCache` : fichier texte injecté (`S`/`P`/`N`), tout est re-vérifié à la lecture ; l'attente (`pendingNdem/Mboko`) est un champ séparé ; pas de méthode `credit`/`add` (`receiveVoucher`, `dropPending`).
- `WalletRefusal` (noms stables, pour les vecteurs Java) ≠ `WalletReason` (§ 7.6, textes) ; `WalletRefusal.shown` fait le lien. Le câblage `C/sync/Reason.kt` est laissé à w22-07.
- `+500 en attente` : monnaie omise pour NDEM seul, nommée pour MBOKO ou les deux.
- Vecteurs : générés par `WalletVectorsTest` (`WALLET_VECTORS_WRITE=1`), test de dérive + relecture DU FICHIER (anneaux reconstruits depuis les clés publiques du fichier) ; clés de test = graines 0x11/0x22/0x33/0x44 répétées, marquées TEST SEULEMENT.
SYMBIOSE: cap=aucune (cœur pur, w22-07 nommera la capacité) · proto=inchangé · reason=WalletReason (15 clés § 7.6), à brancher dans Reason.kt par w22-07 · deux écrans=non applicable ici (w22-07)
NON FAIT / À VALIDER :
- Mutation « oublier le domaine dans la signature » : NON exécutée (modification temporaire du code de signature refusée par le système de permissions). Preuve équivalente par les tests : `domainSeparationIsEnforced` et les vecteurs `wrong-domain` / `no-domain` / `wrong-prefix-cbp1` sont refusés ; à rejouer par l'auditeur si voulu.
- Parité côté Java (w22-01) : non vérifiée (backend/ interdit). Points à aligner : charge JSON compacte sans espace, message signé, signature voucher, calcul de cible, jour d'échéance UTC.
- Fuseau de « au 04/10 18:42 » : choisi par l'appelant (testé UTC et Africa/Douala).
- Aucun appareil, aucun réseau, aucune clé réelle.
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [ ] diff ≤ plafond (neufs seulement : ≈ 1 100 lignes de code + vecteurs) [x] un commit
