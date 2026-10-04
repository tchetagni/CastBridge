# Vague 20 — Quiz en ligne : trois périmètres, signe « Partie sûre », passerelle `castbridge-play` sur le port 443

<!-- routage Fable 2026-10-03 -->

**Source** : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` (Fable, architecte). Branche de référence `integration/agents` (HEAD `145c9e2d`). Exécution sur ordre du coordinateur seulement.

**Exigence du propriétaire (2026-10-03, verbatim)** : « On doit pouvoir être capable de jouer sur internet quand la TV est connectée avec un signe que la partie est sûre : TV seule, en réseau local, depuis internet. Le serveur doit avoir un port approprié pour la mise en œuvre de parties en ligne sur internet. C'est un enjeu commercial fort de pouvoir jouer sur internet. »

## Amendement 2026-10-04 — TV seulement, POC d'abord

**Conception** : `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (l'emporte sur DESIGN-W20 là où ils divergent). **Décisions du propriétaire (2026-10-04, verbatim)** : « Les parties libres sont exclusivement sur la TV, rien ne se fait en dehors de la TV ou d'une appli phone synchronisée à une TV activée » ; « Je cherche juste à avoir un POC partie en ligne pendant le lancement » ; « Après le lancement, seules les TV pourront toujours faire les quiz et les téléphones synchronisés, car un téléphone synchronisé, par internet = TV à laquelle il est connecté » ; « Pas de délégation en phase 2. Seule une TV connectée à internet joue. Le pire des modes de connexion est la passerelle bluetooth » ; « La liaison passerelle se fera au pire en EDGE, sinon 3G, 4G ou 5G » ; débit de référence fixé à 40 kbps.

**Règles ajoutées** : **R8** le seul client du service est une CastBridge-TV activée connectée à Internet (ticket `cbp1` + activation `cbx1` pour créer **et** pour rejoindre) ; aucun téléphone, navigateur ou client anonyme ne se connecte au service ; les téléphones sont des joueurs locaux **relayés** par leur TV. **R9** aucune page web de jeu, jamais (`/play` = page d'information). **R10** liaison de référence = EDGE par la passerelle Bluetooth, 40 kbps : critère « Duel complet à 8 joueurs relayés fluide à 40 kbps (question ≤ 1,5 s, révélation/classement ≤ 2 s, ack ≤ 1,5 s) ». **R11** pas de délégation ni de voucher de téléphone.

**Chemin du POC (phase 1, prioritaire)** : `w20-04b` ∥ `w20-05a` → `w20-05` réduit « POC » (exception de gel derrière `quiz.online`, D-AM-1) → actes du propriétaire (clé de ticket, API avec la route de ticket, image `castbridge-play` avec `CASTBRIDGE_PLAY_WEB=0`, route nginx `/play/`, clé d'émetteur, révocations `off` pour le POC) + relevé `H-PLAY-BT` → démonstration de 10 min (2 TV, 2 téléphones). ≈ 4,5 agent·jours, ≈ 6 $ (estimé). w20-06, 09, 10, 11, 13 : **hors** chemin du POC.

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w20-04b | `sonnet-w20-04b-poc-join-tv-seulement-relais.md` | join réservé aux TV activées, `CASTBRIDGE_PLAY_WEB=0`, page d'information, siège relais multi-TV (`relayedBy`), révocations explicites (`off` POC, trou `DIRECT` fermé), coalescence « dernier état » | cœur + service | M | sonnet | **oui** | 400 / 20 | **PRÊT (POC, ordre 1)** | — |
| w20-05a | `sonnet-w20-05a-poc-client-tv-coeur.md` | cœur client TV : `PlayHttpTransport` (SSE + POST, proxy de la passerelle), `PlayTvSession`, `RelayAuthority`, `LinkCause`, `QuizHttp` sur autorité, simulateur EDGE 40 kbps | cœur | M | sonnet | échantillon | 400 / 20 | **FAIT (cœur, claude/w20-05a-poc-client-core)** : rapport `docs/agent-reports/sonnet-w20-05a.md` ; critère de fluidité : question, révélation, classement tenus, ack ≤ 1,5 s NON tenu (mesures) ; tests à deux TV en attente de 04b | test final après 04b |
| w20-05 (POC) | `sonnet-w20-05-tv-wiring-play.md` (amendé) | écrans TV minimum : Créer / Rejoindre / code / bandeau / relais, derrière `quiz.online` | **exception de gel** | M | sonnet | **oui** | 400 / 20 | ATTEND D-AM-1 (POC, ordre 2) | 04b, 05a |
| w20-14 | `sonnet-w20-14-join-voucher-tv-synced.md` | délégation de siège téléphone / voucher `cbj1` | — | — | — | — | — | **RETIRÉ** (non retenu par décision du propriétaire 2026-10-04) | — |

## Faits (vérifiés le 2026-10-03)
- Le Quiz tourne sur le serveur de la TV (port 8765, `/quiz/*`, code 4 chiffres, jeton 128 bits, SSE + long-poll, 8 joueurs, bonne réponse jamais avant clôture) : `docs/QUIZ.md` § 3-4, `C/quiz/QuizRoom.kt`, `C/quiz/QuizHttp.kt`.
- `castbridge-api` : Java/Maven, Spring Boot 3.5.16, **sans** WebSocket ni SSE ; `127.0.0.1:7090` → 8080 ; actuator 8081 non publié ; nginx partagé (`infra-nginx`, certbot) **hors dépôt** ; VM 7,8 Go (≈ 4,6 Go disponibles), vCPU inconnus.
- Droits : `TvGate`/`Entitlements`/`RentalLines` dans `:core` (Kotlin) ; l'API atteste l'appareil (`deviceToken`), ne connaît pas `:core` ; activation `cbx1` déjà acceptée comme preuve par `/api/v1/tunnel/enroll`.
- Contenu : 217 494 questions, 93 lots, 30 % réservables par question ; 68 lots publiés aujourd'hui **tout en libre** ; gel des `reserved-ids` et paquets réservés **à faire** (`docs/agent-reports/quiz-toutes-les-questions.md`).
- `TrialPolicy` ferme le Quiz en essai ; `QuizEdition.TRIAL_OPEN = true` : contradiction (D-W20-5).
- Juridique reporté au 2026-12-31 : jetons virtuels seulement, aucun prix.

## Règles W20
- **R1 (W15)** : chaque cahier commence par un test rouge sur `integration/agents`, sortie collée.
- **R2** : un seul cahier à la fois sur `server-play/` jusqu'à la fusion de w20-03 ; un seul sur `backend/src/main/java/castbridge/server/play/` (w20-04) ; **aucun** cahier W20 ne touche `C/tv/ReceiverServer.kt`, `R/TvService.kt`, `C/owner/**`, `C/lots/**`, `B/licenses/**`.
- **R3** : `TV_ONLY` et `LAN` **n'ajoutent aucun appel réseau** (preuve par `ProxySelector` espion) ; le hors-ligne ne se dégrade jamais.
- **R4** : aucun secret privé dans `server-play/` ; le PIN (`docs/ADMIN.md`) n'apparaît jamais dans le protocole `play`.
- **R5** : aucun exécutant ne se connecte au serveur de production (`ssh`, `docker`, `--apply`) ni à un appareil ; w20-08 produit des runbooks **DRY-RUN**.
- **R6** : règle de symbiose W19 (additif, `caps`, `Reason`, deux écrans, ligne `SYMBIOSE:`), plus la règle « périmètre » (w20-12).
- **R7** : audits Opus **obligatoires** sur w20-03, w20-04, w20-05, w20-07 ; échantillon ailleurs.

## Modèle d'exécution
haiku pour les docs ; sonnet pour tout le reste ; Opus en audit seulement. Jauges : S ≈ 150 k / 8 k, M ≈ 400 k / 20 k, L ≈ 800 k / 40 k jetons (entrée / sortie), **estimées, non vérifiées**.

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w20-01 | `sonnet-w20-01-scope-safety-sign-authority.md` | `PlayScope`, `SafetySign`, `GameAuthority`/`LocalAuthority`, `PLAY_*` | cœur pur | M | sonnet | échantillon | 400 / 20 | FAIT (branche `claude/w20-01-scope-signe-autorite`, + `PlayTiming` 1,5 s) | — |
| w20-02 | `sonnet-w20-02-play-protocol-server-room.md` | protocole `play-v1`, `ServerRoom`, `RoomCode`, `RttBook`, `ServerAuthority` (purs) | cœur pur | L | sonnet | échantillon | 800 / 40 | FAIT (branche `claude/w20-02-protocole-salle`, + délai inter-questions 1,5 s dans `ServerRoom`) | 01 |
| w20-03 | `sonnet-w20-03-play-service-loopback.md` | service `server-play/` : WS + SSE + long-poll, page `/play`, `/play/health`, boucle locale | service + tests | L | sonnet | **oui** | 800 / 40 | FAIT (branche `claude/w20-03-service-play`, module `:server-play`, JDK seul sans Spring ; **audit Opus à faire**) | 02 |
| w20-04 | `sonnet-w20-04-entitlement-tickets.md` | ticket `cbp1`, `HostRights` (`cbx1` + locations ⇒ `TvAccess`), réservées à la demande, `PlayRules` | cœur + API + service | L | sonnet | **oui** | 800 / 40 | FAIT (branche `claude/w20-04-droits-tickets`, ticket `cbp1` à usage unique (I1), `HostRights`, `ReservedBank`, `PlayRules` ; **audit Opus à faire** ; gel `reserved-ids.json` et D-W20-5 non traités, voir le rapport) | 03 |
| w20-07 | `sonnet-w20-07-anticheat-limits.md` | `RttBook` câblé, grâce, `BotScore`, `Limits`, `Pseudonym`, fuzz de schéma, journaux | cœur + service | M | sonnet | **oui** | 400 / 20 | FAIT (branche `claude/w20-07-anti-triche` : `BotScore`, `Pseudonym`, `Limits`, `PlayRedact`, gardes `server-play/…/guard/` ; rapport `sonnet-w20-07.md` ; **audit Opus obligatoire à faire**) | 03 |
| w20-11 | `sonnet-w20-11-latency-chaos-leak-load.md` | `LatencySim`, chaos de reprise, fuite de réponse sur socket, charge, persona | tests | M | sonnet | non | 400 / 20 | PRÊT (après 03, 07) | 03, 07 |
| w20-09 | `sonnet-w20-09-lobbies-leaderboards.md` | salons publics, classements, schéma `castbridge_play`, rétention, `DELETE /play/me` | service | L | sonnet | échantillon | 800 / 40 | PRÊT (après 04, 07) — **amendé 2026-10-04** : TV de production seulement, classements par identité de TV + siège ; phase 2 | 04, 07, 04b |
| w20-10 | `sonnet-w20-10-moderation-privacy.md` | signalements, bannissements, admin minimal (jeton distinct), confidentialité, `QUIZ-EN-LIGNE.md` | service + docs | M | sonnet | échantillon | 400 / 20 | PRÊT (après 09) — **amendé 2026-10-04** : modération par identité de TV ; phase 2 | 09 |
| w20-08 | `sonnet-w20-08-ops-runbook-proxy-port-tls.md` | `PLAY-OPS.md` (nginx `/play/`, WebSocket, TLS, `ufw`, 7443), compose additif, SQL, `deploy-server.sh --service play` | outils + docs | S | sonnet | non | 150 / 8 | FAIT (branche `claude/w20-08-runbook-ops`, tout en DRY-RUN : `PLAY-OPS.md`, `docker-compose.play.yml` séparé, `.env.play.example`, `play-schema.sql`, `--service play`, `tools/tests/test_deploy_play.sh` ; **rien exécuté sur le serveur**, audit non requis) | 03 |
| w20-05 | `sonnet-w20-05-tv-wiring-play.md` | câblage CastBridge-TV : transport sortant, bandeau, Ouvrir/Fermer Internet, repli 60 s, relais local | **après le gel** | L | sonnet | **oui** | 800 / 40 | **AMENDÉ 2026-10-04** : réduit à « POC » (voir la table de l'amendement) ; le reste = phase 2 | 01-04, 07 |
| w20-06 | `sonnet-w20-06-phone-web-client.md` | page `/play` (pendant le gel) + onglet Quiz CastBridge, lien d'application, WebView | page : pendant ; `S/` : après | M | sonnet | échantillon | 400 / 20 | **AMENDÉ 2026-10-04** : page (A) **RETIRÉE** (aucune page web de jeu) ; app (B) sans objet en phases 1-2 | 03, 05 |
| w20-12 | `sonnet-w20-12-docs-quiz-en-ligne.md` | `QUIZ.md` § 10, `HANDOFF`, parcours P-55…58, gabarit « périmètre », régressions | docs | S | haiku | non | 150 / 8 | PRÊT (brouillon après 03) | 01-11 |
| w20-13 | `sonnet-w20-13-tables-classes-events.md` | V2 : tables multiples, salles de classe, tournois horaires, sponsor sans prix | service + `S/` | L | sonnet | échantillon | 800 / 40 | CONDITIONNEL (D-W20-6, 10) | 09 |

**Coûts** (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet L 7 × ≈ 2,0 $ = 14 $ ; sonnet M 4 × ≈ 1,0 $ = 4 $ ; sonnet S 1 × ≈ 0,4 $ ; haiku S 1 × ≈ 0,2 $ ; audits Opus obligatoires 4 × ≈ 0,8 $ = 3,2 $ ; échantillons Opus 6 × ≈ 0,4 $ = 2,4 $ ; reprises et questions ≈ 15 % ⇒ **Total ≈ 28-29 $**, ≈ **31 agent·jours** (w20-13 compris : sans lui ≈ 26 $, ≈ 28 j). **Pendant le gel** : 01, 02, 03, 04, 07, 11, 09, 10, 08, 06 (page) ≈ 23 j, ≈ 22 $.

## Fichiers possédés (disjoints)

| Cahier | Nouveaux | Zones additives |
|---|---|---|
| w20-01 | `C/quiz/online/{PlayScope,SafetySign,Authority,PlayReason}.kt`, tests, `safety-table.json` | `C/sync/Reason.kt` (fin d'enum, si présent) |
| w20-02 | `C/quiz/online/{PlayProtocol,PlayCodec,RoomCode,RttBook,ServerRoom,ServerAuthority,PlayTransport,EventRing}.kt`, tests, `docs/PLAY-PROTOCOL.md` | — |
| w20-03 | `server-play/**` (sauf zones des autres), `Dockerfile`, `README.md` | `android/settings.gradle.kts` (include) |
| w20-04 | `backend/src/main/java/castbridge/server/play/**`, `server-play/…/entitlement/**`, `C/quiz/online/PlayRules.kt`, fixtures | `backend/src/main/resources/application.yml`, `backend/docker-compose.yml` (secret déclaré), `docs/API-SERVER.md` |
| w20-07 | `C/quiz/online/{BotScore,Pseudonym,Limits,BadCodeCounter,PlayRedact}.kt`, `blocklist.txt`, `server-play/…/guard/**` | `PlayWebSocketHandler` (points d'appel), `RoomCode` (rotation) |
| w20-11 | `CT/quiz/online/{LatencySim,FairnessTest}.kt`, `server-play/src/test/…/chaos/**`, `seeds.txt` | `CT/compat/personas/` |
| w20-09 | `C/quiz/online/{Lobby,Board}.kt`, `server-play/…/{lobby,board,retention}/**`, `V1__play.sql` | `server-play/build.gradle.kts`, `application.yml` du service |
| w20-10 | `server-play/…/moderation/**`, `V2__moderation.sql`, `play-admin.html`, `confidentialite.html`, `docs/QUIZ-EN-LIGNE.md` | `PlayRetentionJob`, `docs/QUIZ.md` (renvoi) |
| w20-08 | `docs/PLAY-OPS.md`, `backend/sql/play-schema.sql`, `backend/.env.example` | `backend/docker-compose.yml` (service), `tools/release/deploy-server.sh` (`--service`), `backend/README.md`, `docs/RELEASES.md` |
| w20-05 | `R/quiz/{PlayTransportOkHttp,PlayBanner,PlayScopeDialog,PlayTicketClient,PlayLocalRelay,PlayFallback}.kt`, tests, `docs/test-plans/H-PLAY.md` | `R/QuizHub.kt`, `R/QuizActivity.kt`, extension Quiz (`/api/quiz/scope`), `receiver/build.gradle.kts` (OkHttp si absent) |
| w20-06 | `server-play/…/static/play/*` (zones), `S/quiz/PlayDeepLink.kt`, tests | `S/QuizScreen.kt`, `sender/AndroidManifest.xml`, `docs/QUIZ.md` § 3 |
| w20-12 | — | `docs/QUIZ.md`, `docs/HANDOFF.md`, parcours, gabarit, `docs/REGRESSIONS.md`, divulgation |
| w20-13 | `C/quiz/online/{Tables,ClassRoom,EventSchedule}.kt`, `server-play/…/v2/**`, `V3__events.sql`, `S/quiz/ClassRoomScreen.kt` | page admin (w20-10), `S/QuizScreen.kt` |
| w20-04b (amendement) | `server-play/…/static/play/info.html`, `server-play/src/test/…/poc/{TvOnlyJoinTest,MultiTvRelayTest,WebClosedTest,RevocationsModeTest,StateCoalescingTest}.kt`, `CT/quiz/online/ServerRoomRelayerTest.kt` | `SP/{PlayConfig,PlayServer,PlayPageController,RoomRegistry}.kt`, `offer` de `PlayFallbackController`/`PlayWebSocketHandler`, `C/quiz/online/{ServerRoom,PlayProtocol,PlayCodec}.kt`, fixtures de test (`webPlay = true`), `docs/PLAY-PROTOCOL.md`, PLAY-OPS § 4.2, `.env.play.example` |
| w20-05a (amendement) | `C/quiz/online/{PlayHttpTransport,PlayTvSession,RelayAuthority,LinkCause}.kt`, tests cœur, `server-play/src/test/…/poc/client/{TvClientLoopbackTest,EdgeFluidityTest,SlowSocksProxy}.kt` | `C/quiz/QuizHttp.kt` (constructeur sur autorité), `ServerAuthority.kt` (join d'une TV, `ack` de relais) |

## Tranches et ordre

```
  S0 cœur pur          w20-01 ──► w20-02
                                     │
  S1 service           ───────────► w20-03 (audit Opus) ──┬──► w20-04 (audit Opus) ──┐
                                                          ├──► w20-07 (audit Opus) ──┼──► w20-11 (chaos, charge)
  S3 ops/docs                                             └──► w20-08 (runbook)      │
  S2 produit                                                                         └──► w20-09 ──► w20-10
  S4 câblage (sortie du gel)   w20-05 (TV, audit Opus) ──► w20-06 (app ; la page web peut précéder, dès w20-03)
  S3 docs                      w20-12 (brouillon après 03, final après 11)
  S5 V2 (conditionnel)         w20-13 après w20-09 et D-W20-6/10

  Valeur livrée : 01 (le signe visible en local dès la sortie du gel) → 03 (une salle Internet qui tourne en test) → 04 (le commercial)
                  → 07/11 (sûr et prouvé) → 08 (déployable par le propriétaire, après O-1…O-7) → 05/06 (visible) → 09/10 → 12 → 13
  Parallélisme ≤ 3 ; w20-03 seul sur server-play/ jusqu'à fusion ; audits Opus groupés (03+07, puis 04, puis 05).
```

## Décisions ouvertes et faits bloquants (voir `DESIGN-W20` § 7)
D-W20-1 (443 + `/play/`), D-W20-2 (7443 fermé par défaut), D-W20-3 (serveur autoritaire), D-W20-4 (service dédié), **D-W20-5 (essai : Quiz ouvert + salles privées, contradiction `TrialPolicy`)**, D-W20-6 (hôte téléphone : non en V1), D-W20-7 (confirmation TV), D-W20-8 (pas de migration d'hôte), D-W20-9 (classement par appareil enregistré), D-W20-10 (classes et sponsors sans prix avant le juridique), D-W20-11 (admin `play` à jeton distinct), D-W20-12 (durées), D-W20-13 (enfants), D-W20-14 (réservées publiées après le gel des ids), D-W20-15 (plafonds). **BLOQUÉ** : B-W20-1 nginx/pare-feu de production (hors dépôt), B-W20-2 vCPU, B-W20-3 juridique 2026-12-31.
