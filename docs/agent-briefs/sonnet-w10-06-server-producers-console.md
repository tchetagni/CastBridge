# w10-06 — Serveur : producteurs (import de soumission, file de revue, classification, prix proposé, publication, reçu de dépôt), ventes et relevés signés, versements, retrait et ordre `work.takedown`, console `/admin/producers/**`

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (w5-07 souhaité pour brancher `SaleListener`)
> **Groupe : W10b-2** (vague W10b) · prérequis : w10-05 ; w5-07 souhaité · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Producer*Test,WorkReview*Test,Statement*Test,Takedown*Test,PolicyCatalogTest'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui (argent, relevés, ordre signé)

**Vague 10b · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (w5-07 souhaité pour brancher `SaleListener`).** Conception : DESIGN-W10 § 2.4, § 2.5, § 3.1, § 3.4-3.7, § 6.2-6.3, § 8 (S1, S4, S5). Branche `claude/sonnet-w10-06`. Rapport : `docs/agent-reports/sonnet-w10-06.md`. Dépend de w10-05 (entités, dépôts, propriétés).

## Objectif
Le propriétaire gère, depuis la console web existante (`/admin/**`, rôles et TOTP de `docs/LICENSE-ADMIN.md`), le cycle complet d'une œuvre et d'un producteur : **import** d'un dossier de soumission (Studio), **revue** avec liste de contrôle, **doublons** par empreinte, **classification** plateforme (≥ producteur), **prix proposé → fragment de grille** à signer hors ligne, **publication** (les deux lots) et **reçu de dépôt** signé ; **ventes** (`SaleListener`), **relevés mensuels** signés, **versements** (TOTP), **retrait** avec ordre différé `work.takedown` ; audit chaîné ; CSV.

## Pourquoi (preuves)
- `B/licenses/AuditLog.java` (audit chaîné), `Totp.java`, rôles (`Role.java`), pages `/admin/licenses/**` (gabarits `TPL/`) : à imiter.
- `B/orders/PolicyCatalog.java:15` (`ACTIONS` liste fermée), `:51` (paramètres par action) ; `B/licenses/EnvelopeIssuer.java` (ordres signés `POLICY`).
- `B/lots/LotService.upload/publish` (`:78-157`) : publication des lots.
- DESIGN-W5 § 3.3 `shop_order` (`FULFILLED`, `REFUNDED`, `item`) ; w5-07 `OrderService` : point de branchement.

## Fichiers possédés
Nouveaux `B/producers/{ProducerService,WorkReviewService,StatementService,TakedownService,SaleListener,ProducerAdminController,ProducerApiController,ProducerCsv,DepositReceipt}.java`, `TPL/producers*.html`, `BT/producers/service/**` ; `B/orders/PolicyCatalog.java` (action `work.takedown`, additif) ; `TPL/lic-nav.html` (un lien « Producteurs »). **Hors zone** : `B/lots/**` (utiliser `LotService`), `B/shop/**` (si w5-07 fusionné : **demander** dans le rapport l'appel `saleListeners.forEach(...)` ; ne pas l'éditer ici), `B/telemetry/**`.

