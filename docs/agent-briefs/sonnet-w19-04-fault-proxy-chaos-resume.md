# w19-04 — Tests : `FaultProxy` (pannes injectées sur loopback), `RadioSim` de voie, assertions S-1/S-2/S-4/S-5/S-8, et `ChaosResumeTest` à graine (invariant « chaque octet arrive exactement une fois, vérifié, ou l'échec est visible avec sa cause des deux côtés »)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (tests seulement) · statut : PRÊT (après w19-13)
> **Groupe : W19-S0** · prérequis : w19-01, w19-13 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.compat.Fault*' --tests 'castbridge.core.compat.Chaos*' --tests 'castbridge.core.journey.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2 j) · audit Opus : non

**Vague 19 · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-1, S-2, S-4, S-5, S-8, S-REPRISE), § 1.4, § 3.5.3 (invariants I-1…I-4), § 4.2. Branche `claude/sonnet-w19-04`. Rapport : `docs/agent-reports/sonnet-w19-04.md`. Tests seulement : aucun fichier de production.

## Objectif
(1) `CT/compat/FaultProxy.kt` : mandataire TCP loopback (fil par connexion, bibliothèque standard) entre un client et une `ReceiverServer` réelle (ou une `TranscriptTv` de w19-03), piloté par un scénario : `closeEarly(afterBytes)`, `slow(bytesPerSec)`, `inject(status, body, headers)`, `rstMidBody(fraction)` (`SO_LINGER 0`), `restartServer(fraction)` (rappel vers `TvSim.restart()`), `clockSkew(ms)` (sur `JourneyClock`), `drop()` ; compteurs (connexions, octets, coupures). (2) `CT/compat/RadioSim.kt` : trois « voies » = trois bases HTTP distinctes (ports) vers le **même** serveur, avec `up/down` par voie et une latence/débit simulés ; `routeFlip(seq)`. (3) `CT/compat/FaultGuaranteesTest.kt` : une méthode par garantie S-1, S-2, S-4, S-5, S-8 (table du § 4.2). (4) `CT/compat/ChaosResumeTest.kt` : 5 fichiers (1 Mio, 8 Mio, 64 Mio, homonyme de taille différente, « Déplacer » simulé par `MoveProof`), `N = 40` évènements tirés par `java.util.Random(seed)` à des instants aléatoires (coupure, RST, redémarrage TV, « kill » téléphone = nouvelle `PhoneSim` sur la file persistée, changement de voie, 404 session, 429, 503 volume, 507, rotation de jeton, horloge ±24 h, source modifiée sur le 3ᵉ fichier) ; **invariant final** (DESIGN § 4.2) ; graines fixes `1, 2026, 424242` + une graine aléatoire **journalisée** (`CHAOS seed=…`, rejouable par `-Dchaos.seed=`) ; durée < 90 s par graine.

## Pourquoi (preuves)
- Le harnais W14 (`CT/journey/TvSim.kt:49, 138-170`, `PhoneSim`, `JourneyClock`) sait démarrer/redémarrer une vraie `ReceiverServer` sur port 0 : la fondation existe, il manque la **couche réseau** qui casse des octets.
- `docs/REGRESSIONS.md` R-06, R-09, R-15 : des correctifs vérifiés « au niveau du vrai `ReceiverServer` » mais jamais sous coupure aléatoire ; R-17 : aucun test de boucle.
- Exigence du propriétaire : « garantir les reprises / renégociation quel que soit le scénario » ⇒ un générateur, pas une liste finie.

## Fichiers possédés
Nouveaux : `CT/compat/FaultProxy.kt`, `CT/compat/RadioSim.kt`, `CT/compat/FaultGuaranteesTest.kt`, `CT/compat/ChaosResumeTest.kt`, `CT/compat/ChaosInvariant.kt` (vérification de l'invariant : SHA-256 final, compteur de blocs écrits lu dans le sidecar/`PartAssembler`, états des deux côtés). Zone additive : `CT/journey/TvSim.kt` (`restartAt`, accès au `RejectionLedger`, ≤ 25 lignes), `CT/journey/PhoneSim.kt` (`relaunch()` sur la même file, ≤ 25 lignes). **Hors zone** : tout `C/` (si une assertion révèle un défaut de production : test `@Ignore("W19 trou : …")` + `QUESTION:`), `S/`, `R/`.

## Étapes
1. `FaultProxy` + test d'auto-contrôle (un `GET /api/hello` traverse ; `closeEarly` ferme ; `rstMidBody` produit une `IOException` côté client).
2. `FaultGuaranteesTest` : S-1 (refus `NAME_TAKEN` via RST : code sur le téléphone **et** dans le registre ≤ 5 s simulées) ; S-2 (serveur qui ferme toujours : ≤ 12 essais / ≤ 10 min, `Paused`, `onWaiting` « essai n/12 ») ; S-4 (rejeu ×3 de `begin`, `chunk`, `finish`, `abort` : disque identique, `duplicates` = 0 hors voies parallèles) ; S-5 (`restartServer(0.3)` : même % des deux côtés ≤ 60 s ; `relaunch()` téléphone : idem) ; S-8 (`clockSkew(±24 h)` : issue identique octet pour octet).
3. `ChaosResumeTest` : générateur, journal des évènements (`seed`, instant, évènement) imprimé en cas d'échec ; invariant ; trois graines fixes + aléatoire.
4. **Vert** : porte ; durée mesurée dans le rapport.

## Critères d'acceptation
- Chaque garantie a un test nommé `sN_…` ; chaque scénario RS cité dans le cahier est couvert au moins par un évènement du chaos (table dans le rapport : RS ⇒ évènement).
- Le chaos échoue **avec** la graine et la séquence imprimées ; rejouable.
- Un défaut de production découvert est **isolé** (`@Ignore` nommé + `QUESTION:`), jamais contourné par un test plus faible.
- Aucune attente réelle > 2 s (horloge simulée ; `TestWatchdogGuardTest` respecté).

## Cas limites
Évènement pendant `finish`/`verifying` ; deux évènements dans la même milliseconde ; coupure pendant la lecture du registre ; graine qui ne tire aucune coupure (test toujours valide) ; `rstMidBody` à 0 % et 100 %.

## À ne pas faire
Modifier `C/` ; affaiblir un seuil du DESIGN pour passer ; écrire un test qui attend en temps réel ; dépendre d'un port fixe.

## Rapport
RAPPORT + `CHAOS seeds=1,2026,424242,<aléatoire> · durées · RS couverts : <liste>` + `SYMBIOSE: cap=— · proto=inchangé · reason=— · deux écrans=FaultGuaranteesTest.s1`.
