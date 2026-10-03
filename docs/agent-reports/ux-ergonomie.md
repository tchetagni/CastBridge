# Rapport : ergonomie et navigation (`claude/ux-ergonomie`, 2026-10-03)

Demande du propriétaire : « avec un agent Opus améliore et optimise l'ergonomie et la navigation » (CastBridge et CastBridge-TV). Agent : Opus. Branche `claude/ux-ergonomie` depuis `integration/agents` `c8be0e9`, un seul commit, **non poussé**. Conception et audit chiffré : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md`. Cahiers du reste : `docs/agent-briefs/SONNET-UX-INDEX.md` (ux-01…ux-07).

## Ce qui a été fait (dans le gel : décisions pures testées, écrans minces)

| Changement | Fonction pure (`android/core/.../core/ux/UiTexts.kt`) | Écrans touchés |
|---|---|---|
| L'app s'ouvre sur « CastBridge TV » (plus « TV DLNA ») ; ordre et ids de télémétrie inchangés | `PhoneTabs` | `S/MainActivity.kt` |
| Vocabulaire unique : *Copier sur la TV et lire* · *Copier sur la TV* · *Déplacer vers la TV* · *Lire en direct*, une ligne d'explication chacun ; « Caster »/« Diffuser » → « Lire sur la TV » | `SendWay`, `SendWays` | `S/OpenWithActivity.kt`, `S/player/CastSheet.kt`, `S/player/PhoneLibrary.kt`, `S/player/PlayerScreen.kt`, `S/player/ImageViewer.kt`, `S/TvHome.kt`, `S/MainActivity.kt` ; textes `C/tv/TransferQueue.kt` (`SOURCE_LOST`), `S/UploadService.kt` (`BUSY_TEXT`) |
| « Ouvrir avec » : « Copier sur la TV et lire » en premier ; feuille du lecteur : Copier et lire › Lire en direct › Déplacer | `SendWays.castOrder` | idem |
| Sept blocages muets de l'accueil supprimés (cause + action), message en haut, rouge seulement pour un problème, ✕ ; « le code de la TV a changé » enfin affiché dans l'assistant | `HomeNotices`, `UiNotice` | `S/TvHome.kt` |
| Barre « Envoi vers la TV : « titre » · 2 en attente · 1 échec » sur tous les onglets sauf l'accueil ; la notification de file ouvre la carte | `QueueGlances` | `S/MainActivity.kt` (`QueueStrip`), `S/TvHome.kt`, `S/TransferQueueService.kt` |
| TV : l'aide nomme « Copier sur la TV » (texte du cœur) | `TvHelpTexts` | `R/PlayerActivity.kt` (texte seulement) |
| TV : focus D-pad remis après reconstruction des rangées ; RETOUR revient sur la tuile d'origine | `TvHomeFocus` | `R/HomeScreen.kt` |
| TV profil enfant : la tuile « Jeux » ne disparaît plus | `ParentalRules.KID_HOME` (cœur) | — |
| TV écran Langues vide : chemin exact sur le téléphone | `LangCatalog.EMPTY_MESSAGE` (cœur) | — |

Touches (calculées depuis le code) : copier un fichier 3 → 2 ; voir la TV 2 → 1 ; changer de TV 3 → 2 ; relier une TV 4-5 → 3-4 ; libérer de la place 5 → 4 ; somme des 10 parcours principaux 34-35 → 28-29 ; où en est la copie / lire une erreur : « chercher » → visible sans toucher ; TV, retour sur « Connexion & réglages » 16 → 1 appui.

## Tests et vérifications

- **Rouge d'abord** : `CT/ux/UiTextsTest.kt` (20 tests) lancé contre des corps factices : **19 échecs par assertion** (le 20ᵉ, « file vide ⇒ rien », passe par construction), journal conservé hors dépôt.
- Vert : `UiTextsTest` 20/20, `LangLotConsumerTest`, `CopyQueue*Test`.
- Suite complète `:core:test` (chien de garde 60 s/test) après le dernier changement, `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin` : voir § Résultat ci-dessous.
- Test existant modifié : `LangLotConsumerTest` (texte épinglé de `EMPTY_MESSAGE`, mis à jour au nouveau texte ; aucune assertion affaiblie).

## Résultat de la dernière exécution

`:core:test` complet : **BUILD SUCCESSFUL** (3 min 50 s) : 329 classes, **2 556 tests, 0 échec, 0 erreur, 2 ignorés** (préexistants), aucun « TEST TROP LONG ». `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` : verts.

## Ce que seuls des appareils réels peuvent confirmer

- P-40…P-44 (`docs/test-plans/PARCOURS-CRITIQUES.md` § F) : barre de file sur un écran de 6" avec grand texte ; notification de file qui ouvre la carte sur le S21+ ; focus D-pad sur GaiaOS 32 bits 720p (délai 80 ms, reconstruction pendant une réception) ; lisibilité des lignes d'explication.
- Aucune capture n'a été prise : le téléphone (`RFCR313ABNF`, CastBridge 1.2.37-beta) était dans une autre application et seul l'`adb` en lecture était permis ; la TV n'a pas été touchée.

## Risques

- **TV sans appareil** : le changement de focus est petit (demandes de focus seulement) mais n'a pas tourné sur la TV de référence. Cas limite noté : tuile d'activité ouverte, retour, puis vidéo lancée depuis le téléphone ⇒ le focus revient sur cette tuile (jamais perdu).
- **Libellés changés** : toute capture ou aide externe citant « Envoyer une vidéo », « Copier vers la TV », « Caster » est à rafraîchir (docs/ADMIN.md:175 mis à jour dans ce commit).
- **Conflits de fusion** : `S/MainActivity.kt` (w11-01, w17-07), `S/TvHome.kt` (w11-08, w7-19), `R/HomeScreen.kt` (w11-03, w11-10), `C/parental/ParentalModel.kt` (w11-04, w17-02) ; amendements écrits dans `SONNET-UX-INDEX.md`.

## Décisions demandées (détail : conception § 7)

D-UX-1 Langues en profil enfant (**visible**) ; D-UX-2 onglet « TV DLNA » (**inchangé jusqu'à W11**) ; D-UX-3 fusion « Regarder »/« Bibliothèque » (**oui, dans w11-08**) ; D-UX-4 « Copier sur la TV et lire » sur l'accueil (**oui, hors gel**) ; D-UX-5 ordre de « Ouvrir avec » (**fait**) ; D-UX-6 « Libérer de la place » (**oui, après le gel**).
