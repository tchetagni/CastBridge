# Conception W21 — Données techniques du POC : recueillir et analyser, sans gêner le jeu, pour régler la production sur preuves

> Document de conception (architecte, 2026-10-04). **Aucun code n'est modifié par ce document.** Branche de référence `integration/agents`, HEAD `774afb31`. Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `SP/` = `server-play/src/main/kotlin/castbridge/play/`, `B/` = `backend/src/main/java/castbridge/server/`. Les fichiers cités ont été lus le 2026-10-04 (liste § 13) ; rien n'a été exécuté, aucun réseau, aucun appareil, aucun serveur. Les chiffres non mesurés sont marqués « estimé ».

## Demande du propriétaire (verbatim, 2026-10-04)

« Pour la phase de POC, tu vas mettre en place une infrastructure serveur qui va recueillir et analyser les données techniques pour des réglages affinés en phase prod »

**Lecture retenue** : pendant le POC du jeu en ligne (amendement « TV seulement », `DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md`) et pour les deux applications en général, recueillir des **mesures techniques agrégées** (jamais de flux bruts, jamais de données personnelles nouvelles), les **ranger** dans la base existante, les **analyser** (percentiles par classe de liaison, séries temporelles, entonnoir de partie, signaux d'anomalie) et produire des **recommandations chiffrées** pour chaque constante réglable, avec un chemin clair « preuve → valeur livrée ». **Étendre** la télémétrie existante (`docs/TELEMETRY.md`, `POST /api/v1/events/batch`, `kpi_*`, `/admin/kpi`), ne rien dupliquer.

## 0. En vingt lignes

1. **Ce qui existe déjà** (vérifié) : télémétrie d'usage à deux niveaux (essentiel / statistiques d'usage **sur consentement, désactivé par défaut**), catalogue fermé (`B/telemetry/EventCatalog.java`, 27 évènements), validation stricte (liste blanche par évènement, **valeurs scalaires seulement**, `props` ≤ 2 000 caractères, clés interdites), déduplication par UUID, file locale bornée à 2 Mo, agrégats `kpi_*`, pages `/admin/kpi`. Mesures techniques **déjà recueillies** : `connectivity_check` (voie, réussite, latence d'une sonde), `gateway_session` (durée, octets de la passerelle Bluetooth), `cast_end` (canal, débit moyen, réussite, code d'erreur), `playback_*` (codec, décodage matériel, résolution, erreur), `download`, `quiz_game`/`quiz_answer` (Quiz **local** : mode, joueurs, score, durée, temps de réponse), plantages et erreurs, heartbeats (modèle, ABI, stockage). **Rien** sur le jeu en ligne (pas encore câblé), ni RTT, ni temps d'ouverture, ni classe cellulaire, ni mémoire, ni Wi-Fi Direct, ni file de copie au-delà de `cast_end`.
2. **Trou trouvé** : `TelemetryUploader.flush` envoie jusqu'à 500 évènements / 1,5 Mo par lot toutes les 15 min **quel que soit le moment**, y compris par la passerelle Bluetooth **pendant** une partie Internet. **Amendement du propriétaire (2026-10-04, § 3.4, § 3.4 bis-quater)** : la TV n'envoie **plus jamais** de télémétrie par la passerelle Bluetooth ; (a) TV reliée à Internet **sans** passerelle ⇒ elle envoie elle-même **toutes les 12 h** ; (b) TV synchronisée à un téléphone ⇒ elle **remet** au téléphone **toutes les 4 h** (quand il est disponible) un lot **scellé** (chiffré et authentifié de bout en bout TV → serveur), par le **meilleur canal local** (réseau commun > Wi-Fi Direct > Bluetooth, le Bluetooth pleinement permis quand il est le seul) ; (c) le **téléphone synchronisé** (de confiance : `TrustRegistry`/`PinBook` ; n'importe lequel des 8 au plus), coursier opaque, l'envoie au serveur avec **son** Internet (`POST /api/v1/events/relay`) ; (d) **réponse du propriétaire** : les statistiques **propres** du téléphone passent aussi de 15 min à **12 h**, avec la même compression. Jamais pendant une partie Internet ni 30 s après ; jamais par le téléphone pendant qu'il sert de passerelle.
3. **Questions** : 24 questions concrètes (§ 1) ; **catalogue de 59 métriques** (§ 2 : 31 P0, 21 P1, 7 P2), chacune reliée à la constante qu'elle règle et à sa règle de décision.
4. **Forme** : histogrammes **à bornes fixes** calculés sur la TV (bornes posées **exactement** sur les seuils de décision : 400, 1 000, 1 500, 2 000 ms ; 40 kbps), aplatis en propriétés entières (`r0…r11`) : **compatible** avec la validation actuelle (aucun conteneur JSON). Compression rigoureuse (§ 3.4 : DEFLATE brut niveau 9 avec **dictionnaire** versionné, cases vides omises, durées et horodatages quantifiés) : ≈ **0,25-0,35 Ko par partie** et ≈ 2-3 Ko par jour d'usage (estimé) ; plafonds : techniques **8 Ko / jour / TV**, toute la télémétrie **24 Ko / jour / TV** (≈ 1 s de Bluetooth à 20 Ko/s).
5. **Familles neuves** `net.*`, `play.*`, `link.*`, `perf.*`, `sync.*` (16 évènements, dont 3 de remise) au catalogue fermé, niveau **statistiques d'usage**, **plus** une cohorte POC côté serveur : double porte (consentement **et** appartenance à la cohorte), interrupteur par appareil tenu par le propriétaire (`/admin`), renvoyé aux apps dans les directives du heartbeat (`pocMetrics`), **refusé à l'ingestion** hors cohorte.
6. **Confidentialité** : technique seulement ; ni nom, ni pseudonyme, ni IP, ni ASN, ni texte libre, ni titre, ni contenu de question ; pays au plus (déjà connu) ; plancher k = 5 appareils par case affichée ou exportée ; brut 30 jours, agrégats 12 mois ; effacement en cascade existant ; accès administrateur seulement, exports journalisés.
7. **Service de jeu isolé** : `castbridge-play` n'obtient **aucun** identifiant ni accès réseau nouveau ; il écrit des agrégats horaires (sans appareil, sans adresse) dans son volume existant `play-state` ; un **collecteur côté hôte** (cron) les lit par `docker cp` et les **pousse** à l'API locale (`127.0.0.1:7090`) avec une **clé d'ingestion dédiée** qui ne sert qu'à cette route, refusée si la requête vient de nginx. Aucune route `/play/metrics` (le `location ^~ /play/` de nginx l'exposerait).
8. **Stockage** : migration **V62** dans `castbridge-db` (cohorte, quotas, agrégats horaires/journaliers, métriques du service, journal d'exports, **registre des lots scellés** `tele_batch`, jeton précédent de l'appareil gardé 14 jours) ; les évènements bruts restent dans `telemetry_event` avec une purge **par famille à 30 jours** ; plafonds de lignes ; ≈ 50-100 Mo disque au pire (estimé), sans effet sensible sur la mémoire de MySQL (512 Mo).
9. **Analyse** : vues SQL + scripts Python `tools/analysis/` (percentiles par classe de liaison, statistiques **robustes** : médiane des médianes par appareil, poids plafonné par appareil, moyenne tronquée), rapport hebdomadaire Markdown/CSV avec **la valeur proposée et sa confiance** ; section « POC en ligne » dans `/admin/kpi`.
10. **Boucle de réglage** : ≈ 80 % des constantes du jeu en ligne vivent **dans le service** (`castbridge-play`) : elles se règlent par variable d'environnement bornée + `deploy-server.sh --service play` (sans livrer de TV). Les constantes de la TV se règlent par livraison (APK verrouillés). **Configuration distante signée : non justifiée pour le POC** ; en phase 2, si nécessaire, une action `tune.set` dans la **liste fermée des ordres signés existants** (aucun format neuf, aucun `GET /api/v1/config`).
11. **À construire** : 12 cahiers W21 (§ 10, dont w21-01b enveloppe scellée et w21-02b route de relais, ajoutés par l'amendement), ≈ **19-20 $**, ≈ **15 agent·jours** (estimé) ; premières preuves **serveur** dès w21-02 + w21-03 + w21-03b (aucune TV à livrer), preuves **TV** avec w21-04 (après w20-05a).
12. **Décisions** : 17 (§ 11, dont 6 nées de l'amendement d'envoi) ; **D-W21-1 (pour le coursier) et D-W21-12 fermées par le propriétaire** : le coursier est un téléphone synchronisé, n'importe lequel des 8, sous la porte de la TV ; les statistiques propres du téléphone passent de 15 min à 12 h ; les autres ont chacune une recommandation ; aucun « BLOQUÉ » de fait, mais des dépendances (w20-04b, w20-05a fusionnés).

13. **Fraîcheur** : une mesure peut arriver jusqu'à 12 h (direct) ou 4 h + attente du téléphone + son retour en ligne (souvent des heures, parfois des jours) après les faits : agrégats reconstruits sur 14 jours glissants, rapport hebdomadaire **arrêté à J−3**, métrique de fraîcheur M-57 (§ 7.5).

## 1. Les questions auxquelles les données doivent répondre

Chaque question nomme la constante qu'elle règle (fichier:ligne lu) et la métrique du § 2 qui y répond.

| # | Question | Constante réglée (où) | Métriques |
|---|---|---|---|
| Q-1 | Débit et RTT **réels** d'une TV par la passerelle Bluetooth, par classe cellulaire (EDGE/3G/4G/5G), comparés au Wi-Fi et à l'Ethernet, et selon l'heure ? Le plancher de 40 kbps est-il tenu ? | seuils H-PLAY-BT (amendement § 2.7), `SafetySign.SLOW_RTT_MS = 1 500` (`C/quiz/online/SafetySign.kt:43`) | M-01…M-05 |
| Q-2 | Fréquence et durée des coupures (changement de cellule), taux de reprise réussie (même session / `resume` / perte) ? | `fallbackIdleMs = 40 000` (`SP/PlayConfig.kt:91`), `SafetySign.LOST_AFTER_SEC = 60`, courbe de reconnexion 0-2-4-8-15-30 s (w20-05a) | M-06…M-09, M-31 |
| Q-3 | Combien de temps pour ouvrir une partie (ticket API, TCP, TLS, `hello`→`welcome`) par classe ? | délais de connexion de `PlayHttpTransport` (10 s), vie du ticket 600 s, 20 tickets/h | M-10, M-11 |
| Q-4 | Combien d'**annonces de question** arrivent **après** `opensAtServerMs` (marge à la réception), par classe ? Le délai de 1,5 s suffit-il ? | `PlayTiming.INTER_QUESTION_GAP_MS = 1 500` (`C/quiz/online/PlayTiming.kt:12`), règle adaptative D-AM-10 | M-12, M-13 |
| Q-5 | Le critère de fluidité (question ≤ 1,5 s, révélation ≤ 2 s, `ack` ≤ 1,5 s) est-il tenu en vrai ? | coalescence (w20-04b), `outboxMaxBytes = 64 Ko` (`SP/PlayConfig.kt:84`) | M-13…M-15, M-33, M-34 |
| Q-6 | Distribution des temps de réponse par classe de liaison ; écart d'équité entre classes ; pénalités dues aux plafonds ? | `RttBook.MAX_COMPENSATION_RTT_MS = 200`, `MAX_RELAY_RTT_MS = 400`, `MAX_GRACE_MS = 1 000` (`C/quiz/online/RttBook.kt:37-39`), fenêtre 20 s | M-16, M-17, M-35, M-36 |
| Q-7 | Combien de refus `TOO_EARLY`, `CLOSED` (dont « en vol » juste après clôture), `SAME`, `FORBIDDEN` ? | grâce de clôture, `PlayTiming.gate` | M-18, M-37 |
| Q-8 | Combien de temps la TV montre-t-elle orange / rouge, et pourquoi (passerelle, RTT, reprise) ? | `SLOW_RTT_MS`, `LOST_AFTER_SEC` | M-19 |
| Q-9 | Quels refus `PLAY_*` (création, entrée, reprise, ticket) et à quelle fréquence ; tickets brûlés ? | plafonds `createsPerIpPerHour = 20`, `createsPerIdentityPerDay = 30`, `maxRoomsPerSubject = 2`, essai 3/jour, 20 tickets/h | M-20, M-38, M-39 |
| Q-10 | Les plafonds d'adresse partagée (CGNAT) gênent-ils des joueurs légitimes ? | `maxPerIp = 24`, `maxPerIpShared = 64`, `connPerMinute = 60` (`SP/PlayConfig.kt:44-52`) | M-39, M-40 |
| Q-11 | Taille réelle d'un `state` à 8 joueurs, octets par partie et par type de message, effet de la coalescence ? | coalescence, vues différentielles (phase 2), budget 40 kbps | M-21, M-33, M-41 |
| Q-12 | Coût réseau de l'entretien (`ping` de salle 5 s, ping WS 25 s) et vitesse de convergence du RTT retenu (minimum de 8 échantillons) ? | `ServerRoom.PING_EVERY_MS = 5 000` (`C/quiz/online/ServerRoom.kt:599`), `pingMs = 25 000`, `pongTimeoutMs = 40 000`, `RttBook.WINDOW = 8` | M-42, M-04 |
| Q-13 | Le service tient-il (latence du tick, tas, connexions, salles) ? | `tickMs = 200`, `mem_limit 384m`, `maxRooms`, `maxConnections` | M-43, M-44 |
| Q-14 | Combien de téléphones par TV en partie, combien de refus au 9e, latence du relais local téléphone → TV ? | `maxRelayedPerTv = 8` (w20-04b) | M-22, M-23 |
| Q-15 | Le PIN de la TV est-il redemandé à tort (R-10) ; renouvellements de jeton silencieux ? | `PinBook` par `tvId`, `CredentialGate` | M-45 |
| Q-16 | La file de copie : états finaux (Terminé vérifié / Repris / Abandonné / En attente), réessais, refus (R-17), doublons évités (R-12), reprises (R-09) ? | bornes de réessai W19 § 3.3, `TransferQueue` | M-24, M-25 |
| Q-17 | La lecture ralentit-elle la copie (R-15/R-16) : temps « ralenti », plancher 512 Ko/s atteint, coupures de lecture pendant une copie ? | `PlaybackPriority.*` (`C/xfer/PlaybackPriority.kt:86-101`), `BufferGovernor.FULL_SEC/LOW_SEC/HOLD_SEC` (`C/xfer/BufferGovernor.kt:103-114`) | M-26, M-27 |
| Q-18 | Wi-Fi Direct (W18) sur le terrain : temps de jonction, débit, pertes, changements de voie ? | `rerouteAfterLoss` (3 échecs/10 min), délais `WdPolicy` | M-28, M-29 |
| Q-19 | La TV 32 bits souffre-t-elle (tas, mémoire basse, images perdues) pendant une partie Internet, une copie, une lecture ? | ABI armeabi-v7a, 720p (mémoire du propriétaire) | M-30, M-46 |
| Q-20 | Temps de chargement des tranches embarquées de la banque de questions et coût mémoire sur TV 32 bits ? | politique de préchargement de la banque | M-47 |
| Q-21 | Temps des vignettes (`R/Thumbnailer.kt`) et chemin de décodage (matériel/logiciel) ? | politique de vignettes | M-48, (`playback_*` existant) |
| Q-22 | Les routes de la TV (direct / passerelle, collant 10 min, `C/connect/Routes.kt:16`) changent-elles souvent ? | `stickyMs = 600 000` | M-49 |
| Q-23 | Combien coûte la télémétrie elle-même, par chemin (direct 12 h / remise au téléphone 4 h) et par canal local (réseau commun, Wi-Fi Direct, Bluetooth) ? Les lots arrivent-ils, avec quel retard ? | cadences 12 h / 4 h, plafonds de lot, bornes des files, TTL | M-50, M-53…M-59 |
| Q-24 | Les mesures sont-elles saines (refus de cohérence, quotas atteints, appareils aberrants) ? | quotas d'ingestion | M-51, M-52 |

## 2. Catalogue des métriques (59 lignes)

Conventions : **Où** = TV, Tél. (téléphone), SP (service de jeu), API ; **Éch.** = échantillonnage ; « hist. LAT » = histogramme à bornes fixes du § 3.2 ; cardinalité = nombre maximal de valeurs distinctes des dimensions (toutes **fermées**). Les dimensions communes `via` ∈ {`ethernet`,`wifi`,`hotspot`,`bt_gw`,`none`} (6) et `cell` ∈ {`2g`,`3g`,`4g`,`5g`,`unk`,`-`} (6) ne sont **jamais** libres. P0 = indispensable au POC, P1 = utile au réglage de production, P2 = confort.

| id | Métrique (évènement.prop) | Unité | Où | Éch. | Card. | Règle → paramètre | P |
|---|---|---|---|---|---|---|---|
| M-01 | `net.link.r*` RTT applicatif (aller-retour des POST / `ping`-`pong` vus par la TV) | hist. LAT ms | TV | chaque mesure, résumé par session de jeu | via×cell 36 | RTT médian ≤ 1 000 et p95 ≤ 2 000 par classe (≥ 5 appareils) ⇒ seuils H-PLAY-BT tenus ; sinon la classe est déclarée « hors POC » (démo en 3G/4G) | P0 |
| M-02 | `net.link.t*` débit descendant passif (octets reçus / durée des rafales ≥ 2 Ko) | hist. KBPS | TV | par rafale, résumé par session | 36 | part des rafales < 40 kbps > 10 % pour une classe ⇒ la classe ne tient pas le plancher (amendement R10) | P0 |
| M-03 | `net.link.u*` débit montant passif (POST `act`/`relayAct`) | hist. KBPS | TV | par POST ≥ 1 Ko | 36 | p10 < 30 kbps ⇒ regrouper les `relayAct` d'une même rafale (phase 2) | P1 |
| M-04 | `net.link.j` gigue (écart interquartile du RTT) | ms | TV | par session | 36 | IQR > 500 ms ⇒ la marge du délai entre questions doit couvrir p95, pas la médiane | P1 |
| M-05 | `net.link.h` heure locale de la session (0-23, tranche de 3 h) | tranche | TV | par session | 8 | p50 RTT d'une tranche > 1,5 × la journée ⇒ avertissement « heures chargées » dans PLAY-OPS | P2 |
| M-06 | `net.link.cuts` coupures du flux (aucun octet > 5 s) | nombre | TV | par session | 36 | coupures/heure par classe ; sert M-07…M-09 | P0 |
| M-07 | `net.link.o*` durée des coupures | hist. OUT ms | TV | par coupure | 36 | p95 > 40 s pour `bt_gw` ⇒ relever `fallbackIdleMs` à 60 s (borne 30-90 s) | P0 |
| M-08 | `net.link.rs_same / rs_resume / rs_full / rs_lost` issue des reprises | nombre | TV | par coupure | 4 | `rs_lost / cuts` > 5 % ⇒ revoir la courbe de reconnexion (M-09) puis `fallbackIdleMs` | P0 |
| M-09 | `net.link.rt*` délai coupure → reprise effective | hist. OUT ms | TV | par reprise | 36 | médiane de coupure ≈ 5-10 s et reprise médiane > coupure + 6 s ⇒ courbe 0-1-3-6-10-15 s au lieu de 0-2-4-8-15-30 s | P0 |
| M-10 | `net.conn.ms_ticket / ms_tcp / ms_tls / ms_hello` décomposition de l'ouverture | hist. SET ms | TV | par ouverture | 36 | `ms_total` p95 > 8 s pour une classe ⇒ réutiliser la connexion TLS de l'API pour le ticket, ou demander le ticket plus tôt (pendant l'écran Code) | P0 |
| M-11 | `net.conn.ok / fail` issue de l'ouverture + code (`PLAY_*`, `tls`, `dns`, `timeout`, `socks`) | nombre × code | TV | par ouverture | ≤ 24 codes | taux d'échec > 5 % sur une classe ⇒ chercher le code dominant (proxy SOCKS de la passerelle, heure TV) | P0 |
| M-12 | `play.timing.l*` marge à la réception d'une annonce = `opensAt − (serverNow + rtt/2)` sur l'horloge locale | hist. LEAD ms (négatif = en retard) | TV | par question | 36 | **règle du délai** : `late%` = part < 0 ; si `late%` ≤ 1 % **et** p5 ≥ 300 ms pour toutes les classes (≥ 5 appareils, ≥ 200 questions) ⇒ garder 1 500 ms (ou essayer 1 250) ; si `late%` > 2 % pour une classe ⇒ mode adaptatif `clamp(max(1 500, pire RTT + 500), 1 000, 2 000)` (D-AM-10) ; jamais > 2 000 sans accord | P0 |
| M-13 | `play.timing.late` annonces reçues après l'ouverture | nombre | TV | par partie | 36 | idem M-12 (compte exact) | P0 |
| M-14 | `play.timing.v*` délai clôture → révélation reçue | hist. LAT ms | TV | par question | 36 | part > 2 000 ms > 5 % ⇒ le critère de fluidité échoue ; voir M-33 (file) avant tout | P0 |
| M-15 | `play.timing.a*` délai `relayAct` envoyé → `ack` reçu | hist. LAT ms | TV | par réponse | 36 | part > 1 500 ms > 5 % ⇒ WebSocket (une connexion) en phase 2 au lieu des POST | P0 |
| M-16 | `play.timing.w*` temps de réponse en % de la fenêtre, joueurs de cette TV | hist. FRAC | TV | par réponse | 36 | p90 `bt_gw` − p90 `wifi` > 10 points ⇒ examiner l'équité (M-35/M-36) ; jamais d'allongement de fenêtre par classe (triche) | P1 |
| M-17 | `play.timing.lr*` relais local : réponse du téléphone reçue → `relayAct` posté | hist. LAT ms | TV | par réponse | 6 (voie locale) | p95 > 400 ms ⇒ le relais local mange la marge `MAX_RELAY_RTT_MS` : traiter la file locale avant le flux | P1 |
| M-18 | `play.game.too_early / closed / same / forbidden` | nombre | TV | par partie | 4 | `too_early` > 0,5 % des réponses ⇒ horloge d'affichage en avance (règle 11) : corriger le client, pas le serveur | P0 |
| M-19 | `play.game.orange_ms / red_ms` + cause dominante (`gw`, `rtt`, `resume`) | ms | TV | par partie | 3 causes | orange > 50 % du temps avec fluidité tenue (M-14/M-15) ⇒ proposer `SLOW_RTT_MS` 2 000 (sémantique du signe : décision du propriétaire) | P0 |
| M-20 | `play.fail.reason` refus hors partie (`PLAY_*`, étape `ticket/create/join/resume`) | nombre × code | TV | par refus, ≤ 20/jour | ≤ 16 × 4 | `PLAY_BUSY` légitime (sans signal robot) > 1 % des créations ⇒ relever le plafond concerné dans sa borne | P0 |
| M-21 | `play.game.bytes_state / bytes_q / bytes_other / max_state` octets reçus par type | octets | TV | par partie | 1 | remplace l'estimation « ≈ 5 Ko / état à 8 » (R-7) ; `max_state` > 8 Ko ⇒ vues différentielles en phase 2 | P0 |
| M-22 | `play.game.phones` téléphones relayés (max sur la partie) | nombre 0-8 | TV | par partie | 1 | distribution ; dimensionne M-21 | P1 |
| M-23 | `sync.relay.cap_refused` 9e téléphone refusé | nombre | TV | par partie | 1 | > 2 % des parties ⇒ demande produit (pas de changement de plafond sans décision) | P2 |
| M-24 | `sync.xfer.final` état final + code (`Reason`) + réessais + reprises | nombre × état | TV et Tél. | par élément de file | 4 états × ≤ 32 codes | réessais p95 > 6 ou `Abandonné` > 3 % sur un code ⇒ revoir la borne de réessai du code (W19 § 3.3) | P1 |
| M-25 | `sync.xfer.dedup / resumed_bytes` doublons évités (R-12), octets repris (R-09) | nombre, octets | Tél. | par élément | 1 | aide à prouver la reprise par blocs ; aucune règle automatique | P2 |
| M-26 | `sync.xfer.slowed_ms / floor_hits` temps « copie ralentie », plancher 512 Ko/s atteint (R-15/R-16) | ms, nombre | TV | par élément | 1 | `floor_hits` fréquents sans saccade de lecture (M-27 = 0) ⇒ relever le plancher ; saccades ⇒ baisser `PLAYING_MAX_STREAMS` ou relever `HOLD_SEC` | P1 |
| M-27 | `perf.hour.rebuf` saccades de lecture pendant une copie active | nombre | TV | horaire | 2 (copie oui/non) | saccades/heure avec copie > 2 × sans copie ⇒ bande de confort `BufferGovernor` trop serrée | P1 |
| M-28 | `link.wd.join_ms` temps de jonction Wi-Fi Direct (W18) + issue + code | hist. SET ms | TV et Tél. | par jonction | ≤ 12 codes | p90 > 20 s ⇒ ajuster les délais de jonction ; échecs > 20 % sur un modèle ⇒ voie BT par défaut pour ce modèle | P1 |
| M-29 | `link.wd.kbps / drops / reroutes` débit, pertes, changements de voie | hist. KBPS, nombre | TV | par session | 1 | `reroutes`/h > 1 ⇒ revoir `rerouteAfterLoss` (3 échecs / 10 min) | P1 |
| M-30 | `perf.hour.heap_p95 / heap_max / lowmem` tas de l'app, alertes mémoire basse | Mo, nombre | TV | toutes les 60 s, résumé horaire | ABI 3 × état 4 (`idle/play/copy/online`) | `heap_p95` > 75 % de la limite en partie Internet sur armeabi-v7a ⇒ réduire les tampons (`EventRing`, file SSE) | P0 |
| M-31 | `link.gw.ms / bytes / reconnects / socks_fail` sessions de passerelle | ms, octets, nombre | TV | par session de passerelle | cell 6 | `socks_fail` > 2 % des connexions ⇒ défaut du proxy local, pas du réseau | P0 |
| M-32 | `link.gw.cell` classe cellulaire déclarée par le téléphone passerelle (sans permission, § 3.6) + classe **mesurée** (bande de RTT/débit) | classe | Tél. → TV | à l'attache et au changement | 6 | désaccord déclarée/mesurée > 30 % ⇒ n'utiliser que la classe mesurée dans les rapports | P0 |
| M-33 | `srv.outbox_hw` plus haute file de sortie par connexion + `srv.coalesced` états remplacés | hist. octets, nombre | SP | par connexion / par message | transport 3 × link 6 | `outbox_hw` p99 > 32 Ko ⇒ risque de 1008 : vues différentielles ; `coalesced` prouve la parade | P0 |
| M-34 | `srv.close` fermetures par code (1000/1001/1008/idle/pong) | nombre × code | SP | par fermeture | ≤ 8 | 1008 par file pleine > 0,1 % des connexions ⇒ idem M-33 | P0 |
| M-35 | `srv.comp_pen` pénalité due au plafond de compensation (`rtt/2 − 100 ms`) | hist. LAT ms | SP | par réponse comptée | link 6 | p95 par classe : publié dans le rapport ; **ne jamais relever** `MAX_COMPENSATION_RTT_MS` sans décision (marge de triche) | P1 |
| M-36 | `srv.relay_pen` pénalité du plafond relais (`rttTV − 400 ms`) | hist. LAT ms | SP | par `relayAct` | link 6 | idem : `MAX_RELAY_RTT_MS` **jamais** relevé (amendement § 2.7) ; la valeur sert à expliquer les scores | P1 |
| M-37 | `srv.ack` résultats d'`ack` par type + `srv.grace_ok` réponses acceptées pendant la grâce + `srv.closed_late` `CLOSED` arrivées < 1,5 s après clôture | nombre | SP | par réponse | 9 × link 6 | `closed_late` > 1 % des réponses d'une classe ⇒ proposer `MAX_GRACE_MS` 1 250 (borne 1 500, décision) | P0 |
| M-38 | `srv.refuse` refus par `PLAY_*` et étape | nombre × code | SP | par refus | ≤ 16 × 4 | croisé avec M-20 ; tickets brûlés / tickets émis > 10 % ⇒ revoir l'ordre des contrôles | P0 |
| M-39 | `srv.cap_hit` plafonds atteints (`per_ip`, `per_ip_shared`, `creates_ip_hour`, `identity_day`, `trial_day`, `rooms_subject`, `conn_minute`) | nombre | SP | par refus | 7 | atteinte sans signal robot > 1 % des créations ⇒ relever dans la borne (variables existantes de `PlayConfig`) | P0 |
| M-40 | `srv.ip_conc` connexions simultanées par adresse (max horaire, **aucune adresse gardée**) | hist. 1-2-4-8-16-24-32-64 | SP | horaire | 1 | p99 proche de 24 ⇒ CGNAT réel : relever `maxPerIp` ou étendre la liste « adresse partagée » | P1 |
| M-41 | `srv.bytes` octets envoyés par type de message (`state`, `question`, `reveal`, `ack`, `ping`, autres) + `srv.state_sz` taille d'un `state` par nombre de joueurs | octets, hist. | SP | par message | 6 × 8 | taille réelle à 8 joueurs ; budget 40 kbps vérifié | P0 |
| M-42 | `srv.rtt` RTT par transport et classe déclarée (`hello.link`) + `srv.rtt_conv` délai avant 8 échantillons | hist. LAT ms | SP | par `pong` | 3 × 6 | convergence p50 > 40 s ⇒ ping de salle à 3 s pendant les 60 premières secondes seulement (coût ≈ +0,1 kbps) | P1 |
| M-43 | `srv.tick_lag` retard du tick | hist. 10-25-50-100-200-500 ms | SP | par tick (résumé) | 1 | p99 > 100 ms ⇒ capacité (vCPU) avant toute hausse de `maxRooms` | P1 |
| M-44 | `srv.heap / conns / rooms` maxima horaires | Mo, nombre | SP | horaire | 1 | tas > 85 % de 384 Mo ⇒ baisser `maxRooms` (complète `/play/health`) | P1 |
| M-45 | `sync.pair.outcome` (`token_renewed`, `pin_reused`, `pin_asked_once`, `pin_asked_again`, `locked`) | nombre | Tél. | par association | 5 | `pin_asked_again` > 0 ⇒ régression R-10 (alerte, pas de réglage) | P1 |
| M-46 | `perf.hour.jank` images > 100 ms (Choreographer) et p95 du temps d'image | nombre, ms | TV | horaire | état 4 | p95 > 50 ms en partie Internet ⇒ alléger le rendu du bandeau / de la vue | P1 |
| M-47 | `perf.quiz.slice_ms / heap_delta / questions` chargement d'une tranche embarquée | hist. UI ms, Mo | TV | par chargement | ABI 3 | p90 > 1 s sur armeabi-v7a ⇒ précharger la tranche suivante pendant le délai entre questions | P1 |
| M-48 | `perf.thumb.th*` temps de vignette + échecs | hist. UI ms | TV | horaire | 1 | p90 > 500 ms ⇒ file de vignettes basse priorité pendant la lecture | P2 |
| M-49 | `net.route.flips` changements de route (direct ↔ passerelle) | nombre | TV | horaire | 1 | > 3 / heure ⇒ allonger `stickyMs` | P2 |
| M-50 | `perf.hour.tele_bytes` (TV) et `sync.courier.self_bytes` (téléphone) octets de télémétrie compressés par jour, par chemin `direct`/`phone`/`self` | octets | TV et Tél. | horaire / quotidien | 3 | > 8 Ko techniques ou > 24 Ko au total / jour ⇒ resserrer l'échantillonnage ou le dictionnaire ; **> 0 octet vers le serveur par la passerelle = défaut** | P0 |
| M-51 | `api.tech_rejected` refus d'ingestion par motif (cohérence, quota, cohorte, catalogue) | nombre × motif | API | par lot | ≤ 8 | un appareil > 20 % de refus de cohérence ⇒ exclu des recommandations (§ 7.3) | P0 |
| M-52 | `api.tech_rows` lignes brutes techniques, taille des tables | nombre, Mo | API | quotidien | 1 | > 80 % du plafond ⇒ alerte et échantillonnage plus fort | P2 |
| M-53 | `perf.hour.q_bytes / q_batches / q_evicted / q_expired` file scellée de la TV | octets, nombre | TV | horaire | 1 | `q_evicted + q_expired` > 0 sur > 10 % des TV ⇒ cadence de remise ou TTL à revoir ; file > 50 % de sa borne sur une TV ⇒ téléphone absent (dire au propriétaire) | P0 |
| M-54 | `tele.handoff` remise TV → téléphone : `chan` (`lan`/`wd`/`bt_api`/`bt_frames`), `ok`, `fail` (code fermé), `ms`, `bytes`, `chunks`, `resumes`, `paused_ms` (suspendue par copie/lecture/partie) | ms, octets, nombre | TV | par remise | 4 canaux × ≤ 8 codes | échec > 10 % sur un canal ⇒ revoir les morceaux (4 Ko) ou l'ordre des canaux ; `ms` p95 Bluetooth > 30 s ⇒ réduire le lot | P0 |
| M-55 | `sync.courier` file du téléphone coursier : `tvs`, `bytes`, `batches`, `evicted`, `expired`, `fwd_ok`, `fwd_fail`, `wait_ms` (remise reçue → envoi au serveur), `self_bytes`, `self_sends` (statistiques propres, cadence 12 h) | nombre, octets, hist. OUT/SET | Tél. | quotidien | 1 | `wait_ms` p95 > 24 h ⇒ le téléphone reste hors ligne : rapport « fraîcheur » ; `evicted` > 0 ⇒ bornes par TV trop basses | P1 |
| M-56 | `api.relay_rejected` enveloppes refusées par motif (`mac`, `token_stale`, `replay`, `too_old`, `quota_phone`, `quota_tv`, `size`, `cohort`) | nombre × motif | API | par enveloppe | 8 | `mac` > 0 ⇒ alerte sécurité (téléphone falsificateur ou défaut) ; `token_stale` > 2 % ⇒ allonger la garde du jeton précédent | P0 |
| M-57 | `api.tele_lag` fraîcheur = réception serveur − `ts` le plus récent du lot, par chemin `direct`/`phone` | hist. 1 h, 4 h, 12 h, 24 h, 48 h, 72 h, 7 j, 14 j, 30 j | API | par lot | 2 | part > 72 h > 10 % ⇒ le rapport hebdomadaire doit attendre plus que J−3 | P0 |
| M-58 | `api.tele_dup` lots reçus plusieurs fois (par un ou plusieurs téléphones) et évènements dédoublonnés | nombre | API | par lot | 2 | > 20 % ⇒ accusés mal retransmis à la TV (§ 3.4 quater) | P1 |
| M-59 | `tele.receipt` accusés du serveur rapportés à la TV : délai remise → accusé, accusés invalides (MAC) | hist. OUT/SET, nombre | TV | par accusé | 1 | accusé invalide > 0 ⇒ téléphone qui ment (ignoré, alerte) ; délai p95 > 7 j ⇒ TTL de la file TV à relever | P2 |

Rangement : M-01…M-32, M-45…M-50, M-53, M-54, M-59 sont des propriétés des 13 évènements du § 3.3 (côté apps) ; M-33…M-44 sont des clés du fichier de métriques du service (§ 5) ; M-51, M-52, M-56…M-58 sont calculées par l'API ; M-55 est émise par le téléphone.

## 3. Côté apps : le cœur pur `TechMetrics`

### 3.1 Principes

- **Pur, JVM, dans `C/telemetry/tech/`** : aucune dépendance Android ; horloge monotone injectée (`elapsedRealtime` côté app) ; **jamais d'horloge murale** pour une durée (heure de la TV parfois fausse, D-5 de l'amendement).
- **Agrégation sur l'appareil** : un enregistreur par session de jeu, par heure et par élément de file ; **aucun échantillon brut ne sort** de l'appareil, seulement des histogrammes et des compteurs.
- **Mémoire bornée** : tableaux `IntArray` de taille fixe ; au plus 16 enregistreurs ouverts ; ≈ 32 Ko de tas au pire (estimé) ; anneau de 64 évènements techniques en attente au-delà duquel les plus anciens P2 puis P1 partent avant les P0.
- **Désactivé = zéro coût** : sans `pocMetrics` (directive serveur) **ou** sans consentement « statistiques d'usage », `TechMetrics` est un objet nul (aucune allocation, aucune mesure) ; prouvé par test.
- **Mesure passive** : on mesure ce que le jeu fait déjà (POST, flux, `ping`) ; **aucune sonde active automatique** pendant une partie ; la sonde `generate_204` existante (`R/TvNetDiag.kt`) n'est lancée qu'à l'attache de la passerelle et une fois par heure **hors partie** (≈ 0,5 Ko, estimé) ; la mesure de débit `GET /api/gateway/speed` reste un geste du propriétaire (relevé H-PLAY-BT).

### 3.2 Histogrammes à bornes fixes (pas de t-digest)

Bornes partagées (`TechBuckets`, même tableau en Kotlin, Java et Python, vérifié par un test de parité sur `tools/analysis/catalog/tech-metrics.json`) :

| Nom | Bornes supérieures (la dernière case est « au-delà ») | Cases | Seuils de décision posés sur une borne |
|---|---|---|---|
| LAT (ms) | 100, 200, 300, 400, 600, 800, 1 000, 1 500, 2 000, 3 000, 5 000 | 12 | 400 (relais), 1 000 / 2 000 (H-PLAY-BT), 1 500 (signe, délai) |
| LEAD (ms) | −1 000, −500, −250, 0, 250, 500, 750, 1 000, 1 250, 1 500 | 11 | 0 (en retard), 300 ≈ 250 (marge) |
| KBPS | 16, 24, 32, 40, 48, 64, 100, 200, 500, 2 000 | 11 | 40 (plancher) |
| OUT (ms) | 1 000, 2 000, 5 000, 10 000, 15 000, 30 000, 40 000, 60 000 | 9 | 40 000 (`fallbackIdleMs`), 60 000 (perte) |
| SET (ms) | 500, 1 000, 2 000, 3 000, 4 000, 6 000, 8 000, 12 000, 20 000 | 10 | 6 000 / 8 000 (ouverture) |
| UI (ms) | 16, 50, 100, 250, 500, 1 000, 2 000, 5 000 | 9 | 500, 1 000 |
| FRAC (% de fenêtre) | 10, 20, …, 90 | 10 | — |

Pourquoi : **fusion exacte** par simple somme (côté serveur, sur plusieurs appareils et plusieurs heures), validation facile (somme des cases = `n`), taille fixe et minime, et les règles de décision s'expriment en **parts au-delà d'un seuil**, calculées **exactement** puisque chaque seuil est une borne. Les percentiles (p50/p90/p95) sont interpolés dans la case (précision suffisante pour un rapport ; aucune règle ne dépend d'un percentile interpolé sans sa part exacte à côté). Un t-digest serait plus fin mais non exactement fusionnable, plus gros et difficile à valider contre l'empoisonnement.

### 3.3 Évènements neufs (16 au catalogue), compatibles avec la validation actuelle

Un histogramme `H` de `k` cases devient les propriétés entières `H0…H(k−1)` plus `Hn` (total) ; aucun conteneur JSON (la validation actuelle refuse les conteneurs : `TelemetryService.java:143`) ; noms d'évènement ≤ 32 caractères (`telemetry_event.name VARCHAR(32)`), `props` ≤ 2 000 caractères (vérifié par test au maximum de chaque évènement).

| Évènement | Quand | Propriétés (toutes bornées) | dim1 / dim2 | Octets JSON (estimé) |
|---|---|---|---|---|
| `net.conn` | chaque ouverture de partie Internet | `via`, `cell`, `ok`, `fail` (code ≤ 24 valeurs), `ms_ticket`, `ms_tcp`, `ms_tls`, `ms_hello`, `ms_total`, `tries` | via / cell | 260 |
| `net.link` | fin de session de jeu (ou toutes les 30 min d'une longue session) | `via`, `cell`, `mcell` (classe mesurée), RTT `r0…r11,rn`, débit descendant `t0…t10,tn`, montant `u0…u10,un`, `j`, `h`, `cuts`, coupures `o0…o8`, reprises `rt0…rt8`, `rs_same`, `rs_resume`, `rs_full`, `rs_lost`, `ms`, `bytes_up`, `bytes_dn` | via / cell | 1 100 |
| `play.game` | fin de partie Internet | `role` (`host`/`guest`), `mode`, `via`, `cell`, `phones`, `questions`, `end` (`finished`/`lost`/`left`/`abandoned`), `too_early`, `closed`, `same`, `forbidden`, `orange_ms`, `red_ms`, `cause`, `bytes_state`, `bytes_q`, `bytes_other`, `max_state`, `cap_refused` | via / end | 420 |
| `play.timing` | fin de partie Internet | `via`, `cell`, marge `l0…l10`, `late`, révélation `v0…v11`, `ack` `a0…a11`, temps `w0…w9`, relais local `lr0…lr11` | via / cell | 900 |
| `play.fail` | refus hors partie (≤ 20 / jour) | `stage`, `reason` (`PLAY_*` fermé), `via`, `retryable` | stage / reason | 120 |
| `link.gw` | fin de session de passerelle | `cell`, `mcell`, `ms`, `bytes`, `reconnects`, `socks_fail`, `probe_p50`, `probe_p90` | cell | 200 |
| `link.wd` | fin de session Wi-Fi Direct | `ok`, `fail`, `join_ms`, débit `t0…t10,tn`, `drops`, `reroutes`, `ms` | ok / fail | 300 |
| `perf.hour` | chaque heure d'usage | `state` dominant, `abi`, `heap_p95`, `heap_max`, `heap_limit`, `lowmem`, `jank`, `frame_p95`, `rebuf_copy`, `rebuf_nocopy`, `tele_bytes`, `tele_via`, `flips` | state / abi | 300 |
| `perf.quiz` | chargement d'une tranche de banque | `abi`, `slice_ms`, `questions`, `heap_delta` | abi | 90 |
| `perf.thumb` | horaire (si vignettes) | vignettes `th0…th8,thn`, `fails` | — | 160 |
| `sync.xfer` | état final d'un élément de file | `final` (4), `code`, `channel`, `retries`, `resumes`, `slowed_ms`, `floor_hits`, `dedup`, `resumed_bytes`, `ms`, `kbps` | final / code | 230 |
| `sync.pair` | association / renouvellement | `outcome` (5), `tvs` (1, 2, 3+) | outcome | 70 |
| `sync.relay` | (réservé) — compté dans `play.game.cap_refused` ; **pas** créé en V1 | — | — | — |
| `tele.handoff` | (amendement) chaque remise TV → téléphone | `chan` (`lan`/`wd`/`bt_api`/`bt_frames`), `ok`, `fail`, `ms`, `bytes`, `chunks`, `resumes`, `paused_ms` | chan / ok | 120 |
| `tele.receipt` | (amendement) accusé du serveur reçu par la TV | `lag_h` (heures entre scellement et accusé), `bad_mac` | — | 50 |
| `sync.courier` | (amendement, téléphone) quotidien | `tvs`, `bytes`, `batches`, `evicted`, `expired`, `fwd_ok`, `fwd_fail`, attente `wt0…wt8`, `self_bytes`, `self_sends` | — | 220 |

(15 évènements réellement émis + 1 réservé ⇒ **16** au catalogue serveur ; l'amendement d'envoi en a ajouté 3.)

**Budget par TV** (estimé ; compression du § 3.4) : une partie Internet = `net.conn` + `net.link` + `play.game` + `play.timing` ≈ 2,7 Ko JSON, ≈ 1,4 Ko une fois les cases vides omises et les valeurs quantifiées, ≈ **0,25-0,35 Ko** après DEFLATE à dictionnaire (rapport ≈ 0,2, **non mesuré** : w21-01b mesure le rapport réel sur un corpus synthétique et le consigne) ; une heure d'usage (`perf.hour`) ≈ 0,08 Ko ; les évènements d'usage existants d'une journée (≈ 50 × 120 o) ≈ 1 Ko. **Journée typique ≈ 2-3 Ko compressés.** Plafonds : techniques **8 Ko / jour / TV**, toute la télémétrie **24 Ko / jour / TV** (au-delà : P2 puis P1 techniques abandonnés localement, puis les évènements d'usage les plus anciens ; les essentiels jamais).

### 3.4 Politique d'envoi (amendement du propriétaire, 2026-10-04) — remplace la « porte d'envoi » de la première version

**Verbatim** : « L'envoi de statistiques quand le téléphone est connecté à internet sans la passerelle se fait chaque 12 heures après compression rigoureuse des données. Quand il est synchronisé à un phone se fait au phone chaque 4 heures en compressé également quand celui-ci est disponible. C'est le phone qui une fois connecté enverra au serveur. » Puis : « La TV envoie en bluetooth la télémétrie si c'est le seul canal dispo pour la synchro ».

**Lecture et ambiguïté tranchées par le propriétaire (2026-10-04)** : les deux lectures valent. (1) La TV : 12 h en direct, 4 h vers le téléphone. (2) Le téléphone, pour **ses propres** statistiques : « Celles qui pourraient passer de 15 à 12 heures. Oui » ⇒ la constante du téléphone (`TelemetryUploader.FLUSH_EVERY_MS`, côté CastBridge) passe de 15 min à **12 h**, avec compression rigoureuse. (3) « Le téléphone coursier est un téléphone synchronisé » : seul un téléphone **de confiance** de la TV (`TrustRegistry`/`PinBook`) peut être coursier, jamais un autre ; **n'importe lequel** des téléphones synchronisés (8 au plus) peut l'être. D-W21-12 est **fermée**.

| Cas | Règle | Cadence | Chemin |
|---|---|---|---|
| (a) TV reliée à Internet **sans** passerelle (Wi-Fi avec Internet, Ethernet, point d'accès d'un téléphone en Wi-Fi) | la TV envoie elle-même ses lots scellés | **toutes les 12 h** (première fois 12 h après le démarrage de la file ; tirage aléatoire ± 30 min pour étaler les TV) | `POST /api/v1/events/relay` avec **son** jeton d'appareil (« relais par soi-même », même format que (c)) |
| (b) TV **synchronisée** à un téléphone (lien de confiance existant : `TrustRegistry`/`PinBook`), y compris quand son Internet passe par la passerelle Bluetooth | la TV scelle un lot **toutes les 4 h** et le **remet** au premier téléphone de confiance **disponible** (à portée sur un canal local) | lot fermé toutes les 4 h ; remise dès qu'un téléphone se présente | meilleur canal local (§ 3.4 ter) |
| (c) le téléphone **synchronisé** (coursier) | coursier **opaque** : il ne lit ni ne modifie rien, envoie dès qu'il a Internet (hors passerelle active) ; seul un téléphone de confiance de la TV reçoit des lots (le `TeleHub` de la TV refuse tout autre appelant) | à la prochaine connexion, au plus 1 envoi / 15 min | `POST /api/v1/events/relay` avec **son** jeton |
| (d) les statistiques **propres** du téléphone | même compression (`Packer` + dictionnaire), lot non chiffré (ce sont ses données, sous **son** consentement) ; jamais pendant une session de passerelle active ; file existante bornée (`EventQueue`, 2 Mo) ; reprise : un lot ne quitte la file qu'à la réponse du serveur (existant) | **toutes les 12 h** (± 30 min), au lieu de 15 min ; envoyées dans la même requête que les lots coursier quand il y en a | `POST /api/v1/events/relay` (enveloppe `path:"self"`, non scellée mais compressée) — `POST /api/v1/events/batch` reste accepté pour les anciennes versions |
| (a) et (b) possibles à la fois | **(a) gagne** ; un lot est attribué à **un** chemin au scellement ; un lot direct qui échoue 2 fois (24 h) devient remettable au téléphone | — | — |
| aucun des deux | la TV accumule dans sa file bornée (TTL 30 j, éviction § 3.4 bis) | — | — |
| **Jamais** | aucun envoi vers le serveur **par la passerelle Bluetooth** depuis la TV (ni pendant une partie, ni autrement) ; aucune remise ni aucun envoi pendant une partie Internet ni dans les 30 s qui suivent | — | — |

**Deux choses à ne pas confondre** : (1) la télémétrie **ne traverse pas** la passerelle Bluetooth de la TV vers le serveur (le téléphone ne relaie pas d'octets de télémétrie de la TV vers Internet en tant que passerelle) ; (2) elle **peut** voyager **de la TV au téléphone** par Bluetooth (remise locale, RFCOMM), puis le **téléphone** l'envoie avec **son** Internet, plus tard, comme ses propres données. **Le heartbeat** (`POST /api/v1/devices/heartbeat`, mises à jour, directives) **n'est pas** une statistique : il garde son chemin actuel, passerelle comprise (D-W21-13).

**Portée** : toute la télémétrie de la TV (usage, techniques **et** essentiels `error`/`crash`/`update_install`) suit ces cadences (lecture : « statistiques » = tout le contenu de la file ; les essentiels perdent donc leur envoi à 15 min : D-W21-13). Les statistiques propres du téléphone suivent la cadence de 12 h (D-W21-12, fermée) ; son heartbeat garde son chemin.

**Consentement du coursier (réponse du propriétaire, D-W21-1 fermée)** : le téléphone synchronisé transporte les lots de la TV **sous la porte de consentement et de cohorte de la TV** ; aucun consentement séparé du téléphone n'est demandé pour ce transport (les lots sont scellés et opaques pour lui ; ce ne sont pas ses données). Ses **propres** statistiques restent sous **son** consentement. **Limite dite** : le serveur ne connaît pas le lien de confiance local (`TrustRegistry` est sur les appareils) ; la règle « seul un téléphone synchronisé » est tenue **par la TV** (le `TeleHub` ne sert qu'aux téléphones de confiance) ; côté serveur, l'enveloppe authentifiée prouve que le lot vient de la TV, quel que soit le téléphone qui l'apporte.

**Compression rigoureuse** (pure, `C/telemetry/tech/Packer.kt`, w21-01b) :

| Étape | Choix | Pourquoi |
|---|---|---|
| Format | lignes JSON des évènements, **au même schéma** que `/api/v1/events/batch` (le serveur réutilise sa validation) | une seule validation |
| Quantification | cases d'histogramme et compteurs **nuls omis** (le serveur lit l'absence comme 0 ; `Σ` des cases présentes = `n`) ; durées ≥ 1 s arrondies à 10 ms, durées d'ouverture à 50 ms ; tas en Mo ; octets en multiples de 64 ; `ts` arrondi à la seconde (évènements d'usage) et à la minute (techniques) ; UUID d'évènement en 22 caractères base64url (le serveur le remet en forme canonique) | ≈ moitié du JSON brut, aucune perte utile à une règle (tous les seuils restent exacts) |
| Algorithme | **DEFLATE brut niveau 9 avec dictionnaire prédéfini** (`java.util.zip.Deflater.setDictionary`, présent sur JVM et Android, **aucune dépendance**) | zstd/brotli gagneraient ≈ 10-20 % mais demandent une bibliothèque native armeabi-v7a : refusés |
| Dictionnaire | ≈ 2-4 Ko, versionné (`dict=1`), construit à partir du catalogue (noms d'évènements, de propriétés, valeurs fermées, séquences fréquentes) par un script `tools/analysis/build_tele_dict.py` ; même octets dans `core` et `backend` (test de parité) | le gain d'un dictionnaire est maximal sur de petits lots |
| Taille d'un lot | ≤ **32 Ko compressés** et ≤ 500 évènements ; lot 4 h typique ≈ 0,5-1 Ko | tient en < 2 s en Bluetooth (§ 3.4 ter) |

### 3.4 bis Files persistantes bornées (TV et téléphone), reprise, idempotence

| Côté | File | Bornes | TTL | Éviction | Persistance |
|---|---|---|---|---|---|
| TV | file d'évènements existante (`EventQueue`, 2 Mo) **puis** file **scellée** (`SealedOutbox`) : lots fermés et scellés | 128 Ko scellés, 64 lots | **30 jours** (au-delà, les techniques seraient refusés par le serveur : brut 30 j) | d'abord les lots les plus anciens ne contenant que des P2/P1 techniques, puis les plus anciens ; jamais un lot contenant des essentiels tant qu'un autre peut partir | fichier par lot + index JSON atomique (modèle `OrderQueue` / `DeliveryQueue`) |
| Téléphone | file coursier (`CourierQueue`) : enveloppes opaques **par TV** | 16 lots et 64 Ko **par TV**, 8 TV au plus, 512 Ko au total | **14 jours** | dans la TV la plus chargée, le lot le plus ancien ; une 9e TV remplace la TV sans remise depuis le plus longtemps | idem (`OrderQueue`) |

**Reprise** : une remise interrompue reprend à l'offset (morceaux de 4 Ko, § 3.4 ter) ; un envoi au serveur interrompu est rejoué (le serveur dédoublonne). **Idempotence** : chaque lot a un `batchId` (128 bits) ; le serveur tient `tele_batch` (35 jours) et ignore un lot déjà reçu (accusé renvoyé tel quel) ; à l'intérieur, l'UUID de chaque évènement est déjà dédoublonné (`telemetry_event.uq_event_id`). Un lot n'est **retiré de la file de la TV qu'à réception d'un accusé du serveur vérifié** (§ 3.4 quater) ; il est retiré de la file du téléphone dès que le serveur l'a accepté (accusé gardé pour la TV).

### 3.4 ter Chemin TV → téléphone : meilleur canal local, Bluetooth permis quand il est le seul

**Ce qui existe (lu)** : `LinkPlanner.Route` (`C/tv/BtProtocol.kt:436`) choisit déjà, pour un envoi de fichier, `Lan` (réseau commun, HTTP) > `Direct` (Wi-Fi Direct W18, HTTP) > `BluetoothTunnel` (**l'API HTTP de la TV par le tunnel Bluetooth du téléphone**, « les mêmes requêtes qu'en Wi-Fi, plus lentes ») > `Bluetooth` (trames RFCOMM) ; les ordres différés utilisent le canal propriétaire `CBTO` (trames 16-21, reprise par offset, `docs/ORDRES.md` § 9) ; débit Bluetooth **documenté, non mesuré** ici : « ~100-300 ko/s » (`docs/REMOTE-TUNNEL-BT.md:38`), « ~100-250 kB/s » (`BtProtocol.kt:435`). `GET /api/gateway/speed` mesure l'**Internet** de la passerelle, pas le lien local : il ne sert pas ici.

**Protocole retenu (additif, règle de symbiose W19)** : deux routes HTTP de la TV, servies par un hub `TeleHub` (modèle `R/QuizHub.kt`), **joignables par les trois routes HTTP** de `LinkPlanner` (`Lan`, `Direct`, `BluetoothTunnel`), authentifiées par le lien de confiance existant du téléphone (jeton de confiance / PIN, comme l'API de télécommande) :
- `GET /api/tele/outbox?since=<batchId>` ⇒ liste `{batchId, size, sha256}` des lots remettables ; `GET /api/tele/outbox/<batchId>?offset=<n>` ⇒ morceau de ≤ 4 Ko (en-tête `X-Tele-Total`) ;
- `POST /api/tele/receipts` ⇒ accusés du serveur rapportés par le téléphone (§ 3.4 quater).
- **Repli** sans tunnel HTTP (trames brutes seulement) : types `CBTO` additifs **22-26** (`TELE_HELLO`, `TELE_OFFER`, `TELE_NEED`, `TELE_CHUNK`, `TELE_RECEIPT`), calqués sur `ORDER_*` (même mécanique de reprise) ; une version ancienne ignore les types inconnus (testé). Construit **seulement si** l'exécutant w21-04 constate que des TV de la cohorte n'ont pas le tunnel HTTP (à vérifier) ; sinon reporté.

**Choix du canal** : celui que `LinkPlanner` donne pour ce téléphone **à cet instant** (aucune jonction Wi-Fi Direct créée **pour** la télémétrie : on n'utilise le groupe que s'il existe déjà) ; Bluetooth pleinement permis quand c'est le seul canal (cas courant : foyers sans box).

**Contraintes du Bluetooth** (débit faible et partagé avec la copie, la passerelle, la télécommande) :

| Règle | Valeur |
|---|---|
| Priorité | la plus basse : la remise cède à toute requête de copie, de télécommande, de passerelle |
| Suspension | pendant une copie, une lecture vidéo, une partie (locale ou Internet) et 30 s après ; reprise ensuite à l'offset |
| Morceaux | 4 Ko, reprenables par offset, empreinte SHA-256 du lot vérifiée par le téléphone à la fin (lot faux ⇒ jeté et redemandé) |
| Fenêtre | au plus 30 s et 64 Ko par remise ; au plus une remise / 15 min par téléphone |
| Hypothèse de débit | **20 Ko/s utiles** (≈ 10 fois sous le chiffre documenté, pour couvrir le partage et la surcharge du tunnel ; **non mesurée**) |
| Chiffrage | lot 4 h typique 0,5-1 Ko ⇒ **< 0,1 s** ; lot maximal 32 Ko ⇒ **≈ 1,6 s** ; plafond quotidien 24 Ko ⇒ **≈ 1,2 s / jour** ; même à 5 Ko/s, < 7 s pour un lot maximal |

**TV sans aucun canal** (ni téléphone à portée, ni Internet direct) : elle accumule dans sa file scellée (128 Ko ≈ 40-60 jours typiques, TTL 30 j, éviction § 3.4 bis) ; M-53 le dit à sa prochaine remise.

### 3.4 quater Enveloppe scellée TV → serveur, téléphones multiples, accusés

**Exigence** : le téléphone ne doit **ni** apprendre le jeton d'appareil de la TV, **ni** lire, **ni** fausser les mesures. **Fait vérifié** : le serveur garde `device.token_hash = SHA-256(jeton)` (hexadécimal, sans sel : `B/devices/DeviceService.java:111, 156`). La TV connaît le jeton, donc son empreinte ; le téléphone ne connaît ni l'un ni l'autre.

**Mécanisme minimal** (`C/telemetry/tech/Sealer.kt` ↔ `B/telemetry/tech/EnvelopeOpener.java`, w21-01b / w21-02b) :
- clé de lot : `K = HKDF-SHA256(ikm = octets de SHA-256(jeton), sel = batchId, info = "castbridge-tele-v1")` (32 octets) ;
- scellement : **AES-256-GCM** (fourni par la JVM et Android, aucune dépendance), nonce 96 bits aléatoire ; **chiffrement + authentification** en une opération ; données associées (en clair, authentifiées) = en-tête `{v:1, deviceId (identifiant public de l'API), batchId, createdAt, dict, events, path:"direct"|"phone", consent:"usage"|"essential", consentVersion}` ;
- le serveur recalcule `K` à partir de `token_hash` (ou de `prev_token_hash` pendant 14 jours après un réenregistrement : colonne additive V62), ouvre, vérifie, puis passe les évènements à la validation **existante** (catalogue, consentement, cohorte, quotas, cohérence) **au nom de la TV** ; le consentement retenu est le **plus restrictif** de l'en-tête et de la fiche serveur ;
- **limite dite franchement** : une fuite de la base (`token_hash`) permettrait de forger des lots de télémétrie (pas d'usurper l'appareil : l'authentification par jeton reste celle du jeton lui-même) ; impact faible, et une fuite de la base est déjà grave. Durcissement possible en phase 2 : clé de télémétrie propre tirée à l'enregistrement.
- **TV jamais enregistrée** (aucun jeton) : elle ne peut pas sceller ; elle ne peut pas non plus être dans la cohorte (qui se choisit par appareil enregistré) : **hors POC** ; phase 2 : enregistrement relayé avec réponse scellée.

**Route** `POST /api/v1/events/relay` (w21-02b) : corps = 1 à 16 enveloppes (base64url), ≤ 256 Ko ; appelant = **téléphone** authentifié par son jeton (`app=phone`) **ou** la TV elle-même (chemin direct) ; une enveloppe `path:"self"` (non scellée, compressée) porte les statistiques **propres** de l'appelant (téléphone à 12 h) et suit la validation existante de cet appelant ; plafonds : par téléphone 200 enveloppes et 1 Mo / jour, par TV 16 enveloppes / jour (6 attendues + reprises), enveloppe ≤ 32 Ko ; rejeu : `batchId` déjà vu ⇒ `duplicate` (accusé renvoyé) ; enveloppe dont le `ts` le plus ancien a > 30 jours ⇒ techniques refusés `too_old` (la règle existante « jour du serveur si l'horloge s'écarte de plus de 30 j » ne doit **pas** redater des évènements relayés en retard) ; aucun appairage téléphone↔TV exigé (le MAC prouve l'origine ; le téléphone relais est noté dans `tele_batch.via_device_id` pour les quotas et M-58). Réponse par enveloppe : `{batchId, status: accepted|duplicate|rejected, reason?, receipt}`.

**Accusé du serveur vérifiable par la TV** : `receipt = HMAC-SHA256(K, "receipt|" + batchId + "|" + status + "|" + pocMetrics)` ; le téléphone le rapporte à la TV (`POST /api/tele/receipts` ou `TELE_RECEIPT`) ; la TV **ne purge** un lot que sur un accusé valide (un téléphone menteur ne peut pas faire perdre de données ; accusé invalide compté : M-59) ; l'accusé porte aussi le bit `pocMetrics` (directive) pour une TV qui ne parle au serveur que par son téléphone.

**Téléphones multiples (jusqu'à 8 par TV)** : un seul coursier suffit. La TV marque un lot « remis à X » mais le garde ; tant qu'aucun accusé n'est revenu, elle **ne le réoffre pas** pendant 24 h, puis à n'importe quel téléphone ; si deux téléphones l'envoient, le serveur dédoublonne (`batchId`, puis UUID des évènements) : **le dédoublonnage serveur suffit** à la justesse ; la règle des 24 h ne sert qu'à limiter les doublons (M-58). Un accusé rapporté par **n'importe quel** téléphone purge le lot.

### 3.5 Cohorte POC et consentement côté app

`TechMetrics.enabled = consent == USAGE && directives.pocMetrics == true && !childProfileActive`. `pocMetrics` arrive par la réponse du heartbeat (`DeviceService.Directives`, champ additif, absent = faux) **ou** par l'accusé scellé d'un lot (§ 3.4 quater) pour une TV qui ne parle au serveur que par son téléphone. Retrait du consentement ⇒ la file technique est vidée (même mécanique que `EventQueue.removeIf`, `C/telemetry/Telemetry.kt:126`). Profil enfant actif ⇒ aucune mesure (prudence : le jeu Internet y est fermé de toute façon).

### 3.6 Classe cellulaire de la passerelle

La TV ne connaît que « passerelle Bluetooth ». Le **téléphone passerelle** (`S/BtGatewayService.kt`) déclare une classe grossière **sans permission nouvelle** : `ConnectivityManager.getNetworkCapabilities(active)` ⇒ `TRANSPORT_CELLULAR` + `getLinkDownstreamBandwidthKbps()` ⇒ bandes `2g` (< 150 kbps), `3g` (< 2 Mbps), `4g` (< 50 Mbps), `5g` (≥ 50 Mbps), sinon `unk` ; **jamais** `READ_PHONE_STATE`, jamais l'opérateur, jamais l'identifiant de cellule. Champ additif dans la trame d'attache de la passerelle (à localiser par l'exécutant : `S/BtGatewayService.kt` ↔ `R/BtGatewayHost.kt`). La TV calcule aussi une **classe mesurée** `mcell` à partir de M-01/M-02 (mêmes bandes appliquées au débit passif médian) ; les rapports utilisent la classe mesurée en cas de désaccord (M-32). Les bandes de `getLinkDownstreamBandwidthKbps` sont des **estimations du système** (non vérifiées sur les téléphones du propriétaire).

## 4. Confidentialité et sûreté par conception

### 4.1 Ce que dit `docs/TELEMETRY.md` aujourd'hui (lu)

Deux niveaux : **essentiel** (toujours : identification de l'appareil, modèle, versions, stockage, erreurs, plantages, mises à jour, heartbeats) et **statistiques d'usage** (tout le reste) **sur consentement, désactivé** tant que l'utilisateur n'a pas accepté sur l'écran d'information (`ConsentText.VERSION = "2026-11"`, `C/connect/ConsentText.kt:8`). Le serveur **refuse** tout évènement non essentiel d'un appareil sans consentement (`TelemetryService.java:120`). Le texte affiché de la partie « usage » cite : fonctionnalités, temps passé, envois, lectures (durées, formats, réussite), quiz, échecs, Apprendre, téléchargements, usage de la passerelle Bluetooth. Il **ne cite pas** explicitement « la qualité de la liaison du jeu en ligne » ni « les performances de l'appareil » (mémoire, images).

### 4.2 Posture retenue (rien de juridiquement nouveau)

| Règle | Application |
|---|---|
| Niveau | toutes les familles `net.*`, `play.*`, `link.*`, `perf.*`, `sync.*` sont **statistiques d'usage** (jamais « essentiel ») ; refusées sans consentement, côté app **et** côté serveur |
| Double porte | consentement **et** cohorte POC (table serveur, tenue par le propriétaire) ; hors cohorte : refus à l'ingestion (motif « hors cohorte POC ») |
| Cohorte limitée | jusqu'à la relecture juridique (reportée au 2026-12-31), la cohorte ne contient que des TV **du propriétaire** ou de testeurs qui ont **accepté expressément** la mesure (accord hors application, tenu par le propriétaire) ; plafond 200 appareils |
| Texte d'information | **inchangé** pendant le POC (pas de nouvelle version du texte avant la relecture) ; proposition de phrase additive préparée par w21-09 pour la relecture : « …et, si votre appareil participe à l'essai du jeu en ligne, des mesures techniques de la connexion et des performances (délais, débits, mémoire), sans aucun contenu » |
| Minimisation | aucune propriété textuelle libre ; codes fermés ; nombres bornés ; pas d'horodatage plus fin que le `ts` existant ; tranche horaire de 3 h pour M-05 |
| Jamais | nom, pseudonyme (même haché), adresse IP, ASN, opérateur, identifiant de cellule, SSID, adresse MAC, nom de fichier, titre, texte ou identifiant de question en ligne, code de salle, jeton, ticket, activation ; les clés interdites existantes (`EventCatalog.FORBIDDEN`) s'appliquent |
| Identifiant | aucun identifiant neuf : l'appareil est celui du jeton (`device_id`) ; les **exports** et scripts d'analyse ne voient qu'un pseudonyme `pid = HMAC-SHA256(sel serveur, device_id ‖ mois)` tronqué à 12 hexadécimaux, **renouvelé chaque mois** (aucun suivi d'un appareil d'un mois à l'autre hors base) |
| Pays | au plus `device.country` (existant, tiré de l'IP à l'enregistrement) ; jamais de ville |
| k-anonymat | toute case de tableau de bord ou d'export avec **< 5 appareils distincts** est masquée (« < 5 appareils ») ; exception : appareils marqués `owner_test` dans la cohorte (TV du propriétaire), visibles un par un dans une vue séparée « Mes TV de test » (D-W21-4) |
| Conservation | évènements bruts techniques **30 jours** ; agrégats horaires 30 jours ; agrégats journaliers **12 mois**, ne gardant que les cases ≥ 5 appareils (les autres fusionnées en `*`) ; métriques du service : horaires 90 jours, journalières 12 mois (aucune donnée d'appareil) |
| Effacement | `DELETE /api/v1/devices/me` et l'effacement admin suppriment déjà en cascade `telemetry_event` (et `tele_batch`) ; un lot encore dans la file d'un téléphone après l'effacement est refusé à l'arrivée (appareil inconnu ⇒ enveloppe non ouvrable) ; l'app vide aussi sa file scellée ; la cohorte et le quota sont en cascade (V62) ; les agrégats horaires sont reconstruits la nuit depuis le brut (l'appareil effacé en disparaît) ; les agrégats journaliers ne contiennent que des cases ≥ 5 appareils |
| Accès | `/admin/**` (`ROLE_WEBADMIN`, TOTP) et `/api/v1/admin/**` (`ROLE_ADMIN`) existants (`B/config/SecurityConfig.java:68, 92`) ; chaque export CSV ou rapport tiré par l'API est journalisé (`tech_export_audit`) |
| Aucun secret | le cœur refuse d'enregistrer une propriété dont le nom est interdit ; les messages d'erreur ne sont jamais copiés (codes seulement) ; le service de jeu passe ses métriques par les règles de `LogRedactor`/`PlayRedact` (aucun champ `ip`, `code`, `name`, `device` n'est **jamais** une dimension) |

### 4.3 Ce que la relecture juridique reportée ne couvre pas encore (honnête)

(1) La qualification « statistiques d'usage » des mesures de qualité de liaison et de performance, et le fait que le texte affiché ne les cite pas : couvert pendant le POC **seulement** par la restriction de la cohorte aux TV du propriétaire et de testeurs consentants ; (2) le transfert hors du Cameroun (VPS à l'étranger, déjà signalé par `TELEMETRY.md` § 7) s'applique aussi à ces mesures ; (3) le pseudonyme mensuel `pid` reste une donnée pseudonyme, pas anonyme ; (4) l'exception `owner_test` suppose que ces TV appartiennent réellement au propriétaire ; (5) aucune formalité auprès de l'autorité n'a été faite. **Rien de tout cela n'est nouveau** par rapport à la télémétrie existante : le POC n'ouvre aucune finalité au public avant la relecture.

## 5. Côté service de jeu : `castbridge-play` reste isolé

### 5.1 Ce qui est mesuré (M-33…M-44)

Un objet pur `PlayMetrics` (dans `SP/metrics/`) reçoit des appels de comptage depuis `RoomRegistry`, `PlayFallbackController`, `PlayWebSocketHandler` et un observateur additif de `ServerRoom` (`RoomObserver` : résultat d'`ack`, échantillon de RTT, pénalités de compensation et de relais, grâce, `CLOSED` tardif). Dimensions **fermées** seulement : transport (`ws`/`sse`/`longpoll`), classe déclarée par la TV (`hello.link`, champ **additif** du protocole, valeur ramenée à la liste fermée, inconnue ⇒ `unk`), code de refus `PLAY_*`, résultat d'`ack`, type de message, nombre de joueurs (1-8), code de fermeture. **Jamais** : `roomId`, code de salle, adresse, identité de TV, pseudonyme, question.

### 5.2 Comment les métriques sortent : recommandation « fichier + collecteur hôte qui pousse »

| Option | Pour | Contre | Verdict |
|---|---|---|---|
| (a) `GET /play/metrics` lu par un collecteur | simple | le bloc nginx `location ^~ /play/` (PLAY-OPS § 4.4) l'exposerait au public sauf ajout d'un `deny` (acte manuel du propriétaire, fragile) ; tout conteneur d'`infra-net` pourrait le lire ; nouvelle route sur un service audité | non |
| (b) l'API tire `http://castbridge-play:8080/...` par `infra-net` | aucun cron | mêmes défauts que (a) + dépendance API → jeu | non |
| (c) le service pousse vers l'API avec une clé | temps réel | **donne un identifiant au service de jeu** (contraire à son isolation : il ne détient aucun secret, R4 W20) | non |
| **(d) fichier horaire dans le volume `play-state` + collecteur côté hôte qui pousse à l'API locale avec une clé dédiée** | aucune route neuve, aucun secret dans le service, volume déjà inscriptible (`/var/lib/castbridge-play`, PLAY-OPS § 5) | un cron de plus sur l'hôte | **oui** |

Détails de (d) :

- **Écriture** : à chaque heure close, `MetricsFile` écrit `/var/lib/castbridge-play/metrics/m-<AAAAMMJJHH>-<instance>.json` (≤ 64 Ko, écriture atomique par renommage) ; anneau de 72 fichiers (3 jours, ≤ 4,6 Mo) ; `CASTBRIDGE_PLAY_METRICS=on|off` (défaut `on`), dossier `CASTBRIDGE_PLAY_METRICS_DIR` ; un échec d'écriture est compté et journalisé (sans détail), jamais fatal.
- **Collecte** : `tools/analysis/collect_play_metrics.sh` (cron hôte toutes les 15 min, utilisateur `ubuntu`, `sudo docker cp castbridge-play:/var/lib/castbridge-play/metrics/. <tmp>`), puis pour chaque fichier non encore poussé : `curl -fsS --max-time 10 -H "Authorization: Bearer $(cat ~/castbridge/secrets/play-metrics-ingest.key)" --data-binary @f http://127.0.0.1:7090/api/v1/ingest/play-metrics` ; mémoire des fichiers poussés dans `~/castbridge/state/play-metrics.pushed` ; le collecteur **n'écrit jamais** dans le conteneur.
- **Clé** : 256 bits aléatoires dans `~/castbridge/secrets/play-metrics-ingest.key` (0600), **hors** de `~/castbridge/services/play/` (dont `.env.play` est monté dans le conteneur de jeu) ; l'API ne connaît que son empreinte `CASTBRIDGE_PLAY_METRICS_KEY_SHA256` (variable de l'override de l'API) ; absente ⇒ la route répond 404.
- **Route API** `POST /api/v1/ingest/play-metrics` : refusée (404) si la requête porte `X-Forwarded-For` (nginx en pose toujours un : seule une connexion locale directe passe), comparaison à temps constant de l'empreinte, corps ≤ 64 Ko, schéma strict (métriques et dimensions de la liste fermée, nombres bornés, cohérence des histogrammes), idempotente sur `(heure, instance)` (remplacement), réponse sans écho du corps.

## 6. Stockage : migration V62 dans `castbridge-db`

**Numéro de migration** : V62 n'est PLUS réservé à W21 : le grand livre W22 (w22-02) a pris V62 (renumérotée depuis V63, audit H4). w21-02 prend « plus haut numéro existant + 1 » AU MOMENT DE SA FUSION (V63 si le grand livre est déjà fusionné), nomme son fichier `V<n>__tech_metrics.sql` et le dit au rapport ; les « V62 » ci-dessous sont des indications de rédaction. Numéro vérifié à la rédaction : aucune migration ≥ V62 sur aucune branche (`git log --all -- backend/src/main/resources/db/migration`), règle « plus haut + 1 » (`backend/src/main/resources/db/migration/README.md`). Une seule migration, additive, livrée par **server-1.1.2** (le `pom.xml` est en 1.1.1 ; étiquettes `server-1.1.0`, `server-1.1.1` existantes).

```sql
-- V62__tech_metrics.sql (proposition ; l'exécutant w21-02 l'écrit et la teste)
CREATE TABLE poc_cohort (
  device_id  BIGINT NOT NULL PRIMARY KEY,
  owner_test BOOLEAN NOT NULL DEFAULT FALSE,     -- TV du propriétaire : vue par appareil permise
  added_at   DATETIME(6) NOT NULL,
  added_by   VARCHAR(64) NOT NULL,               -- identifiant de l'admin, jamais un texte libre
  CONSTRAINT fk_poc_cohort_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE);

CREATE TABLE tech_quota (                         -- quota d'ingestion par appareil et par jour
  device_id BIGINT NOT NULL, stat_day DATE NOT NULL, events INT NOT NULL DEFAULT 0, bytes INT NOT NULL DEFAULT 0,
  rejected INT NOT NULL DEFAULT 0,
  CONSTRAINT pk_tech_quota PRIMARY KEY (device_id, stat_day),
  CONSTRAINT fk_tech_quota_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE);

CREATE TABLE tech_rollup_hour (                   -- reconstruit depuis telemetry_event (aucun device_id)
  stat_hour DATETIME NOT NULL, app VARCHAR(16) NOT NULL, metric VARCHAR(24) NOT NULL,
  via VARCHAR(12) NOT NULL DEFAULT '', cell VARCHAR(4) NOT NULL DEFAULT '', dimx VARCHAR(32) NOT NULL DEFAULT '',
  version_code INT NOT NULL DEFAULT 0,
  devices INT NOT NULL, samples BIGINT NOT NULL, sum_value DOUBLE NOT NULL DEFAULT 0,
  buckets VARCHAR(160) NOT NULL DEFAULT '',       -- comptes des cases, CSV écrit par le serveur
  dev_median DOUBLE NULL,                         -- médiane des médianes par appareil (robuste)
  CONSTRAINT pk_tech_rollup_hour PRIMARY KEY (stat_hour, app, metric, via, cell, dimx, version_code));

CREATE TABLE tech_rollup_day (LIKE tech_rollup_hour); -- idem par jour, cases < 5 appareils fusionnées en '*' (forme exacte : à écrire sans LIKE si Flyway/MySQL le refusent)

CREATE TABLE play_metric_hour (                   -- service de jeu : aucune donnée d'appareil
  stat_hour DATETIME NOT NULL, instance VARCHAR(16) NOT NULL, metric VARCHAR(24) NOT NULL,
  dim1 VARCHAR(16) NOT NULL DEFAULT '', dim2 VARCHAR(16) NOT NULL DEFAULT '',
  cnt BIGINT NOT NULL DEFAULT 0, sum_value DOUBLE NOT NULL DEFAULT 0, max_value DOUBLE NOT NULL DEFAULT 0,
  buckets VARCHAR(160) NOT NULL DEFAULT '', received_at DATETIME(6) NOT NULL,
  CONSTRAINT pk_play_metric_hour PRIMARY KEY (stat_hour, instance, metric, dim1, dim2));
CREATE TABLE play_metric_day (…mêmes colonnes, stat_day DATE…);

-- amendement d'envoi : registre des lots scellés (anti-rejeu, quotas, fraîcheur, doublons)
CREATE TABLE tele_batch (
  batch_id      CHAR(22)    NOT NULL PRIMARY KEY,      -- 128 bits base64url
  device_id     BIGINT      NOT NULL,                  -- la TV (attribuée après ouverture de l'enveloppe)
  via_device_id BIGINT      NULL,                      -- le téléphone coursier (NULL = direct)
  path          VARCHAR(8)  NOT NULL,                  -- direct | phone
  status        VARCHAR(12) NOT NULL,                  -- accepted | rejected
  events        INT         NOT NULL, bytes INT NOT NULL,
  oldest_ts     DATETIME(6) NULL, newest_ts DATETIME(6) NULL, received_at DATETIME(6) NOT NULL,
  copies        INT         NOT NULL DEFAULT 1,        -- reçus en double (M-58)
  CONSTRAINT fk_tele_batch_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE);
CREATE INDEX ix_tele_batch_via ON tele_batch (via_device_id, received_at);
CREATE INDEX ix_tele_batch_day ON tele_batch (received_at);
ALTER TABLE device ADD COLUMN prev_token_hash CHAR(64) NULL, ADD COLUMN prev_token_until DATETIME(6) NULL; -- 14 jours après un réenregistrement

CREATE TABLE tech_export_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, at DATETIME(6) NOT NULL, admin VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL, params VARCHAR(256) NOT NULL, row_count INT NOT NULL);
```

**Évènements bruts** : dans `telemetry_event` (aucune table brute neuve) ; `EventCatalog.Def.dayCounter = false` pour les familles techniques (elles ne gonflent pas `kpi_event_day`) ; purge nocturne **par famille** à 30 jours (`delete … where name like 'net.%' … and stat_day < ? limit 10 000`, index `ix_event_name_day` existant) ; les autres évènements gardent leurs 13 mois.

**Plafonds et budget** (estimé) :

| Quoi | Plafond | Taille |
|---|---|---|
| techniques par appareil et par jour | 400 évènements **et** 100 Ko de `props` ; au-delà : refus « quota technique du jour » | — |
| lignes brutes techniques (30 j) | 200 000 ; au-delà : refus « capacité », alerte M-52 | ≈ 200 000 × ≈ 1 Ko (ligne + index) ≈ **200 Mo disque au pire** ; régime attendu (50 TV × 30 évènements/jour × 30 j = 45 000) ≈ 45 Mo |
| `tech_rollup_hour` (30 j) | ≈ 13 métriques × 36 classes × 720 h, en pratique ≪ 50 000 lignes | < 10 Mo |
| `tech_rollup_day` (12 mois) | cases ≥ 5 appareils | < 5 Mo |
| `play_metric_hour` (90 j) | ≈ 60 clés × 2 160 h ≈ 130 000 lignes | ≈ 20 Mo |

Effet sur la mémoire de MySQL (512 Mo) : les requêtes lisent par `(name, stat_day)` et par clé primaire des agrégats ; le tampon InnoDB n'a pas à contenir les tables (disque libre ≈ 80 Go, PLAY-OPS § 1). La taille réelle du tampon InnoDB de `castbridge-db` **n'a pas été lue**.

**Tâches planifiées** (dans l'API, même modèle que `TelemetryService.nightly`) : toutes les heures à :07, reconstruction idempotente de `tech_rollup_hour` pour **les heures touchées par les évènements reçus depuis le dernier passage** (les lots arrivent jusqu'à 12 h, et via un téléphone parfois des jours, après les faits ; fenêtre bornée à 14 jours) ; `tele_batch` purgé à 35 jours ; 03:50 (Douala) : `tech_rollup_day` des 14 derniers jours touchés (k ≥ 5 appliqué), purges (brut 30 j, horaires 30 j / 90 j, journaliers 12 mois), `play_metric_day`.

## 7. Analyse

### 7.1 Vues SQL (w21-02)

`v_tech_hist_day` (fusion des cases par jour × métrique × classe, avec `devices`), `v_play_game_funnel` (ouvertures → `welcome` → parties finies / perdues / quittées par classe), `v_play_srv_day` (métriques du service par jour). Toutes appliquent le plancher k = 5 sur les vues destinées aux pages.

### 7.2 Scripts `tools/analysis/` (Python 3, stdlib + `pytest` ; lecture par l'API admin JSON, **jamais** de connexion directe à la base de production)

| Script | Fait |
|---|---|
| `tech_fetch.py` | tire `GET /api/v1/admin/tech/rollups?from&to&metric` (jeton admin lu dans un fichier, jamais en argument) vers un CSV local |
| `tech_percentiles.py` | percentiles interpolés et **parts exactes au-delà des seuils** par classe, avec intervalle de confiance de Wilson pour chaque part |
| `tech_timeseries.py` | séries horaires/journalières par classe, détection de rupture (médiane glissante ± 3 MAD) |
| `tech_funnel.py` | entonnoir d'une partie Internet par classe |
| `tech_anomalies.py` | appareils aberrants (médiane par appareil à > 3 MAD de la cohorte), appareils à refus de cohérence > 20 %, classes sous k |
| `tech_recommend.py` | applique les **règles de décision** du catalogue (fichier `tools/analysis/catalog/tech-metrics.json`) ; sortie `recommandations-<semaine>.md` + `.csv` : paramètre, valeur actuelle, valeur proposée (dans sa borne), règle, preuves (n appareils, n échantillons, part ± IC), **confiance** (`haute` : ≥ 10 appareils et IC à 95 % entièrement d'un côté du seuil ; `moyenne` : ≥ 5 appareils ; `insuffisante` sinon : aucune proposition) |
| `tech_weekly.py` | rapport hebdomadaire (Markdown) : tableau des 27 P0, recommandations, anomalies, budget de télémétrie (M-50), santé de l'ingestion (M-51/M-52) |

### 7.3 Statistiques robustes contre l'empoisonnement et la domination

Pour toute règle : (1) **médiane des médianes par appareil** à côté de la valeur poolée ; (2) **poids plafonné** : un appareil ne compte pas pour plus de 20 % des échantillons d'une case (au-delà, ses cases sont réduites proportionnellement) ; (3) **moyenne tronquée à 10 %** pour les moyennes ; (4) exclusion des appareils signalés par `tech_anomalies.py` ; (5) une recommandation exige ≥ 5 appareils distincts **et** que la valeur poolée et la médiane des médianes tombent du même côté du seuil ; sinon « insuffisante ».

### 7.3 bis Fraîcheur des données (effet de l'amendement d'envoi)

Retard possible entre une mesure et son arrivée : **statistiques propres du téléphone** jusqu'à 12 h (+ fin d'une session de passerelle) ; **TV en direct** jusqu'à 12 h (+ attente de la fin d'une partie) ; **par téléphone** jusqu'à 4 h (fermeture du lot) + attente d'un téléphone à portée + attente de son retour en ligne (heures, parfois jours ; TTL 30 j côté TV, 14 j côté téléphone). Conséquences : (1) l'heure et le jour d'une mesure restent ceux de l'appareil (`ts`), jamais ceux de l'arrivée ; (2) agrégats **reconstruits sur 14 jours glissants** (§ 6) ; (3) le **rapport hebdomadaire couvre la semaine close à J−3** (lundi pour la semaine finie le jeudi précédent… règle : semaine ISO terminée depuis ≥ 3 jours) et donne la **complétude estimée** (part des TV de la cohorte ayant livré un lot couvrant la période, M-57) ; une recommandation n'est émise que si la complétude ≥ 80 % ; (4) les tableaux de bord affichent « données jusqu'au … (complètes à x %) » ; (5) la boucle de réglage (§ 8.3) prend **une semaine de plus** pour la mesure de contrôle ; (6) les mesures du **service de jeu** (fichier horaire, collecteur 15 min) restent **quasi temps réel** : ce sont elles qui donnent les premières preuves.

### 7.4 Page « POC en ligne » dans `/admin/kpi`

Nouvelle section `poc-en-ligne` (même modèle `Kpi.Section` / `Tile` / `Chart` / `Table` que les dix sections existantes, `B/telemetry/Kpi.java`, `B/admin/AdminKpiPages.java`), filtres existants + classe de liaison : tuiles (TV de la cohorte actives, parties Internet, part d'annonces en retard, part de révélations > 2 s, part de `ack` > 1,5 s, part de rafales < 40 kbps), graphiques (RTT par classe, marge des annonces, durée des coupures, ouverture décomposée), tableaux (refus `PLAY_*`, plafonds atteints, recommandations de la semaine), sous-page « Mes TV de test » (appareils `owner_test` seulement) ; **toute case < 5 appareils masquée** ; export CSV journalisé ; même contenu en JSON par `GET /api/v1/admin/kpi/poc-en-ligne`. Gestion de la cohorte : `GET/POST/DELETE /api/v1/admin/tech/cohort` (jeton admin) et un tableau simple dans la même page (CSRF existant).

## 8. Boucle de réglage : de la preuve à la valeur livrée

### 8.1 Où vivent les constantes

| Constante | Où elle s'applique | Comment on la règle | Bornes |
|---|---|---|---|
| délai entre questions (`PlayTiming.INTER_QUESTION_GAP_MS`), mode fixe/adaptatif | **service** (l'annonce porte `opensAtServerMs`) | variable `CASTBRIDGE_PLAY_GAP_MS` + `CASTBRIDGE_PLAY_GAP_MODE=fixed|adaptive` (w21-08), redéploiement `--service play` | 1 000-2 000 ms (propriétaire) ; adaptatif dans la même plage |
| grâce de clôture (`MAX_GRACE_MS`) | service | `CASTBRIDGE_PLAY_GRACE_MS` (w21-08) | 500-1 500 ms ; > 1 000 ⇒ décision |
| ping de salle (`PING_EVERY_MS`), `fallbackIdleMs`, `pongTimeoutMs`, `outboxMaxBytes` | service | variables (w21-08 pour le ping de salle ; les autres : à vérifier si déjà lues de l'environnement) | ping 2-10 s ; idle 30-90 s |
| plafonds (`maxPerIp`, `maxPerIpShared`, `createsPerIpPerHour`, `createsPerIdentityPerDay`, `maxRoomsPerSubject`) | service | variables **existantes** de `PlayConfig` | celles de `PlayConfig` |
| `MAX_COMPENSATION_RTT_MS`, `MAX_RELAY_RTT_MS`, essai 3/jour, 8 relayés, tickets 20/h | sécurité / commerce | **jamais à distance** ; changement = décision + livraison | — |
| courbe de reconnexion, `LOST_AFTER_SEC`, cadences 12 h / 4 h et bornes des files (`SendPolicy`, `SealedOutbox`, `CourierQueue`), bandes `BufferGovernor`/`PlaybackPriority`, délais Wi-Fi Direct, `stickyMs` | **TV** (et téléphone) | constantes livrées (APK verrouillés `-PrequireActivation=true`, copiés dans `Download` de la clé USB) | revue de code |
| `SLOW_RTT_MS` (signe) | service (calcul du signe) | constante + décision (sémantique de l'orange) | 1 000-2 500 |

### 8.2 Configuration distante signée : pas pour le POC

**Évaluation** : (1) les paramètres qui comptent pour le jeu sont **dans le service** : une variable bornée + un redéploiement `--service play` (rollback `deploy-server.sh --rollback --service play`) suffit, sans toucher aux TV ; (2) la cohorte POC est petite et ses TV reçoivent des APK du propriétaire ; (3) une configuration distante ajouterait une surface de contrôle à distance (signature, rejeu, bornes, retour arrière) à auditer, pour un gain faible ; (4) il existe déjà un canal signé, étroit et audité : les **ordres différés** (`docs/ORDRES.md`, enveloppe `cbx1`, liste fermée, séquence par clé, cible `device`/`group:`, garde-fous « jamais de sécurité »). **Recommandation** : pas de configuration distante en W21. **Phase 2, seulement si** une constante de la TV doit varier par cohorte : une action `tune.set` ajoutée à la liste fermée des ordres (`PolicyActions` / `PolicyCatalog` / `tools/orders/actions.json`), paramètres d'une liste close avec bornes compilées dans la TV (valeur hors borne ⇒ `BAD_PARAMS`), aucune clé de sécurité dans la liste (la liste `NEVER` reste refusée), déploiement par `group:` (pourcentage = taille du groupe), retour arrière = ordre contraire (valeur par défaut), défaut sûr sur la TV sans ordre. **Aucun** `GET /api/v1/config`.

### 8.3 Le chemin d'une valeur

`tech_recommend.py` (confiance « haute ») ⇒ ligne dans le rapport hebdomadaire ⇒ décision du propriétaire (oui/non, consignée dans `docs/HANDOFF.md`) ⇒ service : modification de `.env.play` + `deploy-server.sh --service play --apply` (santé, retour arrière automatique) ; TV : cahier d'une ligne + APK verrouillé ⇒ **mesure de contrôle** une semaine après (même règle, même cohorte) ⇒ si la règle se dégrade : retour à la valeur précédente.

## 9. Sécurité de la surface neuve

| Menace | Parade |
|---|---|
| Ingestion abusive par un appareil | jeton d'appareil (existant), cohorte, quota par appareil (400 évènements, 100 Ko / jour), filtre de débit existant (`B/config/RateLimitFilter.java`), lot ≤ 500 / 2 Mo (existant) |
| Métriques empoisonnées qui faussent le réglage | contrôles de cohérence à l'ingestion (somme des cases = `n`, `n` ≤ 10 000 par évènement, parts et maxima plausibles, durée de session ≥ somme des coupures) ; statistiques robustes (§ 7.3) ; exclusion des aberrants ; ≥ 5 appareils pour toute proposition |
| Rejeu | UUID d'évènement dédupliqué (existant) ; métriques du service idempotentes sur `(heure, instance)` |
| Saturation de la base (512 Mo) | plafonds de lignes, purges nocturnes par lots de 10 000, agrégats reconstruits (pas de croissance cachée), alerte M-52 |
| Clé d'ingestion du service volée | inutile depuis Internet (refus si `X-Forwarded-For`, donc tout passage par nginx) ; ne permet que d'écrire des agrégats bornés dans `play_metric_*` ; rotation = nouvelle empreinte dans l'override de l'API |
| Le service de jeu obtient un secret | aucun : ni clé, ni accès base, ni route neuve ; test `NoSecretsTest` étendu (aucun nom `*METRICS_KEY*` dans `PlayConfig.ENV_NAMES`) |
| Tableau de bord trop bavard | rôles existants ; k = 5 ; exports journalisés ; aucune vue par appareil hors `owner_test` (la fiche appareil existante montre déjà les 200 derniers évènements de tout appareil, techniques compris : sans donnée personnelle, accepté) |
| Journaux | l'ingestion ne journalise que des comptes et des motifs, jamais les `props` ni le corps ; le service passe par `LogRedactor` |
| Gêne du jeu par la mesure | aucune télémétrie par la passerelle ; aucune remise ni envoi pendant une partie et 30 s après (§ 3.4) ; remise locale de plus basse priorité, suspendue pendant copie/lecture/partie ; objet nul hors cohorte ; test « 0 octet pendant une partie » (w21-04) |
| Téléphone coursier curieux ou malveillant | enveloppe AES-256-GCM (il ne lit rien), clé dérivée de l'empreinte du jeton qu'il ne connaît pas (il ne fausse rien : toute altération ⇒ refus `mac`, M-56) ; il peut seulement **retenir** ou **rejouer** : rejeu ⇒ `duplicate` ; rétention ⇒ TTL + réoffre à un autre téléphone après 24 h ; faux accusé ⇒ refusé par la TV (HMAC) |
| Abus de la route de relais | jeton du téléphone, plafonds par téléphone (200 enveloppes, 1 Mo / jour) et par TV (16 / jour), enveloppe ≤ 32 Ko, corps ≤ 256 Ko, `batchId` unique, évènements trop anciens refusés, filtre de débit existant |
| Bombe de décompression | décompression bornée à 2 Mo par enveloppe (même borne que `TelemetryController.MAX_BODY`), dictionnaire fixe connu du serveur |

## 10. Ce qu'il faut construire, dimensionné et ordonné

Index : `docs/agent-briefs/SONNET-WAVE21-INDEX.md`. Ordre pensé pour **des preuves tôt** : les métriques **du service** ne dépendent ni du consentement des TV ni d'un APK : dès w21-02 + w21-03 + w21-03b déployés (server-1.1.2 + nouvelle image `castbridge-play`), la première partie du POC produit des preuves.

| Ordre | Cahier | Livre | Modèle | Audit Opus | Dépend |
|---|---|---|---|---|---|
| 1 | **w21-01** catalogue (JSON de parité) + cœur pur `TechMetrics`, histogrammes, enregistreurs, quota, `SendPolicy` (cadences 12 h / 4 h, jamais pendant une partie) | le cœur prouvé sur JVM | sonnet M | échantillon | — |
| 1 | **w21-01b** cœur pur : compression à dictionnaire (`Packer`), enveloppe scellée AES-GCM (`Sealer`), files bornées TV (`SealedOutbox`) et téléphone (`CourierQueue`), accusés HMAC, codec de remise par morceaux | l'enveloppe et les files prouvées sur JVM | sonnet M | **obligatoire** (cryptographie, files) | w21-01 (catalogue) |
| 1 | **w21-02** API : familles au catalogue, cohorte, quotas, cohérence, V62, agrégats, purges, directive `pocMetrics`, vues | ingestion sûre | sonnet M | **obligatoire** | (parité avec w21-01 à la fusion) |
| 1 | **w21-03** service de jeu : `PlayMetrics`, `RoomObserver`, `hello.link`, fichier horaire | métriques du service | sonnet M | **obligatoire** | w20-04b fusionné (règle R2 W20) |
| 2 | **w21-03b** route d'ingestion `play-metrics` + collecteur hôte (DRY-RUN testé) | le pont sans secret dans le service | sonnet S | **obligatoire** | w21-02 |
| 2 | **w21-02b** API : `POST /api/v1/events/relay` (ouverture de l'enveloppe, `tele_batch`, plafonds, accusé), jeton précédent 14 j, agrégats sur 14 jours glissants | le relais accepté au nom de la TV | sonnet M | **obligatoire** | w21-02, w21-01b (vecteurs de test partagés) |
| 3 | **w21-04** câblage TV derrière `pocMetrics` (sans écran neuf) : points de mesure, cadences, file scellée, `TeleHub` (remise par `LinkPlanner` : réseau commun > Wi-Fi Direct > tunnel Bluetooth), envoi direct 12 h | preuves TV | sonnet M | **obligatoire** | w21-01, w21-01b, w20-05a fusionné |
| 3 | **w21-05** scripts d'analyse + recommandations | rapports | sonnet M | échantillon | w21-02 |
| 3 | **w21-06** section `/admin/kpi` « POC en ligne » + cohorte + k = 5 + journal d'exports | tableau de bord | haiku M | échantillon | w21-02 |
| 3 | **w21-07** téléphone **synchronisé coursier** : retrait des lots par tous les canaux, file par TV, envoi au serveur dès qu'il a Internet, accusés rapportés ; **statistiques propres à 12 h compressées** ; classe cellulaire de la passerelle, `sync.pair`, `sync.xfer`, `sync.courier` | les mesures TV et téléphone arrivent au serveur | sonnet M+ | échantillon | w21-01b, w21-02b (contrat), w21-04 (routes de la TV) |
| 4 | **w21-08** réglages bornés du service (délai, mode adaptatif éteint, grâce, ping de salle) | la boucle de réglage côté service | sonnet S | **obligatoire** | w21-03 (même `PlayConfig`) |
| 5 | **w21-09** docs (`TELEMETRY.md`, `API-SERVER.md`, `PLAY-OPS.md`), cron hebdomadaire, phrase de consentement proposée | exploitation | haiku S | — | tous |

Coût (prix 2026-09 de l'index : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet M 8 × ≈ 1,0-1,1 $ ≈ 8,3 $ (01, 01b, 02, 02b, 03, 04 et 07 en M+, 05) ; sonnet S 2 × ≈ 0,4 $ = 0,8 $ (03b, 08) ; haiku M ≈ 0,5 $ (06) ; haiku S ≈ 0,2 $ (09) ; audits Opus obligatoires 7 × ≈ 0,8 $ = 5,6 $ (01b, 02, 02b, 03, 03b, 04, 08) ; échantillons 4 × ≈ 0,4 $ = 1,6 $ (01, 05, 06, 07) ; reprises 15 % ≈ 2,5 $. **Total ≈ 19-20 $, ≈ 15 agent·jours** (première version : ≈ 14-15 $, ≈ 11 j ; l'amendement d'envoi ajoute ≈ 5 $ et ≈ 4 j).

Livraisons de production (actes du propriétaire, jamais par les exécutants) : **server-1.1.2** (API : w21-02, w21-02b, w21-03b, w21-06 ; migration V62 ; `deploy-server.sh server-1.1.2 --apply`, sauvegarde et retour arrière automatiques) ; **server-play-0.2.x** (w21-03, w21-08 ; `--service play`) ; cron du collecteur et clé d'ingestion (w21-09 écrit le runbook) ; APK TV/téléphone verrouillés (w21-04, w21-07). Retour arrière de l'API vers 1.1.1 avec V62 appliquée : Spring Boot ignore par défaut les migrations « futures » (`spring.flyway.ignore-migration-patterns` = `*:future`) — **à vérifier dans `application.yml`** par w21-02 et à écrire dans PLAY-OPS.

## 11. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W21-1 | Les mesures techniques sont-elles « statistiques d'usage » (consentement) **et** réservées à une cohorte POC (TV du propriétaire + testeurs consentants) jusqu'à la relecture juridique, sans changer le texte affiché ? **Fermée pour le coursier** (propriétaire, 2026-10-04) : « Le téléphone coursier est un téléphone synchronisé » ⇒ il transporte les lots de la TV sous la porte de la TV, sans consentement séparé | **Oui** | les classer « essentielles » serait juridiquement nouveau ; changer le texte maintenant anticipe la relecture |
| D-W21-2 | Interrupteur par TV : directive serveur `pocMetrics` (cohorte tenue dans `/admin`) plutôt qu'un ordre signé `flag.set` ? | **Oui** (le serveur refuse de toute façon hors cohorte ; un ordre n'ajoute pas de sûreté et passe par le téléphone) | `flag.set poc.metrics` en plus, pour les TV jamais en ligne (inutile : une TV en partie Internet est en ligne) |
| D-W21-3 | Conservation : brut 30 j, horaires 30 j, journaliers 12 mois (cases ≥ 5), service 90 j / 12 mois ? | **Oui** | plus long : à justifier à la relecture |
| D-W21-4 | Plancher k = 5 sur toutes les pages et exports ; vue par appareil seulement pour les TV marquées `owner_test` ? | **Oui** (sinon, avec 2-10 TV de POC, la page serait vide) | pages vides tant que la cohorte < 5 |
| D-W21-5 | Sortie des métriques du service : fichier dans `play-state` + collecteur hôte qui pousse à l'API locale avec une clé dédiée ? | **Oui** | (a)-(c) du § 5.2 : route publique ou secret dans le service |
| D-W21-6 | Pas de configuration distante signée pendant le POC ; réglages du service par variables bornées ; `tune.set` dans les ordres en phase 2 si besoin ? | **Oui** | un `GET /api/v1/config` signé : surface neuve à auditer pour quelques TV |
| D-W21-7 | Délai adaptatif construit **éteint** (`CASTBRIDGE_PLAY_GAP_MODE=fixed`), allumé seulement si la règle M-12 le demande avec confiance haute ? | **Oui** | rester à 1,5 s fixe sans moyen de réagir |
| D-W21-8 | Classe cellulaire du téléphone sans permission (estimation de bande passante du système), plus classe mesurée par la TV ? | **Oui** (jamais `READ_PHONE_STATE`) | demander une permission téléphone : refusée par l'esprit de minimisation |
| D-W21-9 | Budget (remplacé par l'amendement) : techniques ≤ 8 Ko compressés / jour / TV, toute la télémétrie ≤ 24 Ko ; lot ≤ 32 Ko ; aucune remise ni envoi pendant une partie et 30 s après ; **aucune** télémétrie par la passerelle ? | **Oui** | — |
| D-W21-10 | Ni ASN, ni opérateur, ni ville ; pays seulement depuis `device.country` ? | **Oui** | l'ASN identifierait l'opérateur, utile mais nouveau |
| D-W21-11 | Deux livraisons séparées : server-1.1.2 (API) puis server-play-0.2.x (service) ? | **Oui** (retours arrière indépendants) | — |
| D-W21-12 | **FERMÉE** (propriétaire, 2026-10-04 : « Celles qui pourraient passer de 15 à 12 heures. Oui ») : la TV suit 12 h direct / 4 h vers le téléphone **et** les statistiques propres du téléphone passent de 15 min à 12 h, compressées | appliquée (w21-07) | — |
| D-W21-13 | Les essentiels de la TV (`error`, `crash`, `update_install`) suivent-ils aussi les cadences 12 h / 4 h ? Le heartbeat (pas une statistique) garde-t-il son chemin, passerelle comprise ? | **Oui** et **oui** | essentiels à part : un envoi plus fréquent, y compris par la passerelle (contraire à « jamais par la passerelle ») |
| D-W21-14 | Enveloppe scellée AES-256-GCM avec clé dérivée de `SHA-256(jeton)` (que le serveur garde déjà), accusé HMAC vérifié par la TV ? | **Oui** (aucun secret neuf, aucune dépendance ; limite : une fuite de base permet de forger de la télémétrie) | clé de télémétrie propre à l'enregistrement : plus sûr, plus long (phase 2) |
| D-W21-15 | Remise locale par les routes HTTP de la TV via `LinkPlanner` (réseau commun > Wi-Fi Direct existant > tunnel Bluetooth), trames `CBTO` 22-26 seulement si des TV n'ont pas le tunnel ; jamais de groupe Wi-Fi Direct créé pour la télémétrie ? | **Oui** | trames brutes partout : plus de code, même résultat |
| D-W21-16 | TV jamais enregistrée (sans jeton) : hors POC ; enregistrement relayé en phase 2 ? | **Oui** (elle ne peut pas être dans la cohorte de toute façon) | — |
| D-W21-17 | Rapport hebdomadaire arrêté à J−3 et recommandations seulement si complétude ≥ 80 % ? | **Oui** | des recommandations sur des données incomplètes |

**BLOQUÉ (faits)** : aucun. **Dépendances** : w21-03/08 attendent la fusion de w20-04b ; w21-04 attend celle de w20-05a. **À vérifier** (non bloquant) : présence du tunnel HTTP Bluetooth (`LinkPlanner.Route.BluetoothTunnel`) sur les TV de la cohorte ; point d'enregistrement des hubs HTTP de la TV ; version de l'API en production ; `ignore-migration-patterns` ; la trame d'attache de la passerelle ; la disponibilité de `getLinkDownstreamBandwidthKbps` sur les téléphones du propriétaire.

## 12. Risques

| # | Risque | Prob. | Parade |
|---|---|---|---|
| R-1 | Cohorte trop petite : rien ne passe le plancher k = 5, aucune recommandation « haute » | **élevée** pendant le POC | vue `owner_test` ; recommandations « moyenne » signalées comme telles ; la preuve du service (sans appareil) n'est pas soumise à k |
| R-2 | La mesure gêne le jeu (octets, tas, processeur sur TV 32 bits) | moyenne | politique d'envoi (rien pendant une partie, rien par la passerelle, remise locale de plus basse priorité), objet nul hors cohorte, tableaux fixes, test « 0 octet pendant une partie », mesure du tas avant/après (w21-04) |
| R-3 | Le texte de consentement ne cite pas ces mesures | certaine | cohorte restreinte (D-W21-1), phrase proposée à la relecture (w21-09) |
| R-4 | Croissance de la base | faible | plafonds, purges, alerte |
| R-5 | Empoisonnement par une TV modifiée | faible (cohorte fermée) | cohérence, robustesse, exclusion |
| R-6 | Classe cellulaire peu fiable | moyenne | classe mesurée prioritaire |
| R-7 | Conflits de fusion avec w20-04b / w20-05a (mêmes fichiers) | moyenne | dépendances explicites, zones additives, un cahier à la fois sur `server-play/` |
| R-8 | Horloge de TV fausse ⇒ jours de rattachement faux | faible | durées monotones ; jour du serveur si l'horloge s'écarte (règle existante) |
| R-9 | Le collecteur cron oublié ou cassé | moyenne | M-52 « dernière heure reçue » sur la page ; ligne dans HANDOFF |
| R-10 | Le volume `play-state` plein | faible | anneau de 72 fichiers ≤ 64 Ko |
| R-11 | Données TV trop tardives pour le POC (téléphones rarement à portée ou rarement en ligne) | moyenne | métriques du service d'abord ; TV du propriétaire en direct (12 h) ; complétude affichée ; TTL 30 j |
| R-12 | Les KPI d'usage du **téléphone** (pages `/admin`, fiche appareil) deviennent moins frais (12 h au lieu de 15 min) | certaine | DAU/MAU restent alimentés par les heartbeats (inchangés) ; la page dit « données jusqu'au … » |
| R-13 | Clé dérivée de `token_hash` : une fuite de la base permet de forger de la télémétrie | faible | impact limité à des agrégats ; phase 2 : clé propre à l'enregistrement |
| R-14 | Le tunnel HTTP Bluetooth absent sur certaines TV | moyenne | repli trames `CBTO` 22-26 (construit seulement si constaté) |

## 13. Ce qui a été lu, et ce qui n'a pas pu être vérifié

**Lu** : `docs/TELEMETRY.md` (entier), `docs/API-SERVER.md` § 4 et la réponse du heartbeat, `docs/PLAY-PROTOCOL.md` (entier), `docs/PLAY-OPS.md` (§ 0-1, 4.4 partiel, 5-10), `docs/ORDRES.md` (§ 1-4), `DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (entier), titres de `DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md`, extraits de `DESIGN-W19-SYMBIOSE-PHONE-TV-2026-10-03.md` (R-09…R-17, RS-xx), `SONNET-WAVE20-INDEX.md` (tête), cahier w20-05a (tête) ; code : `B/telemetry/{EventCatalog,TelemetryController,TelemetryService,AdminKpiController,Kpi}.java` (entiers), `V3__telemetry.sql`, `db/migration/README.md`, `B/config/SecurityConfig.java` (chaînes), `B/devices/DeviceController.java` (heartbeat), `SP/HealthController.kt`, `SP/guard/LogRedactor.kt`, `SP/PlayServer.kt` (routage), `SP/PlayConfig.kt` (constantes), `C/telemetry/{Telemetry,TelemetryUploader}.kt`, `C/connect/ConsentText.kt`, `C/store/StoreFlag.kt`, constantes de `PlayTiming`, `RttBook`, `SafetySign`, `ServerRoom.PING_EVERY_MS`, `PlaybackPriority`, `BufferGovernor`, `Routes` ; sites d'émission de `quiz_*`, `connectivity_check`, `gateway_session`, `playback_end`, `cast_*`.

**Lu pour l'amendement d'envoi** : `docs/ORDRES.md` § 6-12 (téléphone messager, `OrderQueue`, trames `CBTO` 16-21, limites), `LinkPlanner` (`C/tv/BtProtocol.kt:431-450`), `C/link/BulkRoute.kt` (signatures), `B/devices/DeviceService.java` (empreinte du jeton), `docs/REMOTE-TUNNEL-BT.md:38`.

**Non lu / non vérifié** : le tunnel HTTP Bluetooth (`BluetoothTunnel`) en pratique et sa présence sur les TV de la cohorte ; le point d'enregistrement des hubs HTTP de la TV ; `TrustRegistry`/`PinBook` ligne à ligne ; le débit réel RFCOMM (aucune mesure dans le dépôt) ; le rapport de compression réel du DEFLATE à dictionnaire ; `KpiService.java` et `AdminKpiPages.java` ligne à ligne (seulement leur structure) ; `DeviceService.Directives` (forme exacte) ; `PlayFallbackController`/`PlayWebSocketHandler` (files de sortie) et `RoomRegistry` ligne à ligne ; la trame d'attache de la passerelle Bluetooth ; `WdDiagnostic`, `WdPolicy`, `Thumbnailer`, `QuizHub.cachedSource` (points de mesure à localiser par les exécutants) ; le code de w20-04b et w20-05a (non encore écrit ou non fusionné) ; `application.yml` (Flyway) ; la taille du tampon InnoDB ; l'état de l'API, de nginx et des volumes en production ; le comportement réel de `getLinkDownstreamBandwidthKbps` ; **aucun chiffre de taille, de débit, de latence ou de coût n'a été mesuré**.
