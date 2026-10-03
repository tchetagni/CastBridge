# w18-11 — Kit du test terrain (10 minutes, application existante) : protocole, relevés SSH/adb sans secret, table de décision, gabarit de verdict — À LANCER EN PREMIER

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : sonnet si un script dépasse 80 lignes · statut : **PRÊT — premier cahier de la vague**
> **Groupe : W18-terrain** · prérequis : aucun · porte : `python3 -m pytest tools/tests/test_wd_kit.py -q && bash -n tools/wd/tv_wd_facts.sh`
> **Jauge : ≈ 120 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 18-terrain · Effort S · Modèle : haiku · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 7 (protocole, table, diagnostic), § 4 (variantes). Branche `claude/sonnet-w18-11`. Rapport : `docs/agent-reports/sonnet-w18-11.md`. Règle R6 : le script **lit** ; il n'écrit rien sur la TV, ne démarre ni n'arrête aucun groupe (c'est le propriétaire, par le MENU) ; aucune adresse complète ni mot de passe dans la sortie.

## Brique existante
Le bouton « Wi-Fi Direct » de la fiche de la TV (branche `claude/wd-manual-button`) est l'outil principal du test dès qu'il est installé : un appui ⇒ F1/F4/F5/F6/F9 affichés (GO supporté ?, SSID masqué, 192.168.49.1 joignable ?, débit sur 20 Mo, LAN de la TV conservé ?). Le protocole écrit ici propose **(A)** ce bouton et **(B)** l'application existante en secours ; le script SSH complète (F2, F3, F7, F11, `dmesg`).

## Objectif
Donner au propriétaire ce qu'il faut pour établir en 10 minutes le **fait bloquant** B-W18-1…4 avec le bouton (A) ou les applications **actuelles** (B) (TV : MENU › Connexion & réglages › Wi-Fi Direct ; téléphone : onglet Wi-Fi Direct puis onglet CastBridge TV) : (1) `docs/test-plans/WIFI-DIRECT-FIELD-TEST.md` : le protocole § 7.1 pas à pas, les 11 faits F1-F11 à cocher, la table § 7.2, les seuils et leur sens ; (2) `tools/wd/tv_wd_facts.sh` : à lancer sur le Mac, par SSH (relais `nc` du téléphone, mémoire « Relais téléphone → TV ») ou `adb -s <tv> shell`, qui imprime F1-F3 et l'état des interfaces en **une page** ; (3) `docs/agent-reports/w18-field-test.md` : gabarit que le propriétaire remplit (faits, mesures, verdict coché) ; (4) `tools/tests/test_wd_kit.py` : le script passe `bash -n`, la sortie d'exemple ne contient ni mot de passe ni adresse complète (test de masquage sur une sortie factice).

## Pourquoi (preuves)
- `R/WifiDirectGroup.kt` (MENU : nom, mot de passe, 192.168.49.1 affichés) ; `S/WifiDirectScreen.kt` (onglet : nom + mot de passe ⇒ `requestNetwork`) ; `docs/TRANSFER.md` § 6 (banc, `discard=1`) ; `docs/ADMIN.md` § 9 (SSH par clé, port 2222) ; mémoire « Relais téléphone → TV ».
- `C/tv/UsbHardware.kt` (lecture de `/sys/bus/usb/devices`), `R/TvService.kt:1113` (`localIp`).

## Fichiers possédés
Nouveaux `docs/test-plans/WIFI-DIRECT-FIELD-TEST.md`, `tools/wd/tv_wd_facts.sh`, `tools/wd/README.md`, `docs/agent-reports/w18-field-test.md` (gabarit), `tools/tests/test_wd_kit.py` ; `docs/test-plans/README.md` (une ligne). **Hors zone** : tout code Android, `tools/transfer-bench/**`.

## Étapes
1. `tv_wd_facts.sh [--adb <serial> | --ssh <hôte> [-p 2222]]` : exécute **en lecture** : `getprop ro.product.model ro.build.version.release` ; `pm list features | grep -E 'wifi'` ; `dumpsys wifip2p | head -60` ; `ls /sys/class/net/` ; pour chaque interface `cat /sys/class/net/<i>/operstate`, `address` **masquée** (3 derniers octets ⇒ `xx`) ; `cat /sys/bus/usb/devices/*/product /sys/bus/usb/devices/*/idVendor /sys/bus/usb/devices/*/speed` ; `ip addr` **masqué** (`192.168.49.1` affiché tel quel car fixe ; les autres IPv4 ⇒ `a.b.x.x`) ; `dmesg | grep -i -E 'aic|p2p|hostapd|usb .*reset' | tail -20` (si lisible ; sinon « dmesg non lisible ») ; `cmd wifi status 2>/dev/null | head -5` ; **F11 seulement si demandé** : `--try-softap` imprime la commande `cmd wifi start-softap …` **sans l'exécuter** et explique comment la lancer puis `cmd wifi stop-softap`.
2. Sortie : bloc « FAITS » F1 (feature), F2 (combinaisons si `iw` existe, sinon « inconnu : lire F4 »), F3 (module), interfaces, puis « À FAIRE PAR LE PROPRIÉTAIRE : F4…F10 » avec les cases.
3. `WIFI-DIRECT-FIELD-TEST.md` : tableau § 7.1 (minute, geste, lire), tableau § 7.2 (verdict), définitions (Mo/s = ce que l'app affiche ; « LAN gardé » = `ping` de la box depuis la TV pendant le groupe), **ce qu'il ne faut pas faire** (ne pas laisser le groupe du MENU allumé après le test : mot de passe affiché à l'écran), durée.
4. Gabarit `w18-field-test.md` : en-tête (date, versions lues à l'écran « Version », modèle de TV), F1-F11 avec valeurs, verdict coché, « observations » (chaleur, `dmesg`), signature propriétaire.
5. `test_wd_kit.py` : `bash -n` ; fonction de masquage testée sur une sortie factice contenant `mot de passe : abcDEF…` et `192.168.1.117` ⇒ ni l'un ni l'autre en clair.
6. **Vert** : porte.

## Critères d'acceptation
Porte verte ; le script n'exécute aucune commande qui écrit (`grep -n "start-softap\|svc wifi\|setprop\|rm \|echo .*>" tools/wd/tv_wd_facts.sh` ne montre que des `echo` d'instructions) ; le protocole tient en une page A4 ; chaque fait F1-F11 a une case et une définition.

## Cas limites
TV sans `dumpsys wifip2p` (ancien GaiaOS) ⇒ « inconnu » ; SSH indisponible ⇒ `adb` par le relais ; `iw` absent (probable) ⇒ F2 remplacé par le résultat F4 ; propriétaire sans box ⇒ F9 « sans objet » (c'est le cas de 85 % des foyers : le dire).

## À ne pas faire
Aucune écriture sur la TV ; aucun secret dans la sortie ni dans le gabarit ; ne pas modifier le banc ; ne pas cibler la TV du propriétaire depuis un test automatisé.

## Rapport
`STATUT`, exemple de sortie masquée, la page de protocole, rappel au coordinateur : **aucun cahier 18b/18c ne démarre** sans `w18-field-test.md` rempli.
