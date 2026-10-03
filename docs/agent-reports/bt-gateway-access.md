# Passerelle Bluetooth accessible et bascule automatique : rapport (2026-10-03)

Branche `claude/bt-gateway-access`, partie de `integration/agents` b405333d. Demande du propriétaire : régler la liaison
par ADB, rendre le bouton de passerelle Bluetooth visible, ouvrir facilement un tunnel SSH depuis le Mac.
Aucun code (PIN) dans ce rapport, les fichiers ou les journaux.

## Constats ADB (téléphone RFCR313ABNF, CastBridge 1.2.37-beta), en lecture seule sauf la navigation dans l'app
- Wi-Fi : le téléphone est sur « Orange-0415_2G » (192.168.0.108). La TV 192.168.0.121 ne répond pas (ping : 100 % de perte, absente de l'ARP).
  Son interface Wi-Fi (MAC 74:24:ca:06:00:45) figurait encore en voisin IPv6 STALE puis PROBE, sans réponse au ping6.
  Le propriétaire l'a confirmé : TV allumée mais hors Wi-Fi.
- Bluetooth : « Smart TV » 74:24:CA:06:00:46 est appairée et **connectée** (`dumpsys bluetooth_manager`, Bonded devices).
- Point d'accès : aucun (SoftApManagers:0). swlan0 10.97.59.49 a un voisin 10.97.59.1 qui refuse le port 8765 : ce n'est pas la TV.
  Wi-Fi Direct : aucun groupe (groupFormed false).
- logcat : `TvLink: reprise de Smart TV: TvAbsent` (le HELLO Bluetooth de reprise n'a pas abouti).
- Passerelle démarrée depuis l'UI (Avancé > Bluetooth > Passerelle Bluetooth, Smart TV, API + SSH, exposition réseau non cochée).
  Écoute vérifiée dans /proc/net/tcp6 : 127.0.0.1:2222 et 127.0.0.1:18765 seulement.
- Mac : `adb forward` 2222 et 18765 OK. `curl http://127.0.0.1:18765/api/hello` donne **HTTP 200**
  `{"app":"castbridge-tv","v":"0.7","pinRequired":true}` en 0,8 s, par une liaison Bluetooth partagée.
- SSH : `Connection closed by 127.0.0.1 port 2222`. Message affiché par le téléphone : « Service introuvable ou fermé par la TV
  (read failed, socket might closed or timeout, read ret: -1) ». **« CastBridge SSH » est fermé côté TV : il faut aller sur la TV dans
  MENU > Administration > activer SSH.** `cbdev status` n'a donc pas pu être lancé.
- Diagnostic de l'app : la TV répondait par Bluetooth, mais l'onglet restait gris sur « Recherche de la TV… ». L'app ne basculait pas
  d'elle-même sur la passerelle, et une entrée Wi-Fi périmée (adresse .121) causait « TV injoignable : Connection reset ».
- État laissé : le coordinateur a ensuite repris le téléphone. Au moment du commit, la passerelle n'écoutait plus. Un fichier de
  vidage UI reste dans /sdcard/Download/cb_ui.xml (sans données sensibles ; non effacé, l'effacement étant interdit).

## Ce qui change
- `android/core/.../ux/BtGatewayView.kt` (fonctions pures). `BtGatewayView` décide quand montrer le bouton, quelle TV présélectionner
  (TV enregistrée, service CastBridge, classe TV ou nom de TV ; jamais les casques, kits auto ou ordinateurs), les textes, les lignes
  pour le Mac (`<code>` jamais remplacé, `forClipboard` remasque toute valeur) et la barre. `BtFallback.decide` gère la bascule :
  TV absente du Wi-Fi depuis 8 s avec une TV Bluetooth ⇒ passerelle API seule en boucle locale ; Wi-Fi revenu ⇒ arrêt (seulement si elle
  a été démarrée automatiquement et sans SSH) ; réglage désactivé ⇒ rien ; nouvel essai après 30 s, jamais en boucle ; états orange ou rouge
  avec la cause et un seul geste ; ligne « envoi lent ~100-300 ko/s ».
  Tests écrits d'abord (26/28 rouges sur assertion), puis verts : `BtGatewayViewTest` (16), `BtFallbackTest` (12).
- `sender/BtGatewayCard.kt` (nouveau, écrans minces) : bouton pleine largeur avec icône, démarrage en un appui, carte active avec
  « Copier », « Ajouter le SSH (pour le Mac) », interrupteur « Basculer sur Bluetooth quand le Wi-Fi est absent » (activé par défaut),
  barre `GatewayStrip`, boucle `rememberBtFallback` (sonde `/api/hello` sans code).
- `TvHome.kt` : bloc d'état et bouton sous la TV, et sur « Trouvons votre TV » / « TV injoignable ». Point orange ou rouge au lieu du gris,
  « Connectée par Bluetooth ». En mode code, la base devient 127.0.0.1:18765 quand la bascule est active.
  `TvHub` ouvre Avancé sur Bluetooth.
  `MainActivity.kt` : barre visible sur les autres onglets. `TvDiscovery.kt` : l'entrée « (Bluetooth) » reste proposée même à côté
  d'entrées Wi-Fi périmées. Non touchés : PinStore.kt, TvLink.kt, BtSshGateway.kt (garanties intactes : boucle locale par défaut,
  exposition réseau sur demande avec l'avertissement, la TV vérifie code ou jeton, SSH de bout en bout).
- `tools/remote/tv-tunnel.sh up|status|down [série]` et `tools/tests/test_tv_tunnel.py` (11 tests, faux adb, sans appareil).
  `docs/REMOTE-TUNNEL-BT.md`. Suites : `docs/agent-briefs/sonnet-btgw-01-tv-tunnel-on-demand.md`.
- Vérifications : `:core:test` complet (2975 tests, 0 échec), `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` OK.
  Rien n'a été installé.

## Depuis le Mac
`tools/remote/tv-tunnel.sh up`, puis `ssh -p 2222 tv@127.0.0.1 'cbdev status'` (une fois le SSH activé sur la TV) ou
`curl -H 'X-CB-Pin: <code>' http://127.0.0.1:18765/api/hello`.

## Risques
- La bascule ne tourne que lorsque l'onglet « CastBridge TV » est affiché (brief 2).
- En mode code, le code de la TV choisie part vers la seule TV Bluetooth : avec deux TV, il peut partir vers la mauvaise (refus 401).
  Mitigation : TV unique exigée ; correctif prévu au brief 3.
- Une passerelle démarrée par TvLink (`ensureApi`) ou à la main n'est jamais arrêtée par la bascule (voulu).
- Sonde toutes les 3 s (hello HTTP, 2,5 s maximum) tant que l'onglet est affiché.

## Ce que seul un essai réel confirmera
Apparition de l'orange et « Connectée par Bluetooth » sur la vraie TV (v0.7), retour au vert au retour du Wi-Fi, lisibilité du
bouton et de la carte en 1080x2400, comportement d'Android 14 (service au premier plan démarré depuis l'onglet), puis SSH et
`cbdev status` après l'activation du SSH sur la TV.
