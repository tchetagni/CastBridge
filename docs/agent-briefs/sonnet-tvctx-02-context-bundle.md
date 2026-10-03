# tvctx-02 — `GET /api/context` : le contexte de la TV, en lecture seule, borné, mis en cache par TV
> **Modèle : sonnet** · escalade : audit Opus (nouvelle route authentifiée, essai)
> **Groupe : TVCTX-B** · prérequis : tvctx-01 · porte : `:core:test --tests '*Context*' --tests '*TrialPolicy*'` + `python3 -m unittest discover tools/tests`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M)

Règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, un commit, rapport `docs/agent-reports/<id>.md`, jamais `main`, ni serveur ni secret, français, « CastBridge » / « CastBridge-TV »). **Test rouge d'abord, par assertion** ; à la fin `:core:test` complet + `:sender:compileDebugKotlin` + `:receiver:compileDebugKotlin` par `tools/agents/gradle-lock.sh`. Lire d'abord `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md`. Ne jamais affaiblir l'authentification ; aucun code ni jeton dans un journal, une notification, une sauvegarde ; garder R-01…R-10.

## Objectif
« Les infos de session sont dans la TV » : chaque TV donne au téléphone son contexte en **une** lecture authentifiée (jeton ou code), que le téléphone garde **par `tvId`** pour l'affichage hors ligne (« vu le … »), jamais fusionné entre TV.

## Contenu (rien que le téléphone ne lise déjà)
`tvId`, nom, version, édition (`/api/activation`), profil enfant actif et état parental **sans le code parental** (`/api/parental/config/get`), lots (résumé de `/api/lots`), locations (résumé de `/api/rental`), file de réception (`/api/transfer/state`), réglages de stockage (`/api/storage`), `etag`. **≤ 64 Ko** (au-delà : listes tronquées et `truncated: true`). Jamais la liste des téléphones de confiance.

## Fichiers (possédés)
- nouveau `android/core/src/main/kotlin/castbridge/core/tv/TvContext.kt` (assemblage pur à partir de fournisseurs, borne de taille, `etag`) ; `ReceiverServer.kt` : une ligne de routage `path == "/api/context"` (GET).
- `tools/routes/routes.txt` (+ `/api/context`), `C/owner/TrialPolicy.kt` (lecture permise en essai), liste propriétaire si elle énumère les routes.
- nouveau `android/sender/src/main/kotlin/castbridge/sender/TvContextCache.kt` : fichier privé par `tvId` (`filesDir/tvctx/<tvId>.json`), **exclu** de `backup_rules.xml` et `data_extraction_rules.xml` (+ `tools/tests/test_backup_rules.py`).

## Tests (rouges d'abord)
`TvContextTest` : aucun champ secret (`pin`, `token`, code parental), borne 64 Ko, `etag` stable ; vrai `ReceiverServer` : 401 sans identifiant, 200 avec jeton ou code ; deux TV ⇒ deux caches distincts, jamais mélangés.
