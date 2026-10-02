STATUT: TERMINÉ
CAHIER: sonnet-w6-04 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w6-04
PORTE: gradle :core:test --tests 'castbridge.core.owner.SuperSessionTest' → VERT (14 cas)
SUITE COMPLÈTE: :core:test → VERT (2003 tests, 0 échec)
FICHIERS: android/core/src/main/kotlin/castbridge/core/owner/SuperSession.kt ; android/core/src/test/kotlin/castbridge/core/owner/SuperSessionTest.kt ; docs/agent-reports/sonnet-w6-04.md

API publique (pour w6-01 `superActive`, w6-19)
- `enum SuperDuration(ms) { H1, H4, H12, H24 }` ; `SuperSession.MAX = H24`, `DEFAULT = H12`, `MAX_FAILURES = 5`.
- `SuperSession(clock: TvClock, audit: AuditChain? = null, state: State? = null, random)` ; `State(openedAt, untilMs, nonce)`.
- `open(d, nowWall): State` · `close(reason, nowWall)` · `active(nowWall)` · `remainingMs(nowWall)` · `noteFailure(nowWall): Boolean` (vrai si fermée) · `noteSuccess()`.
- `encode()` = `"v1 openedAt until nonce"` (vide si aucune session) ; `SuperSession.decode(text, clock, audit?)` : null si altéré (champ, version, nonce non 32 hex, until <= openedAt, durée > 24 h).
- Audit : `super.open` (cible = nom de la durée) et `super.close` (cible = raison), `AuditChain.verify()` vrai.

NOTE pour w6-01/w6-19 : le brief écrit `open(d)` ; l'heure murale est passée explicitement (`nowWall`), comme `TvClock.now(clock)`.
RISQUE: la détection d'un recul d'horloge dépend de `TvClock.observe(...)` appelé régulièrement côté Android (comme la TV, toutes les 5 min) ; sans observation, un recul inférieur à l'avance déjà écoulée n'est pas vu. Le test d'observation le montre. Le scellement du blob et l'effacement du fichier sont à faire côté Android (w6-19).
AUTOCONTRÔLE: zone ok · porte ok · aucun secret · aucune dépendance · FR · 2 fichiers de code · un commit · aucun `import android`
