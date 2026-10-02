# w15-15 — Lecteur et streaming du téléphone : serveur média authentifié, session de cast qui abandonne, notification Media3 retirée, cache de miniatures en octets, DLNA robuste
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus par échantillon (serveur média : jeton) · statut : PRÊT
> **Groupe : W15-S2-c** (vague W15, tranche S2 ; fichiers disjoints de w15-13/14/16) · prérequis : S0 fusionnée ; décision D-W15-5 (jeton de session) par défaut = oui · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PhonePlayer*' --tests '*CopyProgress*' --tests 'castbridge.core.net.MediaTokenTest' --tests 'castbridge.core.upnp.*'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 15 S2 (téléphone + cœur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-15`. Rapport : `docs/agent-reports/sonnet-w15-15.md`. Règle : test rouge d'abord ; la logique va dans le cœur, les écrans l'appliquent.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.1 et § 2.5)
P-10 / P-10b (`S/MediaServer.kt:17-21,28-29,55` : pas d'authentification, id `hashCode` 32 bits, toutes interfaces, `items` jamais vidé, descripteur non positionnable ⇒ 500) · P-18 / F-06 (`S/player/CastSession.kt:272-278` : `IOException` ⇒ nouvel essai sans fin, jamais FAILED ; `S/ServerService.kt:38-41` wake lock 4 h ; **vu dans le logcat du 2026-10-02 17:17-17:21**) · F-07 (notification Media3 id 1001 canal `default_channel_id` résiduelle, **vue dans `dumpsys notification`**) · P-08 (`S/player/PlaybackService.kt:93-125` tout contrôleur accepté) · P-09 (`S/player/PlayerActivity.kt:160-165` droit URI lié à l'activité) · P-15 (`S/player/PhoneLibrary.kt:228,248` LruCache en entrées) · P-19 / P-20 (`S/Upnp.kt:26-31,64-88` SSDP sur réseau par défaut, `describe` séquentiel, `Seek` REL_TIME) · P-21 partiel (`CastSession.kt:137,223` MIME `video/mp4` pour `.mkv` ; `S/TvPlayerSettings.kt:60-61` `readBytes()` sans plafond).

## Fichiers possédés
Nouveaux `C/net/MediaToken.kt` (pur : id 128 bits aléatoire, jeton de session, URL `/media/<id>?t=<jeton>`, vérification en temps constant) + `CT/net/MediaTokenTest.kt` ; `C/upnp/Soap.kt` (zone `Seek` ABS_TIME de repli, MIME par extension) + tests `CT/upnp/*` ; `S/MediaServer.kt`, `S/ServerService.kt`, `S/player/CastSession.kt`, `S/player/PlaybackService.kt`, `S/player/PlayerActivity.kt` (zone URI externe), `S/player/PhoneLibrary.kt` (zone cache), `S/Upnp.kt`, `S/TvPlayerSettings.kt` (zone lecture du fichier), `S/DlnaHandoff.kt` (URL avec jeton), `android/sender/src/main/res/values*/strings.xml` (nom du canal de notification de lecture, français) ; `CT/PhonePlayerTest.kt`, `CT/CopyProgressTest.kt`. **Hors zone** : `C/tv/**`, `R/**`, `TvLink`, `UploadService`.

## Étapes (test rouge, correctif, vert)
1. `MediaToken` + tests (deux URI ⇒ ids distincts ; jeton faux ⇒ 403 ; jeton expiré à la fin de session) ; `MediaServer` : lie l'interface Wi-Fi seule (repli : toutes), `unregister` à l'arrêt de la diffusion, 416/500 propres sur descripteur non positionnable (copie en cache ≤ 200 Mo sinon refus explicite).
2. `CastSession.poll()` : politique pure `CastRetry.next(failures, elapsedMs)` ⇒ FAILED après 60 s d'échecs consécutifs avec texte « <TV> ne répond plus : lecture arrêtée. Relancez depuis la TV ou renvoyez. » ; wake lock relâché ; test : client qui lève en boucle ⇒ FAILED à 60 s simulées.
3. `PlaybackService` : `onConnect` restreint (paquet, Android Auto, système) ; `onTaskRemoved`/fin de lecture ⇒ notification retirée (`stopForeground(STOP_FOREGROUND_REMOVE)` + `clearListener`) ; canal nommé « Lecture » (français) ; test instrumenté impossible ⇒ vérification F/ADB : `dumpsys notification` sans `media3_group_key` 30 s après la fin.
4. URI externes (Telegram) : copie en cache si `content://` sans `MediaStore` et taille ≤ 512 Mo, sinon lecture directe avec message en cas d'échec.
5. `PhoneLibrary` : `LruCache` en octets (`sizeOf`), budget 1/8 de `maxMemory`.
6. `Upnp` : `bindSocket` sur le réseau Wi-Fi (`ConnectivityManager`) ; `describe` en parallèle (≤ 4) avec délai global 10 s ; `Seek` ABS_TIME de repli ; MIME par extension ; `TvPlayerSettings` lecture bornée 2 Mo.
7. Vert : porte ; `:sender:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 8 tests nouveaux rouges puis verts ; `grep -n "hashCode" S/MediaServer.kt` vide ; `grep -n "default_channel_id" android/sender/src` vide (canal nommé) ; `grep -n "readBytes()" S/TvPlayerSettings.kt` borné.

## À ne pas faire
Pas de changement du protocole DLNA au-delà des replis ; pas de dépendance ; ne pas toucher `UploadService` ni `TvLink` ; pas de nouveau texte hors du message de P-18 et du nom de canal.

## Rapport
`STATUT`, table défaut ⇒ test, URL d'exemple (jeton masqué), à valider sur le S21+ (notification Media3 après « Ouvrir avec » ; cast sur un rendu DLNA tiers si disponible).
