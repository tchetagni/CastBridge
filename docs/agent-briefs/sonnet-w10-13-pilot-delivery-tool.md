# w10-13 — Outil de livraison du pilote : `tools/producer-studio/livrer.py` (émission d'une location d'œuvre avec l'outil de bureau, scellement, envoi et installation sur la TV, état, balayage)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10d-3** (vague W10d) · prérequis : w10-04, w10-08 · porte : `python3 -m unittest discover -s tools/tests -p 'test_livrer.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10d · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 10.4 (A et B, point 7), § 14 (ligne « Outils du propriétaire »). Branche `claude/sonnet-w10-13`. Rapport : `docs/agent-reports/sonnet-w10-13.md`. Dépend de w10-04 (lots d'œuvres et catalogue des bouquets avec `oeuvre-*`), w10-08 (routes TV : `/api/oeuvres`, routage de `/api/rental/install`).

## Objectif
Le propriétaire, depuis son Mac (sur le LAN de la TV, ou par le **relais téléphone** décrit dans sa mémoire : `nc` sur le téléphone en ADB), **livre une location d'œuvre ou de chaîne** à une TV sans serveur ni W5 : demande d'appareil → `emettre --location oeuvre-<id>=…:30` (outil de bureau, clé réelle) → `lot-chiffrer` des lots du bouquet → envoi par morceaux → `/api/rental/install` → état ; plus `--status`, `--sweep`, `--apercus DIR` (pousse les aperçus en clair) et un **journal** de livraisons (CSV) pour le pilote papier.

## Pourquoi (preuves)
- `tools/rental-test/rental_test.py:17-80` : `call`, `desk`, `emettre --location`, `lot-chiffrer`, boucle d'upload, `/api/rental/install` : **à généraliser, pas à dupliquer** (import ou extraction d'un module commun `tools/rental-test/rentalops.py` **non** : hors zone ⇒ copier les fonctions dans `livrer.py` avec mention d'origine, puis proposer la factorisation au rapport).
- `docs/ACTIVATION-TOOLS.md` § 8 (`--catalogue`, `--location-bouquet`, durée exacte vérifiée) ; `docs/RENTAL-LOTS.md` § 9.
- Mémoire du propriétaire : relais Mac → téléphone (ADB) → TV par `nc` : paramètre `--via-adb <port>` (tunnel `adb forward` + `nc`, documenté, **jamais** de PIN dans la ligne de commande : `--pin-file` ou variable).

## Fichiers possédés
Nouveaux `tools/producer-studio/livrer.py`, `tools/tests/test_livrer.py`, `tools/producer-studio/LIVRAISON.md` ; `tools/rental-test/README.md` (une ligne de renvoi). **Hors zone** : `tools/rental-test/rental_test.py`, `tools/activation-desktop/**`, code Android/serveur.

## Étapes
1. `livrer.py location --tv URL --pin-file F --desk DOSSIER --pass-file P --catalogue bundles-catalog.json --bouquet oeuvre-ndolo-kwata-01 [--jours 30] --lots content/oeuvres/dist [--licence ID] [--via-adb 8765] [--journal pilote/livraisons.csv]` : (a) `GET /api/activation/request` (demande v2 si la TV la fournit, v1 sinon) ; (b) `emettre --appareil … --production --licence … --catalogue … --location-bouquet <bouquet> --sortie tmp` (durée **du catalogue**, jamais saisie ; refus si bouquet absent ou lot libre) ; (c) `POST /api/activation/install` ; (d) pour chaque lot du bouquet : `lot-chiffrer --activation … --produit loc-<bouquet> --lot … --sortie sealed/` puis upload par morceaux de 512 Ko (`/api/lots/part`, `/api/lots/upload`, reprise) puis `POST /api/rental/install?name&contract` (corps = catalogue de lots signé) ; (e) `GET /api/rental` + `GET /api/oeuvres` ⇒ résumé ; (f) ligne CSV `date,tv(code masqué),bouquet,jours,contrat,lots,statut`.
2. `livrer.py apercus --tv … --lots DIR` : pousse les lots `oeuvre:*-trial` + preuve (catalogue) en clair (`/api/lots/install`), sans activation.
3. `livrer.py status`, `livrer.py sweep` (`POST /api/rental/sweep`), `livrer.py retirer --oeuvre <id>` (`POST /api/oeuvres/remove`, refus 409 si louée en cours : affiché).
4. Sécurité : PIN et mot de passe du coffre **jamais** en argument ni dans le journal ; `--via-adb` affiche les commandes `adb forward` exactes et vérifie que le port répond ; refuse une URL non locale sans `--je-sais`.
5. `LIVRAISON.md` : procédure pas à pas du pilote (clé de bureau **réelle**, build TV verrouillé qui lui fait confiance : `-PtrustedKeysFile`, mémoire « builds verrouillés seulement »), temps estimé par TV, pannes courantes (TV hors de portée, PIN refusé, budget 200 Mo plein, lot libre).
6. Tests : serveur HTTP factice (comme `tools/tests` existants) rejouant les routes ; `desk` simulé (`--desk-cmd` injectable) ; reprise après coupure au milieu d'un upload ; refus d'un bouquet absent ; journal écrit.

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests -p 'test_livrer.py'      # vert
python3 tools/producer-studio/livrer.py --help                         # 5 sous-commandes
grep -n 'pin' tools/producer-studio/livrer.py | grep -i 'argv\|print' ; echo "(aucun PIN imprimé)"
# propriétaire, TV de référence : une œuvre d'exemple louée 2 jours, lue, puis horloge +3 j ⇒ sweep ⇒ retirée (aperçu conservé)
```

## Cas limites
- TV avec W5 fusionné : `livrer.py` reste utilisable (mêmes routes TV) ; le serveur n'en sait rien : le journal CSV sert d'import (w10-06 `sales/import`).
- Bouquet `chaine-<p>` : plusieurs lots ; un lot déjà installé (même version) : « déjà en place », pas de renvoi.
- TV en essai : `/api/rental/install` refusé ⇒ message « clé de production requise ».

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas modifier `rental_test.py` ; aucune clé ni PIN dans le dépôt ou les journaux ; ne pas contourner la durée du catalogue ; messages français.

## Rapport
`STATUT`, fonctions copiées de `rental_test.py` (proposition de module commun), temps mesuré par livraison (si TV disponible), questions.
