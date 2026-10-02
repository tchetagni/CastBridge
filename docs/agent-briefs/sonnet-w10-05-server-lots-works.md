# w10-05 — Serveur : fonctions de lots et plafonds par fonction, `WorkLotValidator`, relais du catalogue des œuvres, lots d'œuvres jamais servis en clair, migration et entités producteurs

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT
> **Groupe : W10b-1** (vague W10b) · prérequis : w10-01, w10-04 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='LotsApiTest,WorkLotValidatorTest,WorksCatalogApiTest,ProducersSchemaTest'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui (migration, garde de service des lots)

**Vague 10b · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 6.1, § 6.2, § 6.3 (routes publiques), § 13. Branche `claude/sonnet-w10-05`. Rapport : `docs/agent-reports/sonnet-w10-05.md`. Dépend de w10-01 (format `work.json`), w10-04 (catalogue des œuvres d'exemple). **Premier cahier de la sous-vague** : il fixe le schéma et les propriétés.

## Objectif
Le serveur accepte des lots `oeuvre` (≤ 25 Mo), `langues` (≤ 3 Mo), `langmedia` (≤ 100 Mo), valide le contenu d'un lot d'œuvre, **ne sert jamais un lot complet d'œuvre en clair** (seuls les aperçus `-trial` passent par `/api/v1/lots/...` ; les complets ne sont servis que **scellés** par les routes W5), relaie le catalogue des œuvres signé hors ligne, et possède les **tables** du marché producteur (module éteint par défaut).

## Pourquoi (preuves)
- `B/lots/LotService.java:46-49` (`FEATURES = Set.of("learn","quiz")`, `MAX_LOT_BYTES = 10 Mo`), `:59-65` (validateurs par fonction, `@Component implements LotValidator` remplace le défaut), `:81`, `:106-107`, `:190` (messages « learn ou quiz attendu »).
- `B/lots/LotController.java:55-63` : sert tout lot publié ; `B/lots/BundleCatalogController.java:20-70` : relais à copier.
- `backend/src/main/resources/db/migration/README.md` : « plus haut + 1 », vérifier toutes les branches ; V62+ libre.
- DESIGN-W5 § 4.4 : `GET /api/v1/shop/rentals/{contract}/lots/{lot}/{version}` sert les lots **scellés** (w5-06).

## Fichiers possédés
`B/lots/LotService.java`, `B/lots/LotController.java`, nouveaux `B/lots/WorkLotValidator.java`, `B/lots/WorksCatalogController.java`, `B/config/CastbridgeProperties.java` (additif), migration `V<n>__producers.sql`, nouveaux `B/producers/{Producer,ProducerWork,ProducerWorkVersion,ProducerFingerprint,ProducerReview,ProducerTakedown,ProducerSale,ProducerStatement,ProducerPlayStat}.java` + `*Repository.java`, `BT/lots/**`, `BT/producers/model/**`, `backend/src/main/resources/application.yml` (bloc `castbridge.producers`, `castbridge.lots.max-bytes.*`). **Hors zone** : `B/shop/**` (W5), `B/producers/*Service*`, contrôleurs d'administration (w10-06), `B/telemetry/**` (w10-07).

## Étapes
1. `LotService` : `FEATURES = {learn, quiz, langues, langmedia, oeuvre}` ; `maxBytesFor(feature)` depuis `castbridge.lots.max-bytes.{learn:10M,quiz:10M,langues:3M,langmedia:100M,oeuvre:25M}` (propriétés, défauts) ; messages d'erreur listant les fonctions ; `catalog(feature, …)` accepte les nouvelles fonctions ; test : un lot `oeuvre` de 26 Mo refusé, 24 Mo accepté.
2. `WorkLotValidator` (feature `oeuvre`) : ZIP lisible, entrées `work.json` + `media/<un fichier>` + `cover.webp` (+ `lot.json` toléré), **rien d'autre** ; `work.json` : champs obligatoires, `id` = scope du lot (ou `teaserOf` + `-trial` = scope), classification ∈ {all,12,16}, aperçu ⇒ `durationS ≤ 60` et taille ≤ 2 Mo, `media.sha256`/`bytes` exacts, mime ∈ {audio/ogg, video/mp4} ; messages français ; tests avec des ZIP construits en mémoire.
3. `LotController.download` : si `feature == "oeuvre"` et scope **sans** suffixe `-trial` ⇒ **403** `{"error":"Une œuvre complète n'est servie que scellée pour une location"}` (test) ; `HEAD` idem ; les aperçus restent servis normalement.
4. `WorksCatalogController` : `GET /api/v1/catalog/works` relais de `castbridge.catalog.works-file` (défaut `<storage-dir>/lots/works-catalog.json`), ETag, ≤ 2 Mio, 404/503 comme les bouquets ; test avec le catalogue d'exemple de w10-04 (copié dans `BT/resources`).
5. Migration `V<n>__producers.sql` (n = plus haut + 1 sur **toutes** les branches, vérifié ; si W5 a déjà pris un numéro, prendre le suivant) : tables du DESIGN § 6.2 ; colonnes nullables / défauts ; index `producer_work(state)`, `producer_sale(statement_id)`, `producer_fingerprint(work_id)` ; `CHECK` sur les énumérations ; commentaire d'en-tête (aucun secret, aucune donnée nominative hors `display_name`/`slug`, hachages pour nom civil et téléphone).
6. Entités JPA + dépôts (lecture/écriture simples) ; `ProducersFeature` (`castbridge.producers.enabled=false` ⇒ aucune route montée ; test 404).
7. `application.yml` : blocs documentés ; `backend/README.md` **non** (w10-15 doc) : seulement un commentaire dans `application.yml`.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='LotsApiTest,WorkLotValidatorTest,WorksCatalogApiTest,ProducersSchemaTest'   # vert
grep -n 'oeuvre' backend/src/main/java/castbridge/server/lots/LotController.java   # garde 403 présente
ls backend/src/main/resources/db/migration | sort -V | tail -1                     # V<n>__producers.sql, n = plus haut + 1
```

## Cas limites
- Catalogue de lots (`/api/v1/lots/catalog?feature=oeuvre`) : liste les aperçus **et** les complets (le téléphone a besoin des métadonnées du complet pour la livraison W5) ; seul le **téléchargement** du complet en clair est refusé.
- Un lot `langmedia` de 100 Mo : accepté ; la route de téléchargement doit rester en flux (déjà `FileChannel`).
- Module producteurs éteint : les tables existent (migration appliquée), rien n'y écrit.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `B/shop/**` ; ne pas écrire de service métier (w10-06) ; ne pas déployer ; pas de données réelles ; messages en français.

## Rapport
`STATUT`, numéro de migration choisi et preuve (`git log --all -- …/migration`), interfaces publiques des dépôts (pour w10-06/07), questions.
