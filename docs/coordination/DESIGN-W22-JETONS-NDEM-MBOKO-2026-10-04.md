# Conception W22 — Jetons virtuels NDEM et MBOKO : grand livre serveur à double entrée, attributions par édition, mises en ligne TV à TV, conversion, transferts, bons hors ligne

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Branche de référence `integration/agents` (HEAD `5696385e`). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `SP/` = `server-play/src/main/kotlin/castbridge/play/`, `B/` = `backend/src/main/java/castbridge/server/`. Les fichiers cités ont été lus le 2026-10-04 (liste § 12). Rien n'a été exécuté : aucun réseau, aucun appareil, aucun serveur. Les chiffres non mesurés sont marqués « estimé ».
>
> **Deux niveaux** (décision du propriétaire, « Je teste d'abord la faisabilité technique ») : **NIVEAU 1 = POC de faisabilité technique** (priorité absolue, coût minimal, démontrable vite) ; **NIVEAU 2 = produit complet**, conservé ici, marqué « après le test de faisabilité ». Chaque règle porte son niveau.

## Décisions du propriétaire (verbatim, 2026-10-04, dans l'ordre)

1. **Spécification** : « Afin d'animer et dynamiser l'intérêt du système, on va introduire un compte de jetons virtuels comme dans les jeux en ligne avec 2 types (MBOKO, et NDEM). Le NDEM servira aux parties en ligne sans enjeu. Le MBOKO servira aux parties en ligne avec enjeu. Un essai offrira 100 NDEM x nombre de mois. Une production 1000 NDEM x nombre de mois. L'illimité offrira un crédit automatique de 5000 NDEM à l'ouverture et 1000 NDEM/mois. En production exclusivement, on aura 10 MBOKO x nbre de mois, ou 50 MBOKO avec 10 MBOKO/mois. La plateforme propose 1000 NDEM pour un MBOKO en automatique, mais aussi des transferts libres entre compte TV. La plateforme rémunérera en MBOKO ou en NDEM en fonction des politiques futures. Les jetons serviront aussi pour les jeux d'échecs. Les jetons pourraient s'activer offline par voucher aussi ».
2. « Ignore le risque juridique jusqu'au 1/1/2027 » (transmise par le coordinateur) : section juridique réduite (§ 6) ; aucune contrainte motivée par le droit ; **MBOKO transférable** (transferts libres entre comptes TV) ; **mises MBOKO actives par défaut en production** ; seul un **interrupteur d'exploitation** (coupure en cas d'incident, actif par défaut) ; les règles d'**intégrité économique** et de sécurité sont conservées ; « aucun retrait en argent » reste une **règle de produit**.
3. « Je teste d'abord la faisabilité technique » : niveaux 1 et 2 (§ 9).
4. « Le joueur décide de convertir son jeton de NDEM à MBOKO librement » : conversion **à la demande du joueur**, 1 000 NDEM → 1 MBOKO, par multiples de 1 000, immédiate, atomique, idempotente, **jamais automatique ni périodique** ; « en automatique » = la plateforme applique le taux elle-même, sans intermédiaire.
5. « Je veux aussi MBOKO vers NDEM » : la conversion est **bidirectionnelle**, à la demande du joueur ; taux inverse non donné : **symétrie retenue par défaut** (1 MBOKO = 1 000 NDEM dans les deux sens, aucun arbitrage possible) ; un écart éventuel (frais de conversion de la plateforme) est un **paramètre de politique**, jamais figé dans le code (D-W22-14).

Contraintes permanentes reprises (non rediscutées) : seule une **TV activée connectée à Internet** joue en ligne ; les téléphones sont des joueurs locaux **relayés** par leur TV et ne parlent jamais au service de jeu (`DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md`, I-1…I-5) ; **compte = identité de la TV** (code d'appareil de l'activation `cbx1`, `HostRights.identity`) ; ≤ 8 téléphones par TV ; liaison de référence **EDGE 40 kbps** par la passerelle Bluetooth ; jetons **virtuels**, **aucun paiement** de jetons hors du prix de la licence, **aucun retrait**.

## 0. En vingt lignes

1. **Lecture** : deux monnaies **virtuelles**, portées par un compte **par identité de TV**. **NDEM** = monnaie des parties en ligne « sans enjeu » (cagnottes amicales, attribuée à toutes les éditions) ; **MBOKO** = monnaie des parties « avec enjeu » (attribuée **seulement en production**, mises MBOKO **réservées aux identités de production**). Les parties en ligne **sans aucune mise restent gratuites** (W20, « parties libres » = gratuites) : aucun jeton n'est exigé pour jouer.
2. **Attributions** (§ 1.2) : essai 100 NDEM/mois ; production à durée N mois : 1 000 NDEM/mois **et** 10 MBOKO/mois (total 10 × N) ; production **illimitée** (clé sans durée) : 5 000 NDEM + 50 MBOKO **à l'ouverture**, puis 1 000 NDEM + 10 MBOKO/mois. **Lecture retenue** de « 10 MBOKO × nbre de mois, ou 50 MBOKO avec 10 MBOKO/mois » : le « ou » distingue la **clé à durée** (10 × N) de la **clé illimitée** (50 + 10/mois), en miroir exact de la règle NDEM (D-W22-1). Versement **par tranche mensuelle** (une tranche par période de 30 jours commencée pendant la validité), pas d'avance de N mois (D-W22-2).
3. **« Nombre de mois »** = `ceil(jours de la clé / 30)` ; **les locations (heures, jours, pilote de 3 semaines W16) ne sont pas des éditions** : elles n'attribuent rien. Le serveur lit l'édition **dans l'activation `cbx1`** que la TV présente (l'API ne connaît pas le cœur des licences, module éteint : `B/play/PlayTicketService.java:24-25`, `application.yml:117`).
4. **Conversion bidirectionnelle** : à la demande, sur la TV (écran « Convertir mes jetons »), NDEM → MBOKO (1 000 pour 1, multiples de 1 000) **et** MBOKO → NDEM (1 pour 1 000 par défaut, frais de plateforme 0 % au lancement, paramètre de politique) ; une transaction atomique du grand livre par conversion, clé d'idempotence ; l'**étiquette d'origine** (poche BONUS, niveau 2) **suit** la conversion dans les deux sens : l'aller-retour ne « blanchit » rien.
5. **Transferts** : libres entre comptes TV, **NDEM et MBOKO** ; la TV qui reçoit affiche un **code de réception** court et éphémère (`R` + 8 caractères, 10 min, usage unique) ; **aucun annuaire public**.
6. **Architecture** : **grand livre à double entrée tenu par l'API principale** (`castbridge-api`, MySQL `castbridge-db`) : comptes par identité et par monnaie (disponible, bloqué), écritures **immuables** avec clé d'idempotence, soldes **dérivés** des écritures (plus une table de soldes tenue dans la même transaction, vérifiée), aucune balance négative d'un compte de joueur, comptes système pour la création et la destruction.
7. **La TV ne crée jamais de jeton** : elle n'a qu'un **cache** signé par l'API (`cbw1`, instantané de solde) pour l'affichage hors ligne ; toute dépense passe par l'API.
8. **Service de jeu isolé** : `castbridge-play` ne reçoit **aucun** identifiant du grand livre ni accès réseau nouveau. Recommandation : **blocage par jeton signé + résultat signé** : la TV obtient de l'API un **bon de blocage `cbe1`** (mise bloquée, signée par la clé « portefeuille » de l'API) et le joint à `create`/`join` ; le service vérifie la signature (clé **publique**), arbitre, puis signe le **résultat `cbr1`** avec **sa propre clé dédiée** ; n'importe qui (la TV gagnante, ou le collecteur d'hôte toutes les 5 min) poste `cbr1` à l'API, qui vérifie et règle en une transaction. Un service compromis ne peut que **redistribuer les mises déjà bloquées** de la partie, jamais créer.
9. **Bons hors ligne** (`cbv1`, Ed25519, clé **hors ligne du propriétaire**, nonce, monnaie, montant, échéance, cible « toute TV » ou une TV) : la TV les vérifie et les **active sans Internet** (crédit « en attente », visible) ; le serveur les **confirme** à la reconnexion. Comme **toute dépense exige l'API** (les jetons ne servent qu'en ligne), un bon montré à deux TV ou rejoué après une restauration **ne crée aucune perte** : le second est refusé à la synchronisation (reste un désagrément d'affichage). MBOKO hors ligne : **oui, mais bons ciblés seulement**.
10. **Économie** (§ 5) : comme le compte suit l'**identité de l'activation**, une activation copiée **ne crée pas** de compte (même identité, même compte) : le Sybil exige de **vraies** clés distinctes, dont l'émission est contrôlée (propriétaire, agents à quotas ; un agent n'émet jamais de clé illimitée, `C/owner/DelegatedVerifier.kt:52`). Ordre de grandeur : **1 MBOKO = 10 mois d'essai** ; une production d'un mois vaut **11 MBOKO-équivalents**. Le risque n°1 n'est donc pas l'essai mais **l'émission frauduleuse de clés de production**, le **vol d'un compte** (activation copiée) et la **fuite d'une clé de signature**. Règles : tranches mensuelles, liaison du compte à l'appareil API, plafonds quotidiens calibrés au-dessus de l'usage honnête, NDEM d'essai « bonus » non transférable en sortie, surveillance de vélocité, gel et reprise par l'administrateur.
11. **Niveau 1 (POC de faisabilité)** : 8 cahiers, ≈ **15 $**, ≈ **12 agent·jours**, ≈ **7 jours ouvrés** en parallèle (estimé). Il prouve : grand livre et invariants (tests de propriétés), attributions par édition, solde sur la TV, partie TV à TV avec mise NDEM puis MBOKO réglée par résultat signé, conversion **dans les deux sens**, **un** transfert, **un** bon hors ligne. **Échecs avec mise : niveau 2** (aucune salle d'échecs en ligne n'existe côté serveur : `docs/CHESS.md` § 6, relais « à implémenter »).
12. **Niveau 2** : 6 cahiers (anti-abus complet, console de gel/reprise et réconciliation nocturne, bons au détail à code court, échecs en ligne avec mise, télémétrie et politique de rémunération, liaison forte du compte à la clé d'installation).
13. **Juridique** : reporté au 2027-01-01 (§ 6, dix lignes).

## 1. Règles du propriétaire, restituées précisément (niveau 1 sauf mention)

### 1.1 Les deux monnaies

| | NDEM | MBOKO |
|---|---|---|
| Usage | mises des parties en ligne **« sans enjeu »** (cagnotte NDEM partagée au classement) | mises des parties en ligne **« avec enjeu »** (cagnotte MBOKO) |
| Qui peut miser | toute TV activée en ligne (essai, production, grâce, illimitée) | **identités de production seulement** (production, grâce, illimitée) ; l'essai : jamais |
| Attribuée à | toutes les éditions | **production exclusivement** (« En production exclusivement ») |
| Unité | entier, ≥ 0 | entier, ≥ 0 |
| Conversion | NDEM → MBOKO à 1 000 pour 1, **à la demande** | MBOKO → NDEM à 1 pour 1 000 (frais 0 % par défaut), **à la demande** |
| Transfert entre TV | **libre** (sous plafonds d'intégrité, § 5) | **libre** (sous plafonds d'intégrité, § 5) |
| Partie en ligne **sans mise** | **gratuite**, aucun jeton requis (W20 A-1) | idem |
| Valeur, retrait | virtuel ; **aucun retrait**, aucun achat direct de jetons (règle de produit) | idem |

« Partie sans enjeu » est lu comme « partie dont la mise est en NDEM » (cagnotte amicale), et non comme « droit d'entrée en NDEM » : un droit d'entrée ferait payer une partie en ligne, contraire aux « parties libres » gratuites (D-W22-3).

### 1.2 Attributions par édition

L'édition est **lue dans l'activation `cbx1`** présentée par la TV (même lecture que le service de jeu : `SP/entitlement/HostRights.kt`, `TvGate`) : `kind=TRIAL` ⇒ **ESSAI** ; `kind=PRODUCTION` avec droit `usage` ⇒ **PRODUCTION à durée** ; `kind=PRODUCTION` **sans** droit `usage` ⇒ **ILLIMITÉE** (`B/licenses/ActivationService.java:68-93`, « illimitée » = production sans durée ; un essai n'est jamais illimité) ; droit `super` (super administrateur) ⇒ **aucune attribution automatique** (TV du propriétaire ; dons manuels si voulu, D-W22-4). Le libellé « Illimité » d'un achat du bouquet `tout` (`C/owner/Activation.kt:244`) est un droit de **contenu**, pas une durée : il ne change rien ici.

| Édition (lue dans `cbx1`) | NDEM | MBOKO | Rythme | Total pour une clé de N mois |
|---|---|---|---|---|
| **ESSAI** (1..365 j, défaut 30) | 100 / mois | 0 | une tranche au début de chaque période de 30 j commencée pendant la validité | 100 × N |
| **PRODUCTION à durée** (1..3 660 j) | 1 000 / mois | 10 / mois | idem | 1 000 × N NDEM, **10 × N MBOKO** |
| **ILLIMITÉE** (production sans durée) | **5 000 à l'ouverture** puis 1 000 / mois | **50 à l'ouverture** puis 10 / mois | ouverture une fois par identité ; tranches mensuelles tant que la clé est valide | — |
| **GRÂCE** (production échue, dans la grâce) | 0 nouvelle tranche | 0 | — | — |
| Suspendue, révoquée, horloge douteuse, aucune clé | 0 | 0 | — | — |
| **Location** (heures, jours, défaut 30 j, pilote W16 du 12/10 au 01/11) | **0** : une location n'est pas une édition | 0 | — | — |

**« Nombre de mois »** : `N = ceil(jours / 30)` pour une clé à durée (essai de 7 jours ⇒ N = 1 ⇒ 100 NDEM ; production de 90 jours ⇒ N = 3 ; 365 jours ⇒ 13). **Tranches** : la période `k` (k = 0, 1, …) commence à `ancre + 30 j × k`, où `ancre` = début du droit `usage` (sinon `issuedAt`, sinon `notBefore`) de la **première** activation acceptée de l'identité. Une tranche est **due** si, à l'instant de début de sa période (horloge du **serveur**), une activation valide de l'identité couvrait cet instant ; son montant est celui de la **meilleure** édition valide à cet instant (ILLIMITÉE > PRODUCTION > ESSAI) : **jamais deux tranches pour la même période**, même avec plusieurs clés superposées (renouvellement anticipé, essai puis production). Une clé de N mois couvre l'intervalle semi-ouvert `[début, début + 30 N j)`, qui contient **exactement N** débuts de période d'un réseau de pas 30 j : le total « × nombre de mois » est tenu exactement quand l'ancre est le début de cette clé, et à ± 1 tranche près quand une clé antérieure a fixé l'ancre (arrondi assumé, D-W22-2). **Matérialisation paresseuse** : la TV n'est pas toujours en ligne ; à chaque contact (`POST /api/v1/wallet/sync`), l'API calcule toutes les tranches dues jusqu'à maintenant et les inscrit, chacune avec la clé d'idempotence `grant:<identité>:<monnaie>:p<k>` : **aucune tranche n'est perdue** par une TV restée hors ligne, **aucune n'est doublée** par deux synchronisations. Aucun ordonnanceur n'est nécessaire au niveau 1.

**« À l'ouverture »** (illimitée) : à la **première synchronisation** où l'API accepte une activation ILLIMITÉE pour cette identité, **une fois par identité, pour toujours** (clé `grant:<identité>:open-unlimited`) : réactiver, réinstaller ou recevoir une nouvelle clé illimitée ne redonne pas l'ouverture. Une identité passée d'essai à illimitée reçoit l'ouverture une fois ; ses tranches d'essai déjà versées restent acquises.

**Révocation, remboursement d'une licence** : les tranches **futures** cessent d'elles-mêmes (c'est l'intérêt des tranches) ; les tranches passées restent acquises sauf **fraude** (reprise par l'administrateur, niveau 2, § 5.4).

**Lecture alternative de la règle MBOKO** (si le propriétaire lit autrement, D-W22-1) : (B) **choix** offert à toute licence de production entre « 10 × N » et « 50 + 10/mois » ; (C) « 10 × N » versés **d'un coup** à l'émission. (C) est déconseillé : une clé de 3 660 jours vaudrait 1 220 MBOKO le premier jour, irrécupérables en cas de fraude ou de remboursement.

### 1.3 Conversion dans les deux sens (décisions 4 et 5)

| Point | Règle |
|---|---|
| Déclencheur | **action explicite du joueur** sur la TV (écran « Convertir mes jetons », choix du **sens**), confirmée à la télécommande ; **jamais** automatique, jamais périodique, jamais déclenchée par une mise à court |
| Taux | `rate` = 1 000 NDEM pour 1 MBOKO (NDEM → MBOKO) ; sens inverse : 1 MBOKO donne `rate × (1 − fee)` NDEM, **`fee` = 0 au lancement** (symétrie : un aller-retour rend exactement la mise de départ, aucun arbitrage). `rate` et `fee` vivent dans la **table de politique** du serveur (`wallet_policy` : `convert.rate` borné 1..1 000 000, `convert.reverseFeeBp` en points de base, borné 0..2 000 = 0..20 %), lus à chaque conversion et **affichés** tels quels ; jamais dans le code |
| Quantité | NDEM → MBOKO : multiples de `rate` NDEM (1, 2, 3… MBOKO), le reste reste en NDEM ; MBOKO → NDEM : nombre entier de MBOKO ; avec frais, le montant NDEM est arrondi **vers le bas** et l'écart va au compte système `SYS:FEE` (rapporté, jamais rendu) |
| Effet | **une** transaction par conversion, quel que soit le sens : dans chaque monnaie, débit d'un côté, crédit de l'autre par le compte système `SYS:CONVERT` (et `SYS:FEE` pour l'écart) ; clé d'idempotence fournie par la TV (`conv:<id>:<clé>`), le sens et la quantité font partie de l'empreinte du contenu (même clé, autre sens ⇒ `IDEM_CONFLICT`) |
| Étiquette d'origine (niveau 2) | la poche **BONUS** (NDEM d'essai ou promotionnel) **suit** la conversion : NDEM BONUS → MBOKO BONUS → NDEM BONUS ; chaque conversion consomme d'abord la poche BONUS de la monnaie source et crédite la poche **de même étiquette** de la monnaie cible ; l'aller-retour ne transforme donc jamais du bonus en jetons transférables |
| Conditions | TV en ligne (le grand livre est au serveur) ; solde suffisant dans la monnaie source ; plafonds d'intégrité du § 5.3 (niveau 2), **comptés dans les deux sens ensemble** |
| Qui peut détenir des MBOKO | **toute** identité : un essai peut en obtenir par conversion de son NDEM ou en **recevoir** par transfert, les voir et les reconvertir en NDEM ; **miser** des MBOKO : production seulement (règle du propriétaire). Justification : la règle « MBOKO en production exclusivement » porte sur l'**attribution** et l'**enjeu** ; interdire la simple détention bloquerait un cadeau d'une TV de production vers une TV d'essai sans rien protéger (un essai ne peut ni miser ni, au niveau 2, retransférer des MBOKO : R-E6), et l'essai qui passe en production retrouve ses MBOKO |

### 1.4 Transferts libres entre comptes TV

Monnaies : **NDEM et MBOKO**. Identification du destinataire : **code de réception** demandé par la TV destinataire (`R7K2-M9QX` : 8 caractères Crockford + 1 de contrôle, ≈ 40 bits, valable 10 min, usage unique, 3 actifs au plus par identité), lu à voix haute ou par message entre les deux foyers, tapé à la télécommande sur la TV qui envoie ; l'API répond par le nom de la TV destinataire **masqué** (« TV de K… · …4F2Q ») avant confirmation. **Pas d'annuaire, pas de recherche par nom.** Un transfert = **une** transaction (débit émetteur, crédit destinataire, même monnaie, même montant, clé d'idempotence). Plafonds d'intégrité : § 5.3.

### 1.5 Rémunération par la plateforme (« en fonction des politiques futures »)

Jamais codée en dur : une **table de règles** (`wallet_reward_rule`, niveau 2) : `id`, `evenement` (liste fermée : `ONLINE_GAME_FINISHED`, `ONLINE_GAME_WON`, `STREAK_7D`, `TOURNAMENT_RANK`, `ADMIN_CAMPAIGN`), `monnaie`, `montant`, `editions` permises, `plafond_jour_identite`, `plafond_jour_global`, `debut`, `fin`, `active` (**faux par défaut**). Chaque versement est une écriture `REWARD` avec clé `reward:<règle>:<identité>:<référence>`. Au niveau 1, seule existe l'écriture `ADMIN_GRANT` (don manuel motivé, journalisé).

### 1.6 Échecs

Les échecs d'aujourd'hui sont **locaux** (la TV héberge la salle, les téléphones jouent par elle : `C/chess/ChessRoom.kt`) ; le relais Internet (`ChessRelayClient`) n'a **aucun serveur** (`docs/CHESS.md` § 6). Comme **toutes** les places d'une TV partagent **un seul** compte, une mise entre deux téléphones d'une même TV n'a pas de sens. Les mises aux échecs exigent donc des **échecs en ligne TV à TV** dans `castbridge-play` (aux règles W20 : TV seulement, téléphones relayés) : **niveau 2** (cahier w22-12). Le mécanisme (bloquer → résultat signé → régler) est **le même** que pour le Quiz : rien à concevoir de plus.

### 1.7 Bons hors ligne

Voir § 4. Monnaies : NDEM (cible « toute TV » ou une TV) ; MBOKO (cible **une TV** seulement).

## 2. Vue d'ensemble

```
                  clé hors ligne du propriétaire (bons cbv1, lots importés)
                                   │
   ┌───────────────────────────────▼──────────────────────────────────────────┐
   │ castbridge-api (Spring, 512 Mo)  module « wallet » (B/wallet/**)          │
   │  ┌──────────────────────────────────────────────────────────────────┐    │
   │  │ GRAND LIVRE à double entrée (MySQL castbridge-db)                │    │
   │  │  comptes : <identité>:NDEM:DISPO / :NDEM:BLOQUE / :MBOKO:…        │    │
   │  │  système : SYS:GRANT, SYS:VOUCHER, SYS:REWARD, SYS:CONVERT,       │    │
   │  │            SYS:ADJUST (signés négatifs : sources/puits)           │    │
   │  │  écritures immuables, idempotence, soldes dérivés + vérifiés      │    │
   │  └──────────────────────────────────────────────────────────────────┘    │
   │  clé « portefeuille » (privée) : signe cbw1 (instantané) et cbe1 (blocage)│
   │  clé publique du service de jeu : vérifie cbr1 (résultat)                 │
   └──────▲──────────────────────▲─────────────────────────────▲──────────────┘
          │ HTTPS (jeton         │ cbr1 posté par la TV         │ cbr1 posté par le
          │ d'appareil + cbx1)   │ gagnante (voie rapide)       │ collecteur d'hôte (cron 5 min,
          │ sync, convertir,     │                              │ docker cp du volume play-state)
          │ transférer, bloquer  │                              │
   ┌──────┴──────────────┐       │        ┌─────────────────────┴───────────────────┐
   │ CastBridge-TV       │───────┘        │ castbridge-play (384 Mo, isolé)          │
   │  cache cbw1 (lecture)│  cbe1 + cbp1   │  vérifie cbe1 (clé PUBLIQUE de l'API)    │
   │  bons cbv1 en attente├──────────────►│  arbitre la partie (ServerRoom)          │
   │  /quiz local ◄─ tél. │◄──────────────┤  signe cbr1 (SA clé dédiée « résultat ») │
   └─────────────────────┘   cbr1 en fin  │  AUCUN identifiant du grand livre        │
                              de partie   └──────────────────────────────────────────┘
   Les téléphones lisent le solde chez LEUR TV (/api/wallet local) ; ils ne parlent ni à l'API portefeuille ni au service de jeu.
```

**Autorité** : l'API est la **seule** autorité comptable ; le service de jeu est l'autorité **d'arbitrage** (qui a gagné quoi, dans la limite des mises bloquées) ; la TV est une **vitrine** (cache signé, bons en attente) qui ne crée rien.

## 3. Architecture

### 3.1 Grand livre à double entrée (niveau 1)

**Comptes** : `compte = (titulaire, monnaie, poche)`. Titulaires : une **identité de TV** (`XXXX-XXXX-XXXX-XXXX`, code d'appareil de l'activation) ou un titulaire **système**. Poches d'un joueur : `DISPO` (disponible), `BLOQUE` (mises en cours) ; niveau 2 : `BONUS` (NDEM d'essai ou promotionnel, § 5.3). Comptes système (un par monnaie) : `SYS:GRANT` (attributions), `SYS:VOUCHER` (bons), `SYS:REWARD` (rémunérations), `SYS:CONVERT` (contrepartie des conversions dans les deux sens), `SYS:FEE` (frais de conversion inverse, 0 au lancement), `SYS:ADJUST` (corrections et reprises de l'administrateur), `SYS:POT` (cagnotte transitoire d'un règlement, toujours ramenée à 0 dans la même transaction).

**Transaction** = un ensemble d'écritures ; **pour chaque monnaie, la somme des montants signés d'une transaction est nulle**. Types (liste fermée) :

| Type | Écritures (monnaie) | Clé d'idempotence |
|---|---|---|
| `GRANT` | `SYS:GRANT −a` ; `id:DISPO +a` | `grant:<id>:<cur>:p<k>` ou `grant:<id>:open-unlimited` |
| `CONVERT` sens N→M (q MBOKO) | NDEM : `id:DISPO −rate·q`, `SYS:CONVERT +rate·q` ; MBOKO : `SYS:CONVERT −q`, `id:DISPO +q` | `conv:<id>:<clé TV>` |
| `CONVERT` sens M→N (q MBOKO) | MBOKO : `id:DISPO −q`, `SYS:CONVERT +q` ; NDEM : `SYS:CONVERT −rate·q`, `SYS:FEE +f`, `id:DISPO +(rate·q − f)` avec `f = ceil(rate·q·fee)` (0 au lancement) | `conv:<id>:<clé TV>` |
| `TRANSFER` | `src:DISPO −a` ; `dst:DISPO +a` | `xfer:<src>:<clé TV>` |
| `ESCROW_LOCK` | `id:DISPO −a` ; `id:BLOQUE +a` | `lock:<id>:<clé TV>` (= `eid`) |
| `SETTLE` | pour chaque blocage `e` de la partie : `e.id:BLOQUE −e.a` ; `SYS:POT +Σ` ; puis `SYS:POT −pay_e` / `e.id:DISPO +pay_e` (et le non-utilisé rendu) ; `SYS:POT` revient à 0 | `settle:<rid>` (et `UNIQUE(eid)` sur le règlement) |
| `ESCROW_REFUND` | `id:BLOQUE −a` ; `id:DISPO +a` | `refund:<eid>` |
| `VOUCHER` | `SYS:VOUCHER −a` ; `id:DISPO +a` | `vch:<nonce>` (**globale** : un bon ne sert qu'une fois, toutes identités confondues) |
| `REWARD` (niv. 2) | `SYS:REWARD −a` ; `id:DISPO +a` | `reward:<règle>:<id>:<réf>` |
| `ADJUST` / `CLAWBACK` (niv. 2) | `id:DISPO ∓a` ; `SYS:ADJUST ±a` (motif, administrateur, TOTP) | `adj:<n>` |

**Invariants** (tous testés en propriétés au niveau 1, sauf mention) :

| # | Invariant | Où il est tenu |
|---|---|---|
| I-1 | **Conservation** : pour chaque monnaie, Σ de tous les comptes = 0 (après toute suite d'opérations) | cœur pur + contrainte de transaction + réconciliation |
| I-2 | **Aucun solde négatif** d'un compte de joueur (`DISPO`, `BLOQUE`, `BONUS`) | cœur + `SELECT … FOR UPDATE` + `CHECK (balance >= 0)` |
| I-3 | **Masse en circulation** = −(SYS:GRANT + SYS:VOUCHER + SYS:REWARD + SYS:ADJUST + SYS:CONVERT + SYS:FEE) par monnaie ; **la conversion conserve la valeur au taux près, dans les deux sens** : solde de `SYS:CONVERT` en NDEM = −`rate` × solde de `SYS:CONVERT` en MBOKO (NDEM net détruit = `rate` × MBOKO net créé) ; `SYS:FEE` ≥ 0 et = 0 tant que `fee` = 0 ; un aller-retour N→M→N à `fee` = 0 laisse tous les comptes inchangés | cœur + réconciliation |
| I-4 | **Idempotence** : rejouer une opération (même clé) ne change rien et rend le même résultat ; même clé avec un contenu différent ⇒ refus `IDEM_CONFLICT` | `UNIQUE(idem_key)` + comparaison d'empreinte du contenu |
| I-5 | **Transfert et règlement conservent** : Σ payé = Σ mises utilisées ; aucun gain hors des blocages listés | cœur (`Settlement.check`) + vérification de `cbr1` |
| I-6 | **Un blocage est réglé ou rendu une seule fois** | `UNIQUE(eid)` dans `wallet_escrow` + état `OPEN → SETTLED|REFUNDED` |
| I-7 | **La TV ne crée rien** : aucune route ne crédite sur la parole de la TV ; seules sources : tranches calculées par le serveur depuis une activation vérifiée, bon signé par la clé hors ligne **et** présent dans un lot importé, résultat signé par le service de jeu (redistribution), don d'administrateur | revue Opus + test « aucune route » |
| I-8 | **Soldes dérivés** : la table `wallet_balance` (cache transactionnel) = Σ des écritures par compte | réconciliation (test au niveau 1, travail nocturne au niveau 2) |
| I-9 | **Une tranche par période** et **une ouverture par identité** | clés d'idempotence |

**Concurrence** : chaque opération verrouille les lignes `wallet_balance` concernées dans un **ordre fixe** (tri par identifiant de compte) pour éviter les interblocages ; transaction `REPEATABLE READ` ; aucune opération longue sous verrou.

### 3.2 Comment l'API apprend l'édition et l'identité (niveau 1)

À chaque `POST /api/v1/wallet/sync`, la TV envoie son **jeton d'appareil** (en-tête existant de `DeviceClient`) et ses activations `cbx1` (≤ 4, ≤ 8 Ko chacune, comme le service de jeu). L'API vérifie avec le **port Java existant** `B/licenses/EnvelopeVerifier.java` (pur, sans base) et un anneau de **clés publiques des émetteurs** (même contenu que `CASTBRIDGE_PLAY_TRUSTED_KEYS`, propriété `castbridge.wallet.trusted-keys`) ; refuse une activation d'une autre TV (code d'appareil ≠ celui des empreintes) ; extrait `kind`, droit `usage`, `super`, dates ; consulte une liste de révocations **si** elle est configurée (fichier signé, comme `CASTBRIDGE_PLAY_REVOCATIONS_FILE`) ; en déduit l'édition (§ 1.2) **avec l'horloge du serveur**. **Identité** = code d'appareil de l'activation. **Liaison à l'appareil API** (anti-vol, niveau 1 minimal) : à l'ouverture, le compte retient l'`deviceId` API ; toute opération qui **fait sortir** des jetons (transfert, blocage, conversion) depuis un autre `deviceId` est refusée (« Ce compte est lié à une autre TV ») ; réaffectation par l'administrateur ; niveau 2 : signature des opérations par la clé d'installation de la TV (w22-14). Cohérent avec le lien collant du ticket de jeu (`PlayTicketService`, `MAX_DEVICES_PER_CODE = 2`, audit B1).

**À vérifier par le cahier** : que `EnvelopeVerifier` et `WireActivation` s'instancient **sans** le module des licences (`castbridge.licenses.enabled=false`) ; sinon, copier le strict nécessaire dans `B/wallet/`.

### 3.3 Mises en ligne et service de jeu isolé (niveau 1)

**Options évaluées**

| Option | Principe | Pour | Contre | Verdict |
|---|---|---|---|---|
| A | `castbridge-play` appelle l'API pour bloquer et régler | simple à comprendre | donne au service un identifiant et un accès réseau vers l'API (contraire à son isolement, W21 § 0.7) ; un service compromis peut tout débiter | **écartée** |
| B | **blocage `cbe1` signé par l'API**, porté par la TV avec son ticket `cbp1` ; **résultat `cbr1` signé par le service** avec une clé dédiée, posté à l'API par la TV ou le collecteur | aucun secret du grand livre dans le service (il ne détient qu'une clé **publique** de l'API et **sa** clé de résultat) ; dommage d'un service compromis **borné aux mises bloquées** de ses parties ; rien de nouveau sur le réseau du service | une requête de plus par TV avant la partie (≈ 1-2 s en EDGE, une fois, préparée dans le salon) ; un résultat peut tarder (collecteur) | **RECOMMANDÉE** |
| C | le ticket `cbp1` porte la mise | une seule pièce | l'API émet `cbp1` sans connaître l'édition ni le solde au bon moment ; mélange attestation d'appareil et argent ; `cbp1` est consommé une fois par création ou entrée | écartée |

**Séquence (option B)**

```
TV A (hôte, production)          API (grand livre)                  castbridge-play                 TV B (invitée)
 │ « Créer · mise 20 MBOKO        │                                    │                               │
 │   · 2 joueurs ici misent »     │                                    │                               │
 │── POST /wallet/escrow ────────►│ DISPO −40 → BLOQUE +40 (lock:A:k1)  │                               │
 │◄── cbe1{eid1,A,MBOKO,20,2,exp}─│                                    │                               │
 │── create(cbp1, cbx1, stake={MBOKO,20}, escrow=cbe1) ───────────────►│ vérifie cbe1 (clé publique    │
 │                                │                                    │ API) : id = HostRights.identity│
 │                                │                                    │ MBOKO ⇒ édition PROD/GRÂCE     │
 │                                │◄──────── POST /wallet/escrow ───────────────────────────────────────│ (même chose,
 │                                │──────── cbe1{eid2,B,MBOKO,20,3} ──────────────────────────────────►│  3 joueurs)
 │                                │                                    │◄── join(cbp1,cbx1,escrow=cbe1)─│
 │   … Duel de 10 questions, téléphones relayés (W20) …                 │                               │
 │                                │                                    │ fin : Pot.split par siège,     │
 │                                │                                    │ agrégé par blocage ; signe cbr1│
 │◄────────────── cbr1 (message de fin) ───────────────────────────────┤──────────── cbr1 ────────────►│
 │── POST /wallet/settle(cbr1) ──►│ vérifie cbr1 (clé de résultat), eid1/eid2 OPEN, Σpay = Σutilisé    │
 │                                │ SETTLE en une transaction (settle:<rid>) ; non-utilisé rendu        │
 │                                │◄── cbr1 rejoué par TV B ou par le collecteur : idempotent ─────────│
```

**Règles** : (1) une mise est **par siège** (téléphone relayé ou télécommande), payée par le compte **de la TV** ; la TV déclare `k` sièges misant (1..8) et bloque `mise × k` ; au départ, le service retient `min(k, sièges présents de cette TV)` ; le non-utilisé est rendu au règlement. (2) Cagnotte partagée par **`Pot.split`** existant (`C/quiz/Wallet.kt:54-85` : 100 % ; 70/30 ; 60/30/10 ; ex æquo à parts égales ; 0 point = rien ; reste au meilleur), puis **agrégée par blocage** : la TV reçoit la somme des gains de ses sièges. (3) **Duel seulement** (règle existante de `QuizRoom.configure`). (4) **Monnaie unique par salle** ; mise par siège bornée (NDEM 1..1 000, MBOKO 1..100, configurables). (5) **Abandon** (hôte perdu ≥ 60 s, drain du service, salle fermée avant la fin) : `cbr1` de type `ABORT`, chaque blocage rendu en entier. (6) Une TV invitée perdue ≥ 60 s : ses sièges marquent 0 aux questions manquées, la partie continue (amendement § 2.7) ; sa mise reste en jeu. (7) **Blocage sans résultat** : rendu automatique à `exp + 6 h` (matérialisé à la synchronisation suivante de la TV ou par la réconciliation) ; un `cbr1` arrivé après le rendu est refusé et journalisé (`RESULT_AFTER_REFUND`). (8) Le service garde chaque `cbr1` dans `play-state/results/` (≤ 7 j) ; le **collecteur d'hôte** (même modèle que W21 w21-03b : cron, `docker cp`, `curl` vers `127.0.0.1:7090`) les poste toutes les 5 min : comme `cbr1` est **signé**, la route de règlement n'exige aucun secret (n'importe qui peut poster un résultat authentique), seulement un plafond de débit. (9) Interrupteurs d'exploitation : `castbridge.wallet.stakes.ndem` et `castbridge.wallet.stakes.mboko` côté API, `CASTBRIDGE_PLAY_STAKES` côté service, **actifs par défaut** ; coupés : refus propre `STAKES_SUSPENDED` (« Mises suspendues pour maintenance »), les parties sans mise continuent.

**Ce qu'un service de jeu compromis peut faire** : redistribuer les mises **déjà bloquées** des parties qu'il arbitre (borné par les plafonds de mise et le nombre de parties) ; il ne peut ni créer, ni débiter un compte qui n'a pas bloqué, ni régler deux fois. Rotation : `castbridge.wallet.play-result-pubkeys` accepte deux clés (comme `TICKET_PUBKEY_2`).

### 3.4 Formats signés (niveau 1)

Tous sur le modèle de `cbp1` (Java signe, Kotlin vérifie : `B/play/PlayTicketService.java`) : `<préfixe>.<b64url de la charge JSON>.<b64url de la signature Ed25519>`, signature sur `<domaine>\n<préfixe>.<charge b64url>` ; la charge est signée **telle qu'envoyée** (aucune recanonicalisation) ; JSON plat, clés courtes, entiers en ms.

| Format | Signé par | Vérifié par | Domaine | Charge | Taille (estimé) |
|---|---|---|---|---|---|
| `cbw1` instantané de solde | clé « portefeuille » de l'API (`kid`) | TV (clé publique compilée dans l'APK, liste comme `activation-trusted-keys.txt`) | `castbridge-wallet-snapshot-v1` | `kid, id, ed (TRIAL/PROD/UNLIMITED/NONE), n (NDEM dispo), nb (NDEM bloqué), m, mb, seq (dernière écriture), at, flags (frozen, stakesN, stakesM)` | ≈ 350 o |
| `cbe1` blocage | clé « portefeuille » de l'API | `castbridge-play` (clé publique, `CASTBRIDGE_PLAY_WALLET_PUBKEY`, `_2`) | `castbridge-wallet-escrow-v1` | `aud=castbridge-play, kid, eid, id, cur, per, k, amt (= per × k), iat, exp (≤ 30 min)` | ≈ 380 o |
| `cbr1` résultat | clé « résultat » **du service de jeu** (privée montée en secret dans le conteneur) | API (`castbridge.wallet.play-result-pubkeys`) | `castbridge-play-result-v1` | `kid, rid (128 bits), room, game, cur, per, kind (END/ABORT), at, lines=[[eid, id, used, pay]…]` | ≈ 250 o + 90 o par TV |
| `cbv1` bon | clé **hors ligne du propriétaire** (portée dédiée « bons portefeuille ») | TV (clé publique compilée) et API | `castbridge-wallet-voucher-v1` | binaire compact : version 1, index de clé 1, monnaie 1, montant u32, nonce 10 o, échéance u16 (jours depuis 2026-01-01), cible 8 o (0 = toute TV, sinon 8 premiers octets de SHA-256(code d'appareil)) + signature 64 o = **91 o** | 146 caractères Crockford ; QR ≈ version 6 |

**Clés** : « portefeuille » (API, nouvelle, fichier secret à côté de la clé de ticket), « résultat » (service de jeu, nouvelle, la **seule** clé privée nouvelle du service : ce n'est **pas** un secret du grand livre ; `NoSecretsTest` est étendu pour l'autoriser nommément et refuser toute autre), « bons » (hors ligne, propriétaire ; jamais sur le serveur). Vecteurs communs Kotlin ↔ Java : `tools/wallet/wallet-vectors.json` (`castbridge-wallet-vectors-v1`).

### 3.5 Cache de la TV (niveau 1)

`C/wallet/WalletCache.kt` (pur) : garde le **dernier** `cbw1` valide (signature, `id` = identité de cette TV, `seq` croissant : un instantané plus ancien est ignoré), plus la liste des **bons en attente** (§ 4). Affichage hors ligne : « 3 450 NDEM · 12 MBOKO · au 04/10 18:42 » ; jamais un solde calculé localement au-delà de l'instantané. Le cache **n'est pas une autorité** : effacé, il se reconstruit à la synchronisation suivante ; altéré, la signature échoue et il est ignoré. Rafraîchi à chaque synchronisation (ouverture du Quiz en ligne, retour de partie, toutes les 15 min si en ligne, après chaque opération : la réponse de l'API porte toujours un `cbw1` neuf). Les téléphones lisent `GET /api/wallet` **local** de la TV (même jeton local que `/quiz`).

### 3.6 Surface de l'API (module `B/wallet/**`, `castbridge.wallet.enabled`)

| Route | Niveau | Entrée | Sortie / effet |
|---|---|---|---|
| `POST /api/v1/wallet/sync` | 1 | jeton d'appareil ; activations `cbx1` ; bons en attente `cbv1` (≤ 10) | matérialise les tranches dues, confirme ou refuse chaque bon, rend les blocages échus ; rend `cbw1` + résultats des bons + 20 dernières lignes d'historique |
| `GET /api/v1/wallet/history?before=` | 1 | jeton d'appareil | lignes (type, montant, monnaie, date, libellé français, contrepartie masquée), 50 par page |
| `POST /api/v1/wallet/convert` | 1 | `{dir: "N2M"|"M2N", q (MBOKO), idem}` | transaction `CONVERT` ; `cbw1` ; la réponse porte le taux et les frais appliqués |
| `GET /api/v1/wallet/policy` | 1 | — | `rate`, `reverseFeeBp`, bornes de mise, interrupteurs (pour l'affichage) |
| `POST /api/v1/wallet/receive-code` | 1 | — | `{code, exp}` (10 min) |
| `GET /api/v1/wallet/receive-code/{code}` | 1 | — | destinataire masqué ; 10 consultations / h / identité |
| `POST /api/v1/wallet/transfer` | 1 | `{code, cur, amt, idem}` | transaction `TRANSFER` ; `cbw1` |
| `POST /api/v1/wallet/escrow` | 1 | `{cur, per, k, idem}` | `ESCROW_LOCK` ; `cbe1` |
| `POST /api/v1/wallet/settle` | 1 | `cbr1` (aucune authentification : signé) | `SETTLE` ou `ESCROW_REFUND` idempotents |
| `POST /admin/wallet/grant` | 1 | identité, monnaie, montant, motif (TOTP) | `ADMIN_GRANT` (= `ADJUST` positif) |
| `GET /admin/wallet/reconcile` | 1 | — | vérifie I-1, I-3, I-8 ; rapport |
| `POST /admin/wallet/freeze`, `/clawback`, `/rebind` | 2 | identité, motif (TOTP) | gel des sorties ; reprise ; réaffectation d'appareil |
| `POST /admin/wallet/vouchers/import` | 1 (minimal) / 2 (lots par revendeur) | manifeste de lot signé par le propriétaire | nonces connus |

Tous les refus portent un **motif fermé** et un **texte français** (§ 7.6). Débits : 30 opérations d'écriture / min / identité ; 600 / min global (estimé, réglable).

### 3.7 Données et migration (MySQL, niveau 1)

`V63__wallet.sql` (« plus haut + 1 » au moment de la fusion : V62 est pris par W21 ; `V62__shop` de W5 n'est pas fusionné ; le cahier vérifie). Toutes les tables sont **nouvelles** ; aucune table existante n'est modifiée (retour arrière = module éteint).

```
wallet_account   (id PK, holder VARCHAR(24) [identité ou SYS:…], cur ENUM('NDEM','MBOKO'), pocket ENUM('DISPO','BLOQUE','BONUS','SYS'),
                  UNIQUE(holder, cur, pocket))
wallet_balance   (account_id PK FK, balance BIGINT NOT NULL, version BIGINT, CHECK (pocket SYS or balance >= 0) -- tenu par le service)
wallet_txn       (id PK BIGINT, kind VARCHAR(16), idem_key VARCHAR(96) UNIQUE, content_sha CHAR(64), holder VARCHAR(24) NULL,
                  ref VARCHAR(64) NULL [eid, rid, nonce, code], created_at DATETIME(6), actor VARCHAR(32) [tv|play|admin:<nom>|system])
wallet_entry     (id PK BIGINT, txn_id FK, account_id FK, amount BIGINT [signé], INDEX(account_id, id))
wallet_identity  (holder PK, api_device_id BIGINT, anchor_at DATETIME(6), opened_unlimited BOOL, edition VARCHAR(10), last_sync_at,
                  frozen BOOL DEFAULT FALSE, frozen_reason VARCHAR(200) NULL)
wallet_escrow    (eid CHAR(22) PK, holder, cur, per INT, k INT, amount BIGINT, state ENUM('OPEN','SETTLED','REFUNDED'), exp_at, settled_rid NULL)
wallet_result    (rid CHAR(22) PK, sha CHAR(64), kind, received_at, outcome VARCHAR(24))
wallet_recv_code (code CHAR(9) PK, holder, exp_at, used_at NULL)
wallet_policy    (name VARCHAR(48) PK, value BIGINT, min_value BIGINT, max_value BIGINT, updated_at, updated_by)
                 -- convert.rate=1000, convert.reverseFeeBp=0, stake.maxPerSeat.NDEM=1000, stake.maxPerSeat.MBOKO=100, transfer.dailyCap.*,
                 -- switch.stakes.NDEM=1, switch.stakes.MBOKO=1, switch.transfer=1, switch.convert=1, switch.vouchers=1 (bornes vérifiées à l'écriture)
wallet_voucher_batch (batch_id PK, cur, count, issued_at, expires_at, manifest_sha, imported_at, revoked_at NULL)
wallet_voucher   (nonce CHAR(20) PK, batch_id FK, cur, amount, target CHAR(16) NULL, redeemed_by VARCHAR(24) NULL, redeemed_at NULL,
                  rejected_dupes INT DEFAULT 0)
```

**Budget** (estimé) : une écriture ≈ 60 o de données + index ≈ 150 o ; une partie misée ≈ 2 transactions par TV (blocage, règlement) ≈ 5 écritures par TV ≈ 1 Ko avec la transaction. Une TV active : 2-3 tranches + 60 parties misées / mois ≈ **65 Ko / mois**. 1 000 TV ⇒ ≈ 65 Mo / mois ; 10 000 TV ⇒ ≈ 650 Mo / mois (disque, pas mémoire). Niveau 1 (POC, ≤ 50 TV) : négligeable. Niveau 2 : **clôture mensuelle** (transaction de report par compte, écritures de plus de 13 mois archivées en CSV compressé hors base, `wallet_entry` partitionnée par mois) ; aucune incidence sur la mémoire de MySQL (pool 128 Mo, `backend/docker-compose.yml:18`) tant que les index tiennent.

**Mise en production** : tag `server-1.x` par `tools/release/deploy-server.sh` (`docs/RELEASES.md:399-410`), Flyway au démarrage, module allumé par `CASTBRIDGE_WALLET_ENABLED=1` à la livraison qui le porte. **Retour arrière** : éteindre le module (routes 404) ; les tables restent (additives) ; un script manuel `tools/wallet/rollback-V63.sql` (DROP) n'est permis **que** si aucune écriture réelle n'existe.

## 4. Bons hors ligne

### 4.1 Format et flux (niveau 1)

```
Propriétaire (hors ligne)                    Serveur                         Revendeur / client                 CastBridge-TV (sans Internet)
make_wallet_vouchers.py --cur NDEM            │                               │                                  │
 --amount 500 --count 100 --target any|<code>  │                               │                                  │
 ⇒ lot-<id>.manifest.signed (nonces)  ───────►│ /admin/wallet/vouchers/import │                                  │
 ⇒ lot-<id>-BONS-SECRET.txt (cbv1 + QR) ─────────────────────────────────────►│ carte avec QR + code long        │
                                               │                               │── QR scanné par CastBridge (tél.)│
                                               │                               │   ou fichier .cbv1 sur la clé USB│
                                               │                               │   ou code tapé (146 car.) ──────►│ vérifie : signature (clé
                                               │                               │                                  │ publique compilée), échéance
                                               │                               │                                  │ (TvClock), cible, nonce non vu
                                               │                               │                                  │ ⇒ « +500 NDEM activés hors ligne
                                               │                               │                                  │   · confirmés à la prochaine
                                               │◄──── POST /wallet/sync (bons en attente) à la reconnexion ───────│   connexion »
                                               │ signature + lot importé + nonce libre ⇒ VOUCHER (vch:<nonce>)     │
                                               │ nonce déjà utilisé ⇒ refus BON_DEJA_UTILISE ─────────────────────►│ ligne retirée, message
```

- **Pourquoi c'est sûr** : les jetons ne servent **qu'en ligne**, et toute dépense (blocage, transfert, conversion) passe par l'API, **après** la synchronisation des bons en attente (la TV synchronise avant toute opération). Un crédit « activé hors ligne » n'est donc **jamais dépensable** avant confirmation : le double emploi ne coûte rien à l'économie.
- **Bon montré à deux TV hors ligne** : les deux l'affichent « en attente » ; la première synchronisée l'obtient ; l'autre reçoit « Ce bon a déjà été utilisé sur une autre TV » (désagrément, pas de perte). Parade : bons **ciblés** (une TV) pour les montants importants, et **tous** les bons MBOKO ciblés.
- **Restauration d'une sauvegarde** après un rachat : la liste « en attente » revient ; à la synchronisation, le nonce est déjà rattaché à **la même** identité : ignoré sans message (idempotent).
- **Ensemble local des nonces vus** (`files/wallet/vouchers.txt`, ≤ 500 lignes, plus anciennes purgées après confirmation) : empêche de « réactiver » deux fois le même bon sur la même TV, pour la propreté de l'affichage seulement.
- **Plafonds hors ligne** (affichage) : ≤ 10 bons en attente et ≤ 20 000 NDEM / 200 MBOKO en attente par TV ; au-delà : « Connectez la TV pour confirmer vos bons ».
- **Clé hors ligne + lot importé** : un bon n'est crédité que si sa signature est valide **et** son nonce figure dans un lot importé non révoqué : la fuite de la seule clé ne suffit pas (il faut aussi l'accès d'administration), la compromission du seul serveur ne fabrique pas de bons (il n'a pas la clé).
- **Saisie** : le code long (146 caractères, groupes de 4 + 1 contrôle, même principe que la clé compacte de 165 caractères déjà tapée sur la TV, `C/owner/Activation.kt`, « Compact activation for manual typing ») est le **dernier recours** ; voies normales : QR lu par l'application CastBridge et poussé à la TV par la liaison locale existante (PIN / téléphone de confiance), ou fichier `.cbv1` dans `Download/castbridge-bons/` de la clé USB. **Non vérifié** : présence d'un lecteur de QR dans l'application CastBridge (sinon : partage de texte vers l'application, ou saisie).

### 4.2 Bons au détail à code court (niveau 2)

Code à gratter `NB-XXXX-XXXX-XXXX-XXXX` (16 Crockford : 2 de lot + 13 secrets ≈ 65 bits + 1 contrôle), **non vérifiable hors ligne** (un code court ne peut pas porter de signature, et un secret dans l'APK serait extrait) : hors ligne, la TV contrôle le format et le **met en attente** ; le serveur le reconnaît par **empreinte** (SHA-256 du code, jamais le code), modèle W5 § 3.4 d et § 5.3-5.4 (lots par revendeur, serial public, rapprochement des ventes, révocation de lot). Anti-force brute : 65 bits ; 5 essais faux / h / identité puis verrou 24 h ; 1 000 essais faux / h global ⇒ alerte ; probabilité de deviner un code parmi 10⁶ actifs en 10⁶ essais ≈ 10⁶ × 10⁶ / 2⁶⁵ ≈ 3 × 10⁻⁸.

## 5. Intégrité économique (cœur du risque)

### 5.1 Ce que vaut chaque compte (ordre de grandeur)

En « MBOKO-équivalent » (1 MBOKO = 1 000 NDEM par conversion) :

| Source | Par mois | À l'ouverture | Remarque |
|---|---|---|---|
| Essai | 0,1 | — | **1 MBOKO = 10 mois d'essai** (10 clés d'essai de 30 j) |
| Production à durée | 1 + 10 = **11** | — | 110 fois un essai |
| Illimitée | 11 | 5 + 50 = **55** | |
| Bon NDEM 500 | — | 0,5 | selon les lots émis |

### 5.2 Modèle de menaces

| # | Attaque | Mécanisme | Gain de l'attaquant | Ce qui la borne |
|---|---|---|---|---|
| S-1 | **Ferme d'essais** (Sybil) | N clés d'essai, transferts libres vers un compte, conversion | 0,1 MBOKO / clé / mois | les clés d'essai sont **émises** (propriétaire, agents à quotas `maxSales`) ; **copier une activation ne crée pas de compte** (identité = celle de l'activation) ; § 5.3 R-E4/R-E5 |
| S-2 | **Clés de production frauduleuses** (agent malhonnête, délégation volée) | clés de production réelles sans paiement | **11 MBOKO / clé / mois** | quotas et journal chaîné des agents (W4-C), « jamais de clé illimitée par un agent » (`DelegatedVerifier.kt:52`), tranches mensuelles (stoppées à la révocation), reprise (§ 5.4) |
| S-3 | **Vol de compte** (activation `cbx1` copiée, w20-04 B1) | l'activation donne l'identité | tout le solde, par transfert | liaison du compte à l'`deviceId` API (§ 3.2) ; niveau 2 : signature par la clé d'installation ; délai sur les gros transferts sortants |
| S-4 | **Collusion en partie** (chip dumping, soft-play) | perdre exprès au profit d'un complice | rien de plus qu'un transfert (les transferts sont libres) | n'a d'intérêt que pour **contourner les plafonds de transfert** : la même vélocité est surveillée sur les gains de partie entre deux mêmes identités (R-E9) |
| S-5 | **Revente de jetons contre de l'argent** hors plateforme | transferts libres | dépend du marché | non empêchable techniquement ; règle de produit (aucun retrait, aucun achat) ; vélocité et concentration surveillées (R-E9) ; gel (§ 5.4) |
| S-6 | **Blanchiment par cascade** de comptes | transferts en chaîne | — | plafonds par jour et par destinataire, âge minimal du compte émetteur (R-E6) |
| S-7 | **Fuite de la clé « résultat »** du service | résultats forgés | mises bloquées des parties en cours seulement | rotation à deux clés, plafond de mise, alerte sur les gains anormaux |
| S-8 | **Fuite de la clé « portefeuille »** de l'API | instantanés et blocages forgés | **aucun** jeton : un `cbe1` forgé ne correspond à aucun blocage au grand livre ⇒ règlement refusé ; un `cbw1` forgé ne trompe que l'affichage d'une TV | rotation ; le service vérifie aussi que l'API connaît le blocage ? (non : le service ne parle pas à l'API) ⇒ **l'API refuse de régler un `eid` inconnu** ; le dommage est une partie faussée, sans création |
| S-9 | **Fuite de la clé des bons** | bons forgés | aucun sans **lot importé** | double condition signature + lot ; révocation de lot |
| S-10 | **Promotion abusée** (rémunérations niveau 2) | boucles de gains | selon la règle | plafonds par règle et par identité, règles inactives par défaut |
| S-11 | **Rejeu / double règlement** | reposter `cbr1`, rejouer une conversion | 0 | idempotence (I-4, I-6) |
| S-12 | **TV rapiécée** qui affiche plus | cache altéré | 0 (affichage) | cache signé, toute dépense à l'API |
| S-13 | **Aller-retour de conversion** (NDEM BONUS → MBOKO → NDEM « propre », puis transfert) | conversion bidirectionnelle | rendre transférable un NDEM d'essai | l'**étiquette BONUS suit la conversion** dans les deux sens (§ 1.3) ; plafonds de conversion comptés **dans les deux sens** (R-E5) ; aller-retour à `fee` = 0 : aucun gain ; avec `fee` > 0 : perte. MBOKO de production → NDEM transférable : **rien n'est contourné**, les MBOKO sont déjà transférables (décision 2) |
| S-14 | **Arbitrage de taux** | écart entre les deux sens | — | symétrie par défaut ; un `fee` ≥ 0 rend tout aller-retour perdant ; jamais un taux inverse **plus favorable** (borne `reverseFeeBp ≥ 0` vérifiée à l'écriture de la politique) |

### 5.3 Règles proposées (calibrées pour ne pas gêner le joueur honnête)

| # | Règle | Valeur proposée | Niveau | Gêne pour l'honnête |
|---|---|---|---|---|
| R-E1 | **Conservation, aucun négatif, idempotence, la TV ne crée rien** (I-1…I-9) | — | **1** | aucune |
| R-E2 | Tranches **mensuelles** (pas d'avance de N mois) | § 1.2 | **1** | aucune (le total est le même) |
| R-E3 | Compte lié à l'appareil API d'ouverture pour toute sortie de jetons | § 3.2 | **1** | changement de TV : réaffectation par l'administrateur |
| R-E4 | NDEM d'essai et promotionnel versé en poche **BONUS** : se mise et se convertit librement (l'étiquette suit la conversion, MBOKO BONUS ↔ NDEM BONUS), **ne se transfère pas en sortie** ; ses **gains** de partie vont en `DISPO` ; les mises consomment le BONUS d'abord | — | 2 | aucune pour jouer ; un essai ne peut pas « donner » ses 100 NDEM d'attribution |
| R-E5 | Conversion : **plafond quotidien par identité**, **les deux sens additionnés** (en MBOKO convertis) | 20 MBOKO / jour ; essai : 2 MBOKO / jour | 2 | un joueur honnête reçoit ≈ 1 000 NDEM + 10 MBOKO / mois : le plafond n'est jamais atteint |
| R-E6 | Transferts sortants : plafond par jour et par destinataire ; compte émetteur âgé d'au moins 72 h | NDEM 10 000 / jour, 5 000 / destinataire / jour ; MBOKO 100 / jour, 50 / destinataire / jour ; essai : 1 000 NDEM / jour, MBOKO 0 | 2 (niveau 1 : **un** plafond global simple, 10 000 NDEM / 100 MBOKO par jour) | rare ; au-delà : « Plafond du jour atteint, réessayez demain » |
| R-E7 | Mises MBOKO : identités PROD / GRÂCE / ILLIMITÉE seulement | — | **1** (règle du propriétaire) | aucune |
| R-E8 | Mise par siège bornée ; perte nette quotidienne par identité bornée | NDEM ≤ 1 000 / siège ; MBOKO ≤ 100 / siège ; perte nette MBOKO ≤ 500 / jour | 2 (niveau 1 : bornes de mise seulement) | aucune à l'usage normal |
| R-E9 | **Vélocité** : alertes (jamais de blocage automatique) : > 3 comptes donateurs vers un même compte en 24 h ; gains de partie > 80 % contre la même identité sur 7 j ; > 20 transferts / jour ; conversion au plafond 5 jours de suite | — | 2 | aucune (alerte pour l'administrateur) |
| R-E10 | Gel et reprise par l'administrateur (motif, TOTP, journal d'audit) | — | 2 | — |
| R-E11 | Plafond d'émission des bons par lot et par revendeur ; bons MBOKO toujours ciblés | lot ≤ 5 000 bons ; montant ≤ 5 000 NDEM ou ≤ 50 MBOKO par bon | 1 (ciblage MBOKO) / 2 (lots par revendeur) | — |
| R-E12 | Interrupteurs d'exploitation (mises NDEM, mises MBOKO, transferts, conversion, bons) **actifs par défaut** | — | **1** | — |

**Effet chiffré sur la ferme d'essais** (S-1), avec R-E4 et R-E5 : les 100 NDEM d'attribution d'un essai restent dans son compte ; pour les faire sortir, l'attaquant doit les **perdre en partie** contre son compte collecteur (S-4), 3 parties par jour au plus en essai (`PlayRules.TRIAL_GAMES_PER_DAY`), surveillé par R-E9. **Coût de 1 MBOKO** ≈ 10 clés d'essai-mois + ≈ 10 parties arrangées. Une clé d'essai coûte ce que coûte son obtention (prix d'agent, **non connu** ici) : la ferme cesse d'être rentable dès que `prix d'une clé d'essai × 10 > prix de revente d'un MBOKO`, ce qui est vrai pour tout prix d'essai non nul **tant que** la production rapporte 11 MBOKO / mois pour le prix d'une licence. **Recommandation** (D-W22-6) : R-E4 + R-E5 au niveau 2, rien de plus ; surveiller S-2 (émission de clés) qui vaut 110 fois plus.

### 5.4 Gel, reprise, audit (niveau 2)

Gel d'une identité : plus aucune sortie (transfert, blocage, conversion) ; entrées et affichage continuent ; la TV affiche « Compte en vérification : contactez votre point focal ». Reprise : `ADJUST` négatif motivé, **jamais** au-delà du solde disponible (pas de négatif : le reste est noté comme dette administrative, non exécutée automatiquement). Journal : chaque action d'administration est une transaction (`actor = admin:<nom>`), plus une ligne dans l'`AuditLog` chaîné existant. Réconciliation nocturne : I-1, I-3, I-8, blocages échus, résultats orphelins ; écart ⇒ alerte et interrupteurs coupés automatiquement **pour les transferts seulement** (les parties continuent).

## 6. Juridique

Reporté au **2027-01-01** par décision du propriétaire : **revue juridique à faire avant cette date**. Points à revoir, pour mémoire seulement : (1) qualification des parties avec mise MBOKO (jeu, concours, loterie) au Cameroun et dans la CEMAC ; (2) jetons attribués avec une licence payante et transférables : risque de qualification monétaire ou de moyen de paiement ; (3) rémunérations de la plateforme ; (4) mineurs (profils enfant déjà bloqués pour Internet : `PlayRules.internetForProfile`) ; (5) identification et lutte contre le blanchiment si un marché de revente apparaît ; (6) protection du consommateur (information, conditions) ; (7) bons vendus au détail ; (8) données (registre des traitements du grand livre). Aucune de ces questions ne contraint la conception d'ici là.

## 7. Expérience (TV et téléphone, textes français)

### 7.1 Carte portefeuille (accueil TV et salon en ligne)

```
┌──────────────────────────────────────────────┐
│ ◎ Jetons   3 450 NDEM · 12 MBOKO             │   compacte, comme « 7/8 téléphones »
│            au 04/10 18:42 · +500 en attente   │   (« en attente » = bons hors ligne)
└──────────────────────────────────────────────┘
```
OK ouvre l'écran « Mes jetons » : soldes en grand (chiffres **et** lettres pour les confirmations, comme W5), 4 actions (← →) : **Convertir**, **Transférer**, **Recevoir**, **Saisir un bon** ; puis **Historique** (↓). Hors ligne : soldes de l'instantané + « Hors ligne : soldes au 04/10 18:42 » ; actions grisées avec la raison, sauf « Saisir un bon ».

### 7.2 Choix de la mise (Créer une partie Internet)

`Mise : Aucune · NDEM · MBOKO` (← →) ; puis `Par joueur : 10 · 20 · 50 · 100` ; puis `Joueurs ici qui misent : 2` (1..8, défaut = téléphones présents) ; récapitulatif « Mise bloquée : 40 MBOKO (quarante) · Solde après : 8 MBOKO » ; OK = confirmer. Rejoindre une salle misée : « Cette partie se joue avec une mise de 20 MBOKO par joueur » ⇒ même choix du nombre de joueurs. Essai : MBOKO grisé « Mises MBOKO : version complète ». Bandeau de partie : le signe « Partie sûre » (inchangé) **et** « Cagnotte : 100 MBOKO ». Fin : « Vous gagnez 54 MBOKO » ou « Partie interrompue : mises rendues ».

### 7.3 Convertir mes jetons (les deux sens)

```
Convertir mes jetons
Sens :     [ NDEM → MBOKO ]   MBOKO → NDEM          (← → change le sens)
Taux :     1 000 NDEM = 1 MBOKO
Vous avez : 3 450 NDEM · 12 MBOKO
Convertir :   ◄  3 MBOKO  ►        (↑ ↓ ou ← → sur la quantité ; multiples de 1 000 NDEM)
Après :       450 NDEM · 15 MBOKO
[ OK : Convertir 3 000 NDEM en 3 MBOKO ]   [ Retour ]
```
```
Sens :       NDEM → MBOKO   [ MBOKO → NDEM ]
Taux :       1 MBOKO = 1 000 NDEM            (avec frais : « 1 MBOKO = 980 NDEM · frais 2 % »)
Convertir :  ◄  5 MBOKO  ►
Après :      8 450 NDEM · 7 MBOKO
[ OK : Convertir 5 MBOKO en 5 000 NDEM (cinq mille) ]
```
Après confirmation : « Conversion faite : 3 MBOKO ajoutés » / « 5 000 NDEM ajoutés ». Sens N→M avec moins de 1 000 NDEM : « Il faut au moins 1 000 NDEM pour 1 MBOKO » ; sens M→N sans MBOKO : « Aucun MBOKO à convertir ». Au niveau 2, une ligne discrète précise la part bonus (« dont 1 000 NDEM bonus : restent bonus après conversion »). La conversion ne se fait **jamais** sans ce geste.

### 7.4 Transférer / Recevoir

**Recevoir** (TV destinataire) : code en très grand `R7K2-M9QX`, compte à rebours « valable 9:41 », « Donnez ce code à la personne qui vous envoie des jetons ». **Transférer** (TV émettrice) : saisie du code au D-pad (groupes de 4, contrôle local) ⇒ « Vers : TV de K… · …4F2Q » ⇒ monnaie, montant ⇒ « Envoyer 2 000 NDEM (deux mille) à TV de K… ? » ⇒ OK ⇒ « Envoyé ». Destinataire : notification à sa prochaine synchronisation « Vous avez reçu 2 000 NDEM ».

### 7.5 Saisir un bon

Trois entrées : « Depuis le téléphone » (l'application pousse le bon), « Depuis la clé USB », « Saisir le code » (clavier 5 × 7 Crockford, groupes de 4 + contrôle, erreur de frappe détectée **localement** au groupe près). Résultat hors ligne : « +500 NDEM activés hors ligne · confirmés à la prochaine connexion ».

### 7.6 Motifs de refus (identiques sur la TV et le téléphone, W19)

| Motif | Texte |
|---|---|
| `INSUFFICIENT` | « Solde insuffisant : 120 NDEM disponibles » |
| `OFFLINE` | « Connexion Internet nécessaire » |
| `BOUND_OTHER_TV` | « Ce compte est lié à une autre TV » |
| `TRIAL_NO_MBOKO` | « Mises MBOKO : version complète » |
| `STAKES_SUSPENDED` | « Mises suspendues pour maintenance : les parties sans mise restent ouvertes » |
| `DAILY_CAP` | « Plafond du jour atteint : réessayez demain » |
| `CODE_UNKNOWN` / `CODE_EXPIRED` | « Code de réception inconnu » / « Code expiré : demandez-en un nouveau » |
| `VOUCHER_USED` / `VOUCHER_OTHER_TV` / `VOUCHER_EXPIRED` / `VOUCHER_BAD` | « Bon déjà utilisé » / « Ce bon est destiné à une autre TV » / « Bon expiré » / « Bon illisible » |
| `FROZEN` | « Compte en vérification : contactez votre point focal » |
| `ACTIVATE` | « Activez la TV pour recevoir des jetons » |
| `CLOCK` | « Vérifiez l'heure de la TV » |

### 7.7 Téléphone (CastBridge)

La page `/quiz` de la TV montre la carte compacte (soldes de la TV) et, pendant une partie misée, la cagnotte et le gain ; le téléphone **ne demande rien** à l'API portefeuille ; les actions (convertir, transférer, bons) se font **sur la TV** (le téléphone peut seulement **pousser** un bon à la TV, niveau 1). Profil enfant : aucune mise, aucun transfert, aucune conversion (la règle Internet existante l'exclut déjà du jeu en ligne).

## 8. Télémétrie technique (W21)

Presque tout se calcule **au serveur, depuis le grand livre**, sans évènement nouveau de la TV : agrégats journaliers `kpi_wallet_daily` (niveau 2) : tranches versées par édition et par monnaie ; masse en circulation (I-3) ; conversions (nombre, histogramme des quantités 1, 2-5, 6-20) ; transferts (nombre, histogramme des montants à bornes fixes 100, 1 000, 10 000 NDEM ; 10, 50, 100 MBOKO) ; parties misées (nombre par monnaie, histogramme des mises par siège, taux d'abandon, délai blocage → règlement p50/p95, part réglée par le collecteur) ; bons (actifs, confirmés, doublons refusés) ; alertes R-E9 (nombre par type). **Aucune identité** dans les agrégats ; plancher k = 5 identités par case affichée (règle W21). Côté TV, un seul évènement au catalogue fermé : `wallet_error{reason}` (motif du § 7.6, niveau statistiques d'usage, avec consentement). Délais d'opération (blocage, synchronisation) : ajoutés aux histogrammes `play.*` de W21 (`walletMs`, bornes 400/1 000/2 000 ms).

## 9. Ce qu'il faut construire

### 9.1 NIVEAU 1 — POC de faisabilité technique (priorité absolue)

| Point à prouver | Testé sur JVM (propriétés, boucle locale) | Demande un essai sur TV réelle | Simplifié au niveau 1 | À durcir au niveau 2 | Cahiers |
|---|---|---|---|---|---|
| (a) grand livre à double entrée et invariants | **oui** : 10 000 suites aléatoires d'opérations (graine fixée, sans dépendance nouvelle) ⇒ I-1…I-9 ; mutations (retirer un verrou, une contrainte d'idempotence) détectées ; concurrence (16 fils) sur H2 en mode MySQL (outillage existant, `backend/src/test/resources/application-test.yml`) **et** sur MySQL 8.4 par Testcontainers (`MySqlContainerTest.java`, sauté sans Docker) | non | soldes vérifiés par un test et une route d'administration, pas de travail nocturne | réconciliation nocturne, clôture mensuelle, archivage | w22-01, w22-02 |
| (b) attributions par édition | **oui** : vecteurs (essai 7 j, production 90 j, illimitée, renouvellement superposé, essai → production, révocation, TV hors ligne 3 mois puis synchronisée) | une vraie TV de production et une d'essai se synchronisent (activations réelles) | révocations par fichier facultatif | liste de révocations quotidienne | w22-01, w22-02 |
| (c) solde visible sur la TV, mis à jour | vérification `cbw1` (vecteurs Kotlin ↔ Java), cache (ancien `seq` ignoré, altéré ignoré) | **oui** : carte sur l'accueil et dans le salon, avant/après une partie | rafraîchissement à l'ouverture du Quiz en ligne et après chaque opération | rafraîchissement périodique fin, notifications | w22-03, w22-07 |
| (d) partie TV à TV avec mise NDEM puis MBOKO | **oui, en deux moitiés reliées par contrat** (le service est en Kotlin/Gradle, l'API en Java/Maven) : (1) boucle locale du vrai `PlayServer` : 2 « TV » JVM, 2 × 2 téléphones relayés, `cbe1` de test, mise NDEM puis MBOKO, essai refusé en MBOKO, drain ⇒ `ABORT` ; les `cbr1` produits sont **gardés en fixtures** ; (2) l'API règle ces mêmes `cbr1` sur des blocages réels (H2 + MySQL), reposte idempotent (TV **et** collecteur), clé inconnue ⇒ rien ; le bout en bout réel est l'essai sur TV | **oui** : démonstration 2 TV + 2 téléphones (amendement § 2.11), une TV par la passerelle Bluetooth | mise par siège, Duel seulement, collecteur par cron | plafonds de perte quotidienne, alertes de gains anormaux | w22-03, w22-04, w22-05 |
| (e) conversion dans les deux sens (1 000 ↔ 1) | **oui** (propriétés : valeur conservée au taux près dans les deux sens, aller-retour neutre à `fee` = 0, perdant à `fee` > 0, jamais gagnant ; idempotence ; même clé avec un autre sens refusée) | **oui** : écran « Convertir mes jetons », les deux sens | taux et frais lus dans `wallet_policy` ; aucun plafond ; pas de poche BONUS | R-E4 (étiquette qui suit), R-E5 | w22-01, w22-05, w22-07 |
| (f) **un** transfert entre deux comptes TV | **oui** (code de réception, expiration, usage unique, liaison d'appareil) | **oui** : TV A reçoit, TV B envoie | un plafond global simple | R-E4, R-E6, R-E9 | w22-05, w22-07 |
| (g) **un** bon hors ligne | **oui** : vérification hors ligne, cible, échéance, nonce local ; deux TV avec le même bon ⇒ une seule confirmée ; restauration ⇒ idempotent | **oui** : TV en mode avion, bon poussé par le téléphone ou fichier USB, puis reconnexion | lot importé à la main, bon par QR/fichier | codes courts au détail, lots par revendeur | w22-03, w22-06, w22-07 |
| (h) mise aux échecs | — | — | **reportée** : aucune salle d'échecs en ligne côté serveur (coût d'un service d'échecs complet ≈ 3-4 j) | w22-12 | — |

**Ce qui conditionne la validité de la démonstration (donc niveau 1)** : conservation, aucun négatif, la TV ne crée rien, idempotence, un blocage réglé une seule fois, MBOKO réservé à la production, interrupteurs d'exploitation. **Ce qui est classé niveau 2** : poche BONUS, plafonds fins, vélocité, gel/reprise, réconciliation nocturne, archivage, bons au détail, rémunérations, échecs, télémétrie, liaison par clé d'installation.

### 9.2 Cahiers de la vague 22

Index : `docs/agent-briefs/SONNET-WAVE22-INDEX.md`. Coûts aux prix des index précédents (sonnet 2/10 $/M, opus 4/20 $/M, haiku 0,8/4 $/M : **non vérifiés**).

| Ordre | Cahier | Niveau | Effort | Modèle | Audit Opus | Dépend |
|---|---|---|---|---|---|---|
| 1 | w22-01 cœur Java pur du grand livre (règles, attributions, conversion, transfert, blocage/règlement, invariants, tests de propriétés) | 1 | M | sonnet | **obligatoire** | — |
| 1 bis | w22-03 formats signés `cbw1`/`cbe1`/`cbr1`/`cbv1` côté Kotlin + cache TV + vecteurs | 1 | M | sonnet | **obligatoire** | — |
| 2 | w22-02 serveur : migration V63, persistance, lecture de l'édition dans `cbx1`, tranches paresseuses, `sync`, historique, `cbw1`, interrupteurs | 1 | L | sonnet | **obligatoire** | 01 |
| 2 bis | w22-04 service de jeu : salles misées, vérification `cbe1`, `cbr1` signé, dépôt des résultats | 1 | M | sonnet | **obligatoire** | 03 |
| 3 | w22-05 API : blocage, règlement, conversion, codes de réception, transfert, collecteur d'hôte | 1 | M | sonnet | **obligatoire** | 02, 03 (04 pour le test de bout en bout) |
| 3 bis | w22-06 bons hors ligne : outil du propriétaire, import, confirmation à `sync`, magasin local de la TV | 1 | M | sonnet | **obligatoire** | 02, 03 |
| 4 | w22-07 câblage TV + page téléphone (carte, convertir, transférer, recevoir, bon, choix de mise) derrière `quiz.online` | 1 | M | sonnet | échantillon | 03, 05, 06 ; w20-05 POC |
| 5 | w22-08 démonstration, exploitation, livraison `server-1.x`, `docs/WALLET.md` | 1 | S | haiku | — | 01-07 |
| 6 | w22-09 anti-abus : poche BONUS, plafonds, vélocité, alertes | 2 | M | sonnet | **obligatoire** | niveau 1 |
| 6 | w22-10 administration : gel, reprise, réaffectation, réconciliation nocturne, clôture et archivage | 2 | M | sonnet | **obligatoire** | niveau 1 |
| 7 | w22-11 bons au détail à code court, lots par revendeur | 2 | M | sonnet | **obligatoire** | 06 |
| 7 | w22-12 échecs en ligne TV à TV avec mise | 2 | L | sonnet | **obligatoire** | niveau 1 |
| 8 | w22-13 télémétrie portefeuille et politique de rémunération | 2 | S | sonnet | échantillon | 09 |
| 8 | w22-14 liaison forte du compte à la clé d'installation de la TV | 2 | M | sonnet | **obligatoire** | 02 |

**Coût du niveau 1** (estimé) : sonnet 6 × M (1,0 $) + 1 × L (1,6 $) = 7,6 $ ; haiku S ≈ 0,15 $ ; audits Opus 6 × 0,8 $ + 1 échantillon 0,4 $ = 5,2 $ ; reprises 15 % ≈ 1,9 $ ⇒ **≈ 15 $**, ≈ **12 agent·jours** ; chemin critique 01 → 02 → 05 → 07 → 08 ≈ **7 jours ouvrés** si 01 ∥ 03, 02 ∥ 04, 05 ∥ 06. **Niveau 2** : 4 × M + 1 × L + 1 × S ≈ 6,1 $ + audits ≈ 4,4 $ + reprises ≈ 1,6 $ ⇒ **≈ 12 $**, ≈ 11 agent·jours.

**Première valeur livrable** : après w22-01 + w22-02 (≈ 3 j), le grand livre **NDEM seul** tourne au serveur et attribue les tranches aux TV qui se synchronisent ; après w22-04 + w22-05, la partie misée NDEM ; MBOKO est le **même chemin** avec une ligne de règle de plus (R-E7).

## 10. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W22-1 | « 10 MBOKO × nbre de mois, ou 50 MBOKO avec 10 MBOKO/mois » : le « ou » distingue-t-il la clé **à durée** (10 × N) de la clé **illimitée** (50 + 10/mois) ? | **Oui** (miroir de la règle NDEM : production 1 000 × N, illimitée 5 000 + 1 000/mois) | (B) choix offert à chaque licence ; (C) 10 × N d'un coup (déconseillé, § 1.2) |
| D-W22-2 | Versement en **tranches mensuelles** (une par période de 30 j commencée), total = taux × N, N = `ceil(jours/30)`, une tranche par période même avec des clés superposées ? | **Oui** | avance de N mois à l'émission : irrécupérable en cas de fraude ou de remboursement |
| D-W22-3 | « Sans enjeu » = parties à **cagnotte NDEM** ; les parties sans mise restent **gratuites** (aucun droit d'entrée en NDEM) ? | **Oui** | un droit d'entrée ferait payer le jeu en ligne |
| D-W22-4 | TV « super illimité » (super administrateur) : **aucune** attribution automatique ? | **Oui** (dons manuels si voulu) | elles recevraient l'ouverture illimitée |
| D-W22-5 | Mise **par siège**, payée par le compte de la TV, cagnotte `Pot.split` existante, Duel seulement ? | **Oui** | mise par TV (score = meilleur siège) : plus simple mais moins juste pour 8 joueurs |
| D-W22-6 | Anti-ferme : poche **BONUS** non transférable pour le NDEM d'essai et promotionnel (jouable et convertible), plafond de conversion **20 MBOKO / jour** (essai 2), plafonds de transfert R-E6, alertes R-E9 sans blocage automatique — au **niveau 2** ? | **Oui** | ferme d'essais bornée seulement par l'émission des clés |
| D-W22-7 | Compte lié à l'appareil API d'ouverture pour toute sortie de jetons, réaffectation par l'administrateur ? | **Oui** | une activation copiée vide le compte |
| D-W22-8 | Bons : clé **hors ligne** du propriétaire + lot importé ; MBOKO seulement en bons **ciblés** ; plafonds d'attente 10 bons / 20 000 NDEM / 200 MBOKO ? | **Oui** | clé serveur : la compromission du serveur fabriquerait des bons |
| D-W22-9 | Option B (blocage `cbe1` signé par l'API + résultat `cbr1` signé par le service avec une clé dédiée, collecteur d'hôte) ? | **Oui** | option A : identifiant du grand livre dans le service |
| D-W22-10 | Échecs avec mise au **niveau 2** (salle d'échecs en ligne à construire dans `castbridge-play`) ? | **Oui** | +3-4 j au niveau 1 |
| D-W22-11 | Coexistence : les « jetons » de la Boutique W5 (commodités du Millionnaire, `C/tokens/`) et les « points de défi » locaux (`C/quiz/Wallet.kt`) restent **distincts** de NDEM/MBOKO au niveau 1 ; au niveau 2, payer les commodités W5 en NDEM et retirer les jetons W5 ? | **Oui** (trois monnaies, c'est trop pour un joueur) | trois monnaies à expliquer |
| D-W22-12 | Mises par siège bornées : NDEM 1..1 000, MBOKO 1..100 ; transferts niveau 1 : 10 000 NDEM / 100 MBOKO par jour et par identité ? | **Oui** | bornes plus hautes : dommage plus grand d'un vol de compte |
| D-W22-14 | Conversion inverse MBOKO → NDEM : **symétrie** (1 MBOKO = 1 000 NDEM) et frais de plateforme **0 %** au lancement, frais = paramètre `convert.reverseFeeBp` de la table de politique (0..20 %), jamais un sens inverse plus favorable ? | **Oui, 0 %** (un écart n'apporte rien tant qu'aucun abus n'est observé ; s'il en apparaît, 1-2 % rend tout aller-retour perdant) | écart dès le lancement : moins lisible pour le joueur |
| D-W22-15 | Une identité d'**essai** peut **détenir** et **recevoir** des MBOKO (conversion, transfert entrant) et les reconvertir en NDEM, sans pouvoir les miser ni (niveau 2) les retransférer ? | **Oui** (§ 1.3 : la règle porte sur l'attribution et l'enjeu) | refuser MBOKO aux essais : bloque un cadeau d'une TV de production sans rien protéger |
| D-W22-13 | Exception de gel pour l'écran « Mes jetons » et le choix de mise, derrière `quiz.online` (comme D-AM-1) ? | **Oui** | la démonstration TV attend la sortie du gel |

**BLOQUÉ (faits non lisibles ici)** : aucun pour concevoir. À relever avant la démonstration : la présence d'un lecteur de QR dans l'application CastBridge ; l'utilisabilité de `EnvelopeVerifier` sans le module des licences ; la clé publique de l'émetteur des activations des TV de démonstration (déjà B-7 de l'amendement).

## 11. Risques

| # | Risque | Prob. | Parade |
|---|---|---|---|
| R-1 | Erreur de conservation dans le règlement (arrondis de `Pot.split`, non-utilisé) | moyenne | `Settlement.check` pur, tests de propriétés, refus de tout `cbr1` dont Σ ≠ Σ utilisé, audit Opus |
| R-2 | Interblocages MySQL sous charge | faible | verrous en ordre fixe, transactions courtes, test 16 fils |
| R-3 | Résultat jamais posté (TV gagnante hors ligne, collecteur arrêté) | moyenne | collecteur 5 min + santé ; rendu à `exp + 6 h` ; journal `RESULT_AFTER_REFUND` |
| R-4 | Redémarrage du service pendant des parties misées | certaine (déploiements) | drain W20 : `cbr1 ABORT` pour chaque salle misée avant l'arrêt |
| R-5 | Activation copiée : vol de solde | moyenne | liaison d'appareil (niveau 1), clé d'installation (niveau 2) |
| R-6 | Émission frauduleuse de clés de production (S-2) | moyenne | tranches mensuelles, quotas d'agents, révocation, reprise |
| R-7 | Clé « résultat » volée sur le serveur | faible | conteneur isolé, rotation à 2 clés, plafonds de mise |
| R-8 | Croissance de la base | faible au POC | clôture et archivage au niveau 2 ; ≈ 65 Ko / TV / mois (estimé) |
| R-9 | Confusion des trois monnaies (jetons W5, points de défi, NDEM/MBOKO) | moyenne | D-W22-11 ; libellés distincts |
| R-10 | Horloge de la TV fausse : bons jugés expirés | faible | `TvClock` ; le serveur juge en dernier |
| R-11 | Requête de blocage lente en EDGE (≈ 1-2 s) | moyenne | faite dans le salon avant « Commencer », une fois par partie ; mesurée (`walletMs`) |
| R-12 | Marché de revente (S-5) | moyenne | règle de produit, alertes de concentration, gel |

## 12. Ce qui a été lu, et ce qui n'a pas pu être vérifié

**Lu** : `DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (entier) ; `DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` (passages mises, juridique) ; `DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` (§ 0, § 1) ; `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` (§ 0-3, § 5-14) ; `DESIGN-W16-…` (§ 0, § 1.4) ; `docs/QUIZ.md` § 5 ; `docs/CHESS.md` (plan) ; `docs/RELEASES.md` (tags serveur) ; code : `C/quiz/Wallet.kt`, `C/quiz/QuizRoom.kt` (mise), `C/tokens/{TokenPolicy,TokenGrant,TokenSync,WalletMark}.kt`, `TokenWallet.kt` (en-tête), `C/lots/{EditionPolicy,PilotRules,RentalDurations}.kt` (en-têtes), `C/owner/Activation.kt` (édition, illimité), `C/owner/{KeyBadge,ProductionForm,DelegatedVerifier}.kt` (passages), `C/chess/{ChessRoom,ChessTransport}.kt` (en-têtes), `SP/entitlement/HostRights.kt`, `SP/PlayConfig.kt` (variables), `B/play/PlayTicketService.java`, `B/licenses/{ActivationService,EnvelopeVerifier}.java` (passages), `V51__licenses_core.sql`, `backend/application.yml` (drapeaux), `backend/docker-compose*.yml` (mémoire), `backend/pom.xml` (le serveur Java **ne dépend pas** du cœur Kotlin), liste des migrations (V61 la plus haute fusionnée ; V62 réservée par W21).

**Non lu ou non vérifié** : `ServerRoom.kt` ligne à ligne (l'extension « salle misée » est spécifiée à partir de l'amendement et de `QuizRoom`) ; `TvGate`, `Entitlements`, `RentalLines` ligne à ligne ; l'instanciation de `EnvelopeVerifier` hors module des licences ; la présence d'un lecteur de QR dans l'application ; (vérifié en fin de rédaction : le serveur teste sur H2 en mode MySQL et, si Docker est présent, sur MySQL 8.4 par Testcontainers) ; le prix d'une clé d'essai et d'une licence (pour chiffrer la rentabilité d'une ferme en XAF) ; aucun chiffre de coût, de taille ou de délai n'a été mesuré.
