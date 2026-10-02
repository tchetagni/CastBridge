STATUT: TERMINÉ
CAHIER: sonnet-w5-02 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w5-02 (depuis integration/agents d903c89) · COMMIT: voir git log
JETONS: inconnu
PORTE: gradle --offline :core:test --tests 'castbridge.core.tokens.*' → VERT (12 s) : 22 tests (TokenWalletTest 13, TokenSyncPolicyTest 7, TokensVectorsTest 2), 48 vecteurs
SUITE COMPLÈTE: gradle --offline :core:test → VERT (2116 tests, 0 échec) ; écarts : aucun
FICHIERS: android/core/src/main/kotlin/castbridge/core/tokens/{TokenGrant,TokenWallet,TokenPolicy,TokenSync,TokenVectors}.kt ; android/core/src/test/kotlin/castbridge/core/tokens/{TokenWalletTest,TokenSyncPolicyTest,TokensVectorsTest}.kt ; tools/activation/tokens-vectors.json ; docs/agent-reports/sonnet-w5-02.md. Hors zone : aucun.
CHOIX:
- Idempotence (QuizBoosts.kt) : `spend(item, cost, nowMs, op)` prend une clé d'opération ; même `op` = un seul débit, rejeu = même `seq` (`Ok.replayed`), autre article/coût = `OpConflict`. Le champ `op` est ajouté à la ligne `spend` (écart au § 3.4 f, nécessaire pour survivre au redémarrage). Aides : `TokenPolicy.opKey(gameId, item, n)`, `opPrefix`, `TokenWallet.spendCount(prefix)` ; un adaptateur `QuizBoosts` de test les utilise.
- Toutes les lignes (license, install, grant, acked, spend) sont chaînées et MACées (le § 3.4 f ne MACait que les dépenses) : sinon une ligne `grant` ajoutée gonflerait le solde. Compaction : reconstruit toute la chaîne (grants d'abord, puis `acked=<seq>|<total>`, puis dépenses restantes) ; `compactAbove` paramétrable (500 par défaut).
- Écriture durable AVANT retour `Ok` ; échec d'écriture = `WriteFailed`, rien débité ; état en mémoire remplacé seulement après écriture. Méthodes `synchronized`.
- Fichier principal seul fait foi ; `.bak` lu seulement si le principal n'existe pas ; fichier vide/illisible = UNREADABLE (pas porte-jetons neuf).
- Interface locale : `TokenSettings` recopiée (w5-01 non fusionné) ; `TokenItem` (mêmes noms que `quiz.Boost`, sans en dépendre) ; énumération locale `TokenRejection` (le `Rejection` de `C/owner` n'a pas BAD_GRANT, hors zone). Paramètre `clock` du constructeur omis (`nowMs` passé à `spend`, pas de code mort).
- Format `wallet.txt` (pour w5-20) : voir KDoc de `TokenWallet` ; lignes `<contenu>|<prev16>|<mac16>`, mac = 8 premiers octets de HMAC-SHA256, prev = 8 octets de SHA-256 de la ligne précédente complète.
NON FAIT / À VALIDER SUR MATÉRIEL: `WalletKeyProvider` Android (InstallKeyStore + SecretWrapper) = w5-17 ; miroirs Java/Python des vecteurs = w5-05.
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
POINTS POUR L'AUDIT OPUS:
1. Limite assumée : suppression du fichier ou retrait des dernières lignes = restauration d'un état antérieur, indétectable en local (vecteur `wallet-tail-truncation-not-detectable-locally`) ; seul le serveur le voit (TOKEN_REPLAY, § 6.5). Un fichier supprimé permet aussi de recréditer des bons encore dans leur fenêtre de 30 jours.
2. Après compaction, les clés d'opération des dépenses accusées sont oubliées : rejouer une opération déjà accusée n'est pas couvert.
3. `ack` n'est persisté que par la compaction ; sinon c'est le serveur qui renvoie `ackedSeq` à chaque contact.
4. MAC tronqué à 64 bits (format du cahier) ; comparaison en temps constant.
5. `TokenGrant.verify` : un corps hors schéma est BAD_GRANT (après KEY_NOT_ALLOWED) ; fenêtre > 30 j = BAD_GRANT ; `expiry` > 0 dépassé = WINDOW_CLOSED.
