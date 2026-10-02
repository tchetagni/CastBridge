# Addendum W5 : porte-jetons perdu ou illisible — bon d'ouverture, marque d'existence, fenêtre de 72 h, reprise par le serveur (2026-10-02)

Amende `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4 (c), § 3.4 (f), § 6.5, § 10 et les cahiers **w5-08** (serveur) et **w5-17** (TV). Source : audit Opus du cœur w5-02 (`claude/sonnet-w5-02`, commit `a4f1873`, `C/tokens/{TokenWallet,TokenGrant,TokenSync}.kt`). Conception seulement ; aucun secret, aucun code.

## 1. Ce que l'audit a établi (vérifié dans le code)
1. `TokenWallet.ensure()` : fichier **et** `.bak` absents ⇒ `EMPTY` ; `credit()` en `EMPTY` n'a ni licence, ni installation, ni `lastGrant` à comparer : **tout bon encore dans sa fenêtre (30 j) est recrédité**. Supprimer `wallet.txt` (root : stockage privé) rejoue jusqu'à 30 jours de bons ; restaurer une copie ancienne rejoue tous les bons livrés après elle. La borne « ≤ 60 » de § 6.5 est **fausse** : la borne réelle est Σ des bons émis dans la fenêtre. Le serveur le voit (`TOKEN_REPLAY`) mais après coup.
2. `UNREADABLE` est **définitif** : `credit()` refuse, aucune API ne rouvre le fichier ; le message promet pourtant « reconnectez le téléphone pour le resynchroniser ».

## 2. Décisions (D-W5-J1 à J7, renversables par le propriétaire)

**D-W5-J1 — Un porte-jetons ne s'ouvre QUE par un « bon d'ouverture » émis par le serveur.** Le corps du bon `type=tokens` gagne une **6ᵉ ligne canonique** `fresh=0|1` (ordre fixe : license, grant, amount, install, expiry, fresh ; couverte par la signature de l'enveloppe comme tout le corps ; `parseBody` exige 6 lignes ; vecteurs w5-02 régénérés). Règle de la TV, sans exception :
- état `OK` ⇒ accepte **seulement** `fresh=0` (mêmes contrôles qu'aujourd'hui : licence, installation, `grant` > dernier) ; un `fresh=1` reçu en `OK` ⇒ nouveau `CreditResult.FRESH_REFUSED` (rien n'est écrit, rien n'est renommé) ;
- état `EMPTY` ou `UNREADABLE` ⇒ accepte **seulement** `fresh=1` ; un `fresh=0` ⇒ nouveau `CreditResult.NEEDS_FRESH` ;
- `NO_KEY` ⇒ `UNREADABLE` comme aujourd'hui.
Conséquence : effacer le fichier (avec ou sans sa marque) **ne recrédite plus rien** : seul le serveur, qui tient le grand livre, peut rouvrir une chaîne, avec un solde qu'il calcule (§ 4). C'est la correction de fond ; la marque (J2) et la fenêtre (J3) sont des défenses en profondeur et de l'UX.
Le **premier** bon d'une installation (aucune ligne `token_grant` pour cet `install_pub`) est toujours `fresh=1` : première installation et reprise suivent le **même** chemin.

**D-W5-J2 — Marque d'existence : fichier `files/tokens/wallet.mark`, pas dans `install.key`.** Justification : `install.key` (w4-03) est écrit une fois, jamais réécrit (« never overwrite ») et son blob Keystore change à chaque réemballage ; y mettre un drapeau qui change à chaque ouverture de chaîne fragilise la clé pour un gain nul, car avec J1 la marque n'est plus un élément de sécurité. Contenu (`SafeFile`, ≤ 5 lignes) :
```
castbridge-wallet-mark-v1
install=<installId 16 hex>
opened=<fp 16 hex du bon d'ouverture de la chaîne courante>|<chain 1..n>|<ms>
mac=<HMAC-SHA256 16 hex sous kWallet des trois lignes précédentes>
```
`TokenWallet(file, keys, mark: WalletMark)` où `WalletMark` est une interface cœur `{ fun read(): Mark? ; fun write(m: Mark): Boolean }` (tests : mémoire). Table de vérité de `ensure()` :
| fichier | marque | état | note |
|---|---|---|---|
| absent | absente | `EMPTY` | première installation, ou réinstallation / « effacer les données » (tout est parti ensemble) |
| absent | présente et valide (mac, `install` = clé courante) | **`UNREADABLE`** (cause `LOST`) | fichier effacé : message de reprise, jamais « aucun jeton » |
| présent, chaîne valide | absente ou mac fausse | `OK` + note journal « marque absente : recréée » | tolérance : une écriture de marque ratée ne bloque pas |
| présent, chaîne valide | présente, `opened` ≠ fp du 1ᵉʳ `grant=` du fichier | **`UNREADABLE`** (cause `FOREIGN_CHAIN`) | copie d'une chaîne antérieure restaurée |
| présent, illisible / mac fausse | quelconque | `UNREADABLE` (cause `BROKEN`) | inchangé |
| marque dont `install` ≠ clé courante | — | marque **ignorée** (comme absente) | reste d'une installation précédente (impossible en pratique : effacée avec elle) |
Cas limites : **première installation** ⇒ `EMPTY` ; **réinstallation / effacer les données** ⇒ `install.key`, marque et fichier disparaissent ensemble ⇒ `EMPTY`, nouvelle clé, le serveur voit un `install_pub` neuf (w5-08 étape 4, inchangée) ; **clé USB** : la restauration de l'`activation` depuis le `Download` de la clé ne touche ni la marque ni le fichier (aucun effet) ; **clé d'installation illisible puis régénérée** (`InstallKeyStore.loadNote`) ⇒ la marque devient étrangère ⇒ ignorée ⇒ `EMPTY` : correct, les bons de l'ancienne installation sont `BAD_GRANT` de toute façon. Limite dite honnêtement : root peut effacer marque + fichier ⇒ `EMPTY` ; grâce à J1 il n'obtient alors **rien** sans un bon `fresh=1` du serveur.

**D-W5-J3 — Fenêtre d'installation : 72 h pour un bon ordinaire, 48 h pour un bon d'ouverture** (`TokenGrant.WINDOW_MAX_MS = 72 h` ; `verify` rejette `BAD_GRANT` au-delà ; `issue` refuse). Un bon expiré avant livraison n'est pas perdu : le serveur **renouvelle la fenêtre** du même `grant_seq` (nouvelle enveloppe, même numéro, `token_grant.envelope_text/issued_at/expires_at` mis à jour, état `ISSUED` inchangé) à chaque contact ; un numéro déjà crédité reste `STALE`. Le délai de relais par téléphone au Cameroun (achat en ville, TV au village) est couvert par ce renouvellement, pas par une fenêtre longue.

**D-W5-J4 — Borne de perte corrigée (§ 6.5).** Avec J1-J3, un attaquant **root** ayant rejoué le bon d'ouverture de sa chaîne courante (effacement de marque + fichier, dans les 48 h de ce bon) regagne au plus **son montant ≤ `offlineGrantMax` = 60 jetons par reprise**, et les reprises sont bornées (J6) : **≤ 120 jetons par installation et par 30 jours**, soit le prix de deux bons `jetons|60`, soit 24 « Secondes chances » à 5 jetons. Détection : au rapport suivant (`lastGrant` recule ou `seq` recule ⇒ `TOKEN_REPLAY`) ou au battement de cœur (`tokensSeq` recule, w5-10) ⇒ `offline_allowed = false`. Sans root : perte **nulle** (stockage privé, « effacer les données » régénère la clé).

**D-W5-J5 — Protocole de reprise (serveur seul émetteur ; clé serveur, portée `ISSUE_PRODUCTION`).**
1. **Signal.** La TV en `UNREADABLE`/`EMPTY` ne peut pas produire de rapport : la passerelle (w5-12) ou la TV en ligne (w5-16) envoie `POST /api/v1/shop/tokens/report` avec `report: null` et `walletState: "unreadable"|"absent"`, `walletCause: "lost"|"broken"|"foreign_chain"|"none"`, `installPub` (déjà dans la demande d'appareil). Le battement de cœur (w5-10) porte les mêmes deux champs.
2. **Serveur (`TokenReconciler.recover`)**, sous verrou de ligne `token_account` : si aucune `token_grant` pour cet `install_pub` ⇒ **première ouverture** (`chain_no = 1`, aucun reliquat). Sinon : les bons `ISSUED|DELIVERED` de cette installation passent `VOID` ; `remainder = Σ(amount − spent_reported)` ; `returned` selon J6 ; `available += returned` (ligne `token_ledger` `kind=ADJUST, ref=recovery:<id>, note=« reprise porte-jetons »`), `reserved −= remainder` ; les dépenses **non accusées** (jamais rapportées) sont **inconnues** du serveur : elles sont réputées **non faites** (le joueur garde ces jetons ; coût ≤ 60 par reprise, assumé dans J6). `token_install.chain_no += 1`, `last_seq_seen = 0`.
3. **Bon d'ouverture** : `amount = min(available, offlineGrantMax)` (≥ 1 sinon pas de bon et message « solde épuisé »), `fresh=1`, `grant_seq` = suivant pour `(licence, appareil)` (jamais remis à zéro), fenêtre 48 h, `token_grant.fresh = true, chain_no`. Si `offline_allowed = false` (fraude constatée) : **aucun** bon, message « porte-jetons suspendu » ; le propriétaire lève la suspension (w5-09).
4. **TV (`TokenWallet.credit` d'un `fresh=1` en `EMPTY`/`UNREADABLE`)** : (a) renomme `wallet.txt` → `wallet.txt.broken-<n>` et `.bak` → `wallet.txt.bak.broken-<n>` (`n` = 1..5 ; au-delà le plus ancien est effacé ; jamais de suppression du fichier courant sans renommage) ; (b) écrit la nouvelle chaîne `license`, `install`, `grant=<seq>|<amount>|<fp>` ; (c) écrit la marque `opened=<fp>|<chain>|<ms>` ; (d) rend `CreditResult.REOPENED` (nouveau) ; si (b) échoue ⇒ `WRITE_FAILED`, l'ancien fichier renommé est **remis en place** (rename inverse). `TokenSync.Applied` gagne `reopened: Boolean`.
5. **Après la reprise** : le rapport suivant porte `lastGrant` ≥ opener ⇒ le serveur reconnaît la chaîne courante ; `seq` repart à 1 **sans** `TOKEN_REPLAY` (J7).
Rejeu d'un bon `fresh=1` sur une TV `OK` : `FRESH_REFUSED` (sinon un bon d'ouverture rejoué effacerait les dépenses locales et doublerait le solde).

**D-W5-J6 — Limites anti-abus (serveur, par `install_pub` et par licence).**
| Règle | Valeur | Effet au-delà |
|---|---|---|
| Reprises automatiques avec restitution de reliquat | **2 / installation / 30 j** et **3 / licence / 90 j** | reliquat **confisqué** (`returned = 0`, `forfeited = remainder`), bon d'ouverture quand même émis sur `available` ; anomalie `TOKEN_RECOVERY_ABUSE` |
| Reliquat cumulé restitué | **> 120 jetons / licence / 90 j** | reprise **`HELD`** : bon d'ouverture émis sur `available` **sans** le reliquat ; le reliquat attend « Approuver / Refuser » du propriétaire (console w5-09, TOTP) |
| Reprise après `offline_allowed = false` | — | aucun bon ; levée manuelle |
| Nouvelle installation (clé régénérée) | règle w5-08 étape 4 **inchangée** (2 / 12 mois) | `TOKEN_REINSTALL_ABUSE` |
Contexte terrain : les jetons se vendent en espèces par bons de 60 chez un point focal ; une licence qui « perd » son porte-jetons plus de deux fois par mois est signalée dans la console, jamais à l'agent ; aucun geste n'est demandé au client (pas de preuve, pas de photo). Les enjeux restent ceux d'une partie de Quiz : 5 / 2 / 3 jetons par commodité, sans valeur d'échange.

**D-W5-J7 — `TOKEN_REPLAY` et chaînes.** Le serveur mémorise par installation la chaîne courante (`chain_no`, `chain_grant_seq` = `grant_seq` de son bon d'ouverture) et `last_seq_seen`. Rapport reçu : `report.lastGrant < chain_grant_seq` ⇒ rapport d'une **chaîne antérieure** (copie restaurée après reprise) ⇒ anomalie **`TOKEN_CHAIN_REPLAY`**, dépenses **non inscrites**, `offline_allowed = false` ; sinon `lastSpendSeq < last_seq_seen` ⇒ `TOKEN_REPLAY` (inchangé) ; `walletState = absent` alors que des bons ont été livrés ⇒ **`TOKEN_WALLET_LOST`** (information + compteur de reprises) ; `unreadable` ×3 ⇒ `TOKEN_WALLET_UNREADABLE` (w5-10, inchangé). Heartbeat w5-10 : `tokensSeq` est comparé **dans la chaîne courante seulement** (`tokensChain` ajouté au battement : `chain_no`).

## 3. Ce que voient les utilisateurs (textes français définitifs)
| Situation | TV (bandeau Boutique / Quiz, ≥ 28 px) | Téléphone (passerelle, après le relais) |
|---|---|---|
| `EMPTY`, jamais de bon | « Aucun jeton sur cette TV. Achetez des jetons depuis la Boutique. » | — |
| `UNREADABLE` (`LOST`, `BROKEN`, `FOREIGN_CHAIN`) | « Porte-jetons illisible. Vos jetons sont en sécurité au serveur : reconnectez le téléphone, ou la TV à Internet, pour le rétablir. » | « Le porte-jetons de la TV doit être rétabli : connectez-vous pour relayer. » |
| Reprise réussie (`REOPENED`) | « Porte-jetons rétabli : 42 jetons disponibles. » | « Porte-jetons de la TV rétabli (42 jetons). » |
| Reprise `HELD` | « Porte-jetons rétabli : 12 jetons disponibles. 30 jetons sont en cours de vérification. » | idem + « Le vendeur vous recontactera (reçu R-XXXX-XXXX). » |
| Reliquat confisqué (`ABUSE`) | « Porte-jetons rétabli : 12 jetons disponibles. » (rien de plus à l'écran) | « Porte-jetons rétabli (12 jetons). Pour toute question, contactez votre point focal. » |
| `offline_allowed = false` | « Porte-jetons suspendu : contactez votre point focal (code d'appareil XXXX-XXXX). » | même texte |
| `NEEDS_FRESH` (bon ordinaire reçu sur un porte-jetons vide) | rien à l'écran ; journal « bon ordinaire ignoré : porte-jetons à rétablir » | « La TV attend un bon d'ouverture : relayez à nouveau. » |
| `FRESH_REFUSED` | rien ; journal | rien |
Jamais de solde affiché supérieur à Σ grants − Σ spends locaux ; jamais de chiffre sans « jetons » ; jamais d'incitation à acheter dans ces messages.

## 4. Serveur : ce qu'il enregistre (additif ; migration `V<plus haut + 1>__token_recovery.sql`, soit **V63** si w5-06 prend V62 ; le cahier vérifie au lancement)
```
token_install   (id PK, license_id, device_code, install_pub CHAR(64), chain_no INT DEFAULT 1, chain_grant_seq INT NULL,
                 last_seq_seen BIGINT DEFAULT 0, last_report_at NULL, offline_allowed BOOL DEFAULT TRUE, wallet_state VARCHAR(16) NULL,
                 recoveries_30d INT DEFAULT 0, created_at, updated_at, UNIQUE(license_id, device_code, install_pub))
