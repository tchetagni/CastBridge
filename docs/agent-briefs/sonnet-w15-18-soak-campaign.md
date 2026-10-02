# w15-18 — Campagne d'endurance : trois nuits, rapports, lignes de régression
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** (mécanique : lancer les scripts, lire les rapports, remplir les tables) · escalade : sonnet si un seuil est dépassé et qu'il faut qualifier la cause · statut : PRÊT (après S2)
> **Groupe : W15-S3** (vague W15, tranche S3) · prérequis : w15-08 fusionné (`tools/soak/`), S2 fusionnée, W14 w14-10 (`FakeTvMain`) souhaité ; émulateurs `cbconnect_tv` (console 5580) et `cbconnect_phone` (5582) disponibles ; APK TV verrouillée + clés de TEST pour l'émulateur · porte : `python3 -m unittest discover -s tools/soak/tests` (le banc lui-même) ; la campagne n'a pas de porte Gradle
> **Jauge : ≈ 100 k jetons entrée / 10 k sortie** par nuit (effort S, surtout du temps machine) · audit Opus : non

**Vague 15 S3 (exécution) · Effort S × 3 · Modèle : haiku · Statut PRÊT.** Branche `claude/sonnet-w15-18` (rapports seulement). Rapports : `docs/agent-reports/soak-<AAAA-MM-JJ>.md` (un par nuit), `docs/agent-reports/sonnet-w15-18.md` (synthèse).

## Objectif
Exécuter les trois nuits de `docs/test-plans/ENDURANCE.md` § 4 (nuit 1 : fausse TV + téléphone émulé ; nuit 2 : TV émulée + téléphone émulé ou réel selon D-W15-7 ; nuit 3 : vrais appareils, **lancée par le propriétaire**, § 6), produire pour chacune un rapport sans secret, et ouvrir une ligne `REGRESSIONS.md` par seuil dépassé.

## Fichiers possédés
`docs/agent-reports/soak-*.md`, `docs/agent-reports/sonnet-w15-18.md`, `docs/REGRESSIONS.md` (lignes nouvelles seulement), `docs/HANDOFF.md` § 0 (une ligne par nuit, même commit). **Hors zone** : tout code ; `tools/soak/` (si le banc a un défaut : `STATUT: BLOQUÉ` + `QUESTION`, pas de correctif ici).

## Étapes (par nuit)
1. Vérifier le prérequis : `git status --porcelain` propre, `python3 tools/soak/soak.py --help`, émulateurs démarrés (`adb devices`), verrou `tools/soak/.lock` absent.
2. Lancer : nuit 1 `soak.py --scenario s-a --tv fake --phone emu --hours 8` puis `s-b --hours 4`, `s-f` en parallèle ; nuit 2 : `s-a`, `s-b`, `s-d`, `s-e`, `s-f` sur `--tv emu` ; nuit 3 : procédure § 6 de `ENDURANCE.md` (le propriétaire lance `night-real` ; l'agent ne lance rien sur les vrais appareils).
3. Le matin : copier `REPORT.md` dans `docs/agent-reports/soak-<date>.md` (en-tête : versions, commit, cibles, durée, graine), vérifier qu'aucune IP complète, aucun jeton, aucun PIN n'y figure (`grep -E '[0-9]{6}|cbk_|192\.168' | grep -v '\*\*\*'` vide).
4. Pour chaque FAIL : ligne `R-NN` dans `REGRESSIONS.md` (symptôme = texte du seuil, preuve = chemin dans `evidence/`, cause = « à qualifier », statut = OUVERT) ; ne pas qualifier la cause (escalade sonnet/Opus par le coordinateur).
5. Synthèse : table nuit × scénario × PASS/FAIL, et verdict contre les critères de sortie (`PLAN-STABILISATION` § 5 point 3).

## Critères d'acceptation
Trois rapports présents, sans secret ; chaque FAIL a sa ligne de régression ; HANDOFF § 0 à jour dans le même commit ; aucun fichier de code modifié (`git diff --stat` ne montre que `docs/`).

## À ne pas faire
Jamais `adb install`, `pm clear`, `uninstall`, reboot ou POST sur la vraie TV ; jamais de PIN faux ; ne pas relancer une nuit échouée « pour voir » sans l'inscrire ; ne pas corriger le banc.

## Rapport
`STATUT`, table de synthèse, liste des `R-NN` ouverts, verdict de sortie proposé.
