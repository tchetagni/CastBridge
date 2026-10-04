# w21-08 — Réglages bornés du service de jeu : délai entre questions par variable, mode adaptatif **éteint par défaut**, grâce de clôture, ping de salle ; jamais une limite de sécurité
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (équité et minutage, bornes, aucune voie d'affaiblissement des limites de sécurité) · statut : **ATTEND w21-03** (même `PlayConfig`, règle R2 W20)
> **Groupe : W21-D** (ordre 4) · porte : `:core:test --tests 'castbridge.core.quiz.online.*'` + `:server-play:test` complet (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 250 k jetons entrée / 15 k sortie** (effort S, ≈ 0,8 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 8 (boucle de réglage, configuration distante écartée), M-12/M-37/M-42 ; `DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` § 2.7 et D-AM-10. Branche `claude/w21-08-play-knobs`. Rapport : `docs/agent-reports/sonnet-w21-08.md`.

## Objectif (autonome)
`PlayTiming.INTER_QUESTION_GAP_MS = 1 500` (plage du propriétaire 1 000-2 000, `PlayTiming.gap` ramène dans la plage), `RttBook.MAX_GRACE_MS = 1 000`, `ServerRoom.PING_EVERY_MS = 5 000` sont des constantes compilées (`android/core/src/main/kotlin/castbridge/core/quiz/online/{PlayTiming,RttBook,ServerRoom}.kt`). Les mesures W21 diront s'il faut les changer. Permettre de les régler **dans le service** par variable d'environnement bornée, validée au démarrage, sans livrer de TV, et préparer la règle adaptative du délai (D-AM-10) **éteinte par défaut**.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/quiz/online/TimingKnobs.kt` (valeurs bornées, pures : `gapMs` 1 000-2 000, `gapMode` `FIXED|ADAPTIVE`, `graceMs` 500-1 500, `roomPingMs` 2 000-10 000 ; constructeur qui **ramène dans la borne et le signale**) ; tests `android/core/src/test/kotlin/castbridge/core/quiz/online/TimingKnobsTest.kt`, `server-play/src/test/kotlin/castbridge/play/TimingKnobsConfigTest.kt`.
- **Zone additive** : `ServerRoom.kt` (`Settings.knobs: TimingKnobs = TimingKnobs.DEFAULT` ; délai, grâce et ping lus des réglages ; `DEFAULT` = valeurs actuelles **exactement**) ; `PlayTiming.kt` (fonction pure `adaptiveGap(worstRttMs, base)` = `clamp(max(base, worstRtt + 500), 1 000, 2 000)`, utilisée seulement si `ADAPTIVE`) ; `PlayConfig.kt` (`CASTBRIDGE_PLAY_GAP_MS`, `CASTBRIDGE_PLAY_GAP_MODE`, `CASTBRIDGE_PLAY_GRACE_MS`, `CASTBRIDGE_PLAY_ROOM_PING_MS` ; hors borne ⇒ **refus de démarrer** avec message clair ; ajoutées à `ENV_NAMES`) ; `RoomRegistry.kt` (passage des réglages à la création de salle) ; `backend/.env.play.example` (lignes commentées avec bornes) ; `/play/health` : champ additif `timing` = `{gapMs, gapMode, graceMs, roomPingMs}` (aucun secret).
- **Interdit** : `RttBook.MAX_COMPENSATION_RTT_MS`, `RttBook.MAX_RELAY_RTT_MS`, `MAX_RTT_MS`, `WINDOW`, plafonds d'essai, de relayés, de tickets : **aucune** variable ne les touche ; `backend/`, `android/receiver|sender`.

## Étapes
1. **Rouge** (sortie collée) : `TimingKnobsTest.defaultsEqualCurrentConstants`.
2. `TimingKnobs` + bornes ; `DEFAULT` = 1 500 / `FIXED` / 1 000 / 5 000.
3. Lecture dans `ServerRoom` (aucune autre logique modifiée) ; `ADAPTIVE` : le « pire RTT » = maximum des RTT retenus (minimum de 8, `RttBook`) des connexions **assises** de la salle au moment de l'annonce ; jamais au-delà de 2 000 ms.
4. `PlayConfig` : variables, validation, refus de démarrer hors borne ; santé.
5. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `TimingKnobsTest.defaultsEqualCurrentConstants` ; `outOfRangeIsClamped` (500 ⇒ 1 000, 5 000 ⇒ 2 000).
- `ServerRoomTimingTest` et tous les tests existants de minutage **verts sans modification** avec `DEFAULT` (mutation : `DEFAULT.gapMs = 1 400` ⇒ un test existant échoue, à montrer).
- `AdaptiveGapTest` : RTT pires 200 / 900 / 1 800 ms ⇒ délais 1 500 / 1 500 / 2 000 ; jamais < 1 000 ni > 2 000 (mutation : retirer le `clamp` ⇒ échec) ; `FIXED` ⇒ toujours `gapMs`.
- `TimingKnobsConfigTest` : `CASTBRIDGE_PLAY_GAP_MS=2500` ⇒ refus de démarrer ; `CASTBRIDGE_PLAY_GRACE_MS=1500` accepté ; aucune variable ne change `MAX_RELAY_RTT_MS`/`MAX_COMPENSATION_RTT_MS` (test qui énumère `ENV_NAMES` et les réglages exposés).
- `NoAnswerLeakTest` et tests d'équité existants verts.
- `/play/health` contient `timing` et aucun autre champ nouveau.

## Interdits
Aucune configuration distante, aucune route, aucune lecture de fichier de réglage ; ne pas changer les valeurs par défaut.

## Rapport
Rouge, vert, mutations, tableau des variables (nom, défaut, borne, effet), extrait de `.env.play.example`.
