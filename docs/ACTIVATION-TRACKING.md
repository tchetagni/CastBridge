# Suivi des codes d'activation (essai et production) : guide du propriétaire et contrat d'intégration

> Module serveur `castbridge.server.activations` (W23, cahier `w23-01`). **Éteint par défaut.** Conception : `docs/coordination/DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md`.
> Ce que le module suit : les **codes et clés d'activation `cbx1` d'essai et de production** (clé compacte comprise), les TV qui les portent, les commandes du propriétaire exécutées sur une TV.
> Ce qu'il ne suit **pas** : les soldes NDEM/MBOKO. Il ne révoque, ne suspend et ne libère **jamais** rien tout seul : ses alertes sont **douces**.

## 1. Décisions du propriétaire (2026-10-04), appliquées telles quelles

| Décision | Dans le module |
|---|---|
| Un code d'activation (essai ou production) est valable **exactement 48 h après son émission** (fenêtre d'installation) ; passé ce délai il **expire** et ne peut plus être installé | `act_key.expires_at` = émission + 48 h ; un jeton dont la fenêtre dépasse 48 h, ou installé plus de 24 h après sa fermeture (marge d'horloge de la TV), ouvre l'alerte `OUT_OF_WINDOW` |
| Une activation **déjà installée garde sa propre durée** | le plafond d'usage (`usage_to`, essai 30 j par défaut, production à durée choisie ou **illimitée**) ne dépend plus des 48 h : une production illimitée reste `ACTIVATED`, un essai passe `ENDED` à son plafond |
| Un code émis **jamais vu sur une TV 48 h après son émission** est **« expiré non utilisé »** | état `EXPIRED_UNUSED` (affiché « expiré non utilisé ») ; un constat tardif (rapport de la TV, remise Bluetooth acquittée) le fait repasser `ACTIVATED` |
| Le **téléphone propriétaire reste hors ligne** pendant la phase hors ligne ; ses journaux sortent par fichier, partage ou QR | aucune route de téléphone connecté n'est nécessaire : le journal se téléverse depuis le navigateur ou `curl` ; la session de console en ligne (`ConsoleSession`) est **désactivée** et non câblée |
| Le **rapport d'activation de la TV** (cahier w23-04) demande une **exception de gel d'écran** (aucun écran visible) | route `POST /api/v1/activations/report` livrée côté serveur ; la partie TV attend l'exception (D-W23-7) |

## 2. Mise en route (actes du propriétaire)

1. Module des licences **allumé** (`CASTBRIDGE_LICENSES_ENABLED`) et clés publiques du bureau et du téléphone dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` (le nom de la clé dit l'outil : `desktop…` = bureau, `phone…` = téléphone, `agent…`, `server`).
2. Dans le **dossier des secrets** du module des licences (`CASTBRIDGE_LICENSES_SECRETS_DIR`) : `act-ref.key` (**obligatoire** : au moins 32 octets aléatoires ; sans elle tout le module répond **503** « Suivi des activations indisponible »), `act-audit.key` (facultative : chaînes en HMAC), `act-checkpoint.key` (facultative : graine Ed25519 en base64, signe les points de contrôle). **Choisir les trois avant la première écriture et ne plus les changer.**
3. `CASTBRIDGE_ACTIVATIONS_ENABLED=1` (reste 404 sans cela). Autres réglages : `CASTBRIDGE_ACTIVATIONS_ARCHIVE_DIR` (archive froide, hors de l'image), `CASTBRIDGE_ACTIVATIONS_EXPORT_MAX_ROWS` (200 000), `CASTBRIDGE_ACTIVATIONS_CONSOLE_SESSIONS` (faux).
4. La migration **V65** crée 19 tables `act_*` / `adm_read_audit` (aucune table existante n'est modifiée). Numéros : V62 (télémétrie W21), V63 (grand livre W22) et V64 (audit anti-triche W21) sont **réservés à d'autres chantiers et non créés ici** ; Flyway n'exige pas la contiguïté (V61 puis V65 démarre), la règle « plus haut + 1 » de `db/migration/README.md` est **volontairement** non suivie pour ce numéro réservé. Si V65 est pris à la fusion : prendre « plus haut + 1 » et mettre le nom du fichier à jour dans `SourceRulesTest` et `ActivationsOffTest`.
5. Importer une première fois le registre (route existante) et téléverser les journaux des outils (§ 4).

**Sauvegarde et restauration.** `backend/backup.sh` fait un `mysqldump` **complet** : il contient déjà toutes les tables `act_*` et `adm_read_audit` (vérifié dans le script) ; il n'en fait pas de dump séparé (contrairement aux `lic_*`) et ne journalise pas les têtes de chaîne : à ajouter par le propriétaire (acte hors de ce cahier, `backup.sh` n'est pas dans ses fichiers) : `SELECT id, last_id, last_hash FROM act_event_head`. **Hors base, à sauvegarder à la main, chiffrées, ailleurs que sur le serveur** : `act-ref.key` (sans elle, les `tv_ref` ne se recalculent plus : l'historique reste mais ne se relie plus aux codes), `act-audit.key`, `act-checkpoint.key`. Après une restauration : `GET /api/v1/admin/activations/integrity` (les deux chaînes) puis comparer avec le dernier point de contrôle gardé hors du serveur ; les journaux et rapports postérieurs se **rejouent** sans doublon (clé d'idempotence `idem_key`, empreinte du lot).

## 3. Les quatre sources, rapprochées au serveur

| Source | Voie | Ce qu'elle apporte |
|---|---|---|
| Émissions du **serveur** | `IssuanceTap` lit `lic_issuance` (curseur, 60 s) | activation (empreinte du jeton), licence, poste, TV, drapeau `server_issued` |
| **Registre** signé importé | `LicenseAuditTap` lit `lic_ledger_import`, `lic_transfer`, `lic_revocation`, `lic_audit` ; `IssuanceTap` lit les émissions importées | évènements `REGISTRY_IMPORT`, `TRANSFERRED`, `REVOKED_KEY/SEAT`, `LICENSE_CHANGED`, `SEAT_RELEASED` ; couple `(kid, nonce)` qui fait reconnaître l'activation comme **déclarée** (le registre ne porte pas le jeton : empreinte différente) |
| **Journal signé** d'un outil hors ligne | `POST /api/v1/admin/activations/journal` | clés compactes, commandes, remises, refus, numérotation dense qui rend un trou visible |
| **Rapport de la TV** | `POST /api/v1/activations/report` (jeton d'appareil) ; `ActivationObserver.observe(...)` pour le coursier W21 et, plus tard, `wallet/sync` | « activé sur quelle TV », édition, version, commandes actives |

Le module des licences n'est **jamais écrit** (test : aucune requête d'écriture sur `lic_*` dans le code du module, et empreinte des tables `lic_*` identique avant/après le scénario).

## 4. Journal d'émission signé (enveloppe `cbx1` de type `journal`)

Codec unique du format (`docs/ACTIVATION-FORMAT.md` § 3) : `target=any`, `seq` = numéro du **lot** pour la clé de l'outil (la clé signataire ; une clé ne déclare que ses propres actions), `expiresAt` = émission + 365 j. Corps :

```
tool=<desk|phone|agent>
app=<version de l'outil>
from=<n° de la première entrée>        to=<n° de la dernière entrée>
prev=<64 hex : empreinte de l'entrée from-1, ou 64 zéros pour la toute première>
e=<n>|<at ms>|<type>|<nom=valeur>|<nom=valeur>…        (une ligne par entrée, n dense et croissant, ≤ 500 entrées, ≤ 128 Ko)
```

Empreinte d'entrée : `h(n) = SHA-256( h(n-1) + "|" + la ligne e=… entière )`, `h(0)` = 64 zéros. Types et champs (jamais de secret) :

| Type | Champs |
|---|---|
| `issue` | `fp` (SHA-256 du jeton), `form=envelope`, `kind` (`trial`/`production`), `subject`, `license`, `seat`, `device` (code d'appareil), `k`, `nonce`, `aseq` (`seq` de l'activation), `issuedAt`, `expiresAt` (+48 h), `usageTo` (ms) **ou** `unlimited=1`, `super` 0/1, `rights` (`-` ou `genre:nombre,…` trié, `usage` compris : même résumé que le serveur), `registry` (facultatif) |
| `compact` | `fp` (SHA-256 des 82 octets), `form=compact`, `kind`, `device`, `windowStartHour` (heures depuis 2026-01-01T00:00Z), `set` |
| `deliver` | `fp`, `way` (`bt`/`usb`/`qr`/`text`), `tv` (`ok`, `refused:<raison>`, `-`) : `tv=ok` vaut **constat** |
| `command` | `power` (`support`/`unlock`/`open_all`), `action` (`diagnostic`/`reset-trial`/`-`), `bundles`, `lots`, `days`, `clamped`, `device`, `challenge` (8 premiers hex de SHA-256 du défi), `result` (`ok`/`refused:<raison>`) |
| `license`, `revoke`, `transfer` | `registry` (identifiant de l'évènement du registre), `target=key` pour une révocation de clé |
| `refused` | `reason`, `device` |

Résultat d'un téléversement (HTTP) : `OK` ou `DUPLICATE` → 200 ; `QUARANTINE` (clé inconnue ou révoquée, signature fausse, entrée **réécrite** : même numéro, autre empreinte ; chaîne qui ne prolonge pas la dernière entrée connue) → **422**, lot gardé tel quel, alerte `JOURNAL_BROKEN`, rien d'appliqué ; `REJECT` : mal formé / trop gros → 400, numéro de lot qui recule pour des entrées nouvelles → 409. Un **trou** (entrées jamais reçues) est accepté et mémorisé ; il devient l'alerte `JOURNAL_GAP` après 7 jours, et se referme tout seul quand le lot manquant arrive (même avec un numéro de lot plus ancien).
**Vecteurs** : `tools/activation/journal-vectors.json` (`castbridge-journal-vectors-v1`, clés de test dérivées de textes publics), produits par `JournalVectorsTest` avec le résultat attendu de chaque étape écrit à la main, et **consommés** par le même test comme le fera w23-03 en Kotlin (`-Dcastbridge.vectors.write=true` pour les régénérer).

## 5. Rapport de la TV (`POST /api/v1/activations/report`)

`Authorization: Bearer <jeton d'appareil>` ; corps JSON ≤ 16 Ko : `{"v":1,"deviceCode":"…","app":{"code":1412,"name":"…"},"activations":["cbx1.…"],"compact":["<82 octets en base64>"],"state":{"edition":"TRIAL|PRODUCTION|SUPER|NONE|ENDED","usageTo":null,"super":false,"openAllUntil":0,"unlockUntil":0,"trialResets":0,"installedAt":{"<fp8>":ms},"commands":[["open_all","<défi 8 hex>",ms,30]]},"at":ms}`. Au plus 4 jetons (8 192 caractères), 2 clés compactes. Chaque jeton est **vérifié** (clé de l'anneau, signature, appareil visé), réduit à son empreinte et à ses champs, puis **jeté** : aucun jeton n'est écrit en base ni dans un journal (test : recherche de `cbx1.` dans toutes les tables du module et dans la sortie capturée). Jeton d'un autre appareil → ignoré + `CLONE` ; clé hors anneau → `UNKNOWN_KEY` ; signature fausse ou jeton mal formé → `BAD_TOKEN` ; fenêtre hors 48 h → `OUT_OF_WINDOW`. Un rapport identique n'écrit **aucun** évènement. Débit : 1 par 10 min et par appareil (429), 3 000 par minute pour tous. Réponse : `{"next": heures, "accepted", "ignored"}` (`next = 0` coupe les rapports, réglage `act_policy.report_next_hours`). **Non fait** : la liste de révocations signée dans la réponse (`revocations`) : elle demande le signeur du module des licences.

