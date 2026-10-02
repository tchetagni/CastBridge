# w8-12 — CastBridge-TV : pointes USB mesurées (partage de connexion USB = lien IP ; accessoire AOA = flux brut) — aucun produit sans mesure

**Vague 8b · Effort M (≈ 2 j) · Modèle : sonnet · Statut BLOQUÉ partiel (D-W8-1 : ports USB des TV cibles, accord pour brancher le téléphone à la TV ; la partie tethering se fait sans décision).** Conception : § 2 (lignes USB), 6.2, 8.5, 15, 16. Branche `claude/sonnet-w8-12`. Rapport : `docs/agent-reports/sonnet-w8-12.md` (**le livrable principal est le rapport chiffré**).

## Objectif
Répondre par des mesures, sur la TV de référence (32 bits, GaiaOS, ports USB 2.0), à deux questions : (1) **Partage de connexion USB** : quand le téléphone (câble USB) active « Partage de connexion USB », la TV voit-elle une interface IP (`usb0`/`eth1`) avec une adresse DHCP ? Si oui, la voie LAN existante marche dessus **sans code de transport nouveau**. (2) **Accessoire Android (AOA)** : la TV peut-elle jouer l'hôte USB, basculer le téléphone en mode accessoire et lire des paquets bulk ? À quel débit ? Avec quelles boîtes de dialogue ?

## Pourquoi (preuves)
- `UsbLane` était une interface vide « rien n'est simulé » (`core/xfer/Lane.kt:82-85`, supprimée par w8-01) ; `docs/TRANSFER.md` § 4 liste les pistes sans mesure.
- La TV sait déjà lire `/sys/bus/usb/devices` (`core/tv/UsbHardware.kt:85-115`) et dire si la clé partage le bus avec le Wi-Fi.
- `NetState.LinkKind` n'a pas de valeur USB (`core/net/NetState.kt:15`).
- Pas de `FEATURE_USB_HOST` ni `UsbManager` dans le dépôt (vérifié par recherche).

## Fichiers possédés
Nouveaux `R/usb/{UsbLinkWatcher,AoaSpike}.kt` (derrière `BuildConfig.USB_SPIKE`, **faux par défaut** : rien ne tourne en production), `android/receiver/src/main/res/xml/usb_device_filter.xml`, `docs/agent-reports/sonnet-w8-12.md` ; `C/net/NetState.kt` (`LinkKind.USB`, `linkKind(usb = …)`). **Hors zone** : `ReceiverServer.kt` (w8-10 lit `usbIp` par un crochet `device` : demander dans le rapport), `TvService.kt` (w8-11), `sender/**`.

## Étapes
1. **Tethering (sans décision)** : `UsbLinkWatcher` : `ConnectivityManager.registerNetworkCallback` + lecture de `NetworkInterface.getNetworkInterfaces()` pour une interface `usb*`/`rndis*`/`eth1` avec adresse IPv4 ; expose `usbIps(): List<String>` (pour `caps.usbIp`) et `LinkKind.USB` à `NetState`. Mesure : téléphone branché, « Partage de connexion USB » activé **à la main**, noter : interface, adresse, `ping` TV→téléphone, puis `tools/transfer-bench/run.sh --tv http://<ip du téléphone ?>` **ne s'applique pas** (le serveur est sur la TV) : mesurer avec le banc depuis un PC relié par le même lien n'est pas possible ; mesurer avec le téléphone 8c s'il existe, sinon `iperf3` si disponible sur la TV (`adb shell`) ; sinon `adb push` d'un fichier de 100 Mo sur l'interface n'est pas pertinent : **dire ce qui a pu être mesuré**.
2. **AOA (après D-W8-1)** : `AoaSpike` : `UsbManager.deviceList`, requête de permission (`usb_device_filter.xml` pour l'accord automatique à l'attachement), requêtes vendeur AOA (51 → version, 52 → chaînes, 53 → start), réouverture du périphérique en mode accessoire (VID 0x18D1, PID 0x2D00/0x2D01), `bulkTransfer` lecture de 16 Kio en boucle pendant 10 s sur un flux envoyé par une app de test côté téléphone (**un `Activity` de test minimal dans le rapport, pas dans le dépôt**, ou l'exemple AOA d'AOSP). Mesurer : débit brut (Mo/s), CPU TV (`top`), stabilité 60 s, boîtes affichées des deux côtés, comportement quand la clé USB est branchée sur l'autre port (si elle existe).
3. Rapport chiffré : tableau (test, matériel, résultat, débit, boîtes, blocages), **verdict** « vaut le coup / ne vaut pas le coup » pour chacune des deux pistes, et ce qu'il faudrait coder (estimation) si oui : tethering = `caps.usbIp` + rien d'autre ; AOA = `UsbAccessoryLane` (téléphone, `UsbAccessory` + `ParcelFileDescriptor`), `AoaHostServer` (TV) sur `BulkStream`, type de service `connectedDevice` côté téléphone (§ 8.5).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin -PusbSpike=true && gradle --offline :receiver:compileDebugKotlin   # les deux compilent ; sans le drapeau, aucune classe USB n'est instanciée
grep -n "USB_SPIKE" android/receiver/build.gradle.kts | wc -l   # ≥ 1
cd android && gradle --offline :core:test --tests 'castbridge.core.NetStateTest*'   # LinkKind.USB couvert (nom du test à vérifier)
```
Le rapport contient au moins : interface vue ou non, débit mesuré ou « non mesurable : raison », verdict.

## Cas limites
TV sans `FEATURE_USB_HOST` (dire « AOA impossible » et s'arrêter) ; téléphone qui se met en charge seule ; clé et téléphone sur le même hub (débit partagé) ; GaiaOS sans pilote RNDIS (`dmesg` si lisible).

## À ne pas faire
Aucun code USB actif sans le drapeau ; ne pas promettre un débit non mesuré ; ne pas modifier le manifeste du téléphone ; ne pas ajouter de permission au manifeste de la TV hors `USB_SPIKE` (un `uses-feature android.hardware.usb.host required=false` est acceptable).

## Rapport
`STATUT` (BLOQUÉ partiel tant que D-W8-1 n'est pas tranchée), mesures, verdicts, estimation, questions.
