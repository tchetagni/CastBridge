# Vague 19 pour agents Sonnet/Haiku — index (2026-10-03) : symbiose CastBridge ↔ CastBridge-TV (un seul système, versions qui ne coïncident jamais, reprise garantie quel que soit le scénario)

Source : `docs/coordination/DESIGN-W19-SYMBIOSE-PHONE-TV-2026-10-03.md` (lire § 0, § 1.1, § 1.4, § 3.5, § 5 et le § cité par chaque cahier). Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md` (**lire la « Règle de symbiose » ajoutée en tête**). Routage : en-tête de trois lignes sur chaque cahier ; `routing.json` : lignes proposées au § Routage (le coordinateur les ajoute).

**Exigences du propriétaire (2026-10-03, verbatim)** : « garantis une synchronisation symbiotique entre app TV et l'app phone » ; « il faut garantir les reprises / renégociation quel que soit le scénario ». Lecture appliquée : les deux applications se comportent comme **un seul système** ; **une autorité par fait** (époque + réconciliation) ; **aucun échec muet** (enveloppe de raison `{code, message_fr, retryable, retryAfterMs}` dans les deux sens, registre des refus sur la TV, journal INFO sans secret) ; **aucun réessai sans fin** (≤ 12 essais ou ≤ 10 min puis état visible) ; **poignée de main de version et de capacités** additive (`/api/hello` enrichi, `X-CB-App`, cache `cap.<tvId>`, encouragements qui ne bloquent jamais) ; **état de synchronisation** `GET /api/sync/state` + carte « Cohérence » ; **protocole de reprise unique** (identité stable, la TV autorité sur ce qu'elle a écrit et vérifié, le téléphone sur la source, interrogation avant tout renvoi, quatre états finaux) ; **matrice de compatibilité JVM** (personas gelées par étiquette) + **chaos de reprise à graine** + **porte de livraison** `check_compat.py`.

**Faits à connaître** : `/api/hello` répond aujourd'hui exactement `{"app":"castbridge-tv","v":"0.7","pinRequired":…}` (`C/tv/ReceiverServer.kt:554-555`) ; `TransferClient.run` réessaie **sans borne** sur `IOException` de `begin`/`finish` (`C/xfer/TransferClient.kt:141, 165`) = R-17 ; la branche `claude/fix-broken-pipe` est vide ; `tools/smoke/smoke.py` et `FakeTvMain` **n'existent pas** (w14-07/08/10 sans rapport) ; étiquettes git disponibles : `tv-0.14.22…26-beta`, `phone-1.2.38/39-beta` ; `tvctx-01` (id dans `/api/hello`) non exécuté.

**Règles W19** : **(R1)** test rouge d'abord (JVM, horloges et radios factices) ; aucun appareil réel ; **(R2)** pendant le gel W15, **aucun** cahier ne touche `S/`, `R/` : w19-09 et w19-10 attendent la sortie du gel ; **(R3)** un seul cahier à la fois sur `C/tv/ReceiverServer.kt` : ordre **w19-01 → w19-13 → w19-02** ; w19-06 ajoute sa route par une classe d'extension (`ServerExtension`) et une seule ligne d'enregistrement ; **(R4)** audit Opus **obligatoire** sur w19-01 (chemin de données, `ReceiverServer`), w19-13 (reprise : perte de données), w19-10 (journal TV : secrets) ; échantillon sur w19-02, w19-06, w19-07 ; **(R5)** tout changement de message téléphone↔TV est **additif** et nomme sa capacité dans `C/sync/Caps.kt` (Règle de symbiose) ; **(R6)** aucun secret, aucune adresse complète dans une enveloppe, un registre, un journal, une persona (`Redact.scrub`) ; **(R7)** les messages français des refus vivent dans `C/sync/Reason.kt` et nulle part ailleurs ; **(R8)** la TV du propriétaire n'est jamais la cible d'un script ; `TVro` lecture seule tolérée pour recouper `hello`/`info` d'une persona ; **(R9)** un partiel n'est jamais « Terminé » : seuls quatre états finaux (DESIGN § 3.5.5).

**Modèle d'exécution** : `haiku` = docs (w19-11), fumée conditionnelle (w19-12) ; `sonnet` = le reste ; audits Opus : 3 obligatoires, 3 échantillons.

## Les 13 cahiers

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w19-01 | `sonnet-w19-01-reason-envelope-ledger-retry.md` | `Reason` (enveloppe + codes + français), `RejectionLedger` (TV), `RetryPolicy` (borné), vidage borné, lecture du registre après « Broken pipe » ; **absorbe R-17** | cœur | L | sonnet | **oui** | 800 / 40 | **PRÊT — EN PREMIER** | — |
| w19-13 | `sonnet-w19-13-resume-protocol-core.md` | protocole de reprise unique : `TransferIdentity` (racine du manifeste dans le sidecar et la file), `SourceGuard`, relecture de carte après coupure, `slice` figé par session, états finaux `XferState`, textes | cœur | L | sonnet | **oui** | 800 / 40 | PRÊT (après w19-01) | w19-01 |
| w19-02 | `sonnet-w19-02-hello-caps-cache-nudges.md` | `/api/hello` additif (`protocol`, `appVersion`, `versionCode`, `id`, `caps`, `edition`…), `Caps` + `impliedBy`, `X-CB-App` par `TvCredential`, `PinBook` `cap.<tvId>`, `UpdateNudge`, `FeatureGate` | cœur | M | sonnet | échantillon | 400 / 20 | PRÊT (après w19-13) | w19-01, w19-13 |
| w19-03 | `sonnet-w19-03-persona-recorder-compat-matrix.md` | `tools/compat/record_persona.py` + shim + adaptateurs par étiquette, personas `compat/<tag>/`, `TranscriptTv`, `TranscriptPhone`, `CompatMatrixTest` | outils + tests | L | sonnet | non | 800 / 40 | PRÊT (après w19-02) | w19-02 |
| w19-04 | `sonnet-w19-04-fault-proxy-chaos-resume.md` | `FaultProxy`, `RadioSim` de voie, assertions S-1/2/4/5/8, **`ChaosResumeTest` à graine** (invariant « chaque octet une fois ») | tests | L | sonnet | non | 800 / 40 | PRÊT (après w19-13) | w19-01, w19-13 |
| w19-05 | `sonnet-w19-05-release-gate-check-compat.md` | `tools/release/check_compat.py`, `docs/PROTOCOL-CHANGES.md`, crochet dans `tag-plan.sh --apply`, `tools/tests/test_check_compat.py` | outils | S | sonnet | non | 150 / 8 | PRÊT (après w19-03) | w19-03, w19-04 |
| w19-06 | `sonnet-w19-06-sync-state-epochs-coherence.md` | `Epochs` (+`bootId`), `SyncState` JSON, route `GET /api/sync/state` (extension), `PhoneSyncState`, `Coherence.compare`, `SignalAgreement` | cœur | L | sonnet | échantillon | 800 / 40 | PRÊT (après w19-01) | w19-01 |
| w19-07 | `sonnet-w19-07-identity-filing-lots-agreement.md` | `FileIdentity(sha256,size)`, `FilingPlan.VERSION` + hachage de réglages, `LotAgreement.plan`, fixtures de formats avant-compatibles (`FormatForwardCompatTest`) | cœur | M | sonnet | échantillon | 400 / 20 | PRÊT | — |
| w19-08 | `sonnet-w19-08-journeys-symbiosis.md` | J-SYM-1…14 dans le harnais W14 (`SymbiosisJourneyTest`, `TvSim` persona, `PhoneSim` file relue) | tests | M | sonnet | non | 450 / 22 | après 01, 02, 03, 06, 07, 13 | tous cœur |
| w19-11 | `sonnet-w19-11-docs-symbiose.md` | `docs/SYMBIOSE.md`, R-17 dans `REGRESSIONS.md`, P-53/P-54/H-REPRISE dans `PARCOURS-CRITIQUES.md` et la liste humaine, `HANDOFF.md` § 0, renvois | docs | S | haiku | non | 120 / 12 | après w19-08 | 01…08, 13 |
| w19-09 | `sonnet-w19-09-phone-wiring-symbiosis.md` | téléphone : codes `Reason` dans file/notification/barre, états finaux, encouragements, carte « Cohérence », bouton Wi-Fi Direct par cap, `X-CB-App` | **après le gel** | M | sonnet | non | 450 / 22 | ATTEND sortie du gel | 01, 02, 06, 13 |
| w19-10 | `sonnet-w19-10-tv-wiring-symbiosis.md` | TV : INFO `CB-REFUS` sans secret, carte « Refusé : … », page « Téléphone » (version + encouragement), époques depuis `TvService`, `bootId` | **après le gel** | M | sonnet | **oui** | 450 / 22 | ATTEND sortie du gel | 01, 02, 06 |
| w19-12 | `sonnet-w19-12-smoke-symbiosis.md` | J-SYM et H-REPRISE dans la fumée `tools/smoke/` (quand elle existe) | conditionnel | S | haiku | non | 120 / 10 | CONDITIONNEL (w14-07/10) | w19-08 |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet L 5 × ≈ 2,0 $ = 10 $ ; sonnet M 5 × ≈ 1,0 $ = 5 $ ; sonnet S 1 × ≈ 0,4 $ ; haiku S 2 × ≈ 0,1 $ ; audits Opus obligatoires 3 × ≈ 0,8 $ = 2,4 $ ; échantillons ≈ 1,5 $. **Total ≈ 19-20 $**, ≈ **23 agent·jours** (cœur ≈ 12, tests/outils ≈ 7, câblage ≈ 3, docs ≈ 1). **Pendant le gel : ≈ 19 j, ≈ 17 $.**

## Tranches et ordre (la valeur d'abord)

```
 gel ──► w19-01 (R-17 visible et borné) ──► w19-13 (reprise prouvée) ──► w19-04 (chaos) ──► w19-02 (versions/caps)
         │                                   │                                              │
         ├─► w19-06 (sync/cohérence)  ∥  w19-07 (identité/rangement/lots)                    ├─► w19-03 (personas, matrice) ─► w19-05 (porte)
         └──────────────────────────────────────────────────────────────────────────────────┴─► w19-08 (J-SYM) ─► w19-11 (docs)
 sortie du gel ──► w19-09 (téléphone) ∥ w19-10 (TV, audit) ──► w19-12 si la fumée existe
