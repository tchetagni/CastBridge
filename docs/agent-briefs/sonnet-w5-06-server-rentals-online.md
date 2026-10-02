# w5-06 — Serveur : locations gérées en ligne (clé de contrat aléatoire sous KEK, boîte v2 vers la clé d'installation, scellement et cache des lots, réémission, fenêtre d'essai gratuite), schéma de la boutique

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après 5a)
> **Groupe : W5b-1** (vague W5b) · prérequis : w5-04, w5-05, w4-05 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Rental*Test'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui · découpage proposé : voir ROUTAGE § 2.3
> **Amendement W16 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 1, § 3.3, § 4.3. « Durée exacte du catalogue ≤ 60 j » devient : durée = `PilotRules` (miroir Java) quand `rental.userChosen = 1` (jours de validité **ou** heures d'utilisation ≤ 96 h avec borne de sûreté) ; `rental_contract` gagne `unit ENUM(HOURS, DAYS, DEFAULT)` et `max_use_minutes` ; les tables `pilot_rental_*` de **w16-08** fusionnent dans `rental_contract`/`shop_order` (prix 0) quand ce cahier existe ; le relevé d'usage (`POST …/usage`, maximum monotone) est à reprendre de w16-08.

**Vague 5b · Effort L (≈ 4 j) · Modèle : sonnet · Statut PRÊT (après w5-04, w5-05 ; w4-05 fusionné).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.3, § 4 (lire en entier). Branche `claude/sonnet-w5-06`. Rapport : `docs/agent-reports/sonnet-w5-06.md`. **Premier cahier de la sous-vague 5b** : il crée la migration, `ShopProperties` et le service de contrats que w5-07/08/09 utilisent. **Rien n'est déployé.**

## Objectif
Le serveur sait, pour une TV dont la demande de boutique est vérifiée (`licence`, `poste`, empreintes, `installPub`) : créer ou renouveler un **contrat de location** (`produit = loc-<bouquet>`, durée exacte du catalogue ≤ 60 j, `period` nouvelle ou existante), avec une **clé de contrat aléatoire** conservée chiffrée sous une **KEK fichier** ; émettre l'**activation de location** signée par la clé serveur (ligne `rental` avec boîte **v2** vers `installPub`) ; **sceller** les lots du bouquet sous la clé du contrat (format `RentalKeys.seal`, déterministe) dans un **cache disque** purgé ; servir ces lots (Range/ETag) ; **réémettre** la boîte pour une nouvelle installation (≤ 3 fois) ; créer le **contrat gratuit d'essai** (`essai|tout`, 3 j / 720 min, une fois par licence+installation et par code d'appareil sur 12 mois).

## Pourquoi (preuves)
- `B/licenses/ActivationService.java:111,158,254` (`issue`, `doIssue` : verrou licence, k parmi n, idempotence, `signer.sign` ; `rightsOf` ne connaît que `purchase|subscription|usage`) ; `ScopedActivationSigner.SERVER_SCOPES` contient `ISSUE_PRODUCTION` ; `B/licenses/LicenseKeyring.java:70-80` (chargement d'une clé depuis `secrets-dir`, modèle pour la KEK) ; `B/lots/LotService.java:72` (`dir()`, fichiers des lots publiés), `B/lots/LotController.java:55` (Range/ETag : modèle de service de fichier) ; w4-05 `B/licenses/RentalBoxV2.java` (XDH) ; `docs/RENTAL-LOTS.md` § 10 (dérivations `rentalKey`→`lotKey`, `seal`) et § 15 (questions tranchées par W5 § 4).
- Migrations : la plus haute aujourd'hui `V61__tunnel.sql` ; w4-16 et peut-être w1-11 ont pris les suivantes : **vérifier** `ls backend/src/main/resources/db/migration/`.

## Fichiers possédés
Nouveaux `B/shop/ShopProperties.java` (`castbridge.shop.*` : `enabled=false`, `rental-cache-max-bytes=4294967296`, `rental-kek-file=rental-kek.key`, `owner-public-keys`, `max-active-contracts-per-license=3`, `order-ttl-hours=72`, `offline-grant-max=60` (lu de la grille sinon), `trial-lots-per-device-months=12`), `B/shop/ShopFeature.java` (404 si `enabled=false`, comme `LicenseFeature`), `B/shop/rental/RentalKek.java`, `RentalContractService.java`, `RentalIssuer.java`, `RentalSealer.java`, `RentalLotController.java` (`GET /api/v1/shop/rentals/{contractId}/lots/{lot}/{version}`), `TrialLotsService.java`, `RentalCachePurge.java` (tâche planifiée), entités/`JdbcTemplate` ; migration `backend/src/main/resources/db/migration/V6x__shop.sql` (**tout le schéma du § 3.3**, y compris commandes, bons, jetons, reçus : une seule migration pour la vague ; numéro = plus haut + 1) ; `BT/shop/rental/**` ; modifiés `B/licenses/ActivationService.java` (**une** méthode additive), `application.yml` (bloc `castbridge.shop`), `backend/README.md` (§ secrets : `rental-kek.key`). **Hors zone** : `B/shop/order/**`, `B/shop/tokens/**`, `B/shop/admin/**` (w5-07…09), `B/shop/wire/**` (w5-05 : utiliser), `B/lots/**`, `B/licenses/*` autres, Android, docs.

## Étapes
1. Migration (schéma complet § 3.3 ; `agent_delegation` : colonnes additives `may_confirm_orders`, `may_sell_vouchers`, `max_confirm_xaf_per_day` si la table existe, sinon les laisser à w4-16 et le dire). Index : `rental_contract(license_id, seat_id, product_id, period_ms)` UNIQUE, `(device_code)`, `(ends_at)`.
2. `RentalKek` : fichier 32 octets dans `secrets-dir` (chemin sûr comme `LicenseKeyring`), `seal(aad, key)` / `open(aad, blob)` AES-256-GCM ; absent et module activé ⇒ échec de démarrage avec message français clair ; absent et module éteint ⇒ rien.
3. `ActivationService.issueWithRights(Actor, licenseId, seatId, deviceRequestText, kind, List<String> rightLines, windowHours, channel)` : **additif**, réutilise `doIssue` (verrou, idempotence par `idem_key` = SHA-256(licence, poste, lignes de droits, fenêtre), `lic_issuance.source = "SHOP"`), accepte des lignes `right=` déjà construites (validées par `WireActivation`), kind `production`, **aucun** `usage` ajouté (la location a sa propre horloge).
4. `RentalContractService.createOrRenew(proof: VerifiedProof, bundle, requestedPeriod: Long?): ContractIssue` : (a) bouquet louable (`rentalDays ≤ 60`, aucun lot libre d'après le catalogue de lots signé : lire `families` comme le bureau) ; (b) contrat existant `(licence, poste, loc-<b>, period)` actif ⇒ renouvellement : même clé (ouverte sous la KEK), nouvelle ligne `startsAt = now`, `period` d'origine ; sinon nouveau contrat : `key = SecureRandom 32 o`, `period = now` ; (c) plafond `max-active-contracts-per-license` ; (d) `box = RentalBoxV2.makeBox(installPub, ephSeed aléatoire, product, period, key)` ; (e) ligne `rental|loc-<b>|<b>|<startsAt>|<period>|<days>|0|0|<maxConcurrent>|v2:…` via le miroir Java de `RentalIssuing.rightWithKey` (w5-05 ou local) ; (f) `issueWithRights` ; (g) `rental_contract` upsert, `last_activation_fp`.
5. `RentalContractService.reissueForInstall(proof, contractId)` : même clé, nouvelle boîte vers le nouvel `installPub` ; compteur ≤ 3 ; au-delà : refus « réémissions épuisées : contactez l'assistance » + anomalie (`ShopAnomalies` de w5-09 : ici, simple ligne de journal `WARN` + table `agent_anomaly`-like **non** : laisser un point d'extension `AnomalySink` interface avec implémentation journal).
6. `RentalSealer.sealed(contract, lotId, version): Path` : lit le fichier du lot publié (`LotService.servable`), `lotKey = HKDF(rentalKey, "lot|<id>|<version>")`, `seal` déterministe (vecteur `seal-lot-file` de `rental-vectors.json` **doit** être reproduit par le port Java existant de w1-10/w4-05 : réutiliser), écrit dans `${storage-dir}/rentals/<contractId>/<lot>-v<n>.sealed` (atomique), ligne `rental_lot_cache` ; `RentalLotController` sert avec Range/ETag (= SHA-256 du scellé) ; **autorisation** : jeton d'appareil de l'appareil qui a commandé **ou** d'un appareil dont la licence = celle du contrat (lier le jeton d'appareil à une licence : via `shop_order.device_code` ↔ `devices` ; sinon, à défaut, au `device_code` de la commande : documenter le choix).
7. `RentalCachePurge` (toutes les heures) : contrats `ENDED` + 7 j ⇒ fichiers supprimés ; au-delà de `rental-cache-max-bytes` ⇒ LRU (`built_at`).
8. `TrialLotsService.ensure(proof)` : règles § 4.6 ; contrat `essai|tout`, 3 j, `maxUsageMinutes = 720`, aucun lot scellé (lots d'essai en clair : la ligne `rental` suffit) ; `shop_order(kind=TRIAL_LOTS, amount=0, payment=OWNER_GRANT:trial)`.
9. Tests H2/MockMvc : création (activation vérifiable par `EnvelopeVerifier`, boîte ouvrable avec la clé privée d'installation de test : port Java `openBox` de w4-05), renouvellement = même clé et même `period`, plafond 3 contrats, bouquet > 60 j refusé, lot libre refusé, réémission ≤ 3, scellé = octets du vecteur `seal-lot-file` quand on injecte la clé du vecteur, Range 206, autorisation 403, purge, essai une fois seulement, module éteint ⇒ 404, KEK absente ⇒ démarrage refusé si `enabled`.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='RentalContractServiceTest,RentalSealerTest,RentalLotControllerTest,TrialLotsServiceTest,ShopMigrationTest'   # vert
cd backend && ./mvnw -q -o test   # suite complète verte
grep -n 'enabled: false' backend/src/main/resources/application.yml | grep -i -B3 shop   # module éteint par défaut
grep -rn 'masterFrom\|rental-master' backend/src/main/java   # 0 hit (aucun maître)
ls backend/src/main/resources/db/migration/ | tail -2   # V6x__shop.sql = plus haut + 1
```

## Cas limites
- Même bouquet, contrat terminé il y a 2 jours : **nouvelle** location (nouvelle `period`, nouvelle clé) ; la réponse le dit (`renewalOf = null`).
- Deux commandes simultanées pour le même contrat : verrou de ligne `rental_contract` (ou verrou de licence de `doIssue`) ; idempotence par `idem_key`.
- Lot publié retiré entre-temps (410) : contrat émis quand même ; le lot manquant est listé `unavailable` (le téléphone ne le livre pas).
- `installPub` absent de la demande (vieille TV v1) : refus « mettez CastBridge-TV à jour » (aucune boîte v1 côté serveur).

## À ne pas faire
Pas de déploiement ; pas de commit sur les branches partagées ; aucun secret ni montant réel dans le code et les tests ; **ne jamais** dériver la clé de contrat de la clé de signature ; ne pas écrire de boîte v1 ; ne pas modifier `WireActivation`/`EnvelopeVerifier`/les vecteurs ; ne pas toucher `B/lots/**`.

## Rapport
`STATUT`, numéro de migration, API de `RentalContractService`/`TrialLotsService` (pour w5-07), chemin du cache, propriété `castbridge.shop.*` finales, méthodes manquantes constatées.
