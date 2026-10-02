# w6-04-fix : correctifs de l'audit Opus de SuperSession

Branche `claude/sonnet-w6-04-fix` (depuis `claude/sonnet-w6-04`). Fichiers : `SuperSession.kt`, `SuperSessionTest.kt`.

1. Observation interne de l'horloge : `open`, `active` et `decode` appellent `clock.observe(nowWall)` ; `decode(text, clock, nowWall, audit)` relève le plancher avec `signedIssuedAt = openedAt`. Tests : recul sans observe externe, horloge neuve (base 0), plancher relevé.
2. Anti-gel de l'horloge : blob `v2 openedAt until nonce uptimeAtOpen failures` ; `active` exige `uptimeNow - uptimeAtOpen` dans `[0, durée[`. Un uptime inférieur à celui de l'ouverture (horloge non persistée) ferme la session au `decode` (raison « horloge »). v1 refusé (null = fermé). Test de l'attaque avec horloge simulée.
3. Compteur d'échecs dans le blob (0 à 4 accepté, sinon refusé). Contrat Android (w6-19) : resceller après chaque `open`, `close`, `noteFailure`, `noteSuccess`. Test aller-retour.
4. `activeState(nowWall): State?` ; quand `active` devient faux, `close("expiration")` une seule fois (audit, état null). Tests audit et idempotence.
5. `isAhead` ferme la session (« horloge »). Test.
6. `decode` refuse `openedAt > clock.now(nowWall) + 60 s`. Test.

Risques : l'uptime n'est pas persisté entre deux sauvegardes de `clock.txt` (5 min) ; un redémarrage avec horloge non rechargée ferme la session (sûr). `remainingMs` appelle `activeState` donc peut fermer.
