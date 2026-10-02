# Conception W12 : réglages signés et phase de « teasing » (expérimentation des valeurs du produit sans recompiler)

> **Statut : conception (Fable, architecte, 2026-10-02). Rien n'est implémenté par ce document.** Exécution : cahiers `docs/agent-briefs/sonnet-w12-NN-*.md` (index `SONNET-WAVE12-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents`. Tous les fichiers et lignes cités ont été relus le 2026-10-02 ; ce qui n'a pas pu être vérifié est marqué **BLOQUÉ** ou **non vérifié**. Aucun montant réel, aucun secret, aucun texte juridique (reporté au 2026-12-31).
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = tests serveur, `TPL/` = `backend/src/main/resources/templates/`.
>
> **Décision du propriétaire (2026-10-02)** : « le premier lancement sera teasing pour les réglages ». Lecture retenue : le premier lancement est une **phase d'expérimentation** destinée à trouver les bonnes valeurs des réglages du produit (durées, fenêtres, quotas, paquets, parts, et le choix entre plusieurs grilles de prix). Il faut donc que **chaque réglage commercial ou comportemental soit modifiable sans recompiler** les applications (la TV reste hors ligne : les valeurs lui arrivent par le téléphone, signées), **mesuré avec consentement**, et **réversible**.

## 0. En douze lignes

