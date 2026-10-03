# Vague UX pour agents Sonnet/Haiku — index (2026-10-03) : ergonomie et navigation, ce qui reste après `claude/ux-ergonomie`

Source : `docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md` (audit chiffré, classement, décisions D-UX-1…6). Protocole commun : `docs/COORDINATION.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main`, ni serveur, ni secret, ni signature ; textes en français ; dire « CastBridge » (téléphone) / « CastBridge-TV »). Gradle uniquement par `bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline …` depuis `android/` avec `-Pkotlin.daemon.jvmargs=-Xmx3g -Dorg.gradle.jvmargs=-Xmx4g` ; test **rouge par assertion d'abord** ; `:core:test` complet une fois à la fin (chien de garde 60 s/test).

**Gel** (`docs/REGRESSIONS.md`, W14/W15) : chaque cahier ne fait que reformuler, replacer, expliquer un blocage ou corriger un défaut prouvé ; toute décision est une fonction pure de `C/ux/` testée ; les écrans restent minces. Les cahiers TV (UX-b) partent **sur décision du coordinateur** et se confirment sur la TV de référence (GaiaOS 32 bits, 720p) ; builds TV verrouillés.

**Déjà fait par `claude/ux-ergonomie`** (ne pas refaire) : `C/ux/UiTexts.kt` (`SendWay`, `SendWays`, `HomeNotices`, `PhoneTabs`, `QueueGlances`, `TvHelpTexts`, `TvHomeFocus`) ; départ sur l'onglet « CastBridge TV » ; vocabulaire unique ; sept blocages muets de l'accueil ; barre de file sur tous les onglets ; « Ouvrir avec » réordonné ; focus D-pad de l'accueil TV ; « Jeux » en profil enfant ; aide TV et écran Langues vide.

## Les 7 cahiers

| id | Cahier | Objet | Groupe | Modèle | Effort | Jauge (entrée / sortie) | Statut | Dépend de / ordre |
|---|---|---|---|---|---|---|---|---|
| ux-01 | `sonnet-ux-01-lots-langues-messages.md` | « Données hors ligne » / Langues : résultat près du bouton, erreur en rouge, chemin PIN expliqué | UX-a | sonnet | S | 120 k / 10 k | PRÊT | jamais en parallèle de w17-07 |
| ux-02 | `sonnet-ux-02-file-exchange-error-states.md` | « Échange de fichiers » : erreur ≠ vide, plus d'impasse « vérification… », échec d'envoi affiché | UX-a | sonnet | S | 110 k / 10 k | PRÊT | — |
| ux-03 | `sonnet-ux-03-tv-never-silent.md` | TV : échec de réception en bannière partout, `playAll` et Wi-Fi Direct disent leur échec | UX-b | sonnet | M | 200 k / 15 k | PRÊT sur décision | séquentiel avec w11-04/10/11/12 et w17-08 (`R/PlayerActivity.kt`) |
| ux-04 | `sonnet-ux-04-tv-empty-states-focus.md` | TV : états vides avec cause + action, toujours une vue focalisable | UX-b | sonnet | M | 220 k / 15 k | PRÊT sur décision | après ux-03 pour `R/HomeScreen.kt` ; jamais avec w11-03 |
| ux-05 | `sonnet-ux-05-pairing-dead-ends.md` | relier/changer de TV sans impasse, « Mes TV » touchable, diagnostics nommés | UX-a | sonnet | S | 150 k / 12 k | PRÊT | avant w11-07 ; jamais avec w7-19 |
| ux-06 | `sonnet-ux-06-delete-confirm-storage-panel.md` | confirmation de suppression, panneau stockage jamais masqué en silence | UX-a | haiku | S | 70 k / 6 k | PRÊT | — |
| ux-07 | `sonnet-ux-07-cast-sheet-busy-queue.md` | feuille « Lire sur la TV » : actif + place dans la file pendant un envoi, ou raison exacte | UX-a | sonnet | S | 160 k / 10 k | PRÊT après vérification (étape 1) | — |