token_recovery  (id PK, token_install_id FK, license_id, at, cause ENUM(FIRST, LOST, BROKEN, FOREIGN_CHAIN, NEW_INSTALL),
                 voided_grants INT, remainder BIGINT, returned BIGINT, forfeited BIGINT, held BIGINT, opener_grant_id FK NULL,
                 status ENUM(AUTO, HELD, APPROVED, DENIED, NO_GRANT), reviewed_by NULL, reviewed_at NULL, note)
token_grant     + fresh BOOL DEFAULT FALSE, chain_no INT DEFAULT 1, window_renewals INT DEFAULT 0
token_ledger    (inchangé ; `kind=ADJUST`, `ref=recovery:<token_recovery.id>` pour une restitution, `ref=recovery-approve:<id>` pour une levée de HELD)
device heartbeat (w5-10) + tokensChain INT NULL, walletCause VARCHAR(16) NULL
```
Anomalies (`ShopAnomalies`) : `TOKEN_WALLET_LOST`, `TOKEN_CHAIN_REPLAY`, `TOKEN_RECOVERY_ABUSE`, `TOKEN_RECOVERY_HELD` (en plus de `TOKEN_REPLAY`, `TOKEN_OVERSPEND`, `TOKEN_WALLET_UNREADABLE`, `TOKEN_REINSTALL_ABUSE`). Console w5-09 : vue « Reprises » (liste, Approuver / Refuser, lever `offline_allowed`), tout sous TOTP et journal d'audit.

## 5. Vecteurs à ajouter (`tools/activation/tokens-vectors.json`, cœur w5-02 ; miroir Java w5-05 et Python)
1. bon `fresh=1` sur `EMPTY` ⇒ `REOPENED`, fichier à 3 lignes, marque `opened` = fp ; 2. bon `fresh=0` sur `EMPTY` ⇒ `NEEDS_FRESH`, rien d'écrit ; 3. bon `fresh=1` sur `OK` ⇒ `FRESH_REFUSED`, fichier intact ; 4. fichier effacé, marque valide ⇒ `UNREADABLE`/`LOST` ; `credit(fresh)` ⇒ `REOPENED`, `wallet.txt.broken-1` absent (il n'y avait pas de fichier) ; 5. fichier corrompu + marque ⇒ `BROKEN` ⇒ reprise ⇒ `wallet.txt.broken-1` présent, nouvelle chaîne ; 6. copie d'une chaîne antérieure + marque de la chaîne courante ⇒ `FOREIGN_CHAIN` ; 7. marque mac fausse + fichier valide ⇒ `OK`, marque recréée ; 8. marque d'une autre `install` ⇒ ignorée ⇒ `EMPTY` ; 9. corps à 5 lignes (ancien format) ⇒ `BAD_GRANT` ; 10. fenêtre 72 h + 1 ms ⇒ `BAD_GRANT` ; bon d'ouverture 48 h + 1 ms ⇒ `BAD_GRANT` ; 11. six `.broken-<n>` ⇒ seuls 5 restent, le plus ancien effacé ; 12. échec d'écriture pendant la reprise ⇒ `WRITE_FAILED`, ancien fichier remis en place, état inchangé ; 13. `TokenSync.apply` avec un `fresh=1` et deux `fresh=0` ⇒ `reopened=true`, `credited=2` (ouverture d'abord, par numéro croissant) ; 14. rapport serveur : `lastGrant < chain_grant_seq` ⇒ `TOKEN_CHAIN_REPLAY` (vecteur serveur w5-08).

## 6. Contrat `QuizBoosts.charge` (demande de l'audit)
`charge(b: Boost, gameId: String, purchaseNo: Int): Boolean` : `gameId` est un identifiant **unique par partie** (`[A-Za-z0-9._-]{1,48}`, tiré au hasard à la création de la partie, **sérialisé** avec elle : une partie reprise garde son identifiant ; `QuizGame` le fournit, jamais l'écran) ; `purchaseNo` est le rang de cet achat pour cet article dans cette partie (1 pour la première « Seconde chance », 1 puis 2 pour les « Jokers en plus ») ; la clé d'opération est `TokenPolicy.opKey(gameId, item, purchaseNo)` et **le même triplet ne débite qu'une fois** : un rejeu (partie restaurée après coupure entre le débit et l'effet) obtient `SpendResult.Ok(replayed = true)` et `charge` rend `true` **sans** second débit, ce qui permet d'appliquer l'effet manquant. À la reprise d'une partie, `TokenHub` recale le compteur d'achats avec `wallet.spendCount(opPrefix(gameId, item))` (le porte-jetons fait foi sur le jeu) ; `maxPerGame` se vérifie sur ce compteur, pas sur l'état en mémoire. `NoBoosts.charge` rend `false`. Un `purchaseNo` déjà débité avec un autre article ou un autre coût ⇒ `OpConflict` ⇒ `false` et journal (jamais silencieux).

## 7. Amendements aux cahiers (reportés dans leur en-tête)
**w5-08 (serveur)** doit en plus : (1) `EnvelopeIssuer.tokensGrant(…, fresh)` : corps à 6 lignes, fenêtre **72 h** (48 h si `fresh`) ; (2) `TokenGrantService` : premier bon d'un `install_pub` ⇒ `fresh=1` ; `refreshWindow(grant)` pour les bons `ISSUED` expirés ; (3) `TokenReconciler.recover(proof, walletState, walletCause)` : J5 étapes 2-3, limites J6, lignes `token_recovery`, `token_install` ; (4) `TOKEN_CHAIN_REPLAY`, `TOKEN_WALLET_LOST`, `TOKEN_RECOVERY_ABUSE`, `TOKEN_RECOVERY_HELD` ; comparaison de `seq` dans la chaîne courante (J7) ; (5) migration additive V<plus haut + 1> (§ 4) ; (6) tests : première ouverture ; reprise avec restitution ; 3ᵉ reprise en 30 j ⇒ confiscation ; cumul > 120 ⇒ `HELD` puis approbation ; `offline_allowed = false` ⇒ aucun bon ; rapport d'une chaîne antérieure ⇒ `TOKEN_CHAIN_REPLAY` ; `seq` = 1 après reprise ⇒ pas de `TOKEN_REPLAY` ; renouvellement de fenêtre sans doublon. Le JSON de réponse gagne `recovery: {status, returned, held} | null`.
**w5-17 (TV)** doit en plus : (1) `WalletKeys` fournit aussi `WalletMark` (fichier `files/tokens/wallet.mark`, mac sous la clé dérivée) ; (2) `TokenHub` instancie `TokenWallet(file, keys, mark)` ; expose `cause()` (`lost|broken|foreign_chain|none`) et `reopenedEvent` pour `ShopHub` (w5-16) et la passerelle (w5-12) ; (3) textes de § 3 ; (4) `QuizBoosts.charge(b, gameId, purchaseNo)` (§ 6) ; `gameId` sérialisé dans la partie sauvée ; (5) `TvConnect.event("tokens_reopened", cause)` ; (6) observable émulateur : effacer `wallet.txt` par `adb` ⇒ « Porte-jetons illisible… » (pas « Aucun jeton ») ⇒ relais ⇒ « Porte-jetons rétabli : N jetons » ⇒ `wallet.txt.broken-1` absent (fichier effacé) ; corrompre une ligne ⇒ idem avec `.broken-1` présent.
**Prérequis** : correctif cœur sur `claude/sonnet-w5-02-fix` (J1, J2, J3, `REOPENED`, `WalletMark`, vecteurs § 5) **avant** w5-05 (miroir Java/Python du corps à 6 lignes), w5-08 et w5-17 ; w5-10 (heartbeat : `tokensChain`, `walletCause`) et w5-12/w5-16 (envoi d'un rapport nul avec l'état) : une ligne chacun, à reporter par le routage.

## 8. Risques résiduels et décisions attendues du propriétaire
Résiduels (assumés) : root + rejeu du bon d'ouverture ≤ 60 jetons par reprise, ≤ 120 / 30 j, détecté au contact suivant ; dépenses hors ligne non rapportées réputées non faites (≤ 60 par reprise, au profit du client) ; une TV jamais reconnectée reste `UNREADABLE` (aucune dépense) ; la fenêtre de 72 h impose un relais dans les 3 jours, sinon renouvellement automatique (aucune perte, un contact de plus). **À trancher par le propriétaire** : (a) valeurs J6 (2 / 30 j, 3 / 90 j, seuil 120) ; (b) restituer ou confisquer par défaut le reliquat non rapporté (ici : restituer dans les limites) ; (c) fenêtre 72 h / 48 h (contre 30 j) ; (d) ordre de fusion : correctif w5-02-fix avant w5-05/08/17.