## Étapes
1. `ProducerService` : créer/éditer un producteur (slug, nom d'artiste, `legal_name_hash`/`phone_hash` = SHA-256 salé serveur, zone, `kyc_level`, `contract_version`, `signed_at`, `payout_pref`), suspendre/clore (TOTP + motif), fiche (cumul dû, œuvres, relevés).
2. `WorkReviewService.importSubmission(producerId, multipart)` : lit `SOUMISSION.json`, `work.json`, les deux lots ; vérifie avec `WorkLotValidator` ; **doublon** : SHA-256 média ou chromaprint déjà en `producer_fingerprint` pour un **autre** producteur ⇒ état `DUPLICATE` (les deux fiches le montrent) ; sinon `SUBMITTED` + `producer_work_version` (version = max + 1) ; les lots sont déposés via `LotService.upload(publish=false)`.
3. Revue : page avec la **liste de contrôle** (DESIGN § 3.5, 5 groupes, cases, notes) ; décision `APPROVE` (classification plateforme ∈ {all,12,16}, ≥ proposée), `CHANGES` (motif, renvoyé au producteur hors ligne), `REJECT` (motif) ; `producer_review` horodaté ; **prix proposé** affiché, champ « prix retenu (XAF) » → état `PRICED` + **export** d'un fragment `price=loc-oeuvre-<id>|30|<xaf>` (+ variante 7 j, + `loc-chaine-<p>|30` si le producteur en a une) à coller dans `tools/prices/sign_prices.py` (texte copiable ; **le serveur ne signe jamais la grille**).
4. Publication : `PUBLISHED` ⇒ `LotService.publish` des deux lots, `published_at`, rappel à l'écran « re-signer et déposer : catalogue des bouquets, catalogue des œuvres, grille » ; **reçu de dépôt** `castbridge-deposit-v1` (`id`, version, `sha256` des deux lots, date, `deposit_code` `D-XXXX-XXXX`) signé par la clé serveur (`EnvelopeIssuer` ou signature texte comme les reçus W5), affiché/imprimable, vérifiable `GET /api/v1/producers/deposits/{code}` (public par code).
5. `SaleListener` (interface + implémentation) : `onFulfilled(orderId, item, amountXaf, agentKid, contractId)` / `onRefunded(orderId)` : pour `loc-oeuvre-*`, `loc-chaine-*` ⇒ `producer_sale` avec parts d'après la **grille publiée** (`set=works.*`, lue par le relais de grille existant ; défauts 60/15) ; chaîne ⇒ `channel_slug`, `work_id` null ; `REFUNDED` ⇒ `reversed_at` (et `REVERSAL` au relevé suivant si déjà relevé). **Import CSV** `POST /api/v1/admin/producers/sales/import` (pilote papier : `ventes.csv` de w10-14, colonnes `date,article,montant,mode,agent`) avec idempotence par ligne (hash).
6. `StatementService` : brouillons du mois (`DRAFT`, lignes par œuvre : locations, renouvellements, remboursements, brut, part), `issue` ⇒ texte `castbridge-producer-statement-v1` signé (clé serveur), code `P-XXXX-XXXX`, report `CARRIED` si `total_due < works.minPayoutXaf` ; `markPaid` (TOTP, `paid_via`, référence de reçu) ; `GET /api/v1/producers/statements/{code}` public par code (texte + signature) ; CSV.
7. `TakedownService` : ouvrir (requérant, motif, `refund`) ⇒ `SUSPENDED` immédiat ; décision (TOTP) ⇒ `TAKEN_DOWN` + `LotService.revoke` des deux lots + **ordre** `work.takedown {work, reason(≤120), refund}` émis via `EnvelopeIssuer` (portée `POLICY`, cible `Any` ou `Device` si un code est fourni) et déposé dans la file d'ordres existante (`B/orders/**`, mécanisme de w3-09 / deferred-orders) ; `PolicyCatalog.ACTIONS += "work.takedown"`, paramètres `work`, `reason`, `refund`.
8. Pages : `/admin/producers` (liste, recherche, cumul dû), `/admin/producers/{id}` (fiche), `/admin/producers/works` (file par état, filtres), `/admin/producers/works/{id}` (revue), `/admin/producers/takedowns`, `/admin/producers/statements` ; rôles : propriétaire = tout ; support = lecture + revue sans publication ; lecture seule. Audit : chaque décision, prix, publication, relevé, versement, retrait.
9. Tests : import ⇒ `SUBMITTED` ; doublon inter-producteurs ⇒ `DUPLICATE` ; même producteur nouvelle version ⇒ OK ; classification plateforme < proposée ⇒ refus ; relevé : 3 ventes + 1 remboursement ⇒ parts et total ; seuil ⇒ `CARRIED` ; `markPaid` sans TOTP ⇒ refus ; retrait ⇒ lots révoqués + ordre dans la file ; module éteint ⇒ 404.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='Producer*Test,WorkReview*Test,Statement*Test,Takedown*Test,PolicyCatalogTest'   # vert
grep -n 'work.takedown' backend/src/main/java/castbridge/server/orders/PolicyCatalog.java   # présent
grep -rn 'sign_prices\|grille' backend/src/main/java/castbridge/server/producers | grep -i 'sign(' ; echo "(aucune signature de grille côté serveur)"
```

## Cas limites
- Vente d'une chaîne dont le producteur a 0 œuvre publiée au moment du relevé : part 100 % au producteur (chaîne mono-producteur).
- Remboursement après relevé payé : `REVERSAL` sur le relevé suivant, jamais de relevé négatif payé (report).
- Retrait d'une œuvre déjà retirée : idempotent.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas signer la grille ni les catalogues côté serveur ; ne pas éditer `B/shop/**` ; aucun montant réel ; aucun nom civil en clair en base ; textes des pages en français.

## Rapport
`STATUT`, interface `SaleListener` (signature exacte, pour w5-07), format du relevé et du reçu de dépôt (copie), questions (D-W10-3, D-W10-4).