## 6. États, drapeaux, alertes

**États** (dérivés des faits et de l'horloge, jamais d'un chemin) : `EMISE` (émise, fenêtre ouverte), `ACTIVATED` (constatée), `EXPIRED_UNUSED`, `REPLACED` (une activation plus récente est constatée sur la même TV), `ENDED` (plafond d'usage atteint), `REVOKED` (clé ou poste révoqué à la date de l'émission ou après). **Drapeaux** : `declared_journal`, `declared_registry`, `server_issued`, `seen_on_tv`, `delivered_bt`, `undeclared` (vue sur une TV et déclarée par aucune source), `clone`, `out_of_window`.

| Alerte | Gravité | Condition | Se ferme seule |
|---|---|---|---|
| `UNDECLARED` | haute | vue sur une TV, déclarée par rien, 72 h après le premier constat | oui |
| `JOURNAL_GAP` | haute | entrées manquantes, 7 j | oui |
| `JOURNAL_BROKEN` | critique | lot en quarantaine | non |
| `CLONE` | haute | jeton d'un autre appareil, ou 2 installations de matériels différents derrière un code en 30 j | non |
| `OUT_OF_WINDOW` | haute | fenêtre > 48 h ou installation tardive | non |
| `ENDED_IN_USE` | moyenne | TV encore « activée » 7 j après le plafond, ou activation révoquée encore portée 7 j après | oui |
| `UNKNOWN_KEY` / `BAD_TOKEN` | haute | clé hors anneau / jeton faux | non |
| `LICENSE_PENDING` | basse | production en service sans poste connu du serveur, 7 j | oui |
| `OVER_SEATS` | moyenne | plus de TV constatées que de postes permis | oui |
| `UNDECLARED_COMMAND` | haute | commande rapportée par une TV sans entrée de journal, 72 h | oui |
| `TOOL_STALE` | moyenne | outil dont les activations sont vues mais aucun journal depuis 14 j | oui |

