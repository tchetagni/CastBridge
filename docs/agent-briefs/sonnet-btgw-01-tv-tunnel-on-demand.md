# sonnet-btgw-01 : suites de la passerelle Bluetooth (ce qui n'a pas pu être fait le 2026-10-03)

Contexte : branche claude/bt-gateway-access (bouton visible, bascule automatique, tv-tunnel.sh).
Rapport : docs/agent-reports/bt-gateway-access.md. Gel : décisions pures dans android/core, tests
rouges d'abord, écrans minces, aucune installation sur un appareil sans accord du propriétaire.

1. **SSH à la demande côté TV.** Aujourd'hui, « CastBridge SSH » est fermé tant que le propriétaire
   n'a pas fait MENU > Administration > activer SSH sur la TV (constaté le 2026-10-03 : « Service
   introuvable ou fermé par la TV »). Proposer une requête API authentifiée (code ou jeton du
   propriétaire, journalisée) qui ouvre le SSH pour une durée limitée (ex. 30 min), avec un
   bandeau sur la TV. Décision pure (durée, qui peut, fermeture) dans core ; TV en arm-v7 720p.
2. **Bascule en arrière-plan.** La bascule automatique ne tourne que lorsque l'onglet
   « CastBridge TV » est affiché (point d'appel dans TvHome). La déplacer dans TvLinkManager
   (TvLink.kt, après fusion de claude/pin-persistence) pour couvrir les envois en file et le
   téléphone écran éteint ; réutiliser BtFallback.decide sans le réécrire.
3. **Mode PIN et deux TV.** En mode code (aucune TV de confiance enregistrée), la base
   127.0.0.1:18765 reçoit le code de la TV choisie ; si deux TV existent et qu'une autre est
   appairée, le code pourrait partir vers la mauvaise TV (refus 401). Lier le code à l'adresse
   Bluetooth (clé `bt:<adresse>` de PinKeys) avant d'utiliser la boucle locale.
4. **Signalétique commune.** Aligner LinkLight (vert/orange/rouge/noir) sur la logique de
   claude/tv-status-signals quand elle sera fusionnée (un seul type dans core).
5. **Essai réel** : TV hors Wi-Fi, appairée ; vérifier orange en moins de 15 s, « Connectée par
   Bluetooth », retour au vert et arrêt de la passerelle au retour du Wi-Fi.
