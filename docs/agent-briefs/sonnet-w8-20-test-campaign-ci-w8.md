# w8-20 — Campagne de test W8 (`TEST-CAMPAIGN.md` § W8) et CI (tests `core.xfer`, vecteurs, routes)

**Vague 8d · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après w8-10 et w8-18).** Conception : § 12. Branche `claude/sonnet-w8-20`. Rapport : `docs/agent-reports/sonnet-w8-20.md`.

## Objectif
Une campagne pas à pas pour le propriétaire (TV de référence + téléphone), et une CI qui empêche toute régression du moteur, des vecteurs crypto et de la table des routes.

## Sources
`DESIGN-W8-TRANSPORT-MULTIVOIE.md` § 12.2-12.3 ; `tools/transfer-bench/scenarios.md` (w8-18) ; `docs/TEST-CAMPAIGN.md` (format des § W4-W6, à imiter) ; `.github/workflows/android.yml` (jobs existants) ; `docs/COORDINATION.md` § CI.

## Fichiers possédés
`docs/TEST-CAMPAIGN.md` (§ W8 seulement), `.github/workflows/android.yml`, `docs/COORDINATION.md` (§ CI). **Hors zone** : code, autres docs.

## Étapes
1. `TEST-CAMPAIGN.md` § W8 (≈ 35 étapes numérotées, chacune : préparation, action, attendu, preuve à joindre) : S1 (Wi-Fi maison, 100 Mo, écran « Voies », journal), S3 (clé lente : message disque), S4 (Wi-Fi du téléphone coupé : premier bloc ≤ 3 s par Bluetooth, Direct si activé), coupure Wi-Fi 5 s en plein envoi (aucun « en attente »), `adb shell reboot` de la TV à 40 % d'un 2 Go (reprise ≤ 2 blocs renvoyés : lire `state` avant/après), chiffrement (débit chiffré ≥ 0,9 × clair, ou mode dégradé affiché), essai (refus `M-TV-REFUSED`, rien en file), lots par le vrac (w8-15), lecture pendant l'envoi multivoie (w8-03/w8-10), télécommande Bluetooth réactive pendant un vrac Bluetooth (service dédié), deux téléphones (part équitable), mémoire TV (`dumpsys meminfo` ± 10 Mio), plafond de débit 2 Mo/s respecté.
2. CI `android.yml` : job `core-xfer` : `gradle --offline :core:test --tests 'castbridge.core.xfer.*' --tests 'castbridge.core.Multipath*'` ; **trois graines** pour `PropertyTest` (variable d'environnement `XFER_SEED`, si w8-06 l'a prévue ; sinon une) ; `git diff --quiet tools/activation/xfer-vectors.json` (vecteurs figés) ; `python3 -m unittest tools/tests/test_routes.py`.
3. `COORDINATION.md` § CI : une ligne par nouveau contrôle.

## Critères d'acceptation
```sh
grep -c "^### W8\|^## W8" docs/TEST-CAMPAIGN.md   # 1
grep -n "core-xfer" .github/workflows/android.yml | wc -l   # ≥ 1
grep -n "xfer-vectors.json" .github/workflows/android.yml | wc -l   # ≥ 1
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android.yml')); print('yaml ok')"
```

## À ne pas faire
Pas de secret dans le workflow ; pas de job qui exige une TV ; ne pas réécrire les sections W4-W7 de la campagne.

## Rapport
`STATUT`, nombre d'étapes, jobs ajoutés.
