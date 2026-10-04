# wios-07 — App iPhone : envoyer photos et vidéos à la TV : sélection, file persistée, HEIC → JPEG, progression (Live Activity, notifications), comportement en fond
<!-- routage architecte 2026-10-04 (vague iOS, ordre 5) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (états finaux de la copie, fichiers temporaires effacés) · statut : **ATTEND wios-03, wios-06**
> **Groupe : WIOS** (ordre 5, en parallèle de wios-08, wios-09, wios-tv-03) · porte : `tools/agents/gradle-lock.sh -- xcodebuild … CODE_SIGNING_ALLOWED=NO test` (simulateur, TV factice) ; partie fond et écran verrouillé : P-IOS-5/6/10 (humain)
> **Jauge : ≈ 600 k jetons entrée / 35 k sortie** (effort L, ≈ 2,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F4/F6/F8/F9/F23, R7, R8, R9, D-IOS-14). Moteur : `CBTransfer` (wios-03). Branche `claude/wios-07-envoi`. Rapport : `docs/agent-reports/sonnet-wios-07.md`.

## Objectif (autonome)
Faire de l'onglet « Envoyer » le cœur utile de l'app : choisir des photos/vidéos (Photos) ou des fichiers (Fichiers), choisir *Copier sur la TV et lire* ou *Copier sur la TV*, voir la progression partout (app, **Live Activity**, notification de fin/échec), et **dire la vérité** sur le fond : iOS suspend l'app ; la copie continue pleinement **app ouverte**, partiellement en fond, et reprend toujours au retour.

## Fichiers possédés
- **Nouveaux** : `ios/CastBridge/Send/{SendView,SendQueue,SendQueueStore,MediaImporter,HeicConverter,BackgroundUploader,ContinuedTask,SendNotifications,LiveActivityController}.swift` ; `ios/CastBridgeActivity/{CopyActivityAttributes,CopyActivityWidget,CastBridgeActivityBundle}.swift` ; `ios/CastBridgeUITests/SendFlowTests.swift`.
- **Zones** : `ios/CastBridge/Info.plist` (`NSSupportsLiveActivities = YES`, `BGTaskSchedulerPermittedIdentifiers` si `BGContinuedProcessingTask` est retenu) ; `ios/CastBridge/App/Tabs.swift` (une ligne : l'onglet « Envoyer » montre `SendView`).
- **Interdit** : `ios/CastBridgeKit/**` (lecture seule), autres dossiers de `ios/CastBridge/`, `ios/CastBridgeShare/**` (wios-08 : il dépose dans la file par l'API publique de `SendQueue` définie ici).

## Spécification
1. Sélection : `PhotosPicker` (multi, vidéos et photos, sans accès complet à la photothèque) ; `fileImporter` pour Fichiers ; ressource iCloud non locale ⇒ demande « Télécharger d'abord depuis iCloud (peut utiliser vos données mobiles) ? » ; copie temporaire dans le conteneur du groupe d'apps (`group.com.sti-cm.castbridge`), effacée à l'état final.
2. `HeicConverter` : HEIC/HEIF ⇒ JPEG qualité 0,9 (ImageIO), réglage « Convertir les photos HEIC en JPEG » activé par défaut ; vidéos HEVC envoyées telles quelles (v1).
3. `SendQueue` : file persistée (JSON dans le conteneur), une copie active à la fois par TV, ordre : *Copier et lire* d'abord ; états finaux de `CBTransfer` affichés tels quels ; « Réessayer » sur échec ; doublon ⇒ « Déjà sur la TV » + « Lire ».
4. Premier plan : `CBTransfer` direct ; `isIdleTimerDisabled = true` pendant une copie (rétabli après) ; bandeau « Gardez CastBridge ouvert pour une copie plus rapide ».
5. Passage en fond : `beginBackgroundTask` ; puis `BackgroundUploader` : `URLSession` d'arrière-plan (`background(withIdentifier: "com.sti-cm.castbridge.upload")`, `sharedContainerIdentifier` du groupe), blocs écrits en fichiers temporaires (`nextBlockJob`) confiés par **lots de 8** au plus, `isDiscretionary = false` ; `application(_:handleEventsForBackgroundURLSession:)` réveille, enregistre les résultats (`onBlockResult`) et remet un lot ; `finish` seulement au premier plan ou au réveil.
6. `ContinuedTask` : si `BGContinuedProcessingTask` existe dans le SDK local (iOS 26+ : **vérifier dans le SDK 27 et le dire au rapport**), l'utiliser pour une copie lancée par l'usager (progression système, expiration gérée ⇒ reprise) ; sinon absent.
7. Live Activity (iOS 16.1+) : nom du fichier, TV, pourcentage, débit, état ; mise à jour ≤ 1 fois / 2 s ; `staleDate` 2 min ; texte figé « Ouvrez CastBridge pour continuer » quand l'app est suspendue ; fin ⇒ « Copié sur Salon » 10 min.
8. Notifications locales (permission demandée à la première copie) : fin, échec avec raison ; aucune notification distante.

## Critères d'acceptation
- `SendFlowTests` (simulateur, `xcrun simctl addmedia` d'une vidéo de test et d'une HEIC, TV factice) : copie complète ⇒ « Terminé vérifié » ; HEIC arrivée en `.jpg` sur la TV ; coupure du serveur ⇒ « En attente de la TV », redémarrage ⇒ reprise ⇒ terminé ; fichiers temporaires absents après la fin.
- Build des deux cibles vert ; Live Activity visible dans le simulateur (capture au rapport).
- Le rapport dit précisément ce qui n'a pas pu être exercé (fond réel, écran verrouillé, iCloud) ⇒ P-IOS-5/6/7/10.

## À ne pas faire
- Promettre une copie complète en fond ; transcodage vidéo (v2) ; plus de 2 connexions ; garder un temporaire après l'état final ; notifications distantes ; accès complet à la photothèque.
