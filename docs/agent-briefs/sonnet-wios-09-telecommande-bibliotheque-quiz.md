# wios-09 — App iPhone : télécommande de base, bibliothèque de la TV, Quiz par la page `/quiz` de la TV
<!-- routage architecte 2026-10-04 (vague iOS, ordre 5) -->
> **Modèle : sonnet** · escalade : aucune · statut : **ATTEND wios-02, wios-06**
> **Groupe : WIOS** (ordre 5) · porte : `tools/agents/gradle-lock.sh -- xcodebuild … CODE_SIGNING_ALLOWED=NO test` (TV factice)
> **Jauge : ≈ 400 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F10/F11/F13/F14, D-IOS-9, D-IOS-10). Règle du jeu en ligne : `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` I-2 (aucun téléphone ne parle au service de jeu). Page Quiz : `docs/QUIZ.md` (routes `/quiz/*`, SSE). Branche `claude/wios-09-telecommande`. Rapport : `docs/agent-reports/sonnet-wios-09.md`.

## Objectif (autonome)
Remplir les onglets « Télécommande » et « Quiz », et l'écran « Bibliothèque de la TV », avec les routes HTTP existantes de la TV (aucun changement TV).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridge/Remote/{RemoteView,RemoteModel,LibraryView,LibraryModel,ThumbCache}.swift` ; `ios/CastBridge/Quiz/{QuizView,QuizWebView}.swift` ; `ios/CastBridgeUITests/{RemoteTests,QuizTests}.swift`.
- **Zone** : `ios/CastBridge/App/Tabs.swift` (deux lignes : onglets « Télécommande » et « Quiz »).
- **Interdit** : `ios/CastBridgeKit/**` (lecture), autres dossiers de l'app, extensions.

## Spécification
1. Télécommande : lecture/pause, −30/−10/+10/+30 s, barre de position (`seek`), volume (`/api/volume?pct=`), arrêt, suivant/précédent ; état par `GET /api/info` à 1 Hz **seulement** quand l'onglet est visible ; 409 `needsForeground` ⇒ « Ouvrez CastBridge-TV sur la TV » ; boutons grands (≥ 60 pt), retour haptique.
2. Bibliothèque : `GET /api/library` (vidéos, audio, autres), miniatures `/api/thumb` (202 ⇒ réessai à 2 s, 3 fois ; cache mémoire ≤ 30 Mo), « Lire sur la TV » (`/api/play?name=`), position de reprise affichée ; lecture seule (ni suppression ni renommage en v1).
3. Quiz : « Ouvrir le Quiz sur la TV » (`POST /api/quiz/open`, identifiant), puis `QuizWebView` (`WKWebView`) chargée sur `http://<ip de la TV>:<port>/quiz` ; **navigation limitée** à cette origine (toute autre URL refusée, pas de barre d'adresse) ; aucune donnée de site persistée (`WKWebsiteDataStore.nonPersistent()`) ; message si la page ne charge pas (TV ancienne, réseau).
4. Aucune adresse Internet : test de source sur `ios/CastBridge/Quiz/**` (seules des URL construites depuis l'IPv4 de la TV).
5. Mode démonstration (wios-06) : télécommande et bibliothèque branchées sur `DemoTv` ; Quiz en démonstration = page locale statique d'exemple embarquée (aucun réseau).

## Critères d'acceptation
- `RemoteTests` (TV factice) : lecture d'un fichier de la bibliothèque, pause, avance de 30 s (position changée), volume.
- `QuizTests` : la vue web charge `/quiz` de la TV factice ; une navigation vers `https://exemple.org` est bloquée.
- Aucune requête quand l'onglet n'est pas visible (compteur au rapport).

## À ne pas faire
- Appeler le service de jeu ou le portefeuille ; pistes/sous-titres (v2) ; télécommande Bluetooth/IR (impossible) ; suppression de fichiers.
