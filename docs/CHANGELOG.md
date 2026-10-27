# Journal des modifications de CastBridge

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) ; numérotation [SemVer](https://semver.org/lang/fr/) avec le suffixe `-beta` (règles : `docs/RELEASES.md`, `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`).
Trois produits livrés ont chacun leur numéro : **CastBridge-TV** (`tv-<version>`), **CastBridge** téléphone (`phone-<version>`), **CastBridge Propriétaire** (`owner-<version>`, jamais distribué). Le serveur (`backend/`, `pom.xml` 1.2.5 et lignes `server.*` de `version.properties`) a son propre numéro, étiqueté `server-<version>` ; le service de jeu `server-play` a ses étiquettes `server-play-<version>` (hors `version.properties`).

> **Comment ce journal a été reconstruit, et sa limite.** Aucun tag git n'existait au 2026-10-02 : la correspondance version / commit est reconstituée à partir de l'historique premier-parent de `integration/agents` (`git log --first-parent`) et de l'historique de `version.properties` (`git log -p -- version.properties`). Le contenu de chaque entrée vient **uniquement des sujets de commits** (rien n'est inventé) et peut être **approximatif** : les APK ont parfois été construits avant le commit qui pose leur numéro, et plusieurs numéros n'ont jamais été commités (voir « Numéros sans commit »). Avant le 2026-10-01 (création de `version.properties`, commit `0ff64ed`), les numéros ne sont connus que par les sujets de commits et `docs/HANDOFF.md` : ils sont listés sans promesse de tag. Le plan d'étiquetage prouvable est produit par `tools/release/tag-plan.sh`.
>
> Les étiquettes (`tv-…`, `phone-…`, `owner-…`, `server-…`, `server-play-…`) sont posées depuis le 2026-10-02 ; celles qui manquent encore : `bash tools/release/tag-plan.sh`.

## [Non publié]

- CastBridge-TV 0.14.58-beta verrouillée (141 / 142) : la barre d'état est ENROULABLE (demande du propriétaire, photo de la TV du 2026-10-27 : la colonne de pastilles recouvrait les titres des cartes). Enroulée au départ (jamais mémorisée) : une seule pastille dans l'en-tête, à côté de l'horloge (pire niveau en couleur + signe + nombre, p. ex. « ! 9 »), sans libellé permanent ; un changement la fait pulser et lui prête 4 s un libellé qui reste dans la bande d'en-tête. OK sur la pastille (UP depuis la pastille « prêt », accueil seulement) déroule le panneau en haut à droite, au-dessus du contenu, 3 pastilles par ligne avec libellés ; les flèches circulent entre les pastilles sans jamais atteindre les cartes ; RETOUR, OK sur la pastille ou 10 s sans touche enroulent. Pendant la vidéo : pastille seule (R-53 inchangée), jamais de focus. Marge droite de l'accueil réduite (220 dp → 0). Règles pures `StatusFold` / `StatusFoldState` (:core) + `StatusFoldTest`. À vérifier sur la vraie TV.
- Téléphone 1.2.61-beta (91), branche `claude/cast-open-tv-direct` : (1) la lecture DIRECTE (`CastSession.live`, cible boîtier) réveille CastBridge-TV comme la copie, via `OpenTv.playOpeningTv` (409 needsForeground -> `POST /api/tv/open`, nouvel essai 5 s) ; (2) « Ouvrir avec » propose « Lire en direct sur la TV » puis « Lire en copiant sur la TV » EN PREMIER (la lecture précède la copie), puis copier / déplacer. Textes locaux à `OpenWithActivity` (aucun texte du noyau modifié).
- CastBridge-TV 0.14.56-beta verrouillée (137 / 138) : binaire fonctionnellement inchangé (version seule), le lancement à distance existait déjà.

- CastBridge-TV 0.14.57-beta verrouillée (139 / 140) : la bibliothèque lit le dossier de la clé USB une fois par liste, pas une fois par fichier (`FileStore.fileResolver`) ; accueil vide des minutes avec 172 fichiers sur une clé exFAT (propriétaire, 2026-10-27). Vérifié en JVM (`FileResolverTest`).
- CastBridge-TV 0.14.57-beta verrouillée (139 / 140) : accueil en lecture progressive : les outils et le texte « Lecture de la bibliothèque… » s'affichent tout de suite ; « Reprendre » (10 fichiers au plus, regardés en partie, derniers lus d'abord) est lue dans la base de la bibliothèque seule, avant la liste des volumes (la carte n'est cherchée sur le disque qu'à son ouverture ; absente : message court et la carte disparaît) ; la liste lit la date de chaque fichier avec le dossier au lieu de rouvrir chaque fichier ; le rafraîchissement toutes les 4 s ne recalcule rien tant que la liste, la base, le fichier en lecture, les dossiers virtuels et le profil parental n'ont pas changé ; la liste complète met les cartes à jour en place sans prendre le focus. `/api/library` inchangé. Vérifié en JVM (`HomeFastPathTest`) ; à confirmer sur la TV avec la clé.
- CastBridge-TV 0.14.57-beta verrouillée (139 / 140) : la liste des fichiers n'est lue que par une lecture à la fois (`ReceiverServer.listing`) ; avant, chaque appel périmé lançait la sienne sur tous les volumes et, sur la clé exFAT lente, les fils HTTP s'empilaient jusqu'à ce que même `/api/hello` ne réponde plus (propriétaire, 2026-10-27). Les lecteurs qui sondent (`/api/info`, accueil, ligne d'état) reçoivent la liste précédente pendant une lecture ; qui vient d'écrire attend une lecture faite après son écriture ; une lecture de plus de 0,5 s est consignée (« listing : N fichiers, M volumes, X ms »). Vérifié en JVM (`ListingSingleFlightTest`).
- CastBridge-TV 0.14.58-beta verrouillée (141 / 142) : suppression en masse DÉFINITIVE (décision du propriétaire, 2026-10-27), à la télécommande : MENU sur un fichier > « Sélectionner plusieurs… » (OK coche, bandeau « N sélectionnés · X Go » avec « Supprimer définitivement », « Tout sélectionner », « Annuler » ; RETOUR quitte) et, en haut de la bibliothèque, « Supprimer en masse… » : « Un dossier… », « Vidéos déjà vues », « Non lues depuis… » (1 / 3 / 6 / 12 mois), « Les plus gros d'abord » ; les préréglages ne font que cocher, on corrige avant de confirmer. UNE confirmation (« Supprimer définitivement 23 fichiers (14,2 Go) ? Sans retour possible. », focus sur « Annuler »), puis « Suppression : k / n » et « 23 fichiers supprimés, 14,2 Go libérés » (+ fichiers laissés et pourquoi). Nouvelle route `POST /api/delete/batch` (corps `{"files":[{"name","volume"}]}`, travail en arrière-plan, un seul à la fois : 409) et `GET /api/delete/batch?job=` ; mêmes règles que `/api/delete` par fichier (déplacement en cours : laissé, absent : laissé, lecture : arrêtée), liste invalidée une seule fois à la fin ; refusée (403) tant qu'un profil enfant est actif. Logique pure `castbridge.core.tv.BulkDelete` (`BulkDeleteTest`, `BulkDeleteServerTest`).
- CastBridge téléphone 1.2.62-beta (92) : la bibliothèque de la TV (« Bibliothèque ») permet la suppression en masse définitive : « Sélectionner plusieurs » dans le menu d'un fichier (appui long), cases à cocher, barre « N sélectionnés · X Go », « Tout sélectionner », et « Supprimer en masse » dans le menu « ⋮ » (un dossier, vidéos déjà vues, non lues depuis 1 / 3 / 6 / 12 mois, les plus gros d'abord) ; même confirmation que sur la TV, progression lue sur la TV ; une TV sans la route (404) est supprimée fichier par fichier avec `/api/delete`.