Une alerte par objet (clé `open_key` unique tant qu'elle n'est pas classée) ; un nouvel indice incrémente `hits`, rien d'autre. Accuser réception : `ACT_ALERT_ACK` ; classer avec motif : `ACT_ALERT_DECIDE` (TOTP). Seuils modifiables dans `act_policy` (bornés, chaque changement est un évènement `POLICY_CHANGED`) : `undeclared_grace_hours` 72, `journal_gap_days` 7, `tool_stale_days` 14, `silent_production_days` 30, `silent_trial_days` 14, `report_next_hours` 24, `ended_grace_days` 7, `license_pending_days` 7. Réconciliation : à chaque arrivée pour les objets touchés, et chaque nuit à 03:50 (Douala) pour tout.

## 7. API d'administration (`/api/v1/admin/activations/**`, jeton d'API = propriétaire, audité sous `api-token`)

`GET activations`, `activations/{fp}`, `tvs`, `tvs/{code}`, `dashboard`, `alerts`, `tools`, `changes?after=&wait=`, `export?what=activations|tvs|events|alerts|reads&format=csv|jsonl`, `checkpoints?from=&to=`, `integrity`, `read-audit` ; `POST journal`, `alerts/{id}/ack`, `alerts/{id}/close` (`{"reason"}`), `archive` (`{"reason","remove":true|false}`). Listes : curseur `(clé de tri, id)`, `limit` 50 (≤ 500), **aucun** `OFFSET` ni `COUNT(*)` sur `act_event`. Filtres d'`activations` : `state`, `kind`, `tool` (type ou `kid`), `license`, `device` (code entier ou 4 derniers caractères), `from`, `to`, `flag`, `q` (étiquette de 8 hex), `sort=issued|seen`. `changes` : interrogation longue ≤ 25 s (bloque un fil de requête, 1 par session, 20 en tout ; une ligne d'audit par session de suivi). Débits : lectures 120/min/acteur, exports 10/h/acteur, journaux 30/h/clé.

