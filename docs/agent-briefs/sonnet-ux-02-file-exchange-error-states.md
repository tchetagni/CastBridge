# ux-02 — CastBridge (téléphone) : « Échange de fichiers » — une erreur n'est plus montrée comme « vide », plus d'impasse « vérification de l'espace… »
<!-- routage Opus 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (gel : autorisé, états d'écran par fonction pure)
> **Groupe : UX-a** · prérequis : `claude/ux-ergonomie` fusionné · porte : `cd android && bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline :core:test --tests 'castbridge.core.ux.ExchangeStateTest' :sender:compileDebugKotlin`
> **Jauge : ≈ 110 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

Conception : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` § 1.2 (laissés), § 3 rang 12. Branche `claude/sonnet-ux-02`. Rapport : `docs/agent-reports/sonnet-ux-02.md`.

## Objectif
Le dialogue « Échange de fichiers » (`S/TvTransferScreen.kt`, ouvert par l'accueil « CastBridge TV » › « Échanger des fichiers ») distingue **vide** de **injoignable**, ne reste jamais bloqué sur « vérification de l'espace… », et montre l'échec d'un envoi.

## Pourquoi (preuves)
- `S/TvTransferScreen.kt:61-62` : `runCatching { storage = … }` / `runCatching { tvFiles = … }` avalent l'erreur ; `:182` affiche alors « Aucun fichier sur la TV. ».
- `:70` : un `checkStorage` qui échoue laisse la ligne « vérification de l'espace… » pour toujours et « Envoyer » grisé (impasse).
- `:142-153` : `UploadService.State.Failed` n'est pas affiché dans ce dialogue.

## Fichiers possédés
- Nouveau : `C/ux/ExchangeState.kt`, `CT/ux/ExchangeStateTest.kt`.
- Modifié : `S/TvTransferScreen.kt` (garder la dernière erreur de lecture, appeler les fonctions pures, afficher).

## Étapes
1. Rouge d'abord : `ExchangeState.listLine(loaded: Boolean, error: String?, count: Int): String?` ⇒ « TV injoignable pour l'instant : <cause>. Vérifiez qu'elle est allumée, sur le même Wi-Fi, avec CastBridge-TV ouvert. » quand `error != null`, « Aucun fichier sur la TV. » seulement quand `loaded && count == 0 && error == null`, « Chargement… » avant le premier chargement ; `ExchangeState.check(result: Result?): CheckLine` (en cours / ok / **échec avec raison et « Réessayer »**, `sendEnabled` vrai si l'échec vient du réseau : la TV juge à l'envoi) ; `ExchangeState.failed(reason)`.
2. Câbler : conserver `lastError` à chaque boucle de 5 s ; afficher la ligne ; bouton « Réessayer » qui relance le contrôle ; afficher `Failed.reason` sous la progression.

## Critères d'acceptation
`:core:test` complet vert ; `:sender:compileDebugKotlin` vert ; P-20 (plus de place) inchangé ; aucune chaîne « Aucun fichier » affichée après une erreur réseau (test pur).

## Hors périmètre
La mise en page du dialogue, les voies de transfert (W8 abandonné), la bibliothèque de la TV.
