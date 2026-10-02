# w4-13 — CastBridge (téléphone) : mode « Point focal » (clé de l'agent, délégation, vente, émission, scellement, livraison)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT **version réduite W5** (pas de scellement ni livraison de lots) ; D7 contact
> **Groupe : W4c-2** (vague W4c) · prérequis : w4-11 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SaleFlow*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 4c · Effort L (≈ 4 j) · Statut PRÊT (après w4-11 ; w4-14 en parallèle sur des fichiers disjoints ; D7 pour le contact affiché, sinon `BuildConfig.OWNER_CONTACT` vide).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 1, § 2, § 5, § 7. Branche `claude/sonnet-w4-13`. Rapport : `docs/agent-reports/sonnet-w4-13.md`.

## Objectif
Dans CastBridge (téléphone), un mode « Point focal » : création de la clé de l'agent (coffre à code), import de la délégation (QR/texte/fichier) vérifiée contre les clés du propriétaire, préparation (catalogue, grille, lots), **vente hors ligne** chez le client (lecture de la demande v2 par Bluetooth / Wi-Fi / QR / collage, choix de l'article, encaissement, émission avec ticket, scellement des lots, livraison, reçu), livraison différée (48 h), renouvellement. Le journal lui-même (fichier, synchronisation, écran des ventes) est w4-14 : ici on appelle `LedgerStore.append` (interface de w4-11) via un `FocalLedger` fourni par w4-14 (`S/focal/LedgerFile.kt`) ; tant qu'il n'existe pas, `MemoryLedgerStore`.