## CastBridge-TV

### [0.14.55-beta] (code 136 ; verrouillée 137) : 2026-10-19
- un refus pour manque de place dit pourquoi la clé USB n'a pas servi (R-55, propriétaire, 2026-10-18 : « l'erreur … n'est pas normale » ; clé de 58 Go avec 23 Go libres et refus « il resterait 211 Mo sur Mémoire interne ») : après « Espace insuffisant : … », une phrase par autre volume présent, par exemple « Clé USB (23 Go libres) non utilisée : lecture seule (test d'écriture raté). », « … fichier trop gros pour FAT32. », « … réglage « Où ranger les nouveaux fichiers : Mémoire interne » ; choisissez Automatique ou la clé dans MENU › Stockage. » (ou « … choix « Destination sur la TV : Mémoire interne » du téléphone ; choisissez Automatique ou la clé. »), « … jugée lente, pourtant retenue en dernier recours. », et « pas assez de place (…) » ou « quota de stockage atteint » ; au début d'un envoi, au contrôle préalable et à l'ouverture d'un transfert multi-connexion ; un seul volume : le message est inchangé
- une clé écartée « lecture seule » quand une copie est refusée pour manque de place est testée de nouveau avant la réponse (cette clé seulement, une fois par minute au plus), puis la TV refait son choix : son test d'écriture n'était fait qu'une fois au montage ; un réglage « Mémoire interne » ne relance rien
- vérifié en JVM (`SpaceRefusalTextTest` : 8 tests, `SpaceRefusalServerTest` : 7 tests sur la vraie `ReceiverServer`) et par la compilation de `:receiver` ; à confirmer sur la TV de référence avec la clé : parcours P-99 (la cause réelle du défaut du 2026-10-18, réglage ou test d'écriture, reste à relever sur la TV)
- dans le dépôt, sans effet sur l'APK : parcours P-99 (`docs/test-plans/PARCOURS-CRITIQUES.md`), `docs/REGRESSIONS.md` (R-55) et `docs/STORAGE.md` (§ 4)

### [0.14.54-beta] (code 134 ; verrouillée 135) : 2026-10-18
- la pastille « Stockage » de la barre d'état dit l'espace restant de la clé USB qui reçoit les copies (décision du propriétaire du 2026-10-18 : « le stockage devrait indiquer le pourcentage d'espace restant dans la clé »), au lieu du volume le plus serré : « Clé USB · 37 % libre » quand la clé est branchée, montée, inscriptible et choisie comme destination (la politique de stockage des envois, `docs/STORAGE.md` § 4), « Stockage · 12 % libre » (mémoire interne) sinon ; seuils inchangés (vert à 20 % libre ou plus, orange de 5 à 20 %, rouge sous 5 % ou en lecture seule) ; si l'autre mémoire est presque pleine ou en lecture seule, la pastille garde son volume, passe au moins à l'orange et le panneau Connexions l'écrit sous le libellé (« 48 Go libres sur 128 Go · mémoire interne presque pleine ») ; aucune pastille de plus, légende du lecteur complétée
- vérifié en JVM (`StatusFeedRulesTest`, `StatusSnapshotTest` : le volume de destination, le repli sur la mémoire interne, l'autre volume en alerte, la clé en lecture seule, une garde de source du branchement) et par la compilation de `:receiver` ; à confirmer sur la TV de référence avec la clé branchée : parcours P-98
- dans le dépôt, sans effet sur l'APK : parcours P-98 (`docs/test-plans/PARCOURS-CRITIQUES.md`), `docs/TV-ACCUEIL.md` (« Pastilles »), `docs/TV-PLAYER.md` (« Pastilles d'état ») et `docs/STORAGE.md` (§ 4)
- la réception Bluetooth écrit le même fichier de taille `<nom>.meta` que l'envoi HTTP (la taille totale, avant le premier octet, retiré quand le fichier est complet) : une copie COMMENCÉE en Bluetooth peut être reprise en Wi-Fi à l'octet reçu (`GET /api/part?size=` ne répond plus `PART_OTHER`), suite de R-54 (« la reprise de la copie est lente bien que la disponibilité d'un réseau wifi soit effective ») ; un `.part` dont le `.meta` annonce une autre taille est écarté et la copie repart de zéro, comme le 409 du HTTP ; ces copies apparaissent désormais dans la liste des copies reçues en partie (accueil, MENU « Copie : annuler »)
- vérifié en JVM (`BtPartMetaTest` : 5 tests sur la vraie `ReceiverServer` et la vraie `BtProtocol.serve` : reprise en Wi-Fi d'une copie commencée en Bluetooth, autre taille refusée, `.meta` écrit avant « prêt » et retiré à la fin, autre contenu écarté, copie ancienne sans `.meta` adoptée) et par la compilation de `:receiver` ; à confirmer sur la TV réelle avec le téléphone : parcours P-97 (R-54) ; sera livré avec la prochaine construction de CastBridge-TV (0.14.54)

### [0.14.53-beta] (code 132 ; verrouillée 133) : 2026-10-18
- la bande de licence (l'étiquette en haut au centre : « PRODUCTION · Clé illimitée » en vert, « ESSAI … » en orange) n'est plus permanente (décision du propriétaire du 2026-10-18 : « La bande verte de production ne doit pas être permanente et apparaître seulement lorsqu'on revient sur l'accueil pour 15 secondes ») : elle s'affiche 15 s chaque fois que l'accueil de CastBridge-TV est (re)montré (lancement de l'app, retour d'une vidéo, fermeture du MENU ou de la bibliothèque, retour de Quiz, Apprendre, Jeux…) puis disparaît ; jamais pendant une vidéo, MENU ouvert, dans la bibliothèque ni dans une autre activité. Hypothèse à confirmer (le propriétaire peut la renverser) : la bande ROUGE (clé finie ou absente, « ACTIVATION TERMINÉE », « SANS CLÉ ») reste affichée sur tous les écrans, comme avant ; « SUPER ILLIMITÉ » (vert) devient temporaire comme la production. Règle pure `KeyBadgeDisplay` (`core/owner/KeyBadge.kt`), branchement dans `KeyBadgeOverlay` et `PlayerActivity` (`showHome`, `onResume`, `hideScreens`, `showLibrary`, ouverture du MENU)
- vérifié en JVM (`KeyBadgeDisplayTest`, 10 tests : la règle et une garde de source du branchement) et par la compilation de `:receiver` ; à confirmer sur la TV de référence : parcours P-96
- dans le dépôt, sans effet sur l'APK : parcours P-95 et P-96 (`docs/test-plans/PARCOURS-CRITIQUES.md`), journal des régressions R-53, `docs/TV-PLAYER.md` (« Icônes du lecteur »), `docs/TV-ACCUEIL.md` (« Pastilles ») et `docs/TRIAL-EDITION.md` (bande de licence)

### [0.14.52-beta] (code 130 ; verrouillée 131) : 2026-10-18
- la barre d'état (les pastilles en haut à droite : Wi-Fi, Bluetooth, Internet…) ne reste plus à l'écran pendant la vidéo ni quand le MENU « Connexion & réglages » est ouvert (R-53, « Les affichages des icônes Bluetooth, Wi-Fi… d'extrême droite perdurent pendant le visionnage de la vidéo ou la navigation sur le menu ») : à l'accueil elle reste et prend la télécommande ; MENU ouvert elle disparaît et revient à la fermeture ; pendant une vidéo elle n'apparaît qu'avec la barre de commandes (OK court) ou 4 s après un changement d'état d'une pastille (passage en alerte compris) ; sur la bibliothèque, 4 s après un changement seulement. Une pastille orange ou rouge qui dure ne la garde plus (la règle du 2026-10-04 « l'alerte garde la zone » est remplacée pour la vidéo) ; libellés de 28 sp, glyphes de 40 dp et marges de 5 % en lecture inchangés
- vérifié en JVM (`PlayerIconsTest`, règle pure `PlayerIcons.barVisible`) et par la compilation de `:receiver` ; à confirmer sur la TV de référence : parcours P-95, R-53 « CORRIGÉE (à confirmer sur appareil) »

### [0.14.51-beta] (code 128 ; verrouillée 129) : 2026-10-15
- une copie interrompue s'annule depuis la TV (R-51, « Une copie interrompue sur la TV n'est pas annulable ») : le MENU porte une ligne « Copie : annuler « titre » (42 %, interrompue) » (ou « …, en cours ») par copie reçue en partie, et la tuile « Recevoir du téléphone » les propose après « Comment envoyer depuis le téléphone » ; une confirmation où « Non, la garder » est présélectionné, puis le bandeau « Copie annulée : titre » (ou « Rien à annuler ») ; l'annulation efface le fichier partiel (`.part` et `.meta`) sur tous les volumes et arrête l'envoi qui écrivait encore
- l'accueil distingue les copies vivantes des copies interrompues : « ⬇ Réception de X : 42 % » seulement pour une copie qui avance (progression en cours, session multivoie ouverte ou écriture depuis moins de 60 s), sinon la puce « ⏸ Copie interrompue : X (42 %) · MENU > Copie : annuler » ; `receiving()` ne liste plus que les copies vivantes, donc « Générer un nouveau PIN » n'est plus refusé à cause d'un fichier partiel mort (il restait annoncé des heures, jusqu'au balayage des orphelins : 24 h, 7 jours sur une clé USB)
- fenêtre de refus de 10 minutes après une annulation : le téléphone qui reprend la même copie reçoit un seul texte français, « Copie annulée sur la TV : renvoyez-la dans quelques minutes si c'était une erreur. », au lieu d'attendre et de réessayer sans fin (`PUT /upload/` répond 400, `POST /api/transfer/begin` répond 409, `GET /api/part` ajoute le champ optionnel `code` `TV_CANCELLED`) ; aucun changement de protocole, un téléphone 1.2.56 ou 1.2.57 reste compatible
- nouvelle route `POST /api/reception/cancel?name=` (mêmes gardes que `/api/reset` : code de la TV ou jeton), classée fermée en édition d'essai ; `tools/routes/routes.txt` régénéré
- notifications (R-52, « les copies ne signalent plus en notification ») : tant qu'Android jette les notifications de CastBridge-TV (permission `POST_NOTIFICATIONS` refusée ou jamais accordée sous Android 13 et plus, ou notifications coupées dans les réglages), la PREMIÈRE ligne du MENU dit « Notifications : désactivées sur cette TV — autoriser (copies reçues, demandes du téléphone) » ; OK redemande l'autorisation seule tant qu'Android montrera son dialogue, sinon ouvre le réglage des notifications de l'application (repli : détails de l'application, puis « Réglage indisponible sur cette TV ») ; toute réponse du dialogue est retenue, celle du lancement groupé avec le Bluetooth comme celle de la ligne ; le contrôle parental range la ligne avec les réglages
- au début de chaque copie reçue, la TV le dit à l'écran (bandeau « ⬇ Réception de … », jamais sur une vidéo qui joue) ; notifications coupées, l'avertissement « Notifications désactivées : les copies ne s'affichent qu'à l'écran (MENU > Notifications) » vient UNE fois par lancement de l'application
- vérifié en JVM seulement : `:core:test` 5 868 tests, 0 échec, 0 erreur, 4 ignorés (les anciens) sur la branche fusionnée, `:receiver:compileDebugKotlin` et `:sender:compileDebugKotlin` verts ; NON ESSAYÉ sur la TV : parcours P-93 (copie interrompue) et P-94 (notifications) à jouer, R-51 et R-52 restent « à confirmer sur appareil »
- dans le dépôt, sans effet sur l'APK : parcours P-93 et P-94 (`docs/test-plans/PARCOURS-CRITIQUES.md`), `docs/TRANSFER.md` § 12, `docs/STORAGE.md`, `docs/ADMIN.md` (route, ligne du MENU, sens de `notifications` dans `GET /api/background`), journal des régressions R-51 et R-52, guide utilisateur (« Si ça ne marche pas » › « Copies et notifications »)
- détail : `git log tv-0.14.50-beta..tv-0.14.51-beta`

### [0.14.50-beta] (code 126 ; verrouillée 127) : 2026-10-15
- l'état « grâce » est exposé au téléphone : `GET /api/activation` dit aussi `grace` et `graceUntil` (ms UTC ; `false` et `null` hors grâce), parce que `trial` est vrai en grâce (installation antérieure au verrou, jusqu'au 2026-11-01) et que le téléphone prenait alors la TV pour une version d'essai
- textes R-48 corrigés : l'avis d'usage de l'écran verrouillé (`LockedTexts.NOTICE`), l'écran « Passer en version complète » (`TrialPolicy.UPGRADE_EXPLAIN`) et la tuile « Passer en production » de l'accueil ne demandent plus de communiquer le code d'appareil (le code seul ne permet pas d'émettre une clé) : la clé se demande avec CastBridge › Activer la TV, qui lit la demande d'appareil complète
- routes de l'édition d'essai classées : `/api/relay` (vérité réseau unique de la TV, lecture seule) devient ouverte, comme `/api/net` ; `/api/games/room` et `/api/games/journals` restent fermées, désormais couvertes par `TrialRoutesTest`
- code mort retiré de `LockedTexts` : `KEY_WAYS` et `shareMessage` (textes périmés, plus appelés)
- dans le dépôt, sans effet sur l'APK : CI (`timeout-minutes` dans les jobs, build de contrôle verrouillé à clé jetable, `tools.yml`), outils Python (194 tests verts), docs de versions, guide utilisateur (ordre réel des quatre voies d'activation)
- détail : `git log tv-0.14.49-beta..tv-0.14.50-beta`

### [0.14.49-beta] (code 124 ; verrouillée 125) : 2026-10-11
- clé de la console du S21+ (kid 3447e186…) ajoutée aux clés de confiance

### [0.14.48-beta] (code 122 ; verrouillée 123) : 2026-10-11
- la demande d'appareil d'une TV verrouillée porte install_sig= sans dépendre de WalletHub.init
- détail : `git log tv-0.14.47-beta..tv-0.14.48-beta`

### [0.14.47-beta] (code 120 ; verrouillée 121) : 2026-10-07
- activation par Bluetooth sans appairage (CPace sur le code), I-4
- CastBridge-TV 0.14.47-beta (code 120 ; verrouillée 121) et CastBridge 1.2.54-beta (code 84) : Bluetooth sans appairage authentifié par le code, I-4
- act-bt : activer la TV par Bluetooth SANS appairage, authentifié par le code (CPace, AES-GCM) ; I-4 / R-49

### [0.14.46-beta] (code 118 ; verrouillée 119) : 2026-10-07
- activation par code, relais, jeux, ouverture à distance, clé USB, correctifs d'audit
- détail : `git log tv-0.14.34-beta..tv-0.14.46-beta`

### [0.14.34-beta] (code 94 ; verrouillée 95) : 2026-10-04
- pastilles, code PIN, Quiz en ligne corrigé, logo
- détail : `git log tv-0.14.33-beta..tv-0.14.34-beta`

### [0.14.33-beta] (code 92 ; verrouillée 93) : 2026-10-04
- accueil en groupes, logo Cameroun et MBOKO
- feat(tv): accueil en boutons de groupe qui ouvrent une grille (Médias, Apprendre, Jeux et jetons, Téléphones et réseau, Administration) ; logo TV compact sans MBOKO à l'accueil
- détail : `git log tv-0.14.32-beta..tv-0.14.33-beta`

### [0.14.32-beta] (code 90 ; verrouillée 91) : 2026-10-04
- portefeuille, Quiz en ligne, options multimédia, diffusion YouTube
- détail : `git log tv-0.14.31-beta..tv-0.14.32-beta`

### [0.14.31-beta] (code 88 ; verrouillée 89) : 2026-10-04
- options multimédia du lecteur
- détail : `git log tv-0.14.30-beta..tv-0.14.31-beta`

### [0.14.30-beta] (code 86 ; verrouillée 87) : 2026-10-04
- barre de boutons, légende des icônes, diagnostic
- feat(tv): barre de commandes à l'écran pour télécommande de base, diagnostic d'affichage 6 s sans touche, icônes du lecteur toutes libellées et légende
- détail : `git log tv-0.14.29-beta..tv-0.14.30-beta`

### [0.14.29-beta] (code 84 ; verrouillée 85) : 2026-10-04
- Remplir l'écran par défaut
- détail : `git log tv-0.14.28-beta..tv-0.14.29-beta`

### [0.14.28-beta] (code 82 ; verrouillée 83) : 2026-10-04
- détail : `git log tv-0.14.27-beta..tv-0.14.28-beta`

### [0.14.27-beta] (code 80 ; verrouillée 81) : 2026-10-03
- fix(transfert): audit R-17 : bloc interrompu = 503, réponse anticipée lue, détecteur par catégorie et par octets, vidage borné et assaini
- détail : `git log tv-0.14.26-beta..tv-0.14.27-beta`

### [0.14.26-beta] (code 78 ; verrouillée 79) : 2026-10-03
- fix(lecture): image figée pendant une copie avec son fluide, icône de copie dans le lecteur (R-16)

### [0.14.25-beta] (code 76 ; verrouillée 77) : 2026-10-03
- détail : `git log tv-0.14.24-beta..tv-0.14.25-beta`

### [0.14.24-beta] (code 74 ; verrouillée 75) : 2026-10-03
- Quiz embarqué : toutes les questions (217 494), réservables par question
- fix(quiz): compact fixed-width level cards in a wrapped D-pad grid; only questions are reservable

### [0.14.23-beta] (code 72 ; verrouillée 73) : 2026-10-03
- sélecteur de niveaux du Quiz fondé sur le contenu réel
- Incrémentation TV 0.14.23-beta (code 72) pour la variante verrouillée (code 73).
- feat(quiz): every free level is filled in the TV level picker (review questions playable, SIL and Form 4 aliases)

### [0.14.22-beta] (code 70 ; verrouillée 71) : 2026-10-03
- accueil et Quiz fluides, signalétique, Quiz embarqué
- détail : `git log tv-0.14.21-beta..tv-0.14.22-beta`

### [0.14.21-beta] (code 68 ; verrouillée 69) : 2026-10-02
- progression de réception, lecture prioritaire pendant une copie, clé d'installation des locations, bouton lots Langues
- détail : `git log tv-0.14.20-beta..tv-0.14.21-beta`

### [0.14.20-beta] (code 66 ; verrouillée 67) : 2026-10-02
- receive-progress fix
- détail : `git log tv-0.14.19-beta..tv-0.14.20-beta`

### [0.14.19-beta] (code 64 ; verrouillée 65) : 2026-10-02
- Version bumps: TV 0.14.19-beta (code 64→locked 65), phone 1.2.31-beta (code 61)
- feat(tv): rangement réel par catégorie à la réception (Films, Séries/Titre/Saison, Musique…), index des noms d'origine, « Ranger ma bibliothèque » (plan puis application)
- détail : `git log tv-0.14.18-beta..tv-0.14.19-beta`

### [0.14.18-beta] (code 62 ; verrouillée 63) : 2026-10-02
- chore(langues): générateur des packs A0 pilotes (gen_a0_pilot.py)
- détail : `git log tv-0.14.17-beta..tv-0.14.18-beta`

### Numéros sans commit / sans étiquette
Les numéros **0.14.12 et 0.14.13** n'ont jamais été posés dans `version.properties` par un commit (leurs codes exacts ne sont pas prouvables : ils sont entre 51 et 53) : `0.14.12-test` est un build d'essai documenté dans `docs/HANDOFF.md`, à ne jamais publier. Le saut de 0.14.11 (code 49) à 0.14.14 (code 54) est donc normal. Les numéros **0.13.4 à 0.14.5** (et téléphone **1.2.6 à 1.2.18**) correspondent à la vague de fusions du 2026-10-01 : aucun commit ne les nomme.

Versions sans étiquette : TV 0.14.35 à 0.14.45 ; téléphone 1.2.32, 1.2.33 et 1.2.44 à 1.2.52 : voir `git log -p -- version.properties` et docs/HANDOFF.md § 0.

### [0.14.17-beta] (code 60 ; verrouillée 0.14.17-beta-verrouillee, code 61) : 2026-10-02
Étiquette `tv-0.14.17-beta` sur `faf8636`.
- Ajouté : client TV du tunnel d'assistance à distance (écran des conditions, enrôlement, SSH inverse direct ou par la passerelle du téléphone, liste d'experts) ; brouillon `CONDITIONS-ASSISTANCE-A-DISTANCE`.
- Modifié : aria2 désactivé dans l'édition d'essai.

### [0.14.16-beta] (code 58 ; verrouillée 59) : 2026-10-02
Étiquette `tv-0.14.16-beta` sur `ea0d28c`.
- Sécurité : correctifs d'audit (grâce en date absolue, fichiers d'état écrits de façon atomique, droits refusés par défaut, retour d'horloge détecté avec temps monotone, plafond d'essai implicite, liste blanche de routes en essai, BT/USB/SSH fermés en essai).
- Modifié : clé de production = durée seule, licence automatique, aucun droit obligatoire ; import du catalogue de lots signé depuis le serveur ; tunnel d'administration à distance (côté serveur, désactivé par défaut) et signature de la liste d'experts.

### [0.14.15-beta] (code 56 ; verrouillée 57) : 2026-10-02
Étiquette `tv-0.14.15-beta` sur `1c26a11`.
- Ajouté : politiques de production dans les outils Propriétaire (durée de clé au choix, durée de location fixée par le serveur par lot, 30 jours par défaut, lots choisis dans le catalogue) ; passage essai vers production depuis l'application téléphone ; contenu de base pour chaque classe et matière (mode classe).

### [0.14.14-beta] (code 54 ; verrouillée 55) : 2026-10-02
Étiquette `tv-0.14.14-beta` sur `17a79f2` (regroupe aussi les commits intermédiaires depuis 0.14.11, numéros 0.14.12 et 0.14.13 jamais commités).
- Ajouté : droit de plafond d'usage (`usage|duree|from|to`), fenêtre unique de 12 h pour les lots d'essai, restrictions de l'édition d'essai (streaming et Sudoku seulement), badge de clé permanent sur chaque écran, écran de passage essai vers production, verrouillage à l'expiration ; module Langues sur la TV (tuile, consommateur, démarrage) ; locations de lots livrées depuis le téléphone ; bandeau de la page d'administration de la TV ; contenu nursery, tle-a, droit-l3 (+ lots).
- Ajouté (commits intermédiaires) : locations branchées sur la TV (coffre, grand livre, balayage `RentalHub`, routes `/api/rental`, `/api/activation/install` par PIN seul et `/request`, toutes les activations acceptées conservées) ; consommateur Apprendre enregistré dans le magasin de lots, outillage de test de location prouvé sur émulateur ; champ du code d'appareil de la console qui garde le curseur.
- Corrigé : deux figures de leçon ramenées sous le budget de 8 Ko ; 7 roues Python (75 Mo) retirées des sources Kotlin.

### [0.14.11-beta] (code 49 ; verrouillée 50) : 2026-10-02
Étiquette `tv-0.14.11-beta` sur `e40173a`.
- Ajouté : `SUPER_UNLIMITED` (la clé du super administrateur signe le droit `super` : lit et déverrouille tout, locations permanentes ; les comptes temporaires et illimités continuent d'expirer et de supprimer les locations), miroirs Java et Python.
- Fusionné : `naming-patterns`, `rental-lots` (les locations gardent leurs dates, fenêtre d'installation toujours 48 h), `media-pipeline`, `deferred-orders`, `activation-tools`.

### [0.14.10-beta] (code 47 ; verrouillée 48) : 2026-10-01
Étiquette `tv-0.14.10-beta` sur `ce052c8`.
- Modifié : l'accueil de la TV montre ce qui arrive même quand le nom existe déjà complet ; serveur aligné sur le format commun `cbx1` (fenêtre de 48 h en heures, pas de licence permanente par la clé serveur).

### [0.14.9-beta] (code 45 ; verrouillée 46) : 2026-10-01
Étiquette `tv-0.14.9-beta` sur `88b3035`.
- Modifié : licence d'usage permanente (achat du lot « tout », `ISSUE_UNLIMITED` seulement) remplace la fenêtre d'installation illimitée ; fenêtre de 48 h pour tout le monde ; vecteurs et vérificateur Python à jour.

### [0.14.8-beta] (code 43 ; verrouillée 44) : 2026-10-01
Étiquette `tv-0.14.8-beta` sur `e6825e7`.
- Modifié : codes d'activation avec fenêtre d'installation de 48 h depuis la création (appliquée par l'émetteur et la TV), clés illimitées réservées au super administrateur, clé compacte v2 en heures ; vecteurs régénérés, recoupement Python 125/125.

### [0.14.7-beta] (code 41 ; verrouillée 42) : 2026-10-01
Étiquette `tv-0.14.7-beta` sur `86ad3f2`.
- Corrigé : l'écran d'activation demande toutes les permissions Bluetooth (annonce) pour que les services démarrent.

### [0.14.6-beta] (code 39 ; verrouillée 40) : 2026-10-01
Étiquette `tv-0.14.6-beta` sur `0ff64ed` (première version de `version.properties`).
- Ajouté : source unique des versions ; variante verrouillée (code +1, suffixe `-verrouillee`).
- Contenu cumulé depuis 0.13.2 (fusions du 2026-10-01, versions intermédiaires non nommées) : verrou d'activation de la TV (interrupteur de compilation, clés publiques de confiance hors dépôt, grâce pour les installations mises à jour, écran d'activation avec fichier USB ou clé saisie) ; canal Bluetooth propriétaire (la TV reçoit les demandes d'appareil et accepte les activations même verrouillée ; la clé est seulement « posée » et le propriétaire valide) ; `GET /api/activation` ; écran d'activation affichant la version et l'état du canal ; entrée super administrateur locale (barrière bcrypt injectée à la compilation hors dépôt, essais limités, geste caché) ; CastBridge Dev (SSH par clé seule + `cbdev install/uninstall/start/status`) ; lots, télécommande intelligente, animations réseau, onglet parental, validation bêta, télécommande Bluetooth seule, tunnel Bluetooth, enveloppe générique ; contenus Apprendre et Quiz (primaire, collège, lycée, anglophone, technique/supérieur) ; clé USB (marque, modèle, série, débit) dans les écrans de stockage ; classeur de séries (Titre/Saison).

### [0.13.x] : 2026-10-01
Numéros cités par les sujets de commits (aucun n'est dans `version.properties`, pas de tag proposé sauf accord) :
- 0.13.0 (code 24), `2d6446f` : tout le lot charte graphique, Jeux/Sudoku, Bluetooth plug and play, contrôle parental, quiz 300 parties + packs, assistant de bibliothèque (dossiers TV, corbeille), adresse serveur masquée. Publiée sur la clé USB, installée par le propriétaire.
- 0.13.1 (code 25), `a3bfc56` : badge du nombre de connexions SSH en haut à droite.
- 0.13.2 (code 26), `ddcefa1` : accès à l'écran d'accessibilité de CVTE LiteSettings depuis l'aide de la télécommande.
- 0.13.3 (code 27) : Bluetooth plug and play robuste (branche `claude/plug-and-play-robust`, commit `21af017`, fusionnée par `073f3c7`).

### Versions antérieures (citées par les sujets de commits, 2026-09-30 et 2026-10-01)
0.4.2 et 0.4.3 (passerelle Internet Bluetooth, diagnostics), 0.7 (échecs, téléchargements aria2), 0.7.1 (lecteur du téléphone), 0.8 (télécommande du téléphone), 0.9 (Apprendre), 0.9.1 (code 19), 0.11 (code 20, chemin de mise à jour propre), 0.11.1 (code 21, correctif JSON de `/api/library`), 0.12.0 (code 22), 0.12.1 (code 23, adresse serveur officielle masquée).

## CastBridge (téléphone)

### [1.2.60-beta] (code 90) : 2026-10-19
- la ligne de la file (« Copie impossible : il n'y a plus assez de place sur la TV. … ») et la notification « n'est pas parti » (déployée) portent le texte entier du refus de la TV pour manque de place, avec les phrases par volume (R-55), à la place du seul conseil « Libérez de la place sur la TV » ; la ligne rouge de la carte d'envoi et celle du contrôle préalable montraient déjà le message entier
- vérifié en JVM (`UploadFailureTest` : 3 tests ajoutés) et par la compilation de `:sender` ; à confirmer avec une CastBridge-TV qui envoie les phrases par volume : parcours P-99

### [1.2.59-beta] (code 89) : 2026-10-18
- la copie d'un gros fichier ne repart plus en Bluetooth quand le Wi-Fi de la TV est revenu (R-54, « la reprise de la copie est lente bien que la disponibilité d'un réseau wifi soit effective ») : au lancement, sans route Wi-Fi ni Wi-Fi Direct, la file attend le Wi-Fi de la TV (45 s si elle l'a vu il y a moins de 10 min, sinon 15 s ; aucune attente sous 5 Mo) avant le Bluetooth ; pendant un envoi Bluetooth elle surveille le lien et, dès que la route Wi-Fi est stable depuis 3 s et que la TV peut reprendre la copie, arrête le Bluetooth et repart en Wi-Fi au même pourcentage (2 fois au plus par fichier, pas sous 1 Mo restant) ; la notification dit « Envoi Bluetooth de X · 59 % · passera en Wi-Fi dès son retour »
- vérifié en JVM (`AutoWifiDirectTest`, `WifiFirstQueueSourceTest`) et par la compilation de `:sender` ; à confirmer sur appareil : parcours P-97. Limite connue : une copie commencée en Bluetooth reste en Bluetooth (la réception Bluetooth de la TV n'écrit pas le `.meta` que le Wi-Fi exige pour reprendre, `docs/TRANSFER.md` § 13). Pas encore construit ni versionné

### [1.2.58-beta] (code 88) : 2026-10-15
- notifications (R-52, « les copies ne signalent plus en notification ») : quand Android jette les notifications de CastBridge (permission `POST_NOTIFICATIONS` absente ou refusée, ou un canal de copie coupé dans les réglages), un texte en rouge et un bouton « Autoriser » s'affichent en tête de la carte « Envoi vers la TV » et de la file d'attente de l'accueil (une seule fois à l'écran) : « Notifications désactivées pour CastBridge : la progression des copies n'apparaît pas dans la barre. », ou « Canal « Envoi vers la TV » désactivé : … » en nommant les canaux coupés ; rien n'est affiché quand tout va bien
- « Autoriser » demande la permission depuis l'accueil : le dialogue d'Android pour un téléphone jamais interrogé (la demande ne vivait plus que dans l'onglet « TV DLNA » depuis l'ergonomie du 2026-10-03, qui a fait de l'accueil l'écran de départ), sinon le réglage des notifications de CastBridge (repli : détails de l'application) ; l'état est relu au retour des réglages ; un refus est retenu, y compris celui de l'onglet « TV DLNA »
- R-51 (copie interrompue annulable depuis la TV) : l'envoi du téléphone n'est pas modifié ; la 1.2.58 embarque seulement le cœur partagé, et lit comme avant le texte de la TV sur un refus 400 ou 409 (limite connue, non mesurée : un gros envoi en un seul flux peut ne montrer que « liaison perdue » pendant les 10 minutes de refus, `docs/TRANSFER.md` § 12)
- vérifié en JVM seulement : 36 tests de règles pures et de gardes de source (`NotificationAccessPolicyTest`, `NotificationHintTest`, `NotificationAccessSourceTest`, `NotificationHintSourceTest`) et 16 mutations tuées sur 16 ; NON ESSAYÉ sur le S21+ ni sur la TV : parcours P-94 à jouer (R-52 reste « à confirmer sur appareil »)
- détail : `git log phone-1.2.57-beta..phone-1.2.58-beta`

### [1.2.57-beta] (code 87) : 2026-10-15
- « Activer la TV » lit `grace` et `graceUntil` dans `GET /api/activation` (`ActivationScreenState.Kind.GRACE`) : une TV en période de grâce s'affiche « TV en période de grâce : activation à fournir », avec « Jusqu'au JJ/MM/AAAA (restant : N jours) » et ce qu'il reste à faire, au lieu de « Cette TV est en version d'essai » ; une TV qui ne dit pas `grace` (CastBridge-TV 0.14.49 et avant) se lit comme avant
- console du propriétaire (variante superadmin) : l'avis « Cette TV n'envoie pas sa clé de signature d'installation » nomme les vraies versions concernées (TV d'avant 0.14.37, ou 0.14.47 et moins encore verrouillée) et demande la mise à jour en 0.14.48 ou plus, au lieu de « 0.14.37 ou plus » (`ConsoleInstallKey`, corrigé avec la TV 0.14.48)
- commentaire de code d'`ActivateTvActivity` : l'ordre réel des quatre voies (réseau local 10 s, Bluetooth sans appairage 25 s, réseau de la TV 50 s, Bluetooth appairé 20 s, ou 90 s si un appairage est à valider)
- détail : `git log phone-1.2.56-beta..phone-1.2.57-beta`

### [1.2.56-beta] (code 86) : 2026-10-09
- « Lire » et « Copier et lire » ouvrent CastBridge-TV si elle n'est pas devant
- Téléphone 1.2.56-beta (code 86)
- détail : `git log phone-1.2.55-beta..phone-1.2.56-beta`

### [1.2.55-beta] (code 85) : 2026-10-07
- partage de la demande d'appareil en texte seul (WhatsApp), QR séparé
- CastBridge 1.2.55-beta (code 85) : partage de la demande d'appareil corrigé (WhatsApp) ; gradle-lock.sh exécutable
- act-fix-2 : « Partager la demande » envoie le texte seul, « Partager le code QR » est un bouton à part (R-50, WhatsApp abandonne le texte d'un partage image)
- détail : `git log phone-1.2.54-beta..phone-1.2.55-beta`

### [1.2.54-beta] (code 84) : 2026-10-07
- voie Bluetooth sans appairage dans Activer la TV, I-4
- CastBridge-TV 0.14.47-beta (code 120 ; verrouillée 121) et CastBridge 1.2.54-beta (code 84) : Bluetooth sans appairage authentifié par le code, I-4
- act-bt : activer la TV par Bluetooth SANS appairage, authentifié par le code (CPace, AES-GCM) ; I-4 / R-49

### [1.2.53-beta] (code 83) : 2026-10-07
- Activer la TV par le seul code, Ouvrir sur la TV, relais automatique, correctifs d'audit
- détail : `git log phone-1.2.43-beta..phone-1.2.53-beta`

### [1.2.43-beta] (code 73) : 2026-10-04
- refus expliqués, demande d'appareil, échecs de copie, logo
- détail : `git log phone-1.2.42-beta..phone-1.2.43-beta`

### [1.2.42-beta] (code 72) : 2026-10-04
- saisie directe du code PIN
- détail : `git log phone-1.2.41-beta..phone-1.2.42-beta`

### [1.2.41-beta] (code 71) : 2026-10-04
- détail : `git log phone-1.2.40-beta..phone-1.2.41-beta`

### [1.2.40-beta] (code 70) : 2026-10-03
- fix(transfert): audit R-17 : bloc interrompu = 503, réponse anticipée lue, détecteur par catégorie et par octets, vidage borné et assaini
- détail : `git log phone-1.2.39-beta..phone-1.2.40-beta`

### [1.2.39-beta] (code 69) : 2026-10-03
- détail : `git log phone-1.2.38-beta..phone-1.2.39-beta`

### [1.2.38-beta] (code 68) : 2026-10-03
- code PIN mémorisé par TV, bascule Bluetooth, signalétique, ergonomie
- détail : `git log phone-1.2.37-beta..phone-1.2.38-beta`

### [1.2.37-beta] (code 67) : 2026-10-03
- Copier et lire pendant la copie, file d'attente de copie unique et persistante (R-08, R-09), envoi des lots de Langues par lot (R-07)
- bump(version): CastBridge phone 1.2.37-beta (versionCode 67)
- fix(file-copie): réserves de la seconde relecture R-09 (réservation périmée, limite de durée, annulation par génération)
- détail : `git log phone-1.2.36-beta..phone-1.2.37-beta`

### [1.2.36-beta] (code 66) : 2026-10-03
- Copier et lire, la TV démarre la lecture pendant la copie (copie ordonnée)
- chore(versions): téléphone 1.2.36-beta (66)
- fix(copier-et-lire): la TV démarre la lecture pendant la copie (R-08)

### [1.2.35-beta] (code 65) : 2026-10-02
- bouton Envoyer à la TV par lot de Langues, Télécharger et envoyer, lots téléchargés à la main livrés à la TV
- chore(versions): téléphone 1.2.35-beta (65)
- fix(langues): envoyer à la TV un lot téléchargé à la main (R-07) : lots épinglés livrés par le bouton global, bouton par carte, « Télécharger et envoyer »

### [1.2.34-beta] (code 64) : 2026-10-02
- Copier sur la TV et lire (Ouvrir avec), clés de PIN tolérantes, lecture prioritaire pendant une copie
- détail : `git log phone-1.2.31-beta..phone-1.2.34-beta`

### [1.2.31-beta] (code 61) : 2026-10-02
- Version bumps: TV 0.14.19-beta (code 64→locked 65), phone 1.2.31-beta (code 61)
- feat(tv): rangement réel par catégorie à la réception (Films, Séries/Titre/Saison, Musique…), index des noms d'origine, « Ranger ma bibliothèque » (plan puis application)
- détail : `git log phone-1.2.30-beta..phone-1.2.31-beta`

### [1.2.30-beta] (code 60) : 2026-10-02
- chore(langues): générateur des packs A0 pilotes (gen_a0_pilot.py)
- détail : `git log phone-1.2.29-beta..phone-1.2.30-beta`

### [1.2.29-beta] (code 59) : 2026-10-02
Étiquette `phone-1.2.29-beta` sur `faf8636`. Synchronisée avec le tunnel d'assistance à distance de la TV 0.14.17 (passerelle du téléphone).

- détail : `git log phone-1.2.28-beta..phone-1.2.29-beta`

### [1.2.28-beta] (code 58) : 2026-10-02
Étiquette `phone-1.2.28-beta` sur `ea0d28c`. Envoi de la clé par Wi-Fi ; import du catalogue de lots signé ; correctifs d'audit.

- détail : `git log phone-1.2.27-beta..phone-1.2.28-beta`

### [1.2.27-beta] (code 57) : 2026-10-02
Étiquette `phone-1.2.27-beta` sur `1c26a11`. Passage essai vers production depuis l'application ; politiques de production.

- détail : `git log phone-1.2.26-beta..phone-1.2.27-beta`

### [1.2.26-beta] (code 56) : 2026-10-02
Étiquette `phone-1.2.26-beta` sur `17a79f2` (le code saute de 35 à 56 : builds intermédiaires jamais commités). Planification Langues ; livraison des lots loués depuis le téléphone.

- détail : `git log phone-1.2.25-beta..phone-1.2.26-beta`

### [1.2.25-beta] (code 35) : 2026-10-02
Étiquette `phone-1.2.25-beta` sur `b92998e`. Ne copie jamais un fichier que la TV détient déjà complet (même nom et même taille).

- détail : `git log phone-1.2.24-beta..phone-1.2.25-beta`

### [1.2.24-beta] (code 34) : 2026-10-02
Étiquette `phone-1.2.24-beta` sur `e40173a`. `SUPER_UNLIMITED` (voir TV 0.14.11).

- détail : `git log phone-1.2.23-beta..phone-1.2.24-beta`

### [1.2.23-beta] (code 33) : 2026-10-01
Étiquette `phone-1.2.23-beta` sur `ce052c8`. Bandeau de copie sous la barre du lecteur, pourcentage dans les notifications de transfert.

- détail : `git log phone-1.2.22-beta..phone-1.2.23-beta`

### [1.2.22-beta] (code 32) à [1.2.21-beta] (code 31) : 2026-10-01
Étiquettes `phone-1.2.22-beta` (`88b3035`) et `phone-1.2.21-beta` (`e6825e7`). Codes d'activation : fenêtre de 48 h, licence d'usage permanente (voir TV 0.14.8 et 0.14.9).

- détail : `git log phone-1.2.20-beta..phone-1.2.22-beta`

### [1.2.20-beta] (code 30) : 2026-10-01
Étiquette `phone-1.2.20-beta` sur `86ad3f2`. Récupération automatique d'une TV déjà appairée après une réinstallation.

- détail : `git log phone-1.2.19-beta..phone-1.2.20-beta`

### [1.2.19-beta] (code 29) : 2026-10-01
Étiquette `phone-1.2.19-beta` sur `0ff64ed`. « Activer la TV » montre l'état d'activation propre de la TV ; ouverture de l'écran « Activer la TV » (clé collée, TV appairée, envoi par Bluetooth) avec appairage automatique ; contenus et fonctions fusionnés le 2026-10-01 (file de copie ou déplacement en arrière-plan, sélecteur multi-fichiers, « Ouvrir avec », progression de copie pendant la diffusion, classeur de séries).

- détail : étiquette précédente absente (numéros antérieurs non étiquetés) : `git log phone-1.2.19-beta`

### [1.2.x] : 1.2-beta à 1.2.5-beta, 2026-10-01
Numéros cités par les sujets de commits, aucun dans `version.properties` : 1.2-beta (code 10, `2d6446f`), 1.2.1-beta (11, `a3bfc56`), 1.2.2-beta (12, `285f593` : carte « introuvable » stable et « Réassocier » en un geste), 1.2.3-beta (13, `23e9257` : écran parental avec le jeton du téléphone de confiance), 1.2.4-beta (14, `a6cc6b0` : correctif d'un plantage de la télécommande), 1.2.5-beta (15, branche `claude/plug-and-play-robust`).

- détail : aucune étiquette pour ces numéros : `git log -p -- version.properties`

### Versions antérieures
0.7 (échecs, téléchargements), 0.9 (télécommande), 1.0-beta (Apprendre), 1.1-beta (code 9), 1.1.1-beta (adresse serveur officielle masquée).

## CastBridge Propriétaire (console des activations)

### [0.2.4] (code 6) : 2026-10-02
- chore(langues): générateur des packs A0 pilotes (gen_a0_pilot.py)
- détail : `git log owner-0.2.3..owner-0.2.4`

### [0.2.3] (code 5) : 2026-10-02
Étiquette `owner-0.2.3` sur `faf8636`.

- détail : `git log owner-0.2.2..owner-0.2.3`

### [0.2.2] (code 4) : 2026-10-02
Étiquette `owner-0.2.2` sur `ea0d28c`. Clé de production = durée seule ; envoi de clé par Wi-Fi ; import du catalogue signé ; signature de la liste d'experts.

- détail : `git log owner-0.2.1..owner-0.2.2`

### [0.2.1] (code 3) : 2026-10-02
Étiquette `owner-0.2.1` sur `1c26a11`. Durée de clé au choix, durée de location fixée par le serveur par lot, lots choisis dans le catalogue.

- détail : `git log owner-0.2.0..owner-0.2.1`

### [0.2.0] (code 2) : 2026-10-02
Étiquette `owner-0.2.0` sur `e40173a`. Droit `SUPER_UNLIMITED`.

- détail : `git log owner-0.1.0..owner-0.2.0`

### [0.1.0] (code 1) : 2026-10-01
Étiquette `owner-0.1.0` sur `0ff64ed`. Console du téléphone propriétaire (coffre, garde de déverrouillage, verrouillage automatique, émission d'activation, clé publique, journal chaîné), outil `castbridge-owner` en ligne de commande, bibliothèque partagée `:ownerlib`, entrée super administrateur locale.

- détail : étiquette précédente absente (numéros antérieurs non étiquetés) : `git log owner-0.1.0`

## Serveur (`backend/`, pom 1.2.5)

### [1.2.5] (code 9) : 2026-10-07
Étiquette `server-1.2.5` ; en service depuis le 2026-10-07 (docs/HANDOFF.md § 0).
- **Adresses stables de téléchargement** : `GET|HEAD /dl/{app}/latest.apk` répond `302` vers `/dl/{app}/{fichier}` de la dernière version **stable, non retirée, à 100 % de déploiement** (plus haute `versionCode`) ; sans `abi`, une TV prend `armeabi-v7a`, puis `universal`, puis `arm64-v8a`, un téléphone `universal` ; `?abi=` choisit cette architecture puis `universal` ; `Cache-Control: no-store`, `X-Content-SHA256`, `X-CastBridge-Version` ; `404` en français sans version. Le nom `latest.apk` ne peut pas être un fichier publié (tous commencent par `castbridge-`) : la route `/dl/{app}/{fichier}` est inchangée.
- **Page publique `GET /telecharger`** (et `/telecharger/`) : « Télécharger CastBridge », version, date, taille, SHA-256, bouton et code QR (SVG en ligne, générateur `QrSvg` existant) pour CastBridge-TV et CastBridge, lignes d'installation sur la TV, lien vers `/guide/` ; HTML construit en Java sans Thymeleaf, aucun script ; même chaîne de sécurité, même CSP et même cache que `/guide/` ; ne révèle ni versions retirées ni canaux non stables.
- Limite de débit par IP étendue à `/telecharger`, et **correction d'un contournement qui existait déjà** pour `/api`, `/dl` et `/guide` : le filtre lisait l'URI brute, donc `/%64l/…`, `/%61pi/…` ou `/%67uide/` (une lettre encodée en `%XX`, que Spring décode et sert) passaient sans limite. Le chemin est lu décodé (`UrlPathHelper`), comme le voient les contrôleurs. Aucune route modifiée.
- Procédure : `tools/release/publish-apk.sh` (publication par une commande, jeton lu dans le conteneur ; notes avec espaces, apostrophes et `$` transmises intactes : `ssh` recolle ses arguments, le serveur coupait les notes au premier mot), `docs/RELEASES.md` § 8, `docs/API-SERVER.md` § 1.
- Tests : `DownloadLinksTest`, `DownloadLinksRateLimitTest`, `DownloadPageTest`, `QrSvgTest` (`backend/src/test/java/castbridge/server/updates/`).

### [1.2.4] (code 8) : 2026-10-07
- route publique /guide/ (guide utilisateur) ; révocation de poste datée >= émission (correctif)
- détail : `git log server-1.2.3..server-1.2.4`

### [1.2.3] (code 7) : 2026-10-06
- quotas de licence fermés
- serveur 1.2.3 : quotas de postes fermés (pas d'augmentation, un poste libéré ne passe pas à un autre appareil)

### [1.2.2] (code 6) : 2026-10-06
- V67 (plafond de transferts 0), chemins de transfert fermés, release des ordres sans interblocage
- serveur 1.2.2 : V67 plafond de transferts à 0, chemins de transfert fermés, OrderService.release sans interblocage
- Licences : transferts et réattribution de poste fermés (aucun poste ne change de TV)
- détail : `git log server-1.2.1..server-1.2.2`

### [1.2.1] (code 5) : 2026-10-05
- suivi des activations (V63 éteint), enregistrement automatique des licences (V64 à V66), interrupteur du registrar
- détail : `git log server-1.2.0..server-1.2.1`

### [1.2.0] (code 4) : 2026-10-04
- portefeuille V62
- détail : `git log server-1.1.1..server-1.2.0`

### [1.1.1] (code 3) : 2026-10-02
- chore(server): version 1.1.1 (support langues : LangLangValidator, outil de publication)
- fix(core): correctifs d'audit des fonctions pures d'état W14-05 (chemin de confiance par jeton, PinFallback, clés multi-IP, cible d'activation, file d'envois)
- détail : `git log server-1.1.0..server-1.1.1`

### [1.1.0] (code 2) : 2026-10-02
- licences, tunnel (éteint par défaut), catalogue de bouquets signé, archive libre publique, hygiène ; migrations V4 à V61 (additives)

### Jalons antérieurs aux versions numérotées (`server-<version>`, `docs/RELEASES.md` § 13)

Jalons tirés de l'historique : module de gestion des licences (désactivé par défaut) fusionné le 2026-10-01 puis aligné sur `cbx1` ; fenêtre d'installation de 48 h ; service d'ordres différés ; locations (règles de dates, vecteurs) ; correctif de l'adresse `/admin/` ; tunnel d'administration à distance côté serveur (désactivé par défaut) ; WireGuard ouvert sans pair. Le serveur de production n'a pas de révision traçable avant le premier déploiement par `tools/release/deploy-server.sh` (« révision initiale inconnue »).

## Service de jeu en ligne (`server-play`, castbridge-play)

### [0.1.1] : 2026-10-04
- castbridge-play 0.1.1 (staging privé, correctifs des audits)
- détail : `git log server-play-0.1.0..server-play-0.1.1`

### [0.1.0] : 2026-10-03
- castbridge-play 0.1.0 (staging privé)
