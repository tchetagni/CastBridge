STATUT: TERMINÉ
CAHIER: copy-and-play (exception au gel, demande du propriétaire 2026-10-02) · MODÈLE: sonnet · BRANCHE: claude/copy-and-play · COMMIT: voir git log
PORTE: `:core:test` complet (inclut journey/lint) → VERT (2432 tests, 0 échec) ; `:sender:compileDebugKotlin` → VERT
ROUGE avant : `CopyAndPlayTest` 3/4 en échec par assertion (stub qui refuse tout) ; VERT après : 4/4 (18 lignes de table + balayage + 2 tests de liaison)
FICHIERS: android/core/src/main/kotlin/castbridge/core/trust/CopyAndPlay.kt ; android/core/src/test/kotlin/castbridge/core/CopyAndPlayTest.kt ; android/sender/src/main/kotlin/castbridge/sender/OpenWithActivity.kt ; docs/test-plans/PARCOURS-CRITIQUES.md (P-37) ; ce rapport
CHOIX: décision pure `CopyAndPlay.decide(Facts)` -> Enabled / Degraded / Disabled (étiquette, `copies`, une ligne de raison) ; `linkOf(SendFacts, SendChoice)` réutilise les états de `SendChoices` (session, chemin PIN vérifié, vérification = comme « Copier », aucune = désactivé)
CHOIX: lecture = `CastSession.start(..., CastAction.LIVE, item)` (même chemin que « Diffuser sur », le téléphone sert /media) ; copie = mêmes routes que `send()` (TransferQueue.enqueue ou UploadService.start, move=false) ; aucune modification de TransferQueue/UploadService/TvLink/PinStore
CHOIX: édition de la TV = UNKNOWN (le téléphone ne la connaît pas : `SendGuard` et la preuve TV ne sont pas dans ce code) ; la TV d'essai refuse la copie elle-même (403 essai). Dès qu'une source d'édition existe, il suffit de la passer à `Facts.edition`
CHOIX: chaque partie échoue seule (phrase unique en toast) ; après démarrage, ouverture de la télécommande (PlayerActivity ACTION_REMOTE)
NON FAIT / À VALIDER SUR MATÉRIEL: partage de bande passante lecture + copie (la copie ralentit le flux) ; veille du téléphone (services d'envoi au premier plan, à vérifier) ; échec asynchrone de la lecture visible seulement via l'état CastSession ; TV d'essai réelle (bouton dégradé non atteignable tant que l'édition est inconnue) ; TV à code en état « vérification » : l'adresse IP n'est connue qu'après la sonde (sinon phrase « la TV n'est pas encore jointe »)
FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
QUESTION: aucune

## Correction (copy-and-play-fix)
La première version dupliquait l'action existante : elle lançait `CastAction.LIVE` PLUS une copie séparée (`TransferQueue`/`UploadService`), deux flux se partageant la bande. Le comportement voulu est `CastAction.COPY` (« Copier sur la TV et lire »), déjà géré par `CastSession`. Corrigé dans `docs/agent-reports/copy-and-play-fix.md` : le bouton appelle `CastSession.start(…, how.action, item)` et rien d'autre.
