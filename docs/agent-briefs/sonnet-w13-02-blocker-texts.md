# w13-02 — `BlockerTexts` : la table française des 47 blocages (cause · une action · libellé du bouton · texte de puce), figée par test
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : aucune (table donnée mot pour mot, mécanique) · statut : PRÊT
> **Groupe : W13-a** (vague W13, tranche S1) · prérequis : w13-01 fusionné (enums `BlockerCode`, `Fix`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.BlockerTextsTest'`
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S) · audit Opus : non

**Vague 13a (cœur) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W13-AUCUN-BLOCAGE-SILENCIEUX-2026-10-02.md` § 2 (colonne « Message »), § 3.3 (textes de puce), § 3.5, § 3.7. Branche `claude/sonnet-w13-02`. Rapport : `docs/agent-reports/sonnet-w13-02.md`.

## Objectif
Un seul objet `BlockerTexts` (cœur, pur) qui donne, pour chaque `BlockerCode`, le **titre**, la **cause** (une phrase), **l'action** (une phrase impérative), le **libellé du bouton** (par `Fix`) et le **texte court de puce** ; les noms de TV, tailles, volumes, délais sont **injectés** depuis `BlockerCtx`. Les messages sont copiés **mot pour mot** de la colonne « Message (cause · action) » du § 2 : la partie avant « · » = cause, après = action.

## Pourquoi (preuves)
- Les textes existants restent la source pour les états fins de la liaison : `C/trust/LinkText.kt:52-108` (`untrustedAdvice`, `refused`, `absent`, `bluetooth`, `credentialExpired`) — **ne pas dupliquer** : `BlockerTexts.of(TV_REINSTALLED)` **délègue** à `LinkText.untrustedAdvice(HINT_OTHER_INSTALL)`, `TOKEN_REVOKED` à `HINT_SAME_INSTALL`, `BT_OFF` à `LinkText.bluetooth(OFF)`, `TV_APP_KILLED` à `LinkText.absent(SERVICE_ABSENT, tv)`.
- Règle de redaction : `C/trust/Diagnostics.kt:45-53` (`Redact`), `docs/TELEMETRY.md:55-58`.
- Nom des apps : « CastBridge » (téléphone), « CastBridge-TV » (TV), jamais sender/receiver.

## Fichiers possédés
Nouveaux : `C/link/BlockerTexts.kt`, `CT/link/BlockerTextsTest.kt`. **Hors zone** : `LinkText.kt`, `Blocker.kt`, `S/**`, `R/**`.

## Signatures à respecter
```kotlin
package castbridge.core.link
data class BlockerText(val title: String, val cause: String, val action: String, val button: String, val chip: String) { val sentence get() = "$cause $action" }
object BlockerTexts {
    fun of(b: Blocker): BlockerText                       // injecte ctx.tv, ctx.volume, ctx.missingBytes (TransferRule.size), ctx.retryAfterMs (secondes), ctx.tvMessage
    fun button(fix: Fix): String                          // ex. ASK_PIN → "Saisir le code de la TV", RETRY → "Réessayer", NONE → ""
    fun chip(tv: String, health: String /* "ready"|"code"|"trial"|"checking"|"away"|"blocked" */, route: String?): String   // "● Salon · prête (Wi-Fi)" …
    fun supportHint(code: String): String                 // "Code support : CB-…"
}
```

## Étapes
1. Copier la table du § 2 dans un `when (b.code)` (un cas par code, dans l'ordre du tableau) ; les variantes `<TV>`, `<volume>`, `<N>`, `<taille>`, `<ETA>`, `<débit>`, `<fichier>`, `<lot>`, `<raison TV>` deviennent des injections ; valeur manquante ⇒ formulation sans le nombre (jamais « null »).
2. Boutons par `Fix` (table figée) : `ASK_PIN` « Saisir le code de la TV » ; `REPAIR_PAIRING` « Réassocier » ; `HELLO_AGAIN`/`REDISCOVER_IP`/`WAIT_WAKE`/`RETRY_AFTER`/`RETRY` « Réessayer » ; `OPEN_TV_APP` « J'ai ouvert CastBridge-TV » ; `FREE_SPACE`/`OTHER_VOLUME` « Choisir la destination » ; `REINSERT_USB` « Clé remise » ; `CHECK_TV_CLOCK` « Vérifier l'heure sur la TV » ; `UPDATE_TV` « Mettre la TV à jour » ; `UPDATE_PHONE` « Mettre CastBridge à jour » ; `PARENTAL_ON_TV` « Compris » ; `PRODUCTION_KEY`/`ACTIVATE_TV`/`RENEW_KEY` « Activer la TV » ; `WIFI_SETTINGS` « Ouvrir le Wi-Fi » ; `DISABLE_VPN` « Ouvrir les réglages VPN » ; `BATTERY_SETTINGS` « Autoriser en arrière-plan » ; `KEEP_SCREEN_ON` « Garder l'écran allumé » ; `RENAME_FILE` « Renommer » ; `REPICK_FILE` « Choisir le fichier » ; `FORMAT_EXFAT` « Compris » ; `USE_IP` « Utiliser l'adresse IP » ; `NONE` « ».
3. Puce : `ready` ⇒ « ● <TV> · prête (<route>) » ; `code` ⇒ « ● <TV> · code à saisir » ; `trial` ⇒ « ● <TV> · essai : copies fermées » ; `checking` ⇒ « ◐ <TV> · vérification… » ; `away` ⇒ « ○ <TV> · hors de portée » ; `blocked` ⇒ « ✕ <TV> · action requise ».
4. `BlockerTextsTest` : (a) chaque `BlockerCode` a titre, cause, action non vides ; (b) chaque `Fix` a un bouton (sauf `NONE`) ; (c) aucun texte ne contient `Regex("[0-9]{6}")`, `cbk_`, `Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")`, « sender », « receiver » ; (d) injection : `ctx.tv = "Salon"` apparaît dans la phrase ; `missingBytes = 2 Gio` ⇒ « 2,0 Go » ; (e) **table figée** : un fichier `EXPECTED` (code → phrase) dans le test ; un changement de texte fait échouer le test (c'est voulu).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'BlockerCode\.' android/core/src/main/kotlin/castbridge/core/link/BlockerTexts.kt` ≥ 47 ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/ | wc -l` ⇒ 0.

## À ne pas faire
Pas de reformulation des messages du § 2 ; pas de texte dans un autre fichier ; pas de modification de `LinkText.kt`.

## Rapport
`STATUT`, nombre de codes couverts, délégations à `LinkText`, écarts (codes ajoutés par w13-01 hors § 2 : `BT_OFF`, `CANCELLED`, `PHONE_PERMISSION`, `TV_RESTARTING` : textes proposés dans le rapport).
