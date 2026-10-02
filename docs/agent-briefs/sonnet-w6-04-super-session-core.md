# w6-04 — `SuperSession` (cœur) : session super administrateur limitée, horloge monotone, scellée

**Vague 6a · Effort S (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 4.2, § 4.3. Branche `claude/sonnet-w6-04`. Rapport : `docs/agent-reports/sonnet-w6-04.md`.

## Objectif
Une session **pure et testée** : `SuperSession.State(openedAt, untilMs, nonce)`, durées 1 h / 4 h / 12 h (défaut) / 24 h (plafond), horloge `TvClock` (monotone : un recul n'allonge pas, un saut en avant > 45 j termine), `open(duration)`, `close()`, `active(now)`, `remaining(now)`, `onPasswordFailures(n)` (5 échecs consécutifs ⇒ fermée), `encode()/decode()` pour un blob que l'Android scelle (`SecretWrapper`/Keystore, w6-19), et un journal d'ouverture/fermeture via `AuditChain` existant.

## Pourquoi (preuves)
- `C/owner/SuperAdminGate.kt` (bcrypt, `Result.Open`), `C/owner/OwnerVault.kt:60-78` (`UnlockGuard`), `:80-102` (`AuditChain`) ; `OL/ConsoleActivity.kt:48-54` (verrou de 2 min **qui ne doit pas** fermer la session) ; `C/owner/Keys.kt:81` (`TvClock`).

## Fichiers possédés
Nouveaux `C/owner/SuperSession.kt`, `CT/owner/SuperSessionTest.kt`. **Hors zone** : `SuperAdminGate.kt`, `OwnerVault.kt`, `OL/**`, `S/**`.

## Étapes
1. `enum class SuperDuration(ms) { H1, H4, H12, H24 }` ; `MAX = H24`.
2. `class SuperSession(private val clock: TvClock, private val audit: AuditChain?)` : `open(d: SuperDuration): State` (nonce aléatoire 16 octets hex ; `audit.append(now, "super.open", d.name, "ok")`) ; `close(reason)` ; `active(nowWall)` = `clock.now(nowWall) < until` **et** pas de saut avant douteux ; `remainingMs` ; `noteFailure()`/`noteSuccess()` compteur d'échecs ; `encode()` = `"v1 openedAt until nonce"` ; `decode(text, clock)`.
3. Tests : ouverture 12 h ⇒ active à +11 h, inactive à +12 h ; recul de l'horloge murale de 10 h ⇒ toujours inactive après 12 h monotones ; saut avant de 60 j ⇒ inactive ; 5 échecs ⇒ fermée, 4 ⇒ ouverte ; `close` ⇒ inactive ; aller-retour `encode/decode` ; `AuditChain.verify()` vrai après ouverture/fermeture ; `H24` plafond (une durée plus longue est impossible par construction).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.SuperSessionTest'   # vert, ≥ 10 cas
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/owner/SuperSession.kt   # 0 hit
```

## Cas limites
`decode` d'un texte altéré ⇒ `null` (session absente, jamais « ouverte par défaut ») ; nonce non vérifié ici (le scellement est l'affaire d'Android) ; aucune donnée secrète dans `encode()`.

## À ne pas faire
Ne pas changer la durée du verrou de la console (2 min) ; ne pas lier la session à `KeyScope.SUPER_UNLIMITED` (portée pour la TV, sans rapport) ; pas d'Android.

## Rapport
`STATUT`, API publique (pour w6-01 `superActive`, w6-19), cas.
