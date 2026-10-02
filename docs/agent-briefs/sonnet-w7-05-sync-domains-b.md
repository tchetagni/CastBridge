# w7-05 — Domaines de synchronisation `par` (rapports parentaux), `shop` (jetons/bons W5), `set` (réglages), `ico` (icônes d'état)

**Vague 7a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w7-04 : coder contre `SyncDomain`/`DomState` de son cahier ; définir localement en `private` si absent et le dire).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 7.2. Branche `claude/sonnet-w7-05`. Rapport : `docs/agent-reports/sonnet-w7-05.md`.

## Objectif
Quatre adaptateurs purs : `ParDomain` (la TV annonce **seulement** les identifiants de rapports en attente ; le corps continue de passer par CBTP/`holder/pull` W6), `ShopDomain` (compteurs W5 `tokensSeq`, `walletState`, bons en attente ; **sans** W5 fusionné : domaine présent mais vide, capacité `CAP_SHOP` absente), `SetDomain` (nom de la TV, `wdEnabled`, `autostart`, langue, quota, état de l'assistance à distance, `net`), `IcoDomain` (icônes `StatusIconModel`).

## Pourquoi (preuves)
- `C/parental/ParentalSync.kt:32-48` (`ReportSyncHost.fetch/ack`), `C/parental/ParentalReports.kt:148-186` (`ReportOutbox`, ids `hex(ts)-6hex`) : le domaine `par` ne transporte que `{reportId, kind, ts}` ; l'ack réel reste celui de CBTP (idempotent par id, `ParentalSync.kt:150`). **Jamais** de corps de rapport dans `CBSY` (données de mineurs : une seule voie d'absorption, DESIGN-W6 § 2.7).
- `C/status/StatusIcons.kt:86` (`StatusIconModel`, `holdMs` 20 s, 6 visibles) : source de `ico`.
- `R/TvPrefs.kt` (préférences visibles), `C/net/NetState.kt` (`NetState`, `netJson`) : source de `set` (injectée par interface, pas d'import `R/`).
- DESIGN-W5 § 4.5 (2) (`TvShopCache` lit `/api/activation/request`, `/api/rental`, `/api/tokens/report`) : `shop` pousse ces lectures.

## Fichiers possédés
Nouveaux : `C/link/domains/ParDomain.kt`, `C/link/domains/ShopDomain.kt`, `C/link/domains/SetDomain.kt`, `C/link/domains/IcoDomain.kt`, `CT/link/DomainsBTest.kt`. **Hors zone** : `C/parental/**`, `C/status/**`, `C/net/**` (lecture seule).

## Signatures à respecter
```kotlin
class ParDomain(val side: Side, val pendingIds: () -> List<Triple<String, String, Long>> /* id, kind, ts */, val onPending: (List<String>) -> Unit) : SyncDomain
class ShopDomain(val side: Side, val snapshot: () -> Map<String, String>?, val onApply: (Map<String, String>) -> Unit) : SyncDomain   // snapshot null = W5 absent
class SetDomain(val side: Side, val read: () -> Map<String, String>, val write: (Map<String, String>) -> Unit) : SyncDomain       // champs : name, wdEnabled, autostart, lang, quotaMb, assist, net
class IcoDomain(val side: Side, val icons: () -> List<Map<String, String>>, val onIcons: (List<Map<String, String>>) -> Unit) : SyncDomain   // kind, tech, label, until
```

## Étapes
1. `ParDomain` : TV détient ; entrées `id=<reportId>`, `kind`, `ts` ; digest = liste triée des ids ; `apply` téléphone ⇒ `onPending(ids)` (w7-18 déclenche le tirage CBTP/HTTP **immédiat** au lieu d'attendre 15 min) ; un id acquitté par CBTP disparaît au `Delta` suivant.
2. `ShopDomain` : TV détient `tokens` (`tokensSeq, walletState`), téléphone détient `vouchers` (`pending=n`) ; `snapshot()==null` ⇒ `state()` = seq 0, digest de l'état vide, et le domaine n'est **pas** annoncé dans `caps` (l'engine lit `present()`; ajouter `val present: Boolean` à l'adaptateur).
3. `SetDomain` : TV détient `name, wdEnabled, autostart, lang, quotaMb, assist (connectée|hors ligne|attente conditions), net (wifi|ethernet|phone|none)` ; téléphone détient `phoneName, cdm (yes|no|unsupported)` ; `apply` écrit **seulement** les champs de l'autre détenteur.
4. `IcoDomain` : TV détient ; entrée par icône, `until` (ms) ; coalescé 1 s par l'appelant ; téléphone : `onIcons` ⇒ puce « 2 téléphones, clé USB » dans « Connexion ».
5. Tests (≥ 30) : idempotence, digest stable, détenteur gagne, `ShopDomain` absent, `ParDomain` : id acquitté disparaît, aucun champ interdit (test de liste noire : aucune entrée ne contient `body`, `title`, `pkg`, `app`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.DomainsBTest'   # vert, ≥ 30
grep -n 'body\|"title"' android/core/src/main/kotlin/castbridge/core/link/domains/ParDomain.kt | wc -l   # 0
```

## Cas limites
`ReportOutbox` vide ⇒ snapshot vide (digest constant) ; `IcoDomain` > 50 icônes ⇒ les 50 plus récentes ; `SetDomain.name` ≤ 60 caractères (`PhoneName.sanitize`).

## À ne pas faire
Ne pas transporter de rapport parental, de PIN, de jeton, d'adresse ; ne pas modifier `ParentalSync.kt`.

## Rapport
`STATUT`, signatures, table champ → détenteur.
