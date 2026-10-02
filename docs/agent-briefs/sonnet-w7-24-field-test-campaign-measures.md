# w7-24 — Campagne de test terrain W7 et scripts de mesure (`tools/link-test/`), `docs/TEST-CAMPAIGN.md` § W7

**Vague 7d (à lancer AUSSI en avance, partie « mesures de base », dès le début de 7a : B2) · Effort M (≈ 2 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 9.1, § 10.3 (R2, R3), § 13 (B2). Branche `claude/sonnet-w7-24`. Rapport : `docs/agent-reports/sonnet-w7-24.md`.

## Objectif
(1) **Mesures de base avant W7** (B2) : scripts qui mesurent sur la vraie TV et le vrai téléphone, avec le code **actuel** : durée d'un `connect()` RFCOMM (`…0001`, `…0005`), délai de libération après fermeture (le « already opened » de `bt-tunnel-keepalive.md:11`), débit CBT1 et HTTP LAN, temps de reconnexion après `reboot` de la TV, délai mDNS ; résultats consignés dans `docs/agent-reports/sonnet-w7-24.md` § « Base » pour fixer `LinkConfig` (w7-01) ; (2) `docs/TEST-CAMPAIGN.md` § W7 : ≈ 40 étapes numérotées (téléphone + TV + box) reprenant la table § 9.1 (mesure, commande, seuil, résultat attendu, case à cocher), dont : première liaison 3 touches, redémarrage TV, coupure courant, changement d'IP, isolation des clients, latence `act`/`lots`/`xfer`/`par`, transfert interrompu (TV et téléphone), batterie 2 h, jobs manqués/OEM (Tecno/Infinix/Itel, Xiaomi, Samsung), CDM, réinstallation téléphone (`pm clear`), réinstallation TV, TV ancienne ↔ téléphone neuf et l'inverse (synchro simple), TV sans Bluetooth (QR), VPN, hotspot téléphone, 2 TV, 2 téléphones ; (3) `tools/link-test/` : `measure_bt.sh` (via `adb` + `logcat` filtré `LinkRuntime|TvBeacon|LinkHost`), `measure_sync.sh` (`curl` activation → horodatage notification via `logcat`), `battery.sh` (`dumpsys batterystats`), `jobs.sh` (`dumpsys jobscheduler`), `journal_pull.sh` (`run-as` export + redaction), `README.md` ; (4) relais Mac → téléphone → TV (`nc`, mémoire du projet) documenté dans le README.

## Pourquoi (preuves)
- `docs/TEST-CAMPAIGN.md` (sections W5/W6 existantes : même format) ; `tools/transfer-bench` (banc existant : réutiliser pour le débit) ; `docs/HANDOFF.md:182-186` (non vérifié sur la vraie TV) ; `docs/BT-PLUG-AND-PLAY.md:126,133` (« à valider sur matériel »).
- Constantes à mesurer : `LinkPool` gap 1,5 s (`C/tunnel/LinkPool.kt:20-30`), `LinkMachine.Config` (`C/trust/LinkMachine.kt:82-88`), `btConnectMs` (w7-01).

## Fichiers possédés
Nouveaux : `tools/link-test/measure_bt.sh`, `tools/link-test/measure_sync.sh`, `tools/link-test/battery.sh`, `tools/link-test/jobs.sh`, `tools/link-test/journal_pull.sh`, `tools/link-test/README.md`, `tools/tests/test_link_test_scripts.py` (`bash -n`, options `--help`, redaction du `journal_pull`). Modifié : `docs/TEST-CAMPAIGN.md` (§ W7 ajouté). **Hors zone** : tout code Android, autres docs.

## Étapes
1. Scripts `bash` POSIX, `set -euo pipefail`, `--serial` pour choisir l'appareil, sortie CSV (`mesure,valeur,unité,horodatage`), aucun secret (le PIN se passe par variable d'environnement `CB_PIN`, jamais en argument).
2. `journal_pull.sh` : `adb shell run-as castbridge.sender cat files/link/journal.txt` (téléphone) et `curl /api/link/journal` (TV) ; redaction locale supplémentaire (regex adresses BT, IP, `cbk_`).
3. Campagne : chaque étape = « Préparation / Action / Attendu / Seuil / Résultat ☐ », regroupées par appareil ; une annexe « Problèmes terrain 1-9 » reliant chaque problème à l'étape qui le vérifie.
4. **Exécuter la partie « Base »** avec le propriétaire (ou lui laisser les commandes exactes si l'agent n'a pas d'appareil : dire lequel) ; consigner.

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests -p 'test_link_test_scripts.py'    # vert
bash -n tools/link-test/*.sh
grep -c '☐' docs/TEST-CAMPAIGN.md   # ≥ 40 nouvelles cases (comparer à avant)
grep -n 'CB_PIN=' tools/link-test/*.sh docs/TEST-CAMPAIGN.md | grep -v '\$CB_PIN\|export CB_PIN' | wc -l   # 0 (aucun PIN en clair)
```

## Cas limites
Pas d'appareil disponible pour l'agent ⇒ `STATUT: TERMINÉ (mesures de base à exécuter par le propriétaire)` avec la liste des commandes ; TV sans `run-as` (release) ⇒ journal via `/api/link/journal` seulement.

## À ne pas faire
Pas de script qui modifie la TV ou le téléphone hors réglages de test documentés (`stay_on_while_plugged_in`) ; aucun secret dans les fichiers.

## Rapport
`STATUT`, mesures de base (table), étapes de campagne, scripts.
