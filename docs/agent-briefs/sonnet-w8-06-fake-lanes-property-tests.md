# w8-06 — Cœur : fausses voies (`FakeLane`), horloge simulée et tests de propriété du moteur multivoie

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w8-01, w8-02)
> **Groupe : W8a-3** (vague W8a) · prérequis : w8-01, w8-02 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Property*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-01 et w8-02).** Conception : § 12.1. Branche `claude/sonnet-w8-06`. Rapport : `docs/agent-reports/sonnet-w8-06.md`.

## Objectif
Prouver hors ligne, sans TV, que le moteur ne corrompt jamais, converge vers la somme des débits (quand le disque ne borne pas), reprend après des pannes aléatoires, ne s'interbloque pas, reste borné en mémoire, et que la garde retire une voie nuisible. **Tests seulement** : aucun fichier de production.

## Pourquoi (preuves)
- `MultipathTransferTest` (33 cas) et `MultipathServerTest` (18 cas) testent des voies HTTP réelles sur la boucle locale avec des `Thread.sleep` : lents, non paramétrés, sans gigue ni pertes.
- `TransferBench.Shaped`/`Bucket` (`core/xfer/TransferBench.kt:116-134`) simulent des débits mais en temps réel.

## Fichiers possédés
Nouveaux `CT/xfer/{FakeLane,FakeClock,PropertyTest,ResumePropertyTest}.kt`. **Hors zone** : tout fichier de `main/` (si une accroche manque — p. ex. horloge injectable dans `LaneHealth` ou `Scheduler` —, la demander dans le rapport à w8-01/w8-02 ; en attendant, utiliser le paramètre `clock`/`sleepMs` existant de `Scheduler`, `Scheduler.kt:18-19`).

## Étapes
1. `FakeClock` : temps virtuel ; `sleep(ms)` avance le temps **sans** attendre ; les threads ouvriers du `Scheduler` reçoivent `sleepMs = clock::sleep` et `clock = clock::nanos`. Ordre déterministe par graine.
2. `FakeLane(id, kind, bandwidthBps, latencyMs, jitterMs, lossPct, disconnectAt: List<Long>, reconnectAfterMs, unit)` : `send` copie l'unité dans un `PartAssembler` réel (dossier temporaire) ou un tableau partagé, et « coûte » `latence + taille/débit ± gigue` sur la `FakeClock` ; `lossPct` = `Outcome.Failed` non fatal ; aux instants `disconnectAt` la voie échoue jusqu'à `reconnectAfterMs` ; un modèle optionnel « même radio » : `sharesRadioWith(id)` divise le débit des deux voies par 2 quand elles sont actives ensemble (pour la garde).
3. `PropertyTest` (paramétré, graine fixe, ≥ 200 combinaisons de 1-5 voies, fichiers 4-64 Mio, blocs 1 Mio) : (1) racine finale égale ; (2) durée simulée ≤ taille / (0,85 × Σ débits) quand `diskBps ≥ Σ` ; (3) octets en vol côté `TransferHost` ≤ `maxInflight` (compteur assertif à chaque écriture) ; (4) tampons des voies ≤ 2 Mio ; (5) garde : voie « même radio » retirée en ≤ 25 s simulées ; (6) aucun test > 20 s réels (`@Timeout`).
4. `ResumePropertyTest` : voies tuées à des instants aléatoires ; `TransferHost` remplacé à un instant aléatoire (même dossier : `PartAssembler.open` relit `.state`) ; terminaison et ≤ 2 blocs renvoyés par incident (compter les `Already`/`Corrupt`) ; toutes les voies retirées puis réadmises.
5. Rapport de tests lisible : chaque propriété imprime les paramètres de la pire itération.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.PropertyTest' --tests 'castbridge.core.xfer.ResumePropertyTest'   # vert en < 60 s réels
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.*'   # tout 8a vert
```

## Cas limites
Fichier de 0 o et de 1 o ; une seule voie à 100 % de pertes pendant 30 s simulées (le transfert attend, ne meurt pas) ; `disconnectAt` pendant le doublon de fin ; graine différente = mêmes propriétés (lancer 3 graines en CI, w8-20).

## À ne pas faire
Aucune attente réelle (`Thread.sleep` interdit dans les nouveaux tests) ; pas de réseau ; ne pas modifier `main/`.

## Rapport
`STATUT`, accroches demandées, durée réelle de la suite, pire itération par propriété.
