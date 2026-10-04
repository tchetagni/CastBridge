# Vague 22 — Jetons virtuels NDEM et MBOKO : grand livre serveur, attributions par édition, mises en ligne TV à TV, conversion dans les deux sens, transferts, bons hors ligne

<!-- routage architecte 2026-10-04 -->

**Source** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md`. Branche de référence `integration/agents` (HEAD `5696385e`). Exécution sur ordre du coordinateur seulement.

**Spécification du propriétaire (2026-10-04, verbatim)** : « Afin d'animer et dynamiser l'intérêt du système, on va introduire un compte de jetons virtuels comme dans les jeux en ligne avec 2 types (MBOKO, et NDEM). Le NDEM servira aux parties en ligne sans enjeu. Le MBOKO servira aux parties en ligne avec enjeu. Un essai offrira 100 NDEM x nombre de mois. Une production 1000 NDEM x nombre de mois. L'illimité offrira un crédit automatique de 5000 NDEM à l'ouverture et 1000 NDEM/mois. En production exclusivement, on aura 10 MBOKO x nbre de mois, ou 50 MBOKO avec 10 MBOKO/mois. La plateforme propose 1000 NDEM pour un MBOKO en automatique, mais aussi des transferts libres entre compte TV. La plateforme rémunérera en MBOKO ou en NDEM en fonction des politiques futures. Les jetons serviront aussi pour les jeux d'échecs. Les jetons pourraient s'activer offline par voucher aussi ». Puis : « Ignore le risque juridique jusqu'au 1/1/2027 » ; « Je teste d'abord la faisabilité technique » ; « Le joueur décide de convertir son jeton de NDEM à MBOKO librement » ; « Je veux aussi MBOKO vers NDEM ».

**Règles de la vague** : **J1** le grand livre est tenu par l'API principale, à double entrée, écritures immuables, idempotence, aucun solde de joueur négatif, conservation par monnaie ; **J2** la TV ne crée jamais de jeton (cache signé `cbw1`, bons en attente confirmés par le serveur) ; **J3** `castbridge-play` ne reçoit aucun identifiant du grand livre : il vérifie `cbe1` par clé publique et signe `cbr1` avec sa clé dédiée ; **J4** compte = identité de la TV (activation `cbx1`), téléphones jamais clients de l'API portefeuille ni du service de jeu ; **J5** MBOKO : attribué et misé en production seulement (détention et réception permises à tous) ; **J6** conversion à la demande dans les deux sens, taux et frais dans la table de politique (symétrie, 0 % au lancement) ; **J7** interrupteurs d'exploitation actifs par défaut ; **J8** aucun retrait, aucun achat direct de jetons (règle de produit) ; **J9** tests de propriétés sans dépendance nouvelle ; audit Opus obligatoire sur tout ce qui touche à la valeur.

## Niveau 1 — POC de faisabilité technique (priorité absolue)

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w22-01 | `sonnet-w22-01-grand-livre-coeur-java.md` | cœur pur Java : transactions à double entrée, attributions, conversion deux sens, transfert, blocage/règlement, invariants (propriétés) | serveur | M | sonnet | **oui** | 400 / 20 | **PRÊT (ordre 1)** | — |
| w22-03 | `sonnet-w22-03-formats-signes-cache-tv.md` | cœur Kotlin : `cbw1`, `cbe1`, `cbr1`, `cbv1`, cache TV, vecteurs | cœur | M | sonnet | **oui** | 400 / 20 | **PRÊT (ordre 1 bis)** | — |
| w22-02 | `sonnet-w22-02-serveur-grand-livre-v63-attributions.md` | migration V63, `JdbcLedger`, édition lue dans `cbx1`, tranches paresseuses, `sync`, historique, `cbw1`, politique | serveur | L | sonnet | **oui** | 600 / 30 | ATTEND 01 (ordre 2) | 01 |
| w22-04 | `sonnet-w22-04-service-jeu-mises-resultat-signe.md` | salles misées, `cbe1` vérifié, `Pot.split` par siège, `cbr1` signé, dépôt, drain ⇒ ABORT | cœur + service | M | sonnet | **oui** | 450 / 25 | ATTEND 03 + w20-04b (ordre 2 bis) | 03, w20-04b |
| w22-05 | `sonnet-w22-05-api-blocage-reglement-conversion-transfert.md` | blocage, règlement, rendus échus, conversion deux sens, codes de réception, transfert, collecteur | serveur | M | sonnet | **oui** | 450 / 25 | ATTEND 02, 03 (ordre 3) | 02, 03 (04 pour les fixtures réelles) |
| w22-06 | `sonnet-w22-06-bons-hors-ligne.md` | outil des bons (clé hors ligne), import, confirmation à `sync`, rachat hors ligne sur la TV | serveur + cœur + outil | M | sonnet | **oui** | 400 / 20 | ATTEND 02, 03 (ordre 3 bis) | 02, 03 |
| w22-07 | `sonnet-w22-07-tv-telephone-portefeuille-poc.md` | carte, « Mes jetons », convertir (deux sens), transférer, recevoir, bon, choix de mise, page téléphone | **exception de gel** (D-W22-13) | M | sonnet | échantillon | 450 / 25 | ATTEND 03, 05, 06, w20-05 POC (ordre 4) | 03, 05, 06 |
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

## Décisions du propriétaire attendues
D-W22-1 (lecture de la règle MBOKO : durée ⇒ 10 × N, illimitée ⇒ 50 + 10/mois), D-W22-2 (tranches mensuelles), D-W22-3 (« sans enjeu » = cagnotte NDEM, jeu sans mise gratuit), D-W22-4 (super illimité sans attribution), D-W22-5 (mise par siège), D-W22-6 (anti-ferme niveau 2), D-W22-7 (liaison d'appareil), D-W22-8 (bons : clé hors ligne + lot importé, MBOKO ciblé), D-W22-9 (option B), D-W22-10 (échecs niveau 2), D-W22-11 (coexistence des jetons W5 et points de défi), D-W22-12 (bornes), D-W22-13 (exception de gel), D-W22-14 (symétrie, frais 0 %), D-W22-15 (essai peut détenir/recevoir des MBOKO). Recommandations : § 10 de la conception. **Aucune ne bloque w22-01, w22-03.**

## Faits vérifiés le 2026-10-04
- Le serveur Java ne dépend pas du cœur Kotlin (`backend/pom.xml`) ; le service de jeu, si (`server-play/build.gradle.kts:11`).
- L'API ne connaît pas l'édition (`backend/src/main/java/castbridge/server/play/PlayTicketService.java:24-25`) ; le module des licences est éteint par défaut (`backend/src/main/resources/application.yml:117`) ; le service de jeu lit l'édition dans `cbx1` (`server-play/src/main/kotlin/castbridge/play/entitlement/HostRights.kt`).
- Migrations fusionnées jusqu'à V61 ; V62 réservée par W21.
- Tests serveur : H2 en mode MySQL (`backend/src/test/resources/application-test.yml`) et MySQL 8.4 par Testcontainers (`MySqlContainerTest.java`).
- `Pot.split` existe (`android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt:54-85`) ; échecs en ligne sans serveur (`docs/CHESS.md` § 6).
