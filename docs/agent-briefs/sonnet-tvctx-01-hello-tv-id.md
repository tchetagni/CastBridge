# tvctx-01 — Identifiant stable de la TV dans `/api/hello` et fiche du téléphone par cet identifiant
> **Amendement W19 (Fable, 2026-10-03)** : ce cahier n'a pas été exécuté (aucun rapport au 2026-10-03). Le cahier **w19-02** (`sonnet-w19-02-hello-caps-cache-nudges.md`) **absorbe** l'ajout de `id` dans `/api/hello` avec les autres champs additifs (`protocol`, `appVersion`, `versionCode`, `caps`…) par `HelloV2.body()`. Si tvctx-01 est lancé **avant** w19-02 : produire `id` par une fonction `HelloV2`-compatible (`C/sync/HelloV2.kt` minimal, nommé dans le rapport) et w19-02 se rebasera ; si w19-02 est fusionné d'abord : tvctx-01 ne refait **pas** `/api/hello` et ne garde que la partie « fiche du téléphone par identifiant ». Règle de symbiose : `SYMBIOSE: cap=— (champ additif public) · proto=inchangé · reason=— · deux écrans=—`.
> **Modèle : sonnet** · escalade : audit Opus (route publique, identité)
> **Groupe : TVCTX-A** (après le gel ou en correctif terrain) · prérequis : `claude/pin-persistence` fusionnée · porte : `:core:test --tests '*PinBook*' --tests '*HelloCompat*' --tests '*ReceiverServer*'`
> **Jauge : ≈ 250 k jetons entrée / 12 k sortie** (effort S-M)

Règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, un commit, rapport `docs/agent-reports/<id>.md`, jamais `main`, ni serveur ni secret, français, « CastBridge » / « CastBridge-TV »). **Test rouge d'abord, par assertion** ; à la fin `:core:test` complet + `:sender:compileDebugKotlin` + `:receiver:compileDebugKotlin` par `tools/agents/gradle-lock.sh`. Lire d'abord `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md` et `docs/agent-reports/pin-persistence.md`. Ne jamais affaiblir l'authentification ; aucun code ni jeton dans un journal, une notification, une sauvegarde ; garder R-01…R-10.

## Objectif
Deux TV **du même modèle** jointes par le code (nom de service `"CastBridge TV " + Build.MODEL`, `R/TvService.kt:1032`, que le mDNS suffixe « (2) » au hasard des redémarrages) ne doivent jamais échanger leurs codes. La TV donne un identifiant stable public ; le téléphone range la fiche sous cet identifiant.

## Fichiers (possédés)
- `android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt` : seulement la réponse de `GET /api/hello` + un paramètre de constructeur `tvId: String? = null` (additif).
- `android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt` : passer `tvId = TvIdentity.publicId(trust.installId)`.
- nouveau `android/core/src/main/kotlin/castbridge/core/trust/TvIdentity.kt` : `publicId(installId) = sha256("cbtv-id|" + installId).take(16)` (jamais l'`installId` lui-même : il sert aux locations).
- `android/core/src/main/kotlin/castbridge/core/trust/PinBook.kt` : `tvId` accepte un identifiant de TV (`tv:<publicId>`) prioritaire sur `name:` ; `link` relie `name:`/`host:` à `tv:` ; une fiche `bt:` dont `publicId(installId)` vaut ce `tv:` est la même TV.
- `android/sender/src/main/kotlin/castbridge/sender/TvDiscovery.kt` : après résolution mDNS, un `GET /api/hello` (déjà public) lit `tvId` (absent sur une TV ancienne : rien ne change).

## Tests (rouges d'abord)
`PinBookTest` : deux TV « CastBridge TV M1 » / « … (2) » dont les noms s'échangent ⇒ chaque code reste à sa TV par `tvId` ; TV ancienne sans `tvId` ⇒ comportement actuel. `HelloCompatTest` : un téléphone ancien ignore le champ. Serveur : `/api/hello` contient `tvId` (16 hex), jamais l'`installId`.

## Interdits
Aucune nouvelle route (pas de changement de `tools/routes/routes.txt`) ; `/api/hello` reste sans authentification et sans autre donnée.
