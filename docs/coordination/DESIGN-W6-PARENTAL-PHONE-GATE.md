# Conception W6 — Rapports parentaux de toute la TV, mode minimal du téléphone, mode total du super administrateur

> Document de conception (Fable, architecte, 2026-10-02). **Aucun code n'est modifié par ce document** ; l'exécution se fait par les cahiers `docs/agent-briefs/sonnet-w6-NN-*.md` (index : `SONNET-WAVE6-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents`. Tous les fichiers et symboles cités ont été vérifiés par lecture le 2026-10-02.
>
> **Demande du propriétaire (2026-10-02)** : « analyse l'amélioration du contrôle parental qui permet de donner tous les rapports d'usage de l'app TV et plus généralement de la TV au détenteur de l'appli smartphone. L'appli smartphone doit avoir des fonctionnalités minimales tant qu'elle n'est pas connectée et synchronisée avec une télé possédant une activation de production. La console d'administration offre toutefois les fonctionnalités totales pour le téléphone du super admin, après activation sur son interface. »

## 0. En dix lignes

1. **Rapports parentaux « toute la TV »** : la TV mesure déjà CastBridge-TV (vidéos, jeux, Apprendre, Quiz, blocages) et, en option, l'application au premier plan (`UsageStatsManager`, sondage de 4 s). W6 ajoute **l'écran allumé/éteint, les minutes par application et par tranche horaire, les lancements, les applications installées/retirées, le Sudoku, les jetons (W5), les téléchargements et les connexions** (téléphones, clé USB, SSH, assistance à distance : comptages, jamais de contenu), dans des **tampons circulaires compacts** (< 60 Ko), écrits au plus toutes les 60 s, rien l'écran éteint.
2. Le destinataire devient le **« téléphone détenteur »** (*parent holder*) : téléphone **de confiance** (appairage existant) **+ désigné avec le code parental + consentement affiché et validé sur la TV** (télécommande), au plus 3 ; révocable des deux côtés ; **indicateur permanent** sur la TV (« Rapports partagés avec N téléphone(s) ») y compris en mode enfant, en termes adaptés.
3. **Format v2 des rapports** : JSON versionné, **signé Ed25519 par la clé d'installation de la TV** (nouvelle `InstallSigner`, graine dans le `KeystoreWrapper` de w4-03) **et** HMAC par destinataire (compatibilité). Stockage TV **chiffré au repos** sous la même clé (hors sauvegardes, hors de portée d'une session SSH d'assistance). Rétention : 14 jours de journal, 35 jours d'agrégats.
4. **Synchronisation** : Bluetooth (CBTP, existant, étendu) **et Wi-Fi local** (nouvelles routes `/api/parental/holder/*`, le téléphone identifié par son **jeton de téléphone de confiance**, plus par le code parental). **Pas de relais distant en v1** (décision architecte, renversable) : local d'abord ; le relais chiffré de bout en bout est esquissé au § 2.9 pour une vague ultérieure.
5. **Mode minimal du téléphone** : nouvelle porte `PhoneGate` (`core/owner`), miroir de `FeatureGate`/`TvGate` : tant qu'aucune **preuve de TV en production** n'est détenue, seules les fonctions de la liste blanche `MINIMAL_WHITELIST` (figée par un test) sont ouvertes : lier une TV, partager le code d'appareil, porter une clé, **« Télécharger tous les contenus libres »**, diffuser/télécommander **vers la TV liée** (la TV applique son édition), lots **d'essai** vers une TV d'essai, aide, confidentialité, langue, entrée super-admin et point focal. **Copier un média vers la TV exige une TV en production prouvée** (refusé dans l'interface **et** dans la file de transfert) ; une **matrice fonction × état de la TV** (§ 3.7) et un **catalogue unique de messages** dérivé de la machine d'états de synchronisation (§ 3.8) fixent chaque cas.
6. **Preuve de TV** (`TvProof`) : défi du téléphone → réponse signée par la **clé d'installation** de la TV, contenant le **jeton d'activation de production** lui-même (vérifié hors ligne sur le téléphone contre `TRUSTED_KEYS`), le code d'appareil, l'état `TvGate`, le défi. Clé d'installation **épinglée** à la première liaison sécurisée. Cache **14 jours** (et jamais au-delà de la fin de la clé), horloge monotone (réutilise `TvClock`), re-synchronisation à chaque liaison. **Essai, grâce, mode réduit (W4) : aucune preuve.**
7. **Protection par le chemin des données** : le serveur exige la même preuve (`X-CB-TV-Proof`, déjà prévue par W5 pour la boutique) pour servir les lots complets, les locations, les jetons ; `LotsRuntime` ne synchronise que les lots `-trial` sans preuve ; la porte d'interface ne protège que ce qui n'a pas de serveur derrière (lecteur du téléphone, jeux hors ligne) et le dit honnêtement.
8. **Super administrateur** : après le mot de passe (`SuperAdminGate`, haché bcrypt présent **seulement** dans une build `-Pcastbridge.superAdmin=true`) **et** l'ouverture du coffre (`OwnerStore`), une **session** `SuperSession` (12 h, renouvelable, verrouillable) place `PhoneGate` en état `Super` : **toutes** les fonctions du téléphone sans preuve de TV, la console d'émission, les outils boutique du propriétaire. **Pas** les rapports parentaux d'une TV qui ne l'a pas désigné (décision architecte, § 4.4). Aucun lien avec `SUPER_UNLIMITED` (portée de clé **pour la TV**) ni avec la délégation des points focaux (état `Agent`, restreint, jamais total).
9. **Changements par rapport aux décisions antérieures** (§ 7) : la décision du 2026-10-01 « le téléphone reste ouvert » est **remplacée** (interrupteur de compilation `REQUIRE_TV_PROOF`, grâce absolue de 14 jours pour les téléphones déjà installés, comme `FleetMigration`) ; la limite « Bluetooth seulement » de PARENTAL.md tombe ; `GET /api/activation/proof` prévu par W5 (w5-16) devient la version **signée avec défi** de W6 ; w5-18 reste valable et W6 s'y empile.
10. **Effort** : 23 cahiers, 5 sous-vagues, ≈ 46 agent·jours ; tests JVM sans appareil pour la porte, la preuve, les sessions, la collecte, les formats et l'agrégation ; **trois décisions propriétaire** prises ici avec recommandation, **deux BLOQUÉ** (textes juridiques / divulgation magasin, contact D7).

## 1. État des lieux vérifié (à ne pas refaire)

