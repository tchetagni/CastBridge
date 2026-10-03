# ux-03 — CastBridge-TV : un échec de réception, une lecture « tout lire » ou une permission refusée se voient sur tout écran de CastBridge-TV
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus (module TV, sans appareil) · statut : PRÊT **sur décision du coordinateur** (gel : défaut prouvé « échec muet » ⇒ autorisé ; à valider sur la TV de référence)
> **Groupe : UX-b** (vague UX, TV) · prérequis : `claude/ux-ergonomie` fusionné · ordre : **séquentiel** avec w11-04 / w11-10 / w11-11 / w11-12 / w17-08 (tous touchent `R/PlayerActivity.kt`) · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.xfer.*' --tests 'castbridge.core.ux.*' :receiver:compileDebugKotlin`
> **Jauge : ≈ 200 k jetons entrée / 15 k sortie** (effort M, ≈ 1 j) · audit Opus : oui (échantillon)

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 2 (« Voir la progression »), § 3 rangs 10 et 17. Branche `claude/sonnet-ux-03`. Rapport : `docs/agent-reports/sonnet-ux-03.md`. Builds TV toujours **verrouillés** (`-PrequireActivation=true`) si un APK est produit ; aucune installation par l'agent.

## Objectif
1. Un **échec de réception** (« Échec de la réception : … ») s'affiche en **bannière** (même composant que « Vidéo reçue ✓ ») quel que soit l'écran de CastBridge-TV au premier plan, et reste lisible dans la puce d'en-tête jusqu'à la prochaine réception (pas 8 s).
2. « Tout lire » (`playAll`) et la demande de permission Wi-Fi Direct disent leur échec.

## Pourquoi (preuves)
- `C/tv/TransferProgress.kt:47` (`fail`), `:20,145` (`keepEndedMs = 8000`) ; `C/tv/ReceiverServer.kt:779,861,891,945,949` ; `R/TvService.kt:263,619` : la notice ne s'affiche que si `PlayerActivity` est devant (`screen.shown`) ; l'échec n'a **pas** de bannière (le succès en a une : `R/PlayerActivity.kt:841`, `R/TvCards.kt:288-303`).
- La plupart des lanceurs Android TV ne montrent pas les notifications (`R/TvService.kt:661-668`).
- `R/PlayerActivity.kt:669` (`playAll` dans `runCatching` sans message), `:698` (permission Wi-Fi Direct, idem).

## Fichiers possédés
- Modifié : `C/tv/TransferProgress.kt` (durée de rétention d'un **échec** : jusqu'à la prochaine réception ou 10 min), `C/xfer/ReceiveCard.kt` (texte de l'échec : nom + cause + « Renvoyez-le depuis le téléphone »), `R/TvService.kt` (relayer l'échec vers la bannière de l'écran courant ; repli : toast), `R/PlayerActivity.kt` (`flash` pour l'échec ; messages de `playAll` et Wi-Fi Direct).
- Tests : `CT/xfer/ReceiveCardTest.kt` (existant, étendre) ou nouveau `CT/ux/ReceptionFailureTest.kt`.

## Étapes
1. Rouge d'abord : la carte d'un échec reste dans `ReceiveCards.of(...)` après 8 s et jusqu'à 10 min ; son titre contient le nom du fichier et « Renvoyez-le depuis le téléphone » ; un succès suivant l'efface.
2. Bannière : `TvService` publie l'échec par le même canal que `receivedNotice` ; `PlayerActivity` et les autres activités qui ont une bannière l'affichent ; sinon `Toast` long (lisible à 3 m : 19 sp minimum).
3. `playAll` : « Lecture impossible : <raison> » ; Wi-Fi Direct : « Autorisation refusée : Wi-Fi Direct reste éteint. »

## Critères d'acceptation
`:core:test` complet vert, `:receiver:compileDebugKotlin` vert ; P-11/P-15/P-36 inchangés ; nouveau cas JVM « échec visible après 8 s » ; à confirmer **H** sur la TV de référence (bannière sur l'écran Apprendre pendant un échec).

## Hors périmètre
La forme de l'accueil (W11), les notifications du téléphone, les voies de transfert.