1. **Inventaire** (§ 1) : 61 valeurs recensées. **12** sont de l'argent et restent dans la **grille de prix signée hors ligne par le propriétaire** (W4-C § 6 ; le document de réglages ne peut jamais inventer un prix, seulement **choisir une grille pré-signée**) ; **29** sont des réglages commerciaux ou de comportement **portés par le nouveau document signé** ; **20** sont des **invariants de sécurité jamais réglables à distance** (clés de confiance, portées, super-admin, 48 h d'installation, grâce de verrou, signatures, consentement du tunnel, listes blanches).
2. **Mécanisme** (§ 2) : un document `settings` = **enveloppe `cbx1` existante, nouveau type `settings`** (ACTIVATION-FORMAT § 3 : « ajouter un type ajoute un analyseur de corps et une portée, jamais un second format »), signé par une clé portant **`POLICY`** (clé serveur `ScopedActivationSigner.java:17-18` ; clé maîtresse du bureau en secours), cible **`any`** (jamais un appareil : aucun favoritisme possible), **`seq` strictement croissant par clé**, fenêtre `notBefore`/`expiresAt` (90 j par défaut), corps = **liste fermée** de clés `set=<clé>|<valeur>` avec **bornes vérifiées par l'application** (`SettingsSchema`, même patron que `PolicyActions.BUDGETS`, `C/policy/PolicyActions.kt:32`) et des lignes `exp=` (registre d'expériences). **Refus net sur signature fausse** ; clé inconnue ignorée (compatibilité) ; valeur hors bornes **ignorée et journalisée** (le défaut compilé reste).
3. **Livraison** : serveur → téléphone (`GET /api/v1/settings/current`, toutes les 6 h avec `OrdersSyncJob`) → TV par le lien existant (Wi-Fi local `POST /api/settings/install`, trames Bluetooth 16-21 du canal propriétaire une fois `PolicyHub` branché : ORDRES.md § 13, **non fait**, `R/PolicyHub.kt:11-17`) ; **sans téléphone en ligne** : fichier `Download/CastBridge/settings` sur la clé USB (même veilleur que `activation`, `R/ActivationCenter.kt:145`), QR / texte collé (outil de bureau `DK/Qr.kt`). Cache local, **repli sur les défauts compilés**, **retour arrière = nouveau document (seq + 1) au contenu d'un ancien**, **interrupteur** = document `reset=all`.
4. **Composition** : une **clé d'activation garde sa propre durée** (`usage|duree|…`, ACTIVATION-FORMAT § 3.3) : les réglages ne changent que les **défauts des émetteurs** pour les clés **futures** ; une **location** garde la durée fixée dans son contrat (`rental|…` signé, `rentalDays` du catalogue) ; la **grâce** (`lock.graceDays`, `version.properties`) et les **48 h** (`ActivationPolicy.CODE_VALIDITY_HOURS`, `C/owner/Activation.kt:122`) restent **compilées**.
5. **Expériences** (§ 3) : registre minimal dans le document (`exp=<nom>|<clé>|<bras>|<sel>|<début>|<fin>|<garde-fou>`), **cohorte déterministe sans nouvel identifiant** (`SHA-256("castbridge-cohort-v1|sel|sujet")`, sujet = identifiant de licence ; sinon `kid` de l'agent ; sinon local), **la TV fait foi** (le téléphone lit `GET /api/settings` et affiche le même bras), métriques = comptabilité serveur (ventes, renouvellements : sans consentement, agrégées) + télémétrie avec consentement « statistiques » (propriété `exp=<nom>:<bras>`, jamais un identifiant), **seuils** repris de W10 § 10.2 ; avec 15-25 foyers, **tests séquentiels (semaine A / semaine B) et par point focal** plutôt qu'un A/B à prétention statistique.
6. **Console** (§ 4) : page web `/admin/settings` (TOTP) : éditeur avec bornes, **aperçu du diff**, « signer et publier », historique, retour arrière ; API additive `/api/v1/admin/settings/**` ; migration **`V<plus haut + 1>__settings.sql`** (V61 aujourd'hui, `backend/src/main/resources/db/migration/`, V62 convoité par w1-11, w2-10/W5, W4-C et W10 : **le cahier vérifie au lancement**) ; journal d'audit chaîné (même principe que `order_audit`, `V60__deferred_orders.sql:72`).
7. **UX** (§ 5) : **le prix montré à l'achat engage** (reçu avec `grid=<generatedAt>` et `variant=`), un changement de prix est annoncé « nouveau prix depuis le … », aucune rétroactivité ; ligne **« Réglages : v<seq> du JJ/MM (clé …), expire le … »** dans « Connexion & réglages » (`R/PlayerActivity.kt:617-632`), dans « À propos » du téléphone (`S/ConnectScreens.kt:142-148`) et dans l'onglet Activer de la console.
8. **Risques** (§ 6) : confusion de prix, TV incohérentes (parc par version de réglages, événement **essentiel** `settings_applied{seq}`), rejeu (seq + fenêtre), altération (fail-closed, bornes, aucune valeur d'argent), abus d'agents (ils ne signent rien ; cible `any` seulement).
9. **Première tranche** (§ 7) : **lecture seule de 10 clés** par le téléphone, la TV et les émetteurs + publication par la console : cahiers w12-01, 03, 04, 06, 07, 08 (≈ 11 j), sans dépendre de W4-W6 (non fusionnés : `tools/prices`, `S/shop`, `S/focal`, `C/owner/PhoneGate.kt`, `DegradedPolicy.kt` **absents** du dépôt le 2026-10-02).
10. **Interaction avec W4-W11** (§ 8) : liste des cahiers qui **codent en dur** des valeurs devant lire `Settings` (18 cahiers nommés) ; amendements à lire **sans éditer** ces cahiers.
11. **Effort** : 12 cahiers, 4 sous-vagues, ≈ **22 agent·jours** ; première tranche ≈ 11 j ; coût API estimé ≈ 9 $ (jauges § index).
12. **Décisions du propriétaire** (§ 9) : 8 questions, chacune avec une recommandation appliquée par défaut.

## 1. Inventaire des réglages (vérifié)

Légende : **Prop.** = qui consomme la valeur (TV / tél. / outils d'émission : console, bureau, serveur / serveur) ; **Sens.** = sensibilité (€ argent, 🔒 sécurité, UX) ; **Source** = où elle vit aujourd'hui ; **Régl.** = **G** grille signée propriétaire (argent), **D** document de réglages (ce W12), **J** jamais à distance (invariant). Les valeurs « W5/W10 » n'existent qu'en conception : elles entrent dans le schéma **à leur fusion** (clé réservée, défaut = valeur de conception).

### 1.1 Argent : grille de prix signée hors ligne (**G**, inchangée dans son principe)

| Clé (article) | Valeur aujourd'hui | Source | Prop. | Bornes | Note |
|---|---|---|---|---|---|
| `price=cle-essai\|30` | 0 | W4-C § 6 (`DESIGN-W4-VENTE-TERRAIN.md:94`) ; **`tools/prices` absent** | tél. agent, boutique | 0 | |
| `price=cle-production\|30/90/365` | **BLOQUÉ D9-bis** | W4-C:95-97, W5 § 3.1 | idem | ≥ 0 | durées de clé à confirmer |
| `price=loc-<bouquet>\|<rentalDays>` | BLOQUÉ | W4-C:98 ; `content/bundles-rental.json` (30 j) | idem | ≥ 0 | durée = catalogue, jamais une autre |
| `price=jetons\|20/60/150` | BLOQUÉ D-W5-1 | W5:68-70 | boutique | ≥ 0 | |
| `price=loc-oeuvre-<id>\|30`, `…-7j\|7`, `loc-chaine-<p>\|30`, `achat-pack-langues-<code>\|0` | hypothèses 150 / 50 / 500 / 500 XAF | W10 § 2.3:73-80, D-W10-2 | boutique | ≥ 0 | hypothèses d'expérience, jamais dans le code |
| `variant=<id>` (**nouveau**, en-tête optionnel) | `default` | ce document § 2.6 | tél., TV, serveur | `[a-z0-9-]{1,16}` | plusieurs grilles pré-signées ; le document de réglages **choisit** |

**Total G : 12 lignes de prix** (5 articles de clés/locations + 3 paquets de jetons + 4 familles W10). Le format de la grille ne change que par l'en-tête `variant=` (§ 2.6). Les lignes `set=tokens.*` (W5:73) et `set=works.*` (W10:77-78) **sortent de la grille** et vont dans le document de réglages (décision D-W12-3).

### 1.2 Réglages commerciaux et de comportement : document signé (**D**)

| # | Clé `settings` | Valeur aujourd'hui | Source (file:line) | Prop. | Sens. | Bornes retenues | Tranche |
|---|---|---|---|---|---|---|---|
| 1 | `trial.defaultDays` | 30 | `C/owner/Activation.kt:127` (`TRIAL_DEFAULT_DAYS`) ; `OWNER-CONSOLE.md:96` | émetteurs | € | 1–365 (`TRIAL_MAX_DAYS`, `:128`) | **1** |
| 2 | `trial.lotsWindowDays` | 3 | `C/lots/RentalLines.kt:23` (`TRIAL_DAYS`) | émetteurs, serveur (W5 § 4.6) | € | 1–3 (`:45`) | **1** |
| 3 | `trial.lotsWindowMinutes` | 720 | `C/lots/RentalLines.kt:22` ; texte `C/owner/KeyBadge.kt:47` | émetteurs, TV (badge) | € | 1–720 (`:45`) | **1** |
| 4 | `rental.defaultDays` | 30 | `C/lots/RentalDurations.kt:14` ; `content/bundles-rental.json` | émetteurs (bouquet sans `rentalDays`) | € | 1–366 (`RentalLines.MAX_DAYS`, `:11`) | **1** |
| 5 | `rental.maxOnlineDays` | 60 (W5) | `DESIGN-W5:11,62` | serveur | € | 1–60 | W5 |
| 6 | `rental.maxConcurrent` | 3 (W5) ; code TV 20 | `DESIGN-W5:305` ; `C/lots/RentalLines.kt:14` (`MAX_CONCURRENT`) | serveur | € | 1–20 | W5 |
| 7 | `rental.freeReissues` | 3 (W5) | `DESIGN-W5:347` | serveur | € | 0–10 | W5 |
| 8 | `shop.orderTtlHours` | 72 (W5) | `DESIGN-W5:196` | serveur, tél. | UX | 24–168 | W5 |
| 9 | `shop.trialReadOnly` | 1 (W5) | `DESIGN-W5:306` (`ShopPolicy.trialMayOrder`) | tél., TV | € | 0/1 | W5 |
| 10 | `quiz.tasterPerDay` | 3 (W5) | `DESIGN-W5:269` (`TrialPolicy.quizTasterPerDay`) | TV | UX | 0–20 | W5 |
| 11 | `tokens.expiryDays` | 0 | `DESIGN-W5:73` (grille) | serveur, TV | € | 0–730 | W5 |
| 12 | `tokens.offlineGrantMax` | 60 | `DESIGN-W5:73,280` | serveur | €/🔒 | 0–200 (perte maximale assumée) | W5 |
| 13 | `tokens.kidDailyDefault` | 0 | `DESIGN-W5:73,285` | TV | UX | 0–50 | W5 |
| 14 | `tokens.welcome` | 10 | `DESIGN-W5:264` | serveur | € | 0–50 | W5 |
| 15 | `tokens.secondChance` / `extraJoker` / `swapQuestion` | 5 / 2 / 3 | `DESIGN-W5:263` | TV | € | 0–50 chacun | W5 |
| 16 | `works.shareProducer` / `works.shareAgent` | 60 / 15 (%) | `DESIGN-W10:77,85` | serveur (relevés), tél. (affichage) | € | 0–100, somme ≤ 100 | W10 |
| 17 | `works.minPayoutXaf` | 5 000 | `DESIGN-W10:78` | serveur | € | 0–100 000 | W10 |
| 18 | `works.tvBudgetMb` / `works.phoneBudgetMb` | 200 / 300 | `DESIGN-W10:261` (`WorkBudget`) | TV, tél. | UX | 50–2 000 / 50–4 000 | W10 |
| 19 | `langues.packUpdateMonths` | 12 | `DESIGN-W10:65` | serveur, tél. | € | 1–36 | W10 |
| 20 | `langues.phoneQuotaMb` | **2048 (code)** vs **500 (doc)** | `C/langues/LangBudget.kt:75` (`PHONE_LANG_DEFAULT_MB`) ; `docs/LANGUES.md:439` ; mémoire du propriétaire : 500 | tél. | UX | 100 (`:76`)–6 144 | **1** |
| 21 | `lots.tvBudgetMb` | 10 | `C/lots/LotApi.kt:30` ; déjà `budget.set lots_mb` (`PolicyActions.kt:32`) | TV | UX | 0–100 000 (mêmes bornes) | **1** |
| 22 | `pairing.autoWindowMin` | 10 (W7) | `DESIGN-W7:12,129` ; **aucune constante en code** | TV | 🔒/UX | 1–10 | **1** |
| 23 | `pairing.knockWindowMin` | 2 | `DESIGN-W7:26` (comportement existant) ; TV : `C/trust/PairingSession.kt` (**non vérifié** pour la fenêtre, `blockMs` 10 min `:19`) | TV | 🔒/UX | 1–5 | **1** |
| 24 | `pairing.blockMin` | 10 | `C/trust/PairingSession.kt:19` | TV | 🔒 | 5–60 (plancher 5 : jamais moins) | **1** |
| 25 | `telemetry.flushMin` | 15 | `docs/TELEMETRY.md:63` ; `C/telemetry/TelemetryUploader.kt` (**ligne non vérifiée**) | TV, tél. | UX | 5–120 | **1** |
| 26 | `reduced.reminderPerDay` / `reduced.tickMin` | 1 / 15 (W4-B) | `DESIGN-W4-MODE-DEGRADE.md:41-42` | TV | UX | 0–3 / 5–60 | W4-B |
| 27 | `proof.validityDays` / `proof.staleDays` | 14 / 7 (W6) | `DESIGN-W6:156-160,233` (`ProofPolicy.VALIDITY_DAYS` 1–30) | tél. | 🔒/UX | 1–30 / 1–14 | W6 |
| 28 | `delegation.defaultDays` / `maxDays` / `maxSales` | 90 / 180 / 200 (W4-C) | `DESIGN-W4-VENTE-TERRAIN.md:32,41,135` | émetteurs | € | 1–180 / ≤ 180 / 1–10 000 | W4-C |
| 29 | `ui.idleDimMin` | 10 (W11) | `DESIGN-W11:248` | TV | UX | 1–60 | W11 |
| — | `price.variant` | `default` | § 2.6 | tél., TV, serveur | € | identifiant d'une grille pré-signée | **1** |
| — | `settings.message` | — | § 2.4 (texte brut ≤ 280, comme `message.show`) | tél., TV | UX | — | **1** |

**Total D : 29 familles de clés (41 clés élémentaires)** ; **10 clés dans la première tranche** (§ 7).

### 1.3 Invariants : jamais réglables à distance (**J**)

| # | Invariant | Où (vérifié) | Pourquoi |
|---|---|---|---|
| 1 | Clés de confiance d'activation `BuildConfig.TRUSTED_KEYS` (fichier hors dépôt) | `android/receiver/build.gradle.kts:27-39` ; `C/owner/TrustedKeyParser.kt:17-21` (fail-closed) | la racine de confiance ne se change que par un message `keyring` signé par la clé de secours (W7 D-W7-4) ou une recompilation |
| 2 | Clés des mises à jour / catalogues `UpdateKeys.PUBLIC_KEYS` | `C/update/UpdateKeys.kt:16-19` | idem |
| 3 | Portées des clés et `SERVER_SCOPES` (jamais `TRANSFER`, `COMMAND_OPEN_ALL`, `SUPER_UNLIMITED`) | `C/owner/Keys.kt:21-43` ; `B/licenses/ScopedActivationSigner.java:14-18` | un réglage ne peut pas élargir une portée |
| 4 | Super-admin : `SUPERADMIN_BCRYPT`, `TapSequence` 7/2 500 ms/3 000 ms, `UnlockGuard` 3/10/30 min/24 h | `C/owner/SuperAdminGate.kt:14,34,47` ; `C/owner/OwnerVault.kt:62` | |
| 5 | Fenêtre d'installation **48 h** de toute clé | `C/owner/Activation.kt:122` ; `ActivationIssuer.kt:59` | politique commerciale **figée** (OWNER-CONSOLE.md § 48 h) |
| 6 | Grâce de verrou `lock.graceDays` / `lock.graceStartMs` (D1 : passer à 0 par recompilation) | `version.properties` ; `receiver/build.gradle.kts:36,38` ; `C/owner/FeatureGate.kt:27-48` | un réglage à distance ne doit jamais pouvoir **rallonger** une grâce ; la raccourcir à 0 est une décision de build (D1) |
| 7 | `LOCKED_WHITELIST` et liste blanche des routes d'essai `TrialPolicy` | `C/owner/FeatureGate.kt:20` ; `C/owner/TrialPolicy.kt:31-38` | ouvrir une route en essai = conception, pas réglage ; seuls des **drapeaux de visibilité** (ex. aperçus) sont réglables |
| 8 | « Tout ouvert » ≤ 30 j, `Power.maxDays`, `MAX_EXTENSION_MS` 366 j | `Activation.kt:136` ; `Keys.kt:10-14` ; `PolicyActions.kt:36` | plafonds de pouvoir |
| 9 | Vérifications de signature, forme canonique, `seq` strictement croissant, `MAX_TOKEN` 4 000 | `C/owner/Envelope.kt`, `Order.kt:54-77`, `PolicyEngine.kt:41` | |
| 10 | `TvClock` : saut ≤ 400 j, `AHEAD_MAX` 45 j, marge 24 h | `C/owner/Keys.kt:84-88,152` | un réglage n'élève jamais le plancher d'horloge (ORDRES § 8) |
| 11 | Consentement du tunnel (`TunnelTerms.VERSION`, acceptation liée à l'installation) et interrupteur par TV côté serveur | `C/tunnel/TunnelTerms.kt:18,55-56` ; `B/tunnel/TunnelService.java:306` | un réglage ne vaut pas consentement |
| 12 | Consentement télémétrie (deux niveaux) et liste des clés interdites | `docs/TELEMETRY.md:19-32,55-58` ; `B/telemetry/TelemetryService.java:120` | |
| 13 | `allowBackup=false`, règles d'exclusion | `docs/OWNER-CONSOLE.md:115` | |
| 14 | Liste `NEVER` des ordres, liste fermée d'actions | `PolicyActions.kt:39-42` ; `B/orders/PolicyCatalog.java:15-21` | le document de réglages a **sa propre** liste fermée (`SettingsSchema`) |
| 15 | PIN / jeton `X-CB-Token`, verrou progressif `PinGuard` | `C/tv/ReceiverServer.kt:523-541` | |
| 16 | Dérivation du coffre (PBKDF2 600 000, Argon2 cible), HMAC du porte-jetons | `OL/OwnerStore.kt:29-39` ; W5 § 6.5 | |
| 17 | Plafond de transferts (par licence, serveur) | `B/licenses/LicenseApiController.java:111-114` (`/{id}/settings` : `graceDays`, `transferCap`) | réglage **par licence** existant, hors document global |
| 18 | `MAX_DAYS` 366, `MAX_GRACE_DAYS` 30, `MAX_USAGE_MINUTES` des locations | `C/lots/RentalLines.kt:11-13` | bornes **supérieures** des réglages, pas des réglages |
| 19 | Période du balayeur `RentalSweeper` 6 h, `lessonDeferralMs` 15 min | `C/lots/RentalSweeper.kt:41` ; `RentalEngine.kt:25-33` | technique, sans valeur produit |
| 20 | Tolérance d'abonnement 7 j, ≤ 30 j | `C/lots/Entitlement.kt:175` ; ACTIVATION-FORMAT § 3.3 | portée par le droit signé |

**Total J : 20 invariants.** Règle générale : **tout ce qui ouvre un droit, élargit une portée, rallonge une grâce ou affaiblit une vérification n'est pas un réglage.**

### 1.4 Écarts constatés (à trancher, § 9)
- Langues : **2048 Mo en code** (`LangBudget.kt:75`, test `LanguesTest.kt:221`) contre **500 Mo décidés** (`docs/LANGUES.md:439`) ; aucune lecture en production : la clé `langues.phoneQuotaMb` (défaut compilé **500** après w12-01) règle l'écart.
- `FeatureGate.ActivationRequirement.graceDays = 14` (`FeatureGate.kt:27`) mais la TV passe toujours `BuildConfig.ACTIVATION_GRACE_DAYS` = 30 : défaut mort, à aligner par w12-11 (doc) ; pas un réglage.
- Fenêtres d'appairage 10 min / 2 min : **conception W7 seulement** (`DESIGN-W7:12,129`) ; le code n'a que `PairingSession.blockMs` 10 min. Les clés 22-23 n'ont d'effet qu'après w7-06/w7-14.
- Il n'existe **aucune** classe `TrialWindow` (seulement `CT/lots/TrialWindowTest.kt:10`) : les durées sont dans `RentalLines`.

## 2. Mécanisme : le document `settings`

### 2.1 Pourquoi une enveloppe `cbx1` et pas un troisième format
Le dépôt a déjà **deux** patrons de document signé : (a) les catalogues JSON signés par la clé des mises à jour (`SignedBundleCatalog.kt:34-40` : texte canonique, `generatedAt` anti-retour, **sans `seq` ni expiration**, vérifiés contre `UpdateKeys`), (b) l'enveloppe `cbx1` (`Envelope.kt:5-60` : `kid`, `seq` par clé, `nonce`, fenêtre, cible, signature Ed25519, forme canonique) avec un vérificateur commun et un moteur d'application idempotent sur la TV (`PolicyEngine.kt`). Les réglages ont besoin de **seq**, **fenêtre**, **portée de clé**, **retour arrière** et **transport existant jusqu'à la TV hors ligne** : c'est (b). ACTIVATION-FORMAT § 3 l'exige : « ajouter un type ajoute un analyseur de corps et une portée, jamais un second format ». Les ordres `flag.set`/`budget.set` (≤ 16 paramètres, delta par ordre) sont **trop étroits** et **TV seulement** : le document de réglages est un **instantané complet**, lu par la TV **et** le téléphone **et** les outils d'émission.

### 2.2 Format (corps du type `settings`)
En-tête d'enveloppe inchangé (ACTIVATION-FORMAT § 3.2) : `type=settings`, `kid` (clé portant **`POLICY`**), `seq` (**compteur propre aux réglages**, voir § 2.3), `nonce`, `issuedAt`, `notBefore` = `issuedAt`, `expiresAt` = `issuedAt` + validité (**90 j par défaut, 1 h–366 j**), **`target=any`** (seul `any` est accepté en v1 : `WRONG_TARGET` sinon). Corps, lignes **triées**, forme canonique obligatoire :
```
schema=1                                   version du schéma de clés (SettingsSchema.VERSION)
basedOn=<seq ou 0>                         pour un retour arrière : le seq dont le contenu est repris (informatif, journalisé)
reset=all                                  (optionnel, seul dans le corps avec schema=) : interrupteur, tout revient aux défauts compilés
set=<clé>|<valeur>                         ≤ 64 lignes ; clé [a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){1,3} ≤ 48 car. ; valeur ≤ 128 car., sans saut de ligne
exp=<nom>|<clé>|<bras>|<sel>|<début ms>|<fin ms>|<garde-fou>   ≤ 8 lignes ; nom [a-z0-9-]{1,24} ; bras = a:<val>,b:<val>[,c:<val>] (≤ 4) ; garde-fou = <clé-métrique>:<min>:<max> ou vide
```
Un `set=` pour une clé qui est aussi la clé d'une `exp=` donne la **valeur hors expérience** (bras par défaut `a`). Jeton ≤ 4 000 caractères (`PolicyEngine.MAX_TOKEN`, `:41`) : avec 64 lignes de 60 caractères on reste sous 4 000 ; au-delà, refus `TOO_LARGE`.

### 2.3 Vérification (ordre exact, premier échec = motif ; `SettingsVerifier`, cœur Kotlin, miroir Java et Python)
1. taille ≤ 4 000 (`TOO_LARGE`) ; 2. décodage et forme canonique (`MALFORMED`) ; 3. `type=settings` (`UNKNOWN_TYPE`) ; 4. clé connue dans l'anneau (**mêmes `TRUSTED_KEYS` que les activations et les ordres**) et non révoquée (`UNKNOWN_KEY`, `REVOKED_KEY`) ; 5. signature (`BAD_SIGNATURE`) ; 6. portée **`POLICY`** (`KEY_NOT_ALLOWED`) ; 7. `target=any` (`WRONG_TARGET`) ; 8. **`seq` strictement supérieur** au dernier accepté **pour cette clé, dans l'espace `settings`** (`STALE_SEQUENCE`) : l'espace est distinct de celui des ordres (`SeqState` séparé, fichier séparé) pour que les deux compteurs du serveur restent indépendants ; 9. fenêtre : `notBefore − 24 h ≤ maintenant ≤ expiresAt` sur le temps **de confiance** (`TvClock` côté TV ; horloge murale côté téléphone, avec le plancher `issuedAt` des activations : un document ne **lève jamais** le plancher) ; 10. schéma : `schema` ≤ celui de l'application **ou supérieur** (clés inconnues ignorées) ; `reset=all` exclusif ; 11. **chaque `set=`** : clé connue → valeur analysée selon son type (entier, booléen, identifiant) et **bornée** par `SettingsSchema` ; hors bornes ⇒ **ligne ignorée + journal `OUT_OF_BOUNDS(clé)`** (le défaut compilé reste) ; clé inconnue ⇒ ignorée + journal `UNKNOWN_KEY_IGNORED` ; 12. `exp=` : syntaxe, clé connue, bras dans les bornes, `fin > début`, sinon **expérience ignorée** (jamais le document entier). Un document accepté **remplace** entièrement l'état (`SettingsState = défauts + set acceptés`) : aucun delta, idempotent, **rejouer le même jeton renvoie le même accusé** (même mécanique que `PolicyEngine.receive`, `:71-83`).

**Fail-closed** : tout échec aux étapes 1-9 = **rien n'est appliqué, l'état précédent reste** (jamais les défauts : un attaquant ne peut pas « remettre à zéro » en envoyant un faux document). Un document **expiré** (étape 9, `WINDOW_CLOSED`) : voir § 2.5.

### 2.4 Schéma (`SettingsSchema`, liste fermée, un seul fichier, trois miroirs)
Une entrée par clé : `name`, `type` (`INT`, `BOOL`, `ID`, `TEXT`), `min`/`max` ou regex, **`default` = la constante compilée actuelle** (le schéma **réexporte** `ActivationPolicy.TRIAL_DEFAULT_DAYS`, `RentalLines.TRIAL_DAYS`, etc. : une seule source), `consumers` ∈ {TV, PHONE, ISSUER, SERVER}, `sensitivity` ∈ {MONEY, SECURITY, UX}, `since` (version du schéma). **Même patron** que `PolicyActions.BUDGETS` (`PolicyActions.kt:32`). Parité **Kotlin / Java / Python** par un fichier partagé **`tools/settings/schema.json`** (comme `tools/orders/actions.json` pour les actions, ORDRES § 4) et un test qui échoue si les listes divergent. Les clés **W5/W10** sont déclarées dès w12-01 avec leurs défauts de conception, pour que les cahiers W5/W10 les lisent au lieu de coder en dur (§ 8). `settings.message` : texte brut ≤ 280, sans lien (règle de `MESSAGE_SHOW`, `PolicyActions.kt:123`).

**Ce que le schéma refuse par construction** : toute clé dont le nom commence par `key.`, `scope.`, `trust.`, `grace.`, `super.`, `tunnel.consent`, `update.verify` (liste `NEVER` propre, testée comme `PolicyActions.NEVER`, `:39`).

### 2.5 Cache, repli, expiration, retour arrière, interrupteur
- **Cache** : TV `files/settings/current.txt` (jeton tel quel + accusé) et `files/settings/state.json` (état, seq par clé, journal ≤ 100 lignes), écriture atomique (`QueueStore`, comme `PolicyStorage`, `PolicyEngine.kt:17-20`) ; téléphone `files/settings/current.txt` ; bureau `~/.castbridge-activation/settings.txt` ; console téléphone : `OwnerStore` (nouvelle entrée `settings=`).
- **Repli** : sans document (ou fichier abîmé) ⇒ **défauts compilés**, sans plantage ; l'application **ne distingue pas** « défaut compilé » et « réglage reçu » dans son code métier : une seule API `Settings.int("trial.defaultDays")`.
- **Expiration** (recommandation D-W12-7) : à `expiresAt`, les **expériences s'arrêtent** (bras par défaut), les **valeurs restent appliquées** (une TV hors ligne ne doit pas changer de comportement sans message), la ligne de diagnostic dit **« périmé »**, et les **émetteurs** (console, bureau, serveur) refusent d'émettre avec un document périmé de plus de 30 j sans confirmation explicite. Jamais de minuteur qui verrouille (ORDRES § 2 « pas de brique »).
- **Retour arrière** : la console publie un **nouveau** document (`seq + 1`, `basedOn=<ancien seq>`) au contenu de l'ancien ; un ancien jeton **ne peut jamais** être réappliqué (`STALE_SEQUENCE`). Historique serveur = tous les jetons publiés (immuables) + audit chaîné.
- **Interrupteur** : document `reset=all` (seq + 1) ⇒ défauts compilés partout, expériences arrêtées, cache conservé (seq) ; côté serveur, `CASTBRIDGE_SETTINGS_ENABLED=false` ⇒ `GET /api/v1/settings/current` → 404 (les applications gardent leur dernier état : **une panne du serveur ne change rien**).

### 2.6 Composition avec les prix, les clés d'activation et les locations
- **Prix** : la grille reste signée **hors ligne par le propriétaire** (clé des mises à jour, W4-C § 6) ; le serveur peut héberger **plusieurs grilles** (`prices-<variant>.json`, en-tête `variant=<id>`, `GET /api/v1/catalog/prices?variant=`) ; le document de réglages porte `price.variant` (ou une `exp=` dessus). Les outils refusent une grille dont la `variant` n'est pas celle attendue ; **un reçu porte `grid=` et `variant=`** (le `grid=` existe déjà, `DESIGN-W4-VENTE-TERRAIN.md:68`). **Conséquence de sécurité** : la clé `POLICY` du serveur **ne peut pas créer un prix**, seulement choisir parmi ceux que le propriétaire a signés.
- **Clés d'activation** : `trial.defaultDays`, `trial.lotsWindow*` sont lus par **l'émetteur** au moment d'émettre ; la clé émise porte sa durée (`usage|duree|…`) et sa fenêtre (`rental|essai|…`) et la TV **n'en lit que le droit signé**. Une TV ne relit **jamais** un réglage pour recalculer la durée d'une clé installée (« une clé installée reste », OWNER-CONSOLE.md:87).
- **Locations** : la durée est **fixée par le serveur à l'achat** (contrat, `rentalDays` du catalogue de bouquets, W5 P2) ; `rental.defaultDays` ne sert qu'aux bouquets sans `rentalDays` et aux émissions hors ligne du bureau ; `rental.maxOnlineDays` borne le serveur. Un contrat livré ne change pas.
- **Ordres différés** : les clés `lots.tvBudgetMb` et `budget.set lots_mb` visent le même paramètre : **le document de réglages a priorité** quand il est plus récent (`issuedAt`), sinon l'ordre ; w12-07 documente la règle et la teste. `flag.set` reste pour les drapeaux ; aucun drapeau n'est dupliqué dans le schéma.

### 2.7 Livraison (trois chemins, tous vers le même `SettingsEngine`)
```
Console /admin/settings (TOTP) ─sign (clé POLICY)─► table settings_doc (seq, jeton) ─► GET /api/v1/settings/current {seq, token}
     │                                                                                        │ toutes les 6 h (OrdersSyncJob) ou à la demande
     │ (secours) bureau : tools/settings/sign_settings.py --key maître → jeton                 ▼
     │          → fichier Download/CastBridge/settings (clé USB) / QR (DK/Qr.kt) / texte    Téléphone : SettingsRuntime (vérifie, cache, applique)
     ▼                                                                                        │ Wi-Fi local POST /api/settings/install (PIN / téléphone de confiance)
TV : SettingsHub.receive(token) ◄── veilleur USB (ActivationCenter.kt:145, fichier « settings ») ◄── trames 16-21 (après w12-09)
     │ GET /api/settings → {seq, kid, issuedAt, expiresAt, schema, cohorts=…, stale}
     ▼ (le téléphone affiche ce que la TV a réellement)
```
- **Téléphone** : `SettingsRuntime` (même mécanique que `OrdersRuntime`, `S/OrdersRuntime.kt:25,38`) ; à chaque liaison avec une TV : `GET /api/settings` ; si `seq` TV < seq téléphone ⇒ `POST /api/settings/install` ; si TV > téléphone ⇒ le téléphone **adopte** le jeton de la TV (il peut avoir été livré par USB) après vérification.
- **TV sans aucun téléphone ni Internet** : clé USB (point focal) ; en dernier recours, saisie **impossible** (≈ 1 500 caractères) : on ne prévoit pas de clé compacte pour les réglages (les défauts compilés suffisent à une TV isolée).
- **Outils d'émission** : bureau `catalogue-serveur`-like `reglages-serveur` (tire le jeton) ou import fichier ; console téléphone (APK `:owner` **sans permission réseau**, `android/owner/build.gradle.kts:7-8`) : import par fichier / collage / QR, ou automatiquement quand la console tourne dans l'app téléphone (entrée super-admin, `SuperAdmin.kt`).

### 2.8 Télémétrie des réglages (sans consentement « statistiques » : essentiel)
Événement **essentiel** `settings_applied{seq, kid, schema, stale}` (état des fonctions : TELEMETRY.md § 2, « état des fonctions ») envoyé à chaque changement de seq : le serveur sait **quelle version de réglages chaque appareil applique** (parc par version) sans aucune donnée personnelle de plus que le battement de cœur existant. Pas d'`exp` dans cet événement.

## 3. Expériences

### 3.1 Registre (dans le document, § 2.2 `exp=`), champs
`nom`, `clé` réglée, `bras` (≤ 4, chacun une valeur **dans les bornes**), `sel` (8 hex, change ⇒ réassignation), `début`/`fin` (ms ; hors fenêtre ⇒ bras `a`), `garde-fou` (`<métrique>:<min>:<max>` : affiché à la console ; **jamais appliqué automatiquement** par les applications). Le registre complet (hypothèse, métrique, seuil go/no-go, décision) vit **côté serveur** (`settings_experiment`, § 4) ; le document ne porte que ce que les appareils doivent savoir.

### 3.2 Cohortes sans identifiant supplémentaire
`bras = bras[ SHA-256("castbridge-cohort-v1|" + sel + "|" + sujet)[0] mod n ]` avec **sujet** = (1) identifiant de **licence** (`license=` de l'activation installée, `lic-…`) — le téléphone et la TV d'un même foyer tombent dans le **même bras** ; (2) sinon (essai, `license=trial`) le `kid` de la **délégation** de l'agent qui a vendu (W4-C) ou, sans W4-C, (3) un **sel local** aléatoire jamais envoyé. **La TV fait foi** : `GET /api/settings` renvoie `cohorts=<nom>:<bras>,…` et le téléphone affiche la même chose pour la TV liée ; au serveur, le téléphone envoie **le bras** (pas le sujet) dans la demande de devis (W5 `/shop/quote`), le serveur **revalide** que le prix demandé est celui de la grille du bras. Aucun identifiant nouveau n'est créé ni transmis ; `exp=<nom>:<bras>` est une propriété **à faible cardinalité**, autorisée dans la liste blanche de la télémétrie (`EventCatalog`), et **n'est jointe qu'aux événements déjà soumis au consentement « statistiques »** (`shop_view`, `shop_order`, `tokens_spend`, `playback_end`, `work_play`, `content_stat`).

### 3.3 Métriques, sources et consentement
| Métrique | Source | Consentement | Agrégation |
|---|---|---|---|
| Conversion essai → production | serveur : `lic_issuance` (`V51__licenses_core.sql`) + journal des agents (W4-C `SALE`) ; par bras : la vente porte le bras au moment de l'émission | aucun (comptable) | par bras, par semaine, par point focal |
| Locations : longueur **utilisée** vs achetée | télémétrie `playback_end`/`content_stat` + `work_play` (W10) avec `exp` | **statistiques** | médiane, % de contrats dont ≥ 50 % de la durée est utilisée |
| Renouvellements, 7 j vs 30 j | serveur `shop_order` (W5) ou CSV du pilote papier (W10 § 10.4) | aucun | taux à J+durée |
| Achats de jetons, dépense | serveur grand livre (W5) ; `tokens_spend{item, exp}` | aucun / statistiques | par bras |
| Complétions (leçons, quiz) | `content_stat` (`EventCatalog.java:117-118`), `quiz_game` | statistiques | par bras |
| Charge d'assistance, refus « trop cher » | fiche agent (papier), W10 § 10.5 | consentement oral sur fiche | manuel |
| Parc par version de réglages | `settings_applied` | essentiel | nombre d'appareils par seq |

**Chemin hors ligne** : file locale `EventQueue` (2 Mo, `TELEMETRY.md:62-64`), envoi au retour du réseau ou par la passerelle Bluetooth du téléphone ; **rien n'est ajouté** au chemin. Limite honnête : une TV jamais en ligne ne remonte rien ; les ventes (serveur, agents) restent mesurables.

### 3.4 Décision et retour vers la console
Avec **15-25 foyers** (W10 § 10.6), un A/B simultané n'a **aucune puissance statistique** sur une conversion : recommandation **D-W12-5** : (a) **tests séquentiels** (semaine A valeur 1 / semaine B valeur 2, même cohorte) pour les durées et fenêtres ; (b) **cohorte par point focal** (2 agents = 2 bras) pour un prix ; (c) l'A/B par licence seulement quand le parc dépasse ~200 foyers. Seuils : ceux de W10 § 10.2 (H1-H11) ; une expérience sans garde-fou ni seuil écrit **n'est pas publiable** (la console l'exige). La page `/admin/kpi/experiments` (w12-10) montre par expérience : bras × (appareils, événements, métrique), fenêtre, garde-fou, et un bouton « clore » qui **propose** le document suivant (bras retenu ⇒ `set=`) sans le signer. Décision = le propriétaire (TOTP) ; **jamais automatique**.

## 4. Console du propriétaire et serveur

### 4.1 Pages (`/admin/settings`, TOTP, CSRF, CSP comme `/admin/orders`)
1. **Réglages courants** : tableau clé / défaut compilé / valeur publiée / bornes / sensibilité / consommateurs / dernière modification ; filtre par famille ; **une clé € exige la saisie du TOTP** à la publication (comme les gestes de la boutique, W5).
2. **Brouillon** : édition avec validation des bornes **côté serveur** (`SettingsSchema` Java, miroir), message d'erreur par clé, **aperçu du diff** (ancien → nouveau, clés ajoutées / retirées / inchangées), **estimation d'impact** (« 3 clés € : les émetteurs changeront de défaut ; 0 clé 🔒 »), zone `exp=` (nom, clé, bras, sel généré, dates, garde-fou, hypothèse, métrique, seuil : les 4 derniers restent en base).
3. **Signer et publier** : TOTP ⇒ `seq` suivant **sous verrou** (`settings_key_seq`, même patron que `order_key_seq`, `V60__deferred_orders.sql:6`), signature par **`OrderSigner`** existant (clé `POLICY`, `B/orders/OrderSigner.java`) ⇒ `settings_doc` (immuable) ⇒ audit chaîné.
4. **Historique** : liste des seq (date, auteur, résumé du diff, `basedOn`), **« Rétablir cette version »** = brouillon pré-rempli (seq + 1) ; « Interrupteur » = brouillon `reset=all`.
5. **Parc** : appareils par seq appliqué (`settings_applied`), périmés, jamais vus.
6. **Grilles** : liste des grilles de prix déposées (`variant`, `generatedAt`, `keyId`) — affichage seulement : **le serveur ne signe pas de grille**.

### 4.2 API additive (`B/settings/**`, module `castbridge.settings.enabled=false` par défaut)
| Route | Rôle |
|---|---|
| `GET /api/v1/settings/current` (jeton d'appareil ; `ETag` = seq) | `{"seq":n,"token":"cbx1…","expiresAt":ms}` ; 404 si module éteint ou rien publié |
| `GET /api/v1/settings/{seq}` | un jeton publié (immuable) |
| `GET /api/v1/catalog/prices?variant=` (W4-C w4-12/16, **non fusionné**) | relais des grilles ; w12 n'y touche pas, note l'interface |
| `GET /api/v1/admin/settings/schema` | le schéma (bornes, défauts) |
| `GET/PUT /api/v1/admin/settings/draft` | brouillon ; `PUT` valide les bornes (400 par clé) |
| `GET /api/v1/admin/settings/draft/diff` | diff contre le dernier publié |
| `POST /api/v1/admin/settings/publish` (TOTP) | signe, attribue `seq`, enregistre, audite |
| `GET /api/v1/admin/settings/history` ; `POST …/history/{seq}/restore` | liste ; brouillon pré-rempli |
| `POST /api/v1/admin/settings/kill` (TOTP) | publie `reset=all` |
| `GET /api/v1/admin/settings/audit/verify` | première ligne altérée |
| `GET/POST /api/v1/admin/settings/experiments` | registre complet (hypothèse, métrique, seuils, décision) |
| `GET /api/v1/admin/kpi/experiments` (w12-10) | bras × métriques |

### 4.3 Tables (migration `V<plus haut + 1>__settings.sql` ; **le cahier w12-03 vérifie `git log --all -- backend/src/main/resources/db/migration` au lancement** : V61 aujourd'hui ; V62 est **convoité** par w1-11 (`sonnet-w1-11-backend-hygiene.md:29`), w2-10/W5 (`V62__shop.sql`, `DESIGN-W5:79`), W4-C (`V62__agents.sql`, `DESIGN-W4-VENTE-TERRAIN.md:112`) et W10 (« plus haut + 1 ») ; **recommandation : W12 prend le numéro libre au moment de sa fusion, jamais un numéro réservé à l'avance**)
```
settings_key_seq   (kid PK, last BIGINT)
settings_doc       (seq PK, kid, token TEXT, body_json TEXT, based_on BIGINT NULL, issued_at, expires_at, published_by, published_at, summary VARCHAR(200))
settings_draft     (id PK=1, body_json TEXT, updated_by, updated_at)
settings_experiment(name PK, key, arms_json, salt CHAR(8), starts_at, ends_at, guardrail VARCHAR(64), hypothesis TEXT, metric VARCHAR(64), go_threshold, nogo_threshold, state ENUM(DRAFT, RUNNING, CLOSED), decision TEXT NULL, closed_at NULL)
settings_audit     (id PK, at, actor, action, seq NULL, detail TEXT, prev_hash CHAR(64), hash CHAR(64))    -- chaîné comme order_audit
settings_device    (device_id PK FK device, seq, kid, schema, stale BOOL, seen_at)                          -- alimentée par settings_applied
kpi_experiment_day (exp, arm, day, metric, n INT, value DOUBLE, PK(exp, arm, day, metric))                  -- w12-10, migration suivante
```
Déploiement sûr : migration additive, interrupteur éteint par défaut, préproduction d'abord (port 7091, clé de test), retour = `CASTBRIDGE_SETTINGS_ENABLED=false` (tables inertes) ; **Flyway ne revient pas en arrière** (`VERSIONING-DEVOPS-2026-10-02.md:166`) : sauvegarde avant.

### 4.4 Outil de bureau (secours hors ligne) et console téléphone
- `tools/settings/sign_settings.py --schema tools/settings/schema.json --in reglages.txt --key <clé POLICY : maître du bureau> --seq <n> --valid-days 90 --out settings.cbx1` (Python, `cryptography`, **mêmes octets** que Java/Kotlin, vecteurs `tools/activation/settings-vectors.json`) ; `--qr` (PNG) et `--usb <dossier>` (écrit `Download/CastBridge/settings`). Usage : serveur en panne ou TV isolée. La clé du bureau porte `POLICY` (ACTIVATION-FORMAT § 2 : « bureau = toutes ») ; la clé du **téléphone propriétaire ne porte jamais `POLICY`** (`OWNER-CONSOLE.md:102`, `OL/OwnerStore.kt:54`) : **la console téléphone ne signe pas de réglages** (décision D-W12-1), elle les **lit**.
- **Attention aux séquences** : deux signataires (serveur, bureau) = deux `kid` = deux compteurs ; l'appareil garde le **document le plus récent par `issuedAt`** parmi les clés acceptées (règle écrite et testée) ; la console web affiche un document de secours reçu via `settings_applied` comme « hors console ».

## 5. UX : montrer des valeurs qui peuvent changer sans troubler

1. **Prix** : le prix affiché vient de la grille **du bras de la TV** ; à l'achat, le reçu / la commande portent `grid=`, `variant=`, le prix ; **le prix affiché au moment de la commande engage** (le serveur refuse un devis dont le prix diffère de la grille du bras : « le prix a changé, voici le nouveau ») ; un renouvellement se fait **au prix courant**, dit avant confirmation ; **pas de rétroactivité** ni de remboursement automatique ; baisse de prix dans les **7 jours** après un achat ⇒ geste du propriétaire (TOTP, W5), **jamais automatique** (règle de non-prédation W5 § 6.1 : pas d'« offre qui expire »).
2. **Durées** : la fiche dit toujours la durée **du droit qui sera émis** (« 30 jours », lue des réglages au moment de l'affichage) et, pour un droit **déjà installé**, celle du droit (badge, compte à rebours) ; jamais « la durée a changé » sur un droit en cours.
3. **Changements** : un `settings.message` (texte brut, ≤ 280) accompagne tout changement commercial (« À partir du 15/10, les locations durent 45 jours ») ; affiché une fois (téléphone) / bandeau discret (TV), comme `message.show`.
4. **Diagnostic** : ligne **« Réglages : v<seq> · <JJ/MM/AAAA> · clé <4 premiers hex> · expire le <date> [périmé] »** dans « Connexion & réglages » de la TV (`PlayerActivity.kt:617-632`), dans « À propos » du téléphone (`ConnectScreens.kt:142-148`), dans l'onglet Activer de la console (« Réglages v<seq> : essai 30 j, lots 3 j/720 min ») et dans le bureau (`emettre` affiche la version utilisée) ; **« Réglages : défauts (aucun document) »** sinon. Les bras d'expérience **ne sont pas affichés** à l'utilisateur (biais), mais figurent dans le diagnostic exportable (`RemoteDiagnostics.report`, `C/remote/RemoteRoutes.kt:61-75`) pour l'assistance.
5. **Grandfathering** : un droit émis sous les anciens réglages **reste intégralement** ; un bouquet dont `rentalDays` change garde ses contrats en cours ; les jetons livrés gardent leur valeur en commodités (prix en jetons **au moment de la dépense**, confirmés à chaque fois, W5 § 6.1).
6. **Transparence** : « À propos > Politiques appliquées » (ORDRES § 11, **non implémenté**) liste aussi les documents de réglages (seq, date, résumé « 3 valeurs modifiées ») ; texte d'information : **reporté au 2026-12-31** avec le juridique ; d'ici là, la ligne de diagnostic suffit (aucune donnée personnelle n'est en jeu).

## 6. Risques et calendrier pilote

### 6.1 Risques
| Risque | Mesure | Résiduel |
|---|---|---|
| **Confusion de prix** (TV et téléphone montrent deux prix) | la TV fait foi ; téléphone lit `GET /api/settings` ; grille `variant` du bras ; prix engagé au devis ; message de changement | téléphone hors ligne avec vieux cache : « prix à confirmer à la connexion » |
| **TV incohérentes** (parc à plusieurs seq) | parc par seq (`settings_applied`), le téléphone pousse à chaque liaison, clé USB des agents, `settings.message` | TV jamais rejointe : défauts compilés ou dernier document (dit « périmé ») |
| **Rejeu d'anciens réglages** | `seq` strictement croissant par clé, fenêtre `expiresAt`, règle « plus récent par `issuedAt` » entre clés | horloge TV très en retard : `TvClock` (plancher des activations) |
| **Altération** (téléphone, USB, serveur compromis) | signature Ed25519 ; fail-closed ; bornes ; **aucune valeur d'argent** dans le document (choix de grille seulement) ; liste `NEVER` ; cible `any` | serveur compromis : peut choisir une **grille existante** et des durées **dans les bornes** ; détecté par l'audit chaîné et le parc ; révocation de la clé serveur par la clé de secours (`REVOKE`) |
| **Abus par un agent** | les agents ne portent pas `POLICY` (`DESIGN-W4-VENTE-TERRAIN.md:38`) ; aucune cible par appareil ; le bras est **revalidé** par le serveur ; journal chaîné des ventes (`grid=`, `variant=`) | agent qui vend « au vieux prix » : anomalie `price ≠ grille` existante (W4-C:114) |
| **Expérience biaisée** | sel figé, bras affichés nulle part, garde-fous, décision humaine, N honnête (séquentiel) | effet de nouveauté : lire les semaines 2+ |
| **Dérive des bornes** (nouvelle app élargit une borne) | bornes **dans l'app** ; schéma versionné ; test de parité ; l'audit Opus relit toute borne € ou 🔒 | |
| **Oubli d'expiration** | rappel console à J-14 ; émetteurs refusent au-delà de 30 j de péremption | |

### 6.2 Calendrier de la phase de teasing (semaines, à caler sur le pilote W10 § 10.6)
| Sem. | Quoi |
|---|---|
| S0 | décisions D-W12-1…8 ; lancement de la **première tranche** (w12-01, 03, 04, 06, 07, 08 ; ≈ 11 j, 2 exécutants en parallèle ⇒ ≈ 1,5-2 semaines calendaires) ; schéma figé (10 clés) |
| S1-S2 | serveur en préproduction (clé de test), publication **seq 1 = défauts compilés** (aucun changement de comportement : mesure du parc par seq) ; TV de référence (GaiaOS 32 bits) : USB + Wi-Fi ; tél. : fetch, relais, diagnostic |
| S2 | w12-02, 05, 09, 10 (expériences, miroirs, Bluetooth, KPI) ; w12-11/12 (docs, campagne) |
| S3 | **seq 2** : premier réglage réel (ex. `trial.lotsWindowMinutes` 720 → 480 ou `trial.defaultDays` 30 → 14 : **décision**) + `settings.message` ; mesure 2 semaines ; A/B par point focal sur `price.variant` **seulement si** la grille W4-C est fusionnée, sinon test séquentiel papier |
| S5 | **seq 3** : seconde valeur ; comparaison S3-S4 vs S5-S6 ; `/admin/kpi/experiments` |
| S7 | bilan (avec W10 S7) : valeurs retenues ⇒ **seq 4 (consolidation)** ; les valeurs retenues deviennent les **défauts compilés** de la version suivante (version.properties non concerné) ; rapport `docs/coordination/BILAN-TEASING-REGLAGES.md` |

## 7. Première tranche (ce qu'on construit d'abord)

**Objectif** : réglages signés **en lecture seule**, **10 clés**, consommés par le téléphone, la TV et les émetteurs, publiés par la console web. Sans W4-W6.

| Clé | Consommateur dans la tranche | Effet visible |
|---|---|---|
| `trial.defaultDays` | console (`OL/ConsoleActivity.kt:183-198`), bureau (`DK/Cli.kt` `emettre`), `OwnerCli`, serveur (`ActivationService.issue`) | durée proposée par défaut |
| `trial.lotsWindowDays`, `trial.lotsWindowMinutes` | mêmes émetteurs ; TV : texte du badge (`KeyBadge.kt:47`) | fenêtre d'essai des clés futures ; badge exact |
| `rental.defaultDays` | bureau, console (`RentalDurations.kt:14`) | durée des émissions hors ligne |
| `lots.tvBudgetMb` | TV `LotsHub` (`R/LotsHub.kt:31`, `maxBytes`) | budget des lots |
| `langues.phoneQuotaMb` | téléphone (`LangBudget`) ; défaut compilé **500** | quota Langues |
| `pairing.blockMin` | TV `PairingSession.blockMs` (`:19`) | blocage après refus (plancher 5) |
| `telemetry.flushMin` | TV, tél. `TelemetryUploader` | cadence d'envoi |
| `price.variant` | **lu et affiché** seulement (grille non fusionnée) | prépare W4-C/W5 |
| `settings.message` | tél., TV | message d'accompagnement |

(`pairing.autoWindowMin` et `pairing.knockWindowMin` sont **déclarés** dans le schéma dès la tranche 1 mais sans consommateur tant que w7-06/w7-14 ne sont pas fusionnés.)

**Cahiers** : w12-01 (cœur + vecteurs), w12-03 (serveur API + migration), w12-04 (page admin), w12-06 (téléphone), w12-07 (TV), w12-08 (émetteurs). **Porte de la tranche** : vecteurs verts en Kotlin/Java ; sur la TV de référence : fichier USB `settings` accepté (badge et diagnostic changent), jeton altéré refusé, ancien seq refusé, fichier absent = défauts ; console : publier, diff, retour arrière, audit vert.

## 8. Interaction avec les vagues W4-W11 (cahiers qui codent en dur des valeurs devant lire `Settings`)

À lire comme un **amendement** (les cahiers ne sont pas édités) : « là où le cahier code la valeur ci-dessous, lire `Settings.int(<clé>)` avec cette valeur comme défaut compilé ; si `castbridge.core.settings` n'est pas fusionné, garder la constante **dans un seul endroit nommé** pour que w12 la remplace en une ligne ».

| Cahier | Ligne(s) | Valeur codée | Clé `settings` |
|---|---|---|---|
| `sonnet-w4-11-…` | :23 | `maxRentalDays 0..60`, validité ≤ 180 j | `rental.maxOnlineDays`, `delegation.maxDays` |
| `sonnet-w4-12-…` | :23 | délégation 30/90/180, clés 30/90/365 | `delegation.defaultDays`, durées de la grille (G) |
| `sonnet-w4-08-…` | :15, :24 | rafraîchissement 15 min | `reduced.tickMin` |
| `sonnet-w5-01-…` | :25 | `expiryDays` 0, `offlineGrantMax` 60, `kidDailyDefault` 0, `welcome` 10, 5/2/3 | `tokens.*` (**sortent de la grille**, D-W12-3) |
| `sonnet-w5-04-…` | :16, :24 | 60 j, `quizTasterPerDay = 3` | `rental.maxOnlineDays`, `quiz.tasterPerDay` |
| `sonnet-w5-06-…` | :11, :28-29 | ≤ 60 j, 3 j / 720 min, 3 contrats, réémissions ≤ 3 | `rental.maxOnlineDays`, `trial.lotsWindow*`, `rental.maxConcurrent`, `rental.freeReissues` |
| `sonnet-w5-07-…` | :21-22 | code 90 j, 5 essais/h, verrou 1 h | **restent fixes** (🔒) ; `shop.orderTtlHours` seulement (:196 de la conception) |
| `sonnet-w5-08-…` | :22 | `offlineGrantMax` | `tokens.offlineGrantMax` |
| `sonnet-w5-11-…`, `sonnet-w5-12-…` | :21-23 ; :11, :27 | « 30 jours », 72 h, 100 Mo | `rental.defaultDays` (affichage), `shop.orderTtlHours`, `LotBudget.PHONE_MAX_BYTES` (reste constante) |
| `sonnet-w5-14-…` | :24 | `sign_prices.py` avec réglages 0/60/0/10/5/2/3 | la grille ne porte plus de `set=` : **retirer** ces lignes de `sign_prices.py` |
| `sonnet-w5-17-…`, `sonnet-w5-18-…` | :24, :36 ; :11, :21 | 5 jetons, 3 parties, `kidDailyTokens = 0` | `tokens.secondChance`, `quiz.tasterPerDay`, `tokens.kidDailyDefault` |
| `sonnet-w6-02-…`, `sonnet-w6-03-…` | :22 ; :11, :24 | 14 j, 7 j | `proof.validityDays`, `proof.staleDays` |
| `sonnet-w6-01-…`, `sonnet-w6-11-…` | :11 ; :11, :21 | `graceDays = 14`, `phone.lock.graceDays=14` | **invariant** (J6) : reste compilé |
| `sonnet-w10-02-…` | :24 | parts 60 / 15, seuil 5 000 | `works.share*`, `works.minPayoutXaf` (**sortent de la grille**) |
| `sonnet-w10-08-…`, `sonnet-w10-11-…` | :11, :33 ; :11 | 200 Mo, 300 Mo | `works.tvBudgetMb`, `works.phoneBudgetMb` |
| `sonnet-w10-12-…` | :11, :24 | 12 mois | `langues.packUpdateMonths` |
| `sonnet-w10-14-…` | :23 | `releve.py --parts 60,15 --seuil 5000` | lit le document de réglages (`tools/settings/read_settings.py`) |
| `sonnet-w11-10-…` | :38 | 10 min | `ui.idleDimMin` |
| `sonnet-w7-06-…`, `sonnet-w7-14-…` | :6, :43, :45 ; :6, :19, :22 | 10 min / 2 min | `pairing.autoWindowMin`, `pairing.knockWindowMin` |
| `sonnet-w11-02-…` | :29, :78, :88, :94 | « 12 h » | texte dérivé de `trial.lotsWindowMinutes` (via `KeyBadge`) |

**Fichiers partagés** (ordre à respecter ou fusion entre les deux) : `C/telemetry/Telemetry.kt` (w12-02 **après** w11-04/w11-14/w10-07) ; `R/TvService.kt`, `R/ActivationCenter.kt`, `R/PlayerActivity.kt` (w12-07 **après** w11-10/11/12 et w10-08/09 s'ils sont lancés ; w3-02, w4-08, w6-12 touchent `ActivationCenter`) ; `R/OwnerBtHost.kt`, `C/owner/OwnerChannel.kt`, `R/PolicyHub.kt` (w12-09 **après** w2-01, w4-15, w6-12) ; `OL/ConsoleActivity.kt`, `DK/*` (w12-08 **après** w4-02, w4-12, w5-14) ; `C/owner/TrialPolicy.kt`, `tools/routes/routes.txt` (w12-07 **après** w5-04, w4-07, w10-08) ; `B/telemetry/EventCatalog.java` (w12-10 **après** w5-16, w10-07) ; `TPL/lic-nav.html` (w12-04 : un lien, après w5-09/w10-06).

## 9. Décisions prises par l'architecte (renversables) et questions au propriétaire

**Prises ici** : enveloppe `cbx1` type `settings` ; portée `POLICY` ; cible `any` seulement ; `seq` propre aux réglages ; validité 90 j (1 h–366 j) ; instantané complet, idempotent ; clé inconnue ignorée, hors bornes ignorée et journalisée, signature fausse = rien ; expiration = valeurs gardées + expériences arrêtées + émetteurs prudents ; interrupteur `reset=all` ; prix hors du document (grille `variant`) ; `tokens.*`/`works.*` hors de la grille ; cohorte par licence puis agent puis local, **la TV fait foi** ; aucune cible par appareil ; module serveur éteint par défaut ; aucun affichage des bras ; `settings_applied` essentiel.

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W12-1** | Qui signe les réglages : clé serveur `POLICY` via `/admin/settings` (TOTP), clé maîtresse du bureau en secours ; **jamais** la console téléphone ni un agent ? | w12-01/03/08 | **oui** (le téléphone propriétaire ne porte pas `POLICY`, OWNER-CONSOLE.md:102) |
| **D-W12-2** | Les prix restent dans la grille signée hors ligne ; le document ne fait que **choisir une grille** (`variant`) ? | w12-01, W4-C/W5 | **oui** (une compromission du serveur ne crée pas de prix) |
| **D-W12-3** | `tokens.*` et `works.*` **sortent** de la grille (W5:73, W10:77) et vont dans le document ? | w5-01, w5-14, w10-02 | **oui** (une seule source pour les réglages, la grille ne porte que des prix) |
| **D-W12-4** | Grâce (`lock.graceDays`), 48 h, listes blanches : **jamais** réglables à distance ? | — | **oui** (invariants § 1.3) |
| **D-W12-5** | Méthode d'expérience au pilote : séquentiel + par point focal ; A/B par licence seulement à ≥ 200 foyers ? | w12-02, calendrier | **oui** |
| **D-W12-6** | Les 10 clés de la première tranche (§ 7) ; défaut compilé Langues = **500 Mo** (code : 2048) ? | w12-01 | **oui**, 500 |
| **D-W12-7** | À l'expiration d'un document : valeurs gardées + expériences arrêtées + émetteurs prudents (plutôt que retour brutal aux défauts) ? | w12-01 | **oui** |
| **D-W12-8** | Première valeur à tester en S3 : `trial.defaultDays` 30 → 14 **ou** `trial.lotsWindowMinutes` 720 → 480 **ou** rien (seq 1 = défauts, mesure seule) ? | calendrier | **seq 1 = défauts** en S1, puis **`trial.defaultDays` 14 j** en S3 (conversion mesurable sans télémétrie : ventes) |

## 10. Effort et ancrages

| Sous-vague | Cahiers | Effort |
|---|---|---|
| 12a cœur (schéma, vérificateur, moteur, vecteurs ; expériences, cohortes) | w12-01, w12-02 | ≈ 5 j |
| 12b serveur (API, migration, signature, audit ; page admin ; miroir Python + signeur de secours) | w12-03, w12-04, w12-05 | ≈ 6,5 j |
| 12c applications (téléphone ; TV ; émetteurs ; Bluetooth/PolicyHub) | w12-06, w12-07, w12-08, w12-09 | ≈ 7,5 j |
| 12d mesure et docs (KPI expériences ; docs ; campagne/CI) | w12-10, w12-11, w12-12 | ≈ 3 j |
| **Total** | 12 | **≈ 22 j** ; première tranche ≈ 11 j |

**Ancrages vérifiés pour les exécutants** : enveloppe `C/owner/Envelope.kt:5-60` ; vérificateur d'ordres `C/owner/Order.kt:54-77` ; moteur `C/policy/PolicyEngine.kt:32-115` (`receive` :71, `process` :85, persistance :132) ; état `C/policy/PolicyState.kt:14-27` ; bornes `C/policy/PolicyActions.kt:30-32` ; `SeqState`, `TvClock` `C/owner/Keys.kt:84-88` ; transport `C/policy/OrderTransport.kt:22-33`, trames `C/policy/OrderFrames.kt:7-40` ; téléphone `S/OrdersRuntime.kt:25,38`, `S/PhoneConnect.kt:44` ; TV `R/PolicyHub.kt:19-34` (non branché), `C/owner/OwnerChannel.kt:21-38` (trames 5 et 8 seulement), `R/OwnerBtHost.kt:29-42` ; veilleur USB `R/ActivationCenter.kt:145,159-167` ; routes `C/tv/ReceiverServer.kt:330,366,523-541`, `tools/routes/routes.txt`, `C/owner/TrialPolicy.kt:31-38` ; diagnostics `R/PlayerActivity.kt:611-634`, `S/ConnectScreens.kt:142-148`, `C/remote/RemoteRoutes.kt:61-75` ; serveur ordres `B/orders/{OrderService,OrderSigner,ConfiguredOrderSigner,OrderEnvelope,PolicyCatalog,AdminOrderController,AdminOrdersPage}.java`, `V60__deferred_orders.sql:6,72` ; licences `B/licenses/{EnvelopeIssuer,EnvelopeVerifier,ScopedActivationSigner,AuditLog,Totp}.java` ; télémétrie `B/telemetry/{EventCatalog.java:31-35,117-118,TelemetryService.java:120,240}`, `C/telemetry/Telemetry.kt` ; catalogues `C/lots/SignedBundleCatalog.kt:34-65`, `B/lots/BundleCatalogController.java:29-44` ; émetteurs `OL/ConsoleActivity.kt:116,183-198`, `OL/OwnerStore.kt:54,68-80`, `DK/Cli.kt:59,81-101`, `DK/Qr.kt:11-22`, `C/owner/OwnerCli.kt:44-60`, `C/lots/RentalDurations.kt:14,37-43` ; constantes `C/owner/Activation.kt:122-136`, `C/lots/RentalLines.kt:10-23,45`, `C/langues/LangBudget.kt:75-76`, `C/lots/LotApi.kt:30-33`, `C/trust/PairingSession.kt:19`, `C/owner/KeyBadge.kt:47-49` ; vecteurs `tools/activation/verify_vectors.py`, `tools/orders/actions.json`.

**Non vérifié / BLOQUÉ** : ligne exacte de la cadence dans `C/telemetry/TelemetryUploader.kt` ; emplacement de la fenêtre d'appairage 2 min dans `TvService`/`PairingSession` ; existence d'un lecteur QR dans l'app téléphone (repli : collage de texte, fichier) ; montants (D9-bis) ; capacité réelle du canal USB sur les TV d'autres marques (le veilleur lit `Download/` : STORAGE.md).
