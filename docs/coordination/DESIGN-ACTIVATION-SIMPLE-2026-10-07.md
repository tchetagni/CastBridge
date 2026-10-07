# Conception — Activer CastBridge-TV : le plus simple et le plus fiable possible

Date : 2026-10-07. Décision du propriétaire : « j'ai eu de la peine à activer l'app TV : Wi-Fi et Bluetooth difficilement configurables, la clé d'activation sur la clé USB n'arrivait pas à être lue. Cela a freiné la dynamique de distribution. Rends l'activation la plus simple et la plus fiable possible. » Cadre : le téléphone est le relais fiable et discret de l'utilisateur ([mémoire 2026-10-07], `DESIGN-RELAIS-TELEPHONE-2026-10-07.md`).

## 0. En dix lignes
- Le client ne configure **ni le Wi-Fi de la TV, ni un appairage Bluetooth, ni une clé USB** pour activer. La TV verrouillée **crée elle-même un réseau** et affiche **un code à 6 chiffres** (et un QR). Sur le téléphone, « Activer la TV » ne demande que ce code : le téléphone rejoint la TV **tout seul** (réseau local si la TV y est déjà, sinon le groupe Wi-Fi Direct dérivé du code, sinon Bluetooth), lit la demande d'appareil, obtient la clé et l'installe. Le téléphone garde son Internet mobile pendant toute l'opération.
- La clé arrive sur le téléphone par la voie disponible : **console propriétaire/agent** sur place (émission locale immédiate), **serveur** (demande envoyée, clé renvoyée dès approbation), ou **collée** (reçue par WhatsApp). Jamais 165 caractères tapés à la télécommande.
- La clé USB reste une voie **préparée par l'agent** (outil de bureau qui écrit la clé au bon endroit, dont le dossier propre de l'application, seul lisible partout) et reconnue par la TV **au branchement**, avec un bandeau et un bouton, jamais une recherche muette.
- Chaque voie dit à l'écran ce qu'elle fait et pourquoi elle échoue ; l'ordre proposé dépend de ce qui est **détecté** (téléphone déjà relié ? clé branchée ? Internet ?).
- Sécurité inchangée : la clé est signée pour cette TV (code d'appareil, clé d'installation), vérifiée comme une clé collée ; le code à 6 chiffres n'est qu'une preuve de présence physique.

## 1. Pourquoi c'était dur (constats)
| Constat (terrain) | Cause | Réponse |
|---|---|---|
| Wi-Fi de la TV difficile | mot de passe box à la télécommande, dongle USB partagé avec la clé, isolation de réseau invité | la TV **héberge** le réseau d'activation (Wi-Fi Direct) ; aucune box requise |
| Bluetooth difficile | visibilité de la TV, appairage, HELLO qui tarde | Bluetooth devient le **3ᵉ repli**, jamais la première proposition |
| Fichier `activation` non lu | Android 11+ cache `Download/` aux apps sans « Accès à tous les fichiers » ; `Android/data/castbridge.receiver/files/` seul sûr ; nom ou extension inexacts | l'outil de l'agent **écrit la clé USB** aux bons endroits ; la TV lit **au branchement** et le dit ; explorateur complet et clé cherchée **dans** le fichier (0.14.43) |
| Code d'appareil à recopier à la main | 19 caractères à l'écran, dictés à l'agent | lu **automatiquement** par le téléphone relié (route verrouillée), partagé en un geste |
| Clé à coller : 165 caractères | format signé `cbx1` incompressible | collage réservé au téléphone (presse-papiers), plus jamais proposé en premier sur la TV |

