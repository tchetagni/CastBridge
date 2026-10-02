STATUT: TERMINÉ
CAHIER: copy-and-play-fix · MODÈLE: sonnet · BRANCHE: claude/copy-and-play-fix · COMMIT: voir git log
CORRECTION: le bouton « Copier sur la TV et lire » appelle `CastSession.start(applicationContext, target, CastAction.COPY, item)` seul (chemin de PhoneLibrary et de la feuille de diffusion) ; plus de LIVE + copie parallèle.
DÉCISION PURE: `CopyAndPlay.Decision` porte `action` (COPY pour Enabled/Disabled, LIVE pour Degraded = essai, lecture seule + « La copie n'est pas disponible en version d'essai ») au lieu de `copies` ; libellé = `CastAction.COPY.label`. Édition TV inconnue (UNKNOWN) : se comporte comme COPY, la TV d'essai refuse l'envoi elle-même.
ROUGE: `CopyAndPlayTest` 2/4 en échec par assertion (Degraded renvoyant COPY) ; VERT: 4/4.
PORTE: `:core:test` complet → VERT (2432 tests, 0 échec) ; `:sender:compileDebugKotlin` → VERT
RETIRÉ: second flux (TransferQueue.enqueue / UploadService.start / takePersistableUriPermission), `LABEL_PLAY_ONLY`, `copies`, messages « lecture + copie », paramètre `choice` de `copyAndPlay`, import `CastAction` inutile.
FICHIERS: core trust/CopyAndPlay.kt ; core test CopyAndPlayTest.kt ; sender OpenWithActivity.kt ; docs/test-plans/PARCOURS-CRITIQUES.md (P-37) ; docs/agent-reports/copy-and-play.md ; ce rapport
RISQUES: la TV à code (non session) doit être jointe en IP (adresse connue après la sonde) sinon « la TV n'est pas encore jointe » ; le démarrage de la lecture pendant l'envoi reste à valider sur matériel ; échec asynchrone visible seulement via l'état de `CastSession`.
FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
