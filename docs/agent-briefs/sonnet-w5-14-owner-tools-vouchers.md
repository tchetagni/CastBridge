# w5-14 — Outils du propriétaire : délégation sans maître (+ `confirmOrders`, `sellVouchers`, `maxConfirmXafPerDay`), retrait du secret maître, grille avec jetons, fabrication des lots de bons (`tools/vouchers`), vecteurs agents régénérés

**Vague 5c · Effort M (≈ 2 j) · Modèle : sonnet · Statut BLOQUÉ partiel (D-W5-1 montants/paquets, D-W5-2 dénominations : l'agent livre la mécanique avec des fichiers `TEST` ; D9-bis inchangé).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.1, § 3.4 (d), § 5.3, § 14 ; `SONNET-WAVE5-INDEX.md` « Changements aux cahiers w4 » (w4-11, w4-12, w4-17). Branche `claude/sonnet-w5-14`. Rapport : `docs/agent-reports/sonnet-w5-14.md`. Dépend de w4-11, w4-12 fusionnés.

## Objectif
(1) `Delegation` (cœur) : **retirer** `agentx=` et `master=` (et `wrapMaster/openMaster`), **imposer** `maxRentalDays = 0`, **ajouter** `confirmOrders=0|1`, `sellVouchers=0|1`, `maxConfirmXafPerDay=<n>` (optionnels, forme canonique) ; (2) console et bureau : plus de maître (`OwnerStore.master=`, `exportMaster`, `maitre importer/exporter`, `MASTER_SWITCH_MS` **supprimés** s'ils existent), formulaire de délégation avec les trois nouveaux champs, plus de « Locations autorisées » ; (3) grille : `tools/prices/sign_prices.py` accepte `jetons|<n>` et les réglages `tokens.*`, `content/prices.json` d'exemple (`TEST`, montants 0) ; (4) `tools/vouchers/make_vouchers.py` : lot signé + codes secrets + stock ; (5) `agent-vectors.json` **régénéré** (délégation sans maître) et les tests Kotlin/Java/Python alignés (Java/Python : w5-05 déjà passé ⇒ ici, mettre à jour les attentes des tests Java/Python **si** elles cassent : les fichiers `BT/licenses/AgentVectorsTest.java` et `verify_vectors.py` sont alors **autorisés en lecture seule** : signaler la casse dans le rapport pour le coordinateur, ne pas les éditer).

## Pourquoi (preuves)
- w4-11 `C/owner/Delegation.kt` (champs § 3 de W4-C, `wrapMaster/openMaster` via boîte v2) ; w4-12 `OL/{ConsoleActivity,OwnerStore,DelegationsTab}.kt`, `DK/{Cli,Gui,Desk,KeyFile,DelegationStore}.kt`, `tools/prices/sign_prices.py` ; `tools/trial-edition/trial_edition.py sign-catalog` (modèle de signature hors ligne, **ne pas modifier**) ; w5-01 `VoucherCode`, `VoucherBatch` (format à reproduire en Python) ; `C/owner/PhoneConsole.kt`, `C/owner/OwnerCli.kt`.
- P1 : l'agent ne loue plus ⇒ le maître n'a plus de raison d'exister dans la console.

## Fichiers possédés
`OL/{ConsoleActivity,OwnerStore,DelegationsTab}.kt`, `C/owner/{Delegation,OwnerCli,PhoneConsole}.kt`, `DK/{Cli,Gui,Desk,DelegationStore}.kt`, `tools/activation-desktop/src/test/**`, `tools/prices/**`, `content/prices.json`, nouveaux `tools/vouchers/make_vouchers.py`, `tools/vouchers/README.md`, `tools/tests/test_make_vouchers.py`, `tools/tests/test_sign_prices.py`, `tools/activation/agent-vectors.json`, `CT/owner/{DelegationTest,AgentVectorsTest,OwnerCliTest,PhoneConsoleTest}.kt`. **Hors zone** : `C/owner/DelegatedVerifier.kt` (lire ; s'il accepte `rental` ≤ `maxRentalDays`, la règle `0` suffit), `S/**`, `R/**`, `backend/`, `tools/trial-edition/**`, docs.

## Étapes
1. `Delegation` : retrait des champs et méthodes du maître ; `maxRentalDays` analysé mais **doit** valoir 0 (`BAD_DELEGATION` sinon) ; champs `confirmOrders`, `sellVouchers`, `maxConfirmXafPerDay` (0..10 000 000) en fin de corps, omis si absents ; KDoc : « un point focal ne loue pas (P1) ».
2. Console : `DelegationsTab` : cases « Peut vendre des bons », « Peut confirmer des commandes », champ « Plafond de confirmations par jour (XAF) » ; suppression de la bascule « Locations autorisées » et de toute création de maître ; `OwnerStore` : si une ligne `master=` existe dans un coffre, elle est **ignorée** et un avertissement propose de la retirer (« secret des locations obsolète ») ; `publicLine()` inchangée (`DELEGATE`).
3. Bureau : `delegation --confirmer-commandes --vendre-bons --plafond-confirmation-xaf N` ; retrait de `maitre …`, `--location-max-jours` ; `Gui` idem ; `Desk.rentalMaster()` reste **seulement** si `tools/rental-test` en dépend (KDoc « tests »), sinon retiré.
4. `sign_prices.py` : entrées `{"item":"jetons","qty":60,"amount":0}` et `"settings":{"tokens.expiryDays":0,"tokens.offlineGrantMax":60,"tokens.kidDailyDefault":0,"tokens.welcome":10,"tokens.cost.secondChance":5,"tokens.cost.extraJoker":2,"tokens.cost.swapQuestion":3}` ⇒ lignes `price=jetons|60|0`, `set=…` ; `--check` ; tests.
5. `make_vouchers.py --item jetons|60 --count 500 --expire 2028-10 --agent <kid|-> --key <KeyFile> --out DIR` : `secrets.token_bytes` → codes (format w5-01 : préfixe 2 Crockford du lot, 13 aléatoires, 1 contrôle **identique** à `Base32C.check(salt = 1)` : reproduire l'algorithme en Python et le prouver sur les vecteurs `voucher-code` de `shop-vectors.json`), serials, `batch-<id>.signed.txt`, `batch-<id>-CODES-SECRET.csv` (en-tête « À IMPRIMER PUIS DÉTRUIRE — NE JAMAIS COMMITTER »), `batch-<id>-stock.csv` ; `--check FICHIER` ; `.gitignore` : `*-CODES-SECRET.csv` (vérifier qu'il est déjà couvert par w1-08, sinon ajouter la ligne **dans `tools/vouchers/.gitignore`**, pas à la racine).
6. `agent-vectors.json` régénéré (`CASTBRIDGE_WRITE_VECTORS=1`), cas `master-wrap` supprimés, cas `delegation-rental-refused` (délégation avec `maxRentalDays=30` ⇒ `BAD_DELEGATION`), `delegation-shop-fields` ajoutés.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'   # vert
cd android && gradle --offline :activation-desktop:test   # vert
cd android && gradle --offline :ownerlib:compileDebugKotlin   # si SDK
python3 -m unittest discover -s tools/tests -p 'test_make_vouchers.py'; python3 -m unittest discover -s tools/tests -p 'test_sign_prices.py'   # verts
grep -rn 'master=' android/core/src/main/kotlin/castbridge/core/owner/Delegation.kt   # 0 hit
grep -rn 'rentalMaster\|exportMaster' android/ownerlib/src/main/kotlin   # 0 hit
python3 tools/vouchers/make_vouchers.py --item 'jetons|60' --count 3 --expire 2028-10 --agent - --key tools/activation/test-key.txt --out /tmp/v && ls /tmp/v   # 3 fichiers (clé de test seulement)
git status --porcelain | grep -c 'CODES-SECRET'   # 0
```

## Cas limites
- Délégation déjà émise **avec** `master=` (w4 fusionné avant W5) : la TV et le serveur l'acceptent encore (champs inconnus ignorés ? **non** : `master=` était connu ; décider : `Delegation.parse` **tolère** `master=`/`agentx=` en les ignorant jusqu'à expiration de ces délégations, et refuse de les **émettre**) ; le dire dans le KDoc et dans le rapport.
- Lot de bons de 5 000 codes : génération < 2 s ; signature unique.

## À ne pas faire
Pas de commit sur les branches partagées ; **jamais** un fichier `CODES-SECRET` dans le dépôt ; aucun montant réel ; ne pas modifier `tools/trial-edition/**`, `verify_vectors.py`, `BT/**` ; ne pas donner `DELEGATE` à la clé serveur ; français.

## Rapport
`STATUT: BLOQUÉ` (D-W5-1, D-W5-2) + ce qui est livré ; formats finaux (`delegation`, `prices.signed.json`, `batch-*.signed.txt`) pour w5-20/w5-23 ; casse éventuelle des tests Java/Python sur `agent-vectors.json` (pour le coordinateur).
