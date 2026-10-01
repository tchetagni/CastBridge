# Brief : tunnel Bluetooth « API » fiable (une liaison réutilisée, pas une par requête)

Agent cloud. Base `origin/integration/agents`. Branche `claude/bt-tunnel-keepalive`, petits commits, push, pas de PR, pas de main. Français. Apps « CastBridge » / « CastBridge-TV ».
Lire : `docs/BT-PLUG-AND-PLAY.md` (service « CastBridge API »), `docs/ADMIN.md` §11, `core/.../tv/BtTunnel*.kt`, `TcpTunnel`, sender `BtSshGatewayService.kt`
(`api: connect attempt`, `ensureApi`, `apiBase`), receiver `BtApiControl.kt`, `BtTunnelBridge`.

## Défaut mesuré (2026-10-01, S21+ Android 15 ↔ TV 0.13.3 / 1.2.6-beta, Wi-Fi du téléphone coupé)
- Télécommande : « Liaison perdue : reconnexion… / hors ligne », le volume ne bouge pas. Wi-Fi allumé : tout marche.
- TV : `GET /api/bluetooth/tunnel` = `enabled:true, listening:true, refused:0, lastError:null, active:["S21+"]` : la TV accepte et ne voit pas d'erreur.
- Téléphone (journal, adresse masquée) : `CastBridgeSshGw api: link closed (up 182 B, down 714 B)` puis, 0,6 s après,
  `RFCOMM_CreateConnectionWithSecurity: already at opened state 1/2 … bta_jv_rfcomm_connect: RFCOMM_CreateConnection failed`, puis
  `api: connect attempt 1/2 failed: read failed, socket might closed or timeout, read ret: -1`, en boucle (une nouvelle liaison par requête HTTP).
- Hypothèse : le téléphone ouvre une liaison RFCOMM par connexion TCP locale (127.0.0.1) et réessaie avant que la pile Bluetooth ait libéré
  la précédente ; la TV ferme aussi une liaison inactive après 30 s (`idleMs`).

## À faire
1. **Une seule liaison RFCOMM persistante** par TV, réutilisée pour plusieurs requêtes HTTP successives (multiplexage simple ou file stricte :
   une requête à la fois, `Connection: keep-alive` côté téléphone, trames de longueur) ; jamais deux `connect()` en parallèle vers la même TV.
2. **Réessais** : après une fermeture, attendre ≥ 1,5 s avant de rouvrir, courbe croissante avec gigue, 3 essais puis état clair (« Bluetooth : la TV
   ne répond pas », pas de boucle serrée). Un `connect()` en cours bloque les suivants (verrou).
3. **Garde-vivant** : ping léger toutes les ~15 s tant que la télécommande ou un transfert est actif (la TV garde 10 min si `busy()`, 30 s sinon) ;
   la télécommande compte comme « occupé » côté TV. Rétrocompatible : une ancienne TV/ancien téléphone continue à marcher (une liaison par requête).
4. **Télécommande** : le canal CBTR (touches) doit rester utilisable seul quand le tunnel HTTP n'est pas établi (déjà prévu : le vérifier de bout en
   bout, `RemoteSession` sur Bluetooth, latence mesurée, reprise après coupure).
5. **Tests JVM** avec faux transport RFCOMM (ouverture lente, fermeture tardive, « already opened », coupure en cours de réponse, deux requêtes
   simultanées, TV qui ferme à 30 s) ; diagnostic dans « Mes TV » : nombre de liaisons ouvertes, dernier motif de fermeture.
6. Docs : `docs/BT-PLUG-AND-PLAY.md`, `docs/HANDOFF.md` (entrée datée). Ne compile pas Android si impossible (le dire) : le propriétaire teste sur matériel.
Rapport final court en français : correctif, tests avant/après, ce qui reste à valider sur la TV.
