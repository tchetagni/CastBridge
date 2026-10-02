# w4-08 — CastBridge-TV : câblage du mode réduit (routes, tuiles, écran, réévaluation périodique)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w4-07)
> **Groupe : W4b-2** (vague W4b) · prérequis : w4-07 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 4b · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-07).** Conception : `docs/coordination/DESIGN-W4-MODE-DEGRADE.md` § 4, § 5. Branche `claude/sonnet-w4-08`. Rapport : `docs/agent-reports/sonnet-w4-08.md`.

## Objectif
Une TV dont la clé de production est terminée continue de lire ses médias, de recevoir le streaming du téléphone et d'ouvrir ses contenus achetés ; tout ce qui **fait entrer** quelque chose est fermé avec un message clair ; la tuile « Renouveler la clé » est première ; l'écran de renouvellement se propose une fois par jour ; la bascule se produit **en cours de fonctionnement** (pas seulement au redémarrage).

## Pourquoi (preuves)
- `R/PlayerActivity.kt:97` : `Locked`/`Grace` ⇒ `ActivationActivity` ; `:539-541` tuile d'essai ; `R/ActivationActivity.kt:83,117,177`.
- `R/TvService.kt:175,196` (`locked()` bloque le cœur), `:258` `routeGuard` (essai seulement), `:181` `rentalTick` 15 min, `:808` JSON `/api/activation`.
- Appels de `trial()` : `R/BtServer.kt:122`, `R/UsbImporter.kt:39,49,57,89`, `R/SshControl.kt:43`, `R/Games.kt:43`, `R/ParentalHub.kt:271-274`, `R/PlayerActivity.kt:539`, `R/LanguesActivity.kt` (tuile grise w2-04), `R/ActivationCenter.kt:90`.
- Audit A1-7 (verrou évalué au démarrage seulement), UX-4.

## Fichiers possédés
`R/ActivationCenter.kt`, `R/TvService.kt`, `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `R/ActivationActivity.kt`, `R/BtServer.kt`, `R/UsbImporter.kt`, `R/SshControl.kt`, `R/Games.kt`, `R/ParentalHub.kt`, `R/LanguesActivity.kt`, `R/KeyBadgeOverlay.kt`, `android/core/src/main/resources/castbridge/admin.html`. **Hors zone** : `C/**` (w4-07, w4-09), `R/RentalHub.kt`, `R/LearnHub.kt`, `R/LotsHub.kt`, manifeste, docs.

## Étapes
1. `ActivationCenter` : `enum Restriction { TRIAL, DEGRADED }` ; `restriction(): Restriction?` ; `reduced()` ; `degraded()` ; `policyMessage()` ; `trial()` conservé ; `refreshState(): Boolean` (vrai si l'état a changé depuis le dernier appel : mémoriser la classe d'état) ; `dailyPromptFor(state)` (clé de préférence distincte pour `Degraded`).
2. `TvService` : `routeGuard` par `restriction()` ; dans `rentalTick` (toutes les 15 min) : `if (ActivationCenter.refreshState())` ⇒ `notice(policyMessage())`, `broadcast` de rafraîchissement d'accueil (mécanisme existant de `setStatus`/`statuses`), et si le nouvel état est `Locked` (essai terminé) : arrêter le cœur comme au démarrage verrouillé (`stopCore()` s'il existe, sinon `watchForActivation` + fermeture du serveur : vérifier ce qui est réversible et le dire) ; `/api/activation` : `"degraded"`, `"endedAt"` (via `statusFields` déjà étendu par w4-07).
3. `BtServer.acceptFile`, `UsbImporter` (4 sites), `SshControl` : `reduced()` + `policyMessage()` (ou `TrialPolicy.*_MESSAGE` / `DegradedPolicy.*` selon la restriction : ajouter `DegradedPolicy.USB_MESSAGE`, `SSH_MESSAGE`, `BT_MESSAGE` si w4-07 ne les a pas définis : les proposer dans le rapport, ne pas modifier `C/`).
4. `Games.visible()` : `DegradedPolicy.gameAllowed` en mode réduit ; `ParentalHub.TRIAL_CLOSED_LABELS` → filtrer par la politique courante (toujours par libellé, tant que w3-11 n'a pas introduit les ids ; ne pas élargir).
5. `PlayerActivity.homeTools` : tuile `upgrade` première avec `DegradedPolicy.UPGRADE_LABEL` et sous-titre `KeyBadge.endedSummary` ; tuiles `receive/usb/downloads` grisées (`on = false`, sous-titre « Clé à renouveler ») ; Langues selon `LanguesHub.hasContent` ; `onResume` : `dailyPromptFor(Degraded)` ⇒ `ActivationActivity` avec `EXTRA_UPGRADE` + nouvel extra `EXTRA_RENEW`.
6. `ActivationActivity` : en `Degraded` (ou `EXTRA_RENEW`) : titre `DegradedPolicy.UPGRADE_TITLE`, texte « Votre clé s'est terminée le … Vos vidéos et vos contenus achetés restent lisibles. Pour tout retrouver, demandez une nouvelle clé avec ce code : … », bouton « Continuer en mode réduit », **Retour autorisé** (garder le blocage de `Retour` pour `Locked` seulement, `:177`).
7. `KeyBadgeOverlay` : couleur ambre pour `ended` en mode réduit (contraste ≥ 4,5:1, w2-04).
8. `admin.html` : bandeau « Mode réduit : clé à renouveler » quand `degraded`.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK)
grep -n 'ActivationCenter.trial()' android/receiver/src/main/kotlin/castbridge/receiver/{BtServer,UsbImporter,SshControl,Games}.kt   # 0 hit (remplacés par reduced())
grep -n 'refreshState' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # ≥ 1
grep -n 'Renouveler la clé\|UPGRADE_LABEL' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # ≥ 1
```
Observable (émulateur ou TV, clé de production de **1 jour** émise avec l'outil de bureau, puis horloge avancée de 2 jours ou attente) : la TV reste sur l'accueil, badge « CLÉ TERMINÉE », bibliothèque lisible, « Lire en direct » depuis le téléphone fonctionne, « Recevoir du téléphone » refuse avec le message, `POST /api/upload` → 403, Quiz absent de « Jeux », Sudoku présent ; nouvelle clé ⇒ tout revient, achats conservés.

## Cas limites
- Leçon en cours au moment de la bascule : rien ne se ferme (Apprendre reste ouvert) ; seule la notice apparaît.
- Streaming en cours : non interrompu.
- `Locked` en cours de fonctionnement (essai terminé) : l'arrêt du cœur coupe le streaming : acceptable (c'est l'essai) ; le dire dans la notice avant (`KeyBadge.reminder` J-1).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `C/` ; ne pas rendre `Retour` possible en `Locked` ; pas de nouvelle icône ; textes en français ; « CastBridge-TV ».

## Rapport
`STATUT`, liste des 23 sites relus avec la décision pour chacun (`trial()` gardé / `reduced()`), captures émulateur si possible, ce qui n'a pas pu être vérifié sans TV.
