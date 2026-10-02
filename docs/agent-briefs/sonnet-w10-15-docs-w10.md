# w10-15 — Documentation : `OEUVRES.md`, `PRODUCTEURS.md`, mises à jour LOTS, RENTAL-LOTS, MEDIA-POLICY, PARENTAL, TRIAL-EDITION, API-SERVER, TELEMETRY, CONTENT-PUBLISH, SHOP, HANDOFF

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10e-1** (vague W10e) · prérequis : 10a-10d fusionnées · porte : `ls docs/OEUVRES.md docs/PRODUCTEURS.md && grep -c 'oeuvre' docs/LOTS.md`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10e · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 (tout). Branche `claude/sonnet-w10-15`. Rapport : `docs/agent-reports/sonnet-w10-15.md`. Après la fusion de 10a-10d (lire les rapports `docs/agent-reports/sonnet-w10-01…14.md` : la doc décrit **ce qui a été fait**, pas la conception).

## Objectif
Deux documents nouveaux et dix mises à jour, honnêtes (ce qui est vérifié sur matériel vs JVM seulement), sans secret ni montant.

## Fichiers possédés
Nouveaux `docs/OEUVRES.md`, `docs/PRODUCTEURS.md` ; `docs/LOTS.md`, `docs/RENTAL-LOTS.md`, `docs/MEDIA-POLICY.md`, `docs/PARENTAL.md`, `docs/TRIAL-EDITION.md`, `docs/API-SERVER.md`, `docs/TELEMETRY.md`, `docs/CONTENT-PUBLISH.md`, `docs/HANDOFF.md`, `docs/SHOP.md` (**si** w5-20 l'a créé). **Hors zone** : code, `docs/legal/**` (w10-16), `docs/TEST-CAMPAIGN.md` (w10-17).

## Étapes
1. `docs/OEUVRES.md` (format et TV) : qu'est-ce qu'une œuvre (lot `oeuvre:<id>`, aperçu `-trial`, `work.json` champ par champ, presets), `WorkStore` (budget 200 Mo, volume, adoption USB), lecture (loopback mémoire, repli fichier privé, résiduel), routes `/api/oeuvres*`, essai/réduit/enfant, balayage, ordre `work.takedown` et sa limite hors ligne, télémétrie `work_play`, limites honnêtes (25 Mo, pas de filigrane, TV rootée), évolution conteneur long format (renvoi DESIGN § 5.6).
2. `docs/PRODUCTEURS.md` (guide du propriétaire) : rôles, parcours S1 (Studio → import → revue → prix → catalogues → publication → reçu de dépôt), liste de contrôle de modération, classification, doublons, versions, retrait, relevés et versements (espèces/bons, seuil), import CSV du pilote, console (pages), limites, ce qui attend le juriste (renvoi `docs/legal/`).
3. `LOTS.md` § 2 et § 7 : fonctions `langues`, `langmedia`, `oeuvre` ; plafonds par fonction ; second magasin ; § 1 : « la TV ne télécharge jamais un lot » : préciser l'exception W5 (déjà) et que les œuvres suivent la même règle.
4. `RENTAL-LOTS.md` : § 7 (familles : `content/oeuvres/lots.json`), § 11 (résiduel : œuvre en mémoire pendant la lecture), § 15 (renvoi W5/W10).
5. `MEDIA-POLICY.md` § 1 : ligne « Œuvres locales : fonction `oeuvre`, magasin et budget propres, hors de la règle 3 Mo ; presets dans `tools/producer-studio/presets.json` ».
6. `PARENTAL.md` : classification des œuvres par le catalogue, filtre par profil, code -16, pas de réglage parent ; limites.
7. `TRIAL-EDITION.md` § 15 / `TrialPolicy` : aperçus d'œuvres visibles en essai (D-W10-6) ; `tout` exclut les œuvres.
8. `API-SERVER.md` : `GET /api/v1/catalog/works`, garde 403 des lots complets `oeuvre`, `GET /api/v1/producers/statements/{code}`, `/deposits/{code}`, `/admin/producers/**`, import CSV ; `castbridge.producers.enabled`.
9. `TELEMETRY.md` § 4 : `work_play` ; § 6 : agrégation producteurs et consentement.
10. `CONTENT-PUBLISH.md` : `content/oeuvres/dist/`, `sign-works-catalog`, dépôt du catalogue des œuvres (`castbridge.catalog.works-file`), ordre (lots, puis catalogues).
11. `SHOP.md` (si présent) : trois familles, articles, page chaîne.
12. `HANDOFF.md` § 0 : entrée datée « W10 : … fait / non vérifié sur matériel / décisions en attente » ; **aucun secret**.

## Critères d'acceptation
```sh
ls docs/OEUVRES.md docs/PRODUCTEURS.md
grep -n 'oeuvre' docs/LOTS.md docs/MEDIA-POLICY.md docs/API-SERVER.md docs/TELEMETRY.md | wc -l    # ≥ 8
grep -rn 'XAF [0-9]\|[0-9]\{3,\} XAF' docs/OEUVRES.md docs/PRODUCTEURS.md                           # 0
grep -n 'W10' docs/HANDOFF.md | head -1
```

## Cas limites
- Un cahier 10a-10d non fusionné : la doc le dit (« prévu, non livré ») au lieu de décrire une fonction absente.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant, numéro, secret ; pas de code ; pas d'affirmation juridique ; dire « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT`, écarts entre conception et réalisation relevés dans les rapports, questions.
