# w21-03 — Service de jeu `castbridge-play` : métriques agrégées sans donnée personnelle (`PlayMetrics`, `RoomObserver`, champ additif `hello.link`), fichier horaire borné dans le volume `play-state`
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (isolation du service, aucune adresse/identité/code dans les métriques, fichier borné) · statut : **ATTEND la fusion de w20-04b** (règle R2 W20 : un seul cahier à la fois sur `server-play/`)
> **Groupe : W21-A** (ordre 1) · prérequis : `integration/agents` contenant w20-04b · porte : `:server-play:test` complet + `:core:test --tests 'castbridge.core.quiz.online.*'` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 2 (M-33…M-44), § 5 (service isolé, option (d)). Branche `claude/w21-03-play-metrics`. Rapport : `docs/agent-reports/sonnet-w21-03.md`. Livraison : image `castbridge-play` **server-play-0.2.x** (acte du propriétaire).

## Objectif (autonome)
`castbridge-play` (`server-play/src/main/kotlin/castbridge/play/`) n'a ni base, ni secret, ni journal de requêtes ; il expose `/play/health` (réservé à 127.0.0.1 par nginx) et écrit dans un volume `play-state` (`/var/lib/castbridge-play`, `docs/PLAY-OPS.md` § 5). Le bloc nginx `location ^~ /play/` exposerait toute route nouvelle : **aucune route n'est ajoutée**. Livrer des métriques **agrégées par heure**, à dimensions fermées, écrites dans un fichier borné du volume, que le collecteur hôte (w21-03b) lira.

## Fichiers possédés
- **Nouveaux** : `server-play/src/main/kotlin/castbridge/play/metrics/{PlayMetrics,MetricsFile,MetricKeys}.kt` ; `android/core/src/main/kotlin/castbridge/core/quiz/online/RoomObserver.kt` (interface pure, implémentation nulle par défaut) ; tests `server-play/src/test/kotlin/castbridge/play/metrics/*Test.kt`, `android/core/src/test/kotlin/castbridge/core/quiz/online/RoomObserverTest.kt`.
- **Zone additive** : `ServerRoom.kt` (paramètre `observer: RoomObserver = RoomObserver.NONE` ; appels : résultat d'`ack` + classe, échantillon RTT, pénalité de compensation, pénalité relais, réponse acceptée pendant la grâce, `CLOSED` arrivé < 1 500 ms après clôture) ; `PlayProtocol.kt`/`PlayCodec.kt` (`hello.link` facultatif, ≤ 16 caractères, ramené à {`ethernet`,`wifi`,`hotspot`,`bt_gw:2g|3g|4g|5g|unk`,`unk`} ; inconnu ⇒ `unk` ; aucun refus) ; `RoomRegistry.kt` (refus `PLAY_*` par étape, plafonds atteints par nom, connexions simultanées par adresse **sans garder l'adresse**) ; `PlayWebSocketHandler.kt`/`PlayFallbackController.kt` (octets par type de message, file de sortie maximale, `state` remplacés par la coalescence de w20-04b, fermetures par code) ; `PlayServer.kt` (construction, tick : latence du tick ; tas/connexions/salles maxima) ; `PlayConfig.kt` (`CASTBRIDGE_PLAY_METRICS` = `on` défaut / `off`, `CASTBRIDGE_PLAY_METRICS_DIR` défaut `/var/lib/castbridge-play/metrics` ; ajoutés à `ENV_NAMES`) ; `backend/.env.play.example` (deux lignes commentées).
- **Interdit** : `backend/src/main/**`, `android/receiver|sender`, toute route HTTP nouvelle, toute dépendance, tout secret ou identifiant ; `PlayTiming`, `RttBook` (constantes : w21-08).