## 2. Vue opérationnelle : les parcours (du plus simple au plus rare)
**A — Téléphone + code (voie par défaut, hors ligne).** TV allumée ⇒ écran d'activation : « Sur votre téléphone, ouvrez CastBridge › Activer la TV et tapez **482 913** » + QR (URI `WIFI:` du groupe + code). Téléphone : champ « Code affiché sur la TV » ⇒ (1) cherche une TV verrouillée sur le réseau local (mDNS `locked=1`) qui accepte ce code ; (2) sinon rejoint le groupe `WdCode.networkName(code)` / `WdCode.passphrase(code)` par `WifiNetworkSpecifier` (Android 10+, sans quitter l'app, Internet mobile conservé) et parle à `192.168.49.1` ; (3) sinon Bluetooth (existant). Puis lit la **demande d'appareil** (`GET /api/activation/device-request`, route verrouillée, code requis) et affiche « TV trouvée : SMART_TV · code d'appareil ABCD-… ».
**B — La clé.** Trois sources, dans l'ordre de ce qui est disponible : (B1) **console propriétaire / agent** (build superadmin) : « Émettre et installer » (essai ou production, local, immédiat) ; (B2) **serveur** (téléphone en ligne) : « Demander l'activation à CastBridge » envoie la demande (sans `install=`), affiche « Demande n° … envoyée, en attente d'approbation », puis sonde et installe dès que la clé existe (route serveur à créer, § 6) ; (B3) **coller** la clé reçue (WhatsApp, SMS) : le champ accepte le texte ou le fichier ; la demande peut être **partagée** en un geste (texte + QR) à l'agent.
**C — Clé USB préparée par l'agent.** L'outil de bureau écrit sur la clé : `activation` (racine), `Download/CastBridge/activation`, `Android/data/castbridge.receiver/files/activation`, `LISEZMOI.txt`. La TV, au branchement, affiche un bandeau « Clé USB : activation trouvée pour cette TV › **Activer** » (ou la raison exacte : autre TV, périmée, dossier invisible ⇒ chemin à utiliser).
**D — TV en ligne (plus tard, § 6).** La TV envoie sa demande au serveur, affiche un numéro court, l'agent approuve dans la console, la TV sonde et s'active seule.
**E — Saisie à la télécommande.** Dernier recours, conservée.

