STATUT: TERMINÉ
CAHIER: correctifs de l'audit Opus de sonnet-w5-02 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w5-02-fix (depuis claude/sonnet-w5-02 ; fusion de integration/agents a052464 d'abord : la branche d'origine précédait le code de boosts corrigé)
PORTE: :core:test complet VERT (2182 tests, 0 échec) ; jetons : TokenWalletTest 19, TokensVectorsTest 2, TokenSyncPolicyTest 7 ; 54 vecteurs (48 avant)

CORRECTIFS
- (3) QuizBoosts.charge(b, gameId, purchaseNo) : clé d'achat explicite (gameId, article, numéro 1..maxPerGame), contrat documenté (jamais dérivé par l'implémentation ; rejeu = même résultat, aucun débit de plus). QuizGame passe boostCounts+1 ; gameId par défaut = UUID aléatoire ; QuizRoom : UUID (plus de « code-n » réutilisable). NoBoosts et le faux de QuizBoostsTest mis à jour (+ test du numéro 1,2 et des UUID distincts). L'adaptateur de TokenWalletTest exige un UUID, utilise purchaseNo et le rejeu est testé À TRAVERS l'adaptateur (EXTRA_JOKER #1 deux fois, #2 deux fois, #3 refusé, SECOND_CHANCE deux fois = même Ok, rejeu après redémarrage).
- (4) TokenWallet.of(file, keys) : une instance par chemin canonique ; en plus, toutes les instances (constructeur compris) partagent un ReentrantLock par chemin canonique, relisent le fichier sous verrou avant TOUTE opération (cache réutilisé seulement si texte et clé identiques) et prennent FileChannel.lock sur `<nom>.lock` (wallet.lock). Tests : deux instances à dépenses entrelacées (aucun débit perdu, rejeu d'une op faite par l'autre, bon STALE), 4 instances x 8 threads x 40 dépenses (33 réussies, solde 1), fabrique. Le verrou inter-processus n'est pas testé avec un vrai second processus.
- (5) Compaction : garde les dernières dépenses (KEEP_RECENT=64 au moins ou celles des dernières 24 h, paramétrables) ; seules les plus anciennes acquittées sont repliées. Rejeu et plafond par partie restent vrais ; une op plus ancienne que la fenêtre n'est pas dédupliquée (documenté).
- (6) Les lignes `grant` sont repliées en `granted=<dernier>|<somme>` (une seule, juste après install, MACée et chaînée, parse strict) à chaque compaction ; la numérotation (STALE) survit ; une altération rompt la chaîne.
- (7) KDoc de TokenReport : `mac` = empreinte opaque, le serveur ne peut pas la vérifier.
- (8) TokenSync.Reply.parse : `offlineAllowed` absent => false (fermé par défaut) ; test mis à jour.
- (9) Test : noms de TokenItem, plafonds et libellés == quiz.Boost.
- (11) Vecteurs ajoutés : bon signé avec fenêtre > 30 j (BAD_GRANT), cible non Device (WRONG_TARGET), `expiry` dépassée (WINDOW_CLOSED), fichier vide => UNREADABLE (étape `tamper empty`), rejeu après compaction, repli des bons.
- (1)(2) non touchés (conçus par Fable). (10) SafeFile non touché : constat à reporter, SafeFile.write (tmp + move) n'a pas de verrou propre ; il repose désormais sur le verrou du porte-jetons.

CHANGEMENTS DE VECTEURS VOLONTAIRES
- wallet-compaction-keeps-balance-and-sequence : paramétré keepRecent=1, keepWindowMs=0 (sinon rien n'est compacté) ; ses lignes exactes changent (ligne `granted=1|100`). Les 47 autres vecteurs initiaux inchangés. Nouveaux champs optionnels `keepRecent`, `keepWindowMs` ; nouvelle altération `empty` ; miroir serveur (w5-05) à mettre à jour (ligne `granted=`, étapes).

RISQUES
- Relecture et parse du fichier (<~60 Ko) à chaque appel, y compris balance() : coût faible, à surveiller sur TV lente.
- Effet de bord assumé de la relecture : un fichier supprimé pendant la vie de l'instance passe à EMPTY (au lieu de garder l'ancien état en mémoire) ; la politique de re-crédit relève des constats 1 et 2 (Fable).
- Format de fichier étendu (`granted=`) : w5-20 et le miroir serveur doivent l'accepter.
- Les clés de purchaseNo sont posées par QuizGame (boostCounts+1) : w5-17 doit brancher WalletBoosts avec TokenPolicy.opKey et un gameId UUID.