Total ≈ **4,5 agent·jours**, coût API estimé ≈ 1 M jetons d'entrée (jauges non vérifiées). Exécuteur le moins cher suffisant : haiku pour ux-06 (avant/après fournis) ; sonnet pour le reste (états, focus, vérification de comportement) ; audit Opus sur ux-03, ux-04, ux-07.

## Matrice de propriété (fichiers disjoints dans un même groupe)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`.

| id | Fichiers possédés |
|---|---|
| ux-01 | nouveaux `C/ux/LotsNotices.kt`, `CT/ux/LotsNoticesTest.kt` ; `S/LotsScreen.kt` (affichage du message), `S/LotsRuntime.kt` (texte `:243`) |
| ux-02 | nouveaux `C/ux/ExchangeState.kt`, `CT/ux/ExchangeStateTest.kt` ; `S/TvTransferScreen.kt` |
| ux-05 | nouveaux `C/ux/PairNav.kt`, `CT/ux/PairNavTest.kt` ; `S/TvPairScreen.kt` ; `S/TvHome.kt` (rappel `AddTvFlow` seulement) |
| ux-06 | nouveaux `C/ux/StorageNotices.kt`, `CT/ux/StorageNoticesTest.kt` ; `S/TvScreen.kt`, `S/StoragePanel.kt`, `S/TvHub.kt` (`:168`) |
| ux-07 | nouveaux `C/ux/CastSheetState.kt`, `CT/ux/CastSheetStateTest.kt` ; `S/player/CastSheet.kt` |
| ux-03 (UX-b) | `C/tv/TransferProgress.kt`, `C/xfer/ReceiveCard.kt`, `CT/xfer/*` ou `CT/ux/ReceptionFailureTest.kt` ; `R/TvService.kt`, `R/PlayerActivity.kt` (`flash`, `playAll`, Wi-Fi Direct) |
| ux-04 (UX-b) | nouveaux `C/ux/TvEmptyStates.kt`, `CT/ux/TvEmptyStatesTest.kt` ; `R/LibraryScreen.kt`, `R/LanguesActivity.kt`, `R/DownloadsActivity.kt`, `R/HomeScreen.kt` (`reload`) |

Chaque cahier crée **son propre fichier** sous `C/ux/` : aucun ne modifie `C/ux/UiTexts.kt` (propriété de `claude/ux-ergonomie`) ; s'il faut un libellé de `SendWay`, il le **lit**.

## Amendements aux vagues W11 et W17 (à lire par leurs exécutants ; leurs cahiers ne sont pas édités ici)

| Cahier | Amendement |
|---|---|
| w11-01 | l'onglet de départ est déjà « CastBridge TV » (`PhoneTabs.START`) ; reste : mémoire `last_tab` (via `PhoneTabs.at(i)`), barre du haut ⋮ |
| w11-04 | « Jeux » déjà dans `KID_HOME` ; reste : ids ; **ajouter** le contournement d'essai par le menu (`R/PlayerActivity.kt:715-717`) ; D-UX-1 (Langues en profil enfant) |
| w11-05 / w11-08 | réutiliser `SendWay`, `HomeNotices`, `QueueGlances` (pas de second vocabulaire) ; D-UX-3 (fusion « Regarder »/« Bibliothèque ») et D-UX-4 (« Copier sur la TV et lire » sur l'accueil) |
| w11-10 | garder les règles de `TvHomeFocus` (retour sur la tuile d'origine, focus remis après reconstruction) dans la grille |
| w11-13 | mesurer aussi P-40…P-44 |
| w17-02 | ajouter « Boutique » à côté de « Jeux » dans `KID_HOME` |
| w17-07 | `S/MainActivity.kt` `Root()` utilise `PhoneTabs` et affiche `QueueStrip` : l'entrée « Boutique » reste dans `actions` |
