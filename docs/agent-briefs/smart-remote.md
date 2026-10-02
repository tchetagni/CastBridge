# Brief : télécommande « intelligente » du téléphone (stratégies multiples)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : FUSIONNÉ (BOARD) : ne pas relancer ; en cas de reprise, découper
> **Groupe : —** (vague X) · prérequis : aucun · porte : `—`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non · découpage proposé : voir ROUTAGE § 2.3

Agent cloud. Base : branche `integration/agents` (PAS main). Créer `claude/smart-remote` depuis `origin/integration/agents`,
petits commits, pousser cette branche. Pas de pull request. Ne pas toucher `main`, `feat/ssh`, `integration/agents`.
Les apps s'appellent « CastBridge » (téléphone) et « CastBridge-TV » (TV). Textes utilisateur en français.
Lire d'abord : `docs/REMOTE.md`, `docs/REMOTE-VENDOR-CVTE.md`, `docs/HANDOFF.md` (profil de la TV de référence),
`android/sender/.../Remote*.kt`, `core/.../remote/*`, `core/.../connect/Routes.kt`, `LinkPlanner`.

## Demande de l'owner
« Rendre l'appli téléphone intelligente : après identification du fabricant de la TV, elle essaie différentes stratégies de
télécommande. » Le téléphone doit donc piloter le plus de TV possible, pas seulement CastBridge-TV.

## À livrer
1. **Identification de la TV** (module `core`, pur, testé) : à partir d'indices passifs — annonces mDNS/DNS-SD (types de
   service, TXT), réponse SSDP/UPnP (`/description.xml` : fabricant, modèle, services), préfixe d'adresse MAC (OUI),
   nom Bluetooth, ports ouverts connus, signatures HTTP — produire une **empreinte** (fabricant, famille, modèle probable,
   confiance 0–1, preuves) et une **liste ordonnée de stratégies candidates**. Sondage passif d'abord ; jamais d'envoi de
   commande à un appareil que l'utilisateur n'a pas choisi.
2. **Interface `RemoteStrategy`** (core) : `id`, `applicable(fingerprint)`, `connect()`, `send(key)`, `state`, `capabilities`
   (touches, texte, pavé tactile, volume, applications), `probe()` non intrusif, `close()`. Un **orchestrateur** qui essaie les
   stratégies dans l'ordre, avec délais courts, mémorise par TV celle qui a fonctionné (et sa date), bascule sur la suivante
   si elle échoue ou disparaît, ré-essaie la meilleure plus tard, journalise les raisons (sans secret), et garde CastBridge
   natif (déjà existant : HTTP/Bluetooth, jeton) comme première stratégie quand CastBridge-TV est détectée.
3. **Stratégies à implémenter** (chacune isolée, testée avec un faux serveur local ; protocoles décrits par la
   documentation publique/communautaire, jamais copiés d'une app propriétaire) :
   - CastBridge natif (existant, à brancher dans l'orchestrateur) ;
   - **Fabricant CVTE/Amlogic « marque blanche »** : voir `docs/REMOTE-VENDOR-CVTE.md` (WebSocket + protobuf, touches Android
     par code décimal, sans authentification sur la TV de référence) ; inclure l'encodeur protobuf minimal écrit à la main
     (pas de dépendance), la découverte mDNS `_share._tcp`, la lecture du JSON d'information, la reconnexion ;
   - **Android TV / Google TV « Remote v2 »** (appairage par code à 6 caractères, TLS, protobuf) ;
   - **Samsung Tizen** (WebSocket `8001/8002`, jeton d'autorisation) ; **LG webOS** (WebSocket `3000/3001`, appairage) ;
     **Roku ECP** (HTTP `8060`) ; **Sony Bravia** (IRCC/SOAP + PSK) ; **Philips JointSpace** ; **Vizio SmartCast** ;
     **DLNA/UPnP AVTransport + RenderingControl** (lecture, pause, volume sur tout appareil standard) ;
   - **Bluetooth HID** : le téléphone se déclare télécommande/clavier Bluetooth (`BluetoothHidDevice`, Android 9+) pour les
     TV et box qui acceptent un clavier, y compris sans Wi-Fi ;
   - **Infrarouge** (`ConsumerIrManager`) quand le téléphone a un émetteur IR, avec une base de codes minimale par fabricant
     générée par programme ou sous licence libre (documenter l'origine de chaque code) ;
   - Stratégie « Ouvrir l'app du fabricant » (intention vers l'app de télécommande installée sur le téléphone) en dernier recours.
   Si un protocole n'est pas assez sûr de ta part (appairage, chiffrement), implémente le squelette, les tests de l'encodage
   et marque la stratégie `EXPERIMENTAL` (désactivée par défaut) : ne simule pas une réussite.
4. **Interface (CastBridge)** : un écran « Ma TV » qui montre le fabricant identifié et la confiance, la stratégie active,
   les stratégies essayées (réussie / échec / à confirmer), un bouton « Tester » (envoie volume + puis volume − et demande
   « Avez-vous vu le volume changer ? » avant d'enregistrer), un choix manuel de la stratégie, l'explication claire des
   limites (touches impossibles selon la stratégie), et le **diagnostic** copiable sans secret. La télécommande existante
   (`RemoteScreen`) utilise l'orchestrateur et masque les touches non disponibles.
5. **Sécurité et vie privée** : l'utilisateur choisit la TV avant tout envoi ; aucun balayage agressif ; délais et limites de
   débit ; jetons d'appairage stockés dans l'espace privé de l'app, jamais journalisés ; aucune télémétrie des adresses ni
   des codes ; si une TV n'exige aucune authentification (cas CVTE), l'app le signale à l'owner comme risque réseau.
6. **Tests** (JVM, sans matériel) : empreinte (jeux de réponses mDNS/SSDP/OUI de plusieurs marques), ordre des stratégies,
   bascule sur échec, mémorisation, encodage de chaque protocole contre des vecteurs connus, faux serveurs WebSocket/HTTP
   pour CVTE, Roku, Samsung, LG, DLNA ; reprise après coupure. Lancer `cd android && gradle :core:test` ; si le plugin
   Android ne se résout pas dans le cloud (Maven 429/proxy), utiliser un banc de test « core seul » et dire clairement ce
   qui n'a pas pu être compilé (`:sender`). Le code Android doit rester petit et évidemment correct : l'owner compile et
   teste sur son téléphone et sa TV.
7. **Docs** : `docs/REMOTE.md` (stratégies, ordre, limites par marque), `docs/REMOTE-VENDOR-CVTE.md` (compléter si tu
   confirmes d'autres types d'événements), `docs/HANDOFF.md` (entrée datée, sans secret).

## Rapport final (français, court)
Stratégies livrées avec leur statut (stable / expérimental), tests et résultats, ce qui reste à valider sur du vrai matériel,
branche poussée.
