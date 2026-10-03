# w20-04 — Droits et modèle commercial en ligne : ticket `cbp1` (API principale), vérification côté `play` (ticket + activation `cbx1` + locations ⇒ `TvAccess` et lots couverts), questions réservées servies une à la fois, règles essai/production
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (chemin des droits, clés, secret des réservées) · statut : PRÊT (après w20-03 et D-W20-5 tranchée ; sinon implémenter la recommandation derrière un drapeau)
> **Groupe : W20-S1** (service) · prérequis : w20-03 fusionné ; `content/quiz/reserved-ids.json` gelé (sinon le cahier produit le gel en **lecture seule** d'une copie locale et le dit) · porte : `gradle :play-server:test --tests 'castbridge.play.entitlement.*'` + `cd backend && ./mvnw -q test -Dtest='PlayTicket*'` + `:core:test --tests 'castbridge.core.quiz.online.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : **obligatoire**

**Vague 20 · Effort L · Modèle : sonnet · Statut PRÊT (après w20-03).** Conception : `DESIGN-W20` § 2.5, § 4 (T-9, T-10, T-16). Branche `claude/sonnet-w20-04`. Rapport : `docs/agent-reports/sonnet-w20-04.md`. **Seul cahier** sur `backend/src/main/java/castbridge/server/play/` (nouveau paquet, une route additive) ; aucun autre fichier de `backend/`. Aucun `S/`, `R/`.

## Objectif
(1) API principale : `POST /api/v1/play/ticket` (`Authorization: Bearer <deviceToken>`, existant pour `/api/v1/devices/*`) ⇒ `cbp1.<payload>.<sig>` Ed25519 avec une **clé dédiée** `play-ticket.key` (secret fichier, jamais la clé des mises à jour ni de licence), charge `{deviceId, blocked, country, iat, exp = iat + 600 s, nonce}` ; refus si device bloqué ; 20 tickets / device / h. (2) Service `play` : `TicketVerifier` (clé **publique** seule, `exp`, `nonce` mémorisé 10 min), `HostRights` : à la création d'une salle la TV joint `activation` (`cbx1.…`) et `rentals` (lignes signées) ; `play` vérifie les signatures avec les clés **publiques** des émetteurs de confiance (`CASTBRIDGE_PLAY_TRUSTED_KEYS`, même format que `CASTBRIDGE_LICENSES_TRUSTED_KEYS`), exige `deviceId` du ticket = identité de l'activation, applique `TvGate.evaluate` et `Entitlements` du cœur **avec l'horloge du serveur**, relit `GET /api/v1/revocations` (public, signé) toutes les 15 min ⇒ `HostRights(edition: PROD|TRIAL|GRACE|NONE, coveredScopes: Set<String>, publicAllowed, maxGamesPerDay)`. (3) `ReservedBank` : paquets réservés (`CASTBRIDGE_PLAY_RESERVED_DIR`, RO) + `reserved-ids.json` ⇒ tirage `QuizBank` libre + réservées **seulement** si `scope ∈ coveredScopes` ; la question courante seule sort (w20-02 garantit le reste). (4) Règles : tableau § 2.5 (essai : privée, libres, 3 parties/jour ; salon public : libres ; horloge douteuse ⇒ refus avec texte).

## Pourquoi (preuves)
- `B/devices/DeviceService` (`register` ⇒ `deviceToken`, SHA-256 stocké) et `DeviceController` (`Bearer`) : l'identité d'appareil côté serveur existe ; aucun `X-CB-*` côté serveur.
- `B/licenses/PublicLicenseController` : `GET /api/v1/revocations` (signé `cbx1`) et `GET /api/v1/entitlements/me` : lecture publique réutilisable par `play` sans privilège.
- `docs/REMOTE-TUNNEL-TV.md:16` : `POST /api/v1/tunnel/enroll {activation, …}` : précédent d'une activation `cbx1` jointe comme preuve.
- `C/owner/Activation.kt:170-178` `TvAccess`, `TvGate.evaluate(activations, grants, nowMs, rentals, clockDoubt…)` ; `C/lots/Entitlement.kt` `Right.Rental/Purchase`, `Entitlements` ; `C/lots/RentalLines.kt:5,28` : tout est dans `:core`, donc disponible à `play` (Kotlin) et **pas** à l'API Java : d'où le partage des rôles (l'API atteste, `play` évalue).
- `application.yml:110-124` : secrets de licence = fichiers dans `/run/secrets`, clés de confiance publiques en variable : modèle à reproduire pour `play-ticket.key`/`.pub`.
- `docs/agent-reports/quiz-toutes-les-questions.md` § « Conséquence pour le serveur » : 65 252 réservées (30 %), paquets non publiés, gel des ids à faire.
- `C/owner/TrialPolicy.kt:29-38` : liste blanche d'essai **sans** le Quiz vs `QuizEdition.TRIAL_OPEN = true` (`C/quiz/QuizLevelAvailability.kt:14`) : contradiction, D-W20-5.

## Fichiers possédés
- API : `backend/src/main/java/castbridge/server/play/{PlayTicketController, PlayTicketService, PlayTicketKey}.java`, `backend/src/test/java/castbridge/server/play/PlayTicketControllerTest.java`, zone additive `backend/src/main/resources/application.yml` (clé `castbridge.play.ticket-key-file`, `per-device-per-hour`), `backend/docker-compose.yml` (secret `play_ticket_key` **déclaré**, pas monté en production par ce cahier), `docs/API-SERVER.md` (section « Ticket de jeu »).
- `play` : `server-play/src/main/kotlin/castbridge/play/entitlement/{TicketVerifier, HostRights, TrustedIssuers, RevocationsFeed, ReservedBank, PlayRules}.kt`, tests `…/entitlement/{TicketVerifierTest, HostRightsTest, ReservedBankTest, PlayRulesTest}.kt`, fixtures `src/test/resources/play/{ticket-test.key, ticket-test.pub, cbx1-prod.txt, cbx1-trial.txt, rental-cm2.txt}`.
- Cœur : `C/quiz/online/PlayRules.kt` (pur : tableau § 2.5 en code) + `CT/quiz/online/PlayRulesTest.kt`.
- Interdit : `B/licenses/**`, `B/devices/**` (lecture seule), `C/owner/**`, `C/lots/**`.

## Étapes
1. **Rouge** : `PlayRulesTest` (matrice § 2.5 : 9 lignes × créer/rejoindre/réservées/classé), `TicketVerifierTest` (bonne signature, expiré, nonce rejoué, mauvaise clé, `blocked`), `HostRightsTest` (prod sans location ⇒ libres ; prod + location `cm2` ⇒ `coveredScopes = {cm2}` ; essai ⇒ privée/3 j ; révoqué ⇒ `NONE` ; `clockDoubt` ⇒ `NONE` avec texte ; `deviceId` ≠ identité ⇒ refus), `ReservedBankTest` (sans droit : aucune question réservée tirée sur 1 000 parties ; avec droit : réservées présentes ; **jamais** plus d'une question servie par message), `PlayTicketControllerTest` (MockMvc : 401 sans Bearer, 403 bloqué, 200 + vérification de signature avec la clé publique, 429 au 21e).
2. API : clé Ed25519 lue depuis `castbridge.play.ticket-key-file` (absente ⇒ route 503 « ticket désactivé », comme `QuizPackController` sans signature) ; charge canonique `castbridge-play-ticket-v1\n…` ; **aucune** évaluation de droits côté Java.
3. `play` : `TrustedIssuers` (format `desktop:<b64 pub>:SCOPES,…`), `HostRights` via `TvGate`/`Entitlements`, `RevocationsFeed` (cache, échec ⇒ dernière liste connue + `safety` orange « révocations non rafraîchies » après 1 h).
4. `ReservedBank` : `QuizLotConsumer`-like en lecture (réutiliser `QuizPacks`/`QuizLotIndex` du cœur), index en mémoire, questions chargées **par lot à la demande** ; `reserved-ids.json` : un id réservé hors d'un paquet réservé ⇒ exclu (défense en profondeur).
5. Branchement dans `RoomRegistry.create` (w20-03) : `HostRights` requis ; `PlayRules.canCreate/canJoin/bankFor/isRanked`.
6. **Vert** ; rapport avec la **matrice** des droits testée et la liste des clés/variables (aucune valeur).

## Critères d'acceptation
- Aucune clé privée dans `server-play/` (test de source : aucun fichier `*.key` hors `src/test/resources`, aucune lecture de `/run/secrets/*.key`).
- Le ticket ne contient **ni** édition **ni** droits (seulement l'attestation d'appareil) : l'édition vient de `cbx1` évaluée par `play`.
- 1 000 salles sans droit ⇒ 0 question réservée ; 1 000 salles avec droit `cm2` ⇒ réservées `cm2` seulement.
- `TrialPolicy` : si D-W20-5 = oui, ajout **additif** de `quiz` à `GAMES` et de `/api/quiz/*` à la liste blanche **n'est pas** dans ce cahier (zone `C/owner/`) : le rapport le demande au coordinateur (`QUESTION:`), et `PlayRules` traite l'essai comme au § 2.5 dès maintenant.

- **EXIGENCE I1 (audit Opus de w20-03, 2026-10-03, reportée ici)** : le ticket est à **usage unique** (`jti` aléatoire de 128 bits, mémorisé par `play` jusqu'à `exp`, un second `create` avec le même `jti` ⇒ `PLAY_TICKET_REFUSED`), et le nombre de salles ouvertes par **sujet** (appareil attesté) est plafonné (par défaut 2) en plus du plafond global ; le vérificateur de `server-play` (`TicketVerifier`) accepte aujourd'hui un ticket rejouable pendant sa vie (≤ 15 min), c'est le trou que ce cahier ferme. Test : même ticket deux fois ⇒ une seule salle ; un appareil qui ouvre trois salles ⇒ la troisième refusée.

## Cas limites
- Deux activations jointes (production + essai) ⇒ production gagne (`TvGate`). Location expirée hier ⇒ libres seulement, texte « Location terminée : questions libres ». Ticket valide mais `cbx1` absent ⇒ hôte `NONE` : peut **rejoindre** une salle comme téléphone ? **Non** (la TV n'est pas un joueur) ⇒ `PLAY_SCOPE_FORBIDDEN` « Activez la TV pour créer une partie Internet ».

## À ne pas faire
- Ne jamais réutiliser `castbridge_signing_key` ni `license-signing.key` pour les tickets. Ne pas appeler l'API principale avec un privilège depuis `play`. Ne pas servir de liste de questions. Ne pas modifier `TvGate`, `Entitlements`, `TrialPolicy`.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play-ticket · proto=play-v1 · reason=PLAY_TICKET_REFUSED,PLAY_SCOPE_FORBIDDEN · deux écrans=HostRightsTest` + matrice des droits + variables d'environnement + points d'audit Opus (clés, nonce, réservées).
