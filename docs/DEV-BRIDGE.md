# CastBridge Dev : SSH permanent et installation d'APK (outil de développement)

**Jamais distribué.** Application séparée (`castbridge.dev`, module `:devbridge`), pour dépanner et déployer sur la TV de développement sans passer par la clé USB à chaque version. Elle **survit à la désinstallation de CastBridge-TV**, ce qui permet les cycles désinstaller / réinstaller.

## Ce qu'elle fait
- **Serveur SSH permanent** (port **2223**, authentification par **clé seulement**, **réseau local seulement**, verrouillage après échecs), relancé tout seul toutes les 30 s et au démarrage de la TV. Les clés publiques autorisées sont lues **au build** dans un fichier hors dépôt (`~/.ssh/id_ed25519.pub` par défaut, `-PdevKeysFile=`).
- **Commandes** : `ssh -p 2223 tv@<TV> cbdev status | install <apk> | uninstall <paquet> | start <paquet>`. Dépôt d'un APK : `ssh -p 2223 tv@<TV> 'cat > /data/user/0/castbridge.dev/files/inbox/x.apk' < x.apk`.

## À faire une fois sur la TV (ouvrir « CastBridge Dev »)
1. « Autoriser l'installation d'applications » (réglage Android « Installer des apps inconnues »).
2. « Autoriser l'affichage par-dessus » (pour que les confirmations s'affichent).

## Limites d'Android (honnêtes)
- La **désinstallation demande toujours une confirmation** à l'écran de la TV (télécommande). Une installation ou mise à jour en demande une quand l'application est nouvelle, a été installée par un autre installateur, ou si Android < 12. La commande l'annonce et attend 3 minutes. Une mise à jour d'une application **installée par CastBridge Dev** (Android ≥ 12) se fait sans confirmation (à vérifier sur la TV réelle).
- Rien n'est silencieux sans droits système ; l'outil n'en demande pas.

## Sécurité
SSH permanent = surface d'attaque : acceptable sur la TV de développement de l'owner (clé unique, LAN), **à ne jamais installer chez un client**. Retrait : désinstaller « CastBridge Dev ».
