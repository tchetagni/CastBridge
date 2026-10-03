# w20-07 — Anti-triche et limites : `RttBook` câblé (ping/pong), notation compensée et grâce, `BotScore`, `Limits` (IP / appareil / salle / codes), `Pseudonym`, schéma de messages (fuzz), `OriginCheck` durci, journaux sans secret
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (surface Internet : limites, équité, journaux) · statut : PRÊT (après w20-03)
> **Groupe : W20-S1** (service) · prérequis : w20-03 fusionné · porte : `gradle :play-server:test --tests 'castbridge.play.guard.*'` + `:core:test --tests 'castbridge.core.quiz.online.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 2 j) · audit Opus : **obligatoire**

**Vague 20 · Effort M · Modèle : sonnet · Statut PRÊT (après w20-03).** Conception : `DESIGN-W20` § 2.6, § 2.8, § 3.3, § 3.5, § 4 (T-1…T-8, T-13, T-14, T-18). Branche `claude/sonnet-w20-07`. Rapport : `docs/agent-reports/sonnet-w20-07.md`. Pur d'abord (`C/quiz/online/`), câblage dans `server-play/…/guard/`. Aucun `S/`, `R/`, `backend/`.

## Objectif
(1) Pur : `BotScore` (règles § 2.8 : réponses < 300 ms ×10, exactitude > 95 % sur réservées, intervalles identiques, sièges jumeaux même IP/UA ⇒ score 0-100, seuil 70 ⇒ partie **non classée**, texte neutre), `Pseudonym` (§ 3.3 : NFKC, 2-16, alphabet, ≥ 7 chiffres interdits, URL, liste `blocklist.txt` fr/en/pidgin + leet, usurpations), `Limits` (seaux par IP, par `deviceHash`, par salle ; constantes § 2.8 ; horloge injectée), `BadCodeCounter` (10/IP/5 min, 100/IP/j, 50/salle ⇒ rotation), `Redact` pour `play` (jeton, ticket, `cbx1`, pseudonyme ⇒ 8 hex). (2) Service : ping/pong 5 s pendant une question (RTT ⇒ `RttBook`), 25 s hors question ; application de la grâce `min(rtt, 1 s)` et de l'attente `max rtt` avant `reveal` ; filtres `Limits` (avant upgrade et par message) ; `MessageSchema` strict + **fuzz** (10 000 messages aléatoires/mutés ⇒ jamais d'exception non gérée, jamais d'effet sur une salle) ; `OriginCheck` : `null` accepté seulement avec jeton ; `RelayFairness` : la TV ne peut qu'allonger (§ 2.6) ; journaux ECS filtrés par `Redact`.

## Pourquoi (preuves)
- `C/quiz/QuizHttp.kt:177-199` : `allow(ip)` et constantes : limites existantes, mêmes ordres de grandeur.
- `C/quiz/QuizDuel.kt:134` `points(elapsedMs, windowMs)` : 1 000 → 500 : une compensation de 300 ms vaut ≤ 7,5 points ; la triche sur le RTT est **plafonnée** par construction.
- `B/config/RateLimitFilter` : seau par IP sur `X-Forwarded-For` écrasé : même hypothèse réseau pour `play`.
- `docs/PARENTAL.md` § Sécurité : « Tout paramètre d'URL dont le nom contient pin… refusé » : même rigueur pour `token`.
- `DESIGN-W19` § 3.1 règle (5) : « aucun secret ni adresse complète dans `detail` » et `Redact.scrub` TV.

## Fichiers possédés
- Cœur : `C/quiz/online/{BotScore, Pseudonym, Limits, BadCodeCounter, PlayRedact}.kt`, `android/core/src/main/resources/castbridge/quiz/online/blocklist.txt`, tests `CT/quiz/online/{BotScoreTest, PseudonymTest, LimitsTest, BadCodeCounterTest, PlayRedactTest}.kt`.
- Service : `server-play/src/main/kotlin/castbridge/play/guard/{PingPong, LimitFilter, MessageSchema, RelayFairness, LogRedactor}.kt`, tests `…/guard/{MessageSchemaFuzzTest, LimitFilterTest, PingPongTest, RelayFairnessTest, RedactionTest}.kt`.
- Zones additives : `PlayWebSocketHandler` (w20-03) : points d'appel des gardes (≤ 20 lignes) ; `RoomCode` (w20-02) : rotation sur signal de `BadCodeCounter`.
- Interdit : `QuizRoom`, `QuizDuel`, `QuizGame`.

## Étapes
1. **Rouge** : tous les tests ci-dessus ; `MessageSchemaFuzzTest` à graine (10 000 cas) ; `RelayFairnessTest` (TV qui envoie `localElapsedMono` = 0 ⇒ `elapsed` = serveur − rttTV, jamais moins).
2. Pur : `BotScore` (fenêtre glissante par siège, explications internes pour la modération, jamais affichées), `Pseudonym`, `Limits` (O(1), mémoire bornée : ≤ 100 000 clés, éviction LRU), `BadCodeCounter`, `PlayRedact`.
3. Service : `PingPong`, `LimitFilter` (HTTP et WS), `MessageSchema`, `LogRedactor` (filtre Logback sur les champs `message`, `detail`).
4. **Vert** ; rapport : table des limites **effectives** (valeurs, variable `.env`, comportement au dépassement : code, `Retry-After`, journal).

## Critères d'acceptation
- Fuzz : 0 exception non gérée, 0 modification d'état de salle par un message invalide, 3 invalides ⇒ fermeture 1008.
- Un robot « parfait » (réponses 100 ms, 100 %) sur 10 questions ⇒ `BotScore ≥ 70` ⇒ `ranked = false` dans le résultat ; un humain à 2 s ± 1 s et 70 % ⇒ `< 30`.
- `Pseudonym("Admin CastBridge")`, `("+237 6 99 00 11 22")`, `("http://x")` ⇒ refus avec motif ; `("Amina N.")` ⇒ accepté.
- Journal : aucun jeton, ticket, `cbx1`, pseudonyme en clair (test `RedactionTest` sur 50 lignes de journal réelles d'une partie de test).

## Cas limites
- IP partagée d'une école (40 téléphones) ⇒ 8 connexions/IP trop peu : la limite par IP est **relevée à 64** quand les connexions portent ≥ 8 `deviceHash` distincts **et** une salle commune (salle de classe), sinon 8 ; `.env` `CASTBRIDGE_PLAY_MAX_PER_IP_SHARED=64`. RTT qui saute de 100 à 1 800 ms ⇒ EWMA, plafond 400 ms de compensation.

## À ne pas faire
- Jamais d'accusation à l'écran (« tricheur ») : texte neutre « classement non pris en compte ». Pas de liste noire d'IP permanente. Pas de blocage par pays. Pas de prise d'empreinte navigateur agressive.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play1 · proto=play-v1 (champs additifs) · reason=PLAY_BUSY,BAD_NAME · deux écrans=—` + table des limites + points d'audit Opus.