| Brique | Où | Ce qui existe |
|---|---|---|
| Logique parentale pure | `C/parental/ParentalEngine.kt` (`tick` :221, `appTick` :284, `syncInstalled` :306, `reportSupervision` :368, `recordBlocked` :401, `report(days)` :417, clés `usage`/`appusage`/`blocked`/`tamper`) | minutes par jour et par profil (`play`, `games`, `downloads`, `apps`), minutes par application et par jour, blocages, altérations |
| Détecteur toute la TV | `R/ForegroundWatcher.kt` (sondage 4 s, écran allumé seulement, `UsageEvents` MOVE_TO_FOREGROUND/BACKGROUND, fil `cb-fg` priorité fond) ; `C/parental/Supervision.kt` (`ForegroundTracker`, `SupervisionState`) ; manifeste TV :35 `PACKAGE_USAGE_STATS` | léger, adapté 32 bits ; l'accessibilité en option |
| Colle TV | `R/ParentalHub.kt` (`init` :93, `tick` :340 toutes les 15 s, `superviseTick` :419, `onForeground` :428, `accountApp` :446, `journalExtras` :124, `categoryOf` :138 **sans Sudoku** (w5-18 corrige)) ; `R/ParentalActivity.kt` (pages `rootPage` :152, « Rapports vers le téléphone du parent » :179) | compteur, garde, filtre, écrans TV |
| Rapports TV → téléphone | `C/parental/ParentalReports.kt` (`ReportRecipients` ≤ 3 :63-125, `ReportMac` HMAC :128, `ReportOutbox` 40/200 Ko/14 j :148, `ReportBuilder` v1 :222, `ParentalReports.tick` :340, alertes dédupliquées :314) ; `C/parental/ParentalSync.kt` (CBTP :62, `ReportInbox` 150/90 j :125, `ReportSync.run` :194) ; `R/TvService.kt:216` (`parental = ParentalHub.syncHost` dans `BtServer`) ; `S/ParentalInbox.kt` (tirage, tâche 15 min, notification privée) | Bluetooth seulement ; v1 = `{v:1,type,tv,day,profile,totalMin,kinds,apps,blocked,newApps,supervision,tamper}` + `events`/`learn` |
| Journal mesuré | `C/parental/tab/TvJournal.kt` (400 événements / 8 j), `SessionTracker`, `EventType` (`TabModel.kt`), `LearnDigest` | vidéos (titre), Quiz, Échecs, téléchargements, déverrouillages ; **pas le Sudoku, pas l'écran, pas les connexions** |
| Téléphone | `S/ParentalTab.kt` (9 sections :144 ; code vérifié par la TV, `TabLock` 3 min, lecture seule hors TV :114), `S/ParentalWholeTv.kt`, `S/ParentalReportsUi.kt`, `S/ParentalCharts.kt`, `S/ParentalExport.kt`, `S/ParentalData.kt` (prefs `castbridge_parental_ledger`), `S/ParentalScreen.kt` (règles via API) ; `C/parental/tab/ParentalLedger.kt` (idempotent, trous ≠ zéros, rétention 90 j / 20 000 événements) | « ● MESURÉ / ◐ MEILLEUR EFFORT / ○ INDISPONIBLE » |
| Porte TV | `C/owner/FeatureGate.kt` (`Feature.lockedAllowed`, `LOCKED_WHITELIST` figée :20, `GateState` :52, `FeatureGate.state` :68) ; `C/owner/Activation.kt` (`TvGate.evaluate` :187, `TvAccess` :170 avec `trial`, `superUnlimited`, `suspended`) ; `R/ActivationCenter.kt` (`state()` :92, `trial()` :96, `statusFields()` :107) ; `R/TvService.kt:840` (`GET /api/activation`) ; `R/RentalHub.kt:74-79` (`/api/activation/request`, `/api/activation/install`) | le téléphone est **ouvert** (TRIAL-EDITION § 14) ; `CarrierMode` (:133) existe ; `Feature` contient déjà `SHARE_DEVICE_CODE`, `CARRY_ACTIVATION_FOR_TV` |
| Super-admin téléphone | `C/owner/SuperAdminGate.kt` (bcrypt, `enabled` faux sans haché, `TapSequence` 7 tapes + appui long) ; `OL/SuperAdmin.kt` (`SuperAdminActivity : ConsoleActivity`, même mot de passe → `OwnerStore.unlock/create`) ; `OL/ConsoleActivity.kt` (verrou 2 min, `onStop` verrouille, FLAG_SECURE) ; `OL/OwnerStore.kt` (`owner-vault.txt`, PBKDF2 600 000, `UnlockGuard`, journal chaîné) ; `android/ownerlib/build.gradle.kts:9-23` (haché compilé **seulement** avec `-Pcastbridge.superAdmin=true`) ; `S/MainActivity.kt:101-102` (geste caché) | l'audit A5-2 est traité (haché absent des builds ordinaires) ; **aucune notion de session** : la console se verrouille à 2 min et le reste de l'app n'en sait rien |
| Clé d'installation (W4) | w4-01 `C/lots/InstallKey.kt` (X25519), w4-03 `R/KeystoreWrapper.kt` — **en conception/exécution, non fusionnés** | W6 ajoute une graine **Ed25519 de signature** à côté (jamais la même clé pour chiffrer et signer) |
| Preuve d'activation (W5) | DESIGN-W5 § 2 (2), § 4.5 (2), § 15 (`GET /api/activation/proof`, `ActivationProof` dans w5-04, `ShopRequestVerifier` dans w5-07) — **non fusionnés** | W5 transporte le **jeton de production brut** comme preuve pour le serveur ; W6 l'enveloppe dans une réponse **signée au défi** et réutilise la vérification serveur |
| Téléphone : liaison | `S/TvLink.kt` (`AndroidBtTransport`, `TvLinkManager.state`, `LinkUi.Connected.session` avec `credential` = jeton de téléphone de confiance `TvAuth.isUsable` `C/trust/TrustRegistry.kt:189-196`) ; `C/trust/PhoneLink.kt` (`SavedTv` :23, `LinkSession` :31) | la TV sait **quel téléphone** parle en HTTP quand il présente son jeton de confiance (ce que PARENTAL.md § Limites disait impossible n'est plus vrai : `TvService.btTrusted` et `trust.list()` existent) |
| Contenus libres | `S/FreeContentScreen.kt` (« sans activation, sans appairage, sans TV ») | **doit rester** ouvert en mode minimal (décision du 2026-10-02) |
| Sauvegardes | `S/res/xml/backup_rules.xml` (exclut `castbridge_parental_inbox`, `castbridge_parental_ledger`, coffre) ; `allowBackup=false` des deux côtés | à compléter : magasin de preuves, session super, magasin parental TV |

## 2. Volet A — Rapports d'usage de toute la TV au téléphone détenteur

### 2.1 Modèle « téléphone détenteur » (parent holder)

- **Devenir détenteur** (trois conditions cumulatives, toutes vérifiables en JVM, `C/parental/Holders.kt`) : (1) téléphone **de confiance** de la TV (appairage plug-and-play existant, `TrustRegistry`) ; (2) **désignation** par un parent avec le **code parental** (depuis la TV ou depuis le téléphone, `POST /api/parental/holders/add {pin, phoneId}` ; remplace `reports/recipients/add` qui reste accepté comme alias) ; (3) **consentement affiché sur la TV** (`HolderConsentActivity`, plein écran, télécommande) : « Cette TV enverra à « <nom du téléphone> » un résumé de ce qui est regardé, joué et utilisé sur cette TV (toutes les applications si la surveillance est active). Toute personne qui utilise cette TV doit en être informée. [J'ai informé le foyer, continuer] [Annuler] ». La désignation reste **« en attente »** tant que le consentement n'a pas été validé **sur la TV** (un téléphone seul ne peut pas se désigner) ; en attente, aucun rapport ne part.
- **Identité d'un détenteur** : `holderId` = identifiant opaque existant (`ReportRecipients.phoneId`, SHA-256 tronqué de l'adresse Bluetooth) **et** l'identifiant du jeton de téléphone de confiance (hachage du jeton, `TvAuth`) quand il est connu : la même entrée sert au Bluetooth et au Wi-Fi. Jamais d'adresse ni de jeton dans un rapport.
- **Plusieurs détenteurs** : 3 au plus (inchangé) ; chacun a sa clé HMAC et reçoit **les mêmes rapports** (pas de vue par détenteur en v1).
- **Révocation** : depuis la TV (code parental, « Retirer ») ; depuis le téléphone détenteur lui-même (« Ne plus recevoir les rapports », sans code : on peut toujours renoncer) ; **par l'administrateur de la TV** (code de connexion : `POST /api/parental/disable` coupe tout) ; automatiquement quand le téléphone n'est plus de confiance. Révoquer supprime l'outbox du téléphone et sa clé ; le téléphone garde sa copie locale (c'est la sienne) et le dit.
- **Profils** : les règles et les rapports restent **par profil** (`ChildProfile`), plus une section **« Toute la TV »** sans profil (écran allumé, applications, connexions) qui est TV-wide. Un **profil enfant** ne voit sur la TV **que** l'indicateur (§ 2.6) et le total du jour de son propre temps (déjà le cas : avertissement 5 min) ; jamais les rapports, jamais la liste des détenteurs par nom.

### 2.2 Ce que la TV rapporte (et ce qu'elle ne collecte jamais)

