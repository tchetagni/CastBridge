# wios-08 — Extension de partage « CastBridge-TV » : envoyer depuis Photos, Fichiers et toute app, sans dépasser les limites de l'extension
<!-- routage architecte 2026-10-04 (vague iOS, ordre 5) -->
> **Modèle : sonnet** · escalade : aucune · statut : **ATTEND wios-06** (et l'API `SendQueue` de wios-07 : convenir de la signature au début, voir § Spécification 3)
> **Groupe : WIOS** (ordre 5) · porte : `tools/agents/gradle-lock.sh -- xcodebuild … CODE_SIGNING_ALLOWED=NO test` ; vidéo de 2 Go : P-IOS-11 (humain)
> **Jauge : ≈ 400 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F7, § 5.1 point 2). Équivalent Android : `android/sender/src/main/kotlin/castbridge/sender/ShareToTvActivity.kt`. Branche `claude/wios-08-partage`. Rapport : `docs/agent-reports/sonnet-wios-08.md`.

## Objectif (autonome)
Dans la feuille de partage d'iOS, « CastBridge-TV » doit apparaître pour les photos, vidéos et fichiers. L'extension a peu de mémoire (≈ 120 Mo) et peu de temps : elle **ne copie pas vers la TV**. Elle montre la TV cible, le choix *Copier sur la TV et lire* / *Copier sur la TV*, dépose les éléments dans le conteneur du groupe d'apps et les inscrit dans la file de l'app ; l'envoi part à l'ouverture de l'app (ou par la session d'arrière-plan partagée si wios-07 l'expose).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridgeShare/{ShareViewController,ShareView,ShareImporter,ShareHandoff}.swift`, `ios/CastBridgeShare/Info.plist` (zone : `NSExtensionActivationRule` : images ≤ 50, vidéos ≤ 20, fichiers ≤ 20), `ios/CastBridgeShare/CastBridgeShare.entitlements` (zone : groupe d'apps) ; `ios/CastBridgeUITests/ShareExtensionTests.swift`.
- **Interdit** : `ios/CastBridge/**` sauf lecture ; `ios/CastBridgeKit/**` (lecture).

## Spécification
1. `ShareImporter` : `NSItemProvider.loadFileRepresentation` (jamais `loadDataRepresentation` pour une vidéo) ; copie en flux vers `<conteneur>/Inbox/<uuid>/<nom>` ; si la copie dépasse 20 s ou 80 Mo de mémoire résidente estimée, s'arrêter proprement et inscrire une **référence** quand le fournisseur la permet, sinon message « Ouvrez CastBridge et choisissez le fichier depuis Envoyer ».
2. TV cible : lecture de la TV par défaut via `TvBook` (wios-05) **en lecture seule** (le trousseau est partagé par le groupe d'accès du groupe d'apps : documenter la clé `keychain-access-groups` nécessaire au rapport ; si non disponible sans équipe, lire seulement le nom de TV dans un fichier non secret du conteneur écrit par l'app).
3. `ShareHandoff` : écrit `<conteneur>/Inbox/<uuid>/item.json` `{nom, taille, choix, tvId, créé}` ; l'app (wios-07, `SendQueue.importInbox()`) le consomme à l'ouverture ; aucun secret dans ce fichier.
4. Texte de fin : « Ajouté à la file de Salon. Ouvrez CastBridge pour lancer la copie. » ; bouton « Ouvrir CastBridge » (schéma d'URL `castbridge-app://send`, déclaré par l'app ; zone `Info.plist` de l'app : `CFBundleURLTypes`, une entrée).
5. HEIC : la conversion se fait dans l'app (wios-07), pas dans l'extension.

## Critères d'acceptation
- `ShareExtensionTests` (simulateur) : partage de 2 photos et d'une vidéo de test depuis Photos ⇒ 3 `item.json` ⇒ l'app les importe ⇒ file de 3 éléments.
- Mémoire mesurée au rapport (Instruments ou `os_proc_available_memory`) pour une vidéo de 200 Mo dans le simulateur.
- Aucun secret dans le conteneur hors trousseau (test de source + inspection au rapport).

## À ne pas faire
- Envoyer vers la TV depuis l'extension ; afficher la liste des TV avec leurs adresses ; garder des fichiers de l'Inbox après import.
