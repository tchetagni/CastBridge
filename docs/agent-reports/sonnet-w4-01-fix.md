# sonnet-w4-01-fix : correctifs de l'audit Opus (branche claude/sonnet-w4-01-fix, issue de claude/sonnet-w4-01)

1. BLOQUANT corrigé : `BigInteger.TWO` (API 33) remplacé par `BigInteger.valueOf(2)` dans X25519.kt. Recherche dans android/ (main) : aucun autre `BigInteger.TWO/TEN`, `List.of`, `Map.of`, `getFirst/getLast` ; rien d'autre de suspect trouvé.
2. Boîte mixte : toute boîte contenant une partie v1 ET une partie `v2:` (dans les deux ordres) est `Unreadable` (aide `hasV2Part`, aussi pour l'ancien `openBox` obsolète). Cas ajouté au fichier v2 : `box-v1-then-v2-mixed-refused` (`expect.order = v1-first`) ; le fichier v1 n'est pas touché. Tests : RentalTest (ordre inverse, avec et sans coucher, ancien openBox) et RentalVectorsV2.
3. InstallKey : une erreur `unwrap` n'écrase plus jamais la clé. Un nouvel essai d'`unwrap` (panne transitoire du Keystore), puis lecture du `.bak` si le fichier principal se lit mais ne se déchiffre pas ; si tout échoue, `install.key` et `.bak` sont renommés `*.unreadable-<ms>` avant d'écrire une nouvelle clé. KDoc de PlainWrapper corrigée (ne promet plus 0600). Tests : panne unique (clé d'origine rendue, pas de nouvelle clé), panne longue (fichier gardé de côté, remis en place il se relit), repli sur `.bak`.
4. X25519Test : commentaire corrigé (vecteur e0eb7a7c… issu de la liste libsodium).

Suite : :core:test complet 2002 tests, 1 ignoré, 0 échec.

Risque : après une panne longue du wrapper, une nouvelle clé est créée (les locations en cours demandent une réémission) ; l'ancienne reste récupérable à la main. verify_vectors.py ne couvre pas le fichier v2 (204 contrôles v1 OK).
