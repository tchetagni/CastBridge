# Conception W16 : location à durée choisie par l'utilisateur (en heures d'utilisation réelle, plafond 96 h, ou en jours de validité, défaut 30 jours), contenu livré chiffré avec les données de location, pilote gratuit de 3 semaines

> **Statut : conception (Fable, architecte, 2026-10-02, corrigée le 2026-10-03 après trois précisions du propriétaire). Rien n'est implémenté par ce document.** Exécution : cahiers `docs/agent-briefs/sonnet-w16-NN-*.md` (index `SONNET-WAVE16-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents`. Tous les fichiers et lignes cités ont été relus le 2026-10-02 ; ce qui n'a pas pu être vérifié est marqué **non vérifié** ou **BLOQUÉ**. Aucun montant réel, aucun secret, aucun texte juridique (reporté au 2026-12-31) ; aucune commande `gradle`/`adb` lancée.
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`.
>
> **Décisions du propriétaire, mot pour mot.** (2026-10-02) « louer devrait être à l'initiative des utilisateurs pour la durée, et le serveur livrera le contenu chiffré avec les données de location. Testons ceci pour tous les contenus pendant 3 semaines, sans que la durée de connexion en heures ne dépasse pas 96 heures ». Précisions : (a) les 96 h valent **par location** et comptent le **temps d'utilisation réel** ; (b) pilote **gratuit** (mesure seule) ; (c) Apprendre (réservé) et œuvres W10 se louent, **Langues reste libre** ; (d) « **30 jours, c'est lorsqu'on n'indique pas de durée** » ; (e) « **96 heures c'est pour tester la location en heure pendant une durée raisonnable dans la phase de test** » : le plafond de 96 h ne s'applique **qu'à la location à l'heure**, le défaut de 30 jours reste une validité en jours **sans** budget d'usage ; (f) « **les unités de temps sont heures et jours** » : le sélecteur propose des **heures** et des **jours**, rien d'autre.
> Lecture retenue (§ 1) : **heures = temps d'utilisation réel** (compteur, plafond 96 h) ; **jours = validité par l'horloge** (comme aujourd'hui) ; **défaut = 30 jours**. La règle « durée imposée par le serveur, exacte » (`docs/RENTAL-LOTS.md` § 14 `:128-131`, `C/lots/RentalDurations.kt:5-8, 37-43`) devient le mode `rental.userChosen = 0`, hors pilote.

## 0. En quinze lignes

1. **Trois choix dans un seul sélecteur** (§ 1.1) : « **Sans durée précise : 30 jours** » (défaut présélectionné) · « **Jours** » 1 · 3 · 7 · 14 (validité calendaire, pas de budget d'usage, 30 jours au plus) · « **Heures d'utilisation** » 1 · 3 · 6 · 12 · 24 · 48 · 96 (temps de contenu ouvert, compté par la TV, **96 h au plus par location**, avec une borne calendaire de sûreté). **Jours et heures ne se convertissent jamais** : « 12 heures d'utilisation » n'est pas « 1 jour », « 4 jours » n'est pas « 96 h » (§ 1.3, libellés).
2. **Rien de nouveau dans la ligne signée** : la ligne `rental|…` porte déjà les deux grandeurs, **validité en jours** (`durationDays`) et **budget d'usage** (`maxUsageMinutes`, `C/lots/RentalLines.kt:5, 28`). **L'unité choisie est portée par le contrat** sans champ nouveau : location **en jours** ⇒ `durationDays = N`, `maxUsageMinutes = 0` ; location **à l'heure** ⇒ `maxUsageMinutes = H × 60` (≤ 5 760) **et** `durationDays` = borne de sûreté (§ 2.5). Format, vecteurs v1/v2, enveloppe v2, moteur, carnet, balayage, routes, livraison : **inchangés dans leur forme** ; changements **additifs** seulement.
3. **Par bouquet** (produit `loc-<bouquet>`, un contrat par bouquet) ; **3 locations utilisables à la fois par TV** (`maxConcurrent = 3`, moteur existant) et 3 contrats actifs par licence (émetteur) ; **prolonger** une location à l'heure = ligne de renouvellement (même `period`, budgets additionnés par le moteur `C/lots/RentalEngine.kt:95`), **jamais au-delà de 96 h par contrat** (émetteur **et** clamp TV) ; prolonger une location en jours = idem en jours, ≤ 30 j de validité restante ; relouer un bouquet terminé = nouvelle `period` (§ 1.4).
4. **Fenêtre du pilote** : émissions du **lundi 2026-10-12** au **dimanche 2026-11-01** (3 semaines) ; **location à l'heure** : les heures doivent être utilisées **avant le 2026-11-15** (fin + 14 j de grâce : borne de sûreté portée par `durationDays`) ; **location en jours et défaut** : la durée choisie est **honorée en entier** (une « 30 jours » prise le 01/11 finit le 01/12 : borne absolue = fin du pilote + 30 j) ; question D-W16-5 avec l'alternative « tout tronquer au 15/11 ». L'interface dit la **date de fin réelle** avant la confirmation.
5. **Compteur d'usage hors ligne** (§ 2) : `UseMeter` pur (cœur), horloge **monotone** (`elapsedRealtime`, déjà injectée, `R/RentalHub.kt:42`), ticks d'**une minute** avec report du reste, ne compte que **contenu loué ouvert + écran actif** (pause > 5 min, inactivité > 30 min : arrêt), **attribué au lot réellement ouvert** (corrige `R/RentalHub.kt:153-158`, qui impute au premier contrat plafonné), persisté chaque minute par `RentalLedger.save` (`C/lots/RentalLedger.kt:145, 217`) : perte ≤ 1 min, jamais de double compte, sourd à l'horloge murale. Les minutes sont **aussi comptées** pour les locations en jours (sans plafond) : c'est la mesure « intensité d'usage » du pilote.
6. **Rapprochement** : `GET /api/rental` gagne `usedMinutes`, `maxUsageMinutes`, `remainingUsageMinutes`, `unit`, `reason` (additifs à `C/lots/RentalApi.kt:62-72`) ; le téléphone relève (`castbridge-rental-usage-v1`) et remet au serveur (tranche 2) ou au propriétaire (tranche 1) ; le serveur garde le **maximum monotone** par contrat. **Borne honnête** : effacer les données de l'application perd carnet **et** clé d'installation (`files/rental/`) ⇒ réémission avec le **reste** (choisi − dernier relevé), ≤ 3 fois ; TV rootée : limite déjà posée (`RENTAL-LOTS.md:109`).
7. **Livraison** (§ 3) : le flux scellé par TV existant, inchangé (boîte v2, `RentalKeys.seal`, `/api/activation/install` puis `/api/rental/install`, TV hors ligne). **Tranche 1 (module licences éteint, `application.yml:112-114`)** : l'outil de bureau émet (`emettre --location-choix classe-cm2=6h|7j|defaut --pilote pilot.json --registre …`), scelle (`lot-chiffrer`), produit un **dossier de livraison** consommé tel quel par l'écran existant « Locations sur la TV » (`S/RentalDeliveryActivity.kt:55-89`) ; `tools/pilot/louer.py` enchaîne tout (LAN ou relais `nc` par le téléphone). **Tranche 2** : module serveur `castbridge.pilot` (éteint par défaut) : commande à **prix 0**, clé de contrat aléatoire sous KEK (W5 § 4.2), activation signée par la clé serveur, lots scellés servis, relevés, idempotence, file hors ligne. **La plateforme signe ; jamais un agent de terrain.**
8. **Réglages** (§ 4) : 12 clés W12 avec bornes (`rental.userChosen`, `rental.defaultDays = 30`, `rental.pickerDays`, `rental.maxDays = 30`, `rental.hourly.maxUseHours = 96`, `rental.hourly.pickerHours`, `rental.hourly.validityDays = 30`, `rental.maxConcurrent = 3`, `rental.hourly.weeklyQuotaHours`, `rental.cooldownMin = 0`, `pilot.start`, `pilot.end`, `pilot.graceDays`) ; avant W12 : `tools/pilot/pilot.json` (émetteurs) et deux défauts compilés nommés (TV). Cahiers W4/W5/W10/W12 qui codent une durée : § 4.3 (amendements d'en-tête).
9. **Plan de test** (§ 5) : la **location à l'heure est l'objet du test** ; 10 hypothèses chiffrées (part heures / jours / défaut, durées choisies, heures réellement utilisées sur le budget, intensité d'usage des locations en jours, abandon, prolongations, intention de payer, abus, fiabilité du compteur, assistance) ; télémétrie **avec consentement** (`rental_start{bundle, unit, amount}`, `rental_use{bundle, minutes}` par jour, `rental_end{bundle, reason, usedMinutes}`, `rental_extend`, `rental_survey`) ; seuils go / ajuster / no-go ; calendrier S0 (05-11/10) · S1-S3 (12/10-01/11) · bilan horaire le 16/11, complément jours le 02/12.
10. **Première tranche** (§ 6) : **cœur + bureau + docs**, JVM/Python, exécutable **pendant le gel** (plan § 6 `:409-422`) : w16-01…07, 12, 13, 14 ≈ 12,5 j ; pilote jouable avec l'écran téléphone existant et **une location à la fois par TV** ; TV/téléphone complets (w16-10, w16-11) à la sortie du gel ; serveur (w16-08, 09) pour le pilote payant.
11. **Effort** : 14 cahiers, ≈ **22 agent·jours**, coût API ≈ **15 $** (§ 6.3).
12. **Œuvres (W10)** : couvertes par le modèle (une œuvre = un bouquet) mais **W10 n'est pas fusionné** (aucun `WorkHub`/`WorkStore` dans `android/`, vérifié) et ses cahiers TV attendent la sortie du gel : **le pilote porte sur Apprendre** ; œuvres si le pilote minimal W10 sort avant S2 (**BLOQUÉ** calendrier).
13. **Enfants** : un profil enfant **utilise** une location du foyer (ses minutes comptent), **ne commande jamais** (W5 § 6.6) ; le relevé ne porte ni profil ni personne (cohérent avec `docs/PARENTAL.md:5`).
14. **Langues** : jamais loué (`C/lots/RentalPolicy.kt:23-28`) ; affiché « Gratuit », sans sélecteur.
15. **Décisions** (§ 7) : 12 questions au propriétaire avec recommandation ; **questions de produit ouvertes** par les deux unités et le défaut de 30 jours au § 7.2.

## 1. Modèle de produit

### 1.1 Les trois choix du sélecteur et leur nature

| Choix | Nature | `durationDays` | `maxUsageMinutes` | Fin | Libellé exact (téléphone et TV) |
|---|---|---|---|---|---|
| **Sans durée précise : 30 jours** (défaut, présélectionné) | validité calendaire (comme aujourd'hui) | `rental.defaultDays` = 30 (ou `rentalDays` du bouquet s'il en a un, `RentalDurations.daysOf`, `C/lots/RentalDurations.kt:20-25`) | **0** (aucun budget) | date | « Sans durée précise : 30 jours (jusqu'au 11/11) » |
| **Jours** : 1 · 3 · 7 · 14 (`rental.pickerDays`) | validité calendaire | N (≤ `rental.maxDays` = 30) | **0** | date | « 7 jours (jusqu'au 19/10) » |
| **Heures d'utilisation** : 1 · 3 · 6 · 12 · 24 · 48 · 96 (`rental.hourly.pickerHours`) | **temps de contenu ouvert**, compté par la TV | borne de sûreté = `min(rental.hourly.validityDays = 30, jours jusqu'à pilot.end + pilot.graceDays)` | H × 60 (≤ 5 760) | **première** des deux : budget épuisé **ou** borne | « 12 heures d'utilisation (à utiliser avant le 15/11) » |

- **Pourquoi deux natures, pas une** : c'est ce que le propriétaire demande (unités heures **et** jours) et ce que le format sait déjà faire (deux plafonds, le premier atteint gagne : `C/lots/RentalEngine.kt:116-121`). Un **jour** passe même TV éteinte ; une **heure d'utilisation** ne passe que contenu ouvert : deux produits différents, qu'on ne convertit jamais l'un dans l'autre.
- **Pourquoi ces paliers** : jours 1/3/7/14 (un soir, un week-end, une semaine, deux semaines ; 30 = le défaut et le plafond `rental.maxDays`, pour que « jours » et « défaut » ne se recouvrent pas) ; heures 1/3/6/12/24/48/96 (une soirée d'essai, une semaine d'école à 30 min/jour, un mois scolaire, des révisions ; 96 = le plafond décidé). Douze entrées en trois groupes, lisibles à la télécommande.
- **30 jours au plus pour une location en jours** (`rental.maxDays = 30`) : cohérent avec « 30 jours quand on n'indique pas de durée » (le défaut est le maximum, pas une valeur au milieu), avec le plafond en ligne de W5 (60 j) qui reste la borne haute du réglage, et avec la fin du pilote (§ 1.4).
- **Par bouquet, pas par titre** : contrat par produit `loc-<bouquet>` (`C/lots/RentalDurations.kt:10-17`) ; une œuvre est déjà son propre bouquet (W10 § 2.2). Un bouquet libre (Langues) n'a pas de sélecteur.
- **Plusieurs locations en parallèle** : **3 utilisables à la fois par TV** (`maxConcurrent = 3` dans la ligne ; au-delà `OVER_LIMIT`, les plus anciennes gardent leur droit, rien n'est supprimé : `C/lots/RentalEngine.kt:124-131`) ; **3 contrats actifs par licence** (émetteur). **Une seule à la fois en tranche 1** (§ 6.1). Un même bouquet n'a **qu'un contrat à la fois** : on ne cumule pas « 7 jours » et « 12 h » sur CM2 ; changer d'unité = après la fin (ou remplacer, D-W16-3).

### 1.2 Pourquoi 96 h ne s'applique qu'à l'heure (lecture du propriétaire, appliquée)
« 96 heures c'est pour tester la location en heure pendant une durée raisonnable dans la phase de test » : le plafond borne **l'objet du test** (le budget d'une location horaire), pas le produit existant (30 jours de validité). Conséquences : (1) une location en jours ou par défaut n'a **aucun** budget d'usage (`maxUsageMinutes = 0`, exactement l'émission d'aujourd'hui) ; (2) la TV compte **quand même** ses minutes d'usage (`RentalLedger.recordUsage` incrémente `used` sans plafond, `C/lots/RentalLedger.kt:141-145`) : c'est une **mesure**, jamais une limite ; (3) le plafond de 96 h est vérifié par l'émetteur et **clampé** par la TV pour les seules lignes à budget (`RentalConfig.maxUseMinutesPerContract`, § 2.5).

### 1.3 Règle de présentation : jamais de conversion, des libellés qui disent la nature
- **Heures** : toujours « **N heures d'utilisation** » (« 12 heures d'utilisation », « 1 heure d'utilisation »), jamais « 12 h » seul dans un choix ; aide d'une ligne sous le groupe : « Les heures comptent seulement pendant que le contenu est ouvert sur la TV. » ; en cours : « Il vous reste 5 h 20 **d'utilisation** · à utiliser avant le 15/11 ».
- **Jours** : toujours « **N jours** » avec la **date de fin** : « 7 jours (jusqu'au 19/10) » ; aide : « Les jours passent même quand la TV est éteinte. » ; en cours : phrase existante « Il vous reste 5 jours » (`RentalEngine.countdown`, `C/lots/RentalEngine.kt:158-161`).
- **Défaut** : « **Sans durée précise : 30 jours** (jusqu'au 11/11) » ; en cours : « Il vous reste 23 jours ».
- **Interdits** : « 96 h = 4 jours », « 1 jour = 24 h d'utilisation », barres de progression mélangées ; une location à l'heure n'affiche sa borne calendaire que comme « à utiliser avant le … », jamais comme un compte à rebours en jours.
- **Alertes** (`RentalWarning`, trois niveaux existants) : jours : 7 j / 24 h / 1 h avant la date (existant `:142-147`) ; heures : seuils **relatifs** au budget : ≤ 25 % restant / ≤ 60 min / ≤ 10 min (les seuils actuels 24 h / 5 h / 1 h d'usage, `:149-155`, tomberaient à l'ouverture d'une location d'1 h).
- **Fin** : heures : « Vos 12 heures d'utilisation sont épuisées : ce contenu n'est plus disponible. Relouer ? » ; jours : « Location terminée (7 jours, jusqu'au 19/10) : ce contenu n'est plus disponible. Relouer ? » ; borne de sûreté d'une location à l'heure : « Vos heures non utilisées ont expiré le 15/11 (fin du test gratuit). Relouer ? ». Message existant `ENDED` (`:163`) conservé pour les contrats sans unité connue.
- **Badge de clé** (`C/owner/KeyBadge.kt:41`) : « Location : 5 h 20 d'utilisation restante(s) » ou « Location : 5 jour(s) restant(s) » selon l'unité.
- **Fin du test** : « Test gratuit : les heures se terminent au plus tard le 15/11 » sur l'écran des locations tant qu'un contrat horaire est en cours (déduit du contrat, sans réglage sur la TV).

### 1.4 Prolongation, relocation, fin de pilote, limites d'abus
- **Prolonger** : ligne de **renouvellement** de même `(produit, period)` ; le moteur fusionne (`contracts()`, `C/lots/RentalEngine.kt:84-100` : fin = `max(fin, startsAt) + jours` ; **usage additionné** quand toutes les lignes en ont un, **aucun** sinon) et garde la **même clé** (lots déjà livrés toujours lisibles). **Heures** : `maxUsageMinutes` = heures ajoutées × 60, `durationDays` = même borne de sûreté ; somme ≤ 96 h (émetteur : « cette location a déjà 96 h » ; TV : clamp). **Jours** : ligne avec `maxUsageMinutes = 0` et `durationDays` = jours ajoutés ; validité restante ≤ 30 j (émetteur). **On ne mélange pas** : prolonger une location en jours avec des heures (ou l'inverse) est refusé par l'émetteur (le moteur, lui, donnerait « aucun plafond d'usage » dès qu'une ligne est à 0 : `:95`, ce qui transformerait silencieusement une location horaire en location en jours : **à tester comme refus**).
- **Relouer le même bouquet** : **pendant** = prolongation (la `period` du contrat en cours est lue dans `GET /api/rental` et dans le registre) ; **après** la fin (contrat `DONE`, pierre tombale, `C/lots/RentalLedger.kt:20-21`) = **nouvelle** location (nouvelle `period`, nouvelle clé), comptée dans le quota. `rental.cooldownMin = 0` : le quota suffit ; un délai punirait celui qui a épuisé 1 h et en veut 3.
- **Fin du pilote** : `pilot.start = 2026-10-12`, `pilot.end = 2026-11-01` fin de journée (Douala) ; **aucune émission après** (« le test gratuit est terminé »). **Heures** : borne de sûreté `pilot.end + pilot.graceDays (14) = 2026-11-15` ⇒ `durationDays = min(30, jours entiers jusqu'au 15/11)` (émise le 12/10 → 30 j, fin 11/11 ; le 25/10 → 21 j ; le 01/11 → 14 j) ; les heures non utilisées **s'éteignent** par la date (`EXPIRED`/`DATE`, clé détruite, lots effacés par le balayage existant). **Jours et défaut** : durée **honorée en entier** (recommandation D-W16-5 : c'est le produit existant, pas l'objet du test ; tronquer « 30 jours » en « 21 jours » le 25/10 biaise la mesure du choix) ⇒ borne absolue = `pilot.end + rental.maxDays` = **2026-12-01** ; alternative = tout tronquer au 15/11 (le sélecteur montre alors la vraie date, « 30 jours » devient « jusqu'au 15/11 »). Dans les deux cas la **date de fin réelle** est affichée avant confirmation.
- **Limites d'abus d'un pilote gratuit** :

| Règle | Valeur | Où | Pourquoi |
|---|---|---|---|
| Budget d'une location à l'heure | ≤ 96 h | ligne + clamp TV | la décision |
| Validité d'une location en jours | ≤ 30 j | émetteur | = le défaut |
| Simultanées utilisables par TV | 3 | `maxConcurrent` (moteur) | une TV partagée entre foyers ne peut pas tout ouvrir |
| Contrats actifs par licence | 3 | émetteur | la TV ne connaît que ses activations (`RENTAL-LOTS.md:43`) |
| Heures louées par licence sur 7 jours glissants (horaire seulement) | 192 h (`rental.hourly.weeklyQuotaHours`, 0 = sans) | émetteur | deux « 96 h » ou seize « 12 h » par semaine : au-delà c'est du stockage |
| Un contrat par bouquet à la fois | — | émetteur + moteur (même produit) | pas de cumul d'unités |
| Relouer en cours | = prolonger (même unité, plafonds) | émetteur + TV | pas de remise à zéro |
| Réémission après réinstallation | même `period`, heures = choisi − dernier relevé (jours : date inchangée), ≤ 3 fois | émetteur (`RENTAL_REISSUE_ABUSE` au-delà, comme W5 § 4.2) | une réinstallation ne redonne rien |
| Après `pilot.end` | aucune émission | émetteur | fin du test |

## 2. Comptage du temps d'utilisation réel, hors ligne

### 2.1 Ce qui existe (vérifié)
- La TV compte **déjà** des minutes : `RentalLedger.recordUsage(lot, minutes, activations)` (`C/lots/RentalLedger.kt:137-148`) impute à la location utilisable **couvrant le lot** (`RentalLogic.covers`, `:221-224`) qui finit en premier, persiste (`save()`, `SafeFile`, `:145, 217`), déclenche `EXPIRED`/`USAGE` au plafond (`:144` ; `RentalEngine.evaluate:116`), **même horloge douteuse** (`:135`), et incrémente `used` **même sans plafond**. `CT/lots/TrialWindowTest.kt:35-44` prouve la fin par usage.
- Le tick vient de `R/LearnActivity.kt:89-92` (toutes les 60 s **au premier plan**) → `RentalHub.meterOneMinute` (`R/RentalHub.kt:153-158`), qui **choisit le premier contrat plafonné utilisable et son premier lot** : juste tant qu'il n'y a qu'un contrat plafonné ; **faux avec deux** ; **ignore les contrats sans plafond** (donc aucune mesure des locations en jours) ; **absent du Quiz** (vérifié : aucun autre appel).
- Horloge : `TvClock` (`C/owner/Keys.kt:105-165`) : temps monotone injecté (`R/RentalHub.kt:42`), temps de marche cumulé (`uptimeNow`, `:137`), maximum vu jamais reculé ; `RentalEngine.judge` (`C/lots/RentalEngine.kt:69-77`) suspend sur doute, n'allonge jamais.

### 2.2 Le compteur (`C/lots/UseMeter.kt`, pur, w16-02)
- **État** : `openLot: LotId?`, `lastTickMono`, `carryMs` (< 60 000), `pausedSinceMono?`, `lastInputMono`.
- **Entrées** : `open(lot, nowMono)` (contenu loué ouvert, écran actif), `close(nowMono)` (`onPause`, extinction), `pause/resume(nowMono)` (lecture d'une œuvre ; sans effet pour Apprendre), `input(nowMono)` (toute touche), `tick(nowMono)` toutes les 60 s **et** à `close`.
- **Ce qui compte** : minutes entières de « lot ouvert **et** premier plan **et** pas en pause depuis > 5 min (`pauseStopMs`) **et** une touche depuis < 30 min (`idleStopMs`) ». Le reste est **reporté** (`carryMs`) : 59 s + 59 s = 1 min comptée, 58 s en report ; **jamais arrondi vers le haut**, jamais deux fois. Fermer après 30 s : rien compté, report effacé (dix ouvertures de 30 s ne coûtent rien : honnête, et l'inverse serait du vol de minutes).
- **Sortie** : `tick` renvoie `(lot, minutes)` ; l'intégration appelle `ledger.recordUsage(lot, minutes, activations)` **pour tout lot loué**, plafonné ou non (mesure des locations en jours) ; borne `0..1440` (`:138`) respectée.
- **Attribution** : la location couvrant **ce** lot ; prolongation = même contrat ; achat arrivé = `release` (`:131`), le lot sort de la location.

### 2.3 Persistance, coupures, redémarrages
Chaque minute est écrite par `RentalLedger.save` (`SafeFile`, `.bak`, relecture `:170-209`) : **perte ≤ 1 minute** à la coupure ou au `kill` ; `carryMs` non persisté (au pire 59 s offertes). **Reboot** : `elapsedRealtime` repart de 0, `UseMeter` est recréé vide (rien d'ouvert avant que l'écran ne rouvre) : aucun double compte ; `nowMono < lastTickMono` ⇒ `lastTickMono = nowMono`. **Carnet illisible** : état `degraded` existant (`:33-38, 188-193`) ⇒ contrats terminés : une corruption ne peut pas « rendre » des heures.

### 2.4 Résistance aux manipulations (borne honnête)
| Manipulation | Effet | Pourquoi |
|---|---|---|
| Horloge murale reculée / avancée | **aucun** sur les heures ; les jours sont jugés par `TvClock` (suspension, jamais d'allongement) | compteur monotone ; `recordUsage` compte même en doute |
| Tuer l'application pendant une leçon | ≤ 1 min non comptée | écriture par minute |
| TV laissée allumée sur une leçon | ≤ 30 min comptées, puis arrêt (bandeau « Toujours là ? ») | `idleStopMs` |
| Restaurer `rentals.json` (copie du dossier privé) | impossible sans root (`allowBackup=false`, w1-01) ; avec root : limite déjà écrite (`RENTAL-LOTS.md:109-110`) | hors modèle de menace |
| Effacer les données de l'application | carnet **et** `install.key` perdus ⇒ boîtes v2 illisibles ; réémission depuis le **dernier relevé** (≤ 3 fois) | `DESIGN-W4:42` ; § 1.4 |
| Deux TV pour une licence | chaque TV a ses contrats et son compteur ; quota par licence à l'émetteur | `RENTAL-LOTS.md:43` |
**Ce que la TV ne peut pas** : signer son relevé (X25519 ne signe pas ; `sign=ed25519` de w6-12 non fusionné). Au pilote gratuit, un relevé non signé suffit ; `InstallSigner` (W6) signera les relevés quand la location sera payante (crochet noté dans w16-03).

### 2.5 Champs du contrat, unité, compatibilité
- **Ligne signée inchangée** : `rental|<produit>|<bouquets>|<startsAt>|<period>|<jours>|<grâce>|<usage max min>|<simultanées>|<box>` (`C/lots/RentalLines.kt:5, 28` ; `parse` exige 10 champs `:32` : un 11ᵉ rendrait `MALFORMED` toute activation sur les TV installées). **L'unité est portée par la ligne** : `maxUsageMinutes > 0` (et produit ≠ `essai`) ⇒ **location à l'heure** (`useMs = maxUsageMinutes × 60 000`, `notAfter = startsAt + durationDays × jour` = borne de sûreté) ; `maxUsageMinutes = 0` ⇒ **location en jours** (`notAfter` = la fin choisie). Alternative écartée : un préfixe de produit (`loch-<bouquet>`) : il créerait deux contrats possibles par bouquet, deux clés, et casserait « un contrat par bouquet ».
- **Additifs côté lecture** (aucune signature concernée) : `RentalContract.unit: RentalUnit { HOURS, DAYS }` (déduit) ; `RentalStatus` gagne `usedMinutes`, `maxUsageMinutes` (`remainingUsageMinutes` existe `:48`) ; `GET /api/rental` gagne `unit`, `usedMinutes`, `maxUsageMinutes`, `remainingUsageMinutes`, `reason`, `period`, `startsAt` ; `TvRentalView.Rental` idem ; `RentalConfig` gagne `maxUseMinutesPerContract` (5 760 ; 0 = sans clamp) appliqué dans `contracts()` aux seuls contrats à budget.
- **Relevé d'usage** `castbridge-rental-usage-v1` (texte) : en-tête `install=<installId>` (16 hex, déjà dans les journaux : `DESIGN-W4:41`) ; une ligne par contrat : `contract=<produit>@<period>|unit=<hours|days>|used=<min>|max=<min>|state=<…>|reason=<DATE|USAGE|->|endsAt=<ms>|at=<ms TV>`. Aucune personne, aucun profil. Non signé au pilote (§ 2.4).
- **Contrats déjà émis** (30 j, usage 0) : lus comme des locations en jours, affichés comme aujourd'hui.
- **Vecteurs** : `rental-vectors.json` et `rental-vectors-v2.json` **non modifiés** ; nouveau `tools/activation/rental-pilot-vectors.json` (`castbridge-rental-pilot-vectors-v1`) : lignes 1 h / 12 h / 96 h / 7 j / défaut 30 j, prolongation 6 h + 6 h, somme > 96 h clampée, mélange d'unités refusé, borne de sûreté tronquée, émission refusée après `pilot.end`, séquences de compteur (ticks, report, pause, inactivité, coupure, reboot), phrases FR, relevé (w16-06, **audit Opus**).

## 3. Livraison : contenu chiffré avec les données de location

### 3.1 Ce qui ne change pas
Boîte v2 par installation (`C/lots/RentalKeys.kt:102-133`), scellement déterministe (`seal`, `:41-46`), coffre et destruction de clé (`RentalVault`), carnet et pierre tombale, balayage (`RentalSweeper`, `C/lots/RentalSweeper.kt:18, 53-92`), routes TV `/api/rental/*` (`C/lots/RentalApi.kt`), livraison par le téléphone (`RentalDelivery.deliver`, `C/lots/RentalDelivery.kt:96-119` : activation d'abord `:102`), écran « Locations sur la TV », « lot libre jamais loué ». La TV **ne va jamais sur Internet** pour une location (`RentalApi.kt:19`).

### 3.2 Tranche 1 : l'outil de bureau émet (module licences éteint)
```
foyer ──« louer CM2 pour 6 heures » / « 7 jours » / « sans durée »──► propriétaire (Mac)
   tools/pilot/louer.py location --tv <code|URL> --bouquet classe-cm2 --choix 6h|7j|defaut --pilote tools/pilot/pilot.json [--prolonger]
     ├─ (a) demande d'appareil : GET /api/activation/request (LAN ou relais nc via le téléphone en ADB) ou fichier device-request.txt reçu
     ├─ (b) DK emettre --appareil … --production --licence <lic> --catalogue serveur --lots-libres … --location-choix classe-cm2=6h --pilote pilot.json --registre ~/.castbridge-activation/pilot-rentals.csv [--periode <ms>]
     │       → PilotRules : avant pilot.end ? bouquet réservé ? contrats actifs ≤ 3 ? unité = celle du contrat en cours ? somme ≤ 96 h / validité ≤ 30 j ? quota ?
     │       ⇒ RentalSpec(loc-classe-cm2, [classe-cm2], days = borne de sûreté (6h) | 7 (7j) | 30 (defaut), maxUsageMinutes = 360 | 0 | 0, maxConcurrent = 3)
     ├─ (c) DK lot-chiffrer --activation … --produit loc-classe-cm2 --lot … (par lot du bouquet)
     ├─ (d) dossier de livraison <code>-<bouquet>-<period>/ : activation · castbridge-lot-…-vN.lot (scellés) · catalog.json · contract (« loc-classe-cm2@<period> ») · LISEZMOI.txt (unité, fin réelle, « test gratuit »)
     └─ (e) remise : LAN/relais (upload + /api/rental/install directs, comme rental_test.py) OU fichiers au téléphone → « Activer la TV » puis « Locations sur la TV »
registre CSV (émetteur) : date, licence, code TV masqué, bouquet, period, unité, quantité, jours de validité, type (nouvelle|prolongation|réémission)
relevés : louer.py releve --tv … (GET /api/rental) ou fichier castbridge-rental-usage-v1 partagé par le téléphone → tools/pilot/bilan.py
```
- **Maître des clés** : `RentalKeys.masterFrom(signer)` de la clé de bureau réelle (`C/lots/RentalKeys.kt:27-28`, `DK/Cli.kt:321`) : rien de nouveau ; TV = **build verrouillé** qui lui fait confiance.
- **Idempotence** : le registre refuse une seconde émission identique (licence, bouquet, choix, même jour) sans `--encore` ; `--prolonger` exige la `period` du contrat en cours (lue dans `GET /api/rental`) et la même unité.
- **Téléphone hors ligne** : sans objet (les fichiers voyagent) ; l'écran existant reprend un envoi coupé (`:114-116`).

### 3.3 Tranche 2 : module serveur `castbridge.pilot` (éteint par défaut ; `B/pilot/**`, additif)
| Route (jeton d'appareil ; `X-CB-TV-Proof` W6 si fusionné) | Rôle |
|---|---|
| `POST /api/v1/pilot/rentals/quote` `{request, bundle, unit: hours\|days\|default, amount?}` | vérifie la demande (demande v2 + preuve d'activation, W5 § 3.4 a), répond `{allowed, reason?, unit, amount, maxUseMinutes, validUntil, renewalOf?: period, quotaLeftHours, pilotEnd}` ; ne crée rien |
| `POST /api/v1/pilot/rentals/orders` (`Idempotency-Key`) `{request, bundle, unit, amount?, channel}` | `pilot_rental_order` **prix 0**, `FULFILLED` immédiat : contrat (clé aléatoire 32 o sous KEK, `period` nouvelle ou existante), activation signée par la **clé serveur** (`ISSUE_PRODUCTION`, `B/licenses/ScopedActivationSigner.java` ; **non vérifié** : clé publique serveur dans `TRUSTED_KEYS` des TV), lots scellés en cache (`RentalKeys.seal`, `Range`/`ETag`) ; réponse `{orderRef, activation, contract:{product, period, unit, maxUseMinutes, endsAt}, lots:[{id, version, bytes, sha256, url}]}` |
| `GET /api/v1/pilot/rentals/orders/{ref}` | relecture |
| `GET /api/v1/pilot/rentals/{contract}/lots/{lot}/{v}` | lot scellé (même licence seulement) |
| `POST /api/v1/pilot/rentals/{contract}/usage` `{report}` | `pilot_rental_usage` : `used_minutes = max(précédent, relevé)`, `state`, `reason` ; réponse `{recorded, remainingMinutes}` |
| `POST …/orders/{ref}/delivered` `{tvAck}` ; `GET /api/v1/pilot/me` ; `GET /api/v1/pilot/config` (= `pilot.json`) | accusé ; état ; paramètres du sélecteur avant W12 |
| `/admin/pilot/rentals` (TOTP) | tableau par contrat, KPI § 5, CSV, « réémettre (reste) » |
Tables (migration `V<plus haut + 1>__pilot_rentals.sql` ; **le cahier vérifie le numéro**) : `pilot_rental_order (id, order_ref, license_id, seat_id, device_code, install_pub, bundle, unit ENUM(HOURS, DAYS, DEFAULT), amount INT, max_use_minutes, valid_days, amount_xaf = 0, status, channel, idem_key UNIQUE, created_at, fulfilled_at, delivered_at)`, `pilot_rental_contract (id, license_id, seat_id, install_pub, product_id, bundle, period_ms, unit, starts_at, ends_at, max_use_minutes (somme), key_enc, reissues INT, state, UNIQUE(license_id, seat_id, product_id, period_ms))`, `pilot_rental_usage (contract_id, used_minutes, state, reason, reported_at, tv_at, PK(contract_id, reported_at))`, `pilot_rental_survey (id, unit, q, answer, at)` (réponses **sans** appareil ni contrat).
- **Anti-fraude** : réinstallation (nouvel `installPub`, même code) ⇒ réémission de la même `period` avec `max_use_minutes − used` (heures) ou la date inchangée (jours) ; `reissues ≤ 3` ; quota par licence ; `AbuseService` pour les rafales.
- **Signatures** : activation = clé **serveur** ; catalogue / grille = clé des mises à jour du **propriétaire** (hors ligne) ; relevé non signé au pilote ; **aucun agent ne signe** (W5 P1).
- **Téléphone hors ligne** : commande en file (`DeliveryQueue`, `C/lots/DeliveryQueue.kt`) envoyée au retour du réseau (Wi-Fi pour les lots) ; relevé gardé et renvoyé (idempotent : max monotone).

## 4. Réglages (W12) et cahiers qui codent une durée en dur

### 4.1 Clés (`SettingsSchema` ; défauts compilés = valeurs du pilote pendant le pilote, valeur hors pilote entre parenthèses)
| Clé | Type | Défaut | Bornes | Consommateurs | Sens |
|---|---|---|---|---|---|
| `rental.userChosen` | BOOL | 1 (0) | 0/1 | émetteurs, tél., TV (affichage) | 1 = sélecteur ; 0 = durée exacte du catalogue (W5/W12 § 2.6) |
| `rental.defaultDays` | INT | 30 | 1–366 | émetteurs | « sans durée précise » (existant W12 #4) |
| `rental.pickerDays` | TEXT | `1,3,7,14` | ≤ 8 entiers croissants, chacun 1..`maxDays`, ≠ `defaultDays` | tél., TV | paliers en jours |
| `rental.maxDays` | INT | 30 | 1–60 (`rental.maxOnlineDays` W5) | émetteurs | validité maximale choisie (= le défaut) |
| `rental.hourly.maxUseHours` | INT | 96 | 1–720 | émetteurs, TV (clamp) | budget maximal d'une location à l'heure |
| `rental.hourly.pickerHours` | TEXT | `1,3,6,12,24,48,96` | ≤ 8 entiers croissants, chacun 1..`maxUseHours` | tél., TV | paliers en heures d'utilisation |
| `rental.hourly.validityDays` | INT | 30 | 1–60 | émetteurs | borne de sûreté calendaire d'une location à l'heure (avant troncature par la fin du pilote) |
| `rental.maxConcurrent` | INT | 3 | 1–20 | émetteurs (ligne), TV (moteur) | existant W12 #6 |
| `rental.hourly.weeklyQuotaHours` | INT | 192 | 0–672 | émetteurs | 0 = sans quota |
| `rental.cooldownMin` | INT | 0 | 0–1440 | émetteurs | délai avant de relouer un bouquet terminé |
| `pilot.start` / `pilot.end` | INT (ms) | 2026-10-12 00:00 / 2026-11-01 23:59 Douala | `end − start` ≤ 90 j | émetteurs, tél., serveur | fenêtre d'émission |
| `pilot.graceDays` | INT | 14 | 0–30 | émetteurs | borne de sûreté des heures = `pilot.end + graceDays` |
Invariants **jamais réglables** (W12 § 1.3 J18) : `RentalLines.MAX_DAYS/MAX_USAGE_MINUTES/MAX_CONCURRENT`, le format de la ligne, la destruction de clé, la période du balayeur. Un réglage ne touche **jamais** un contrat installé (« une clé installée reste »). **Avant W12** : `tools/pilot/pilot.json` (mêmes noms de clés) pour les émetteurs ; la TV n'a besoin que de `RentalConfig.maxUseMinutesPerContract = 5760` et des libellés par unité.

### 4.2 Composition avec W12 § 2.6
« Une location garde la durée fixée dans son contrat » (`DESIGN-W12:13, 147`) reste vrai : le réglage change ce que l'émetteur **propose** ; la TV ne lit que la ligne. `rental.defaultDays` passe de « bouquet sans `rentalDays` » à « sans durée indiquée » quand `userChosen = 1` ; `rental.maxOnlineDays` (60) borne `rental.maxDays`.

### 4.3 Cahiers qui codent une durée ou la règle « exacte » (amendement d'en-tête ajouté ; le corps n'est pas réécrit)
| Cahier | Ce qu'il code | Amendement |
|---|---|---|
| `sonnet-w4-02` | `--location …:JOURS`, puis « exacte » via `--catalogue` | ajoute `--location-choix`, `--pilote`, `--registre` (w16-05) ; « exacte » = `rental.userChosen = 0` |
| `sonnet-w5-04` | plafond 60 j en ligne | 60 j reste la **borne** ; `PilotRules` (w16-04) fixe jours/usage quand `userChosen = 1` |
| `sonnet-w5-06` | durée exacte du catalogue ≤ 60 j, 3 contrats | durée = `PilotRules`, `unit` et `max_use_minutes` dans `rental_contract` ; `pilot_*` (w16-08) y fusionne quand w5-06 existe |
| `sonnet-w5-11`, `sonnet-w5-12` | « 30 jours » affiché ; livraison | sélecteur à trois groupes (w16-11) ; relevé d'usage à chaque contact |
| `sonnet-w10-02` | `loc-oeuvre-<id>\|30`, `-7j\|7` | l'article porte `\|<N>j` **ou** `\|<N>h` ; `-7j` inutile si `userChosen = 1` |
| `sonnet-w10-13` | « ne pas contourner la durée du catalogue » | `livrer.py` accepte `--choix` via `louer.py` (w16-05 factorise `rentalops`) |
| `sonnet-w12-01`, `sonnet-w12-08` | `rental.defaultDays` seul | 12 clés du § 4.1 déclarées dès la tranche 1 ; émetteurs lisent `userChosen`, `pickerDays`, `maxDays`, `hourly.*`, `pilot.*` |
| `docs/RENTAL-LOTS.md` § 14 | « exacte » | § 17 (w16-12) : « exacte » = `userChosen = 0` ; deux unités ; pilote |

## 5. Plan de test (3 semaines, gratuit) : la location à l'heure est l'objet du test

### 5.1 Hypothèses, métriques, seuils
| # | Hypothèse | Métrique (source) | Go | No-go |
|---|---|---|---|---|
| HP1 | Les foyers prennent l'initiative de louer | % de foyers avec ≥ 1 location en 3 semaines (registre / `pilot_rental_order`) | ≥ 60 % | < 30 % |
| **HP2** | **L'heure a sa place face aux jours et au défaut** | part des locations **heures / jours / défaut** (à l'émission) ; part des **foyers** ayant choisi au moins une fois l'heure | ≥ 30 % des locations à l'heure | < 10 % (le sélecteur horaire n'apporte rien : vendre en jours) |
| **HP3** | **Les heures louées sont utilisées** | médiane `used / max` des locations à l'heure terminées ou au 15/11 (relevés) | ≥ 50 % | < 25 % (sur-estimation : vendre des petits paliers ou au forfait) |
| HP4 | Durées choisies | répartition des paliers heures (1…96) et jours (1…14) ; médianes | informatif : oriente les paliers payants |
| HP5 | Intensité d'usage des locations en jours (comparaison) | minutes d'usage / jour de validité pour jours et défaut (relevés, sans plafond) vs heures | informatif : « 7 jours » = combien d'heures réelles ? |
| HP6 | Pas d'abandon après prise | % de contrats avec 0 min après 7 jours | ≤ 20 % | > 40 % |
| HP7 | Relocation / prolongation | % de foyers avec ≥ 1 prolongation ou 2ᵉ location ; part des prolongations horaires | ≥ 30 % | < 10 % |
| HP8 | Intention de payer (par unité) | question facultative en fin de location (§ 5.2) : « auriez-vous payé ? » oui / non / autre prix, par unité | ≥ 40 % « oui » pour l'heure | < 20 % |
| HP9 | Abus / partage / compteur | % de contrats horaires à 96 h ; `OVER_LIMIT` ; réémissions ; écart `used` TV vs relevé ≤ 5 % ; incidents `SUSPENDED`/`degraded`/clé ≤ 2 | ≤ 10 % à 96 h ; ≤ 1 réémission/TV ; vert | > 25 % ; > 5 incidents |
| HP10 | Charge d'assistance | tickets / foyer / semaine ; temps du propriétaire | ≤ 0,5 ; ≤ 4 h/sem | > 1,5 ; > 10 h |
**Décision** : **GO vers la location payante à l'heure** = HP2, HP3, HP8, HP9 vertes et aucune rouge ; **AJUSTER** (paliers, second pilote) = une rouge hors HP8 ; **NO-GO sur l'heure** (garder les jours) = HP2 rouge **ou** HP8 rouge **ou** deux rouges. HP4/HP5 orientent les **paliers et le modèle de prix** (à l'heure, en jours, les deux).

### 5.2 Mesures, consentement, hors ligne
- **Télémétrie** (catégorie **usage**, consentement « statistiques » `docs/TELEMETRY.md:19-32`, liste blanche par événement, aucune clé interdite `:55-58`) : `rental_start{bundle, unit = hours|days|default, amount, maxMinutes}`, `rental_use{bundle, unit, minutes}` (**agrégé par jour et par contrat**), `rental_end{bundle, unit, reason = usage|date|over_limit, usedMinutes, maxMinutes}`, `rental_extend{bundle, unit, amount}`, `rental_survey{unit, q, answer}` (sans contrat). `bundle` = identifiant public de catalogue. Propriété `exp` (W12) jointe quand elle existe.
- **Relevés hors ligne** (exécution du contrat, pas une statistique : sans consentement, aucune donnée personnelle) : `castbridge-rental-usage-v1` relevé par le téléphone à chaque contact ; remis au serveur (tranche 2) ou au propriétaire (tranche 1). Une TV jamais rejointe ne remonte rien ; le registre d'émission garde ce qui a été loué.
- **Question de fin de location** : facultative, un écran, « oui / non / à un autre prix / ne pas répondre », texte : « Ce test est gratuit. Si ces 12 heures d'utilisation avaient coûté [palier], les auriez-vous prises ? » (paliers **sans montant** tant que D9-bis est BLOQUÉ) ; consentement = répondre (une ligne d'information au-dessus) ; une fois par contrat ; **jamais sous profil enfant**.
- **Fiches** (point focal) : journal des demandes (date, code TV masqué, bouquet, unité, quantité), incidents, questionnaire S3 (5 questions orales, numéro de fiche sans nom).

### 5.3 Comment le propriétaire lit les résultats
- **Tranche 1** : `tools/pilot/bilan.py --registre pilot-rentals.csv --releves releves/ [--telemetrie export.csv]` ⇒ `BILAN-PILOTE-LOCATION-DUREE.md` (HP1-HP10 avec seuils, parts heures/jours/défaut, paliers, `used/max`, intensité jours) + CSV.
- **Tranche 2** : `/admin/pilot/rentals` (TOTP) : par contrat (bouquet, unité, quantité, utilisées, état, dernier relevé), KPI, CSV ; `GET /api/v1/admin/kpi/pilot`.

### 5.4 Cohortes
15-25 foyers (W10 § 10.6), 2 points focaux ; **cohorte A** : Apprendre (tous) ; **cohorte B** : + œuvres si W10 minimal est sur la TV avant S2. Pas d'A/B (gratuit) ; **séquentiel** sur le sélecteur : S1-S2 trois groupes complets, S3 chez un point focal : heures réduites à 6 / 24 / 96 et jours à 7 (D-W12-5). Enfants : utilisent, ne commandent pas.

### 5.5 Calendrier
| Sem. | Dates | Quoi |
|---|---|---|
| S0 | 05-11/10 | décisions D-W16-1…12 ; tranche 1 fusionnée ; **build TV verrouillé** avec le cœur W16 (D-W16-9) dans `Download` de la clé ; `pilot.json` ; kit papier ; 15-25 foyers ; consentement télémétrie demandé |
| S1 | 12-18/10 | ouverture ; relevés hebdomadaires |
| S2 | 19-25/10 | prolongations, relocations ; entretien à mi-parcours (5 foyers) |
| S3 | 26/10-01/11 | sélecteur réduit chez un point focal ; **dernières émissions le 01/11** ; questionnaire |
| S4 | 02-16/11 | les heures non utilisées s'éteignent le **15/11** ; relevés finaux ; **bilan horaire le 16/11** (objet du test) ; décision GO / AJUSTER / NO-GO |
| S5-S6 | 17/11-02/12 | les locations en jours s'éteignent (≤ 01/12) ; complément « intensité jours » le 02/12 |

### 5.6 Risques
| Risque | Mesure | Résiduel |
|---|---|---|
| **Confusion heures / jours** | libellés § 1.3, aide d'une ligne, date de fin réelle, LISEZMOI, fiche ; HP8 entretien | un foyer surpris par la fin par usage |
| **TV partagée entre foyers** | 3 simultanées, quota, HP9 | mesuré, pas empêché |
| **Thésaurisation** (tout louer 30 jours) | ≤ 3 actifs par licence, un contrat par bouquet | — |
| **Défaut dépassant le pilote** | D-W16-5 (honorer, borne 01/12) ; sinon troncature au 15/11 | bilan en deux temps |
| **Enfants** | jamais de question sous profil enfant ; parent commande | — |
| **Langues confondu avec louable** | « Gratuit », pas de sélecteur, refus moteur | — |
| **Horloge / Keystore** | règles existantes (`SUSPENDED`, `installKeyProtection`) ; fiche ; HP9 | TV sans horloge de secours : suspension |
| **Compteur biaisé par la TV allumée** | inactivité 30 min, pause 5 min | œuvres (lecture longue) : seuils à revoir |
| **Gel W15** | tranche 1 = cœur + bureau ; APK TV = D-W16-9 | 1 location par TV, écran existant |
| **Œuvres absentes** | pilote Apprendre | BLOQUÉ calendrier |

## 6. Ce qu'on construit d'abord, et le gel

### 6.1 Tranche 1 : pilote jouable avec le module licences éteint
**Cœur** (JVM, autorisé pendant le gel) : w16-01 moteur (unité, clamp, phrases, alertes relatives, badge) · w16-02 `UseMeter` + carnet (relevé) · w16-03 routes/vue/relevé (`RentalApi` JSON additif, `RentalDelivery`, `RentalUsageReport`) · w16-04 `PilotRules` + émission (`RightsSyntax`, `RentalDurations` mode pilote) · w16-06 vecteurs + **audit Opus** · w16-07 télémétrie (cœur + serveur + doc). **Bureau / Python** : w16-05. **Docs / kit / campagne** : w16-12, 13, 14 (haiku). ≈ **12,5 j**.
**Ce que ça permet** : émettre et livrer les trois choix à toute TV du pilote avec l'écran téléphone **existant** (« Activer la TV » puis « Locations sur la TV ») ou le relais ; la TV **rebâtie avec le cœur W16** (D-W16-9) compte l'usage (compteur actuel : juste avec **une** location plafonnée à la fois ⇒ `maxConcurrent = 1` en tranche 1 ; **les locations en jours ne sont pas mesurées** avant w16-10, puisque `meterOneMinute` ignore les contrats sans plafond), l'affiche (phrases nouvelles du cœur), l'expose ; le propriétaire relève et calcule le bilan.
**Ce que ça ne permet pas** : plusieurs locations en parallèle avec compte juste, la mesure d'intensité des locations en jours (HP5), le Quiz loué compté, l'écran « Louer » sur le téléphone, le relevé automatique, la question d'intention à l'écran (posée par le point focal, fiche).

### 6.2 Tranche 2 : après la sortie du gel (plan § 5 `:397-407`)
w16-10 TV (compteur par lot réel pour tout lot loué, Quiz, phrases, question, télémétrie) · w16-11 téléphone (sélecteur, relevé automatique, file, client serveur) ; **serveur** w16-08 / w16-09 (autorisés pendant le gel ; module **éteint** en production : pilote payant, préproduction). ≈ **9,5 j**.
**Interaction avec W14/W15** : R5 ⇒ **aucun** cahier W16 ne touche `S/`, `R/` avant la sortie ; `R/RentalHub.kt` appartient à w15-17 (zone `TvClock`, `SONNET-WAVE15-INDEX.md:80`) ⇒ w16-10 **après** w15-17 ; `C/lots/DeliveryQueue.kt`, `S/LotsRuntime.kt` (zone `hasWork`) à w15-16 ⇒ w16-11 après ; `C/owner/Keys.kt` (`TvClock`) à w15-17 : W16 **ne le touche pas** (le compteur reçoit `nowMono` en paramètre). Parcours J (W14) pour w16-10/11 : « louer 1 heure d'utilisation, utiliser 61 min, fin par usage », « 7 jours puis horloge +8 j, fin par date », « 12 heures, borne 15/11 atteinte avec 5 h restantes, fin par date ».

### 6.3 Effort et coût (jauges estimées ; prix 2026-09 des index : haiku 1/5, sonnet 2/10, opus 4/20 $/M)
| Sous-vague | Cahiers | Effort | Coût ≈ |
|---|---|---|---|
| 16a cœur | w16-01, 02, 03, 04, 06, 07 | ≈ 8 j | 6 $ + 3 audits Opus ≈ 2,5 $ |
| 16b bureau, Python, docs, kit, campagne | w16-05, 12, 13, 14 | ≈ 4,5 j | 1,5 $ |
| 16c serveur | w16-08, 09 | ≈ 4,5 j | 2 $ + 1 audit ≈ 0,8 $ |
| 16d TV, téléphone (après le gel) | w16-10, 11 | ≈ 5 j | 2 $ + 1 audit ≈ 0,8 $ |
| **Total** | 14 | **≈ 22 j** | **≈ 15-16 $** |

## 7. Décisions

### 7.1 Décisions du propriétaire (recommandation appliquée par défaut ; BLOQUÉ = fait réel manquant)
| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W16-1** | Sélecteur : « Sans durée précise : 30 jours » + jours 1/3/7/14 + heures d'utilisation 1/3/6/12/24/48/96 ? | w16-04/05/11 | **oui** |
| **D-W16-2** | Une location en jours (choisie ou défaut) n'a **aucun** budget d'usage ; seule la location à l'heure est plafonnée (96 h) ; les minutes sont comptées partout comme **mesure** ? | tout | **oui** (lecture (e) du propriétaire) |
| **D-W16-3** | Un seul contrat par bouquet à la fois ; prolonger = même unité ; changer d'unité = après la fin (ou **remplacer** : fin anticipée du contrat en cours, nouvelle `period`) ? | w16-04 | **après la fin** au pilote ; « remplacer » au pilote payant si HP7 le demande |
| **D-W16-4** | 3 utilisables par TV, 3 actifs par licence, quota horaire 192 h / 7 j, `cooldownMin = 0` (1 par TV en tranche 1) ? | w16-04, w16-01 | **oui** |
| **D-W16-5** | **Borne absolue** : heures à utiliser avant le **15/11** (fin + 14 j) ; jours et défaut **honorés en entier** (≤ 01/12) **ou** tout tronqué au 15/11 (le sélecteur montre la vraie date) ? | `pilot.json`, calendrier | **honorer** (le défaut est le produit existant ; tronquer biaise HP2) ; bilan horaire le 16/11, complément jours le 02/12 |
| **D-W16-6** | Périmètre : Apprendre seul ; œuvres si W10 minimal sort avant S2 ? | cohorte B | **Apprendre seul** (**BLOQUÉ** calendrier : W10 TV attend la sortie du gel) |
| **D-W16-7** | Ce qui compte : contenu ouvert + premier plan ; pause > 5 min et inactivité > 30 min arrêtent ; profils enfant comptent ? | w16-02, w16-10 | **oui** |
| **D-W16-8** | Question d'intention en fin de location, facultative, par unité, paliers **sans montant** tant que D9-bis est BLOQUÉ ? | w16-10, w16-13 | **oui** ; montants dès décision |
| **D-W16-9** | Livrer un **build TV verrouillé** avec le cœur W16 pendant le gel (cœur seul, aucun fichier `R/`), copié dans `Download` de la clé ? | S0 | **oui** après `:core:test` complet + `compileDebugKotlin` + liste humaine 12/12 ; sans lui, ni phrases ni relevé |
| **D-W16-10** | Serveur : rien pendant le pilote (licences éteint) ; w16-08/09 pour le pilote **payant** ? | w16-08 | **oui** |
| **D-W16-11** | Télémétrie : 5 événements « usage » avec consentement ; relevé = exécution du contrat (sans consentement, sans personne) ? | w16-07 | **oui** |
| **D-W16-12** | Après le pilote : `rental.userChosen` revient à 0 (exacte) ou reste à 1 ? | suite | **au bilan** ; défaut compilé hors pilote = 0 |

### 7.2 Questions de produit ouvertes par les deux unités et le défaut (recommandation)
1. **La durée de 30 jours dépasse la fin du pilote** : borne absolue ? → **honorer** la durée choisie (≤ 01/12), heures bornées au 15/11 (D-W16-5) ; alternative : tout au 15/11 avec date réelle affichée.
2. **Jours : avec ou sans budget d'usage ?** → **sans** (nature « validité », même famille que le défaut ; un budget caché transformerait « 7 jours » en « 7 jours ou X h » et brouillerait la mesure HP5). Si le propriétaire veut un garde-fou, un budget **de mesure seulement** n'a pas de sens ; un vrai plafond ferait des jours une troisième unité hybride : **refusé**.
3. **Plafond des jours** → **30** (= le défaut), paliers 1/3/7/14 ; 60 j (W5) reste la borne du réglage.
4. **96 h au sélecteur ou seulement le plafond ?** → **au sélecteur** (le défaut n'est plus 96 h : aucun recouvrement) ; HP4 dira s'il sert.
5. **Peut-on cumuler jours et heures sur un même bouquet ?** → **non** (un contrat par bouquet) ; changer d'unité après la fin ; « remplacer » au pilote payant (D-W16-3).
6. **Que mettre en avant pour une location à l'heure : les heures restantes ou la date de sûreté ?** → **les heures** ; la date n'apparaît que comme « à utiliser avant le … » (§ 1.3).
7. **Faut-il une « 1 journée » = 24 heures d'utilisation ?** → **non** : 1 jour (validité) et 24 heures d'utilisation existent déjà, distincts ; une « journée d'utilisation » réintroduirait la conversion interdite.
8. **Bouquet avec `rentalDays` propre** (catalogue, aujourd'hui vide) → le défaut prend `rentalDays` s'il existe, sinon 30 (hiérarchie `RentalDurations.daysOf` conservée) ; les paliers en jours sont bornés par `min(rental.maxDays, rentalDays)`.
9. **Le défaut est-il « par omission » ?** → non : le sélecteur s'ouvre toujours, le défaut est **présélectionné et nommé** ; HP2 distingue un choix d'une omission.
10. **Tarification future** (forfait jours / à l'heure / les deux) → HP2-HP5-HP8 répondent ; **ne pas décider avant le bilan** ; D9-bis bloque les montants.

## 8. Ancrages vérifiés pour les exécutants
Moteur `C/lots/RentalEngine.kt:25-34` (`RentalConfig`), `:37-42` (`RentalContract`), `:84-100` (`contracts`, somme :95), `:107-132` (`evaluate`), `:142-155` (alertes), `:158-173` (phrases) · carnet `C/lots/RentalLedger.kt:122-128` (`markRented`), `:137-148` (`recordUsage`), `:170-218` (chargement, `save`) · lignes `C/lots/RentalLines.kt:5, 11-14, 21-23, 28, 32, 44-55` · durées `C/lots/RentalDurations.kt:10-43` · clés `C/lots/RentalKeys.kt:27-34, 41-46, 102-133` · routes `C/lots/RentalApi.kt:9-20, 27-48, 62-72` · livraison `C/lots/RentalDelivery.kt:62-70, 96-119` · balayage `C/lots/RentalSweeper.kt:18, 41, 53-92` · politique `C/lots/RentalPolicy.kt:23-28, 34-37` · droit `C/lots/Entitlement.kt:42-45` · émission `C/owner/LicensedIssuer.kt:9-19, 26-34, 41, 50-62, 87-95, 140-151` · vérification `C/owner/Activation.kt:121-131, 152, 187-208` · horloge `C/owner/Keys.kt:105-165` · badge `C/owner/KeyBadge.kt:41` · TV `R/RentalHub.kt:42, 122-129, 135-144, 153-164, 170-176`, `R/LearnActivity.kt:89-92`, `R/TvService.kt:187, 221, 259`, `R/ActivationCenter.kt:91, 108` · téléphone `S/RentalDeliveryActivity.kt:55-89` · bureau `DK/Cli.kt:87-95, 246-275, 312-325, 406-413` · serveur `application.yml:112-114`, `B/licenses/LicenseProperties.java`, `B/licenses/WireActivation.java:34-44` · télémétrie `C/telemetry/Telemetry.kt:23-25, 58, 164-165`, `B/telemetry/EventCatalog.java` (lignes **non vérifiées**), `docs/TELEMETRY.md:19-32, 55-58, 66-89` · tests `CT/lots/{RentalTest,TrialWindowTest,RentalDeliveryTest,RentalApiTest,RentalDurationsTest,RentalVectorsV2Test}.kt`, `tools/activation-desktop/src/test/.../RentalCliTest.kt` · outils `tools/rental-test/{README.md,rental_test.py}` · docs `docs/RENTAL-LOTS.md` § 1-6, 11, 13-16 ; `DESIGN-W4-ENVELOPPE-LOCATIONS.md:42` ; `DESIGN-W5:173-217` ; `DESIGN-W10:300-371` ; `DESIGN-W12:44-50, 147, 291-312` ; `PLAN-STABILISATION:397-422` ; `SONNET-WAVE15-INDEX.md:7, 80, 86`.

**Non vérifié / BLOQUÉ** : clé publique **serveur** dans `TRUSTED_KEYS` des TV (tranche 2) ; lignes de `B/telemetry/EventCatalog.java` ; le chemin « Activer la TV » du téléphone pour un fichier `activation` reçu par messagerie (`C/owner/ActivationSend.kt` existe ; écran non relu) ; `content/TRIAL-MANIFEST.json` (identifiants exacts des bouquets de classe, `classe-cm2` supposé d'après W10 § 2.2) ; montants (D9-bis) ; W10 non fusionné (aucun `WorkHub`) ; état réel du gel au lancement (HANDOFF § 0).