```

| Tranche | Cahiers | Livre | Parallélisme |
|---|---|---|---|
| **S0 refus et reprise** | w19-01 → w19-13 → w19-04 | S-1, S-2, S-REPRISE prouvées sur JVM ; R-17 | w19-07 et w19-06 en parallèle de w19-13 (zones disjointes) |
| **S1 versions** | w19-02 → w19-03 → w19-05 | S-6, S-10 ; matrice ; porte | w19-03 ∥ w19-06 |
| **S2 cohérence et preuve** | w19-06, w19-07 → w19-08 → w19-11 | S-3, S-7, S-9, S-11 ; J-SYM ; docs | 2 |
| **S3 câblage** (après le gel) | w19-09 ∥ w19-10 | ce que l'usager voit | 2 ; audit w19-10 |

## Portes

- Cœur : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.sync.*' --tests 'castbridge.core.xfer.*' --tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*'`.
- Compat : `… --tests 'castbridge.core.compat.*'` ; porte de livraison : `python3 tools/release/check_compat.py --strict` (w19-05).
- Outils Python : `python3 -m pytest -q tools/tests` (bibliothèque standard + pytest déjà présent).

## Routage (lignes proposées pour `routing.json`)

```
{"id":"w19-01","model":"sonnet","gauge":"sonnet-L","audit":"opus"},
{"id":"w19-13","model":"sonnet","gauge":"sonnet-L","audit":"opus"},
{"id":"w19-02","model":"sonnet","gauge":"sonnet-M","audit":"sample"},
{"id":"w19-03","model":"sonnet","gauge":"sonnet-L","audit":"none"},
{"id":"w19-04","model":"sonnet","gauge":"sonnet-L","audit":"none"},
{"id":"w19-05","model":"sonnet","gauge":"sonnet-S","audit":"none"},
{"id":"w19-06","model":"sonnet","gauge":"sonnet-L","audit":"sample"},
{"id":"w19-07","model":"sonnet","gauge":"sonnet-M","audit":"sample"},
{"id":"w19-08","model":"sonnet","gauge":"sonnet-M","audit":"none"},
{"id":"w19-09","model":"sonnet","gauge":"sonnet-M","audit":"none"},
{"id":"w19-10","model":"sonnet","gauge":"sonnet-M","audit":"opus"},
{"id":"w19-11","model":"haiku","gauge":"haiku-S","audit":"none"},
{"id":"w19-12","model":"haiku","gauge":"haiku-S","audit":"none"}
```

## Décisions du propriétaire (D-W19-1…14, DESIGN § 7) : recommandations appliquées par défaut

`protocol:8` entier à côté de `v:"0.7"` gardé · fenêtre P-3…P+1 · borne 12 essais / 10 min puis « En pause » (TV absente) ou « Abandonné » (refus, erreurs répétées) · `/api/sync/state` lisible en essai · `X-CB-App` sur toute requête · personas des étiquettes déjà livrées enregistrées maintenant · porte qui refuse l'étiquette (`--allow-red "raison"` écrit) · vidage borné 1 MiB · carte Cohérence sur la fiche TV + page « Téléphone » · encouragement 1/jour/TV · partiel d'essai gardé 24 h · source modifiée = abandon dit · partiels TV 7 j, purge dite. Un cahier qui a besoin d'une autre réponse écrit `QUESTION:` et continue la partie indépendante.
