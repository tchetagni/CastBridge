STATUT: TERMINÉ, À ESSAYER SUR LA TV (cœur testé en JVM ; câblage Android COMPILÉ SEULEMENT ; aucun serveur déployé, aucune TV réelle ici)
CAHIER: sonnet-w22-07 (tranche 1, dite w22-07a) · MODÈLE: sonnet 5.5 · BRANCHE: worktree-agent-ad08751542a91315e (remise sur `integration/agents` 1f8937e6 avant tout ; `ls wallet` montrait EscrowTicket, Snapshot, WalletCache… et `wallet/ops` côté serveur) · COMMIT: `7a8a8360` (+ rapport)
JETONS: inconnu

But du propriétaire : « J'attends la version avec portefeuille sur la TV ». Précision reçue en cours de route (2026-10-04) : « toute activation donne lieu à un portefeuille, il est autonome et hors ligne mais sa mise à jour dépend du serveur » : intégrée (voir « États »).

## Ce que la TV montre et sait faire
- **Accueil** : tuile « ◎ Jetons » (icône pièce + libellé, jamais l'icône seule) dès qu'une activation valide est installée et que le réglage local `wallet.enabled` (défaut ALLUMÉ, bascule dans le menu MENU : « Jetons : masquer… ») est allumé. Texte : `3 450 NDEM · 12 MBOKO` (espace fine insécable) ; AVANT la première synchronisation : « En attente de synchronisation », **sans aucun chiffre**. Cachée si le serveur répond 404/503 sans motif (module éteint ou clé absente) et tant que la TV n'est pas activée.
- **Écran « Mes jetons »** (5 touches + RETOUR, textes ≥ 42 px de conception = 28 px sur 720p, anneau de focus de 6 px ET flèche « ▶ », une action impossible porte « ✖ » et sa raison écrite) : soldes par monnaie et par poche tels que signés (« NDEM : 3 450 disponibles · 0 bloqués en mise »), date des soldes, édition (essai / production / illimitée), avis (licence en grâce / suspendue / révoquée, compte gelé, autre TV), 4 actions + « Actualiser » :
  - **Convertir mes jetons** : sens NDEM → MBOKO ou MBOKO → NDEM (taux et frais lus de `GET policy` : « 1 000 NDEM = 1 MBOKO », « frais 2 % »), quantité de MBOKO par roulette, avant / après AFFICHÉS d'après l'instantané signé, confirmation avec montants en chiffres ET en lettres (« Vous payez 12 000 NDEM (douze mille) et recevez 12 MBOKO (douze) »).
  - **Envoyer des jetons** : monnaie, code de réception du destinataire saisi à la roulette (contrôle du caractère de la même formule que le serveur), montant (chiffres + lettres affichés en direct), confirmation. La TV ne génère aucun code.
  - **Recevoir** : demande un code au serveur et l'affiche en très grand (150 px de conception) avec son compte à rebours.
  - **Historique** : 50 lignes par page du serveur (« Plus anciens »), hors ligne : dernière page gardée.
- **États (cœur testé)** : jamais synchronisé (« Portefeuille créé à l'activation : en attente de la première synchronisation », sans chiffre) · synchronisé (« Soldes au 21/09 14:13 ») · hors ligne avec instantané (« Hors ligne : soldes au … », actions grisées avec « Connexion Internet nécessaire », historique en cache permis) · instantané de plus de 35 jours (bandeau « Mise à jour du serveur attendue ») · licence en attente (« Jetons en attente de notification de votre activation ») · 409 `ACTIVATE` = état d'attente avec son motif français, jamais une erreur technique.
- **Erreurs** : le motif fermé du serveur (`details[0]`) donne le texte français identique sur les deux apps ; motifs inconnus ou nouveaux tolérés (« Opération refusée (code XYZ) », code assaini, jamais de `valueOf`) ; `SETTLE_CAP`, `TRIAL_LIMIT`, `RATE_LIMIT`, `LICENSE_PENDING`, `CONVERT_SUSPENDED`, `TRANSFER_SUSPENDED`, `RESULT_AFTER_REFUND`, `CODE_LIMIT`, `LOOKUP_LIMIT`, `IDEM_CONFLICT`, `BIND_PROOF` ont leur texte ; 404 / 503 sans motif : « Jetons : service indisponible » dans l'écran (carte cachée).
- **Idempotence** : une clé `tv-<24 hex>` par opération, créée à la confirmation ; une coupure réseau renvoie la MÊME requête (3 tentatives), puis « Réessayer » renvoie la même clé ; un refus du serveur n'est jamais renvoyé.

## Rouge d'abord (R1), par assertion
Les 14 classes de test ont été écrites avant tout code de production, contre des **souches** qui compilent et se trompent (`""`, `null`, `false`) : le rouge est donc par assertion.
```
:core:test --tests 'castbridge.core.wallet.ui.*'  → 92 tests, 80 échecs (12 « verts » = négations satisfaites par une souche)
AmountWordsTest.confirmationShowsDigitsAndWords : expected:<[3 450 NDEM (trois mille quatre cent cinquante)]> but was:<[]>
BindProofTest.goldenVectorIsReproducedByteForByte : expected:<xoImN8fTEOxXYnvgC6JZ0lN0n0qvZERwz/vlOjX3MkI=> but was:<null>
Ed25519SignTest.sameBytesAsTheJdkAndVerifies : Array elements differ at index 0. Expected element <-39>, actual element <0>
WalletClientTest.anAnswerOfTheServerIsNeverRetried : expected:<1> but was:<0>   (19 échecs sur 20)
WalletMessagesTest.statusAloneIsMapped : expected:<[Jetons : service indisponible]> but was:<[]>   (6/6)
WalletFlowTest.convertWalksDirection…  : expected:<…ConvertDirection> but was:<…Menu>   (WalletTextsTest ajoutée ensuite : 10/10 en échec, WalletFlowTest 8/11)
ConvertPreviewTest (6/8), WalletEntriesTest (6/7), WalletGateTest (7/8), WalletSyncScheduleTest (4/5), WalletActivationsTest (3/4), WalletIdemTest (2/2), WalletRepliesTest (7/8 ; 1 NullPointerException = `!!` sur une souche qui rend null)
```
Deuxième rouge, après la précision du propriétaire (tests ajoutés AVANT le code : `WalletStatusTest`, carte visible avant la première synchronisation, texte « Disponible après la première synchronisation ») :
```
WalletStatusTest : 7 échecs sur 8  (expected:<NEVER_SYNCED> but was:<SYNCED> ; expected:<WAITING_ACTIVATION> but was:<SYNCED> ; expected:<OFFLINE> but was:<SYNCED>)
WalletGateTest.cardNeedsActivationAndTheFlagAndIsHiddenOnlyWhenTheServiceIsDown : Expected value to be true.
WalletGateTest.withoutSnapshotNothingThatNeedsBalancesIsOffered : expected:<[Disponible après la première synchronisation]> but was:<[Soldes inconnus : connexion au serveur en cours]>
```

## Vert
- `:core:test --tests 'castbridge.core.wallet.*' :receiver:compileDebugKotlin` (par `gradle-lock.sh`, même commande que le cahier) : **exit 0** ; `castbridge.core.wallet.ui.*` : **111 tests, 0 échec** (14 classes) ; `castbridge.core.wallet.*` en tout : **247 tests, 0 échec** (les 136 de w22-03 inchangés).
- **`:core:test` complet** (une fois, à la fin, après le dernier commit de code) : **exit 0, 3 992 tests, 0 échec**.
- **Vecteur doré partagé** `tools/wallet/wallet-bind-vector.json` : produit par une implémentation INDÉPENDANTE (Python `cryptography`, clé de test de graine 0x55…), lu par `BindProofTest` (octets identiques), et vérifié par `java tools/wallet/BindProofCheck.java tools/wallet/wallet-bind-vector.json` (« VECTEUR OK ») qui reprend la logique de `backend/.../BindProof.valid` (message, fenêtre ± 5 min, clé 32 octets, signature 64 octets, le JDK seul). **À faire par l'architecte** : lire ce même fichier dans un test Java du serveur (backend interdit ici).
- Android : **compilé seulement** (`:receiver:compileDebugKotlin`). Rien de l'écran n'a tourné : ni dessin, ni focus à la télécommande, ni lisibilité à 3 m, ni performances du 32 bits. À voir sur la TV.

## Fichiers
Nouveaux, cœur : `android/core/src/main/kotlin/castbridge/core/wallet/ui/{WalletClient,WalletBind,WalletStatus,WalletGate,WalletFlow,WalletMessages,WalletTexts,WalletReplies,WalletModels,WalletSyncSchedule,WalletActivations,WalletEntries,WalletIdem,ConvertPreview,AmountWords}.kt` et 14 classes de test `…/test/…/wallet/ui/*Test.kt`.
Nouveaux, TV : `android/receiver/src/main/kotlin/castbridge/receiver/wallet/{WalletHub,WalletActivity}.kt`, `android/receiver/src/main/res/drawable/ic_cb_jetons.xml`, `android/receiver/wallet-keys.txt` (clés PUBLIQUES du portefeuille ; vide tant que l'exploitant n'y a pas mis la ligne).
Outils : `tools/wallet/wallet-bind-vector.json`, `tools/wallet/BindProofCheck.java`.
Modifiés (additif) : `android/core/.../update/Ed25519.kt` (+ `sign`, RFC 8032), `android/core/.../owner/ActivationIssuer.kt` (`Ed25519Signer` retombe sur `Ed25519.sign` quand le JDK / Android n'a pas Ed25519 : mêmes octets), `android/receiver/build.gradle.kts` (`BuildConfig.WALLET_KEYS`), `AndroidManifest.xml` (WalletActivity), `PlayerActivity.kt` (tuile d'accueil, entrée de menu, démarrage / synchronisation à la reprise), `docs/agent-briefs/SONNET-WAVE22-INDEX.md` (ma ligne). Rien dans `backend/`, `server-play/`, `sender/`. `version.properties` intact.

## Choix et écarts à relire
1. **Ed25519 sur la TV** : `java.security` n'a Ed25519 qu'à partir d'Android 13 ; la TV de référence (GaiaOS 32 bits) ne l'a probablement pas. J'ai donc ajouté `Ed25519.sign` en Kotlin pur (RFC 8032, deux vecteurs officiels + parité d'octets avec le JDK) et fait retomber `Ed25519Signer` dessus ; sans cela `InstallSigner` aurait planté à la création sur la TV.
2. **Clé d'installation = fichier privé** (`files/wallet/install-signer.b64`, `PlainWrapper`), pas le coffre Android : un changement de coffre changerait l'identité et le serveur refuserait la TV (`BIND_PROOF`) jusqu'à une réaffectation par l'administrateur. La preuve vise la copie d'une clé d'activation, pas un accès root. À contester si l'architecte préfère le coffre.
3. **Heure de la preuve** : horloge murale de la TV ; si le serveur refuse (`BIND_PROOF`) et donne son en-tête `Date`, la preuve est refaite UNE fois à l'heure du serveur ; sinon le texte dit de vérifier l'heure de la TV.
4. **Activations jointes à `sync`** : au plus 4 et 8 192 caractères en tout, d'abord « super », puis production, essai, le reste, les plus récentes d'abord (`WalletActivations`).
5. **Carte visible avant toute synchronisation** (précision du propriétaire) : la règle « cachée tant que le serveur n'a pas dit oui » du cahier est remplacée par « cachée seulement si le serveur répond 404/503 ». Le serveur est appelé dès la reprise de l'accueil (première fois tout de suite, ensuite toutes les 15 min).
6. **Taille des textes** : « 28 px » est lu comme 28 px sur une dalle 720p (= 42 px de conception de `Dx`, échelle 1080p) ; le plus petit texte de l'écran fait 42 px de conception. Les textes de la tuile d'accueil restent ceux des autres tuiles (statut en 16 sp, deux lignes si besoin) : à juger sur la TV.
7. **Sens et quantité** : la quantité saisie est un nombre de MBOKO (c'est ce que le serveur attend : `q`) ; le prix en NDEM est calculé par le serveur et seulement ÉCRIT à l'écran d'après le taux de `GET policy`.
8. **`GET /receive-code/{code}`** (destinataire masqué) n'est pas appelé : le cahier demande « code, montant, confirmer » ; le serveur n'a qu'un contrôle d'existence limité (10 / h) que je ne dépense pas à chaque saisie.
9. Hors périmètre (non fait, comme demandé) : choix de mise, écrans de jeu, bons (w22-06), page téléphone, tout `server-play` et `backend`.

## Mutations (appliquées puis retirées, `git checkout` de chaque fichier ; tests `wallet.ui.*`)
16 mutations, **toutes TUÉES** (M6 écrite d'abord comme `return null` ne compilait pas : refaite en `return r`, puis tuée). Après chaque série, `git status` propre.
| # | Mutation | Test qui la tue |
|---|---|---|
| M1 | preuve : heure et appareil inversés dans le message signé | `BindProofTest.goldenVectorIsReproducedByteForByte` (+ 2) |
| M2 | preuve : fenêtre ± 5 min exclusive au lieu d'inclusive | `windowIsPlusOrMinusFiveMinutesInclusive` |
| M3 | motif inconnu lu par `WalletReason.valueOf` (plante) | `unknownReasonsAreTolerated`, `unknownOrNewReasonsNeverCrash…` (`IllegalArgumentException: No enum constant`) |
| M4 | « Réessayer » avec une NOUVELLE clé d'idempotence | `WalletFlowTest.aNetworkFailureKeepsTheSameKeyForTheRetry` |
| M5 | client : une seule tentative sur coupure réseau | `WalletClientTest.networkErrorsRetryWithTheSameKeyAndBody` (+ 1) |
| M6 | client : instantané refusé (autre TV) ignoré, l'échange « réussit » | `aSnapshotOfAnotherTvIsRefusedAndNeverCached` (+ 1) |
| M7 | calendrier : synchronisation toutes les 30 min au lieu de 15 | `tickSyncsEveryFifteenMinutesOnlineOnly` |
| M8 | hors ligne : « Envoyer » reste permis | `offlineDisablesOperationsWithTheirReason` |
| M9 | état : « trop ancien » après 45 jours au lieu de 35 | `licensePendingNoticeIsABannerAboveTheBalances` (+ 1) |
| M10 | aperçu : frais arrondis au plancher (en faveur du joueur) | `feeRoundsAgainstThePlayer` (expected 34 but was 33) |
| M11 | lettres : « quatre-vingt » sans s final | `agreesWithTheExistingReaderUpToTenThousand` (+ 2) |
| M12 | carte : un chiffre inventé avant la première synchronisation | `cardLineHasFiguresOnlyFromASignedSnapshot` |
| M13 | `Ed25519.sign` : clé secrète non rognée (clamping) | `Ed25519SignTest` (3 échecs : vecteurs RFC 8032 et parité JDK) |
| M14 | service : un 503 AVEC motif pris pour « indisponible » | `serverStateFollowsTheAnswers` |
| M15 | carte visible alors que le service est indisponible (404/503) | `cardNeeds…HiddenOnlyWhenTheServiceIsDown` |
| M16 | client : un refus du serveur marqué « réseau » (donc réessayable) | `bindProofRefusedTwiceIsShownWithAClearText` |
Limite honnête : la partie Android (dessin, touches, focus, tuile) n'est protégée par AUCUNE mutation ; elle n'est que compilée.

## Ce que seuls une vraie TV et un serveur déployé peuvent confirmer
- Le dessin, le focus à la télécommande, la lisibilité à 3 m et la vitesse sur le 32 bits de l'écran « Mes jetons » et de la tuile.
- La clé d'installation créée sur la TV et sa vérification réelle par `BindProof.valid` (le vecteur prouve les octets, pas le serveur déployé).
- `GET policy`, `sync`, `convert`, `transfer`, `receive-code` contre MySQL (jamais testés ici : rapports w22-02 / w22-05 : « rien n'a tourné contre MySQL »).
- Qu'une TV sans clé du portefeuille dans `wallet-keys.txt` refuse bien tout instantané (testé en JVM : `UNKNOWN_KEY`), et que le texte « mettez la TV à jour » est alors compris.

## Essai du propriétaire, pas à pas
1. **Serveur** (acte du propriétaire, rien n'est déployé par cette branche) : `CASTBRIDGE_WALLET_ENABLED=1` ; `CASTBRIDGE_WALLET_KEY_FILE=<fichier PEM PKCS#8 Ed25519, secret>` ; `CASTBRIDGE_WALLET_TRUSTED_KEYS="nom:<clé publique brute base64>:ISSUE_TRIAL+ISSUE_PRODUCTION"` (les mêmes émetteurs que les clés d'activation de la TV) ; `CASTBRIDGE_WALLET_REQUIRE_BIND_PROOF=true` (défaut : cet APK signe ; `false` seulement pour une vieille TV qui ne signe pas) ; migration `V62__wallet.sql` appliquée. Une clé d'essai marche sans licence ; une clé de PRODUCTION sans licence enregistrée donne « Jetons en attente de notification de votre activation » (module licences : la licence de la TV doit exister dans le registre).
2. **Clé publique du portefeuille dans l'APK** : `pub=$(openssl pkey -in <fichier de clé> -pubout -outform DER | tail -c 32 | base64)` puis `kid=$(printf '%s' "$pub" | base64 -d | shasum -a 256 | cut -c1-16)` ; écrire la ligne `kid=… pub=…` dans `android/receiver/wallet-keys.txt` (ou `-PwalletKeysFile=…`). Construire l'APK TV VERROUILLÉ comme d'habitude (`-PrequireActivation=true`), le copier dans le `Download` de la clé USB.
3. **TV** : installer, activer (fichier `activation`), revenir à l'accueil : la tuile « ◎ Jetons » apparaît (« En attente de synchronisation » quelques secondes si le réseau passe, puis `N NDEM · M MBOKO` ; les attributions viennent du serveur, la TV n'en calcule aucune).
4. **Convertir** : Jetons > Convertir mes jetons > NDEM → MBOKO > 1 MBOKO > OK > OK ; puis MBOKO → NDEM (frais lus du serveur).
5. **Envoyer** : sur la TV B, Jetons > Recevoir (code très grand) ; sur la TV A, Jetons > Envoyer des jetons > monnaie > saisir le code > montant > confirmer (chiffres et lettres). Les deux soldes se mettent à jour après la synchronisation qui suit.
6. **Hors ligne** : couper le réseau de la TV : « Hors ligne : soldes au … », les 3 opérations disent « Connexion Internet nécessaire », l'historique enregistré reste lisible. Rebrancher : l'écran se met à jour.
7. **Réglage** : MENU > « Jetons : masquer… » cache la tuile et l'écran (`wallet.enabled`), le même point les rend.
