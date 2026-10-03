# Cahiers TVCTX — contexte porté par la TV, plusieurs TV (2026-10-03)

Source : demande du propriétaire du 2026-10-03 (R-10) ; conception `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md` ; correctif déjà livré : branche `claude/pin-persistence`, rapport `docs/agent-reports/pin-persistence.md`. Règles communes : `docs/COORDINATION.md`, `SONNET-WAVES-INDEX.md`. Gel : tvctx-01, 02 et 04 ne créent aucun écran ; tvctx-03 attend la levée du gel des écrans.

| id | Cahier | Objet | Groupe | Effort | Modèle | Audit Opus | Jauge (entrée / sortie, k) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| tvctx-01 | `sonnet-tvctx-01-hello-tv-id.md` | `tvId` public (haché) dans `/api/hello`, fiche du téléphone par `tvId` (deux TV du même modèle) | TVCTX-A | S-M | sonnet | oui | 250 / 12 | PRÊT | pin-persistence |
| tvctx-02 | `sonnet-tvctx-02-context-bundle.md` | `GET /api/context` lecture seule ≤ 64 Ko + cache par TV hors ligne | TVCTX-B | M | sonnet | oui | 350 / 18 | PRÊT | tvctx-01 |
| tvctx-03 | `sonnet-tvctx-03-known-tvs-list.md` | liste des TV connues (joignable / identifiant valide / code à saisir) | TVCTX-C | M | sonnet | échantillon | 300 / 16 | ATTENTE (gel des écrans) | tvctx-01, 02 |
| tvctx-04 | `sonnet-tvctx-04-multi-tv-renewal-keystore.md` | jeton renouvelé pour chaque TV ; fiche chiffrée Keystore (clé perdue ⇒ une demande) | TVCTX-A | M | sonnet | **oui** | 400 / 20 | PRÊT (après w15-03) | pin-persistence, w15-03 |

Fichiers : tvctx-01 (`ReceiverServer` /api/hello, `TvIdentity`, `TvDiscovery`) et tvctx-04 (`LinkDriver`, `PinCrypto`) touchent tous deux `C/trust/PinBook.kt` : **l'un après l'autre** (01 puis 04). tvctx-02 possède `TvContext`, la route et `TvContextCache` ; tvctx-03 seulement `KnownTvs` et `ManageTvsDialog`.
