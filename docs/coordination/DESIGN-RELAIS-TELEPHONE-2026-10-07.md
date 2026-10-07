# Conception — Le téléphone, relais fiable et discret de l'utilisateur pour la TV

Date : 2026-10-07. Décisions du propriétaire : « le téléphone sert de relais fiable et discret de l'utilisateur pour des opérations sensibles avec la TV ; modélise-le comme tel et fais vivre à la TV la meilleure expérience hors ligne et en ligne par relais possible » ; « le jeu en ligne doit s'adapter (gestion des synchronisations) dès qu'il existe au moins un relais ; le relais ne requiert pas de consentement explicite dès lors qu'il est synchronisé à la TV par un PIN ». Elles généralisent celles du 2026-10-04 (ordres signés invisibles, téléphone coursier des avis d'activation et de la télémétrie, assistance silencieuse). Base factuelle : inventaire des 18 mécanismes existants (`docs/agent-reports/inventaire-relais-2026-10-07.md`, lecture seule du code).

## 0. En quinze lignes
- Aujourd'hui, **dix-huit mécanismes** font parler la TV, le téléphone et le serveur, avec **quatre files**, **neuf façons de s'authentifier**, **quatre définitions de « la TV a Internet »**, un canal d'ordres **inerte**, un partage d'Internet **manuel** et sans garde de coût, et **aucun** reçu signé. Quand la TV n'a pas Internet et que le téléphone en a (le cas normal), la TV n'obtient ni mise à jour, ni portefeuille, ni télémétrie, ni révocation, ni heure de confiance, ni ordre.
- Le modèle remplace cela par **un sous-système « Relais »** à deux modes, portés par le téléphone déjà **synchronisé** (PIN mémorisé ou jeton de confiance = **le consentement**, aucun écran de plus) :
  - **Coursier** (hors ligne) : une seule file persistée d'**enveloppes signées** (`cbx1`) dans les deux sens, idempotentes, avec **reçus signés par la TV**, reprise bornée, journal de comptes sans contenu.
  - **Tuyau** (en ligne par relais) : la TV atteint le serveur **à travers le téléphone** (SOCKS sur Bluetooth existant), **démarré par la TV elle-même**, silencieux, borné par une politique de coût, arrêté à l'inactivité ; le téléphone ne voit que du TLS.
- **Discrétion** = une ligne d'état par appareil, une notification neutre quand Android l'impose, aucun nom de fichier ni identifiant dans les journaux, une page « Ce que le téléphone a relayé » (comptes et dates), un réglage de retrait par TV.
- **Fiabilité** = enveloppe à identifiant, reçu, file qui survit aux redémarrages (JobScheduler, Bluetooth, ouverture), réessais bornés avec raison (W19 S-1/S-2), classes de taille et de coût, horloge monotone.
- La TV hors ligne vit alors comme une TV en ligne, en différé : mises à jour, licences, jetons, ordres, révocations, heure, télémétrie, avis d'activation ; et en direct quand le tuyau est ouvert : jeu en ligne, portefeuille, assistance.

## 1. Vue opérationnelle

### 1.1 Acteurs et rôles
| Acteur | Rôle dans le relais |
|---|---|
| **Utilisateur** | possède le téléphone ; synchronise la TV une fois (PIN ou « Ajouter ma TV ») ; n'a rien d'autre à faire ; peut retirer le relais pour une TV |
| **Téléphone (CastBridge)** | **mandataire** : porte les enveloppes (coursier), ouvre le tuyau quand la TV le demande, applique la politique de coût et de discrétion, ne lit jamais le contenu scellé |
| **TV (CastBridge-TV)** | **principal** : signe ses reçus et ses avis avec sa clé d'installation, vérifie chaque enveloppe reçue, demande le tuyau, applique les ordres |
| **Serveur** | autorité : signe ordres, révocations, catalogues, heure ; reçoit avis, télémétrie, reçus ; jamais d'accès direct au téléphone de l'utilisateur hors de ces enveloppes |
| **Propriétaire / agent** | émet activations et ordres ; lit la transparence ; assiste (tunnel) |

### 1.2 Modes
| Mode | Quand | Ce que la TV obtient |
|---|---|---|
| **M0 Autonome** | TV avec Internet validé | tout, directement (`Routes`), inchangé |
| **M1 Coursier** | TV sans Internet, un téléphone synchronisé passe (même sans l'ouvrir : jobs, Bluetooth) | en différé : mises à jour APK, lots, packs, ordres, révocations, heure signée, avis et reçus montants, télémétrie et plantages scellés |
| **M2 Tuyau** | TV sans Internet, téléphone synchronisé à portée, besoin « vivant » | en direct : jeu en ligne, portefeuille, assistance, téléchargement immédiat |
| **M3 Rien** | aucun téléphone synchronisé | la TV le dit (« Synchronisez un téléphone pour mettre à jour la TV ») |

### 1.3 Parcours de référence
- **J-R1 Mise à jour de la TV sans Internet** : le téléphone, sur Wi-Fi, apprend du serveur qu'une TV connue a une mise à jour, télécharge l'APK signé (manifeste Ed25519, SHA-256, `Range`), le porte (LAN/Wi-Fi Direct, Bluetooth si petit), la TV vérifie et installe (confirmation à l'écran si Android l'exige) ; un reçu `update.installed` repart par le téléphone. Rien n'est affiché sur le téléphone sauf la ligne « Relais : 1 mise à jour portée ».
- **J-R2 Ordre signé** (ex. réglage parental, révocation, blocage) : serveur → téléphone (job 30 min) → TV à la prochaine rencontre → reçu signé → serveur. Invisible dans l'usage ; visible dans « Politiques appliquées » sur la TV et la page de transparence.
- **J-R3 Activation** : la clé arrive par le téléphone (DESIGN-ACTIVATION-SIMPLE) ; l'**avis d'activation scellé** repart par le même téléphone, le serveur ouvre licence et poste, renvoie un **reçu** que la TV garde (preuve « enregistrée »).
- **J-R4 Jeu en ligne par relais** : l'utilisateur ouvre « Partie Internet » ; la TV n'a pas Internet ; elle demande le tuyau au téléphone synchronisé ; le téléphone l'ouvre sans question ; la TV joue (ticket + activation + preuve, inchangés) ; le jeu adapte ses synchronisations (§ 2.5) ; le tuyau se ferme 10 min après la fin.
- **J-R5 Télémétrie et plantages** : la TV scelle ses lots (4 h, ≤ 64 Kio) ; le téléphone les porte au serveur ; aucun contenu lisible par le téléphone.

## 2. Vue fonctionnelle

### 2.1 Un seul cœur « Relais » (android/core/relay, pur, testé)
| Élément | Rôle |
|---|---|
| `Envelope` | `cbx1` type `relay` : `id` (UUID), `kind` (ex. `order`, `update.apk`, `revocations`, `time`, `lot`, `actnotice`, `telemetry.sealed`, `receipt`), `from`/`to` (serveur, téléphone, TV `deviceCode`), `created`, `ttl`, `size`, `cost` (`small` ≤ 64 Kio, `bulk`), `sig` (émetteur), charge utile opaque ou scellée (X25519 vers la clé d'installation de la TV pour les montants : télémétrie, avis) |
| `RelayQueue` | file persistée **unique** du téléphone (remplace `orders/queue.json`, `lots/delivery-queue.json` pour le sens descendant, et crée le sens montant) : par TV, priorité (`receipt` > `order` > `revocations` > `time` > `actnotice` > `telemetry` > `update` > `lot`), déduplication par `id`, TTL, reprise par bloc pour `bulk` |
| `RelayPolicy` | ce qui part sur réseau facturé (`small` oui ≤ 5 Mo/jour, `bulk` non sauf réglage « données mobiles pour la TV »), heures, batterie (≥ 15 % pour `bulk`), bornes de réessai (W19), quand ouvrir le tuyau (demande TV + synchronisation + politique) |
| `Receipt` | accusé **signé par la TV** (`deviceCode`, `id` de l'enveloppe, résultat, horodatage monotone + heure locale), porté au serveur ; idempotent |
| `RelayJournal` | comptes seulement (`kind`, nombre, octets, date, TV masquée) ; jamais de nom ni de contenu ; alimente la page de transparence |
| `RelayState` | état affichable : « Relais actif pour SMART_TV · 3 enveloppes en attente · dernière rencontre il y a 2 h » |

### 2.2 Transports (existants, réutilisés)
LAN HTTP `/api/relay/*` (nouvelle route TV, jeton ou PIN) ; Bluetooth : canal propriétaire `…0005` types libres 11-15 (`RELAY_PUT`, `RELAY_GET`, `RELAY_RECEIPT`, `RELAY_ASK_PIPE`, `RELAY_STATE`) ; Wi-Fi Direct pour `bulk` (W18) ; tuyau = `BtGateway` SOCKS (M1) ; serveur : `POST /api/v1/relay/{deviceCode}/outbox` (le téléphone dépose les montants), `GET /api/v1/relay/{deviceCode}/inbox?since=` (il retire les descendants), jeton d'appareil du téléphone ; la TV n'est désignée que par son `deviceCode`.

### 2.3 Ce qui passe par le coursier (sens, source d'autorité, ce qui existe)
| Flux | Sens | Autorité | Existe | Travail |
|---|---|---|---|---|
| Ordres signés | ↓ | serveur | code présent, **inerte** (M7 : TV non câblée, `learnTvCode` sans appelant, écho CBTO non lu, serveur éteint) | câbler, migrer sur `RelayQueue`, reçus |
| Mise à jour APK TV | ↓ | serveur (manifeste signé) | TV directe seulement | **`UpdateCourier`** téléphone : détecte, télécharge sur Wi-Fi, porte, reçu |
| Révocations (clés, postes) | ↓ | serveur (`/api/v1/revocations`) | TV sans aucun code | liste signée portée ; la TV l'applique (`InstalledActivations`) ; reçu |
| Heure signée | ↓ | serveur (`time` signé, fenêtre ±5 min, monotone) | aucun | `TvClock` accepte une heure signée récente (TLS, activations, jeu) |
| Lots, packs quiz | ↓ | serveur (catalogues signés) | M8 ok, M9 doublon (I-4) | unifier sur `RelayQueue` ; retirer (a)/(b) de I-4 à terme |
| Avis d'activation + reçu serveur | ↑ puis ↓ | TV (avis signé), serveur (reçu) | conçu (W23B) | enveloppe `actnotice`, route serveur, reçu gardé par la TV |
| Télémétrie, plantages scellés | ↑ | TV | conçu (W21) ; code actuel : direct 15 min via `Routes` | lots scellés 4 h ≤ 64 Kio, `telemetry.sealed`, dépôt serveur ; direct seulement en M0 |
| Rapports de jeu (anti-triche) | ↑ | TV | partiel (direct) | même enveloppe scellée |
| Signalements de contenu | ↑ | TV | M11 (deux voies, dédoublonnées) | basculer sur l'enveloppe |
| Reçus | ↑ | TV | aucun | généralisés |

### 2.4 Le tuyau (M2) rendu automatique et sûr
- **Demande par la TV** : quand une opération « vivante » a besoin d'Internet et que `Routes` n'a pas de voie, la TV émet `RELAY_ASK_PIPE` au téléphone synchronisé à portée (Bluetooth) ou `POST /api/relay/pipe` (LAN) ; le téléphone synchronisé **ouvre le partage sans question** si la politique l'autorise, affiche la notification neutre imposée par Android (« CastBridge relaie pour SMART_TV »), et ferme 10 min après la dernière connexion.
- **Garde de coût** : réseau facturé ⇒ tuyau réservé aux hôtes CastBridge et au jeu, compteur d'octets, plafond jour (5 Mo par défaut, réglable), au-delà la TV le dit ; réseau non facturé ⇒ libre.
- **Portée** : seuls `bridge.sti-cm.com` et le service de jeu (I-5 : aujourd'hui tout hôte et tout port) ; SOCKS local de la TV réservé au processus CastBridge-TV (jeton local).
- **Une seule vérité « Internet »** sur la TV (`NetState` : `direct`, `via_relay`, `none`), consommée par `WalletHub`, `PlayHub`, `LanguesHub`, `TunnelConnectivity` (I-3) ; `LanguesHub` et tout appel serveur passent par `Routes`.

### 2.5 Jeu en ligne par relais (décision du propriétaire)
- La TV compte **en ligne** dès que `NetState = via_relay` ; le téléphone n'est qu'un tuyau (la règle « aucun téléphone ne parle au service » tient).
- **Synchronisations adaptées** : profil de liaison mesuré (`BtGateway` : latence, débit) ⇒ `PlayHub` passe en **mode relais** : horodatages serveur (jamais l'horloge TV), fenêtre de réponse étendue de la latence mesurée (bornée), reprise `resume{roomId, token, lastSeq}` sans pénalité sur coupure < 60 s, pré-chargement des questions de la manche, affichage « Partie par relais : liaison lente » sans alarme, aucune pénalité de classement liée au relais ; le service accepte `X-CB-Via: relay` (information, pas d'autorité).
- Rien de tout cela ne demande un écran de consentement : la synchronisation par PIN **est** le consentement ; un réglage « Ne plus relayer pour cette TV » existe sur le téléphone.

### 2.6 Discrétion (politique unique, `RelayDiscretion`, testée)
- Téléphone : **une** notification neutre quand un service de premier plan est obligatoire (tuyau, port en cours de gros volume), texte sans nom de fichier ni de contenu (« CastBridge relaie pour <TV> »), visibilité privée sur l'écran verrouillé ; sinon aucune. Les envois de fichiers décidés par l'utilisateur gardent leur nom (ce ne sont pas des relais).
- Journaux : jamais de nom de fichier, de code, de clé, d'adresse complète ; comptes seulement (`RelayJournal`). Balayage des `Log.*` existants (M14, M16, M2).
- TV : ligne d'état « Relais : téléphone de <prénom masqué> · à jour il y a 2 h » ; page « Ce que le téléphone a relayé » (comptes par type, dates) ; « Politiques appliquées » (ORDRES § 11) enfin implémentée.
- Transparence contractuelle : les conditions d'usage mentionnent le relais (texte à valider, juridique reporté au 31/12/2026).

### 2.7 Fiabilité (exigences chiffrées)
| id | Exigence | Mesure |
|---|---|---|
| REL-F1 | Toute enveloppe acceptée par le téléphone est livrée ou expirée **avec reçu ou raison** ; jamais perdue en silence | 0 perte sur 1 000 enveloppes avec pannes injectées (`FaultProxy`) |
| REL-F2 | Idempotence : rejouer une enveloppe ou un reçu ×3 = 0 effet de bord | tests cœur |
| REL-F3 | Reprise ≤ 60 s après retour de la liaison ; `bulk` reprend au bloc | W19 S-REPRISE |
| REL-F4 | File survit à : mort de l'app, redémarrage du téléphone, mise à jour de l'app ; déclencheurs : job 15 min, `ACL_CONNECTED`, ouverture, réseau | tests JVM + P-82 |
| REL-F5 | Réessais bornés et comptés, raison stable, aucun réessai sans fin | W19 S-2 |
| REL-F6 | Le téléphone ne peut ni lire ni forger un contenu scellé ; une enveloppe altérée est refusée par la TV ou le serveur | tests de signature |
| REL-F7 | Coût : 0 octet `bulk` sur réseau facturé sans réglage explicite ; plafond `small` respecté | tests `RelayPolicy` |
| REL-F8 | Horloge : aucune décision du relais ne compare l'heure TV à l'heure téléphone ; TTL sur heure serveur signée ou monotone locale | W19 S-8 |
| REL-F9 | Compatibilité : TV ancienne sans `/api/relay` ⇒ repli sur les mécanismes actuels (lots, ordres BT), jamais d'erreur muette | capacités `caps` |

## 3. Vue constructionnelle (où ça vit)
- **core** : `relay/{Envelope,RelayQueue,RelayPolicy,Receipt,RelayJournal,RelayState,RelayDiscretion}.kt` ; `connect/NetState.kt` ; `policy/*` (ordres) réparé ; `update/UpdateCourier` (pur : décision et manifeste) ; `time/SignedTime.kt`.
- **sender** : `RelayRuntime` (jobs, déclencheurs, transports, remplace `OrdersRuntime` et la file de `LotsRuntime` pour le descendant) ; `BtGatewayService` démarrable par demande TV ; réglages (« données mobiles pour la TV », « Ne plus relayer »), page transparence.
- **receiver** : routes `/api/relay/*`, trames `…0005` 11-15, `RelayHub` (reçus, application : `PolicyHub`, `InstalledActivations`, `UpdateInstaller`, `TvClock`), `NetState` unique, `PlayHub` mode relais, lignes d'état et page.
- **backend** : `relay` (inbox/outbox par `deviceCode`, reçus, `activations/report`, `events/relay`, `time`), module `orders` allumé en production (après correction de l'interblocage : fait en 1.2.3), révocations signées.
- **Hygiène préalable** (bugs de l'inventaire) : I-1 UUID `…0002` utilisé par SSH **et** passerelle Internet (collision) ; I-6 `bindProcessToNetwork` qui coupe l'Internet de l'app ; I-8 port 2223 partagé par le tunnel et CastBridge Dev ; I-2/I-15 documents et commentaires périmés.

## 4. Décisions du propriétaire
1. **Coût sur données mobiles** : par défaut `small` ≤ 5 Mo/jour, `bulk` (APK, lots) sur Wi-Fi seulement ; réglage pour autoriser plus. Recommandation : oui.
2. **Retrait** : un réglage « Ne plus relayer pour cette TV » (par TV), éteint par défaut. Recommandation : oui (pas d'écran de consentement, mais une porte de sortie).
3. **Transparence** : page « Ce que le téléphone a relayé » (comptes, dates) sur les deux appareils, et « Politiques appliquées » sur la TV. Recommandation : oui.
4. **Assistance à distance** : deux documents se contredisent (SSH permanent choisi le 2026-10-02 contre WireGuard + consentement 30 min) ; trancher et retirer l'autre ; le client TV reste bloqué par le contrôle automatique.
5. **Révocations vers la TV** (I-12) par le coursier : oui recommandé (sinon une clé révoquée vit 48 h sur une TV hors ligne et une activation libérée reste utilisable).
6. **Heure signée** : la TV accepte une heure serveur signée récente pour TLS, activations et jeu. Recommandation : oui, avec fenêtre ±5 min et jamais en arrière sur l'horloge système (correction interne seulement).

## 5. Parcours de test
- **P-82** Coursier : TV sans Internet, téléphone synchronisé ; mise à jour TV publiée ⇒ téléphone sur Wi-Fi la porte ⇒ TV installée ≤ 30 min après la rencontre ⇒ reçu visible côté serveur ; téléphone redémarré au milieu ⇒ reprise.
- **P-83** Tuyau : « Partie Internet » sur TV sans Internet, téléphone synchronisé à 3 m ⇒ partie jouée, aucune question sur le téléphone, une notification neutre, fermeture 10 min après ; sur données mobiles : plafond respecté et dit.
- **P-84** Ordre signé : blocage parental émis sur le serveur ⇒ appliqué sur la TV à la rencontre suivante ⇒ reçu ⇒ visible dans « Politiques appliquées ».
- **P-85** Avis d'activation : TV activée hors ligne ⇒ licence ouverte côté serveur après passage du téléphone ⇒ reçu gardé par la TV.
- **P-86** Discrétion : aucun nom de fichier, code ou clé dans `logcat` des deux appareils pendant P-82 à P-85 ; une seule notification.

## 6. Chantiers (Sonnet/Haiku, ordre de valeur)
- **relay-R1 Tuyau automatique + vérité réseau unique** : demande TV, démarrage silencieux, garde de coût, `NetState`, `Routes` partout, mode relais du jeu.
- **relay-R4 Hygiène** : I-1, I-6, I-8, I-2, I-15 (petit, risque réel).
- **relay-R2a Coursier descendant** : cœur `relay/*`, câblage des ordres (M7), `UpdateCourier` (APK TV par le téléphone), révocations, heure signée, reçus.
- **relay-R2b Coursier montant** : avis d'activation + reçu serveur (W23B), télémétrie et plantages scellés (W21), rapports de jeu, routes serveur.
- **relay-R3 Discrétion et transparence** : `RelayDiscretion`, balayage des journaux, pages, réglages, conditions.
- Ensuite : portefeuille par coursier (bons W22), W7 synchronisation.
