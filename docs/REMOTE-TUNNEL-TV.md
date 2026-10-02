# Administration à distance : côté CastBridge-TV (client du tunnel inverse)

Statut : code écrit et testé en JVM (logique, client SSH contre un sshd en processus) ; **jamais essayé sur une vraie TV ni contre le vrai serveur**. Le serveur est décrit dans `docs/REMOTE-TUNNEL.md` (éteint par défaut). Les conditions d'usage sont dans `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md`.

## 1. Décision du propriétaire (rappel)
Chaque CastBridge-TV ouvre elle-même, en sortie, un tunnel SSH inverse vers le serveur, **sur son propre port** (attribué à l'enrôlement). Pas de consentement par session : silencieux au quotidien, lisible à la demande (« À propos »). Prérogative de l'éditeur (assistance, contrôle du respect des conditions d'usage) ; le produit reste sa propriété. **Le tunnel ne s'applique que si la TV a Internet** : directement, ou par CastBridge (le téléphone) qui partage sa connexion. Une TV hors ligne reste hors ligne : aucune tentative, aucune file.

## 2. Ce que fait la TV, dans l'ordre
Portes (une seule fermée : rien ne démarre, rien n'est envoyé, aucune clé n'est même créée) :
1. **Conditions d'usage acceptées sur cette TV** (§ 4).
2. TV **non verrouillée** et au moins une activation **comptante** (essai OU production).
3. **Internet** (§ 5).

Puis (`castbridge.core.tunnel.TunnelMachine`, un seul fil de travail `cb-tunnel` dans `TunnelHub`) :
1. **Clé** : la TV crée sa propre paire ed25519 au premier besoin (`TunnelSshClient`, graine de 32 octets dans `files/tunnel/client/tunnel_key`, mode 0600, écrite par `SafeFile`). Ce n'est **pas** la clé d'activation. Un fichier abîmé est remplacé (la TV s'enrôle alors avec la nouvelle clé).
2. **Enrôlement** : `POST <serveur>/api/v1/tunnel/enroll` `{activation, sshPublicKey, deviceCode}` ; `activation` = la plus récente activation comptante (`TunnelEnroll.pickActivation` : même filtre que l'insigne de clé), `<serveur>` = la base de `TvConnect` (défaut `https://bridge.sti-cm.com`). La réponse `{host, sshPort, user, port, hostKeyFingerprint?}` est validée (nom d'hôte, ports, utilisateur, empreinte `SHA256:`) et gardée dans `files/tunnel/enrollment.json`. Nouvel enrôlement quand l'activation ou la clé change, ou quand le serveur refuse la clé (`AUTH`) ou la redirection (`FORWARD`).
   - **403** : TV révoquée par l'administrateur : arrêt, pause de **24 h** (persistée, un redémarrage ne la raccourcit pas).
   - **429** : attente d'au moins 10 min ; **503/5xx** : attente exponentielle ; autre refus (400, 404…) : au moins 1 h.
3. **Tunnel** : client SSH sortant vers `host:sshPort`, compte `cbtunnel`, authentification par la clé de la TV. **Clé d'hôte** : l'empreinte `hostKeyFingerprint` annoncée par l'enrôlement est exigée ; sans elle, confiance à la première connexion (TOFU), empreinte retenue (`files/tunnel/host_keys`) puis exigée ensuite (`HostKeyPin`). Demande de redirection distante **exactement** `127.0.0.1:<port>` → `127.0.0.1:2223` (§ 3) ; le serveur doit ouvrir **ce** port, sinon la session est fermée. Garde-vivant `keepalive@openssh.com` toutes les 30 s (réponse attendue sous 25 s, sinon fermeture et reconnexion). Le client n'accepte du serveur qu'une chose : relayer les connexions arrivant sur son port vers le sshd local (ni agent, ni X11, ni autre port).
4. **Reconnexion** : attente exponentielle 5 s → 10 min avec gigue (±25 %, jamais plus de 10 min), remise à 5 s après une session d'au moins 60 s. Un seul fil ; hors ligne, il dort (aucun réveil sauf changement de réseau, d'acceptation ou de clé).
5. **Experts** : `GET <serveur>/api/v1/tunnel/experts` à la montée du tunnel puis toutes les 15 min (5 min après un échec), vérifié par `ExpertsList.verify` (clés de confiance du build ; portée REGISTRY exigée ; pas plus ancienne que la dernière liste acceptée). Liste refusée ou serveur injoignable : **la dernière liste valide continue de s'appliquer** et le motif va au journal. La liste est re-vérifiée à chaque lecture (un fichier modifié ne donne aucune clé) ; un expert dont `notAfter` est passé est retiré aussitôt. `authorized_keys` (lignes `ExpertsList.authorizedKeysLines`) est aussi écrit dans `files/tunnel/experts/`.
6. Démarrage : `TvService.startCore` (donc aussi après un redémarrage de la TV, par `BootReceiver` si « Démarrer avec la TV » est actif) ; arrêt avec le service. `TunnelHub.poke()` réveille la machine quand le réseau change, quand les conditions sont acceptées ou retirées.

États : `IDLE` (verrouillée, sans activation comptante, ou hors ligne), `NEEDS_TERMS`, `ENROLLING`, `CONNECTING`, `UP`, `BACKOFF`, `REVOKED`.

## 3. Le sshd du tunnel est SÉPARÉ de la fonction SSH de l'utilisateur
Deux choses différentes :

| | **SSH de l'utilisateur** (`SshControl`) | **Sshd du tunnel** (`TunnelHub`) |
|---|---|---|
| À qui il sert | la personne (ou son agent) qui administre sa TV | l'éditeur et ses experts (assistance, conformité) |
| Démarrage | action explicite (menu, API à code PIN, Bluetooth) | seulement tant que le tunnel est monté et les conditions acceptées |
| Écoute | port 2222, réseau local | **127.0.0.1:2223 seulement** (boucle locale) |
| Clés | `authorized_keys` de l'utilisateur | uniquement la liste d'experts **signée par le propriétaire**, jamais un mot de passe |
| Essai | **refusé** (`TrialPolicy.SSH_MESSAGE`) | **actif aussi en essai** : c'est le canal de l'éditeur, pas une fonction offerte à l'utilisateur |
| Arrêt | inactivité (30 min par défaut) | avec le tunnel ; jamais d'arrêt sur inactivité |

Le port local est **2223** et non 2222 : 2222 est celui de la fonction SSH de l'utilisateur, qui peut tourner en même temps (deux écoutes sur 2222 se gêneraient). Le serveur ne connaît que le port distant ; il n'y a aucun changement de contrat. Les experts ont `restrict,pty` : un terminal, aucune redirection de port. Les échecs de connexion bloquent l'adresse source 60 s (comme le SSH de l'utilisateur) : comme tout arrive de 127.0.0.1 par le tunnel, un essai raté bloque brièvement tous les experts, ce qui protège aussi contre le tâtonnement.

`TvSshServer` a reçu quatre paramètres facultatifs (`bindHost`, `keyAuthority`, `onLogin`, `idleStop`) ; sans eux il se comporte comme avant.

## 4. Conditions d'usage (porte légale)
- Texte versionné `TunnelTerms.VERSION = « Conditions d'usage v1 »` (articles X, Y, Z de `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md`). **Texte à valider par le propriétaire** (commentaire dans `TunnelTerms.kt`). Changer le texte = changer la version : la TV redemande et le tunnel attend.
- Acceptation conservée dans `files/tunnel/terms.json` (`SafeFile`) : version, date, **installation** (= code d'appareil : un fichier copié sur une autre TV ne vaut rien).
- **Écran d'activation de la TV** (`ActivationActivity`, modes verrouillé **et** passage de l'essai à la production) : texte complet et case « J'ai lu et j'accepte les conditions d'usage ». Tant qu'elle n'est pas cochée : « Valider la clé », la recherche sur la clé USB, la lecture automatique de la clé USB (écran et service) et l'envoi d'une clé par le Wi-Fi depuis le téléphone (`/api/activation/install`, TV verrouillée ou en essai) sont refusés avec le message d'explication.
- **TV déjà activée avant cette version** : boîte de dialogue sur l'accueil, **une fois par démarrage** de l'application tant que les conditions ne sont pas acceptées (« Plus tard » la repose au démarrage suivant), et sur demande dans « À propos : Assistance à distance ». Sans acceptation, la TV fonctionne comme avant mais **aucun tunnel**.
- **Téléphone** (CastBridge, « Activer la TV ») : affiche le même texte avant l'envoi et prévient que la TV demandera l'acceptation sur son écran. L'acceptation elle-même se fait sur la TV.
- Décocher la case (écran d'activation) retire l'acceptation : le tunnel se ferme.

## 5. Chemin réseau : direct ou par le téléphone
Constat dans le code : le partage Internet par Bluetooth **n'est pas une route du système**. `BtGatewayHost` (TV) ouvre un **proxy SOCKS5 local `127.0.0.1:1080`** (`Entry`), chaque connexion devient un flux sur la liaison RFCOMM, et le téléphone (`Exit`, `BtGatewayService`) ouvre la vraie connexion TCP (aucune restriction de port, seul le bouclage est refusé). Les appels de la TV vers le serveur passent déjà ainsi (`Routes`) ; ce proxy n'est utilisé **que** par le code de l'application (pas de VPN, pas de route Android).

Le tunnel suit exactement la même règle (`TunnelConnectivity.choose`) :
1. le réseau propre de la TV répond (sonde `netDirectMs` de `TvService`) → `DIRECT` : sockets directes ;
2. sinon un téléphone est connecté **et** la sonde faite par lui répond (`netGatewayMs`) → `GATEWAY` ;
3. sinon `OFFLINE` : rien.

En `GATEWAY` : l'enrôlement et la liste des experts passent par `HttpLite(proxy)` ; pour SSH, **chaque connexion TCP du client est faite par `Socket(Proxy SOCKS5)` vers `host:sshPort`** (nom non résolu, résolu par le téléphone) et le client SSH parle à un **relais local de boucle** (`LoopRelay`, une connexion, deux fils courts) qui copie vers cette socket. L'échange de clés et la vérification de la clé d'hôte sont donc inchangés. Si le téléphone se déconnecte, la socket meurt, la session se ferme, la machine repasse en `IDLE` (hors ligne) ou se reconnecte en direct. Il n'y a pas de bascule d'une session ouverte d'un chemin à l'autre.

## 6. Transparence
- **« Connexion & réglages »** : ligne « Assistance à distance » = *connectée* / *hors ligne* / *en attente d'acceptation des conditions*, plus une ligne de détail (chemin, nouvel essai dans N s, désactivée par l'éditeur…).
- **Page d'administration web** (`admin.html`) : une ligne sous la bannière d'édition (champ `remoteAssist` de `GET /api/activation`, route autorisée aussi en essai).
- **« CastBridge et vos données »** (`ServerActivity`) : paragraphe `TunnelTerms.PRIVACY` (écran d'information et écran de confidentialité) + l'état.
- **Journal local** : `files/tunnel/journal.log` (+ `.1`), 24 ko par fichier, rotatif ; connexions, déconnexions (avec la raison), enrôlements, refus de liste d'experts, et l'**identifiant de l'expert** à chaque session ouverte. Jamais de clé, de jeton, ni de code d'appareil. Lisible dans le menu de la TV : **« À propos : Assistance à distance (état et journal)… »**.

## 7. Fichiers
- Pur (`android/core`, paquet `castbridge.core.tunnel`) : `TunnelTerms.kt` (texte, acceptation, `TermsStore`), `TunnelEnroll.kt` (requête, réponse, issues, choix de l'activation, `EnrollmentStore`), `TunnelBackoff.kt` (attente, `HostKeyPin`, `HostKeyPins`, `TunnelConnectivity`), `TunnelMachine.kt` (états, interfaces `TunnelEnv` / `TunnelTransport` / `TunnelSession`), `ExpertsSync.kt` (`ExpertsStore`, `ExpertsSync`), `TunnelJournal.kt` (journal, textes). `ExpertsList.kt` (inchangé).
- `android/sshd` : `TunnelSshClient.kt` (clé, client MINA, relais), `TvSshServer.kt` (options du sshd du tunnel).
- `android/receiver` : `TunnelHub.kt` (fil, E/S, sshd du tunnel), `TvService.kt` (démarrage, arrêt, réveil, `/api/activation`), `ActivationActivity.kt`, `PlayerActivity.kt` (dialogue, menu, ligne), `ServerActivity.kt`, `RentalHub.kt` (refus de l'installation de clé sans conditions), `ActivationCenter.kt` (`trustedKeys()`).
- `android/sender` : `ActivateTvActivity.kt` (texte).
- Tests : `TunnelMachineTest`, `TunnelLogicTest` (core), `TunnelSshClientTest` (sshd : vrai client contre un sshd MINA en processus qui joue le serveur, puis un « expert » qui entre par le port redirigé jusqu'au sshd du tunnel).

## 8. Ce qui n'est PAS fait / à vérifier
- **Jamais essayé contre un vrai OpenSSH** ni sur une vraie TV (32 bits, GaiaOS) : consommation CPU/batterie de MINA, démarrage du client NIO2 sur Android, `Socket(Proxy)` avec nom non résolu vers `Entry`, débit du garde-vivant par la liaison Bluetooth. Échange de clés limité à ECDH nistp256/384/521 et DH (comme le sshd de la TV : curve25519 manque sur Android) : à vérifier que le sshd du serveur les propose (OpenSSH le fait).
- Le client crée un `SshClient` par session (arrêté à la fermeture) : économe en repos, coûteux à chaque reconnexion si le réseau clignote (l'attente minimale de 5 s limite la fréquence).
- Pas de bascule en cours de session entre direct et téléphone.
- Un 404 de la liste d'experts (liste retirée du serveur) **conserve** la dernière liste valide : retirer un expert se fait par une nouvelle liste signée plus récente, pas en supprimant le fichier.
- Les clés de confiance sont celles du build (`ActivationCenter.trustedKeys()`) ; la liste de révocation de clés n'est pas consultée pour les listes d'experts.
- Les builds de développement sans exigence d'activation n'ont pas d'activation comptante : pas de tunnel (voulu).
- Le texte des conditions et le paragraphe de confidentialité sont des projets : à faire relire par un juriste avant toute commercialisation.
