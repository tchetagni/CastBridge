# w21-09 — Documentation et exploitation des données techniques du POC : `TELEMETRY.md`, `API-SERVER.md`, `PLAY-OPS.md` (collecteur, clé, cron, retour arrière), rapport hebdomadaire, phrase de consentement proposée à la relecture
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : haiku** · escalade : aucune (relecture par le coordinateur) · statut : **ATTEND w21-01…w21-08** (décrit ce qui est fusionné)
> **Groupe : W21-D** (ordre 5) · porte : tests de documentation existants s'il y en a (`ProductionDocsTest` de `:server-play`) + `python3 -m pytest tools/analysis/tests -q`
> **Jauge : ≈ 200 k jetons entrée / 15 k sortie** (effort S, ≈ 0,5 j) · exécutant le moins cher compétent : haiku

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` (entier ; § 4 confidentialité, § 5.2 collecteur, § 6 stockage, § 8 boucle de réglage). Branche `claude/w21-09-docs`. Rapport : `docs/agent-reports/haiku-w21-09.md`.

## Objectif (autonome)
Écrire, **sans rien exécuter sur le serveur**, ce que le propriétaire doit savoir et faire pour exploiter les données techniques du POC, à partir des rapports `docs/agent-reports/*-w21-0*.md` et du code fusionné.

## Fichiers possédés
- **Zone additive** : `docs/TELEMETRY.md` (nouvelle section « Mesures techniques du POC » : familles, cohorte, double porte, budget, k = 5, conservation 30 j / 12 mois, effacement, ce que la relecture juridique ne couvre pas encore — repris du § 4.3 de la conception, **sans** modifier le texte d'information en vigueur) ; `docs/TELEMETRY.md` § 3 (politique d'envoi amendée : TV directe 12 h, TV synchronisée ⇒ lot scellé remis au téléphone toutes les 4 h par le meilleur canal local, le téléphone envoie ; plus de télémétrie de la TV par la passerelle ; files, TTL, fraîcheur) ; `docs/API-SERVER.md` § 4 (routes `/api/v1/admin/tech/**`, `POST /api/v1/events/relay`, `POST /api/v1/ingest/play-metrics`, directive `pocMetrics`) ; `docs/ORDRES.md` (une ligne : le téléphone, déjà messager des ordres, devient aussi coursier des statistiques ; trames 22-26 si construites) ; `docs/PLAY-OPS.md` (nouvelle section « Métriques du service » : fichier horaire, génération de la clé **hors** `services/play`, empreinte dans l'override de l'API, cron du collecteur toutes les 15 min, vérification, retour arrière, variables de réglage de w21-08 avec bornes et procédure « valeur proposée → `.env.play` → `deploy-server.sh --service play --apply` → mesure de contrôle ») ; `docs/HANDOFF.md` (lignes W21, sans secret) ; `tools/analysis/README.md` (complément : cron hebdomadaire `tech_weekly.py`).
- **Nouveaux** : `docs/coordination/W21-PROPOSITION-TEXTE-CONSENTEMENT.md` (phrase additive proposée, **à relire par un juriste**, non appliquée : « …et, si votre appareil participe à l'essai du jeu en ligne, des mesures techniques de la connexion et des performances (délais, débits, mémoire), sans aucun contenu »).
- **Interdit** : `ConsentText.kt` et tout code ; toute commande lancée sur le serveur ; toute valeur de clé.

## Étapes
1. Lire les rapports W21 fusionnés et vérifier chaque nom de route, de variable, de table dans le code (citer fichier:ligne).
2. Écrire les sections ; chaque commande du runbook a « Lire d'abord », « Vérifier », « Revenir en arrière » (style de `PLAY-OPS.md`).
3. Retour arrière de l'API (server-1.1.2 ⇒ 1.1.1) avec V62 appliquée : reprendre la conclusion du rapport w21-02 sur Flyway.

## Critères d'acceptation
- Chaque route, variable, table, script cité existe dans le code fusionné (liste de vérification dans le rapport).
- Aucune valeur secrète, aucune adresse IP de joueur, aucun exemple contenant un vrai identifiant.
- `ProductionDocsTest` (s'il vérifie les variables de `PlayConfig` dans PLAY-OPS) vert.
- Le texte d'information en vigueur n'est pas modifié.

## Rapport
Liste des sections modifiées, liste de vérification, questions ouvertes pour le propriétaire.
