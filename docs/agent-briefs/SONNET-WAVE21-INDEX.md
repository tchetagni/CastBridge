# Vague 21 — Données techniques du POC : recueillir, analyser, régler la production sur preuves

<!-- routage architecte 2026-10-04 -->

**Source** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` (architecte). Branche de référence `integration/agents` (HEAD `774afb31`). Exécution sur ordre du coordinateur seulement.

**Demande du propriétaire (2026-10-04, verbatim)** : « Pour la phase de POC, tu vas mettre en place une infrastructure serveur qui va recueillir et analyser les données techniques pour des réglages affinés en phase prod »

**Principe** : **étendre** la télémétrie existante (`docs/TELEMETRY.md`, `POST /api/v1/events/batch`, `kpi_*`, `/admin/kpi`) ; mesures **passives** agrégées sur l'appareil en histogrammes à bornes fixes (bornes = seuils de décision) ; double porte **consentement « statistiques d'usage » + cohorte POC** ; service de jeu **sans secret** (fichier horaire + collecteur hôte) ; aucune configuration distante en W21 (réglages du service par variables bornées).

## Cahiers

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w21-01 | `sonnet-w21-01-tech-metrics-core.md` | catalogue JSON de parité + cœur pur `TechMetrics` (histogrammes, enregistreurs, quota, `UploadGate`) | cœur (permis) | M | sonnet | échantillon | 400 / 20 | **PRÊT** (ordre 1) | — |
| w21-02 | `sonnet-w21-02-api-tech-ingest-v62.md` | API : familles techniques, cohorte, quotas, cohérence, **V62**, agrégats, purges, `pocMetrics`, vues | serveur | M | sonnet | **oui** | 400 / 20 | **PRÊT** (ordre 1) | parité JSON à la fusion de 01 |
| w21-03 | `sonnet-w21-03-play-service-metrics.md` | `castbridge-play` : `PlayMetrics`, `RoomObserver`, `hello.link`, fichier horaire borné | cœur + service | M | sonnet | **oui** | 400 / 20 | ATTEND w20-04b (ordre 1) | w20-04b |
| w21-03b | `sonnet-w21-03b-play-metrics-ingest-collector.md` | route `POST /api/v1/ingest/play-metrics` (clé dédiée, locale seulement) + collecteur hôte | serveur + outil | S | sonnet | **oui** | 200 / 12 | ATTEND 02 (ordre 2) | 02 |
| w21-04 | `sonnet-w21-04-tv-wiring-tech-metrics.md` | câblage TV derrière `pocMetrics`, porte d'envoi pour toute la télémétrie, aucun écran | TV sans écran | M | sonnet | **oui** | 400 / 20 | ATTEND 01 + w20-05a (ordre 3) | 01, w20-05a |
| w21-05 | `sonnet-w21-05-analysis-scripts.md` | `tools/analysis/` : percentiles, parts exactes, robustesse, recommandations avec confiance, rapport hebdomadaire | outil | M | sonnet | échantillon | 350 / 20 | ATTEND 02 (ordre 3) | 02 |
| w21-06 | `sonnet-w21-06-admin-kpi-poc-en-ligne.md` | `/admin/kpi` « POC en ligne », k = 5, cohorte, « Mes TV de test », exports journalisés | serveur | M | **haiku** | échantillon | 400 / 20 | ATTEND 02 (ordre 3) | 02 |
| w21-07 | `sonnet-w21-07-phone-gateway-class-sync.md` | téléphone : classe cellulaire sans permission, `sync.pair`, `sync.xfer`, `link.wd` | téléphone sans écran | S | sonnet | échantillon | 250 / 15 | ATTEND 01 (ordre 3) | 01 |
| w21-08 | `sonnet-w21-08-play-tuning-knobs.md` | variables bornées du service : délai, mode adaptatif éteint, grâce, ping de salle | cœur + service | S | sonnet | **oui** | 250 / 15 | ATTEND 03 (ordre 4) | 03 |
| w21-09 | `sonnet-w21-09-docs-runbook-weekly.md` | `TELEMETRY.md`, `API-SERVER.md`, `PLAY-OPS.md`, HANDOFF, proposition de phrase de consentement | docs | S | **haiku** | — | 200 / 15 | ATTEND tous (ordre 5) | 01-08 |

## Ordre (preuves tôt)

1. **w21-01 ∥ w21-02 ∥ w21-03** (w21-03 dès que w20-04b est fusionné).
2. **w21-03b** ⇒ le propriétaire livre **server-1.1.2** (API, V62) et l'image `castbridge-play` **server-play-0.2.x**, génère la clé d'ingestion, pose le cron : **premières preuves du service dès la première partie du POC**, sans aucune TV à livrer (RTT par transport et classe déclarée, octets par message, taille d'un `state`, refus, plafonds, file de sortie, `CLOSED` tardifs).
3. **w21-04 ∥ w21-05 ∥ w21-06 ∥ w21-07** : preuves TV (marge des annonces, révélation, `ack`, coupures, ouverture décomposée, classe cellulaire), rapports et page.
4. **w21-08** : la boucle de réglage côté service.
5. **w21-09** : documentation et runbook.

Livraisons de production : actes du propriétaire uniquement (`deploy-server.sh server-1.1.2 --apply` ; `deploy-server.sh server-play-0.2.x --service play --apply` ; cron et clé selon PLAY-OPS mis à jour par w21-09) ; APK TV/téléphone **verrouillés** (`-PrequireActivation=true`), copiés dans `Download` de la clé USB.

## Coûts

Prix 2026-09 (index précédents) : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**. Sonnet M 5 × ≈ 1,0 $ = 5 $ ; sonnet S 3 × ≈ 0,4 $ = 1,2 $ ; haiku M 1 × ≈ 0,5 $ ; haiku S 1 × ≈ 0,2 $ ; audits Opus obligatoires 5 × ≈ 0,8 $ = 4 $ (02, 03, 03b, 04, 08) ; échantillons 4 × ≈ 0,4 $ = 1,6 $ (01, 05, 06, 07) ; reprises 15 % ≈ 1,9 $. **Total ≈ 14-15 $**, ≈ **11 agent·jours** ; ≈ 6-7 jours ouvrés avec trois exécutants en parallèle à l'ordre 1 et quatre à l'ordre 3.

## Règles W21

- **R1 (W15)** : chaque cahier commence par un test rouge sur `integration/agents`, sortie collée.
- **R2 (W20)** : un seul cahier à la fois sur `server-play/` (w21-03 puis w21-08, après w20-04b) ; un seul à la fois sur `C/quiz/online/ServerRoom.kt`.
- **R3** : aucune mesure, aucun octet, aucune sonde hors cohorte ou sans consentement « usage » (objet nul prouvé par test) ; aucun envoi de télémétrie pendant une partie Internet et 30 s après.
- **R4** : aucune donnée personnelle nouvelle ; dimensions fermées ; ni IP, ni ASN, ni opérateur, ni nom, ni pseudonyme, ni texte libre, ni contenu ; clés interdites de `EventCatalog.FORBIDDEN`.
- **R5** : `castbridge-play` ne reçoit **aucun** secret ni route nouvelle ; la clé d'ingestion vit hors de `~/castbridge/services/play/`.
- **R6** : aucun exécutant ne se connecte au serveur de production ni à un appareil ; aucun `--apply`.
- **R7** : gel des écrans respecté : aucun écran ni texte affiché neuf (w21-04, w21-07).
- **R8** : règle de symbiose W19 (additif, ancien pair compatible) pour `hello.link` et le champ `cell` de la passerelle.
- **R9** : audits Opus **obligatoires** sur w21-02, w21-03, w21-03b, w21-04, w21-08.

## Décisions du propriétaire attendues

D-W21-1 … D-W21-11 : `DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 11 (chacune avec recommandation). Aucune ne bloque w21-01, w21-02 ni w21-03 ; D-W21-1 (consentement + cohorte restreinte) et D-W21-5 (collecteur) doivent être tranchées avant la livraison server-1.1.2.
