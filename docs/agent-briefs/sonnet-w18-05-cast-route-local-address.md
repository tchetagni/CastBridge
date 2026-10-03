# w18-05 — Cœur : `LocalAddress.toward(tvIp)` (l'adresse du téléphone que la TV peut joindre), `CastRoute` (cast « Lire en direct », Quiz, télécommande : quelle base, quelle URL, quel refus honnête)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W18a-3** (vague W18a, cœur) · prérequis : w18-02 (type `WdPolicy.Route`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.LocalAddressTest' --tests 'castbridge.core.link.CastRouteTest' --tests 'castbridge.core.player.*'`
> **Jauge : ≈ 250 k jetons entrée / 12 k sortie** (effort S, ≈ 1 j) · audit Opus : non

**Vague 18a · Effort S · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 1 (lignes « Cast », « Quiz », « Télécommande »), § 5.2, § 9 (cast par Bluetooth). Branche `claude/sonnet-w18-05`. Rapport : `docs/agent-reports/sonnet-w18-05.md`. Règle W15 R5 : aucun fichier `S/`, `R/`.

## Objectif
(1) `LocalAddress.toward(tvIp, interfaces)` : parmi les adresses IPv4 du téléphone, celle de l'interface **qui route vers la TV** (même préfixe que `tvIp` d'abord, puis la route par défaut fournie par l'appelant via une socket UDP connectée, jamais « la première `wlan` ») : sur un groupe P2P la bonne est `192.168.49.x` de `p2p-wlan0-0`, pas celle du Wi-Fi de la maison ; (2) `CastRoute.decide(action, kind, route: WdPolicy.Route, phoneAddress)` : pour « Lire en direct » ⇒ `Serve(url = http://<adresse>:8089/media/…, base TV)` ou `Refuse(text)` ou `Suggest(COPY_AND_PLAY)` ; vidéo par Bluetooth ⇒ refus honnête + remplacement ; audio/photo par Bluetooth ⇒ permis via le tunnel ; (3) `QuizUrls.of(tvInterfaces, requesterRoute)` : une URL `/quiz?code=` par interface de la TV, et **celle à afficher** (réseau du téléphone qui a ouvert la salle) ; (4) `RemoteBase.of(route)` : base HTTP de la télécommande (WD `Up` ⇒ 192.168.49.1, LAN ⇒ IP, sinon Bluetooth CBTR) ; (5) la phrase `UiTexts.LIVE` révisée (plus de « restez sur le même Wi-Fi » quand c'est le Wi-Fi Direct).

## Pourquoi (preuves)
- `S/player/CastSession.kt:297-308` `serve()` : `Upnp.localIp()` = première IPv4 de site, `wlan*` d'abord (`S/Upnp.kt:14-18`) ⇒ fausse sur un groupe ; message « Pas d'adresse Wi-Fi : le téléphone et la TV doivent être sur le même réseau » ; `C/tv/ReceiverServer.kt:702-711` `/api/playurl` (http(s) seulement, ≤ 4096 car.).
- `R/QuizHub.kt:169` `joinUrl` = `TvService.localIp()` (première IPv4 de site, `R/TvService.kt:1113`) ; `C/quiz/QuizHttp.kt` (routes sans PIN).
- `S/RemoteController.kt:32` `RemoteTv(host, port, btAddress)` ; `C/player/CastPlan.kt` (`needsPhoneServer`) ; `C/ux/UiTexts.kt:25` (`LIVE`).

## Fichiers possédés
Nouveaux `C/link/LocalAddress.kt`, `C/link/CastRoute.kt` (contient `QuizUrls`, `RemoteBase`), `CT/link/LocalAddressTest.kt`, `CT/link/CastRouteTest.kt` ; `C/ux/UiTexts.kt` (phrase `LIVE` : « La TV lit depuis le téléphone et ne garde rien : restez à portée jusqu'à la fin. »). **Hors zone** : `S/`, `R/`, `C/player/CastPlan.kt` (w18-13), `C/quiz/**`.

## Étapes
1. **Rouge** : `LocalAddressTest` : interfaces `[wlan0 192.168.1.20/24, p2p-wlan0-0 192.168.49.12/24, rmnet0 10.4.2.9/30]`, `tvIp = 192.168.49.1` ⇒ `192.168.49.12` ; `tvIp = 192.168.1.30` ⇒ `192.168.1.20` ; TV hors de tout préfixe ⇒ adresse donnée par `routeProbe(tvIp)` (lambda injectée, factice) ; aucune ⇒ `null` ; IPv6 et link-local ignorées ; une adresse `127.0.0.1` (tunnel Bluetooth) ⇒ `null` **pour le cast vidéo** (il n'y a pas d'interface IP vers la TV : c'est le mux).
2. **Rouge** : `CastRouteTest` : LIVE vidéo + `UseWd(Up)` ⇒ `Serve(url sur 192.168.49.12)` ; LIVE vidéo + `UseLan` ⇒ `Serve(url LAN)` ; LIVE vidéo + `UseBt` ⇒ `Refuse(« La lecture en direct d'une vidéo n'est pas possible par Bluetooth (trop lent) »)` + `Suggest(COPY_AND_PLAY)` ; LIVE audio + `UseBt` ⇒ `Serve(url http://127.0.0.1:<port tunnel>/…)` via le mux ; LIVE photo + `UseBt` ⇒ idem ; `NoRoute` ⇒ `Refuse(texte de WdPolicy)` ; lien web (`castSource = WEB`) ⇒ jamais de serveur téléphone, base seule ; `QuizUrls` : TV `[eth0 192.168.1.5, p2p 192.168.49.1]`, demandeur par WD ⇒ affichée `http://192.168.49.1:8765/quiz?code=…`, toutes listées ; `RemoteBase` : table 4 lignes.
3. `LocalAddress` : `data class Iface(name, ip, prefix)` ; `toward(tvIp, ifaces, routeProbe: (String) -> String?)`.
4. `CastRoute` : `sealed class Choice { Serve(url, base) ; Refuse(text) ; Suggest(alt, text) }` ; textes français dans le fichier.
5. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 14 tests rouges puis verts ; `grep -n "wlan" android/core/src/main/kotlin/castbridge/core/link/LocalAddress.kt` vide (aucun nom d'interface codé en dur) ; aucune modification hors zone.

## Cas limites
Téléphone à la fois sur le LAN de la TV **et** sur son groupe (deux préfixes communs) ⇒ préférer le préfixe de la **route active** (`WdPolicy.Route`), sinon le plus long ; adresse 192.168.49.x sur **deux** interfaces (téléphone lui-même propriétaire d'un autre groupe : impossible en pratique, mais `toward` prend celle dont l'interface n'est pas propriétaire : drapeau `isGo` dans `Iface`) ; URL de plus de 4096 car. (identifiant média) ⇒ `Refuse` avant l'appel.

## À ne pas faire
Pas d'appel réseau réel (`routeProbe` injectée) ; ne pas toucher `Upnp.kt` (w18-10) ; pas de SSDP sur le groupe ; pas de texte hors `CastRoute`/`UiTexts`.

## Rapport
`STATUT`, sorties rouge/vert, la table de `CastRoute`, la signature de `routeProbe` que w18-10 doit fournir (`DatagramSocket.connect(tvIp, 9) ; localAddress`).
