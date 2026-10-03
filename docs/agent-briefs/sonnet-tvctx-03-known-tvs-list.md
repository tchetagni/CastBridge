# tvctx-03 — Liste des TV connues : joignable / identifiant valide / code à saisir (après le gel)
> **Modèle : sonnet** · escalade : échantillon Opus
> **Groupe : TVCTX-C** · prérequis : tvctx-01, tvctx-02 ; **levée du gel des écrans** par le propriétaire · porte : `:core:test --tests '*KnownTvs*'`
> **Jauge : ≈ 300 k jetons entrée / 16 k sortie** (effort M)

Règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, un commit, rapport `docs/agent-reports/<id>.md`, jamais `main`, ni serveur ni secret, français, « CastBridge » / « CastBridge-TV »). **Test rouge d'abord, par assertion** ; à la fin `:core:test` complet + `:sender:compileDebugKotlin` + `:receiver:compileDebugKotlin` par `tools/agents/gradle-lock.sh`. Lire d'abord `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md`. Garder R-01…R-10.

## Objectif
Une ligne par TV du carnet (`PinBook` + `SavedTvs`) : nom, **joignable** (sonde `GET /api/hello`), **identifiant valide** (jeton vivant ou code non refusé), **code à saisir** avec la cause de `CredentialDecision`, TV par défaut. Toucher une ligne = changer de TV (fiche ET contexte du cache tvctx-02). D'abord dans `ManageTvsDialog` existant.

## Fichiers (possédés)
- nouveau `android/core/src/main/kotlin/castbridge/core/trust/KnownTvs.kt` (pur : fusion des fiches `bt:` / `tv:` / `name:`, état par ligne, ordre) ;
- `android/sender/src/main/kotlin/castbridge/sender/TvHome.kt` : seulement `ManageTvsDialog`.

## Tests (rouges d'abord)
`KnownTvsTest` : TV de confiance + TV à code ⇒ deux lignes ; même TV vue par `bt:` et `tv:` ⇒ une ligne ; code refusé ⇒ « code à saisir : le code de la TV a changé » ; aucune donnée d'une TV dans la ligne d'une autre.
