# Vague 22 — Jetons virtuels NDEM et MBOKO : grand livre serveur, attributions par édition, mises en ligne TV à TV, conversion dans les deux sens, transferts, bons hors ligne

<!-- routage architecte 2026-10-04 -->

**Source** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md`. Branche de référence `integration/agents` (HEAD `5696385e`). Exécution sur ordre du coordinateur seulement.

**Spécification du propriétaire (2026-10-04, verbatim)** : « Afin d'animer et dynamiser l'intérêt du système, on va introduire un compte de jetons virtuels comme dans les jeux en ligne avec 2 types (MBOKO, et NDEM). Le NDEM servira aux parties en ligne sans enjeu. Le MBOKO servira aux parties en ligne avec enjeu. Un essai offrira 100 NDEM x nombre de mois. Une production 1000 NDEM x nombre de mois. L'illimité offrira un crédit automatique de 5000 NDEM à l'ouverture et 1000 NDEM/mois. En production exclusivement, on aura 10 MBOKO x nbre de mois, ou 50 MBOKO avec 10 MBOKO/mois. La plateforme propose 1000 NDEM pour un MBOKO en automatique, mais aussi des transferts libres entre compte TV. La plateforme rémunérera en MBOKO ou en NDEM en fonction des politiques futures. Les jetons serviront aussi pour les jeux d'échecs. Les jetons pourraient s'activer offline par voucher aussi ». Puis : « Ignore le risque juridique jusqu'au 1/1/2027 » ; « Je teste d'abord la faisabilité technique » ; « Le joueur décide de convertir son jeton de NDEM à MBOKO librement » ; « Je veux aussi MBOKO vers NDEM ».

**Règles de la vague** : **J1** le grand livre est tenu par l'API principale, à double entrée, écritures immuables, idempotence, aucun solde de joueur négatif, conservation par monnaie ; **J2** la TV ne crée jamais de jeton (cache signé `cbw1`, bons en attente confirmés par le serveur) ; **J3** `castbridge-play` ne reçoit aucun identifiant du grand livre : il vérifie `cbe1` par clé publique et signe `cbr1` avec sa clé dédiée ; **J4** compte = identité de la TV (activation `cbx1`), téléphones jamais clients de l'API portefeuille ni du service de jeu ; **J5** MBOKO : attribué et misé en production seulement (détention et réception permises à tous) ; **J6** conversion à la demande dans les deux sens, taux et frais dans la table de politique (symétrie, 0 % au lancement) ; **J7** interrupteurs d'exploitation actifs par défaut ; **J8** aucun retrait, aucun achat direct de jetons (règle de produit) ; **J9** tests de propriétés sans dépendance nouvelle ; audit Opus obligatoire sur tout ce qui touche à la valeur.

## Niveau 1 — POC de faisabilité technique (priorité absolue)

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w22-01 | `sonnet-w22-01-grand-livre-coeur-java.md` | cœur pur Java : transactions à double entrée, attributions, conversion deux sens, transfert, blocage/règlement, invariants (propriétés) | serveur | M | sonnet | **oui** | 400 / 20 | **FAIT** (`claude/w22-01-grand-livre-coeur`, rapport `docs/agent-reports/sonnet-w22-01.md` ; audit Opus à faire) | — |
| w22-03 | `sonnet-w22-03-formats-signes-cache-tv.md` | cœur Kotlin : `cbw1`, `cbe1`, `cbr1`, `cbv1`, cache TV, vecteurs | cœur | M | sonnet | **oui** | 400 / 20 | **FAIT, audit Opus à lancer** (`claude/w22-03-formats-signes`, rapport `docs/agent-reports/sonnet-w22-03.md`) | — |
| w22-02 | `sonnet-w22-02-serveur-grand-livre-v63-attributions.md` | migration V62 (ex-V63), `JdbcLedger`, édition lue dans `cbx1`, tranches paresseuses, `sync`, historique, `cbw1`, politique | serveur | L | sonnet | **oui** | 600 / 30 | **FAIT, audit Opus à lancer** (branche `worktree-agent-afab6c875088f3962`, rapport `docs/agent-reports/sonnet-w22-02.md` ; MySQL non exécuté) | 01 |
| w22-04 | `sonnet-w22-04-service-jeu-mises-resultat-signe.md` | salles misées, `cbe1` vérifié, `Pot.split` par siège, `cbr1` signé, dépôt, drain ⇒ ABORT | cœur + service | M | sonnet | **oui** | 450 / 25 | ATTEND 03 + w20-04b (ordre 2 bis) | 03, w20-04b |
| w22-05 | `sonnet-w22-05-api-blocage-reglement-conversion-transfert.md` | blocage, règlement, rendus échus, conversion deux sens, codes de réception, transfert, collecteur | serveur | M | sonnet | **oui** | 450 / 25 | **FAIT, audit Opus à lancer** (branche `worktree-agent-a96099f0d35ece410`, rapport `docs/agent-reports/sonnet-w22-05.md` ; MySQL non exécuté ; règlement testé sur des `cbr1` de test, fixtures réelles de w22-04 à rejouer) | 02, 03 (04 pour les fixtures réelles) |
| w22-06 | `sonnet-w22-06-bons-hors-ligne.md` | outil des bons (clé hors ligne), import, confirmation à `sync`, rachat hors ligne sur la TV | serveur + cœur + outil | M | sonnet | **oui** | 400 / 20 | ATTEND 02, 03 (ordre 3 bis) | 02, 03 |
| w22-07 | `sonnet-w22-07-tv-telephone-portefeuille-poc.md` | carte, « Mes jetons », convertir (deux sens), transférer, recevoir, bon, choix de mise, page téléphone | **exception de gel** (D-W22-13) | M | sonnet | échantillon | 450 / 25 | ATTEND 03, 05, 06, w20-05 POC (ordre 4) ; gel : **DÉCIDÉ** (D-W22-13) | 03, 05, 06 |
| w22-08 | `sonnet-w22-08-demo-exploitation-livraison.md` | `docs/WALLET*.md`, clés, variables, collecteur, `server-1.x`, démonstration 15 min | docs + script | S | **haiku** | — | 150 / 15 | ATTEND 01-07 (ordre 5) | 01-07 |

**Chemin critique** : `w22-01 ∥ w22-03` → `w22-02 ∥ w22-04` → `w22-05 ∥ w22-06` → `w22-07` → `w22-08` → actes du propriétaire (clés, `deploy-server.sh server-1.x --apply`, module allumé, cron du collecteur, lot de bons importé, APK verrouillés avec `quiz.online` copiés dans le `Download` de la clé USB) → démonstration (`docs/WALLET-DEMO.md`). **≈ 7 jours ouvrés** si les paires partent ensemble et si D-W22-13 est accordée.

**Première valeur** : après w22-01 + w22-02 (≈ 3 j), le grand livre NDEM tourne au serveur et attribue les tranches aux TV qui se synchronisent ; après w22-04 + w22-05, les parties misées NDEM ; MBOKO emprunte le même chemin (une règle de plus).

**Coût du niveau 1** (prix des index précédents, **non vérifiés** : sonnet 2/10 $/M, opus 4/20 $/M, haiku 0,8/4 $/M) : sonnet 6 × M ≈ 6,0 $ + 1 × L ≈ 1,6 $ ; haiku ≈ 0,15 $ ; audits Opus 6 × 0,8 $ + 1 échantillon 0,4 $ ≈ 5,2 $ ; reprises 15 % ≈ 1,9 $ ⇒ **≈ 15 $**, ≈ **12 agent·jours**.

## Niveau 2 — produit complet (après le test de faisabilité)

| id | Cahier | Objet | Effort | Modèle | Audit Opus | Jauge | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|
| w22-09 | `sonnet-w22-09-anti-abus-bonus-plafonds-velocite.md` | poche BONUS (étiquette qui suit la conversion), plafonds deux sens, transferts, perte nette, alertes | M | sonnet | **oui** | 450 / 25 | ATTEND N1 + D-W22-6 | N1 |
| w22-10 | `sonnet-w22-10-administration-gel-reprise-reconciliation.md` | gel, reprise, réaffectation, réconciliation nocturne, clôture, archivage, pages | M | sonnet | **oui** | 450 / 25 | ATTEND N1 | N1 |
| w22-11 | `sonnet-w22-11-bons-detail-code-court.md` | codes à gratter par empreinte, lots par revendeur, anti-force brute | M | sonnet | **oui** | 400 / 20 | ATTEND 06 | 06 |
| w22-12 | `sonnet-w22-12-echecs-en-ligne-mises.md` | échecs en ligne TV à TV arbitrés par le service, mises | L | sonnet | **oui** | 700 / 35 | ATTEND N1 + D-W22-10 | N1 |
| w22-13 | `sonnet-w22-13-telemetrie-remuneration.md` | `kpi_wallet_daily`, `wallet_error`, table des rémunérations (inactives) | S | sonnet | échantillon | 300 / 15 | ATTEND 09 + W21 | 09, W21 |
| w22-14 | `sonnet-w22-14-liaison-cle-installation.md` | opérations sortantes signées par la clé d'installation | M | sonnet | **oui** | 400 / 20 | ATTEND N1 + D-W22-7 | N1 |

**Coût du niveau 2** (estimé) : sonnet 4 × M + 1 × L + 1 × S ≈ 6,1 $ ; audits 5 × 0,8 + 1 × 0,4 ≈ 4,4 $ ; reprises ≈ 1,6 $ ⇒ **≈ 12 $**, ≈ 11 agent·jours.

## Défi des 10 000 (spécification ajoutée le 2026-10-04 ; conception § 13)

« Il existe aussi un quiz à la logique de « Qui veut gagner des millions » pour encaisser 10 000 NDEM, pour exclusivement remporter des jetons en mode solo. La mise est de 500 NDEM. Il est chargé et géré en offline par le serveur à toutes les TV en production. Pour rappel, l'erreur vaut fin de la partie. 15 questions pour rafler la mise maximale, seul joker le 50:50. Possibilité de ne plus continuer après 5 questions, 8, 10, 13 et emporter les gains affichés. » Nouveau mode solo, **distinct** du Millionnaire local. Échelle recommandée **B** : 50, 100, 200, 300, **500**, 700, 900, **1 200**, 1 600, **2 000**, 2 700, 3 600, **5 000**, 7 000, 10 000 (rendement **≈ 0,8× à 1,1×** selon le modèle du 50:50, taux **supposés** ; 0,5× à 1,5× à ± 5 points). La TV ne crédite rien : journal signé, crédit par le serveur après vérification. **Plafonds du propriétaire** : au plus **3 parties gagnées par jour, 10 par semaine, 15 par mois** (« 15 mois » lu « 15 par mois »), par identité, fenêtres calendaires Africa/Douala, compteur scellé hors ligne revérifié par le serveur, mode fermé avant la mise ; « gagnée » = gain > mise (recommandé) ; garde-fou distinct 30 000 NDEM de gains par mois.

| id | Cahier | Objet | Niveau | Effort | Modèle | Audit Opus | Jauge | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w22-15 | `sonnet-w22-15-defi-10000-coeur.md` | cœur pur : échelle, état de partie, paliers, 50:50, pack `cbk1`, journal `cbm1`, disponible hors ligne, simulateur de rendement | 1 (parallèle, hors chemin critique) | M | sonnet | **oui** | 350 / 20 | **FAIT, audit Opus à lancer** (`worktree-agent-a9f9fa3119f875ea1`, rapport `docs/agent-reports/sonnet-w22-15.md`) | 03 |
| w22-16 | `sonnet-w22-16-defi-10000-serveur.md` | pool « défi », packs par identité, vérification des journaux, `MILLIONS_*`, plafonds, anomalies, rendement, recalage | 2 | L | sonnet | **oui** | 600 / 30 | ATTEND 02, 05, 15 + D-W22-16, 19, 24 (17, 18, 20, 22, 23 DÉCIDÉES) | 02, 05, 15 |
| w22-17 | `sonnet-w22-17-defi-10000-tv.md` | écran TV, moteur local, reprise, gains en attente (exception de gel) | 2 | M | sonnet | **oui** | 400 / 20 | ATTEND 07, 15, 16 | 07, 15, 16 |

**Coût du Défi** (estimé, non inclus dans les totaux ci-dessus) : 2 × M + 1 × L ≈ 3,6 $ ; 3 audits ≈ 2,4 $ ; reprises ≈ 0,9 $ ⇒ **≈ 7 $**, ≈ 5,5 agent·jours. Le tirer entièrement au niveau 1 (D-W22-16) : ≈ +2 jours ouvrés sur le chemin critique.

## Décisions tranchées par le propriétaire (2026-10-04) — statut DÉCIDÉ
**D-W22-1** OUI (10 MBOKO/mois par tranches ; illimitée 50 MBOKO à l'ouverture puis 10/mois) · **D-W22-14** OUI (frais MBOKO → NDEM 0 %, réglables) · **D-W22-11** OUI (jetons W5 et points de défi distincts) · **D-W22-13** OUI (exception de gel de l'écran portefeuille TV : w22-07 n'attend plus que ses dépendances) · **D-W22-17** OUI (Défi : erreur = 0) · **D-W22-18** OUI (échelle B', rendement visé ≤ 0,9 réglable) · **D-W22-23** OUI (gagnée = gain > mise) · **D-W22-22** OUI (3 / jour, 10 / semaine, 15 / mois) · **D-W22-20** OUI (`maxGainMonth` 30 000 NDEM / mois / TV).

**Précision « licences »** : « Les licences sont différentes des clés d'activation (Essai & Production). Les licences sont gérées exclusivement par le serveur pour les clés activées de production ». Essai : lu dans `cbx1` ; production (durée, illimitée, grâce, révocation, MBOKO, Défi) : **licence** du serveur (`LicenseFacts`, w22-02), `cbx1` = identité. **Prérequis de démonstration** : module des licences allumé (`CASTBRIDGE_LICENSES_ENABLED`, éteint par défaut) et licences des TV de démonstration enregistrées ; sinon `LICENSE_PENDING` et seules les tranches d'essai se montrent. Cahiers touchés : w22-01 (motif), w22-02 (`LicenseFacts`, 10 points d'impact), w22-05 (blocage MBOKO), w22-16 (packs et crédits, 10 points d'impact).

**Règle anti-triche ajoutée** : « Trop de gain linéaire lance l'alerte administrateur » ⇒ M-8 (conception § 13.3) dans w22-16 : courbe de gains trop régulière sur 20 parties ⇒ alerte, gains suivants en revue, aucune coupure automatique.

## Décisions du propriétaire encore ouvertes
Défi des 10 000 : D-W22-16 (niveau 2), D-W22-19 (crédit en attente vérifié par le serveur), D-W22-21 (nom « Défi des 10 000 »), D-W22-24 (fenêtres calendaires, vérification au début, gain hors plafond refusé avec mise rendue) : § 13.8 de la conception.

D-W22-2 (tranches mensuelles), D-W22-3 (« sans enjeu » = cagnotte NDEM, jeu sans mise gratuit), D-W22-4 (super illimité sans attribution), D-W22-5 (mise par siège), D-W22-6 (anti-ferme niveau 2), D-W22-7 (liaison d'appareil), D-W22-8 (bons : clé hors ligne + lot importé, MBOKO ciblé), D-W22-9 (option B), D-W22-10 (échecs niveau 2), D-W22-12 (bornes), D-W22-15 (essai peut détenir/recevoir des MBOKO). Recommandations : § 10 de la conception. **Aucune ne bloque w22-01, w22-03.**

## Faits vérifiés le 2026-10-04
- Le serveur Java ne dépend pas du cœur Kotlin (`backend/pom.xml`) ; le service de jeu, si (`server-play/build.gradle.kts:11`).
- L'API ne connaît pas l'édition (`backend/src/main/java/castbridge/server/play/PlayTicketService.java:24-25`) ; le module des licences est éteint par défaut (`backend/src/main/resources/application.yml:117`) ; le service de jeu lit l'édition dans `cbx1` (`server-play/src/main/kotlin/castbridge/play/entitlement/HostRights.kt`).
- Migrations fusionnées jusqu'à V61 ; V62 = grand livre (w22-02). **Numérotation des migrations (règle unique, 2026-10-04) : AUCUN numéro n'est réservé.** Chaque vague prend « plus haut numéro existant + 1 » AU MOMENT DE SA FUSION (`git log --all -- backend/src/main/resources/db/migration`) et le dit au rapport ; un numéro cité dans un cahier est une indication de rédaction, jamais une réservation (une V62 arrivée après une V63 déjà appliquée bloquerait le démarrage : Flyway `out-of-order=false`, audit Opus de w22-02, H4). Aujourd'hui : V62 = grand livre W22 (w22-02, renumérotée depuis V63 avant tout déploiement).
- Tests serveur : H2 en mode MySQL (`backend/src/test/resources/application-test.yml`) et MySQL 8.4 par Testcontainers (`MySqlContainerTest.java`).
- `Pot.split` existe (`android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt:54-85`) ; échecs en ligne sans serveur (`docs/CHESS.md` § 6).

## Lien avec la vague 23
Suivi des **codes d'activation** (demande du propriétaire du 2026-10-04, précisée « codes d'activation d'essai et de production ») : `SONNET-WAVE23-INDEX.md`, conception `DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md` ; aucun suivi de soldes. Migration de w23-01 (rédigée V65) : w22-10, w22-16 et w23-01 prennent chacun « plus haut + 1 » à la fusion (aucun numéro réservé).
