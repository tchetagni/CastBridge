# w19-08 — Tests : parcours de symbiose J-SYM-1…14 dans le harnais W14 (`SymbiosisJourneyTest`) : écart de versions, refus visible des deux côtés, redémarrages, PIN tourné, divergence de bibliothèque, lots, copie ralentie, boucle R-17, deux TV, changement de voie, source modifiée, clé retirée, TV mise à jour, TV éteinte deux jours

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (tests) · statut : après w19-01, 02, 03, 06, 07, 13
> **Groupe : W19-S2** · prérequis : les cahiers cœur fusionnés (sinon : coder contre leurs interfaces et marquer `@Ignore("attend w19-NN")` nommé) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.Symbiosis*' --tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · audit Opus : non

**Vague 19 · Effort M · Modèle : sonnet.** Conception : DESIGN-W19 § 4.3 (table J-SYM-1…14), § 1.4 (RS cités), § 3.4, § 3.5.5. Branche `claude/sonnet-w19-08`. Rapport : `docs/agent-reports/sonnet-w19-08.md`. Tests seulement.

## Objectif
`CT/journey/SymbiosisJourneyTest.kt` : une méthode par parcours J-SYM-1…14, chacune affirmant **l'état des deux côtés** (téléphone : `XferState`/`Coherence.Verdict`/`UpdateNudge` ; TV : `TransferProgress`, `RejectionLedger`, `SyncState`) et le délai (horloge `JourneyClock`). Extensions du harnais : `TvSim.persona(tag)` (démarre une `TranscriptTv` de w19-03 à la place de la vraie `ReceiverServer`), `TvSim.upgradeToHead()` (J-SYM-13 : même dossier de données, serveur HEAD), `PhoneSim.relaunch()` (file relue), `PhoneSim.coherence()`.

## Pourquoi (preuves)
- `CT/journey/{TvSim,PhoneSim,JourneyKit,Scenario}.kt` : le harnais W14 existe (vraie `ReceiverServer` port 0, `restart()`), ses parcours couvrent P-01…P-24 ; aucun ne fait varier la **version** ni ne lit un état des deux côtés en même temps.
- `docs/REGRESSIONS.md` : 16 lignes « CORRIGÉE (à confirmer sur appareil) » dont aucune n'affirme la **concordance** des deux écrans.

## Fichiers possédés
Nouveaux : `CT/journey/SymbiosisJourneyTest.kt`, `CT/journey/SymbiosisKit.kt` (assertions « deux côtés » réutilisables : `assertSameReason(tvLedger, phoneState, code)`, `assertSamePercent`, `assertWithin(seconds)`). Zones additives : `CT/journey/TvSim.kt` (`persona`, `upgradeToHead`, ≤ 40 lignes), `CT/journey/PhoneSim.kt` (`relaunch`, `coherence`, ≤ 30 lignes). **Hors zone** : `C/`, `S/`, `R/`, `CT/compat/*` (utilisés, non modifiés).

## Étapes
1. `SymbiosisKit` ; J-SYM-2 et J-SYM-8 d'abord (R-17 : le rapport colle leur sortie).
2. J-SYM-1, 13 (personas) ; J-SYM-3, 14 (redémarrages, horloge) ; J-SYM-4 (PIN) ; J-SYM-5, 6 (Cohérence, lots) ; J-SYM-7 (ralenti) ; J-SYM-9 (deux TV) ; J-SYM-10, 11, 12 (voie, source, clé).
3. Table dans le rapport : J-SYM ⇒ RS ⇒ garantie S-n ⇒ test.
4. **Vert** : porte ; durée totale < 2 min.

## Critères d'acceptation
- Chaque J-SYM affirme au moins une assertion côté TV **et** une côté téléphone, et un délai.
- Aucun `Thread.sleep` réel ; horloge simulée.
- Un parcours qui échoue révèle un trou de production ⇒ `@Ignore("W19 trou : <fichier:ligne>")` + `QUESTION:`, jamais une assertion affaiblie.

## Cas limites
Persona absente du dépôt (w19-03 incomplet) ⇒ test **sauté avec motif** (`Assume`), listé dans le rapport ; TV persona qui n'a pas `/api/sync/state` ⇒ Cohérence ORANGE « TV ancienne » attendue.

## À ne pas faire
Toucher `C/` ; dupliquer une persona ; écrire une phrase française dans un test (comparer aux constantes de `Reason`/`Coherence`/`XferTexts`).

## Rapport
RAPPORT + sorties J-SYM-2 et J-SYM-8 + table J-SYM ⇒ RS ⇒ S-n + `SYMBIOSE: deux écrans=SymbiosisJourneyTest (14)`.
