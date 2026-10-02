# w8-16 — CastBridge (téléphone) : écran « Voies du transfert », journal structuré `CbxXfer`, réglages (rapide / chiffrement / plafond), rapport copiable

**Vague 8c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-07 ; en parallèle de w8-14 : coder contre `Handle.progress()`/`LaneStats` du rapport w8-07).** Conception : § 11, 4.6 (plafond), 5.4 (dégradé visible). Branche `claude/sonnet-w8-16`. Rapport : `docs/agent-reports/sonnet-w8-16.md`.

## Objectif
Que l'utilisateur (et le propriétaire en diagnostic) voie **par voie** ce qui se passe, ce qui borne (disque ou Wi-Fi), si c'est chiffré, et puisse copier un rapport sans secret ; qu'un journal d'une ligne toutes les 2 s permette l'analyse après coup.

## Pourquoi (preuves)
- Modèle d'écran existant : `BtRoutesScreen` (routes Bluetooth de la télécommande, test réversible, diagnostic copiable, `sender/BtRoutesScreen.kt:34-40`) ; `Diagnostics.scrub` masque jetons/PIN/adresses (`core/trust/Diagnostics.kt:46-50`).
- `UploadService` expose `speed`, `average`, `notice`, `check` (`UploadService.kt:323-348`) : à compléter par les voies.
- `FastTransfer` : une seule préférence `fast` (`sender/FastTransfer.kt`).

## Fichiers possédés
Nouveaux `S/xfer/{LaneDiagnosticsScreen,XferLog}.kt`, `S/{TvTransferScreen,TvScreen}.kt`. **Hors zone** : `S/UploadService.kt`/`FastTransfer.kt` (w8-14 : il publie `lanes: StateFlow<List<LaneStats>>` et lit les préférences ; convenir des noms par le rapport w8-14 ; en attendant, un `StateFlow` vide), `C/**`.

## Étapes
1. `XferLog.line(handleProgress)` : `xfer id=<8 hex> lanes=wifi:5.1M/12ms/0 bt:idle total=5.1M disk=6.0M K=3 enc=aes contiguous=42%` ; `Log.i("CbxXfer", …)` toutes les 2 s pendant un transfert ; jamais d'adresse, de jeton ni de nom de fichier complet (8 premiers caractères + extension).
2. `LaneDiagnosticsScreen` (Compose, accessible depuis `TvTransferScreen` par « Voies du transfert ») : une carte par voie (nom FR : « Wi-Fi (réseau commun) », « Wi-Fi Direct », « Bluetooth », « Lien USB »), débit instantané/moyen, RTT, erreurs, état (active / en veille / écartée / sondage), part des octets ; bandeau « Ce qui limite : le disque de la TV (6,0 Mo/s) » ou « le Wi-Fi » (règle : `disk < total × 0,9` → disque) ; ligne « Chiffré (AES-GCM) » / « Chiffrement désactivé : TV trop lente » / « Non chiffré (code PIN) » ; bouton « Copier le rapport » (texte, passé par `Diagnostics.scrub`).
3. `TvScreen` (réglages de l'onglet TV) : sous « Transfert rapide (plusieurs voies) » existant, ajouter « Chiffrer les transferts » (défaut activé) et « Limiter le débit » (Aucun / 2 / 5 / 10 Mo/s) : lecture/écriture par `FastTransfer` (clés convenues avec w8-14 : `encrypt`, `cap`).
4. Hors transfert, l'écran montre la dernière session (conservée en mémoire) et « Aucun transfert en cours ».
5. Test JVM léger si un modèle pur est extrait (`LaneDiagnosticsModel.bottleneck(disk, total)`, textes) : ≥ 4 cas ; sinon le dire.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -rn "CbxXfer" android/sender/src/main/kotlin | wc -l   # ≥ 1
grep -rn "scrub(" android/sender/src/main/kotlin/castbridge/sender/xfer/LaneDiagnosticsScreen.kt | wc -l   # ≥ 1
```
Observable : pendant un envoi, l'écran montre 2 voies et le bandeau « ce qui limite » ; le rapport copié ne contient ni `cbk_`, ni 6 chiffres, ni adresse `XX:XX:…`.

## Cas limites
TV v1 (une voie, pas de `lanes` côté TV) : l'écran montre les statistiques **locales** ; voie en veille : « En veille (le Wi-Fi suffit) » ; aucune voie : « En attente d'une liaison ».

## À ne pas faire
Pas de phrase inventée pour les refus (catalogue W6) ; ne pas lire les préférences ailleurs que par `FastTransfer` ; pas de graphique lourd (une barre par voie suffit) ; pas de télémétrie nouvelle (aucun nom d'événement sans w8-19).

## Rapport
`STATUT`, noms convenus avec w8-14, captures, compilation.