| Permission | Sensible (TOTP) | OWNER | SUPPORT | READONLY |
|---|---|---|---|---|
| `ACT_READ` | non | oui | oui | oui |
| `ACT_ALERT_ACK` | non | oui | oui | non |
| `ACT_ALERT_DECIDE` | oui | oui | non | non |
| `ACT_EXPORT` (exports, points de contrôle, audit des lectures) | oui | oui | non | non |
| `ACT_JOURNAL_UPLOAD` | non (le lot est signé) | oui | oui | non |
| `ACT_ARCHIVE` | oui | oui | non | non |

Module éteint : **404**. Sans `act-ref.key` : **503**. Les services (`ActivationsAdminService`, `Exporter`, `Archiver`, `JournalService`, `AlertService`) sont publiés pour les pages web de w23-02 : chacun vérifie sa permission lui-même.

## 8. Historique, preuve, archive, effacement

* **Historique immuable** `act_event` : chaîné (tête verrouillée à chaque ajout, HMAC si `act-audit.key`), `idem_key` unique : rejouer une source ne double rien. Types fermés (ceux de la conception, plus `REFUSED` et `ARCHIVED`). La TV n'y est désignée que par `tv_ref = HMAC(act-ref.key, code)[0:8]` ; **jamais** un code entier, un jeton, une clé compacte, un défi : l'écriture refuse un champ qui a la forme d'un code d'appareil.
* **Audit des lectures** `adm_read_audit` : une ligne par lecture (route, filtres normalisés ≤ 300 caractères, jetons et codes masqués, cible `tv_ref` ou 8 hex d'empreinte, lignes rendues, export oui/non), chaînée de même ; une lecture qui finit en 404 est aussi une ligne (un sondage se voit). Écrite après la réponse : un échec d'écriture est journalisé (sans donnée) mais ne bloque pas la lecture.
* **Points de contrôle** (00:10 Douala) : têtes des deux chaînes et compteurs, signés Ed25519 (`act-checkpoint.key`), sinon HMAC, sinon SHA-256. À télécharger (`GET checkpoints`) et **garder hors du serveur**. **Vérification hors ligne** : `python3 tools/activations/verify_export.py --events events.jsonl --checkpoints checkpoints.json --audit-key-file act-audit.key --public-key <base64>` sur un export `what=events` (« chaîne intacte jusqu'au N » ou la première ligne en cause ; code 1 en cas d'anomalie, 2 si une vérification n'a pas pu être faite). Test : `tools/activations/test_verify_export.py`.
* **Instantanés** : comptes par jour (`act_daily`, 00:05) et une ligne par TV et par mois (`act_tv_monthly`).
* **Archive froide** (`POST archive`, jamais automatique) : les lignes de plus de 24 mois deviennent `act_event-AAAA-MM.jsonl.gz` (SHA-256 dans `act_archive`) ; avec `remove=true` elles quittent la base et la chaîne repart de leur dernière empreinte (la vérification en tient compte). Les rapports bruts des TV sont purgés à 90 jours.
* **Droit à l'effacement** : quand le module des licences anonymise un client, un travail (5 min) efface `act_tv.device_code` de ses TV et le code lu dans les lots de journal signés (le lot est marqué effacé : sa signature n'est plus vérifiable), et écrit `ERASED`. La chaîne reste vérifiable. Limite : une TV vue seulement par un essai (aucun poste dans une licence) ne se relie à aucun client.

## 9. Limites connues

* Réponse du rapport sans liste de révocations ; pas de « dossier complet » ZIP ; la session de console du téléphone (`ConsoleSession`, option A) est écrite et testée mais **non câblée** à la chaîne de sécurité (`SecurityConfig`, hors fichiers du cahier) : un filtre doit appeler `authenticate`.
* `changes` bloque un fil de requête (un `DeferredResult` serait refusé par la chaîne de sécurité à la reprise asynchrone).
* Une émission du **registre** n'a pas de ligne d'inventaire tant que son jeton n'a pas été vu (le registre ne porte pas le jeton) : elle est un évènement et un couple `(kid, nonce)`.
* Une TV dont le matériel a changé (k parmi n) et dont l'activation n'est liée à aucun journal peut déclencher une fausse alerte `CLONE` (douce).
* Rien n'a tourné contre le MySQL de production (voir le rapport).
