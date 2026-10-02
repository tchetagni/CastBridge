# w3-14 — Campagne de test sur la vraie TV GaiaOS et le Samsung S21+ : liste de contrôle, scripts, `rental_test` sur matériel

**Vague 3 · Effort M (≈ 1 j d'agent + 1 journée avec le propriétaire) · Statut PRÊT** (après la vague 1 et w2-01..05 ; **le propriétaire doit être présent** : installation par l'explorateur de la clé USB, PIN, Bluetooth réel). Branche `claude/sonnet-w3-14`. Rapport : `docs/agent-reports/sonnet-w3-14.md`.

## Objectif
1. `docs/TEST-CAMPAIGN.md` : liste de contrôle numérotée (≈ 40 étapes, français) couvrant installation verrouillée, code d'appareil, activation par fichier/Bluetooth/saisie, restrictions d'essai (tuiles et routes), fenêtre 12 h, plafond d'usage, passage en version complète, révocation, location (livraison, +25 h, balayage, recul d'horloge, redémarrage), coupure de courant, retrait USB, PIN, Apprendre au D-pad, Langues, Parental, badge sur chaque écran, mise à jour par USB, streaming et multivoie — avec pour chaque étape le **résultat attendu** et **la commande de vérification** (`curl`, `GET /api/activation`, `GET /api/rental`, `adb logcat`).
2. `tools/device/` : `api-smoke.sh TV PIN` (20 routes, codes attendus en essai vs production), `clock.sh` (avancer/reculer l'heure : `adb shell cmd alarm set-time` sur émulateur ; sur la TV : procédure manuelle Réglages), `screens.sh` (captures `GET /api/screenshot` de chaque écran dans un dossier daté).
3. `tools/rental-test/rental_test.py` : fonctionne contre la **vraie TV** (build de test avec clés de test : `docs/HANDOFF.md` § 0 2026-10-02 nuit) ; ajouter l'envoi du catalogue signé (w3-03) et un `--restore-expired` (étape 30 : lot copié avant expiration → 422).
4. Le rapport de campagne remplit `docs/HANDOFF.md` § 9 (ce qui est désormais vérifié / ce qui ne l'est pas).

## Pourquoi (preuves)
- 0 test instrumenté (`find android -path '*androidTest*'` vide) : `ActivationCenter`, `TvService`, `RentalHub.sweep`, `KeyBadgeOverlay`, Bluetooth réel ne se vérifient que sur appareil ; `docs/HANDOFF.md` § 9 liste tout ce qui n'a jamais tourné sur la TV (locations, livraison téléphone → TV, Bluetooth d'activation, parental, multivoie, démarrage auto, focus D-pad).
- `tools/rental-test/README.md` : prouvé sur émulateur seulement ; routes réelles : `/api/activation`, `/api/activation/{install,request}`, `/api/rental`, `/api/rental/{install,sweep}` (il n'existe pas de `/api/rental/status`).
- Audit : TE-1, TE-5 ; plan de 40 étapes de l'audit tests (§ 8), à reprendre et compléter.

## Fichiers possédés
Nouveau `docs/TEST-CAMPAIGN.md`, nouveau dossier `tools/device/`, `tools/rental-test/**`, `docs/HANDOFF.md` (**§ 9 seulement**, après la campagne). **Hors zone** : code des apps (les défauts trouvés deviennent des **fiches** dans le rapport, pas des corrections).

## Étapes
1. Rédiger `TEST-CAMPAIGN.md` à partir du plan de 40 étapes (audit tests § 8), en vérifiant chaque route/commande citée contre le code (`grep -rn '"/api/' android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt android/receiver/src/main/kotlin/castbridge/receiver/*.kt`) ; préciser pour chaque étape : préparation, action (télécommande ou téléphone), attendu, vérification, et « bloquant / majeur / mineur ».
2. `api-smoke.sh` : tableau route → code attendu (essai / production) ; sortie en tableau ; code 1 si écart.
3. `screens.sh` : boucle sur les écrans joignables par l'API (`/api/games/open`, lecteur, Apprendre via `/api/learn/...`) et `GET /api/screenshot` ; nommer `YYYY-MM-DD/<ecran>.png`.
4. `rental_test.py` : mode `--real-tv` (pas de `cmd alarm`, demande de confirmation avant chaque étape qui exige une action à la télécommande) ; envoi du catalogue ; `--restore-expired`.
5. Exécuter la campagne avec le propriétaire (émulateur d'abord pour valider les scripts, puis TV) ; consigner chaque écart avec `id`, étape, attendu, observé, capture, gravité, et le cahier de la vague suivante qui devrait le corriger.

## Critères d'acceptation
```sh
bash -n tools/device/*.sh
bash tools/device/api-smoke.sh http://127.0.0.1:8765 000000 --dry-run     # imprime le tableau attendu sans réseau
python3 tools/rental-test/rental_test.py --help | grep -c 'real-tv\|restore-expired'   # 2
test -f docs/TEST-CAMPAIGN.md && grep -c '^[0-9]\+\.' docs/TEST-CAMPAIGN.md   # ≥ 40
```
Après campagne : rapport avec le tableau des 40 étapes (OK/KO), captures, liste d'écarts classés ; `docs/HANDOFF.md` § 9 mis à jour.

## Cas limites
- La TV n'a pas `adb` réseau (port 5555 fermé, `docs/HANDOFF.md` § 0 2026-10-01) : l'heure se change dans les réglages CVTE ; le dire pas à pas.
- Le build de test (clés de test) ne doit **jamais** être publié ; la campagne « production » utilise la build verrouillée réelle et une clé émise par la console du propriétaire.

## À ne pas faire
Pas de commit sur les branches partagées, pas de publication, pas de correction de code, aucun PIN ni jeton dans les rapports ou captures (flouter/retirer), pas de secret.

## Rapport
`STATUT`, scripts livrés, résultats de la campagne (tableau), écarts → cahiers.