## 3. Vue fonctionnelle (ce qui change)
| Fonction | TV (CastBridge-TV) | Téléphone (CastBridge) | Pur/testé (core) |
|---|---|---|---|
| F1 Réseau d'activation hébergé | sur l'écran d'activation, créer le groupe `WdCode` (si P2P disponible et Wi-Fi allumé) ; le rendre à la sortie de l'écran ou à l'activation ; afficher code + QR ; dire si impossible et pourquoi (`WifiDirect.Err`) | rejoindre le groupe dérivé du code ; ne jamais couper les données mobiles ; abandonner proprement | `WdCode` (fait), `ActivationRoutePlan` (ordre LAN → WD → BT selon faits) |
| F2 Demande d'appareil lisible | route verrouillée `GET /api/activation/device-request` (code requis, mêmes gardes que l'installation) rendant la demande complète : `code`, `k`, `factor=…`, `install=` (clé publique, quand la TV l'a), `install_sig` (amendé par ACT-F4) | lecture automatique, affichage, partage (texte + QR), copie | `DeviceRequestText.complete` / `forServer` (`core/owner`, créé par act-fix-1) ; test de la route verrouillée |
| F3 Obtention de la clé | — | console (B1) : émettre + installer en un écran ; serveur (B2) : demande + sondage ; coller (B3) ; détection automatique d'une clé dans le presse-papiers | `KeyAcquisition` (états, bornes, messages) |
| F4 Installation et confirmation | résultat identique aux deux écrans (« Activée jusqu'au … » / raison) ; groupe rendu ; code de connexion régénéré (existant `LockedPinRotation`) ; la TV reste joignable pour la liaison de confiance ensuite | enchaîne sur « Ajouter ma TV » (liaison de confiance) si l'utilisateur le veut | existant `ActivationCenter.accept` |
| F5 Clé USB au branchement | diffusion `media mounted` ⇒ recherche des 3 chemins + fichiers `activation*` ⇒ bandeau avec bouton ; raisons exactes ; plus de recherche muette toutes les 15 s sans écran | — | `ActivationLookup` (existant) + `UsbBanner` (états) |
| F6 Écran d'activation guidé | ordre des voies selon détection (téléphone relié ? clé ? Internet ?) ; 5 touches ; gros caractères ; une ligne d'état par voie ; QR ≥ 1/4 de l'écran | — | `ActivationScreenPlan` |
| F7 Outil de bureau | — | — | `castbridge-activation-desktop` : `--cle-usb <volume>` écrit les 4 fichiers, vérifie, affiche le résumé |

## 4. Exigences (traçables, testées en JVM sauf mention)
- **ACT-F1** Une TV verrouillée dont le Wi-Fi est allumé et qui supporte P2P expose un groupe `WdCode` **≤ 10 s** après l'ouverture de l'écran d'activation ; sinon l'écran dit la cause en une ligne.
- **ACT-F2** Le téléphone, avec le seul code à 6 chiffres, atteint la TV **≤ 30 s** dans 3 situations : même réseau local ; aucun réseau commun (groupe dérivé) ; Wi-Fi du téléphone éteint mais Bluetooth appairé.
- **ACT-F3** Le téléphone ne perd pas son Internet mobile pendant l'opération (réseau local-only, `BoundRoute`).
- **ACT-F4** La demande d'appareil n'est jamais recopiée à la main : lue par la route verrouillée, partageable en un geste. **Amendé le 2026-10-07 (act-fix-1)** : la route et le texte partagé portent la demande **complète**, `install=` comprise (c'est la clé **publique** X25519 de l'installation, rien de secret, que le cahier croyait privée ; une clé d'essai en enveloppe v2 l'exige) ; seule la voie serveur (B2) envoie la forme sans `install=` (`DeviceRequestText.forServer`).
- **ACT-F5** La clé n'est jamais tapée sur la TV sauf choix explicite (voie E) ; le téléphone accepte texte, fichier, presse-papiers.
- **ACT-F6** Une clé USB préparée par l'outil est reconnue **au branchement** sur Android 9 à 14, avec ou sans « Accès à tous les fichiers » (dossier propre), bandeau ≤ 5 s après le montage.
- **ACT-F7** Même résultat sur les deux écrans (code de raison, message) ; aucun échec muet ; aucun réessai sans fin (bornes S-1/S-2 de W19).
- **ACT-NF1 Sécurité** : seule une clé signée par une clé de confiance pour **ce** code d'appareil s'installe ; le code à 6 chiffres est borné (5 erreurs ⇒ 60 s, plafond global 20/10 min, existant) ; le groupe dérivé n'existe que sur l'écran d'activation ; le mot de passe dérivé d'un code à 10⁶ valeurs est accepté **uniquement** pour ce groupe éphémère (jamais pour la copie de fichiers, qui garde ses mots de passe aléatoires de 93 bits).
- **ACT-NF2 Discrétion** : aucun code, aucune clé, aucune demande dans les journaux ; une seule notification côté téléphone (« TV activée ») ; la TV ne garde que le résultat.
- **ACT-NF3 Hors ligne** : A + B1 et A + B3 fonctionnent sans aucun accès Internet.
- **ACT-NF4 Compatibilité** : TV 0.14.43 (sans groupe dérivé) + téléphone nouveau = voie LAN/BT, message « Mettez la TV à jour pour l'activation sans réseau » ; TV nouvelle + téléphone ancien = QR Wi-Fi lisible par la caméra du téléphone puis « Activer la TV » existant.

## 5. Parcours de test (à ajouter à PARCOURS-CRITIQUES)
- **P-77** Activation sans box ni Bluetooth : TV neuve, téléphone en données mobiles ⇒ code ⇒ activée ≤ 2 min, Internet du téléphone intact.
- **P-78** Même réseau local : code ⇒ voie LAN choisie, pas de groupe créé.
- **P-79** Clé USB préparée par l'outil ⇒ bandeau au branchement ⇒ Activer ⇒ activée ; même clé sur une autre TV ⇒ « clé d'une autre TV ».
- **P-80** Agent sur place avec console : code ⇒ demande lue ⇒ « Émettre et installer » ⇒ activée ; une seule notification.
- **P-81** Compatibilité : TV 0.14.43 + téléphone 1.2.53 ; TV 0.14.45 + téléphone 1.2.50.

## 6. Décisions du propriétaire
1. **Voie serveur (B2/D)** : créer `POST /api/v1/tv/activation-requests` (demande sans `install=`, numéro court, état) + approbation dans la console web + `GET …/{n}` sondé par le téléphone ou la TV ; qui approuve (agent ? automatique pour l'essai ?) ; aujourd'hui `TRIAL_ISSUANCE=manual`, aucune route d'essai automatique. **Recommandation** : essai approuvé automatiquement une fois par code d'appareil (30 j), production approuvée par l'agent.
2. **Groupe dérivé d'un code à 6 chiffres** (ACT-NF1) : accepter le compromis « présence physique » pour l'activation seule. Recommandation : oui.
3. **Caméra dans l'app** : lire le QR dans CastBridge (bibliothèque ZXing core, ~600 Ko, hors ligne) ou s'en remettre à la caméra du système (gratuit, moins fluide). Recommandation : système d'abord ; ZXing plus tard si le terrain le demande.

## 7. Chantiers (exécution Sonnet/Haiku, 2026-10-07)
- **act-tv** : F1, F2, F5, F6 côté TV (groupe `WdCode`, QR, route verrouillée demande d'appareil, bandeau USB, écran guidé), tests JVM des plans, docs.
- **act-phone** : F1 (jonction par code, `WifiNetworkSpecifier`), F2, F3 (console / coller / partager ; la voie serveur attend la décision 1), F4, tests JVM, docs.
- **act-usb-tool** : F7 (`--cle-usb`), LISEZMOI, test JVM sur dossier temporaire.
- Guide utilisateur à réécrire (section 2) après livraison : « Activer en 3 gestes ».
