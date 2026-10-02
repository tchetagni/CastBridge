# w1-04 — Profil « TV hors ligne » : sonde Google et BitTorrent coupés

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (D3 pour le retrait total)
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Aria2*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S/M (≈ 6 h) · Statut PRÊT** (le **retrait complet** d'aria2 attend la décision D3 ; ce cahier coupe DHT/BitTorrent et garde HTTP). Branche `claude/sonnet-w1-04`. Rapport : `docs/agent-reports/sonnet-w1-04.md`.

## Objectif
1. La TV ne contacte plus `connectivitycheck.gstatic.com` périodiquement : la sonde ne part que sur action manuelle (écran « Tester Internet ») ou quand une fonction qui a besoin d'Internet le demande (passerelle, mises à jour), avec un réglage `netProbe` (défaut : off).
2. aria2 : `enable-dht=false`, pas de port d'écoute, pas de BitTorrent/magnet (`bt-*` off, `--disable-ipv6` déjà ?), seulement HTTP(S)/FTP.
3. Le texte de licence d'aria2 (GPLv2+) et l'offre de sources sont embarqués.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/TvNetDiag.kt:21` : `URL("http://connectivitycheck.gstatic.com/generate_204")` ; appelée par `android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt:439` (`netDirectMs = TvNetDiag.probe(null)`) dans `netTick` (60 s, 10-30 s hors ligne) : fuite de présence/IP vers un tiers, sans consentement, contraire au principe « la TV reste hors ligne ».
- `android/core/src/main/kotlin/castbridge/core/dl/Aria2Config.kt:80` `--enable-dht=true`, `:83` `--listen-port=6881-6889` : la TV est joignable par des pairs d'Internet ; risque juridique du partage.
- `android/receiver/src/main/jniLibs/*/libaria2c.so` sans `COPYING` ni offre de sources (script `tools/build-aria2-android.sh` existe).
- Audit : SE-7, LE-6 ; décision D2 (playurl reste), D3.

## Fichiers possédés
`R/TvNetDiag.kt`, `R/TvService.kt` (**uniquement** la zone `netTick`/`checkNetNow`, lignes ≈ 421-547 ; ne rien toucher d'autre : w2-07 découpera le fichier plus tard), `R/TvPrefs.kt`, `C/dl/Aria2Config.kt`, tests `android/core/src/test/kotlin/castbridge/core/dl/*`, nouveau `android/receiver/src/main/jniLibs/COPYING-aria2.txt` (texte GPL-2.0 + offre de sources pointant sur `tools/build-aria2-android.sh` et `tools/aria2-android-build.txt`), `docs/DOWNLOADS.md`, `docs/ADMIN.md` (§ Badge Internet : préciser que l'état vient des rappels système quand la sonde est off).

## Étapes
1. `TvPrefs` : nouveau réglage booléen `netProbe` (défaut `false`).
2. `TvService.netTick` : si `!netProbe`, ne pas appeler `TvNetDiag.probe` ; alimenter `NetState` (`C/net/NetState.kt`) à partir des rappels `ConnectivityManager` (`NET_CAPABILITY_VALIDATED` quand disponible) et de l'état passerelle, sans trafic sortant. La sonde reste appelée par `checkNetNow()` (action manuelle, écran Tests Internet) et quand `TvConnect` doit joindre le serveur.
3. `TvNetDiag` : garder la fonction ; ajouter un commentaire « jamais périodique ».
4. `Aria2Config` : `--enable-dht=false`, `--enable-dht6=false`, `--bt-enable-lpd=false`, `--enable-peer-exchange=false`, retirer `--listen-port`/`--dht-file-path`, et refuser `magnet:`/`.torrent` dans `DownloadManager` (`C/dl/DownloadManager.kt` : **hors zone** — si le refus doit s'y faire, le signaler dans le rapport et le laisser au coordinateur ; sinon le faire dans `Aria2Config` via `--follow-torrent=false` + `--bt-metadata-only`… : préférer `--follow-torrent=false` et ne pas passer les options `bt-*`). Mettre à jour le test de `Aria2Config` (chercher `grep -rln Aria2Config android/core/src/test`).
5. `COPYING-aria2.txt` + mention dans `docs/DOWNLOADS.md` (section « Licences »).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.dl.*'    # vert, et un test affirme l'absence de enable-dht=true / listen-port
grep -n 'enable-dht=true\|listen-port' android/core/src/main/kotlin/castbridge/core/dl/Aria2Config.kt   # 0 hit
grep -n 'TvNetDiag.probe' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # seulement sous une condition netProbe ou action manuelle
ls android/receiver/src/main/jniLibs/COPYING-aria2.txt
```
Observable sur TV (campagne) : `tcpdump`/journal du routeur sans trafic vers `gstatic.com` au repos ; `netstat`/`ss` sur la TV sans port 6881-6889 à l'écoute pendant un téléchargement HTTP.

## Cas limites
- Le badge Internet (`docs/ADMIN.md`) perd sa mesure de latence : afficher « Wi-Fi connecté » / « Internet non vérifié » plutôt que « Pas d'Internet » rouge quand aucune sonde n'a tourné (voir `NetState` : garder un état `UNKNOWN`).
- La passerelle Bluetooth (`BtGateway`) a sa propre sonde via le téléphone : hors zone, ne pas toucher.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas retirer aria2 (décision D3) ; ne pas toucher `/api/playurl` (décision D2) ; textes en français ; dire « CastBridge-TV ».

## Rapport
`STATUT`, options aria2 avant/après, sorties des commandes, ce qui attend D3.
