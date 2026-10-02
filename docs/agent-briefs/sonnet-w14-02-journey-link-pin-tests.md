# w14-02 — Cœur : parcours J-01…J-11 (téléphone de confiance, réinstallations, Réassocier, code PIN juste/faux/changé/absent, verrou, jeton)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon (si un test exige un changement de production, STATUT: BLOQUÉ) · statut : PRÊT
> **Groupe : W14-b** (vague W14, tranche S1) · prérequis : w14-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.LinkJourneyTest' --tests 'castbridge.core.journey.PinJourneyTest'`
> **Jauge : ≈ 350 k jetons entrée / 22 k sortie** (effort M) · audit Opus : échantillon

**Vague 14b (cœur) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 3.2 (J-01…J-11), § 1.1 (R-01, R-03). Parcours P-01…P-10 de `docs/test-plans/PARCOURS-CRITIQUES.md`. Branche `claude/sonnet-w14-02`. Rapport : `docs/agent-reports/sonnet-w14-02.md`.

## Objectif
Écrire les 11 parcours de liaison et de PIN sur le harnais w14-01, **sans toucher au code de production**. Un test qui ne peut pas passer sur `integration/agents` parce que le défaut existe encore est **gardé rouge et marqué** `@Ignore("REGRESSION R-0x ouverte : …")` avec la ligne de `docs/REGRESSIONS.md` : c'est le test « rouge d'abord » que le correctif devra rendre vert. Les tests qui décrivent un comportement déjà corrigé (R-02, R-03 : commits 9f6777f, cf47cc0) doivent être verts.

## Pourquoi (preuves)
- R-03 : `C/trust/LinkDriver.kt` `prepareReassociate` (commit cf47cc0) garde la TV ; `CT/LinkDriverTest.kt` (+29 lignes) le teste **sans HTTP** ; le parcours complet « réinstallée → Réassocier → abandon → la TV reste → Ouvrir avec propose la file » n'existe pas.
- R-01 : la chaîne § 1.4 de `DESIGN-W13` (jeton vert, transfert avec ancien PIN, 401 avalé) n'a aucun test : J-08 la fige.
- Verrou : `C/tv/Security.kt:42-46` (5 refus / 60 s, PIN absent compté) ; `C/trust/Credentials.kt:26-28` (aucun en-tête si `""`).
- Jeton 12 h, mi-vie : `C/trust/TrustRegistry.kt:39,114`, `C/trust/LinkDriver.kt:96-105`.

## Fichiers possédés
Nouveaux : `CT/journey/LinkJourneyTest.kt` (J-01…J-05, J-11), `CT/journey/PinJourneyTest.kt` (J-06…J-10). **Hors zone** : `CT/journey/{JourneyKit,TvSim,PhoneSim}.kt` (si une action manque, l'écrire comme **fonction d'extension privée** dans le fichier de test et le signaler), tout `C/**`.

## Étapes
Un test par ligne de la table § 3.2 de la conception, nom exact de la table, structure `given / when / then` du `JourneyKit` ; assertions **sur les deux côtés** (téléphone : `chip()`, `sendFacts()`, `notifications` ; TV : `registry.list()`, `pinFailures()`, `notices()`).
1. J-01 : `pairWithTv()` ⇒ `chip().state.isGood` ; `registry.list().size == 1` ; `GET /api/info` avec le jeton = 200.
2. J-02 : `tv.reinstall()` ; `phone.run(6)` ⇒ la vue **n'est pas** `LinkState.NoTv`, titre contient « réinstallée » ou « Réassocier » ; `sendFacts().savedCount == 1` ; le journal (`phone.log` ou `Step` texte) contient `Refused code`.
3. J-03 : `reassociate()` ; `tv.bt.authorizeNext()` (ou l'équivalent de `FakeTv`) ; `run(4)` ⇒ `isGood`, `saved.list().size == 1`, jeton ≠ ancien.
4. J-04 : `reassociate()` ; `abandonReassociate()` ; `run(3)` ⇒ `saved.list().size == 1`, `SendChoices.decide(sendFacts()).route == QUEUE` et `copyEnabled`.
5. J-05 : `phone.wipe()` ; `pairWithTv()` sans nouvel « Autoriser » (`tv.bt` ne doit **pas** ouvrir de fenêtre : compteur de fenêtres) ⇒ `isGood`.
6. J-06 : `enterPin(tv.pin) == OK` ; `pins.keysOf(tv)` ⇒ toutes les clés ont le code.
7. J-07 : `enterPin("000000") == REJECTED` ; `tv.pinFailures() == 1` ; `chip()` non `GOOD` ; texte non vide.
8. J-08 : P-05 fait ; `tv.rotatePin()` ; `send(small)` ⇒ `finalState != "Done"`, **dernière** `Notice.final == true` avec texte non vide mentionnant « code » ; nombre de requêtes d'envoi ≤ 3. Si rouge sur `integration/agents` (F1/F3 de W13) : `@Ignore("REGRESSION R-01 ouverte : …")` + ligne REGRESSIONS.
9. J-09 : clé sans code ⇒ `send` termine en `Missing` **avant** la première requête ; `tv.pinFailures() == 0`.
10. J-10 : 5 × `enterPin(bad)` ; `enterPin(tv.pin) == LOCKED` ; `clock.advance(61_000)` ; `run(2)` ⇒ `OK` sans action ; aucune requête pendant le verrou (compter via `tv.notices()` ou un compteur HTTP du `TvSim`).
11. J-11 : `pairWithTv()` ; `clock.advance(13 h)` ; `run(4)` ⇒ `isGood`, jeton renouvelé, aucune vue d'erreur.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c '@Test' CT/journey/LinkJourneyTest.kt CT/journey/PinJourneyTest.kt` ⇒ 6 et 5 ; au plus **2** `@Ignore` au total, chacun citant `R-0x` ; durée totale < 20 s ; `:core:test` complet vert.

## Cas limites
`FakeTv` et autorisation : lire `CT/LinkFixtures.kt` pour la façon d'« autoriser » (option `pairing`/`approve`) ; verrou par IP : `ReceiverServer` voit `127.0.0.1` pour tous les tests ⇒ un `TvSim` **par test** (pas de verrou résiduel).

## À ne pas faire
Aucune modification de production ni du harnais ; aucune assertion sur le temps réel ; pas de `SendFacts(` écrit à la main (toujours `sendFacts()`).

## Rapport
`STATUT`, table J-xx → vert/rouge/`@Ignore` avec motif, extensions privées ajoutées (à remonter dans w14-01 ensuite), lignes proposées pour `docs/REGRESSIONS.md`.
