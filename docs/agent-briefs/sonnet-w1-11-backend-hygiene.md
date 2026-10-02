# w1-11 — Hygiène du serveur : `deviceName`, purge des événements, règle Flyway, catalogue borné, rotation de clé

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Device*Test,Telemetry*Test'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S (≈ 5 h) · Statut PRÊT.** Branche `claude/sonnet-w1-11`. Rapport : `docs/agent-reports/sonnet-w1-11.md`. **Rien n'est déployé** : code et docs seulement.

## Objectif
1. `deviceName` (texte libre de l'utilisateur) n'est plus envoyé en « essentiel » ni stocké tel quel.
2. Une tâche planifiée purge les événements bruts de télémétrie au-delà de la rétention documentée (13 mois) — ou la doc dit explicitement que l'agrégation les supprime.
3. Règle écrite de numérotation Flyway (« plus haut + 1 », plages réservées) ; `README.md` dans le dossier des migrations.
4. `BundleCatalogController` borne la taille du fichier lu et renvoie un `ETag`.
5. `docs/LICENSE-ADMIN.md` § 3.6 « Rotation de la clé serveur » (procédure pas à pas).

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/TvConnect.kt:260` : `deviceName = Settings.Global.DEVICE_NAME` (peut être un prénom) → `backend/src/main/java/castbridge/server/devices/Device.java:35`, affiché dans l'admin ; absent de la liste de `docs/TELEMETRY.md` § 1-2 ; conservé 365 j (`application.yml:95-98`).
- `docs/TELEMETRY.md:142` « événements bruts 13 mois » : aucun `@Scheduled` de purge dans `backend/src/main/java/castbridge/server/telemetry/` (seuls `licenses/quiz/devices/tunnel` en ont).
- `backend/src/main/resources/db/migration/` : V1-4, V30, V50-52, V60, V61 (non suivi) ; `V60__deferred_orders.sql:2` réserve V50-59 ; la branche `wip/external-ai-changes` porte un `V4__bt_address.sql` en collision avec `V4__lots.sql` (ne jamais fusionner).
- `backend/src/main/java/castbridge/server/lots/BundleCatalogController.java:40-42` : `Files.readAllBytes` sans plafond, relu à chaque requête, pas d'`ETag`.
- `docs/LICENSE-ADMIN.md:229` : un paragraphe sur la rotation, aucune procédure.
- Audit : SE-13, OP-7, OP-9, OP-10, A4-8 (Opus).

## Fichiers possédés
`backend/src/main/java/castbridge/server/devices/**`, `backend/src/main/java/castbridge/server/telemetry/**`, `backend/src/main/java/castbridge/server/lots/BundleCatalogController.java`, `android/receiver/src/main/kotlin/castbridge/receiver/TvConnect.kt` (**champ `deviceName` seulement**), `android/core/src/main/kotlin/castbridge/core/connect/*` (champ `deviceName` seulement), nouveau `backend/src/main/resources/db/migration/README.md`, `docs/LICENSE-ADMIN.md` (§ 3.6 nouveau), `docs/TELEMETRY.md`, tests `backend/src/test/java/castbridge/server/**` correspondants. **Hors zone** : `licenses/WireActivation.java`, `EnvelopeVerifier.java` (w1-10), `tunnel/**` (w2-15), migrations existantes (ne jamais éditer une migration déjà déployée : V1-V3 ; les autres sont non déployées mais à laisser intactes).

## Étapes
1. Télémétrie : côté cœur/TV, remplacer `deviceName` par `null` (ou un hachage SHA-256 salé avec `counting_salt` si l'admin en a besoin pour distinguer deux TV : vérifier l'usage dans `AdminDeviceController.java:41`) ; côté serveur, accepter l'absence ; nouvelle migration **`V62__device_name_nullable.sql`** seulement si la colonne est `NOT NULL` (vérifier `V1`/`V2`) — sinon pas de migration. Mettre `docs/TELEMETRY.md` § 1-2 à jour.
2. Purge : `@Scheduled(cron = "0 30 3 * * *")` dans le service de télémétrie : `DELETE … WHERE at < now() - 13 mois` par lots de 10 000 (nom de table à lire dans `V1__*.sql`/`V2`), propriété `castbridge.telemetry.raw-retention-days` (défaut 395), test MockMvc/H2 avec des lignes datées.
3. `db/migration/README.md` : règle « nouvelle migration = plus haut numéro + 1, jamais de trou volontaire, jamais réutiliser un numéro d'une autre branche ; plages réservées : V50-59 licences, V60 ordres, V61 tunnel, V62+ libre ; vérifier `git log --all -- backend/src/main/resources/db/migration` avant de choisir ; `wip/external-ai-changes` contient un `V4` à ne jamais fusionner ».
4. `BundleCatalogController` : plafond 1 Mio (413 au-delà), `ETag` = SHA-256 court du contenu, `Cache-Control: no-cache`, cache mémoire invalidé par `lastModified`. Test API (`BundleCatalogApiTest.java` existe, non commité : l'étendre).
5. `docs/LICENSE-ADMIN.md` § 3.6 : générer la nouvelle clé (nouveau `kid`), l'ajouter aux clés de confiance des TV **avant** toute émission (nouvelle APK), période de chevauchement, émettre une révocation `cbr1` de l'ancien `kid` signée par la clé de secours, vérifier via `GET /api/v1/revocations`, archiver l'ancienne clé hors ligne.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test        # vert (193+ tests)
grep -n 'deviceName' android/receiver/src/main/kotlin/castbridge/receiver/TvConnect.kt    # null ou haché
grep -n '@Scheduled' backend/src/main/java/castbridge/server/telemetry/*.java   # ≥ 1
test -f backend/src/main/resources/db/migration/README.md
grep -n '3.6' docs/LICENSE-ADMIN.md
```

## Cas limites
- Une TV ancienne continue d'envoyer `deviceName` : le serveur doit l'ignorer (ne pas stocker) sans erreur.
- La purge ne doit pas toucher les tables agrégées (`kpi_*`).

## À ne pas faire
Pas de déploiement, pas de commit sur les branches partagées, aucune valeur de secret, pas d'édition des migrations existantes, pas de `V61` (appartient au tunnel, w2-15).

## Rapport
`STATUT`, migrations ajoutées (numéro), sorties des commandes, décision prise pour `deviceName` (null vs haché) avec justification.