## Étapes
1. **Rouge** (sortie collée) : `PlayMetricsTest.closedHourIsWrittenWithoutIdentity`.
2. `MetricKeys` : liste fermée des métriques et dimensions (transport `ws|sse|longpoll`, `link` du `hello`, `PLAY_*`, étapes `ticket|create|join|resume`, résultats d'`ack`, types de message, joueurs 1-8, codes de fermeture, noms de plafonds du § 2 M-39) ; toute clé hors liste ⇒ comptée sous `other` (jamais une chaîne venue du client).
3. `PlayMetrics` (pur, fil-sûr, horloge injectée) : compteurs, sommes, maxima et histogrammes à bornes fixes (bornes LAT et autres : copie des tableaux de `C/telemetry/tech/TechBuckets.kt` **si** w21-01 est fusionné, sinon constantes locales identiques + test de parité marqué à activer) ; mémoire bornée (≤ 2 000 clés vivantes par heure ; au-delà, `overflow` compté) ; à chaque heure close ⇒ instantané immuable.
4. `MetricsFile` : écrit `m-<AAAAMMJJHH>-<instance>.json` (`instance` = 8 hexadécimaux tirés au démarrage) par fichier temporaire + renommage atomique, ≤ 64 Ko (au-delà : histogrammes P2 retirés d'abord, puis `truncated:true`) ; garde 72 fichiers au plus (les plus anciens supprimés) ; un échec d'écriture est compté (`write_fail`) et dit par `LogRedactor` (sans chemin complet ni contenu), jamais fatal ; `CASTBRIDGE_PLAY_METRICS=off` ⇒ rien n'est écrit, rien n'est compté.
5. Format du fichier : `{"v":1,"instance":"…","hour":"2026-10-05T14:00Z","version":"…","metrics":[{"m":"srv.rtt","d1":"sse","d2":"bt_gw:2g","cnt":…,"sum":…,"max":…,"b":[…]}]}` ; **aucun** champ `ip`, `code`, `roomId`, `name`, `device`, `identity`, `token`.
6. Câblage dans les fichiers de la zone additive (appels de comptage seulement ; aucun changement de comportement : les tests existants restent verts **sans** modification).
7. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `PlayMetricsTest.closedHourIsWrittenWithoutIdentity` : une partie de bout en bout en boucle locale (fixtures existantes : `HubFixture`/`PlayLoopbackTest`) puis avance d'horloge d'une heure ⇒ un fichier ; il contient `srv.rtt`, `srv.ack`, `srv.bytes` (dont `state`), `srv.state_sz` à 2 joueurs ; **aucune** sous-chaîne égale au code de salle, au `roomId`, au pseudonyme, à l'adresse 127.0.0.1 de la fixture, au jeton (mutation : ajouter le `roomId` en dimension ⇒ échec).
- `MetricKeysTest` : `hello.link` = « `<script>` » ⇒ dimension `unk` ; 10 000 valeurs distinctes ⇒ ≤ 2 000 clés + `overflow`.
- `MetricsFileTest` : 100 heures ⇒ 72 fichiers ; fichier > 64 Ko impossible (mutation : retirer la borne ⇒ échec) ; dossier non inscriptible ⇒ `write_fail` + service vivant ; `off` ⇒ 0 fichier.
- `RoomObserverTest` : une réponse `TOO_EARLY`, une `CLOSED` tardive, une pénalité relais à RTT TV 900 ms ⇒ observées une fois chacune ; `ServerRoom` sans observateur ⇒ comportement identique (tests existants verts).
- `NoSecretsTest`/`ProductionDocsTest` existants verts et étendus : `ENV_NAMES` contient les deux variables, aucune nommée `*KEY*` ni `*TOKEN*` nouvelle.
- Charge : `PlayMetrics` sous 3 000 connexions simulées et 400 salles (test existant de charge si présent) : surcoût < 5 % du temps du tick (mesure consignée).

## Interdits
Aucune route, aucun réseau sortant, aucune base, aucun secret ; ne pas toucher au comportement de jeu ; ne pas lire l'environnement en dehors de `PlayConfig`.

## Rapport
Rouge, vert, mutations, exemple de fichier (anonymisé de fait), taille maximale observée, surcoût du tick, liste finale des clés.