| Donnée | Source | Étiquette | Granularité | Nouveau ? |
|---|---|---|---|---|
| Écran allumé / éteint | `PowerManager.isInteractive` lu dans le `tick` 15 s existant → segments `[on, off]` | ● MESURÉ | segments bornés (200/jour), total minutes/jour | **oui** (`ScreenSegments`) |
| Application au premier plan | `ForegroundWatcher` (inchangé) → `appTick` | ◐ MEILLEUR EFFORT (surveillance active) | minutes par application **par tranche horaire** (24 × u16 par application et par jour, `UsageSlots`) | **oui** (tranches) |
| Lancements d'applications | `onForeground` (changement de paquet) | ◐ | compteur par application et par jour | **oui** |
| Applications installées / nouvelles / retirées | `syncInstalled` (5 min) | ● | liste du jour (paquet, libellé, catégorie devinée) | partiel (retraits nouveaux) |
| Lectures de la bibliothèque CastBridge-TV | `SessionTracker` VIDEO | ● | titre (tronqué 120), minutes ; **option par profil « sans titres »** (`shareTitles`, défaut vrai) | existe + option |
| Apprendre | `LearnDigest`, `TvJournal.fromLearn` | ● | fiches, épreuves, scores | existe |
| Quiz / Échecs / **Sudoku** | `SessionTracker` ; `SudokuActivity` ajouté à `categoryOf`/tick (w5-18 corrige la catégorie, W6 ajoute le suivi) | ● | parties, minutes, score du Quiz quand w5-17 le fournit | Sudoku **nouveau** |
| Jetons (W5) | compteurs de w5-18 (`tokensSpent`, `purchasesDenied`, `purchasesWithPin`) | ● | par profil et par jour | w5-18 |
| Téléchargements | `DownloadsActivity` (temps) + compteur « lancés / terminés » si `DownloadsHub` l'expose (sinon « non transmis ») | ● | nombres, octets arrondis au Mo | partiel |
| Blocages, altérations, tentatives de code | existants | ● | existants | — |
| Connexions à la TV | nouveaux crochets `ParentalHub.note(kind, label)` : téléphone (nom donné à l'appairage), clé USB (libellé du volume), SSH (ouverture), assistance à distance (ouverture/fermeture, `TunnelHub`) | ● | **comptages et heures seulement** | **oui** |

**Jamais collecté ni transmis** (liste figée dans `ParentalPrivacy.NEVER`, affichée mot pour mot sur la TV et sur le téléphone, et testée : un champ de rapport dont le nom figure dans la liste noire fait échouer le test) : contenu des fichiers, noms de fichiers hors bibliothèque CastBridge-TV, texte, URL, recherches, messages ou contenu **d'une autre application**, captures d'écran, son, image, position, adresses Bluetooth, SSID/BSSID, mots de passe, codes PIN, jetons, clés, identifiants de compte. Le **nom de paquet** et le libellé d'une application sont collectés (c'est l'objet de la surveillance de toute la TV) ; rien de ce qui se passe **dans** l'application.

### 2.3 Collecte sur une TV 32 bits (coût)

- Aucune boucle nouvelle : tout s'accroche au `tick` de 15 s de `ParentalHub` et au sondage de 4 s existant. `UsageSlots` est un tableau `IntArray(24)` par application active (≤ 40 applications/jour) : ≈ 4 Ko/jour en mémoire ; sérialisé **une fois par minute au plus** (compteur de saleté) et au changement de jour ; 7 jours conservés dans le magasin parental + 35 jours d'agrégats (minutes/jour/application, total écran) pour la comparaison hebdomadaire.
- `ScreenSegments` : un segment ouvert quand l'écran passe allumé, fermé quand il passe éteint ou au changement de jour ; borné à 200 segments/jour ; **écran éteint = aucune écriture** (le `tick` continue mais ne touche rien).
- Journal `TvJournal` : 400 → **800 événements, 14 jours** (≈ 160 Ko JSON au pire ; budget TV ≤ 300 Ko pour tout le parental).
- Aucun wakelock, aucune alarme, aucun récepteur de diffusion nouveau (les retraits d'applications sont vus au balayage de 5 min).

### 2.4 Stockage sur la TV (au repos)

- Nouveau `R/ParentalStore.kt` : les préférences `castbridge_parental_reports` (outbox, clés HMAC, journal) et les nouveaux tampons migrent vers `files/parental/*.json` écrits par `SafeFile` (w1-02) et **chiffrés** par `SecretWrapper` (w4-01) sous une **clé de magasin parental** elle-même enveloppée par `KeystoreWrapper` (w4-03). Motif : la session SSH d'assistance à distance (REMOTE-TUNNEL-TV, experts désignés par l'éditeur) peut lire le répertoire privé de l'application ; les données parentales **ne doivent pas être lisibles par l'éditeur** (CONDITIONS-ASSISTANCE-A-DISTANCE : « n'accède pas au contenu personnel »). Le `KeystoreWrapper` lie la clé au matériel : un fichier copié est illisible. Repli sans Keystore utilisable (vieux firmware) : chiffrement sous une clé dérivée de la clé d'installation, et l'état « protection : logicielle » est affiché comme pour les lots (w4-03 `installKeyProtection`).
- Migration : au premier démarrage avec W6, les anciennes préférences sont importées puis effacées ; l'opération est idempotente.
- Rétention : outbox 14 j (inchangé), journal 14 j, tranches 7 j, agrégats 35 j, consentements et détenteurs sans limite tant qu'ils sont actifs. `POST /api/parental/history/clear` efface tout sauf détenteurs et consentements.

### 2.5 Format v2 des rapports (signé, versionné)

Enveloppe (hérite de `OutMsg.envelope()`, champs additifs) :
```json
{"id":"<hex>","ts":1760000000000,"kind":"daily|weekly|alert|snapshot","body":"<JSON texte>","mac":"<hmac hex>",
 "sig":{"alg":"ed25519","kid":"<id de la clé d'installation>","v":"<base64>"}}
```
`sig` signe `"CBR2\n" + id + "\n" + ts + "\n" + kind + "\n" + body` (domaine distinct de `CBR1`). Un ancien téléphone ignore `sig` et vérifie `mac` ; un téléphone W6 exige **les deux** dès qu'il connaît la clé d'installation de la TV (épinglée à la désignation, remise sur le lien sécurisé avec la clé HMAC : champ `installPub` de la réponse CBTP et de `/api/parental/holder/hello`).

Corps `daily` v2 (tout champ v1 conservé ; additifs en gras) :
```json
{"v":2,"type":"daily","tv":"<nom>","tvId":"<sha256 tronqué de la clé d'installation>","day":"2026-10-02","profile":{"id":"c1","name":"Awa"},
 "totalMin":95,"kinds":{"play":40,"games":20,"downloads":0,"apps":35},"apps":[{"pkg":"com.google.android.youtube","label":"YouTube","min":35,"slots":[0,0,…,12,23,0,…],"launches":3,"quality":"best_effort"}],
 "screen":{"onMin":210,"segments":[[1760000000000,1760003600000]]},"launchedApps":7,"newApps":[…],"removedApps":[…],
 "blocked":[…],"tamper":[…],"supervision":{…},"events":[…],"learn":{…},
 "games":{"quiz":{"n":2,"min":20,"best":"8/10"},"sudoku":{"n":1,"min":12},"chess":{"n":0,"min":0}},
 "tokens":{"spent":5,"denied":1,"withPin":0},"downloads":{"started":1,"done":1,"mb":120},
 "connections":[{"kind":"phone","label":"Téléphone de Papa","n":2},{"kind":"usb","label":"LEXAR","n":1},{"kind":"remote_assist","n":0}],
 "holders":2,"clockDoubt":null,"quality":{"measured":["play","games","learn","tokens","downloads","screen","connections"],"bestEffort":["apps"],"unavailable":[]}}
```
`weekly` v2 : par jour `totalMin`, `screenOnMin`, `topApps` avec `slotsSum[24]`, comparaison laissée au téléphone. `alert` v2 : `alert` ∈ {`limit`,`blocked`,`tamper`,`newapp`,`holder_added`,`holder_removed`,`late_use`} (nouveau `late_use` : usage entre 22 h et 6 h si le parent l'a demandé). `snapshot` (nouveau) : état instantané demandé par le téléphone (« Que fait la TV maintenant ? ») : application au premier plan (si surveillance active), profil actif, écran allumé, **sans historique** ; disponible seulement à un détenteur, jamais plus d'une fois par minute, et **journalisé sur la TV** (« <téléphone> a consulté l'état en direct à 18 h 04 », visible dans « Activité d'aujourd'hui » de la TV : la transparence vaut aussi pour les parents).

### 2.6 Transparence sur la TV (non négociable)

- **Indicateur permanent** : `KeyBadgeOverlay` reçoit une ligne supplémentaire quand `holders > 0` : « Rapports d'usage partagés avec 2 téléphones » (adulte) ; en **mode enfant** : « Tes parents reçoivent un résumé de ce que fait la TV » ; sur l'écran d'accueil, une icône discrète près de la tuile « Contrôle parental » avec le même texte en infobulle. Pas de mode discret, pas de réglage pour le cacher (une option « réduire » le passe en icône seule, jamais en rien).
- **« À propos > Contrôle parental et rapports »** : liste des détenteurs (nom du téléphone, date du consentement, dernier retrait), ce qui est collecté (`ParentalPrivacy.COLLECTED`), ce qui ne l'est jamais (`NEVER`), et « Les rapports ne quittent pas la maison : ils vont du téléviseur au téléphone, jamais à un serveur ».
- **Alerte aux détenteurs** quand un détenteur est ajouté ou retiré (`holder_added`/`holder_removed`) : un parent sait toujours qui reçoit.
- **Séparation stricte avec l'assistance à distance** : le tunnel (`TunnelHub`) n'a aucun accès au magasin parental (chiffré, § 2.4) ; aucune route `/api/parental/*` n'est joignable par le tunnel (liste noire dans `TunnelHub`, testée) ; le serveur n'a aucune route parentale ; la télémétrie (`TELEMETRY.md`) ne compte **aucun** événement parental (déjà le cas : test `grep TvConnect` de w5-18 maintenu et étendu à `C/parental/**` et `R/Parental*`).

### 2.7 Synchronisation directe (Bluetooth et Wi-Fi local)

- **Bluetooth** (CBTP, `ParentalSyncProtocol`) : requête `{"v":2,"want":["reports","snapshot"]}` ; une TV v1 répond comme avant (le téléphone lit `v` de la réponse) ; réponse v2 ajoute `installPub`, `holders`, `consent` (`pending|ok`), `snapshot` si demandé. Même tirage par le téléphone désigné (tâche 15 min, à l'ouverture, bouton).
- **Wi-Fi local** (nouveau, `C/parental/HolderHttp.kt` + routes dans `ParentalApi`) : `POST /api/parental/holder/hello` (jeton de téléphone de confiance en en-tête `X-CB-Pin`, comme toute route ; corps `{v:2}`) → `{you:{id,designated,consent},installPub,key?}` ; `POST /api/parental/holder/pull` → rapports en attente (même enveloppes) ; `POST /api/parental/holder/ack {ids,key}` ; `POST /api/parental/holder/snapshot`. La TV retrouve le détenteur par le **hachage du jeton** présenté (`TvService.trust`), jamais par un identifiant écrit dans le corps. **Pas de code parental** pour tirer (le téléphone a déjà été désigné avec le code) ; **code parental obligatoire** pour toute écriture de règles (inchangé). Un téléphone non désigné reçoit 403 `{"error":"Ce téléphone n'est pas désigné pour les rapports"}` et la marche à suivre.
- **Hors ligne** : TV sans téléphone à portée pendant des semaines → outbox 14 j (bornée), agrégats 35 j : à la prochaine visite, le téléphone reçoit ce qui reste et le tableau de bord montre les **trous** (jamais des zéros). La TV ne tente jamais de joindre un serveur pour cela.
- **Une seule voie d'absorption** sur le téléphone (`ParentalLedger.absorb`, idempotente) pour les deux transports.

### 2.8 Côté téléphone : écrans (Compose, tactile)

Le **même onglet Parental** (`S/ParentalTab.kt`, sections :144) : W6 réorganise sans tout réécrire.

| Section | W6 |
|---|---|
| Tableau de bord | bascule **Aujourd'hui / Cette semaine** ; par profil et « Toute la TV » ; cartes : écran allumé (● ), CastBridge-TV (●), autres applications (◐), limite du jour contre réalisé (« au moins X min » quand ◐ manque), 3 premières applications avec **mini-carte horaire** (24 cases), blocages, alertes, état de la surveillance, âge des données, **« N téléphones reçoivent les rapports »** |
| Toute la TV (nouvelle, remplace « Par application ») | par application : minutes, lancements, carte horaire (jour) ou carte jour × heure (semaine, `Heatmap` existant) ; nouvelles / retirées ; **connexions** (téléphones, clé USB, SSH, assistance : comptages) ; état en direct (`snapshot`, bouton, journalisé) |
| Apprendre et Quiz | + Sudoku, + jetons dépensés (w5-18) |
| Rapports / Exports | inchangés ; les exports (texte, PDF, CSV) gagnent les colonnes v2 ; toujours **feuille de partage** du parent, rien d'envoyé |
| Alertes | + `holder_added/removed`, `late_use` (option par profil), préférences par profil |
| Parents de cette TV (nouvelle) | détenteurs (nom, consentement, dernière synchro), « Désigner ce téléphone » (code parental + **attente de la validation sur la TV**, état affiché), « Ne plus recevoir », explication du consentement |
| Confidentialité (nouvelle) | `COLLECTED` / `NEVER`, « aucun serveur », option « sans titres de vidéos » par profil, rétention locale (30-365 j), purge |
| Règles de la TV | inchangé (`ParentalScreen.kt`) + options W6 (titres, `late_use`) |

Accessibilité : chaque chiffre a son mot (déjà la règle), les cartes horaires ont une alternative textuelle (« surtout entre 19 h et 21 h »), couleurs du thème + symboles, tailles de texte respectées (`sp`). Le D-pad est sans objet sur le téléphone.

### 2.9 Relais distant (hors de la maison) : décision et esquisse

**Décision architecte (renversable, D-W6-2) : local uniquement en v1.** Motifs : (1) le modèle « aucun serveur ne voit les données parentales » est une promesse simple à tenir et à expliquer ; (2) le relais ajoute un coût serveur (boîtes aux lettres, rétention, abus) et un risque juridique (le serveur devient intermédiaire de données de mineurs même chiffrées) ; (3) le parent est chez lui le soir : le délai de 15 min suffit pour les résumés, et les alertes critiques (surveillance affaiblie) restent dans l'outbox. **Esquisse v1.5** si le propriétaire le veut : la TV chiffre chaque enveloppe pour la clé **X25519 du détenteur** (générée sur le téléphone, publique remise à la désignation) avec `SecretWrapper`, la dépose par battement de cœur dans une **boîte aux lettres opaque** (`POST /api/v1/mailbox/{tvId}`, ≤ 64 Ko/jour, 7 jours), le téléphone la tire avec son jeton d'appareil ; le serveur ne voit que des octets, des tailles et des heures (métadonnées : à mentionner dans la politique). Rien de cela n'est dans W6.

## 3. Volet B — Mode minimal du téléphone (`PhoneGate`)

### 3.1 Règle

Tant que le téléphone ne détient pas une **preuve valide** qu'il est lié à une TV portant une **activation de production qui compte** (`TvAccess.keyInstalled && !trial && !degraded`), il n'offre que la **liste blanche minimale**. Une TV d'essai, en grâce, en mode réduit (W4), suspendue (« Vérifiez l'heure ») ou verrouillée **ne débloque rien**. Le super administrateur (§ 4) et le point focal (délégation) ont leurs états propres.

### 3.2 La liste minimale (décision architecte, renversable : D-W6-1)

`PhoneFeature(minimalAllowed = true)` : `USAGE_NOTICE`, `PRIVACY_SCREEN`, `DISPLAY_LANGUAGE`, `TV_PAIRING` (trouver, appairer, lier, dépanner : `TvPairScreen`, `ConnectScreens`, diagnostics), `SHARE_DEVICE_CODE`, `CARRY_ACTIVATION_FOR_TV` (`ActivateTvActivity`, QR/collage/fichier, Bluetooth et Wi-Fi), `FREE_CONTENT_DOWNLOAD` (« Télécharger tous les contenus libres », décision du 2026-10-02), `CAST_TO_LINKED_TV` (« Lire en direct », télécommande, volume : fonctions **vers la TV liée**, que la TV accepte ou refuse selon **son** édition : une TV d'essai accepte le streaming, c'est sa raison d'être), `TRIAL_LOTS_SYNC` (synchroniser les lots **`-trial`** vers une TV qui a une clé d'essai : sans cela la fenêtre d'essai de 12 h de TRIAL-EDITION § 15 ne peut pas se remplir), `INTERNET_GATEWAY_FOR_TV` (partage de la connexion par Bluetooth : nécessaire pour activer, mettre à jour, dépanner), `TELEMETRY_CONSENT`, `HELP`, `UPDATES_PHONE` (se mettre à jour soi-même), `SUPER_ADMIN_ENTRY` (geste caché, si la build l'a), `FOCAL_ENTRY` (devenir point focal : la délégation est sa propre preuve).

**Fermé sans preuve** : `PHONE_LIBRARY_PLAYER` (lecteur et bibliothèque **du téléphone**), `SEND_FILES_TO_TV` (envoi/copie de fichiers, DLNA, Wi-Fi Direct, file de transfert), `TV_LIBRARY_BROWSE` (bibliothèque de la TV), `TV_ADMIN` (installer des APK sur la TV, redémarrer, SSH, routes BT), `LEARN_PHONE`, `QUIZ_PHONE`, `CHESS_PHONE`, `GAMES_PHONE`, `DOWNLOADS` (téléchargements HTTP/magnet du téléphone), `LOTS_SYNC_FULL`, `SHOP` (W5), `TOKENS`, `PARENTAL` (tableau de bord et règles ; la réception des rapports en attente continue en tâche de fond pour ne rien perdre, mais l'écran est derrière le mur), `REMOTE_TUNNEL_GATEWAY` (relais du tunnel d'assistance : la TV doit être en production pour être assistée), `ASSISTANT_IA`, `TRANSFER_MULTIPATH`.

**Pourquoi cette coupe** : tout ce qui **sert à obtenir et poser une clé de production** et tout ce qui est **gratuit par décision** (contenus libres) reste ; tout ce qui a de la **valeur propre** (lecteur, jeux, Apprendre, lots complets, boutique, admin de TV, parental) attend la preuve. **Alternative plus stricte** (si le propriétaire la préfère) : retirer `CAST_TO_LINKED_TV` et `TRIAL_LOTS_SYNC` ; conséquence : la TV d'essai ne sert plus qu'au Sudoku. Je ne la recommande pas.

### 3.3 La preuve (`TvProof`, cœur, testée en JVM)

```
téléphone → TV : nonce (32 octets aléatoires, hex)                 [BT : trame OwnerFrames.PROOF_REQUEST (9) ; Wi-Fi : GET /api/activation/proof?nonce=…]
TV        → téléphone : cbx1.<payload>.<sig>  type=proof            [BT : trame PROOF (10) ; Wi-Fi : corps texte]
```
Charge utile (enveloppe `cbx1` existante, type **`proof`**, `kid` = **identifiant de la clé d'installation de signature** (`InstallSigner.keyId`), `seq` = compteur de la TV, `nonce` = celui du téléphone, `issuedAt` = `TvClock.now`, fenêtre 10 min, cible `Device(k, factors)` de la TV) ; corps : `code=<code d'appareil>`, `state=production|trial|grace|degraded|locked|suspended`, `label=<TvAccess.label>`, `endsAt=<ms|0>`, `super=0|1`, `uptime=<TvClock.uptimeNow>`, `activation=<jeton cbx1 de l'activation de production qui compte>` (ou absent).

**Vérification sur le téléphone** (`TvProof.verify`, ordre fixe, première raison) : enveloppe lisible ; type `proof` ; `nonce` = celui émis **et non consommé** (une seule réponse par défi, défis périmés après 10 min sur horloge monotone) ; signature valide **par la clé d'installation épinglée** pour cette TV (`ProofStore`), ou, si aucune n'est épinglée, **première liaison sécurisée** (RFCOMM appairé, ou HTTP avec jeton de téléphone de confiance **et** PIN de connexion saisi) → épinglage (TOFU) avec affichage de l'empreinte sur les deux écrans ; `state == production` ; `activation` présente, **vérifiée hors ligne** par `ActivationVerifier(KeyRing(TRUSTED_KEYS), expect = Subject.TV)` **sans contrôle de matériel** (le téléphone n'a pas les facteurs) mais avec `DeviceCode.of(Fingerprints(a.factors)) == code` **et** `Envelope.Target.Device` de la preuve égal au jeu de facteurs de l'activation ; `kind == PRODUCTION` ; aucun plafond `usage` dépassé à `max(horloge du téléphone, issuedAt)` ; pas de `Right.Super` exigé. **Résultat** : `Proof(tvCode, tvName, installKid, endsAt?, superUnlimited, verifiedAt, seqSeen)`.

**Cache** (`ProofCache`) : une preuve vaut **14 jours** à partir de `verifiedAt` (D-W6-5, réglable 1-30 par `ProofPolicy.VALIDITY_DAYS`) et **jamais au-delà de `endsAt`** de l'activation ; horloge : réutilise `TvClock` (haut-fond + temps monotone + uptime cumulé, `C/owner/Keys.kt:81`) sous le nom `PhoneClock = TvClock` persisté dans `files/proof/clock.txt` : un recul d'horloge n'allonge rien, un saut en avant de plus de 45 jours suspend (état `Minimal(reason = CHECK_CLOCK)`). **Re-synchronisation** : à chaque liaison avec la TV (`TvLinkManager` → `ProofSync`), à l'ouverture de l'app si > 24 h, par bouton ; rappel à J-3 (« Rapprochez-vous de votre TV pour garder toutes les fonctions »). **Expirée** → `Minimal` avec le message d'amélioration (§ 3.5), **aucune donnée supprimée** (lots, bibliothèque, progression restent sur le disque, comme pour la TV).

**Cas** : TV en **mode réduit** (clé terminée, W4) → `state=degraded` → la preuve est **refusée** et toute preuve antérieure de cette TV est **retirée** immédiatement (le téléphone apprend la fin avant les 14 jours) ; **plusieurs TV** : une preuve par TV, l'état est `Linked` si **au moins une** est valide ; **retirer une TV** de l'app supprime sa preuve et sa clé épinglée ; **réinstallation** du téléphone : tout est perdu, une liaison suffit ; **TV hors ligne des semaines** : le téléphone garde ses 14 jours puis retombe, et remonte à la première liaison ; **clé épinglée différente** (TV réinstallée : nouvelle clé d'installation) → la preuve est refusée avec « La TV a changé d'identité : confirmez-la » et un bouton qui ré-épingle **après** affichage de la nouvelle empreinte sur la TV (`ActivationActivity` ou `À propos`) ; **transfert de licence** : l'ancienne TV ne compte plus (révocation de poste) → à la prochaine synchro, `state != production`.

### 3.4 La porte dans le code

- `C/owner/PhoneGate.kt` : `enum class PhoneFeature(val minimalAllowed: Boolean = false, val agentAllowed: Boolean = false)` ; `MINIMAL_WHITELIST` et `AGENT_WHITELIST` **figés par test** (`PhoneGateTest.theMinimalSurfaceIsExactlyTheListedOne`, même discipline que `LOCKED_WHITELIST`) ; `data class ProofRequirement(val required: Boolean = false, val graceDays: Int = 14)` ; `sealed class PhoneGateState { NotRequired ; Grace(untilMs) ; Minimal(reason: MinimalReason) ; Linked(proofs: List<Proof>) ; Agent(delegationSummary) ; Super(session) }` ; `PhoneGate.state(req, proofs, nowMs, migration: FleetMigration?, superSession: SuperSession.State?, agent: AgentState?)` : `!required → NotRequired` ; `super actif → Super` ; `une preuve valide → Linked` ; `agent valide → Agent` ; grâce ; sinon `Minimal` ; `canUse(feature, state)`.
- **Interrupteur de compilation** `REQUIRE_TV_PROOF` (propriété Gradle `-PrequireTvProof=true`, `BuildConfig` de `:sender`), **éteint par défaut** exactement comme `REQUIRE_ACTIVATION` : on l'allume quand (a) w6-10 est sur la TV du propriétaire, (b) la preuve a été obtenue sur son téléphone avec l'interrupteur éteint, (c) la liste blanche a été relue. **Grâce absolue** pour les téléphones déjà installés : `FleetMigration.of(firstInstallTime, phone.lock.graceStartMs)` réutilisé tel quel, `phone.lock.graceDays=14` dans `version.properties`.
- **Android** : `S/gate/PhoneGateRuntime.kt` (état `StateFlow`, recalcul à chaque changement de preuve/session/liaison ; expose `targetTv()` et `refusal(feature, tv)` qui délègue à `PhoneGateTexts`, § 3.8) ; `S/gate/GateWall.kt` (composable `GateWall(feature, tv?) { contenu }` : affiche le mur § 3.5 avec le message du catalogue, l'âge de la preuve et les actions, à la place du contenu, jamais un simple `enabled=false`) ; `MainActivity` : les onglets fermés **restent visibles** avec un cadenas et ouvrent le mur (pas d'interface qui disparaît sans explication) ; **chemin des données** : `LotsRuntime` n'accepte que `LotEditions.TRIAL` sans `Linked` (et le serveur refuse les lots complets sans `X-CB-TV-Proof`), `DownloadService.start` refuse, `ShopRuntime` n'émet aucune demande, `PlaybackService` du téléphone refuse de démarrer une lecture locale, **la file de transfert refuse d'enfiler** tout envoi de média vers une TV non prouvée (`SendGuard`, § 3.7 : `TransferQueue`, `TransferQueueService`, `UploadService`, `BtUploadService`, `FastTransfer`, `MoveToTv`, `DlnaHandoff`, `ShareToTvActivity` ; refus dans `onStartCommand`, pas seulement dans l'écran), `ParentalInbox.sync` **continue** (réception en fond) mais `ParentalTab` est derrière le mur. La **matrice** § 3.7 et le **catalogue** § 3.8 sont la seule source de vérité : un écran n'invente ni règle ni texte.
- **Serveur** : en-tête `X-CB-TV-Proof: <jeton d'activation de production>` (le jeton lui-même, comme W5 § 2 (2)) exigé par `GET /api/v1/lots/catalog?edition=full`, `GET /api/v1/lots/{…}` d'un lot complet, toutes les routes `/api/v1/shop/**` et `/api/v1/tokens/**` ; vérifié par le chemin déjà prévu (`EnvelopeVerifier`, `ShopRequestVerifier` de w5-07) ; un essai renvoie 403 « Cette fonction demande une TV activée en production ». Les contenus libres (`/api/v1/free-content`) et les lots d'essai (`edition=trial`) **ne demandent rien**.

### 3.5 Le mur (textes, `PhoneGateTexts`)

- Titre : « Connectez-vous à une TV activée en production pour tout débloquer ».
- Corps : « CastBridge offre ses fonctions complètes aux téléphones liés à une CastBridge-TV qui a une clé de production. Approchez-vous de votre TV et liez-la ; si elle est en version d'essai, demandez une clé de production avec son code d'appareil. » Boutons : **« Lier une TV »** (TvPairScreen), **« Obtenir une clé de production »** (partage du code d'appareil : `LockedTexts.shareMessage`), **« Télécharger tous les contenus libres »** (toujours là).
- États particuliers : preuve expirée (« Votre dernière synchronisation avec <TV> date de N jours : rapprochez-vous d'elle. ») ; TV d'essai liée (« <TV> est en version d'essai : le téléphone reste en mode minimal. ») ; mode réduit (« La clé de <TV> est terminée : renouvelez-la pour retrouver toutes les fonctions. ») ; horloge douteuse (« Vérifiez l'heure du téléphone. »). Jamais de compte à rebours anxiogène ; une ligne discrète dans Réglages : « Mode : minimal / complet (lié à <TV>, jusqu'au JJ/MM) / super administrateur / point focal ».

### 3.6 Interactions

| Avec | Effet |
|---|---|
| Boutique W5 (`S/shop/**`) | `SHOP` fermé sans preuve **et** `X-CB-TV-Proof` côté serveur (déjà la preuve W5 : un seul jeton, deux usages) ; en `Linked`, la TV choisie pour la commande est l'une des TV prouvées |
| Lots (`LotsRuntime`) | sans preuve : lots `-trial` seulement, vers une TV à clé d'essai ; en `Linked` : tout ; `OwnedLots`/`NOT_ENTITLED` inchangés |
| Tunnel d'assistance | la passerelle Internet (`INTERNET_GATEWAY_FOR_TV`) reste ouverte (il faut pouvoir activer une TV et la mettre à jour) ; le **relais du tunnel** (`REMOTE_TUNNEL_GATEWAY`) exige `Linked` (une TV assistée est une TV cliente) ; **jamais** de données parentales par le tunnel |
| Anti-sabotage (protect-*) | la porte du téléphone est **une porte d'interface + un refus de service local** ; honnêteté : un APK modifié l'ôte ; la valeur (lots complets, locations, jetons) est protégée par le serveur et par les lots chiffrés par TV ; le lecteur/jeux du téléphone ne valent pas une protection plus coûteuse |
| Mode réduit W4 | `degraded` ⇒ pas de preuve ; message dédié |
| Point focal (W4-C/W5) | état `Agent` (délégation vérifiée, `S/focal/**`) : `AGENT_WHITELIST` = minimal + vente de clés + bons + confirmation de commandes + lecture de la demande d'une TV ; **jamais** `Super`, jamais lecteur/jeux/parental par ce biais |
| Contenus libres | toujours ouverts ; le bouton figure **sur le mur** |
| Super administrateur | § 4 |

### 3.7 Matrice fonction × état de la TV appariée (complément du propriétaire, 2026-10-02)

> « Le téléphone ne pourra pas, par exemple, copier une vidéo si la télé appariée n'est pas une version de production ; les messages sur le déblocage de fonctionnalités dépendront des synchros associées. »

**Deux familles de fonctions.** (i) **Fonctions tournées vers une TV** (copier un média, gérer sa bibliothèque, diffuser, piloter Apprendre, livrer des lots, administrer) : la cellule se juge **sur la TV cible** de l'action (celle de la liaison en cours, ou celle choisie dans l'écran), avec **sa** preuve ; (ii) **fonctions propres au téléphone** (lecteur, jeux, Apprendre, téléchargements, boutique, tableau de bord parental) : ouvertes dès qu'**au moins une** TV appariée a une preuve de production valide. La TV reste **toujours** le dernier juge pour (i) (son édition, ses règles parentales) : le téléphone refuse **avant** d'envoyer quand il sait, et **transmet le refus de la TV** quand il ne savait pas.

Colonnes : **A** aucune TV liée · **B** TV liée jamais synchronisée · **C** synchronisée, **essai** (ou grâce) · **D** synchronisée, **production**, preuve fraîche · **E** synchronisée, **clé terminée** (mode réduit W4) · **F** TV injoignable, **preuve en cache valide** (≤ 14 j) · **G** preuve **expirée** · **H** session **super administrateur**. ✔ ouvert · ✖ fermé (identifiant du message, § 3.8) · ◐ partiel · « TV » = ouvert, la TV décide en direct (son refus est transmis tel quel).

| Fonction (`PhoneFeature`) | A | B | C | D | E | F | G | H |
|---|---|---|---|---|---|---|---|---|
| Lier / appairer / dépanner une TV, partager le code d'appareil, porter une clé (`TV_PAIRING`, `SHARE_DEVICE_CODE`, `CARRY_ACTIVATION_FOR_TV`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| **Télécharger tous les contenus libres** (`FREE_CONTENT_DOWNLOAD`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Aide, confidentialité, langue, consentement télémétrie, mise à jour du téléphone | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Partage de la connexion Internet avec la TV (`INTERNET_GATEWAY_FOR_TV`) | ✖ M-NO-TV | ✔ | ✔ | ✔ | ✔ | ✖ M-TV-UNREACHABLE | ✔ | ✔ |
| **Diffuser en direct** vers la TV, télécommande, volume (`CAST_TO_LINKED_TV`) : la TV d'essai l'accepte (TrialPolicy), le mode réduit aussi (DegradedPolicy `/api/playurl`) | ✖ M-NO-TV | TV | TV | ✔ | TV | ✖ M-TV-UNREACHABLE | TV | ✔ |
| Ouvrir / piloter **Apprendre sur la TV** (`LEARN_REMOTE`) | ✖ M-NO-TV | TV | TV (lots d'essai) | ✔ | TV | ✖ M-TV-UNREACHABLE | TV | ✔ |
| **Copier / envoyer un média vers la TV** (Wi-Fi, Bluetooth, Wi-Fi Direct, DLNA « déplacer », file de transfert, multi-chemins) (`SEND_FILES_TO_TV`) | ✖ M-NO-TV | ✖ **M-SYNC-FIRST** | ✖ **M-TV-TRIAL** | ✔ | ✖ **M-TV-ENDED** | ✖ M-TV-UNREACHABLE | ✖ **M-PROOF-EXPIRED** | TV |
| Parcourir la bibliothèque de la TV, lire un média de la TV sur le téléphone (`TV_LIBRARY_BROWSE`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ✔ (lecture : W4 ouvre `/api/library`) | ✖ M-TV-UNREACHABLE | ✖ M-PROOF-EXPIRED | TV |
| Gérer les fichiers de la TV : renommer, déplacer, supprimer, dossiers, clé USB (`TV_LIBRARY_MANAGE`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ✖ M-TV-ENDED | ✖ M-TV-UNREACHABLE | ✖ M-PROOF-EXPIRED | TV |
| **Livraison de lots** à la TV (`LOTS_SYNC_TRIAL` / `LOTS_SYNC_FULL`) | ✖ M-NO-TV | ◐ lots d'essai (M-TRIAL-LOTS-ONLY) | ◐ lots d'essai (M-TRIAL-LOTS-ONLY) | ✔ | ✖ M-TV-ENDED (W4 : aucun lot entrant) | ✔ téléchargement du serveur + file d'attente ; livraison différée | ◐ lots d'essai (M-PROOF-EXPIRED pour le reste) | ✔ |
| **Boutique** W5 : consulter (`SHOP_BROWSE`) | ✖ M-NO-TV | ◐ lecture | ◐ lecture | ✔ | ◐ lecture | ✔ | ◐ lecture | ✔ |
| Boutique : commander, saisir un bon, acheter des jetons (`SHOP_ORDER`, `TOKENS`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL (M-SHOP-READ-ONLY) | ✔ (+ `X-CB-TV-Proof` au serveur) | ✖ M-TV-ENDED | ✔ (le serveur revérifie le jeton) | ✖ M-PROOF-EXPIRED | ✔ |
| Téléchargements du téléphone (HTTP, magnet) (`DOWNLOADS`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ✖ M-TV-ENDED | ✔ | ✖ M-PROOF-EXPIRED | ✔ |
| Lecteur et bibliothèque **du téléphone** (`PHONE_LIBRARY_PLAYER`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ✖ M-TV-ENDED | ✔ | ✖ M-PROOF-EXPIRED | ✔ |
| Apprendre, Quiz, Échecs, jeux **sur le téléphone** (`LEARN_PHONE`, `QUIZ_PHONE`, `CHESS_PHONE`, `GAMES_PHONE`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL (« pendant l'essai, utilisez Apprendre sur la TV ») | ✔ | ✖ M-TV-ENDED | ✔ | ✖ M-PROOF-EXPIRED | ✔ |
| **Tableau de bord parental** (copie locale, détenteurs) (`PARENTAL_DASHBOARD`) ; la réception des rapports en fond continue toujours | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | **✔** (la sécurité des enfants ne s'arrête pas avec la clé : W4 garde `PARENTAL` ouvert sur la TV) | ✔ | ◐ **lecture seule** de la copie locale (règle existante « TV hors de portée ») | ✔ si désigné (D-W6-3) |
| **Règles parentales de la TV** (profils, horaires, applications, détenteurs) (`PARENTAL_RULES`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL (les régler sur la TV) | ✔ | ✔ | ✖ M-TV-UNREACHABLE | ✖ M-PROOF-EXPIRED | TV |
| Administration de la TV : installer des APK, redémarrer, SSH, routes Bluetooth (`TV_ADMIN`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ◐ **mise à jour de CastBridge-TV seulement** (W4 : `UPDATES` ouvert pour pouvoir renouveler) | ✖ M-TV-UNREACHABLE | ✖ M-PROOF-EXPIRED | TV |
| Relais du tunnel d'assistance à distance (`REMOTE_TUNNEL_GATEWAY`) | ✖ M-NO-TV | ✖ M-SYNC-FIRST | ✖ M-TV-TRIAL | ✔ | ✔ (une TV cliente en fin de clé reste assistable) | ✖ M-TV-UNREACHABLE | ✖ M-PROOF-EXPIRED | ✔ |
| Assistant IA (`ASSISTANT_IA`) | ✖ | ✖ | ✖ | ✔ | ✖ | ✔ | ✖ | ✔ |
| Devenir / être point focal (`FOCAL_ENTRY`, propre porte = délégation) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Entrée super administrateur (`SUPER_ADMIN_ENTRY`, build propriétaire seulement) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | — |

Règles de lecture : **H** n'annule jamais un refus **de la TV** (édition, parental) : la session super ouvre le téléphone, pas la TV. **E** : « clé terminée » vaut aussi pour un essai terminé (TV `Locked`) avec le message **M-TV-LOCKED** à la place de M-TV-ENDED. Grâce (installation ancienne du téléphone, 14 j) : les colonnes se lisent comme **D** pour le téléphone, la TV restant juge.

**Garde sur le chemin des données (copie de médias).** Trois barrières, dans l'ordre : (1) l'**interface** (`GateWall`, bouton « Envoyer » remplacé par le mur) ; (2) la **file de transfert** : `TransferQueue.enqueue` (cœur, `C/xfer/**`) reçoit un `TargetTvState` et **refuse d'enfiler** (`Refused(message)`) un envoi vers une TV dont l'état n'est pas `PRODUCTION_PROVEN` ; `TransferQueueService.onStartCommand`, `UploadService`, `BtUploadService`, `FastTransfer`, `MoveToTv`, `DlnaHandoff` et `ShareToTvActivity` (partage depuis une autre app) passent tous par cette file ou par la même garde `SendGuard.check(tv)` ; rien n'est **mis en attente** pour une TV non prouvée (une TV d'essai qui passera en production plus tard ne reçoit pas de file fantôme : l'utilisateur recommence) ; (3) la **TV** (`TrialPolicy`/`DegradedPolicy` ferment déjà `/api/transfer`, `/api/upload`, `/upload`, Bluetooth fichiers, USB). Un refus de la TV malgré (1)-(2) (course : la clé a expiré entre deux synchros) est transmis tel quel (M-TV-REFUSED) et déclenche une synchronisation.

### 3.8 Machine d'états de synchronisation et catalogue des messages (un seul endroit dans le code)

`C/owner/PhoneSync.kt` (pur, testé) : l'état de synchronisation **par TV** :

```
NEVER_PAIRED ─lier──► PAIRED_NEVER_SYNCED ─liaison──► SYNCING ─preuve OK──► SYNCED_FRESH(ageMs)
                                                      │                        │ âge > 7 j
                                                      │ échec                  ▼
                                                      ▼                   SYNCED_STALE(ageMs)   ─ âge > validité ──► EXPIRED(ageMs)
                                              SYNC_FAILED(reason)
reason ∈ { BT_OFF, TV_UNREACHABLE, WRONG_NETWORK, CLOCK_DOUBT, TV_OLD_VERSION, PROOF_REJECTED(rejection), TV_TRIAL, TV_GRACE, TV_LOCKED, TV_DEGRADED, TV_SUSPENDED, IDENTITY_CHANGED }
```
Chaque état porte l'**âge de la dernière preuve valide** (`lastProofAt`, affiché « il y a N j ») et, pour `SYNC_FAILED`, l'état TV connu avant l'échec (une TV de production injoignable reste **F**, pas **G**). Transitions sur horloge monotone (`TvClock`). `SYNC_FAILED(TV_TRIAL|TV_DEGRADED|TV_LOCKED)` sont des **échecs de preuve, pas de liaison** : la liaison est bonne, la TV n'est pas en production.

`C/owner/PhoneGateTexts.kt` : **`refusal(feature: PhoneFeature, tv: TvSummary?, sync: PhoneSync.State, nowMs): Refusal?`** → `null` (ouvert) ou `Refusal(id, title, body, actions: List<Action>, proofAgeText?)`, où `Action ∈ { PAIR_TV, SYNC_NOW, RETRY, GET_PRODUCTION_KEY (partage du code d'appareil), OPEN_TV_UPGRADE (« l'écran « Passer en production » de la TV »), RENEW_KEY, ACTIVATE_TV, ENABLE_BLUETOOTH, OPEN_WIFI_SETTINGS, CHECK_CLOCK, CONFIRM_TV_IDENTITY, UPDATE_TV, FREE_CONTENT, NONE }`. **Tous les écrans** (mur, boutons grisés, services qui refusent, notifications) passent par cette fonction : **aucune chaîne de refus écrite ailleurs** (test : `grep -rn "exige une TV" S/` ne touche que `PhoneGateTexts` et ses appels). Les noms de TV et les nombres sont injectés ; chaque message dit **ce qui manque** et **quoi faire** :

| id | Message (titre · corps) | Actions |
|---|---|---|
| M-NO-TV | « Aucune TV liée » · « Liez votre CastBridge-TV pour utiliser cette fonction. Les contenus libres restent téléchargeables. » | PAIR_TV, FREE_CONTENT |
| M-SYNC-FIRST | « Vérification nécessaire » · « Synchronisez le téléphone avec <TV> pour vérifier son activation. » | SYNC_NOW |
| M-SYNCING | « Vérification en cours » · « Le téléphone vérifie l'activation de <TV>… » | NONE |
| M-TV-TRIAL | « TV en version d'essai » · « Cette fonction exige une TV en version de production. <TV> est en essai : passez en production depuis l'écran « Passer en production » de la TV, ou partagez son code d'appareil <code> pour obtenir une clé. » | OPEN_TV_UPGRADE, GET_PRODUCTION_KEY |
| M-TV-GRACE | « TV sans clé (période de grâce) » · « <TV> fonctionne encore sans clé jusqu'au JJ/MM : elle compte comme un essai. Posez une clé de production pour tout débloquer. » | GET_PRODUCTION_KEY, ACTIVATE_TV |
| M-TV-LOCKED | « TV sans clé » · « <TV> n'a pas de clé d'activation. Posez d'abord une clé : « Activer la TV ». » | ACTIVATE_TV, GET_PRODUCTION_KEY |
| M-TV-ENDED | « Clé de la TV terminée » · « La clé de <TV> est terminée (mode réduit depuis le JJ/MM) : renouvelez-la pour retrouver cette fonction. Vos vidéos et vos achats restent lisibles sur la TV. » | RENEW_KEY |
| M-TV-SUSPENDED | « Heure de la TV à vérifier » · « <TV> demande de vérifier son heure ; l'activation est suspendue jusque-là (sur la TV : « l'heure est juste »). » | RETRY |
| M-PROOF-EXPIRED | « Preuve expirée » · « La preuve de <TV> a expiré (hors connexion depuis N jours) : rapprochez-vous de la TV et synchronisez. Rien n'a été supprimé. » | SYNC_NOW |
| M-PROOF-STALE (avertissement, pas un refus) | « Dernière synchronisation : il y a N jours » · « Encore M jours avant le mode minimal : rapprochez-vous de <TV> quand vous le pouvez. » | SYNC_NOW |
| M-TV-UNREACHABLE | « TV injoignable » · « <TV> est éteinte ou hors de portée. Cette fonction a besoin de la TV. Dernière preuve : il y a N j (valide). » | RETRY |
| M-BT-OFF | « Bluetooth éteint » · « Activez le Bluetooth du téléphone pour joindre <TV>. » | ENABLE_BLUETOOTH |
| M-WRONG-NETWORK | « Pas le même réseau » · « Le téléphone et <TV> ne sont pas sur le même Wi-Fi ; le Bluetooth n'est pas disponible. » | OPEN_WIFI_SETTINGS, RETRY |
| M-CLOCK-DOUBT | « Heure du téléphone à vérifier » · « L'heure du téléphone a sauté de plus de 45 jours : vérifiez-la, puis synchronisez. » | CHECK_CLOCK, SYNC_NOW |
| M-PROOF-REJECTED | « Preuve refusée » · « La preuve envoyée par <TV> a été refusée : <raison en français de `Rejection`>. » | RETRY, UPDATE_TV |
| M-IDENTITY-CHANGED | « La TV a changé d'identité » · « <TV> ne signe plus avec la même clé (réinstallée ?). Comparez l'empreinte <xxxx-xxxx> avec « À propos » de la TV, puis confirmez. » | CONFIRM_TV_IDENTITY |
| M-TV-OLD-VERSION | « TV à mettre à jour » · « <TV> n'a pas la dernière version de CastBridge-TV : mettez-la à jour pour vérifier son activation. » | UPDATE_TV |
| M-TV-REFUSED | « Refusé par la TV » · « <TV> a refusé : « <message de la TV> ». » (TrialPolicy, DegradedPolicy, parental) | SYNC_NOW |
| M-PARENTAL-BLOCKED | « Mode enfant sur la TV » · « La TV est en mode enfant (<prénom>) : <raison>. Le code parental se saisit sur la TV. » | NONE |
| M-TRIAL-LOTS-ONLY | « Lots d'essai seulement » · « Pendant l'essai, seuls les lots d'essai sont envoyés à <TV>. Les lots complets demandent une clé de production. » | GET_PRODUCTION_KEY |
| M-SHOP-READ-ONLY | « Boutique en consultation » · « Vous pouvez consulter la boutique ; commander demande une TV en production. » | GET_PRODUCTION_KEY |

Tests JVM exigés (`PhoneGateTextsTest`, `PhoneSyncTest`) : chaque cellule ✖/◐ de la matrice § 3.7 a son identifiant (**table figée** `EXPECTED_MATRIX` : une cellule qui change fait échouer le test) ; chaque message cite le nom de la TV quand il est connu ; aucun message ne contient d'adresse, de jeton ni de code PIN ; les transitions de `PhoneSync` (âges, expiration à 14 j, recul d'horloge, saut en avant, échec qui garde l'état F) ; `refusal()` est déterministe et sans Android.

### 3.9 Plusieurs TV, TV d'essai + TV de production, téléphone utilisé par un enfant

- **Plusieurs TV** : état par TV (`PhoneSync`, `ProofCache`) ; les fonctions **propres au téléphone** (colonne (ii)) s'ouvrent si **au moins une** TV a une preuve valide (**pas** « la plus stricte » : un foyer qui a une TV de production et une TV d'essai ne doit pas perdre le lecteur du téléphone) ; les fonctions **tournées vers une TV** se jugent **sur la TV cible** de l'action. L'écran principal affiche la **TV active** (celle de la liaison, sinon la TV par défaut `TvLinkManager.saved.default()`) avec une puce **« TV cible : <nom> · production (preuve il y a 2 h) »** ou **« · essai »**, et un sélecteur quand il y en a plusieurs.
- **Essai + production appariées** : le lecteur, les jeux, Apprendre, la boutique, le parental sont ouverts (preuve de la TV de production) ; **copier une vidéo vers la TV d'essai** est refusé **M-TV-TRIAL en nommant cette TV**, avec « Envoyer plutôt vers <TV de production> » comme action supplémentaire ; les lots d'essai partent vers la TV d'essai, les lots complets vers la TV de production.
- **Téléphone utilisé par un enfant** : le téléphone n'a pas de profils enfants (ils vivent sur la TV). Trois garde-fous : (1) un téléphone d'enfant n'est **jamais détenteur** (désignation = code parental **et** consentement sur la TV) : il ne reçoit aucun rapport et son onglet Parental demande le code parental vérifié **par la TV** (existant) ; (2) quand la TV active est en **mode enfant**, le téléphone affiche un bandeau « La TV est en mode enfant (<prénom>) » (lu dans `GET /api/parental`, sans secret) et transmet les refus parentaux de la TV (M-PARENTAL-BLOCKED) sans jamais proposer de saisir le code parental **sur le téléphone** (il se saisit sur la TV, comme aujourd'hui) ; (3) l'état `Linked` **ne dépend pas** de qui tient le téléphone : un enfant avec le téléphone d'un parent a le lecteur du téléphone (comme n'importe quelle application du téléphone) ; les contenus des lots suivent les règles d'âge **de la TV** quand ils y sont joués, et le téléphone **n'offre pas** de bibliothèque TV ni de copie en mode enfant si la TV les bloque (TV juge). Décision architecte : pas de « mode enfant du téléphone » en W6 (hors périmètre, à étudier avec le lecteur du téléphone).

## 4. Volet C — Mode total du super administrateur

### 4.1 Ce qui existe et ce qui manque

Le mot de passe (`SuperAdminGate`) ouvre l'écran console et le coffre ; la console se verrouille à 2 min d'inactivité et quand elle quitte l'écran (`ConsoleActivity:48-54`) ; **le reste de l'application ne connaît pas cet état**. W6 ajoute une **session** explicite, visible, limitée, que `PhoneGate` lit.

### 4.2 `SuperSession` (cœur)

- Ouverture : **après** `SuperAdminGate.Result.Open` **et** `OwnerStore.unlock/create` réussi (les deux facteurs existants) ; l'utilisateur voit « Ouvrir une session super administrateur (12 h) » avec le choix 1 h / 4 h / 12 h (défaut 12 h, plafond 24 h). La session n'est **pas** ouverte par le seul fait d'entrer dans la console.
- Représentation : `SuperSession.State(openedAt, untilMs, nonce)` persistée dans `files/super-session.txt` **scellée** par `SecretWrapper` sous une clé du `KeystoreWrapper` du téléphone (liée à l'appareil ; `setUserAuthenticationRequired` si disponible : la session s'invalide quand l'écran de verrouillage du téléphone est retiré). Horloge : `TvClock` (monotone) ; un recul d'horloge **n'allonge pas** la session ; un saut en avant la **termine**.
- Fermeture : expiration ; bouton « Fermer la session » dans la console et dans le bandeau ; verrouillage automatique de la console (2 min) **ne ferme pas** la session (sinon elle ne servirait à rien) ; 5 échecs de mot de passe consécutifs la ferment ; désinstallation/« effacer les données » la fait disparaître.
- **Bandeau permanent** dans CastBridge tant que la session est ouverte : « Session super administrateur jusqu'à HH:MM · Fermer » (FLAG_SECURE sur les écrans de la console seulement ; le bandeau est public : c'est voulu).

### 4.3 Qui, et quelle protection

- **Seule une build propriétaire** (`-Pcastbridge.superAdmin=true`) possède le haché ; `SuperAdmin.enabled` est faux ailleurs et le geste caché ne réagit pas (déjà). W6 ajoute un **contrôle de publication** : `tools/release/check_no_superadmin.sh <apk>` échoue si l'APK contient un haché bcrypt (`\$2[abxy]\$\d\d\$`) ou une chaîne `SUPERADMIN_BCRYPT` non vide ; branché dans `android.yml`/`release.yml` sur les artefacts distribuables, et dans `docs/RELEASES.md`.
- **Le haché n'est pas une frontière de sécurité** (déjà dit dans `SuperAdminGate.kt`) : la session **ne donne aucun pouvoir sur une TV ni sur le serveur** ; les pouvoirs réels restent la clé du coffre (signatures) et les jetons du serveur. Un APK rapiécé obtient un téléphone « complet » à ses propres yeux : c'est le même risque résiduel que pour toute porte d'interface (§ 3.6), accepté.
- Les points focaux n'ont **jamais** la build propriétaire ; leur état `Agent` est borné par la délégation signée.

### 4.4 Ce que « total » comprend (décision architecte)

| Inclus | Exclu (et pourquoi) |
|---|---|
| Toutes les `PhoneFeature` **sans preuve de TV** (lecteur, jeux, Apprendre, lots complets vers n'importe quelle TV, envoi de fichiers, admin de TV, tunnel) | — |
| Console d'émission (existant : essai, production, SUPER_UNLIMITED, transfert, révocation) | **`SUPER_UNLIMITED` reste une portée de clé pour les TV** : la session n'installe rien sur une TV ; pour que la TV du propriétaire lise tout, il émet une clé `super` comme aujourd'hui |
| Outils boutique du propriétaire (W5 : grille, bons, « geste propriétaire » signé, confirmation de commandes sans plafond) | les actions **serveur** restent derrière l'admin TOTP du serveur (`/admin/shop`) : la session téléphone n'est pas une session serveur |
| Rapports parentaux de **ses propres TV** par la voie normale (désignation + consentement) | **Rapports parentaux de n'importe quelle TV : NON (D-W6-3).** Une TV cliente ne doit livrer ses données de foyer qu'à un détenteur désigné avec le code parental et le consentement affiché : l'éditeur n'est pas un parent. Un pouvoir « lire tous les rapports » serait une porte dérobée au sens de CONDITIONS-ASSISTANCE-A-DISTANCE et de la loi 2024/017 |
| Diagnostic de TV, remise à zéro de l'essai (commandes `SUPPORT` signées, existantes) | — |

### 4.5 Perte du téléphone du super administrateur

Procédure (documentée par w6-19 dans OWNER-CONSOLE) : (1) la **clé du coffre** du téléphone perdu est révoquée par l'outil de bureau (portée `REVOKE`, liste `cbr1` diffusée par battement de cœur, relais téléphone w3-09, fichier USB) ; (2) la **session** n'a aucune valeur hors du téléphone (scellée au matériel) ; (3) le **mot de passe** doit être changé : il est un haché **de compilation** → reconstruire la build propriétaire avec un nouveau haché ; (4) **nouveau téléphone** : nouvelle build, nouveau coffre, **nouvelle ligne publique** à ajouter à `activation-trusted-keys.txt`, puis **mise à jour des TV** pour qu'elles acceptent la nouvelle clé (les clés de confiance sont compilées : limite connue). **Risque** signalé : tant que les TV n'ont pas la nouvelle clé, seule la **clé de secours hors ligne** (OWNER-CONSOLE § 2.5) peut émettre ; recommandation hors W6 : un message signé `keyring` (ajout de clé par la clé de secours) pour éviter de recompiler les TV.

## 5. Transversal

- **Plan des écrans existants** : `S/ParentalScreen.kt` (règles) reste et gagne les options W6 ; `S/ParentalTab.kt` est réorganisé (§ 2.8) ; `S/ParentalWholeTv.kt` est absorbé par la nouvelle section « Toute la TV » (fichier conservé pour les anciennes TV v1, section marquée « ancien format ») ; `R/ParentalHub.kt` reçoit les crochets `note()`, le `ScreenSegments`, les tranches et le Sudoku ; `R/ParentalActivity.kt` : page « Rapports vers le téléphone du parent » devient « Téléphones des parents » avec le consentement ; `R/SupervisionSetupActivity.kt` inchangé.
- **Principe hors ligne** : TV et téléphone ne requièrent jamais le serveur pour le parental ni pour la preuve ; le serveur n'intervient que pour **livrer** (lots complets, boutique) et y applique la même preuve.
- **Budgets** : TV ≤ 300 Ko pour le parental (journal, tranches, outbox, agrégats) ; téléphone : `ParentalLedger` 20 000 événements / 6 000 faits inchangés + tranches (≤ 1 Mo), magasin de preuves < 10 Ko ; aucun service permanent nouveau.
- **Accessibilité** : textes en français simple, chaque indicateur accompagné d'un mot, contrastes du thème, pas de couleur seule, tailles `sp`, boutons ≥ 48 dp ; sur la TV, l'indicateur suit `KeyBadge` (lisible à 3 m, `w2-04`).
- **Tests sans appareil (JVM, `:core:test`)** : `PhoneGateTest` (listes figées, états, grâce, super > linked > agent > minimal), `TvProofTest` (vecteurs : preuve valide, nonce rejoué, clé non épinglée, essai, dégradé, activation périmée, code ≠ facteurs, signature fausse, horloge reculée/avancée), `ProofCacheTest`, `SuperSessionTest` (durées, horloge, fermeture), `CollectTest` (tranches, segments, bornes, changement de jour, écran éteint), `HoldersTest` (consentement en attente, révocation, 3 max, perte de confiance), `ReportV2Test` (champs, liste noire `NEVER`, signature + HMAC, ancien téléphone), `ReportSyncV2Test` (CBTP v1/v2, HTTP détenteur), `WholeTvTest` (agrégation tranches/semaine/trous), `HolderHttpTest`. Vecteurs : `tools/activation/proof-vectors.json` rejoués en Kotlin, Java (serveur) et Python.
- **Effort** : § 8 et l'index (≈ 43 agent·jours sur 5 sous-vagues).

## 6. Sécurité, vie privée, droit (indicateurs, pas un avis juridique)

| Sujet | Analyse | Mesure W6 |
|---|---|---|
| Surveillance du foyer (adultes compris) | Rapporter **toute** la TV au détenteur, c'est surveiller tout utilisateur de la TV, y compris des adultes qui ne sont pas « l'enfant ». Loi camerounaise 2024/017 (information, finalité, minimisation) ; dans le monde OHADA et en UE, la surveillance d'un adulte sans information est un risque pénal/civil ; les magasins d'applications bannissent les logiciels de surveillance **cachés** (Google Play : *stalkerware*, exigence d'un **indicateur persistant** et d'un consentement). | **Indicateur permanent non désactivable**, consentement sur la TV avec mention « tout le foyer informé », alerte aux détenteurs à chaque ajout/retrait, journal des consultations en direct, texte d'information du foyer (w6-20), aucun mode discret, données locales uniquement |
| `PACKAGE_USAGE_STATS` | Accès spécial : exige une **divulgation proéminente** dans l'app (pourquoi, quoi) avant l'écran système, et dans la fiche du magasin si un jour publiée ; la finalité « contrôle parental » est admise. | Écran « Surveillance de toute la TV » (existant) + bloc « Ce que nous lisons / ce que nous ne lisons jamais » avant le lien vers les réglages ; w6-20 rédige le texte magasin |
| Mineurs | Les données d'un enfant ne vont **qu'à** ses parents (détenteurs), jamais à l'éditeur ; un profil enfant ne les voit pas ; les titres de vidéos peuvent être coupés par profil. | `ParentalPrivacy`, option `shareTitles`, aucune route serveur |
| Séparation éditeur / foyer | Le tunnel d'assistance donne à l'éditeur un accès au système de la TV. Sans chiffrement, les données parentales seraient lisibles par un expert distant : contraire à la clause « n'accède pas au contenu personnel » et aux attentes. | Magasin parental chiffré sous Keystore (§ 2.4), routes parentales interdites au tunnel, test `grep` « aucun TvConnect dans parental » maintenu |
| Mode minimal du téléphone | Restreindre l'app sans TV en production est une **politique commerciale** licite si elle est **annoncée** (avis d'usage) et si rien n'est supprimé ; les contenus libres CC BY-SA restent accessibles (obligation de licence sans rapport avec l'activation, mais cohérence). | Mur explicatif, aucune suppression de données, contenus libres sur le mur, texte de l'avis d'usage mis à jour (w6-19/20) |
| Super administrateur | Un état « tout ouvert » sur un téléphone de l'éditeur ne touche aucune donnée client **par conception** (D-W6-3) : il n'y a rien à divulguer aux clients ; à documenter dans la politique interne. | Session visible, limitée, journalisée (`AuditChain`), contrôle de publication |
| Horloge, rejeu | Preuve au défi (nonce unique, 10 min), `seq` croissant par TV, cache borné par `endsAt`, `TvClock` monotone sur le téléphone. | `TvProofTest`, `ProofCacheTest` |
| Épinglage de la clé d'installation | TOFU sur un lien sécurisé ; une TV réinstallée change de clé → confirmation explicite avec empreinte affichée des deux côtés. | § 3.3 |
| Ce qui reste contournable | Porte d'interface du téléphone (APK modifié) ; un enfant avec accès aux Réglages de la TV affaiblit la surveillance (déjà détecté et signalé). | dit dans les docs, jamais promis autrement |

## 7. Ce qui change par rapport aux conceptions et décisions antérieures (liste explicite)

1. **Décision du 2026-10-01 (TRIAL-EDITION § 14) « le téléphone reste ouvert »** → **remplacée** : mode minimal jusqu'à la preuve d'une TV en production ; mise en œuvre par interrupteur de compilation `REQUIRE_TV_PROOF` (éteint par défaut) et grâce absolue de 14 jours pour les téléphones existants. Le « droit propre du téléphone » (TRIAL-EDITION § 11.4, `subject=phone`) **n'est pas utilisé** pour cette porte : la preuve vient de la TV ; une activation `PHONE` reste possible plus tard sans conflit.
2. **PARENTAL.md « Rapports : Bluetooth uniquement, pas de Wi-Fi »** → le Wi-Fi local est ajouté (identité par jeton de téléphone de confiance). Le paragraphe « le HTTP de la TV ne sait pas quel téléphone il a en face » est caduc.
3. **PARENTAL.md « Qui reçoit »** → la désignation exige en plus un **consentement validé sur la TV** ; `reports/recipients/*` reste un alias de `holders/*`.
4. **DESIGN-W5 § 2 (2), § 4.5, § 15 (`GET /api/activation/proof` dans w5-16 ; `ActivationProof` dans w5-04)** → W6 **remplace** la route non signée par `GET /api/activation/proof?nonce=` (réponse `cbx1` type `proof`, w6-10) ; le serveur continue de recevoir **le jeton d'activation brut** dans `X-CB-TV-Proof` (W5 inchangé côté serveur) ; si w5-16 est déjà fusionné, w6-10 le remplace (route sans nonce supprimée).
5. **DESIGN-W5 § 7 (UX téléphone boutique)** → l'onglet Boutique est derrière le mur `SHOP` ; aucun autre changement.
6. **W4-B mode réduit (TV)** → le téléphone tombe en `Minimal` quand sa seule TV est en mode réduit ; W4 ne disait rien du téléphone.
7. **W4-C / W5 point focal** → état `Agent` de `PhoneGate` ; `AGENT_WHITELIST` figée (w5-13 doit relire : le mode point focal ne donne pas le lecteur/jeux).
8. **OWNER-CONSOLE § 7 / « Sauvegarde et super-admin »** → ajout de la session, du bandeau, du contrôle de publication, de la procédure de perte ; le verrou de 2 min de la console ne change pas.
9. **TELEMETRY / CONDITIONS-ASSISTANCE** → ajout explicite : aucune donnée parentale au serveur ni au tunnel ; politique de confidentialité à compléter (information du foyer).
10. **w5-18** reste valable en entier ; W6 ajoute Sudoku au suivi (pas seulement à la catégorie) et reprend ses compteurs dans le v2.
11. **`Feature` (TV)** : inchangé. `PARENTAL` sur la TV reste ouvert en mode réduit (W4) : les parents gardent le contrôle même avec une clé terminée.

## 8. Effort, coût, risques

| Sous-vague | Contenu | Effort |
|---|---|---|
| 6a cœur | PhoneGate + matrice, PhoneSync + catalogue de messages, TvProof/ProofCache/InstallSigner + vecteurs, SuperSession, collecte, détenteurs/confidentialité, rapports v2, agrégation v2 | ≈ 15 j |
| 6b serveur, miroirs, build | preuve côté serveur (lots complets, boutique), Java/Python, interrupteur et grâce du téléphone, règles de sauvegarde | ≈ 4 j |
| 6c TV | clé de signature + route/trames de preuve, collecte et magasin chiffré, consentement et indicateur, Wi-Fi détenteur + CBTP v2 | ≈ 10 j |
| 6d téléphone | porte, mur et puce « TV cible », garde de la file de transfert et chemins de données, onglet Parental v2, session super + contrôle de publication | ≈ 12 j |
| 6e docs, droit, tests, CI | PARENTAL/PHONE-GATE/OWNER-CONSOLE/…, brouillons juridiques, campagne de test, CI | ≈ 5 j |

**Risques** : (R1) dépendance à w4-01/w4-03 (`SecretWrapper`, `KeystoreWrapper`) : si 4a n'est pas fusionnée, w6-02/w6-10/w6-11 implémentent un repli « clé en fichier privé » et le disent (état « protection logicielle ») ; (R2) `UsageStatsManager` absent ou refusé sur certaines TV : les tranches sont ◐/○ comme aujourd'hui, l'écran et CastBridge-TV restent ● ; (R3) allumer `REQUIRE_TV_PROOF` trop tôt verrouille le téléphone du propriétaire : procédure en trois temps (§ 3.4) ; (R4) coût de la réorganisation de `ParentalTab` (480 lignes) : w6-16 réutilise les composables existants ; (R5) juridique : textes à valider avant toute distribution hors du cercle de test (D-W6-4).

## 9. Décisions

**Prises par l'architecte (renversables)** : D-W6-1 liste minimale (§ 3.2, avec l'alternative stricte) ; D-W6-2 pas de relais distant en v1 (§ 2.9) ; D-W6-3 le super administrateur n'a pas accès aux rapports parentaux des TV qui ne l'ont pas désigné (§ 4.4) ; D-W6-5 preuve valable 14 jours, session super 12 h (24 max), grâce téléphone 14 jours ; titres de vidéos partagés par défaut avec option par profil ; `snapshot` journalisé sur la TV.

**BLOQUÉ (faits juridiques/commerciaux)** : **D-W6-4** validation par un juriste du texte d'information du foyer, de la divulgation `PACKAGE_USAGE_STATS` et du paragraphe de la politique de confidentialité (w6-20 produit les brouillons ; **ne pas distribuer** la fonction hors du cercle de test avant) ; **D7** (existant) nom commercial et contact pour les textes.
