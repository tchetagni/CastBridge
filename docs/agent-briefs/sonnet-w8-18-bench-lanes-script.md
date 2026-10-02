# w8-18 — Banc : `TransferBench --lanes` (voies simulées), script de mesure sur la vraie TV (1 Mo / 100 Mo / 2 Go), seuils et tableau

**Vague 8d · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après w8-06).** Conception : § 12.2, 12.3, 9. Branche `claude/sonnet-w8-18`. Rapport : `docs/agent-reports/sonnet-w8-18.md`.

## Objectif
Donner au propriétaire **une commande** qui mesure sa TV dans les scénarios de la conception et dit ce qui borne ; et au banc simulé la capacité de décrire plusieurs voies (débit, latence, pertes, coupures) pour reproduire un cas sans matériel.

## Pourquoi (preuves)
- `TransferBench` : K = 1, 2, 4, 8, réseau seul / réseau + disque, `--simulate` à 4 paramètres (`core/xfer/TransferBench.kt:19-26, 78-114, 169-175`) ; `tools/transfer-bench/run.sh`.
- `FakeLane` (w8-06) existe en test seulement : le banc en reçoit une copie **dans `main`** (`TransferBench` est dans `main`) ou `FakeLane` est déplacée : **non** — recopier le minimum (`Shaped` existe déjà) pour ne pas dépendre des tests.

## Fichiers possédés
`C/xfer/TransferBench.kt`, `tools/transfer-bench/{run.sh,README.md,scenarios.md}`. **Hors zone** : tout le reste.

## Étapes
1. `--lanes "wifi:20M:30ms:0%,bt:200K:80ms:0%,direct:8M:40ms:2%"` (nom:débit:latence:pertes[:coupures `@10s/5s`]) pour `--simulate` : étend `Shaped` ; `--radio wifi,direct` (les deux se partagent le débit) ; tableau par voie (octets, part, débit) + total ; conclusion automatique « agrégat ≥ 0,9 × meilleure voie : OUI/NON ».
2. Vraie TV : `--lanes auto` = `WifiLane` (K adaptatif) ; les voies Bluetooth/Direct ne se mesurent pas depuis un PC (il faut le téléphone : le README le dit et renvoie à l'écran w8-16 et au journal `CbxXfer`) ; `--enc on|off` (quand w8-10 est là : `caps.version ≥ 2`) pour mesurer le coût du chiffrement ; `--gen 1M,100M,2G` crée les fichiers de test une fois (dans `/tmp` ou `--dir`).
3. `run.sh --scenario S1|S3|S6` : enchaîne les tailles, écrit `bench-<date>.txt` avec le tableau, les seuils de § 12.2 (vert/rouge) et la phrase « ce qui borne ».
4. `scenarios.md` : S1-S8 de la conception, ce qu'il faut brancher/couper, la commande, les seuils.
5. Test JVM existant du banc (s'il y en a un : `grep -rl TransferBench CT/`) toujours vert ; sinon un test de l'analyseur `--lanes` (≥ 4 cas) dans `CT/xfer/BenchArgsTest.kt` (**fichier à créer, le déclarer dans le rapport**).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.BenchArgsTest'   # vert
tools/transfer-bench/run.sh --simulate --lanes "wifi:20M:30ms:0%,bt:200K:80ms:0%" --size 32M | grep -E "agrégat|total"   # affiche le tableau et la conclusion
tools/transfer-bench/run.sh --help | grep -c "scenario"   # ≥ 1
```

## Cas limites
Débit `0` dans `--lanes` (refusé avec message) ; TV v1 (pas de `--enc`) ; fichier de 2 Go sur une machine sans place (`--gen` vérifie l'espace).

## À ne pas faire
Pas de dépendance ; ne pas déplacer `FakeLane` hors des tests ; ne pas inventer de chiffre dans `scenarios.md` (seulement les seuils).

## Rapport
`STATUT`, exemple de sortie simulée (copié), ce que le propriétaire doit lancer.
