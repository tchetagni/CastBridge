# w16-05 — Bureau et Python : `emettre --location-choix`, `--pilote`, `--registre`, commande `louer` (dossier de livraison), `rapport-usage` ; `tools/pilot/louer.py` (LAN ou relais téléphone), `tools/pilot/bilan.py`

<!-- routage Fable 2026-10-03 -->
> **Amendement W16-04 (audit Opus, 2026-10-03)** : exiger un **relevé d'usage FRAIS** (GET /api/rental ou fichier `castbridge-rental-usage-v1` du jour) avant toute **prolongation** ou **réémission** d'une location en heures : sans lui, la ligne part, compte au quota (168 h glissantes, `PilotRegistry.hoursIssuedLast168h`) et se perd après le balayage. La réémission exige aussi la clé d'installation d'origine (`ContractSummary.installPub`) et celle, différente, de la TV (`PilotRules.reissue(..., newInstallPub, ...)`) ; le code TV du registre passe par `PilotRegistry.mask` ; écrire par `PilotRegistry.update(file)` (atomique, verrou) ; `pilot.json` n'accepte que les clés connues (dont `freeBundles`).
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (chemin d'émission réel) · statut : PRÊT (après w16-03, w16-04)
> **Groupe : W16b-1** (vague W16b, outils, autorisé pendant le gel) · prérequis : w16-03, w16-04 · porte : `cd tools/activation-desktop && tools/agents/gradle-lock.sh gradle --offline test && python3 -m unittest discover -s tools/tests -p 'test_pilot*.py'`
> **Jauge : ≈ 500 k jetons entrée / 25 k sortie** (effort M-L, ≈ 2,5 j) · audit Opus : échantillon

**Vague 16b · Effort M-L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 3.2, § 5.3. Branche `claude/sonnet-w16-05`. Rapport : `docs/agent-reports/sonnet-w16-05.md`. Mémoire du propriétaire : relais Mac → téléphone (ADB) → TV par `nc` ; **jamais** de PIN ni de mot de passe en argument ; builds verrouillés seulement.

## Objectif
Depuis son Mac, sans serveur (module licences éteint : `backend/src/main/resources/application.yml:112-114`), le propriétaire émet une location **à durée choisie** (défaut, jours, heures) pour une TV, scelle les lots, obtient un **dossier de livraison** que l'écran existant « Locations sur la TV » du téléphone consomme tel quel, ou livre directement par le LAN/relais ; il relève l'usage et calcule le bilan du pilote.

## Pourquoi (preuves)
- `DK/Cli.kt:246-275` (`issue` : `--location`, `--location-bouquet`, `--catalogue`, `--periode`, `--sans-controle-catalogue`), `:312-325` (`lot-chiffrer`), `:406-413` (aide).
- `tools/rental-test/rental_test.py:17-80` (demande, émission, scellement, upload par morceaux, `/api/rental/install`) : **à factoriser** dans `tools/pilot/rentalops.py` (w10-13 le demandait aussi ; `rental_test.py` reste **intact**, hors zone).
- `S/RentalDeliveryActivity.kt:55-61, 72` : l'écran prend des fichiers `.lot`, `catalog.json` et un fichier `contract` (première ligne = « produit@période »).
- `PilotRules`, `PilotRegistry`, `RightsSyntax.rentalChoice` (w16-04) ; `RentalUsageReport` (w16-03).

## Fichiers possédés
`DK/Cli.kt` (zones `issue`, `sealLot`, aide ; nouvelles commandes `louer`, `rapport-usage`), `DK/Desk.kt` (zone `rentalMaster` si besoin), `tools/activation-desktop/src/test/kotlin/castbridge/desktop/RentalCliTest.kt` (tests ajoutés), nouveau `tools/activation-desktop/src/test/kotlin/castbridge/desktop/PilotCliTest.kt` ; nouveaux `tools/pilot/{louer.py,bilan.py,rentalops.py,pilot.example.json,README.md}`, `tools/tests/test_pilot_louer.py`, `tools/tests/test_pilot_bilan.py`. **Hors zone** : `tools/rental-test/**`, `C/**`, `R/`, `S/`, `backend/`.

## Étapes
1. **Rouge** : `PilotCliTest` : `emettre --location-choix classe-cm2=12h --pilote p.json --registre r.csv` écrit une activation dont la ligne `rental` a `maxUsageMinutes = 720` et `durationDays` = borne ; `=7j` ⇒ 7 / 0 ; `=defaut` ⇒ 30 / 0 ; refus après `pilot.end` ; refus sans `--catalogue` (lot libre non vérifié) sauf `--sans-controle-catalogue` ; `--prolonger` exige `--periode` et la même unité ; registre écrit ; `louer` produit le dossier (activation, `.lot` scellés, `catalog.json`, `contract`, `LISEZMOI.txt`) ; `rapport-usage --fichier releve.txt --registre r.csv` fusionne. Python : serveur HTTP factice (comme `tools/tests` existants) rejouant `/api/activation/request`, `/api/activation/install`, `/api/lots/part`, `/api/lots/upload`, `/api/rental/install`, `/api/rental`, `/api/rental/usage` ; `desk` simulé (`--desk-cmd`) ; reprise après coupure ; `bilan.py` sur un registre + relevés d'exemple ⇒ tableau HP1-HP10.
2. `Cli` : `--location-choix b=<defaut|Nj|Nh>` (répétable), `--pilote FICHIER` (obligatoire avec `--location-choix`), `--registre FICHIER` (défaut `~/.castbridge-activation/pilot-rentals.csv`), `--prolonger` (+ `--periode`), `--encore` (lève l'idempotence) ; `louer --appareil … --bouquet … --choix … --lots DIR --catalogue … --sortie DIR` = `emettre` + `lot-chiffrer` × lots du bouquet + dossier ; `rapport-usage`. Les sorties disent l'unité et la **date de fin réelle** en français.
3. `louer.py location|prolonger|releve|status|sweep|apercus` avec `--tv URL|--via-adb PORT` (affiche les commandes `adb forward` exactes), `--pin-file`, `--pass-file`, `--journal pilote/livraisons.csv` ; `releve` écrit `releves/<install>-<date>.txt`.
4. `bilan.py --registre --releves [--telemetrie export.csv] --sortie BILAN.md` : parts heures/jours/défaut, paliers, `used/max`, intensité jours, abandon, prolongations, 96 h atteints, seuils colorés (texte).
5. `README.md` : procédure pas à pas du pilote (clé de bureau réelle, build TV verrouillé qui lui fait confiance, temps par TV, pannes courantes).
6. Vert : porte.

## Critères d'acceptation
Porte verte ; `grep -n 'pin\|pass' tools/pilot/louer.py | grep -i 'argv\|print'` vide ; `python3 tools/pilot/louer.py --help` liste 6 sous-commandes ; `tools/activation-desktop` : tests existants (`RentalCliTest`, `VectorsTest`) verts ; aucun secret dans le dépôt.

## Cas limites
TV sans `install=` (vieille) ⇒ refus existant (`--enveloppe-v1` avant le coucher) ; bouquet inconnu du catalogue ; lot déjà sur la TV (« déjà en place ») ; TV en essai (`/api/rental/install` refusé ⇒ « clé de production requise ») ; relevé d'une autre installation (fichier séparé).

## À ne pas faire
Ne pas modifier `rental_test.py` ; pas de PIN/mot de passe en argument ni en journal ; ne pas contourner `PilotRules` ; messages français ; aucun montant.

## Rapport
`STATUT`, sorties rouge/vert, aide complète des commandes, fonctions copiées de `rental_test.py` (proposition de bascule de `rental_test.py` sur `rentalops.py`, hors zone), temps mesuré par livraison si une TV était disponible.