## Pourquoi (preuves)
- `S/MainActivity.kt:101-108` (entrée cachée « Super administration », boutons « Activer la TV », « Locations ») ; `android/sender/build.gradle.kts:30` (`:ownerlib` déjà dépendance) ; `S/ActivateTvActivity.kt:78-115` (envoi Bluetooth/Wi-Fi d'une clé, `TvBluetooth.with`, `ActivationSend.sendLan`), `:158-175` (lecture de la demande) ; `S/LotsRuntime.kt` (lots téléchargés du serveur, transport Bluetooth) ; `S/RentalDeliveryActivity.kt:48-92` (livraison de lots scellés `.lot` + catalogue ; devient « avancé ») ; `C/lots/RentalDelivery.kt:96-119`.
- `S/agent/**` existe déjà (assistant IA) : **ne pas y toucher**, paquet `castbridge.sender.focal`.
- `grep -n TRUSTED_KEYS android/sender/build.gradle.kts` : vérifier ; absent ⇒ l'ajouter (même mécanique que `android/receiver/build.gradle.kts:24-37`, clés **publiques** du propriétaire) : sans elles, l'app ne peut pas vérifier une délégation.

## Fichiers possédés
Nouveaux `S/focal/FocalActivity.kt`, `S/focal/FocalStore.kt` (clé de l'agent : réutiliser `OwnerVault`/`UnlockGuard` comme `OwnerStore`, **fichier distinct** `focal-vault.txt` ; clé X25519 de l'agent `agentx` dérivée d'une seconde graine scellée), `S/focal/FocalScreens.kt`, `S/focal/FocalSealer.kt` (scelle les lots d'un bouquet avec `RentalKeys.seal` à partir des fichiers de `LotsRuntime`), `S/focal/FocalDelivery.kt` (ticket + lots, BT/Wi-Fi, réutilise `TvBluetooth`, `ActivationSend`, `RentalDelivery`), `S/focal/PendingSales.kt` (48 h), nouveau `C/sales/SaleFlow.kt` (machine d'états pure : `DRAFT → PAID → ISSUED → DELIVERED | PENDING | REFUNDED`, règles de renouvellement, choix `period`), `CT/sales/SaleFlowTest.kt` ; modifiés `S/MainActivity.kt` (entrée « Point focal » visible seulement si `FocalStore.hasVault()`, sinon dans le menu « ⋯ » : « Devenir point focal »), `android/sender/build.gradle.kts` (`TRUSTED_KEYS`), `S/RentalDeliveryActivity.kt` (titre « Livraison avancée »). **Hors zone** : `S/focal/Ledger*.kt`, `S/focal/ReceiptShare.kt`, `C/sales/LedgerSync.kt`, `C/sales/LedgerFile.kt` (w4-14), `C/owner/**`, `C/sales/{SalesLedger,PriceGrid,Receipt}.kt` (w4-11), `R/**`, `backend/`.

## Étapes
1. `FocalStore` : création (code ≥ 12), déverrouillage avec `UnlockGuard`, `publicLine()` = `kid=… pub=… x25519=…` (à donner au propriétaire), stockage de la délégation vérifiée (`delegation.txt`), du catalogue, de la grille (`SafeFile`), verrouillage automatique (2 min, comme la console).
2. Import de la délégation : texte collé / fichier / QR (scanner : si `zxing` est déjà dans `:sender` l'utiliser ; sinon **pas** de scanner dans ce cahier, le dire) → `Delegation.verify(token, KeyRing(TRUSTED_KEYS), …)` → doit nommer **cet** agent (`agent == kid`) → `openMaster` avec `agentx` ⇒ « Locations autorisées » ; affichage : validité, plafonds, bouquets, ventes restantes (compteur local depuis le journal).
3. `SaleFlow` (cœur, pur) : entrées = délégation, grille, catalogue, demande d'appareil (`DeviceRequest`), état de la TV (`GET /api/rental`, `GET /api/activation` si joignable), article choisi, montant encaissé ; sorties = `IssueSpec` à émettre (clé : `usageDays` ≤ `maxKeyDays` ; location : `RentalSpec(loc-<b>, [b], rentalDays du catalogue, period = renouvellement ou null)`), entrée `SALE` à journaliser, refus en français (hors délégation, grille absente, ventes épuisées, délégation expirée, TV sans `install=` ⇒ « mettez CastBridge-TV à jour », lot libre ⇒ `RentalPolicy.refusal`). Tests exhaustifs.
4. `FocalActivity` (Compose) : « Préparer » (synchroniser catalogue/grille/lots quand en ligne : `ServerBundleCatalog`, `PriceGrid`, `LotsRuntime`), « Vendre » (1. lire la TV : Bluetooth `deviceInfo()`, Wi-Fi `/api/activation/request`, coller ; 2. article + prix ; 3. « Espèces reçues : … XAF » ; 4. Émettre ; 5. Livrer ; 6. Reçu), « En attente » (livraisons différées), « Ma délégation », « Mon journal » (écran de w4-14 : lien).
5. Émission : `LicensedIssuer(signer = clé agent, scopes = délégation.scopes, ring = TRUSTED_KEYS + délégation, events = registre local de l'agent (`RegistryStore` fichier), save)` ; `TicketedActivation.encode(delegationToken, issued.token)` ; `FocalSealer.sealBundle(bundle, rentalKey)` ⇒ fichiers `.lot` scellés dans `files/focal/sealed/<contrat>/` ; `FocalDelivery` : activation (Bluetooth `sendActivation(ticketLine)` ou `POST /api/activation/install`), puis `RentalDelivery.deliver(contrat, catalogueJson, lots)`.
6. Journal : `SALE` **avant** la livraison (argent déjà pris) via `LedgerStore.append` ; `NOTE` « réémission » en cas de réémission ; `REFUND` seulement sur une vente `PENDING` non livrée (après livraison : message « demandez au propriétaire »).
7. Reçu : `Receipt.text(entry, name, BuildConfig.OWNER_CONTACT, grid)` → partage (WhatsApp/SMS) ; l'écran reçu est dans w4-14 (`ReceiptShare`) : appeler sa fonction si présente, sinon `Intent.ACTION_SEND` direct.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.sales.SaleFlowTest'   # vert, ≥ 15 cas
cd android && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
grep -rn '237\|XAF [0-9]\|WhatsApp :' android/sender/src/main/kotlin/castbridge/sender/focal   # 0 hit (rien en dur)
grep -n 'TRUSTED_KEYS' android/sender/build.gradle.kts   # ≥ 1
```
Observable (émulateur TV + téléphone, délégation de test émise par l'outil de bureau avec une clé de test) : vente d'une clé de 30 j → TV activée « Point focal : test » → vente d'une location de bouquet → lots livrés et lisibles → clé de 400 j refusée par `SaleFlow` **et** par la TV (test de la TV : w4-15).

## Cas limites
- Téléphone sans réseau depuis des semaines : vendre reste possible tant que la délégation est valide ; la grille/catalogue gardés servent ; message « Synchronisez dès que possible (N ventes non envoyées) ».
- Même TV vendue deux fois en 48 h : avertissement (pas un blocage).
- TV en mode réduit (W4-B) : l'écran le dit (« clé terminée le … ») et propose « Renouveler ».
- Délégation expirée : mode lecture seule (journal, synchronisation), aucune vente.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun secret, montant ou numéro en dur ; ne pas toucher `S/agent/**` ; ne pas donner à l'app agent une clé du propriétaire ; ne pas contourner `SaleFlow` (toute règle de vente est dans le cœur, testée) ; français ; « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT`, parcours testé sur émulateur, dépendances constatées (zxing, `TRUSTED_KEYS`), signatures exposées à w4-14.
