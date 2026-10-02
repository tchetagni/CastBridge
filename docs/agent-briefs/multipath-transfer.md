# Brief : transfert de fichiers à débit maximal (plusieurs voies en même temps, fichier découpé)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : LANCÉ (BOARD) : laisser finir ; relance = découper
> **Groupe : X-1** (vague X) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Transfer*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non · découpage proposé : voir ROUTAGE § 2.3

Agent cloud. Base `origin/integration/agents`. Branche `claude/multipath-transfer`. Protocole `docs/COORDINATION.md` (rapport vivant `docs/agent-reports/multipath-transfer.md`). Pas de PR, pas de main. Français, aucun secret.
Lire : `core/.../tv/ResumableUpload*.kt`, `core/.../tv/ResumableBtUpload*`, `sender/UploadService.kt`, `sender/BtUploadService.kt`, `receiver` serveur d'envoi (`ReceiverServer`, stockage `Storage*`, `docs/STORAGE.md`), `docs/BT-PLUG-AND-PLAY.md`.

## Demande du propriétaire
Copier un fichier du téléphone vers la TV le plus vite possible en exploitant le matériel : Bluetooth, Wi-Fi, USB…, découpage du fichier, envoi concurrent, toute technique plus rapide.

## Limites physiques (à respecter dans la conception, ne pas promettre l'impossible)
- OFDM/COFDM des puces Wi-Fi/Bluetooth sont dans leur micrologiciel : Android ne les expose pas. On agit au niveau transport (TCP/HTTP, RFCOMM) : parallélisme, tailles de tampon, découpage, pipeline, choix des voies.
- Mesures sur la TV de référence (32 bits, Android 14) : écriture sur la clé USB exFAT entre 1,4 et 9,5 Mo/s selon la mesure ; mémoire interne ≈ 2,3 Go dont 0,6 Go libres ; Wi-Fi instable ; Bluetooth classique (RFCOMM) bien plus lent que le Wi-Fi (de l'ordre de 0,1 à 0,25 Mo/s). Le goulot est très probablement l'écriture disque de la TV puis le Wi-Fi ; le Bluetooth n'apporte qu'un petit complément. **Mesure d'abord, optimise ensuite** et dis dans le rapport ce qui borne le débit.

## À livrer
1. **Moteur de transfert multivoie** dans `core` (pur, testable) : fichier découpé en blocs (taille adaptative, ex. 1–8 Mio ; 256 Kio pour la voie lente), manifeste (taille, empreinte SHA-256 par bloc), reprise par carte de blocs, assemblage dans un `.part` préalloué côté TV, vérification de bout en bout puis renommage atomique.
2. **Voies (`Lane`)** : `WifiLane` (K connexions HTTP persistantes en parallèle, K adaptatif 1..8 selon le débit mesuré, grands tampons, pas de poignée de main par bloc), `BluetoothLane` (utilise la liaison partagée de `claude/bt-tunnel-keepalive` ou le canal CBT1 existant ; débit faible, concurrent des autres), `WifiDirectLane` (expérimental, désactivé par défaut), `UsbLane` (interface + cahier de recherche : téléphone branché à la TV par câble USB-C ; ne rien simuler ; la copie par clé USB existe déjà) .
3. **Ordonnanceur** : vol de travail (chaque voie prend le prochain bloc dès qu'elle est libre : la plus rapide en fait plus), fin de transfert avec doublon des derniers blocs pour qu'une voie lente ne bloque pas, mise à l'écart d'une voie morte, rééquilibrage.
4. **Contre-pression côté TV** : la TV annonce sa vitesse d'écriture et sa file d'attente ; ne pas dépasser ce que le disque absorbe (sinon on remplit la mémoire pour rien). Écriture `FileChannel` positionnée, tampon ≥ 256 Kio, jamais de copie inutile ; côté téléphone `FileChannel.transferTo` quand possible. Pas de compression pour les médias déjà compressés (par extension), compression optionnelle pour texte/archives.
5. **API additive côté TV** (anciennes TV : repli automatique sur l'envoi actuel) : `POST /api/transfer/begin`, `PUT /api/transfer/chunk?id&idx` , `GET /api/transfer/state?id`, `POST /api/transfer/finish` ; même authentification que le reste (`TvCredential`, jamais de secret dans les URL ni les journaux) ; quotas et nettoyage des `.part` abandonnés ; respecte le stockage (clé USB si présente, voir `docs/agent-briefs/usb-data.md`).
6. **Banc de mesure** `tools/transfer-bench` (JVM, ligne de commande) : contre une vraie TV, mesure le débit pour K = 1, 2, 4, 8 connexions et par voie, écrit un tableau Mo/s ; le propriétaire le lancera sur sa TV. Sans TV : contre un faux serveur à vitesses simulées.
7. **Tests JVM** : voies simulées (vitesses, latences, pertes), équité du vol de travail, fin de transfert, reprise après coupure à chaque étape, bloc corrompu, voie qui meurt, fichier de 0 octet et énorme (> 4 Go, limite FAT32), anciennes versions. Lancer `cd android && gradle :core:test`.
8. **Intégration** : brancher le moteur à `UploadService` (téléphone) derrière un réglage « Transfert rapide (plusieurs voies) » activé par défaut quand la TV le supporte ; la progression existante (pourcentage, durée) reste alimentée. Code Android minimal et évident : le propriétaire compile et teste sur matériel ; dis ce que tu n'as pas pu compiler.
9. Docs : `docs/TRANSFER.md` (principe, voies, limites physiques, mesures), `docs/HANDOFF.md`.

## Coordination
Rapport vivant sur ta branche ; relis cette section à chaque jalon.

## Réponses du coordinateur
- 2026-10-01 15:35 (coordinateur, précision du propriétaire) : les TV varient : certaines ont des ports USB 3, la TV de référence n'a que des **ports USB 2.0** (480 Mbit/s théoriques, ≈ 30 Mo/s en pratique). Or l'écriture mesurée sur sa clé exFAT est de 1,4 à 9,5 Mo/s : **le port n'est donc pas le goulot, la clé (ou le SoC de la TV) l'est**. Conséquences pour le moteur :
  1. Ne suppose jamais un débit d'écriture selon la version USB : utilise la vitesse **mesurée** (préflight de la TV, `writeBps`) et re-mesure pendant le transfert (la vitesse d'une clé chute quand son cache est plein).
  2. Informe seulement (sans en dépendre) du type de port/clé quand c'est lisible (sysfs/`UsbManager`) dans le diagnostic ; message clair si la clé est lente (« votre clé écrit à 1,4 Mo/s : une clé plus rapide accélérera la copie, le Wi-Fi n'est pas en cause »).
  3. Le banc `tools/transfer-bench` doit séparer : débit réseau seul (écriture dans /dev/null côté TV, si l'API le permet) et débit réseau + disque, pour dire lequel borne.
