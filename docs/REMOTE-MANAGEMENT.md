# Assistance à distance (remote management) : TV, téléphone, serveur

> Fonction la plus utile à la maintenance (décision du propriétaire, 2026-10-01) : permettre au propriétaire, à un expert ou à l'assistant (Claude) d'**aider à distance** une TV, avec l'accord de l'utilisateur.

## État au 2026-10-01
- **Serveur** : point central **WireGuard** installé et à l'écoute (`wg0`, `10.77.0.1/24`, **UDP 51821** ouvert dans UFW, règle `# CastBridge VPN (WireGuard)`), **aucun poste** déclaré, **pas de NAT, pas de routage vers Internet** ; clé privée dans `/etc/wireguard/castbridge-server.key` (600), clé publique `c+cjEwrkYYH4hoQDVw2MlttdsNhjM7+7Q8aR6rAas1A=`. Sauvegarde du pare-feu avant modification : `/home/ubuntu/castbridge/backups/network-20261001-2010/`. Retour arrière : `sudo systemctl disable --now wg-quick@wg0 && sudo ufw delete allow 51821/udp`.
- **Non vérifié** : l'ouverture du port depuis Internet (WireGuard ne répond pas aux paquets non authentifiés : on ne peut pas le tester sans un poste) ; un éventuel **pare-feu du fournisseur** devant la machine (à contrôler dans l'espace client si le premier poste n'arrive pas à se connecter).

## Principes (non négociables)
1. **Jamais de porte dérobée permanente** : aucun accès sans **consentement de l'utilisateur de la TV** pour une session **limitée dans le temps** (30 minutes par défaut, renouvelable par un nouvel accord).
2. **Consentement visible** : la TV (et le téléphone) affichent un **code à usage unique** que l'utilisateur doit confirmer (touche OK), puis un **bandeau permanent** « Assistance à distance en cours » avec un bouton **Arrêter**.
3. **Niveaux** : (a) **diagnostic en lecture seule** (version, état, journaux expurgés, stockage, noms de fichiers) ; (b) **contrôle** (télécommande, réglages, redémarrage des services) : **second accord** ; (c) **transfert de fichiers** : **troisième accord**. Jamais le contenu des médias de l'utilisateur sans accord explicite, jamais de PIN ni de jeton dans les journaux.
4. **Authentification des experts** : une **clé WireGuard par expert** (révocable), plus un **billet d'assistance signé** par le propriétaire (clé de l'outil d'activation) lié à **une TV précise** et à une **date de fin** : le serveur et le téléphone refusent un expert sans billet valide.
5. **Journal d'audit** (qui, quand, quel niveau, quelles commandes) **des deux côtés et sur le serveur**, chaîné ; consultable par l'utilisateur dans « À propos > Assistance ».
6. **Moindre exposition** : chaque poste n'a qu'une adresse `/32` ; **pas de communication entre postes** sauf règle explicite expert → passerelle sur un port de gestion précis ; services de gestion écoutant **uniquement** sur `wg0`.

## Architecture proposée (phase 1 : le téléphone est la passerelle, car la TV est le plus souvent hors ligne)
- **Serveur** : WireGuard (en place) + `assist-broker` (service du projet, écoute sur `10.77.0.1`) : émet et vérifie les billets, ouvre et ferme les règles de pare-feu par session, tient le journal.
- **Téléphone** (application CastBridge) : un écran « Assistance » : l'utilisateur **autorise** (code à usage unique), l'application rejoint le VPN (bibliothèque WireGuard Android, Apache-2.0, demande de consentement du système) et **expose** à l'expert les services de gestion de la TV qu'elle atteint déjà (API et SSH par Bluetooth ou Wi-Fi, passerelle existante `BtSshGateway`). Elle ferme tout à la fin de la session.
- **TV** : un **mode assistance** dans l'API de gestion (accord affiché à l'écran, niveaux, bandeau, bouton Arrêter, journal) ; le VPN **direct** depuis la TV (quand elle a Internet) est une **phase 2** (le service de VPN d'Android demande un consentement sur l'écran de la TV).
- **Poste de l'expert** : outil `castbridge-assist` (Mac, Windows, Linux) : importe sa configuration WireGuard, demande un billet au propriétaire, ouvre la session, offre le diagnostic, la télécommande et le terminal. C'est aussi par là que **l'assistant (Claude)** pourra intervenir, depuis le Mac du propriétaire, **dans le cadre d'une session ouverte avec son accord**.
- **Provisionnement des postes** : script serveur `castbridge-vpn` (`add <nom>`, `revoke <nom>`, `list`) : la **clé privée d'un poste est générée sur le poste**, le serveur n'enregistre que la clé publique et attribue une adresse `10.77.0.x`.

## Limites honnêtes
- Une **TV hors ligne** et sans téléphone appairé à proximité **n'est pas joignable** : il n'y a pas de magie.
- Le **chiffrement WireGuard protège le transport**, pas contre un expert malveillant : c'est pourquoi les niveaux, le consentement, les billets signés et le journal existent.
- L'accès SSH existant de la TV (code PIN, port 2222) reste **un autre canal** : il faut le **désactiver par défaut hors session d'assistance** (à faire).
- Un VPN sur la machine **partagée** ne doit pas permettre d'atteindre d'autres projets : vérifier que **rien d'autre n'écoute** sur `10.77.0.1` et qu'aucun routage n'est ajouté.
