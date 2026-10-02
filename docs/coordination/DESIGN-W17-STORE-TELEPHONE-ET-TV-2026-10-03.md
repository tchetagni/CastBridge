# Conception W17 : la Boutique sur le téléphone (CastBridge) et vue par la TV (CastBridge-TV), hors ligne, avec demande de location depuis la TV

> **Statut : conception (Fable, architecte, 2026-10-03). Rien n'est implémenté par ce document.** Exécution : cahiers `docs/agent-briefs/sonnet-w17-NN-*.md` (index `SONNET-WAVE17-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents` (tête `9e1c942`). Tous les fichiers et lignes cités ont été relus le 2026-10-03 ; ce qui n'a pas pu être vérifié est marqué **non vérifié** ou **BLOQUÉ**. Aucun montant, aucun secret, aucun texte juridique (reporté au 2026-12-31) ; aucune commande `gradle`/`adb` lancée.
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`.
>
> **Décisions du propriétaire, mot pour mot (2026-10-03).** « Je ne vois pas le store » puis « Le store devrait être aussi vu par la TV ». Lecture retenue : il n'existe **aucun écran de boutique** aujourd'hui (vérifié : ni `S/shop/`, ni `R/shop/`, ni `C/shop/`, ni `C/store/` ; W5, W10, W11, W12 ne sont pas fusionnés) ; seul l'écran « Données hors ligne » (`S/LotsScreen.kt`) télécharge des lots et les envoie à la TV. La Boutique doit donc **exister sur le téléphone** (c'est lui qui a Internet, qui livre et qui commande) **et être visible sur la TV** (c'est là que la famille choisit, à la télécommande), la TV restant **hors ligne** : tout ce qu'elle montre lui vient du téléphone, signé.

## 0. En quinze lignes

1. **Une seule Boutique, deux rendus.** Le cœur (`C/store/`, JVM pur) calcule **l'état de chaque article** (sur la TV / envoyé / à louer / loué : reste / gratuit / échantillon / terminé / indisponible) à partir des faits que les deux appareils ont déjà : catalogue de lots signé, catalogue des bouquets signé, manifeste des lots de la TV, carnet des locations, activation, essai, profil enfant. Le téléphone et la TV **dessinent** ce résultat ; aucun écran ne décide (règle de pureté W14, lint `UiStatePurityTest`).
2. **Aucun format signé nouveau.** La vitrine de la TV = les **deux catalogues signés qui existent** : `castbridge-lot-catalog-v1` (`LotManifest`, déjà gardé par le téléphone `S/LotsRuntime.kt:81` et déjà vérifié par la TV comme preuve de lot) et `castbridge-bundle-catalog-v1` (`SignedBundleCatalog`, servi par `GET /api/v1/catalog/bundles`, aujourd'hui lu seulement par les outils du propriétaire). Le téléphone les pousse à la TV (`POST /api/store/catalog`) à chaque contact ; la TV les vérifie avec `UpdateKeys.PUBLIC_KEYS`, refuse un retour en arrière (`generatedAt`), les garde dans `files/store/`. Taille réelle : **≈ 10-40 Ko** (§ 3.2), plafond refusé au-delà de 256 Ko.
3. **Téléphone (pilote)** : entrée « **Boutique** » à la place de l'action « Locations » de la barre du haut (`S/MainActivity.kt:108`, que W5 et W11 prévoyaient déjà de retirer) ; trois rayons **Apprendre** (par classe), **Langues** (« Gratuit »), **Quiz** ; fiche ; « **Louer gratuitement** » (pilote) qui ouvre le sélecteur W16 (`PickerModel`, w16-11) ; « Envoyer à la TV » = `LotsRuntime.deliverLot` **inchangé** ; « Ma TV » = l'écran existant « Locations sur la TV ». **Rien n'est dupliqué** de `LotsScreen`/`LotsRuntime`/`LotsToDeliver` : la Boutique les appelle.
4. **TV** : une tuile « **Boutique** » (`homeTools`, après « Langues » ; W11 la rangera dans « Plus » + une rangée) ouvre `StoreActivity` : colonne gauche (Apprendre · Langues · Quiz · Mes locations · [Œuvres locales, W10]), grille 4 × 2 de cartes à la télécommande, fiche. **La TV ne paie jamais** ; « Louer » sur la TV crée une **demande** (`castbridge-rent-request-v1`) que le téléphone **relève** au prochain contact (`GET /api/store/requests`), confirme (adulte) et exécute ; en secours, un **code court** lisible à l'écran (`CM2-12H-7K3Q`) que l'on tape dans CastBridge ou que l'on dicte au point focal. **Pas de QR au pilote** (D-W17-3).
5. **Loué sur la TV** : « Il vous reste 5 h 20 d'utilisation · à utiliser avant le 15/11 » ou « Il vous reste 23 jours » (phrases W16, `RentalEngine`), bandeau à 25 % / 60 min / 10 min (heures) ou 7 j / 24 h / 1 h (jours) ; **TV d'essai** : vitrine visible, tout « Version complète nécessaire » ; **profil enfant** : vitrine visible, aucune demande (« Demandez à un parent »), refus **dans le cœur** et non seulement à l'écran.
6. **Une seule composante avec W10** : `StoreActivity` est le conteneur ; l'écran « Œuvres » de w10-09 devient son rayon « Œuvres locales » (amendement d'en-tête) ; le catalogue des œuvres signé voyage par la même route `POST /api/store/catalog` (troisième document).
7. **Pendant le gel** (W15 R3/R5) : tout le cœur (`C/store/**`, `CT/**`), les parcours J, les vecteurs, les docs, un outil Python : **≈ 8 agent·jours, ≈ 6 $**, aucun fichier `S/` ni `R/`. **Après la sortie** (ou sur exception explicite du propriétaire, drapeau éteint par défaut, rapport F PASS) : deux cahiers d'écran (téléphone, TV) **≈ 6 j**. Total 11 cahiers, **≈ 16 agent·jours, ≈ 13 $** (§ 7).
8. **Plus petite tranche visible sur les deux écrans** : w17-01…05 (cœur) puis **w17-07 + w17-08** (écrans) ; sans W5 (paiement), sans W10 (œuvres), sans W12 (réglages) : la commande du pilote passe par l'outil de bureau (w16-05) ou le module serveur (w16-08) **tels que W16 les conçoit** : W17 ne crée **aucun** canal de commande nouveau, seulement l'objet « demande » et la vitrine.
9. **Drapeau** `store.enabled` : clé W12 quand W12 existe ; en attendant, un **indicateur signé existant** (`flag.set`, liste `PolicyActions.FLAGS`, `C/policy/PolicyActions.kt:30`) et un défaut compilé **faux en release, vrai en debug**. Les écrans s'allument sans rebuild dès que l'ordre signé arrive (TV : `PolicyHub.state` ; téléphone : lu dans `GET /api/store` de la TV).
10. **Décisions** : 12 questions au propriétaire avec recommandation (§ 8) ; **BLOQUÉ** : identifiants réels des bouquets (`content/TRIAL-MANIFEST.json` absent du dépôt) et dépôt du catalogue des bouquets signé sur le serveur (clé de signature hors dépôt).

## 1. État des lieux vérifié (à ne pas refaire)

| Brique | Où (vérifié le 2026-10-03) | Ce qui compte pour W17 |
|---|---|---|
| Écran « Données hors ligne » | `S/LotsScreen.kt:36-133` (jauge 100 Mo, assistant par classe `:137-160`, section Langues `:167-199`, cartes « Vos données » `:90-113` : `Télécharger`/`Mettre à jour`, bouton `LotsToDeliver.sendButton` `:107`, « Sur la TV » `:117-128`) ; entrée `LotsEntry` `:24-28` depuis Réglages, Apprendre et Jeux | **le seul « store » d'aujourd'hui** ; W17 ne le réécrit pas : la Boutique appelle `LotsRuntime.syncNow(only)`, `deliverLot`, `status(id)`, `tvBudgetText()`, `classScopes()`, `estimate()` |
| Exécution téléphone | `S/LotsRuntime.kt:149-177` (`syncNow`), `:180-188` (`refreshCatalog` : **catalogue de lots signé gardé dans `sp["catalog"]`** `:81`), `:229-233` (`deliverLot`), `:270-277` (`status`, `tvBudgetText`), `:292-304` (tâches toutes les 12 h / 30 min) | le catalogue des **bouquets** n'est **jamais** récupéré par l'app téléphone (seuls les outils du propriétaire appellent `ServerBundleCatalog.fetch`, `C/lots/ServerBundleCatalog.kt:14`) : **manque n° 1** |
| Décision pure d'envoi | `C/lots/LotsToDeliver.kt:45-51` (`sendButton`), `:59-80` (`decide`) ; `C/lots/LotStatus.kt:4-6` (`LotStage`, `LotStatus`) | réutilisé tel quel pour la colonne « envoyé / déjà sur la TV » de l'article |
| Catalogue de lots signé | `C/lots/LotManifest.kt` (`castbridge-lot-catalog-v1`, Ed25519 `UpdateKeys`), `docs/LOTS.md` § 3 (`feature=*` : tous les lots du canal en **une** signature) ; `LotMeta` (`C/lots/LotApi.kt:13` : titre, octets, édition `TRIAL`/`FULL`) | la vitrine connaît **titre, taille, édition** de chaque lot ; la TV sait déjà vérifier ce catalogue (preuve de lot, `TvLotStore.installReceived`) |
| Catalogue des bouquets signé | `C/lots/SignedBundleCatalog.kt:19-69` (`castbridge-bundle-catalog-v1`, anti-retour `notOlderThan`), `C/lots/EditionPolicy.kt:8-30` (`Bundle(id, type, lots, title, rawBytes, rentalDays)`, `BundleCatalog.containing(id)`) ; serveur `GET /api/v1/catalog/bundles` (W10 § 1 : `BundleCatalogController`) | **ce qui groupe les lots en articles** (bouquet = article `loc-<bouquet>`) ; `type` dit la famille (classe, langues, quiz : **identifiants réels BLOQUÉS**, `content/TRIAL-MANIFEST.json` absent du dépôt, `content/bundles-rental.json` présent) |
| TV : magasin, routes | `R/LotsHub.kt:19-69` (un `TvLotStore` sur `files/lots`, consommateurs `learn`/`quiz`/`langues`), `C/lots/LotPush.kt:83-91` (`GET /api/lots` = manifeste `TvManifest`, `C/lots/TvLotStore.kt:16-29`), `R/TvService.kt:251-258` (chaîne `ApiExtension`) | la TV sait **ce qu'elle a** (lots installés, versions, octets, refus) : colonne « sur cette TV » |
| Locations | `C/lots/RentalApi.kt:50-72` (`GET /api/rental` : contrat, produit, bouquets, état, `usable`, `remainingMs`, message, lots, clé) ; `C/lots/RentalEngine.kt:37-52` (`RentalContract`, `RentalStatus.remainingUsageMinutes`, `RentalWarning`) ; `C/lots/RentalDelivery.kt:62-70` (`TvRentalView`), `S/RentalDeliveryActivity.kt:38-94` (« Locations sur la TV ») ; `C/owner/KeyBadge.kt:41` (« Location : 5 j restant(s) ») | états « loué : reste » sur les deux écrans ; W16 (w16-01/03) ajoute `unit`, `usedMinutes`, phrases par unité : **W17 les consomme, ne les refait pas** |
| Familles libre / réservé | `C/lots/RentalPolicy.kt:4-19, 23-28` (`LotFamily.FREE` jamais loué, inconnue refusée), `docs/LANGUES.md` § 13 | rayon Langues = « Gratuit », jamais de sélecteur ; famille inconnue ⇒ « non louable » (fail closed) |
| Essai | `C/owner/TrialPolicy.kt:11` (`CLOSED_TILES` par id : `library, receive, usb, downloads, langues`), `:31-36` (liste blanche des routes : `/api/rental`, `/api/lots`, `/api/activation` ouverts), `R/ActivationCenter.kt:100` (`trial()`), `R/ParentalHub.kt:271-274` (filtre **par étiquette**) ; `docs/TRIAL-EDITION.md` § 15 (fenêtre unique `rental|essai|tout`, jamais un produit) | la tuile « Boutique » **n'est pas** dans `CLOSED_TILES` (visible en essai, lecture seule) ; `/api/store` (lecture) à ajouter à la liste blanche ; `/api/store/request` **fermé** en essai |
| Profil enfant | `C/parental/ParentalModel.kt:227-230` (`KID_HOME` par **étiquette** : « Apprendre », « Quiz », « Échecs », « Bibliothèque », « Aide », « Contrôle parental »), `R/ParentalHub.kt:268, 275-280` (`kidHomeActive`, `filterHome`), `R/PlayerActivity.kt:519-520` (`tile(feature…)` → `ParentalHub.guardTile`) | ajouter « Boutique » à `KID_HOME` (cœur, une ligne) = vitrine visible en mode enfant ; la **demande** est refusée par `RentRequests.create(kid = true)` (cœur) |
| Accueil TV | `R/PlayerActivity.kt:537-614` (`homeTools()` : 18 tuiles ; `HomeTool(icon, label, description, status, on, action)`, `R/TvCards.kt:258`) ; télémétrie `TV_FEATURES` liste close (`C/telemetry/Telemetry.kt:16`) | une tuile `store` = **une** entrée `tile("store", …)` après `langues` + l'id dans `TV_FEATURES` ; W11 (non fusionné) la déplacera dans « Plus » (§ 6) |
| Harnais J | `CT/journey/{JourneyKit,TvSim,PhoneSim,ActivationApiSim,Scenario}.kt` (fusionnés ; `TvSim(clock, name, TvScenario(trial, locked, pin…))`, `ActivationApiSim.switchTrial`), lint `CT/lint/` (W14) | parcours J « vitrine relayée », « demande TV → téléphone », « TV d'essai », « enfant » : `TvSim` gagne une **extension** `StoreApi` (zone additive) |
| Ordres signés | `C/policy/PolicyActions.kt:20, 30` (`flag.set`, `FLAGS` liste close), `R/PolicyHub.kt` (`state` : drapeaux), `S/OrdersRuntime.kt` (téléphone) | drapeau `store.enabled` **avant W12** sans nouveau mécanisme |
| Gel | `PLAN-STABILISATION § 5-6` (critères de sortie ; W10/W11 TV-téléphone **attendent**, cœur **peut continuer**), `SONNET-WAVE16-INDEX.md` R2 (aucun `R/`, `S/`), `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md` | W17a/b (cœur, J, docs, outil) pendant le gel ; W17c (écrans) après ou sur exception (§ 7) |

**Constat.** Tout ce qu'il faut pour une Boutique **hors ligne** existe en briques : deux catalogues signés, un manifeste TV, un carnet de locations, une livraison différée, un harnais J. Les manques réels : **(A)** le téléphone ne récupère pas le catalogue des bouquets ; **(B)** aucune fonction pure ne compose ces faits en « état d'article » ; **(C)** la TV ne reçoit pas la vitrine ; **(D)** aucune façon, depuis la TV, de dire « je veux louer ceci » ; **(E)** aucun écran, sur aucun des deux appareils.

## 2. Boutique du téléphone (CastBridge) : minimale pour le pilote

### 2.1 Entrée et navigation (sans W11, puis avec)
- **Aujourd'hui** : barre du haut `[logo] Activer la TV · Locations · 🔒 · ⚙` (`S/MainActivity.kt:107-110`). **W17** : l'action « **Locations** » devient « **Boutique** » (`StoreActivity`) ; « Locations sur la TV » (`RentalDeliveryActivity`) reste accessible **dans** la Boutique (section « Ma TV » › « Livraison avancée »). Aucune 7ᵉ entrée d'onglet. **Après W11** : `Plus › Boutique et clés › Boutique` (ligne déjà réservée, `DESIGN-W11 § 3.2`), rien à changer dans le cœur.
- **Entrées contextuelles** (W11 § 2 « la boutique entre par le contenu ») : bouton « **Obtenir** » sur une classe absente dans Apprendre (`S/LearnScreen.kt`, **après** le gel, cahier W11 w11-xx ; pas dans W17) ; dans `LotsScreen`, une ligne « Voir la Boutique » (une `TextButton` à côté de « Tout mettre à jour », w17-07).
- **Drapeau** : `store.enabled = false` ⇒ l'action reste « Locations » (comportement actuel, octet pour octet).

### 2.2 Rayons et cartes (`StoreView`, cœur)
```
┌──────────────────────────────────────┐
│ ← Boutique                 Catalogue du 03/10 │
│ ● Salon · connectée · 6,8 Mo libres sur la TV│
│ [Apprendre] [Langues] [Quiz] [Ma TV]        │   ← 4 puces, zone du pouce
│ APPRENDRE · Primaire                         │
│ ┌ CM2 ───────────────────────────── ┐        │
│ │ Leçons + exercices · 2 lots · 4,2 Mo│       │
│ │ Sur la TV : échantillon (essai)     │       │
│ │ Loué : il vous reste 5 h 20 d'utilisation │ │
│ │ [Louer gratuitement] [Envoyer à la TV]│     │
│ └─────────────────────────────────────┘       │
│ ┌ 3e ──────────────────────────────── ┐       │
│ │ … · 3 lots · 6,1 Mo · Pas sur la TV │       │
│ │ [Louer gratuitement] [Télécharger]  │       │
│ └─────────────────────────────────────┘       │
│ APPRENDRE · Secondaire … (défilement)         │
│ ─ Test gratuit jusqu'au 01/11 : toutes les    │
│   locations sont offertes pendant le test ─   │
└──────────────────────────────────────┘
```
- **Rayon Apprendre** : un article par **bouquet** de type classe (`Bundle.type`, **BLOQUÉ** : valeurs réelles ; hypothèse `classe`), groupé par cycle (préfixe de l'identifiant ou `title`), trié comme `classScopes()`. Un bouquet = ses lots `learn:<classe>` + `quiz:<classe>` (`Bundle.lots`). Carte : titre (`Bundle.title`), **contenu** (« Leçons + exercices », « 2 lots »), **taille** (`Bundle.rawBytes` sinon somme des `LotMeta.bytes`), **état sur la TV**, **état de location**, deux boutons au plus.
- **Rayon Langues** : un article par bouquet `type = langues` ou, à défaut, par lot `langues:*` **libre** : badge « **Gratuit** », jamais de sélecteur ni de « Louer » (`RentalPolicy.refusal` ⇒ `FREE`) ; boutons = ceux de `LanguagesSection` d'aujourd'hui (« Télécharger », « Télécharger et envoyer » : `LotsRuntime.downloadLanguage`, `downloadAndSendLanguage`, `:110, 236`).
- **Rayon Quiz** : les lots `quiz:*` qui ne sont pas dans un bouquet de classe (sinon ils sont dans la carte de la classe) ; **jouable sans location** quand le lot est libre, sinon même règle qu'Apprendre.
- **Ma TV** : lignes de `TvRentalView.lines()` (puis `unit`/reste W16), `tvBudgetText()`, bouton « Livraison avancée » (`RentalDeliveryActivity`), « Demandes de la TV » (§ 4).
- **Règle d'un seul bouton d'action par nature** : location (`Louer gratuitement` / `Prolonger` / `Relouer`) · livraison (`Télécharger` / `Envoyer à la TV` / `Déjà sur la TV` / `Envoi en cours` = **exactement** `LotsToDeliver.sendButton`).

### 2.3 États d'un article (fonction pure `StoreView.item(facts)`, mêmes valeurs sur la TV)
| État | Faits | Carte téléphone | Carte TV |
|---|---|---|---|
| `GRATUIT` | tous les lots du bouquet sont `FREE` | « Gratuit » ; Télécharger / Envoyer | « Gratuit · sur cette TV » ou « Gratuit · demandez-le au téléphone » |
| `ECHANTILLON` | la TV n'a que les `-trial` du bouquet | « Sur la TV : échantillon » | « Échantillon » |
| `PAS_SUR_TV` | aucun lot du bouquet dans `TvManifest` | « Pas sur la TV » | « À envoyer depuis le téléphone » |
| `ENVOYE` | `LotStage.SENT`/`SENDING`/`WAITING_TV` (téléphone seulement) | « Envoi en cours 42 % » / « En attente de la TV » | — (la TV ne sait pas) |
| `SUR_TV` | lots complets présents **et** droit (`Access.granted` ⊇ bouquet, ou location utilisable) | « Sur la TV » | « Sur cette TV » |
| `LOUE` | contrat `usable` couvrant le bouquet | « Loué : il vous reste … » + `Prolonger` | idem + bandeau d'alerte |
| `TERMINE` | contrat `EXPIRED`/`DONE` < 30 j | « Location terminée le … · Relouer ? » | idem |
| `A_LOUER` | bouquet réservé, aucun droit | `Louer gratuitement` (pilote) | « Louer » → demande (§ 4) |
| `BLOQUE(raison)` | voir 2.5 | bouton grisé + phrase | phrase |

### 2.4 Louer : sélecteur et libellés (réutilise W16)
`Louer gratuitement` ouvre le **sélecteur W16** (`PickerModel`, w16-11 : « Sans durée précise : 30 jours (jusqu'au 11/11) » présélectionné · Jours 1 · 3 · 7 · 14 · Heures d'utilisation 1 · 3 · 6 · 12 · 24 · 48 · 96, aide d'une ligne par groupe, **date de fin réelle**). Confirmation : « **Louer CM2 · 12 heures d'utilisation · à utiliser avant le 15/11 · gratuit pendant le test** ». Le résultat de la confirmation est **une demande** `RentRequest` (§ 4.1), exactement la même qu'une demande née sur la TV ; son exécution dépend de la tranche : **tranche 1** = fichier/texte partagé au propriétaire (`louer.py --demande`, w17-11) ; **tranche 2** = `PilotClient` (w16-11) → `castbridge.pilot` (w16-08). **W17 n'ajoute aucun canal de commande.** Libellé du bouton quand le pilote est fini (`pilot.end` passé, lu de `pilot.json`/W12) : « Louer » (prix : W5, D9-bis BLOQUÉ ⇒ « prix non communiqué »).

### 2.5 États bloqués (phrases exactes, cœur `StoreTexts`)
| Raison | Téléphone | TV |
|---|---|---|
| TV éteinte / hors de portée | « La TV n'est pas à portée : la demande partira dès qu'elle sera allumée à côté du téléphone. » (la demande est **gardée**) | — |
| Aucune TV enregistrée | « Ajoutez d'abord votre TV (Accueil › Ajouter ma TV). » | — |
| TV d'essai | « Version d'essai : les locations demandent une clé de production. Voyez votre point focal. » | « Version complète nécessaire · Passer en production » |
| Profil enfant actif (TV) | « Un profil enfant est actif sur la TV : demandez à un parent. » | « Demandez à un parent » |
| Quota : 3 locations en cours | « 3 locations en cours sur cette TV : attendez la fin de l'une d'elles. » | « 3 locations en cours » |
| Même bouquet déjà loué (autre unité) | « CM2 est déjà loué (7 jours) : prolongez-le, ou attendez la fin pour changer d'unité. » | idem |
| Lot libre | « Gratuit : rien à louer. » | « Gratuit » |
| Famille inconnue | « Cet article n'est pas louable pour le moment. » | idem |
| Pilote terminé, sans prix | « Le test gratuit est terminé ; tarifs bientôt disponibles. » | idem |
| Place sur la TV | `LotStatusText.skipped` existant (« il manque X Mo… ») | « Place insuffisante sur la TV (X Mo) » |
| Catalogue absent | « Connectez le téléphone à Internet puis actualisez la Boutique. » | « Boutique vide : rapprochez le téléphone » |

### 2.6 Source du catalogue, cache, hors ligne
- `StoreRuntime` (téléphone, w17-06) : à chaque `LotsSyncJob` (12 h) et sur « Actualiser », `ServerBundleCatalog.fetch(baseUrl, keys(), notOlderThan)` **avec les clés de l'app** (`UpdateKeys.PUBLIC_KEYS + EXTRA_UPDATE_KEY`, comme `LotsRuntime.keys()` `:133`) ⇒ `files/store/bundles-catalog.json` ; le catalogue de **lots** est celui déjà gardé (`sp["catalog"]`, `feature=*` : **à vérifier** que `LotSync.fetchCatalog` le demande sans filtre ; sinon une seconde requête `feature=*`). Hors ligne : tout est lisible depuis les deux caches ; « Catalogue du JJ/MM » en en-tête.
- **Relais vers la TV** : à chaque contact (hook d'une ligne dans `LotsRuntime.deliver`, zone w15-16 : proposée au rapport, jamais éditée par W17), `POST /api/store/catalog` si la TV a un `generatedAt` plus ancien (lu dans `GET /api/store`), puis `GET /api/store/requests`.

## 3. Vue de la TV (CastBridge-TV) : ce que « vu par la TV » veut dire

### 3.1 Définition retenue
« Vu par la TV » = **la même vitrine, les mêmes états, à la télécommande, hors ligne**, avec une action « Louer » qui **demande** sans jamais payer ni signer. Trois lectures écartées : (a) un navigateur web vers le serveur (la TV n'a pas Internet par principe, `docs/LOTS.md` § 1) ; (b) un miroir de l'écran du téléphone (rien à la télécommande) ; (c) une boutique TV complète W5 (paiement, bons, clavier à l'écran : attend W5 et le gel ; le conteneur W17 l'accueillera comme sections).

### 3.2 Ce que la TV garde (`files/store/`, budget)
| Fichier | Contenu | Taille mesurable | Règle |
|---|---|---|---|
| `lots-catalog.json` | `castbridge-lot-catalog-v1`, `feature=*` | 10 lots Apprendre + 46 Langues + Quiz ≈ **60 lots × ≈ 180 o ≈ 11 Ko** ; 300 lots ≈ 55 Ko | vérifié `LotManifest` + `UpdateKeys` ; anti-retour `generatedAt` ; **refusé > 256 Ko** |
| `bundles-catalog.json` | `castbridge-bundle-catalog-v1` | 20-40 bouquets × ≈ 200 o ≈ **4-8 Ko** | `SignedBundleCatalog.verify(notOlderThan)` ; refusé > 64 Ko |
| `works-catalog.json` (W10, plus tard) | `castbridge-works-catalog-v1` | ≤ 2 Mio (W10 § 5.3) : **plafond W17 de 256 Ko au pilote** (titres/synopsis seulement ; couvertures dans les lots d'aperçu) | w10-08 (amendé) |
| `requests.json` | demandes en attente (≤ 20) + acquittées (≤ 50, 30 j) | < 8 Ko | `SafeFile` (`.bak`) |
Total **< 100 Ko** au pilote, < 600 Ko avec W10 : très en dessous des 10 Mo du magasin de lots, **hors** budget `TvLotStore` (ce ne sont pas des lots).

### 3.3 Écran (`StoreActivity`, vues classiques, 1280 × 720 dp, D-pad)
```
┌────────────────────────────────────────────────────────────────────────────┐
│ [logo]  PRODUCTION · Location : 5 h 20                     20:41   [⌁][⌁]  │
│ Boutique                                   Catalogue du 03/10 · 6,8 Mo libres│
│ ┌──────────────┐  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│ │▶ Apprendre   │  │ CM2      │ │ CM1      │ │ 6e       │ │ 3e       │      │
│ │  Langues     │  │ Loué     │ │ Sur cette│ │ À louer  │ │ Échantil.│      │
│ │  Quiz        │  │ 5 h 20   │ │ TV       │ │          │ │          │      │
│ │  Mes locations│ └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│ │  (Œuvres W10)│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│ └──────────────┘  │ Tle C    │ │ Droit L1 │ │ GCE A    │ │ Tle D    │      │
│                   │ À louer  │ │ À louer  │ │ À louer  │ │ À louer  │      │
│                   └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│ CM2 · Leçons + exercices · 2 lots · 4,2 Mo · Il vous reste 5 h 20 d'utilisation│
│ ■ Louer   ■ Mes locations   ■ Aide                                          │
└────────────────────────────────────────────────────────────────────────────┘
```
- **Colonne gauche** 4-5 lignes (↑↓), **grille** 4 × 2 (← → OK, défilement par rangée), **ligne de détail** sous la grille (titre, contenu, taille, état : ≤ 20 mots, 21 sp), **touches de couleur** rappelées en bas (verte Louer, jaune Mes locations, bleue Aide ; jamais l'unique chemin : OK sur une carte ouvre la **fiche**). Cartes 250 × 130 dp, étiquette **19 sp**, état **16 sp**, anneau de focus `TvStyle` existant, échelle 1,04, aucune animation d'entrée (W11 § 4.7). ≤ **13 nœuds focusables** (5 + 8) ; W11 vise ≤ 7 par écran : **D-W17-5** (grille 3 × 2 = 11 nœuds, ou 4 × 2 : recommandé 4 × 2, un rayon tient en une page).
- **Fiche** (OK) : titre, contenu (lots et leurs titres), taille, état, « Louer » (verte) / « Prolonger » / « Relouer », Retour.
- **Mes locations** : une ligne par contrat : produit, « Il vous reste … » (phrase W16), « à utiliser avant le … », badge d'alerte ; « Test gratuit : les heures se terminent au plus tard le 15/11 » (déduit du contrat, w16-10).
- **Mémoire / CPU (GaiaOS 32 bits, 720p)** : pas de Compose, pas de WebView, **aucune image** au pilote (Apprendre n'a pas de couverture) ⇒ une `Activity` + ≈ 60 `LotMeta` + ≈ 30 `Bundle` + ≈ 40 cartes recyclées : **< 1,5 Mo** de tas Java (estimation : objets < 200 Ko, vues ≈ 1 Mo), vérification Ed25519 d'un catalogue de 50 Ko **une fois** à la réception (≈ 5-20 ms sur ARMv7, comme la preuve d'un lot aujourd'hui), calcul de `StoreView` **< 5 ms** (table de 60 lignes) refait à l'ouverture et à chaque changement de carnet. Avec W10 : couvertures WebP ≤ 60 Ko décodées **8 à la fois** (visibles), cache `LruCache` 2 Mo ⇒ < 4 Mo. Le `LotOpener` (2 lots en mémoire) n'est **pas** sollicité par la Boutique.
- **Télémétrie** : `feature_used{feature: store, source: tile}` (id `store` ajouté à `TV_FEATURES`, liste close) ; rien d'autre sans consentement.

### 3.4 Essai, enfant, modes
| Mode | Tuile | Vitrine | « Louer » |
|---|---|---|---|
| Production | oui | complète | demande (§ 4) |
| **Essai** (`ActivationCenter.trial()`) | oui (pas dans `CLOSED_TILES`) | complète, chaque article « Version complète nécessaire » ; bandeau « Passer en production » (ouvre `ActivationActivity`) | **refusé dans le cœur** (`RentRequests.create` ⇒ `TRIAL_TV`) |
| **Profil enfant** (`ParentalHub.kidHomeActive()`) | oui (« Boutique » dans `KID_HOME`) | complète (W10 : œuvres filtrées par classification) | « Demandez à un parent » (refus cœur `KID_PROFILE`) |
| Mode réduit (W4-B, non fusionné) | oui | complète | refus « Renouvelez d'abord la clé » (`DegradedPolicy` si présent) |
| `LOCKED` | aucune tuile (accueil inexistant) | — | — |
| Super illimité | oui | tout « Sur cette TV / droit illimité » | sans objet |

### 3.5 Une seule composante avec W10 (œuvres)
`StoreActivity` = conteneur (colonne, grille, fiche, demande) ; **w10-09** (amendé) fournit le rayon « Œuvres locales » (cartes à couverture, filtres genre/langue, chaîne, lecture `WorkHub.play`) **dans** ce conteneur au lieu d'une `WorksActivity` séparée ; W11 remplace la tuile par une rangée « Œuvres locales » + `Plus › Boutique`. Le catalogue des œuvres arrive par `POST /api/store/catalog` (document n° 3) et non par `/api/oeuvres/catalog` (w10-08 amendé). Les états d'article sont les **mêmes** (`StoreView`), une œuvre étant son propre bouquet (W10 § 2.2).

## 4. La demande de location depuis la TV (le seul point d'audit Opus)

### 4.1 L'objet `RentRequest` (`castbridge-rent-request-v1`, texte, cœur `C/store/RentRequest.kt`)
```
castbridge-rent-request-v1
tv=<installId 16 hex>            identité de la TV (TrustRegistry.installId, déjà dans les journaux : DESIGN-W4:41)
bundle=classe-cm2                bouquet du catalogue des bouquets
choice=defaut|<N>j|<H>h          le choix W16 (PilotRules.Choice : jours ∈ pickerDays, heures ∈ pickerHours)
kind=new|extend                  nouvelle location ou prolongation (period lue dans le carnet)
period=<ms|0>                    period du contrat à prolonger
nonce=<8 hex>                    aléa (SecureRandom)
at=<ms TV>                       horloge TvClock.now (informative)
origin=tv|phone                  née sur la TV ou sur le téléphone
```
- **Ce qu'une demande n'est pas** : un droit, un paiement, une signature. **Elle ne donne rien** : seul l'émetteur (bureau w16-05, serveur w16-08) décide, après **confirmation d'un adulte sur le téléphone** et vérification des règles (`PilotRules` : fenêtre du pilote, 3 actifs, unité, quotas). La TV **ne peut pas** signer (X25519 ne signe pas, W16 § 2.4) : on ne prétend pas qu'elle le fait.
- **Code court** (secours : téléphone loin, point focal au téléphone) : `CM2-12H-7K3Q` = `<alias du bouquet ≤ 6>-<choix>-<4 car. Crockford>` où les 4 caractères = base32 des 20 premiers bits de `SHA-256("castbridge-rent-request-v1|" + tv + "|" + bundle + "|" + choice + "|" + nonce)` ; l'**alias** vient du catalogue des bouquets (`title` compacté, règle déterministe `StoreAlias.of(bundle)`, collisions résolues par suffixe numérique dans l'ordre du catalogue). Lisible à 3 m (48 sp), dicté en 20 secondes. Le téléphone qui le reçoit tapé **recompose** la demande (`tv` = sa TV par défaut, `nonce` inconnu ⇒ **vérification impossible** : le code court n'est qu'un **mémo**, l'exécution exige toujours la confirmation d'un adulte sur le téléphone ; `rentreq.py` du propriétaire le dit).
- **Pourquoi pas de QR au pilote (D-W17-3)** : la TV n'a pas d'encodeur QR (seul `DK/Qr.kt` au bureau), CastBridge n'a pas la permission caméra ni de lecteur, et le canal LAN/Bluetooth existe déjà entre les deux appareils : un QR ajouterait ≈ 1,5 j et une permission pour un cas (téléphone loin) que le code court couvre. Renversable après le pilote (hypothèse à mesurer : part des demandes nées sur la TV **sans** téléphone à portée).

### 4.2 Canal : file sur la TV, relevée par le téléphone (existant, aucun transport nouveau)
```
TV (StoreActivity) ─ OK « Louer » ─► RentRequests.create(facts) ──► files/store/requests.json  (état PENDING)
                                       │ refus cœur : TRIAL_TV · KID_PROFILE · FREE_BUNDLE · UNKNOWN_FAMILY · OVER_LIMIT(3) · SAME_BUNDLE_OTHER_UNIT · PILOT_ENDED · DUPLICATE(bundle+choice en attente)
                                       ▼
                  écran « Demande enregistrée : CM2 · 12 heures d'utilisation. Ouvrez CastBridge sur le téléphone, ou donnez ce code : CM2-12H-7K3Q »
téléphone, prochain contact (LotsRuntime.deliver, LAN ou Bluetooth, PIN / téléphone de confiance) :
   GET /api/store/requests  ─► liste PENDING ─► notification « La TV demande : louer CM2 · 12 heures d'utilisation » ─► écran de confirmation (adulte ; code parental du téléphone si configuré, S/ParentalScreen : lecture)
      ├─ Confirmer ─► même chemin que « Louer gratuitement » né sur le téléphone (§ 2.4) ─► POST /api/store/requests/ack?nonce=…&state=ACCEPTED
      ├─ Refuser   ─► POST …/ack?state=REFUSED (la TV affiche « Demande refusée sur le téléphone »)
      └─ Plus tard ─► rien (reste PENDING ; expire après 7 j : EXPIRED)
   livraison (activation + lots scellés) ─► RentalDelivery ─► la TV voit le contrat ─► la demande passe FULFILLED (rapprochement par bundle + period)
```
- **Idempotence et rejeu** : `nonce` unique ; un `ack` répété renvoie le même état ; une demande identique (`bundle`, `choice`) en attente ⇒ `DUPLICATE` (la TV dit « déjà demandé le … »). Pas de signature : un attaquant sur le LAN avec le PIN peut **créer** une demande (route derrière PIN/téléphone de confiance, comme `/api/lots`) : elle ne donne rien sans l'adulte au téléphone ni l'émetteur : **surface = nuisance**, bornée par ≤ 20 demandes en attente et 1 par (bundle, choix).
- **Enfant** : `create(kid = true)` ⇒ refus **dans le cœur**, testé ; la TV affiche « Demandez à un parent » ; le téléphone, lui aussi, ignore une demande `origin=tv` si la TV dit `kidActive` à ce moment (double garde, pas de confiance dans l'écran).
- **TV d'essai** : refus cœur `TRIAL_TV` ; une TV d'essai n'a pas de contrat de location possible (fenêtre `essai` unique, TRIAL-EDITION § 15).
- **Quota** : la TV ne connaît que ses contrats (3 utilisables, `maxConcurrent`) : `OVER_LIMIT` affiché tôt ; l'émetteur reste juge (3 actifs par licence, 192 h / 7 j).
- **Audit Opus (w17-03)** : format, refus, nonce/ack, bornes de la file, absence de tout chemin où la demande vaudrait droit, textes.

### 4.3 Routes TV (`C/store/StoreApi.kt`, `ApiExtension`, derrière PIN / téléphone de confiance)
| Route | Rôle | Essai |
|---|---|---|
| `GET /api/store` | `{enabled, catalogAt:{lots, bundles, works?}, view: StoreView.json (rayons, articles, états), kidActive, trial, pending: n}` | ouvert (lecture) |
| `POST /api/store/catalog` (corps JSON `{"lots": <json signé>, "bundles": <json signé>, "works": <json signé>?}`, ≤ 512 Ko) | vérifie chaque document, anti-retour, écrit `files/store/*.json`, répond `{accepted:[…], refused:{doc: raison}}` | ouvert (une TV d'essai peut **voir**) |
| `GET /api/store/requests` | demandes `PENDING` (+ 50 dernières acquittées) | fermé |
| `POST /api/store/requests/ack?nonce=&state=ACCEPTED|REFUSED` | acquittement idempotent | fermé |
| `POST /api/store/request` (corps = texte `castbridge-rent-request-v1`, `origin=phone`) | **optionnel** : le téléphone dépose une demande née chez lui pour que la TV l'affiche « en cours » (D-W17-8) | fermé |
`tools/routes/routes.txt` classé (test w1-06) ; `TrialPolicy.EXACT/PREFIXES` : `/api/store`, `/api/store/catalog` ; `DENIED_UNDER_ALLOWED` : `/api/store/requests`, `/api/store/requests/ack`, `/api/store/request`.

## 5. Données, fraîcheur, téléphone absent

- **Fraîcheur** : la vitrine TV porte `Catalogue du JJ/MM` ; > 30 j : « Catalogue ancien : rapprochez le téléphone » (jamais un blocage : tout article reste visible et une location en cours reste jouable). Le téléphone pousse à **chaque contact** si plus récent (coût : un `GET /api/store` ≈ 1 Ko + un `POST` ≈ 20-60 Ko, Wi-Fi ou Bluetooth ; sur Bluetooth CBT1 ≈ 60 Ko ≈ 1-2 s, après les lots en attente, jamais avant un envoi de fichier : règle `transferToTvRunning()` existante).
- **Téléphone absent pendant des jours** : la TV affiche la dernière vitrine, ses demandes restent `PENDING` (7 j puis `EXPIRED`, « demande expirée : refaites-la »), ses locations évoluent **seules** (carnet, balayage) : « loué → terminé » sans téléphone ; un article `TERMINE` propose « Relouer » (nouvelle demande). Rien ne dépend d'une horloge murale : états par `TvClock`.
- **Retour arrière du catalogue** : refusé (`generatedAt`), comme pour les durées de location (`SignedBundleCatalog.kt:62-63`).
- **Catalogue plus récent sur la TV que sur le téléphone** (clé USB du point focal, D-W17-9) : le téléphone **adopte** le document de la TV après vérification (même règle que W12 § 2.7).
- **Ce que la vitrine ne porte jamais** : prix (grille W5/W12, hors W17), personne, profil, code d'appareil complet (le `tv=` d'une demande est l'`installId`, déjà exposé dans les journaux et les relevés W16).

## 6. Interaction avec W5, W10, W11, W12, W14, W16 (une composante, pas six)

| Vague | Ce qu'elle apporte à la Boutique | Ce que W17 fixe pour elle |
|---|---|---|
| **W5** (paiement, bons, jetons, commandes) | sections « Payer », « Jetons », « Mes commandes » ; `ShopActivity` TV avec clavier à l'écran | `S/store/StoreScreen.kt` est le **conteneur** ; w5-11 y ajoute ses sections (amendement : `ShopRuntimeView` **étend** `StoreRuntime`) ; la boutique TV W5 = **sections** de `StoreActivity` (jamais une seconde tuile) ; articles `loc-<bouquet>` = ceux de `StoreView` |
| **W10** (œuvres) | rayon « Œuvres locales », catalogue des œuvres, aperçus, `WorkHub` | § 3.5 ; amendements w10-08 (route catalogue), w10-09 (rayon dans `StoreActivity`), w10-11 (onglet dans `StoreScreen`) |
| **W11** (navigation) | `Plus › Boutique`, rangée « Œuvres locales », 6 tuiles | la tuile `store` de W17 est **provisoire** : W11 la retire au profit de `Plus › Appareil › Boutique` (ligne déjà prévue) ; `NavModel` compte W17 comme « ligne de Plus », pas une destination |
| **W12** (réglages) | clé `store.enabled` (BOOL, défaut 0 hors pilote, 1 pendant), `pilot.*` | avant W12 : drapeau `flag.set store.enabled` (`PolicyActions.FLAGS` + 1) et `pilot.json` ; amendement w12-01 **non nécessaire** (le schéma déclare les clés W16 déjà ; `store.enabled` est ajouté par w17-02 dans un fichier `C/store/StoreFlag.kt` qui lira `Settings` quand il existera) |
| **W14** (barrière) | harnais J, lint de pureté, fumée F | W17a livre 6 parcours J (§ 7.2) ; tout écran W17 passe le lint ; F `--tv fake` avant fusion des écrans |
| **W16** (location à durée choisie) | sélecteur, `PilotRules.Choice`, phrases par unité, livraison, `louer.py`, module pilote | `RentRequest.choice` = `PilotRules.Choice` ; « Louer gratuitement » = texte de W16 § 1.3 ; w16-11 (amendé) : l'entrée « Louer » vit dans `StoreScreen`, et `PickerModel.confirm` produit un `RentRequest` ; w16-10 (amendé) : les phrases/bandeaux sont consommés par `StoreActivity › Mes locations` ; w16-05 : `louer.py --demande <fichier|code>` (w17-11 fournit `rentreq.py`, importé par `louer.py` d'une ligne proposée au rapport) |

## 7. Ce qui se fait pendant le gel, ce qui attend, la plus petite tranche visible

### 7.1 Pendant le gel (W15 R3/R5 : cœur, tests, docs, outils ; aucun `S/`, `R/`)
| Cahier | Livre | Modèle | Effort |
|---|---|---|---|
| w17-01 `store-catalog-core` | `C/store/StoreCatalog.kt` : fusion lot-catalogue + bouquets ⇒ `StoreItem` (bouquet, lots, famille, taille, rayon, alias) ; `StoreAlias` ; bornes de taille ; `CT/store/StoreCatalogTest` | sonnet M | 1,5 j |
| w17-02 `store-view-state` | `C/store/StoreView.kt` (états § 2.3, table), `C/store/StoreTexts.kt` (toutes les phrases FR § 2.5), `C/store/StoreFlag.kt`, `KID_HOME` + « Boutique », `TV_FEATURES` + `store` ; `CT/store/StoreViewTest` | sonnet M | 1,5 j |
| w17-03 `rent-request-core` (**audit Opus**) | `C/store/RentRequest.kt` (format, code court, parse/canonique), `C/store/RentRequests.kt` (file, refus, nonce, ack, expiration, rapprochement), vecteurs `tools/activation/store-vectors.json` ; `CT/store/RentRequestTest` | sonnet M | 1,5 j |
| w17-04 `store-api-core` | `C/store/StoreApi.kt` (routes § 4.3, stockage `files/store/`, anti-retour), `TrialPolicy` (liste blanche), `tools/routes/routes.txt` ; `CT/store/StoreApiTest`, `CT/owner/TrialRoutesStoreTest` | sonnet M | 1,5 j |
| w17-05 `journey-store` | `CT/journey/StoreSim.kt` (extension de `TvSim`, zone additive), `CT/journey/StoreJourneyTest.kt` : 6 parcours (§ 7.2) | sonnet M | 1 j |
| w17-09 `docs-store` | `docs/STORE.md`, renvois `LOTS.md`, `RENTAL-LOTS.md`, `HANDOFF.md` § 0 | haiku S | 0,3 j |
| w17-10 `store-test-plan-gate` | `docs/test-plans/STORE-PILOT.md` (liste humaine 10 points), `tools/agents/gate-w17.sh` | haiku S | 0,3 j |
| w17-11 `desk-rent-request-tool` | `tools/pilot/rentreq.py` (lit un fichier/code, vérifie, imprime la commande `louer.py`), rejoue `store-vectors.json` en Python | sonnet S | 0,5 j |
**≈ 8 agent·jours, ≈ 6 $** (sonnet 6 × ≈ 0,9 $, haiku 2 × ≈ 0,1 $, audit Opus 1 × ≈ 0,7 $). Résultat : **rien de visible**, mais tout ce qui décide est écrit, testé, auditable, et les deux écrans deviennent **minces** (dessiner un `StoreView`, appeler trois fonctions).

### 7.2 Parcours J (w17-05), tous sur `TvSim` + `PhoneSim` + `StoreApi`
J-S1 catalogues relayés une fois, pas deux (anti-retour, `generatedAt`) ; J-S2 article « sur la TV » après livraison d'un lot (`/api/lots/install` réel) ; J-S3 demande née sur la TV ⇒ relevée par le téléphone ⇒ `ack ACCEPTED` ⇒ contrat installé (activation de TEST + lot scellé, comme `tools/rental-test`) ⇒ `FULFILLED`, et article « loué : reste » identique sur les deux vues ; J-S4 TV d'essai : vitrine visible, demande refusée `TRIAL_TV`, `GET /api/store/requests` 403 ; J-S5 profil enfant : refus `KID_PROFILE`, aucune ligne dans `requests.json` ; J-S6 téléphone absent 8 j : demande `EXPIRED`, location terminée par le balayage ⇒ article `TERMINE` sans téléphone.

### 7.3 Après la sortie du gel (ou exception du propriétaire, D-W17-11)
| Cahier | Livre | Modèle | Effort |
|---|---|---|---|
| w17-06 `phone-store-runtime` | `S/store/StoreRuntime.kt` (catalogue des bouquets, cache, relais, relevé des demandes, notification, confirmation), ligne proposée dans `LotsRuntime.deliver` (hors zone) | sonnet M | 1,5 j |
| w17-07 `phone-store-screen` | `S/store/StoreScreen.kt`, `StoreItemCard`, `StoreRequestsScreen`, `S/MainActivity.kt` (**une** action), `S/LotsScreen.kt` (**un** bouton) ; lint de pureté | sonnet L | 2 j |
| w17-08 `tv-store-screen` | `R/StoreHub.kt` (branchement `StoreApi` dans la chaîne `TvService:251-258`, une ligne), `R/StoreActivity.kt`, `R/StoreViews.kt`, `R/PlayerActivity.kt` (**une** tuile), manifeste (une activité), `ic_t_store` ; parcours F `--tv emu` | sonnet L | 2,5 j |
**≈ 6 j, ≈ 5,5 $** (+ 2 échantillons Opus ≈ 0,7 $). **Ordre** : w17-06 → w17-07 ∥ w17-08. Prérequis W16 : w16-01/03 (phrases, `unit`) pour « reste » par unité ; sans eux, `TvRentalView.message` existant (« Il vous reste 12 jours »).

### 7.4 La plus petite tranche qui donne au propriétaire une Boutique visible sur les deux écrans
**Tranche V = w17-01 → 02 → 03 → 04 → 05 (cœur, pendant le gel, ≈ 7 j) puis w17-06 → (w17-07 ∥ w17-08) (≈ 6 j)**, avec : rayons Apprendre / Langues / Quiz, envoi à la TV (existant), « Louer gratuitement » → demande → exécution par **l'outil de bureau W16** (`louer.py --demande`) ou le **module pilote** ; vitrine TV avec états et demande ; essai et enfant en lecture. **Sans** : paiement (W5), œuvres (W10), navigation allégée (W11), réglages (W12). Calendrier : cœur fusionné en **≈ 1,5 semaine** (deux exécutants) ; écrans **2 semaines** après la sortie du gel (ou immédiatement sur exception, drapeau **éteint** par défaut, fumée F PASS, liste humaine `STORE-PILOT.md` 10/10 sur la TV de référence). **Séquencement avec W16 tranche 1** : W17a peut tourner **en parallèle** de 16a (fichiers disjoints : `C/store/**` vs `C/lots/**` ; w17-03 lit `PilotRules.Choice` de w16-04 : si w16-04 n'est pas fusionné, `RentRequest.choice` est une chaîne validée localement (`defaut|<N>j|<H>h`) et se rebranche d'une ligne) ; W17c **après** 16d (w16-10/11) pour les phrases par unité et le sélecteur, ou **avant** avec les phrases existantes si le propriétaire veut voir la vitrine d'abord (D-W17-11).

### 7.5 Comment livrer sûrement (W14)
Chaque cahier d'écran : lint de pureté vert (aucune décision d'état dans `S/store`, `R/Store*` : tout vient de `StoreView`/`StoreTexts`/`RentRequests`) ; parcours J de sa zone ; fumée `--tv fake` (w14-10 : la fausse TV du Mac gagne `StoreApi` par w17-05) et `--tv emu` pour la TV ; **drapeau éteint** ⇒ `git diff` du comportement = **zéro** (l'action « Locations » et l'accueil TV inchangés, testé par J-S0 « drapeau éteint : `GET /api/store` ⇒ 404, aucune tuile ») ; liste humaine `STORE-PILOT.md` ; la TV du propriétaire **jamais** cible d'un script (D-W14-3) ; build TV verrouillé (`-PrequireActivation=true`) copié dans `Download` de la clé.

## 8. Décisions

### 8.1 Décisions du propriétaire (recommandation appliquée par défaut ; BLOQUÉ = fait réel manquant)
| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W17-1** | Entrée téléphone : « Boutique » **remplace** l'action « Locations » de la barre du haut (l'écran « Locations sur la TV » reste dans Boutique › Ma TV) ? | w17-07 | **oui** (W5 et W11 le prévoyaient ; une seule porte) |
| **D-W17-2** | TV : une tuile « Boutique » **provisoire** sur l'accueil (18 → 19 tuiles) jusqu'à W11, ou attendre W11 ? | w17-08 | **tuile provisoire** (le propriétaire veut la voir ; W11 la rangera) |
| **D-W17-3** | Demande depuis la TV : **file relevée par le téléphone + code court** ; **pas de QR** au pilote ? | w17-03 | **oui** ; QR renversable après mesure |
| **D-W17-4** | Vitrine TV = les deux catalogues signés existants (**aucun format nouveau**), plafonds 256 Ko / 64 Ko ? | w17-01/04 | **oui** |
| **D-W17-5** | Grille TV 4 × 2 (13 nœuds focusables) ou 3 × 2 (11) ? | w17-08 | **4 × 2** (un rayon par page) |
| **D-W17-6** | TV d'essai : vitrine **visible**, lecture seule, « Version complète nécessaire » ? | w17-02 | **oui** (outil de conversion, cohérent avec W5 § 9 et W10 D-W10-6) |
| **D-W17-7** | Profil enfant : vitrine **visible**, aucune demande (refus cœur) ? | w17-02/03 | **oui** (un enfant peut montrer ce qu'il veut ; l'adulte commande) |
| **D-W17-8** | Une demande née sur le téléphone est-elle déposée sur la TV (`POST /api/store/request`) pour qu'elle affiche « en cours » ? | w17-04/06 | **oui** (une seule liste des demandes, visible des deux côtés) |
| **D-W17-9** | Catalogue apporté par clé USB du point focal (fichier `Download/CastBridge/store/*.json`, même veilleur que `activation`) ? | w17-04 | **non au pilote** (le téléphone suffit ; une ligne de plus si un foyer n'a pas de téléphone) |
| **D-W17-10** | Drapeau `store.enabled` : défaut **faux** en release tant que la liste humaine n'est pas 10/10 ; allumé par ordre signé `flag.set` ? | w17-02 | **oui** |
| **D-W17-11** | Écrans (w17-06/07/08) : **attendre la sortie du gel** (recommandé) ou **exception** immédiate avec drapeau éteint, J + F + liste humaine ? | calendrier | **attendre**, sauf si le propriétaire veut voir la vitrine avant le pilote S1 (12/10) : alors exception **TV d'abord** (w17-08, lecture seule, sans demande) |
| **D-W17-12** | Libellé pilote : « **Louer gratuitement** » + ligne « Test gratuit jusqu'au 01/11 » ; après : « Louer » (prix W5) ? | w17-02 | **oui** |

### 8.2 Décisions prises par l'architecte (renversables)
Un seul cœur `C/store/` pour les deux écrans ; états d'article § 2.3 ; la demande ne vaut jamais droit ; nonce + ack idempotent ; ≤ 20 demandes en attente, 1 par (bouquet, choix), expiration 7 j ; `/api/store` lecture ouverte en essai, demandes fermées ; `KID_HOME` + « Boutique » ; `TV_FEATURES` + `store` ; tuile après `langues` ; pas d'image au pilote ; vérification Ed25519 à la réception seulement ; aucun prix dans W17.

### 8.3 BLOQUÉ / non vérifié
- **Identifiants réels des bouquets** et leur `type` : `content/TRIAL-MANIFEST.json` **absent du dépôt** (`content/bundles-rental.json` présent) ; les rayons supposent `type ∈ {classe, langues, quiz}` : w17-01 lit `type` et range l'inconnu dans « Autres » (jamais une exception).
- **Dépôt du catalogue des bouquets signé sur le serveur** (`castbridge.catalog.bundles-file`, clé de signature hors dépôt) : sans lui, `ServerBundleCatalog.fetch` répond « pas encore de catalogue » et la Boutique affiche les lots **sans** bouquets (rayon par lot, famille lue des lots : mode dégradé prévu).
- `LotSync.fetchCatalog` demande-t-il `feature=*` ? (**non vérifié** ; sinon une requête de plus, w17-06).
- `GET /api/parental` expose-t-il le profil actif au téléphone ? (**non vérifié** ; `GET /api/store` l'expose de toute façon : `kidActive`).
- Taille réelle des catalogues en production (estimations § 3.2 à partir de 10 lots Apprendre, 46 lots Langues, bouquets supposés).
- W5/W10/W11/W12/W16 : non fusionnés ; amendements d'en-tête seulement (w5-11, w10-08, w10-09, w10-11, w16-10, w16-11).

## 9. Ancrages pour les exécutants
Téléphone `S/LotsScreen.kt:22-28, 36-133, 167-199` · `S/LotsRuntime.kt:81, 110-123, 133, 149-188, 229-277, 292-310` · `S/MainActivity.kt:106-110` · `S/RentalDeliveryActivity.kt:38-94` · `S/OrdersRuntime.kt` · cœur `C/lots/LotsToDeliver.kt:45-80` · `C/lots/LotStatus.kt:4-60` · `C/lots/LotManifest.kt` · `C/lots/SignedBundleCatalog.kt:19-69` · `C/lots/ServerBundleCatalog.kt:11-26` · `C/lots/EditionPolicy.kt:8-30, 41-56` · `C/lots/RentalPolicy.kt:4-37` · `C/lots/RentalApi.kt:50-72` · `C/lots/RentalEngine.kt:6-52, 158` · `C/lots/RentalDelivery.kt:62-99` · `C/lots/TvLotStore.kt:13-29, 102, 154` · `C/lots/LotPush.kt:59-91` · `C/owner/TrialPolicy.kt:11, 31-45` · `C/owner/KeyBadge.kt:41` · `C/parental/ParentalModel.kt:227-230` · `C/policy/PolicyActions.kt:20, 30, 105-107` · `C/telemetry/Telemetry.kt:16, 30, 179` · `C/tv/Device.kt:54-62` · TV `R/LotsHub.kt:19-69` · `R/TvService.kt:251-258` · `R/PlayerActivity.kt:519-520, 537-614` · `R/ParentalHub.kt:201, 268-280` · `R/ActivationCenter.kt:100, 111` · `R/TvCards.kt:258` · harnais `CT/journey/TvSim.kt:47-135`, `PhoneSim.kt`, `ActivationApiSim.kt:12-41` · outils `tools/routes/routes.txt`, `tools/pilot/**` (w16-05), `tools/rental-test/rental_test.py` · docs `LOTS.md` § 3, 5, 6 ; `RENTAL-LOTS.md` § 1-2, 7, 13-15 ; `TRIAL-EDITION.md` § 15 ; `PARENTAL.md` ; `DESIGN-W5` § 7-9 ; `DESIGN-W10` § 5 ; `DESIGN-W11` § 3.2, § 4.2, § 6 ; `DESIGN-W12` § 2.7 ; `DESIGN-W14` § 3 ; `DESIGN-W16` § 1, § 3, § 6 ; `PLAN-STABILISATION` § 5-6.
