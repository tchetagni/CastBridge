# Rapport : lots Langues, serveur et tuyaux de téléchargement (2026-10-02)

Branche `claude/langues-lots-pipelines` (depuis `integration/agents` a052464), non poussée. Aucune action sur le serveur de production, la TV ou adb ; aucun réseau ; aucun secret lu ni affiché.

## Ce qui a été fait

**Serveur** (`backend/…/lots/`)
- `LotService.FEATURES` = `learn`, `quiz`, `langues` ; messages « `learn`, `quiz` ou `langues` attendu » (dépôt et catalogue). Apprendre et Quiz inchangés (leur validateur reste `ZipLotValidator`).
- `LangLotValidator` : taille ≤ 3 Mo (plafond du consommateur TV, dans les 10 Mo du `LotBudget`), zip sain (réutilise `ZipLotValidator`), seulement `langue.json` + `media.json` (obligatoire : `langue.json`), `type` = `langue`, `format` 1, unités présentes, `id` et `version` égaux au scope et à la version déclarés, `target`/`source`/`level` cohérents avec le scope, scope `<cible>-<niveau>-<thème>-<départ>` avec langues différentes. Les lots `langues-media` sont refusés (autre fonction).
- `LotValidator.validate(file, size, scope, version)` : méthode par défaut qui ignore l'identité, donc aucun autre validateur ne change.
- Aucune migration Flyway : `V4__lots.sql` a `feature VARCHAR(16)` sans contrainte (dernier numéro V61, rien ajouté).

**Outil de publication** `tools/langues/publish_lots.py` : simulation par défaut ; construit via `gradle --offline :core:buildLangLots` (ou `--lots-dir`) ; contrôle taille, SHA-256, famille libre (refuse « réservé » et toute autre fonction) ; `--apply` : liste, puis `POST /api/v1/admin/lots` (multipart, non publié, `sha256` attendu) puis `/{id}/publish` ; jeton lu dans `CASTBRIDGE_ADMIN_TOKEN` (jamais affiché) ; HTTPS obligatoire, aucune redirection suivie ; idempotent (publié + même empreinte = sauté ; envoyé non publié = publié ; même version autre empreinte = erreur).

**Téléphone** (`LotsRuntime`, `LotsScreen`, `LotSync`, `LangLotConsumer`)
- Le chemin Langues par profil existait déjà (planificateur, `LotSync`, `LotStore`, livraison). Ajouts : liste « Disponibles sur le serveur » (catalogue signé) avec « Télécharger » lot par lot (`pickedLangues`, ajoutés au plan TV et protégés) ; hook `check` de `LotSync` (défaut : aucun) appelé avant rangement, branché sur `LangLotConsumer.verifyContent` (extrait de `install`, partagé avec la TV) : le téléphone ne garde jamais un lot que la TV refuserait.
- Quota : 100 Mo du `LotStore` (le quota média de 500 Mo n'est pas codé : les lots média ne sont pas livrés).

**TV connectée** (`TvLotFetcher`, `SecureHttpLotRemote` dans `core/lots/TvLotFetcher.kt`; `LanguesHub`, `LanguesActivity`)
- Bouton « Mettre à jour les lots Langues » (focusable, D-pad), affiché seulement si le système valide Internet, jamais d'exécution automatique. Progression « Lot i sur n », annulation, erreurs en français. Essai : l'écran est fermé et le bouton refuse aussi.
- Aucune route TV ajoutée : `TrialRoutesTest` et `tools/routes/routes.txt` inchangés.

## Tests
- Backend : `Lot*` 13 (dont `LotsLanguesApiTest` 3 et `LotLangValidatorTest` 2, qui valide les 46 packs réels) ; suite complète 212 tests, 0 échec.
- Python : `test_publish_lots.py` 11 (faux serveur, sans réseau). Suite `tools/tests` : 102, 3 échecs déjà présents et sans rapport (médias absents du worktree : `test_free_content` ×2, `test_content_tools` ×1).
- core : `TvLotFetcherTest` 17, `SecureHttpLotRemoteTest` 4, `LotSyncContentCheckTest` 1 ; `:core:test` complet 2168 tests (1 échec corrigé : garde-fou réseau de `LearnLotsTest`, qui autorise maintenant `TvLotFetcher.kt` et vérifie que seul l'écran Langues l'utilise). `ReceiverTest.insufficientStorageIsFatal` se bloque indéfiniment dans cet environnement (sans rapport, test exclu du lancement par un script d'init hors dépôt).
- `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` : OK. Essai à blanc réel de `publish_lots.py` : 46 lots libres, 103 Ko au total.

## Points pour l'auditeur Opus (audit obligatoire : réseau + signature)
1. `TvLotFetcher.run` : la signature (`signedByAny(publicKeys)`) est vérifiée avant toute lecture du reste ; vérifier qu'aucun champ non signé n'est utilisé (le `channel` et le `feature` du catalogue sont dans la charge signée ; `family` n'existe pas dans le catalogue).
2. Filtrage strict `langues` + édition complète + `LangLots.parse(scope)` + ≤ 3 Mo ; jamais `learn`/`quiz`/réservé. La TV n'a pas de liste de familles : la garantie « libre seulement » repose sur le serveur (l'outil refuse les réservés ; le serveur n'a que des lots libres publiés) et sur le fait qu'un lot scellé ne serait pas un zip lisible. `families` optionnel dans le constructeur, non branché.
3. `SecureHttpLotRemote` : HTTPS seulement (http uniquement vers boucle locale avec drapeau de test), `instanceFollowRedirects=false` et 3xx = erreur, délais 10 s / 20 s, catalogue ≤ 1 Mo, `LotNames.valid` avant d'ouvrir l'URL. Pas de proxy branché (la TV via la passerelle SOCKS du téléphone n'est pas ce chemin).
4. Rejeu d'un vieux catalogue signé : sans effet (versions seulement croissantes, `TvLotStore` refuse le recul). Pas de contrôle de fraîcheur (`generatedAt`) volontairement : l'horloge des TV est souvent fausse.
5. Aucune éviction : un lot qui ne tient pas dans les 10 Mo est sauté (les données envoyées par le téléphone ne sont jamais supprimées). Reprise : `.part` du `inbox` du `TvLotStore`, vérification SHA-256 faite par le fetcher avant `installReceived` pour ne pas polluer la liste des refus.
6. Adresse du serveur de la TV : `TvConnect.link.state.baseUrl` ou `BuildConfig.DEFAULT_SERVER` ou la production ; un serveur de test en http (10.0.2.2) fait refuser le bouton avec un message (utiliser le téléphone).

## Risques et limites
- Non validé sur TV réelle (GaiaOS : TLS ancien ? `HttpURLConnection` en HTTPS vers `bridge.sti-cm.com` à vérifier sur la TV de référence).
- La TV téléchargera les 46 lots s'ils tiennent (≈ quelques centaines de Ko) : pas de choix par langue côté TV.
- Rien n'est publié : le propriétaire doit lancer `publish_lots.py --apply` (docs/LANGUES.md § 15).
