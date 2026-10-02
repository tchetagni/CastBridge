# w14-11 — CastBridge (téléphone) et CastBridge-TV : marqueurs logcat structurés `CB_JOURNEY` aux points de décision des parcours (sans secret, lisibles par la fumée)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-f** (vague W14, tranche S2) · prérequis : w14-06 fusionné (mêmes fichiers) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :receiver:compileDebugKotlin && cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.lint.JourneyMarkersLintTest'`
> **Jauge : ≈ 300 k jetons entrée / 14 k sortie** (effort S) · audit Opus : non

**Vague 14f (Android) · Effort S (≈ 0,75 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 2.2 (famille Logcat), D-W14-7. Branche `claude/sonnet-w14-11`. Rapport : `docs/agent-reports/sonnet-w14-11.md`.

## Objectif
Un seul tag `CB_JOURNEY`, une ligne `Log.i` par **étape nommée** d'un parcours, format `CB_JOURNEY <étape> k=v k=v`, jamais de secret (PIN, jeton, IP complète, nom de fichier complet : haché court), ≤ 1 ligne/s en régime permanent, actif aussi en release. La fumée (w14-08/09) et le propriétaire (`adb logcat -s CB_JOURNEY`) lisent la même chose.

## Pourquoi (preuves)
- Aucun marqueur structuré aujourd'hui (tags épars : `TvLink`, `UploadService`, `CastBridgeTV`, `CastBridgeBT`… ; rapport d'inventaire) ; le diagnostic R-03 a dû se faire sur « reprise de Smart TV: Refused » (`S/TvLink.kt:154`) ; le code de refus n'était pas journalisé (corrigé cf47cc0).
- Les parcours (`docs/test-plans/PARCOURS-CRITIQUES.md`) nomment les étapes ; la fumée doit pouvoir asserter « `xfer.finish` ou `xfer.blocked` est présent » (jamais disparition muette, F1).
- Règles de télémétrie/journal : `docs/TELEMETRY.md:55-60` (clés interdites : ip, mac, ssid, token, pin), `C/trust/Diagnostics.kt:45-53` (`Redact.scrub`).

## Fichiers possédés
Nouveaux : `S/link/JourneyLog.kt` (`object JourneyLog { fun mark(step: String, vararg kv: Pair<String, Any?>) }` : formatage, `Redact.scrub`, limitation 1/s par étape), `R/JourneyLog.kt` (même code, paquet TV ; **pas** dans `:core` car `android.util.Log`), `CT/lint/JourneyMarkersLintTest.kt` (source : chaque étape de la liste ci-dessous apparaît **au moins une fois** dans `S/**` ou `R/**` ; aucun appel `mark(` avec un argument nommé `pin`, `token`, `ip`). Zones (une ligne par point, pas d'autre changement) : `S/TvLink.kt` (`link.step state=… route=…`, `link.refused code=… hint=…`, `link.reassociate`, `link.adopted`), `S/UploadService.kt` et `S/TransferQueueService.kt` et `S/BtUploadService.kt` (`xfer.begin name8=… total=… via=…`, `xfer.progress pct=…` **toutes les 10 %**, `xfer.finish`, `xfer.blocked reason=…`, `xfer.cancel`), `S/OpenWithActivity.kt` (`openwith.decide route=… copy=… move=… savedCount=… pinTv=bool`), `S/ActivateTvActivity.kt` (`activation.view label=… locked=…`), `S/PinStore.kt` (`pin.stored keys=N` ; **jamais** la valeur), `R/TvService.kt` (`tv.ready version=… locked=…`, `tv.receive.begin/progress/finish name8=… via=…`), `R/BtServer.kt` (`tv.bt.hello result=… hint=…`), `R/ActivationCenter.kt` (`tv.activation state=…`), `R/UpdateInstaller.kt` (`tv.update verdict=… from=… to=…`). **Hors zone** : `C/**`, tout autre fichier.

## Étapes
1. `JourneyLog` : `Log.i("CB_JOURNEY", "$step " + kv.joinToString(" ") { "${it.first}=${Redact.scrub(it.second.toString())}" })` ; `name8` = 8 premiers caractères du SHA-256 du nom ; limitation par `step` (dernier horodatage).
2. Poser les marqueurs **aux points existants** (après le calcul de l'état, avant l'affichage) ; pour `xfer.progress`, dans le même endroit que la notification (w14-06 : `XferTexts`), pas de nouveau thread.
3. Lint : liste figée des 18 étapes dans le test ; échec si une manque ou si un `mark(` contient `"pin"`, `"token"`, `"ip"` comme clé.
4. `docs/test-plans/PARCOURS-CRITIQUES.md` n'est pas modifié (lecture) ; le rapport donne la correspondance étape → parcours.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn 'CB_JOURNEY' android/sender/src/main/kotlin android/receiver/src/main/kotlin | wc -l` ≥ 20 ; `grep -rn 'mark(' android/sender/src/main/kotlin android/receiver/src/main/kotlin | grep -E '"pin"|"token"|"ip"' | wc -l` ⇒ 0.

## Cas limites
Release : `Log.i` reste (pas de `if (BuildConfig.DEBUG)`), volume limité ; ProGuard : pas de `-assumenosideeffects` sur `Log.i` dans `receiver/proguard-rules.pro` (lire ; si présent, le signaler : STATUT BLOQUÉ, ne pas éditer).

## À ne pas faire
Aucun changement de comportement ; aucune donnée personnelle ni secret ; aucun marqueur dans `:core` ; pas de nouveau tag autre que `CB_JOURNEY`.

## Rapport
`STATUT`, table étape → fichier:ligne → parcours, sortie du lint, exemple de 10 lignes attendues pour P-11 (fabriqué à la main, marqué « attendu »).
