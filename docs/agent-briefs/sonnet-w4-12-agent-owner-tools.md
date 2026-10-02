# w4-12 — Outils du propriétaire : onglet « Points focaux », maître des locations explicite, commande `delegation`, grille de prix signée

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT **version réduite W5** (sans maître) ; D9-bis pour la grille réelle
> **Groupe : W4c-2** (vague W4c) · prérequis : w4-11 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnerCli*' && python3 -m unittest discover -s tools/tests -p 'test_sign_prices.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non
> **Amendement (architecte, 2026-10-02)** : lire `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L3/L6, § 4). Il **prévaut**. L'app du point focal et la console lisent `sign=` dans la demande et **lient** toute production (`install=`) ; la grille de prix gagne l'article **`reemission-cle|0`** (réémission gratuite après réinstallation de la TV ou pour remplacer une clé compacte) pour que l'anomalie serveur « même appareil vendu deux fois en < 48 h » ne se déclenche pas. Dépend de `claude/sonnet-w4-02-fix` et `claude/sonnet-w4-11-fix`.

**Vague 4c · Effort M (≈ 2 j) · Statut PRÊT (après w4-11). BLOQUÉ partiel : D9-bis (montants) pour le fichier de grille réel ; l'agent livre un exemple `TEST` à 0.** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 5, § 6, § 10. Branche `claude/sonnet-w4-12`. Rapport : `docs/agent-reports/sonnet-w4-12.md`.

## Objectif
Le propriétaire crée, renouvelle et révoque des délégations depuis sa console téléphone ou l'outil de bureau ; le **maître des locations** devient un secret explicite (32 octets, créé une fois, dans le coffre de la console, exportable chiffré vers le bureau) enveloppé dans chaque délégation ; la grille de prix se signe hors ligne et s'importe/affiche dans les outils ; le registre rejoue les événements signés par les agents.

## Pourquoi (preuves)
- `android/ownerlib/.../ConsoleActivity.kt:116-118` (onglets « Activer », « Clé publique », « Journal »), `:193-194` (`RentalKeys.masterFrom(signer)`), `OwnerStore.kt:29-39` (coffre), `:54` (`publicLine` : liste de portées à compléter avec `DELEGATE`).
- `tools/activation-desktop/.../Desk.kt:36-66` (`ring()`, `events()`, `rentalMaster()`), `Cli.kt:81-95` (dispatch), `KeyFile.kt` (fichier de clé chiffré par phrase).
- `tools/trial-edition/trial_edition.py sign-catalog` (modèle de signature hors ligne ; fichier possédé par w2-13 : **ne pas le modifier**, créer `tools/prices/`).

## Fichiers possédés
`android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt`, `android/ownerlib/src/main/kotlin/castbridge/owner/OwnerStore.kt`, nouveau `android/ownerlib/src/main/kotlin/castbridge/owner/DelegationsTab.kt`, `C/owner/OwnerCli.kt`, `C/owner/PhoneConsole.kt`, `tools/activation-desktop/src/main/kotlin/castbridge/desktop/{Cli,Gui,Desk,KeyFile}.kt`, nouveau `tools/activation-desktop/src/main/kotlin/castbridge/desktop/DelegationStore.kt`, `tools/activation-desktop/src/test/**`, nouveaux `tools/prices/sign_prices.py`, `tools/prices/README.md`, `tools/tests/test_sign_prices.py`, `content/prices.json` (exemple, montants 0, champ `"warning": "TEST"`), tests `CT/owner/OwnerCliTest.kt`, `CT/owner/PhoneConsoleTest.kt`. **Hors zone** : `C/owner/Delegation.kt` & co (w4-11 : utiliser), `S/**` (w4-13/14), `R/**`, `backend/`, `tools/trial-edition/**`.

## Étapes
1. `OwnerStore` : ligne `master=<hex scellé>` dans `owner-vault.txt` (scellée par le même code, via `OwnerVault.seal` d'un second blob ou en concaténant à la graine : choisir le plus simple **sans** changer le format des coffres existants : un coffre sans `master=` en crée un à la première ouverture de l'onglet, après confirmation « Créer le secret des locations (irréversible, à sauvegarder) ») ; `rentalMaster(): ByteArray` ; `exportMaster(phrase): String` (format `KeyFile`) ; `publicLine()` ajoute `DELEGATE`.
2. `DelegationsTab` (console) : liste des délégations émises (journal `owner-journal.tsv`, action `delegation`), formulaire : coller la **ligne publique de l'agent** (`kid=… pub=… x25519=…`, produite par l'app agent w4-13), nom, validité (30/90/180 j), `maxKeyDays` (choix 30/90/365), `maxRentalDays` (0 ou 60), `maxSales`, bouquets (catalogue du serveur déjà importé : cases à cocher ; « tout »), bascule « Locations autorisées » (enveloppe le maître) → `Delegation.issue(signer, …)` → affichage/partage/QR (`Partager`, fichier `delegation-<name>.txt`) ; bouton « Révoquer » → `RevocationNotice.issue(signer, now, RevocationState(keys = {agentKid}))` → partage du `cbr1` (le propriétaire le pousse à la TV par Bluetooth/USB et l'importe sur le serveur).
3. Bureau : `delegation --agent FICHIER_LIGNE_PUBLIQUE --nom … --jours 90 --cle-max-jours 90 --location-max-jours 60 --ventes-max 200 --bouquets b1,b2|tout [--sans-locations]`, `delegation revoquer --kid …`, `delegations` (liste), `maitre importer FICHIER` / `maitre exporter FICHIER` ; `Desk.ring()` inclut les délégations de `delegations/*.txt` (`KeyRing.withDelegated`) pour `registre etat` ; `Gui` : panneau « Points focaux » minimal (coller, générer, copier).
4. `OwnerCli`/`PhoneConsole` : mêmes commandes (tests JVM).
5. `tools/prices/sign_prices.py` : lit `content/prices.json` (`{"currency":"XAF","prices":[{"item":"cle-production","days":90,"amount":0},…]}`), vérifie que chaque `loc-<b>` a la durée `rentalDays` du `TRIAL-MANIFEST.json`, produit le texte canonique du § 6 et signe avec la clé des mises à jour (même chargement que `sign-catalog` : importer la fonction si elle est importable, sinon recopier 10 lignes avec la référence) → `content/prices.signed.json` (`{"generatedAt","currency","prices":[…],"signature","keyId"}`) ; `--check` revérifie. Tests Python sur une clé de test.
6. Outils : « Mettre à jour la grille depuis le serveur » (`GET /api/v1/catalog/prices`, même mécanique que le catalogue : `ServerCatalogStore`/`OwnerStore.saveCatalog`) ; affichage des prix dans l'onglet « Activer » (information).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.OwnerCliTest' --tests 'castbridge.core.owner.PhoneConsoleTest'   # vert
cd android && gradle --offline :activation-desktop:test   # vert (ou via tools/core-harness)
python3 -m unittest discover -s tools/tests -p 'test_sign_prices.py'   # vert
python3 tools/prices/sign_prices.py --check content/prices.signed.json 2>&1 | head -2   # « TEST » visible, 0 montant non nul
grep -n 'DELEGATE' android/ownerlib/src/main/kotlin/castbridge/owner/OwnerStore.kt   # 1
cd android && gradle --offline :ownerlib:compileDebugKotlin   # si SDK
```

## Cas limites
- Coffre existant sans maître : création à la demande ; **jamais** silencieuse ; sauvegarde conseillée (export) affichée une fois.
- `period` antérieure à la bascule (fenêtre d'essai émise avec `masterFrom`) : les outils gardent `masterFrom(signer)` en repli pour `--periode` < date de bascule (constante `RentalKeys.MASTER_SWITCH_MS`, à définir **ici** dans `Desk`/`OwnerStore`, pas dans `RentalKeys` hors zone).
- Grille sans l'article demandé : l'outil affiche « non vendable par un point focal » mais le propriétaire peut toujours émettre (il n'est pas borné par la grille).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant réel, aucun numéro, aucun nom ; ne pas modifier `tools/trial-edition/**` ; ne pas donner `DELEGATE` à la clé serveur (ni dans `publicLine` du serveur) ; français.

## Rapport
`STATUT: BLOQUÉ` (D9-bis) + ce qui est livré ; formats de fichiers (`delegation-*.txt`, `prices.signed.json`) ; question au propriétaire : liste des durées de clé à vendre (30/90/365 ?) et montants.
