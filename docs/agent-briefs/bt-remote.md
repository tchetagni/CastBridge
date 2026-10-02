# Brief : télécommande 100 % Bluetooth, trois voies

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : FUSIONNÉ (BOARD) : ne pas relancer
> **Groupe : —** (vague X) · prérequis : aucun · porte : `—`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non · découpage proposé : voir ROUTAGE § 2.3

Agent cloud. Base : branche `integration/agents` (PAS main). Créer `claude/bt-remote` depuis `origin/integration/agents`,
petits commits, pousser cette branche. Pas de pull request. Ne pas toucher `main`, `feat/ssh`, `integration/agents`.
Apps : « CastBridge » (téléphone), « CastBridge-TV » (TV). Textes utilisateur en français.
Lire d'abord : `docs/REMOTE.md`, `docs/REMOTE-VENDOR-CVTE.md` (protocole validé sur la TV de référence), `docs/BT-PLUG-AND-PLAY.md`,
`docs/HANDOFF.md` (profil de la TV), `docs/agent-briefs/smart-remote.md` (l'agent frère `claude/smart-remote` définit
l'interface `RemoteStrategy` ; si elle manque sur ta base, crée une interface minimale compatible dans un NOUVEAU fichier ; le
propriétaire fusionnera), `core/.../remote/*`, `receiver/.../RemoteHub.kt`, `RemoteAccessibilityService.kt`, `BtServer.kt`
(canal CBTR), sender `RemoteController.kt`, `RemoteService.kt`.

## Demande de l'owner
« La télécommande en Bluetooth exclusivement » : le Wi-Fi de sa TV est défaillant. Trois cas à intégrer ET à rendre testables.

## Cas A — CastBridge natif par Bluetooth (existant, à fiabiliser)
Le canal Bluetooth de télécommande (CBTR) pilote déjà les écrans de CastBridge-TV et le volume/muet partout. Vérifier le
chemin complet sans IP (aucune route IP requise), le message d'état affiché au téléphone (« Bluetooth seulement »), la
reconnexion après coupure, et que la télécommande masque les touches impossibles.

## Cas B — Relais Bluetooth → service de télécommande du fabricant (la TV injecte les touches)
Sur les TV CVTE/Amlogic « marque blanche », un service système écoute en WebSocket sur le port 8125 (toutes interfaces) et
injecte des touches Android pour TOUTE la TV ; il accepte aussi la boucle locale (vérifié : handshake 101 depuis la TV
elle-même). CastBridge-TV peut donc relayer : téléphone --Bluetooth--> CastBridge-TV --ws://127.0.0.1:8125--> service
système. Cela donne flèches/OK/Retour/Accueil/menu/médias/volume dans les AUTRES applications sans accessibilité (bloquée
par les « paramètres restreints » sur cette TV) et sans Wi-Fi.
À livrer (TV, `receiver` + `core`) :
1. `VendorBridge` : client WebSocket minimal écrit à la main (RFC 6455, trames client masquées, ping/pong, fermeture,
   reconnexion avec attente croissante, délais courts), encodeur protobuf minimal (voir le doc : champ 1 type = 1,
   champ 2 action = code de touche Android en décimal), lecture de la trame JSON d'information (nom, capacités, taille).
   Aucune dépendance externe. Cible UNIQUEMENT `127.0.0.1` (jamais une adresse distante), port 8125 par défaut, détection
   automatique de disponibilité (le service existe-t-il ?), état exposé `VendorState {ABSENT, CONNECTING, READY, ERROR}`.
2. Intégration dans `RemoteHub` : quand l'écran de CastBridge n'est pas au premier plan, ou si la touche n'est pas prise en
   charge par l'écran, ou si l'accessibilité est indisponible, router la touche (même liste blanche `RemoteKey` qu'aujourd'hui,
   jamais POWER/veille) via `VendorBridge`. Pas de double envoi (si CastBridge a consommé la touche, ne pas la renvoyer).
   `GET /api/remote/state` : ajout ADDITIF `vendor:{available,state}` ; l'interface du téléphone indique « Toute la TV
   (service du fabricant) » quand c'est prêt. Le relais n'accepte que les ordres déjà authentifiés (jeton de confiance/PIN,
   ou lien Bluetooth d'un téléphone de confiance), avec limite de débit (ex. 30 touches/s) et journal sans secret.
3. Tests JVM : encodage protobuf (vecteurs), trames WebSocket (masquage, longueurs 125/126/65536, fragments), machine d'états,
   routage des touches (qui consomme quoi), liste blanche, limite de débit, faux serveur WebSocket local qui vérifie les
   messages reçus et simule coupure/reprise. Une page de test manuelle sur la TV dans les réglages développeur de l'app :
   « Tester le relais » (envoie volume + puis volume −).
4. Sécurité : documenter que ce service système est sans authentification sur le réseau local (risque pour l'owner) ; ne pas
   exposer le relais hors boucle locale ; ne jamais relayer de contenu non whitelisté.

## Cas C — Le téléphone se déclare clavier/télécommande Bluetooth (HID)
Aucune app requise sur la TV : `BluetoothHidDevice` (Android 9+) sur le téléphone, descripteur HID combinant un clavier
(flèches, Entrée, Échap/Retour, Tab) et un « Consumer Control » (Accueil, Menu, Lecture/Pause, Volume +/−, Muet, Suivant,
Précédent, etc.). À livrer (téléphone, nouveaux fichiers) : enregistrement du profil, connexion au TV déjà appairé (ou
appairage par le menu Bluetooth de la TV), envoi des rapports pour chaque touche (appui/relâchement), état clair
(« non pris en charge par cette TV » si l'hôte refuse), permissions Android 12-14 (`BLUETOOTH_CONNECT`), arrêt propre.
Stratégie `RemoteStrategy` « Bluetooth HID » intégrée à l'orchestrateur du frère `smart-remote`, désactivée par défaut
tant qu'elle n'est pas confirmée par un test de l'utilisateur. Tests JVM : construction du descripteur et des rapports
(octets exacts), table touche → usage HID, idempotence appui/relâchement.

## Intégration et diagnostic
- Écran « Ma TV » (ou section de la télécommande) : pour chaque voie A/B/C, état (disponible / non pris en charge / à tester),
  bouton « Tester cette voie » (volume + puis −, demande « Avez-vous vu le volume changer ? »), et une option
  « Bluetooth exclusivement » qui n'utilise que A/B/C et jamais le Wi-Fi.
- Rapport de diagnostic copiable, sans secret ni adresse Bluetooth complète.
- Docs : `docs/REMOTE.md` (les trois voies, limites, risques), `docs/REMOTE-VENDOR-CVTE.md` (compléter), `docs/HANDOFF.md`
  (entrée datée, sans secret).

## Tests et rapport
`cd android && gradle :core:test` ; si le plugin Android ne se résout pas dans le cloud (Maven 429/proxy), banc « core seul » et
dire clairement ce qui n'a pas pu être compilé (`:receiver`, `:sender`). Le code Android doit rester petit et évidemment
correct : l'owner compile et teste sur son téléphone et sa TV. Rapport final en français : ce qui est livré par voie, tests,
ce qui reste à valider sur matériel, branche poussée.
