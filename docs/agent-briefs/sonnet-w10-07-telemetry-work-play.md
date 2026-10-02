# w10-07 — Télémétrie : événement `work_play`, agrégation par œuvre pour les producteurs, consentement

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10b-3** (vague W10b) · prérequis : w10-05 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='EventCatalogTest,PlayStatAggregatorTest' && cd ../android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.telemetry.*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 10b · Effort S (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 5.5, § 6.2 (`producer_play_stat`). Branche `claude/sonnet-w10-07`. Rapport : `docs/agent-reports/sonnet-w10-07.md`. Dépend de w10-05 (entité `ProducerPlayStat`).

## Objectif
Une TV ayant consenti aux « statistiques d'usage » envoie `work_play{work, pct, ms, teaser}` à la fin d'une lecture d'œuvre ; le serveur l'accepte (liste blanche), l'agrège par œuvre et par jour (`plays`, `completions` = `pct ≥ 90`, `devices` distincts, `teaser_plays`) et la page `/admin/producers/stats` (section ajoutée au gabarit de w10-06 **ou** page autonome si w10-06 n'est pas fusionné) l'affiche avec « d'après N téléviseurs ayant accepté les statistiques ». Aucun code d'appareil dans l'événement ; aucune donnée sans consentement.

## Pourquoi (preuves)
- `docs/TELEMETRY.md` § 2 (deux niveaux, refus serveur sans consentement), § 3 (liste blanche par événement, clés interdites : `title`, `name`… ⇒ l'œuvre est désignée par son **identifiant** `work`, jamais son titre), § 4 (catalogue).
- `B/telemetry/EventCatalog.java:81` (`playback_end` : `ms`, `pct`…) ; `C/telemetry/Telemetry.kt:16-51` (`TV_FEATURES`, événements).
- `docs/PARENTAL.md` : aucun événement parental ; `work_play` n'emporte ni profil ni tranche d'âge.

## Fichiers possédés
`C/telemetry/Telemetry.kt`, `CT/telemetry/**`, `B/telemetry/EventCatalog.java`, nouveau `B/producers/PlayStatAggregator.java`, `BT/telemetry/**`, `BT/producers/PlayStatAggregatorTest.java`. **Hors zone** : `R/**` (w10-09 émet l'événement), gabarits (w10-06 ; si absent : page minimale `TPL/producers-stats.html` **autorisée** et notée).

## Étapes
1. `EventCatalog` : `def("work_play", usage, {work: text(32, [a-z0-9-]), pct: number(100), ms: number, teaser: bool})` ; refusé sans consentement (déjà le mécanisme) ; `oeuvres` dans `TV_FEATURES` si une liste de fonctions existe côté serveur.
2. `Telemetry.kt` : `fun workPlay(work: String, pct: Int, ms: Long, teaser: Boolean)` (niveau usage) ; `"oeuvres"` dans `TV_FEATURES` ; test : clé `title` jamais émise.
3. `PlayStatAggregator` : tâche quotidienne (ou à la réception, upsert) : `producer_play_stat(work_id, day)` += plays, completions, teaser_plays ; `devices` = cardinalité des `deviceId` **hachés** du jour (jamais stockés en clair, un `HyperLogLog` n'est pas nécessaire : set en mémoire par jour puis compte), purge des événements bruts selon la politique existante (w1-11).
4. Affichage : par œuvre : 7/30 jours : lectures, complétions (%), aperçus lus, « N téléviseurs consentants » ; un producteur ne voit **jamais** la page (console du propriétaire seulement) : le propriétaire lui communique les chiffres sur le relevé (champ `plays` informatif ajouté par `StatementService` si disponible : signaler à w10-06 ou le faire si fusionné).
5. Tests : événement sans consentement refusé ; clé `title` refusée ; agrégation de 3 événements (2 appareils) ⇒ `plays=3, devices=2, completions=1`.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='EventCatalogTest,PlayStatAggregatorTest'   # vert
cd android && gradle --offline :core:test --tests 'castbridge.core.telemetry.*'      # vert
grep -n 'work_play' docs/TELEMETRY.md || echo "(doc : w10-15)"
```

## Cas limites
- `pct` > 100 ou négatif : borné/refusé par la liste blanche existante.
- Œuvre inconnue du serveur (retirée) : agrégée quand même sous son id (relevé historique), jamais d'erreur renvoyée à la TV.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun identifiant d'appareil ni titre dans l'événement ; pas d'événement sans consentement ; ne pas toucher `R/**`.

## Rapport
`STATUT`, définition exacte de l'événement (pour w10-09), questions.
