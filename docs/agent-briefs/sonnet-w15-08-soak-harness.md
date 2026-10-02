# w15-08 — Banc d'endurance `tools/soak/` (copies en boucle, coupures aléatoires, relevés mémoire, rapport) et route `GET /api/transfer/sessions`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune (scripts ; la route TV est additive et en lecture seule) · statut : PRÊT
> **Groupe : W15-S1** (vague W15, tranche S1) · prérequis : w15-01 fusionné (`TransferHost.progress/active`) ; W14 w14-10 (`FakeTvMain`) **souhaité** : s'il n'existe pas encore, `tools/soak/fake_tv.py` lance `gradle :core:soakTv` (tâche Gradle de ce cahier, `ReceiverServer` sur port libre, scénarios `fresh|slow|busy|pin-rotated|pause`) · porte : `python3 -m unittest discover -s tools/soak/tests` puis `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*MultipathServer*'`
> **Jauge : ≈ 350 k jetons entrée / 25 k sortie** (effort M, ≈ 2 j) · audit Opus : non

**Vague 15 S1 (outils + cœur TV lecture seule) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-08`. Rapport : `docs/agent-reports/sonnet-w15-08.md`.

## Objectif
Un script unique rejoue les scénarios de `docs/test-plans/ENDURANCE.md` § 2 (S-A, S-B, S-D, S-E, S-F ; S-C est humain) contre la fausse TV, la TV émulée ou la vraie TV **en lecture seule**, relève mémoire/handles/notifications/sessions, compare les SHA-256, et écrit un `REPORT.md` d'une page avec PASS/FAIL par seuil et la première preuve.

## Pourquoi
`ENDURANCE.md` (seuils et commandes) ; `PLAN-STABILISATION` § 4-5 (critères de sortie : trois nuits conformes) ; `tools/smoke` n'existe pas (W14 le prévoit : **ne pas** le créer ici) ; précédents à imiter : `tools/rental-test/rental_test.py` (Python stdlib, `--tv`, `--pin`), `tools/transfer-bench/` (banc), `tools/agents/gradle-lock.sh` (verrou `mkdir`), mémoire « Relais téléphone → TV » (`adb forward tcp:18766` + `nc`).

## Fichiers possédés
Nouveaux : `tools/soak/soak.py`, `tools/soak/fake_tv.py`, `tools/soak/make_media.py`, `tools/soak/scenarios/{s-a,s-b,s-d,s-e,s-f,night-real}.json`, `tools/soak/README.md`, `tools/soak/tests/test_soak.py` (analyse des événements, seuils, rapport, sans appareil), `.gitignore` (`tools/soak/out/`, `.lock`) ; `android/core/build.gradle.kts` (tâche `soakTv`, additive) ; nouveau `C/tv/SoakTvMain.kt` (lance `ReceiverServer` avec les scénarios) ; `C/tv/ReceiverServer.kt` (**zone** : nouvelle route `GET /api/transfer/sessions` derrière PIN/jeton, JSON `[{id,name,received,size,finishing,ageMs}]`, et sa ligne dans `tools/routes/routes.txt`) ; `CT/MultipathServerTest.kt` (un test de la route) ; `docs/test-plans/ENDURANCE.md` (§ 1 : mettre à jour les noms de commandes réels). **Hors zone** : `S/**`, `R/**`, `TransferHost`, tout autre test.

## Étapes
1. Route `/api/transfer/sessions` + test (rouge d'abord : la route répond 404) + `routes.txt` (`python3 tools/routes/list_routes.py --check`).
2. `fake_tv.py` / `SoakTvMain` : scénarios, `--port`, `--pause <s>` (simule une TV éteinte), `--rotate-pin`, `--slow <B/s>`, `--busy`.
3. `soak.py` : arguments `--scenario --tv fake|emu|real-ro --phone emu|real --hours --out` ; verrou ; horodatage ; boucle d'événements `events.jsonl` ; perturbations tirées au sort (graine) ; relevés `mem.csv` toutes les 10 min ; SHA par `/stream` Range (TV) ou `adb pull` (émulateur) ; seuils de `ENDURANCE.md` codés dans `thresholds.json` ; `REPORT.md` + `evidence/` ; code de sortie 0/1 ; **mode `real-ro`** : refus de tout POST autre que les copies/lectures du scénario `night-real`, jamais `pm clear`/`uninstall`/`install`, jamais de PIN faux.
4. `make_media.py` : fichiers déterministes ; MP4 via `ffmpeg` si présent.
5. `tests/test_soak.py` : rapport calculé sur des `events.jsonl` fabriqués (PASS, chaque FAIL avec sa preuve), seuils, graine reproductible, verrou.
6. README : prérequis, les trois nuits, lecture du rapport.

## Critères d'acceptation (hors ligne)
Porte verte ; `python3 tools/soak/soak.py --scenario s-a --tv fake --hours 0.05 --out /tmp/x` tourne de bout en bout sur le Mac avec la fausse TV (≥ 20 copies de 1 Mo, SHA vérifiés, `REPORT.md` PASS) ; aucun secret ni PIN dans le dépôt (le PIN de la fausse TV est généré et affiché `******`) ; `list_routes.py --check` vert.

## À ne pas faire
Pas de dépendance Python hors stdlib ; pas de `tools/smoke` ; pas de modification de `TransferHost` ni d'écran ; jamais d'action sur la vraie TV hors `night-real` ; ne pas lancer de nuit réelle (c'est w15-18 et le propriétaire).

## Rapport
`STATUT`, sortie du passage de 3 min, exemple de `REPORT.md`, limites connues (émulateur absent, `ffmpeg` absent).
