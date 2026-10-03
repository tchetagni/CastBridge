# Ergonomie et navigation de CastBridge (téléphone) et CastBridge-TV : audit chiffré, corrections sûres faites, le reste en cahiers (2026-10-03)

Auteur : ingénieur produit/UX (Opus), branche `claude/ux-ergonomie` depuis `integration/agents` (`c8be0e9`). Demande du propriétaire (2026-10-03) : « avec un agent Opus améliore et optimise l'ergonomie et la navigation ». Plaintes antérieures reprises : navigation trop lourde à l'écran, boutique invisible, file de copie et envois confus, **tout blocage doit être expliqué**.

Cadre : **gel anti-régression** (`docs/REGRESSIONS.md` R-01…R-09, W14/W15). Autorisé ici : réorganiser, reformuler, réduire les touches, corriger focus et états, **à condition** que chaque décision soit une fonction pure de `android/core` testée (rouge d'abord par assertion), que les écrans restent minces et que les parcours existants restent verts. Tout ce qui est plus gros est en cahiers `docs/agent-briefs/sonnet-ux-NN-*.md` (index `SONNET-UX-INDEX.md`). Ne refait pas W11 (navigation allégée, `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md`) ni W17 (boutique, `DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md`) : il les **complète** et les **amende** (§ 6).

Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`.

Méthode : lecture du code (lignes citées = tête `c8be0e9`), deux audits en lecture (téléphone, TV), comptage des touches à la main depuis le lancement de l'app. **Aucune capture d'appareil** : le téléphone branché était dans une autre application et seul l'`adb` en lecture était permis (pas de `am start`) ; la TV n'a pas été touchée. Les chiffres sont donc **calculés**, à confirmer sur appareils (§ 8).

---

## 0. En dix lignes

1. **Le téléphone s'ouvrait sur « TV DLNA »** (`S/MainActivity.kt:89` `mutableStateOf(0)`), fonction rare : **+1 touche sur chacun des 10 parcours** fréquents. Corrigé : départ sur « CastBridge TV » (`PhoneTabs.START`), ordre et télémétrie inchangés.
2. **Quatre mots pour la même chose et deux sens pour le même mot** : « Envoyer une vidéo », « Copier vers la TV », « Copier sur la TV… », « Copier sur la TV et lire », « Caster… », « Diffuser », « Lire en direct (sans copier) », « Regarder sur la TV » (= bibliothèque). Corrigé : **un seul vocabulaire** (`C/ux/UiTexts.kt` `SendWay`) : *Copier sur la TV et lire* · *Copier sur la TV* · *Déplacer vers la TV* · *Lire en direct*, chacun avec **une ligne d'explication**, dans « Ouvrir avec », la feuille du lecteur (« Lire sur la TV »), la bibliothèque du téléphone, l'accueil et l'aide de la TV.
3. **Sept blocages muets** sur l'accueil du téléphone (fichiers choisis puis abandonnés sans TV, trois tuiles qui ne faisaient rien sans TV, commande de lecture sans TV, « le code de la TV a changé » écrit puis jamais montré, message d'erreur sous les affiches et rouge même pour un succès). Corrigés : chaque cas dit **cause + action** (`HomeNotices`), le message est **en haut**, rouge seulement pour un problème, refermable.
4. **La file de copie n'était visible que sur un onglet** et la notification de la file rouvrait le dernier onglet. Corrigé : une **barre « Envoi vers la TV : « Film » · 2 en attente · 1 échec »** sur tous les autres onglets (`QueueGlances`), un toucher mène à la carte ; la notification ouvre la carte.
5. **TV, profil enfant** : la tuile « Jeux » disparaissait (liste `KID_HOME` par étiquettes citant « Quiz »/« Échecs », `C/parental/ParentalModel.kt:227`). Corrigé (une ligne du cœur, testée).
6. **TV, focus D-pad perdu** quand une rangée apparaît (premier fichier reçu, « Reprendre », clé USB : `R/HomeScreen.kt:150-153` vide les vues sans redonner le focus) et **retour d'une tuile** (Bibliothèque, Connexion & réglages) qui renvoyait sur la première carte. Corrigé par `TvHomeFocus` (pur) : focus remis au même endroit, retour sur la tuile d'origine (Connexion & réglages : **16 appuis → 1**).
7. Gains mesurés (calculés) : copier un fichier **3 → 2** touches, voir ce qu'il y a sur la TV **2 → 1**, changer de TV **3 → 2**, première liaison **−1**, savoir où en est la copie **« ouvrir l'onglet » → 0** (visible partout), lire une erreur **défilement → 0**.
8. Laissé en cahiers (7, `sonnet-ux-01…07`) : messages de « Données hors ligne », écran d'échange de fichiers (états d'erreur), échec de réception muet sur la TV, états vides non focalisables de la TV, impasses de l'ajout de TV, suppression sans confirmation et « Libérer de la place », feuille du lecteur qui refuse alors que la file accepterait, actions TV sans message.
9. Décisions demandées au propriétaire (§ 7) : 6, chacune avec une recommandation (profil enfant + Langues, onglet « TV DLNA », fusion « Regarder »/« Bibliothèque », copier-et-lire depuis l'accueil, ordre des boutons, « Libérer de la place »).
10. Risques : focus TV non vérifié sur GaiaOS 720p ; nouvelle barre de file sur un petit écran ; libellés changés (captures d'aide existantes). Tout est derrière des fonctions pures testées (20 tests nouveaux) ; aucune route, aucun format, aucune donnée persistante ne change.

---

## 1. Audit : les 10 parcours les plus fréquents du téléphone (touches depuis le lancement)

« Avant » = `c8be0e9` (l'app s'ouvre sur « TV DLNA ») ; « Après » = cette branche. Une touche = un toucher ; le sélecteur de fichiers d'Android compte 1 pour un fichier.

| # | Parcours | Chemin avant (preuve) | Avant | Après | Ce qui reste lourd (→ cahier ou W11/W17) |
|---|---|---|---|---|---|
| 1 | **Mettre une vidéo du téléphone sur la TV** (copier et lire) | onglet « Sur le téléphone » → appui long → « Copier sur la TV… » (`S/player/PhoneLibrary.kt:178`) → carte (`S/player/CastSheet.kt:105`) ; ou accueil : onglet « CastBridge TV » → « Envoyer une vidéo » (`S/TvHome.kt:320`) → fichier (lu **à la fin** de la copie) | 4 (bibl.) · 3 (accueil) | 4 · **2** | l'accueil ne propose pas « Copier sur la TV et lire » (lecture **pendant** la copie) : D-UX-4 |
| 2 | **Copier et lire depuis une autre app** (« Ouvrir avec ») | dialogue : « Copier vers la TV » puis « Copier sur la TV et lire » en **2ᵉ** position (`S/OpenWithActivity.kt:100-101`) | 1 (2ᵉ bouton) | 1 (**1ᵉʳ** bouton, avec son explication) | — |
| 3 | **Copier un fichier** | onglet → « Envoyer une vidéo » → fichier | 3 | **2** | vidéo/audio seulement depuis l'accueil (`:320`), tous types par « Échanger des fichiers » |
| 4 | **Mettre plusieurs fichiers en file** | onglet → « Envoyer une vidéo » → appui long + N + « Sélectionner » | N + 4 | **N + 3** | — |
| 5 | **Envoyer un lot Langues** | onglet « Apprendre » → « Données hors ligne » (`S/LearnScreen.kt:56`) → défiler → 3 sélecteurs × 2 (`S/LotsScreen.kt:176-179`) → « Télécharger mes leçons de langue » (`:185`) | 9 + défilement | 9 + défilement | résultat affiché **en haut** de l'écran, couleur primaire même en erreur (`S/LotsScreen.kt:69`) ; chemin PIN refusé (« Aucune TV enregistrée », `S/LotsRuntime.kt:243`) : **ux-01** ; vitrine : W17 |
| 6 | **Voir ce qu'il y a sur la TV** | onglet → « Bibliothèque de la TV » (`S/TvHome.kt:331`) ; « Regarder sur la TV » ouvre **le même** écran quand rien ne joue (`:324-326`) | 2 | **1** | doublon « Regarder »/« Bibliothèque » : D-UX-3 |
| 7 | **Relier / re-relier une TV** | onglet → assistant « Trouvons votre TV » → « Ajouter ma TV » → choisir → confirmer (`S/TvHome.kt:416`, `S/TvPairScreen.kt:221`) | 4-5 | **3-4** | « Saisir le code de la TV à la place » ferme simplement l'assistant (`S/TvPairScreen.kt:228`) : **ux-05** |
| 8 | **Changer de TV** | onglet → « Mes TV » (`S/TvPairScreen.kt:92`) → petit bouton radio (`:300`) | 3 | **2** | seule la radio est touchable : **ux-05** |
| 9 | **Louer / ouvrir un contenu** | barre du haut « Locations » (`S/MainActivity.kt:116`) | 1 | 1 | boutique absente : **W17** (w17-07 remplace « Locations » par « Boutique ») |
| 10 | **Libérer de la place sur la TV** | onglet → « Bibliothèque de la TV » → appui long → « Supprimer » → confirmer (`S/TvLibraryScreen.kt:182,196`) ; `StoragePanel` n'a **aucune** action de suppression (`S/StoragePanel.kt:54`) | 5 | **4** | pas de « Libérer de la place » ; la liste « Sur la TV » de l'écran Avancé supprime **sans confirmation** (`S/TvScreen.kt:271`) : **ux-06**, D-UX-6 |
| 11 | **Lire une erreur** | message de l'accueil **sous** les tâches, les séries et les affiches (`S/TvHome.kt:344`), toujours rouge même pour « 3 fichiers ajoutés » (`:181`) | défilement, souvent jamais vu | **0** (en haut, sous la ligne de la TV, ton juste, ✕) | messages de « Données hors ligne » : **ux-01** |
| 12 | **Où en est ma copie / ma file ?** | carte de file uniquement sur l'onglet « CastBridge TV » (`S/TvHome.kt:247`) ; la notification de file ouvre le dernier onglet, sans la carte (`S/TransferQueueService.kt:78`) | 1 touche + la bonne page | **0** (barre sur tous les onglets) ; 1 touche vers la carte ; la notification ouvre la carte | — |

**Somme des 10 parcours principaux (1-10, hors N) : avant 34-35, après 28-29 (−6, −17 %)** ; et deux informations critiques (erreur, file) passent de « à chercher » à « visibles sans toucher ».

### 1.1 Vocabulaire relevé (téléphone) et règle retenue

| Avant (fichier:ligne) | Sens réel | Après |
|---|---|---|
| « Envoyer une vidéo » (`S/TvHome.kt:320`) | copie (un seul fichier lu **à la fin** de la copie) | « **Copier sur la TV** » + « Une vidéo seule est lue à la fin de la copie ; reste sur le téléphone » |
| « Copier vers la TV » (`S/OpenWithActivity.kt:95,100`, `S/UploadService.kt:404`, `C/tv/TransferQueue.kt:40`) | copie sans lecture | « **Copier sur la TV** » + explication |
| « Copier sur la TV… » (`S/player/PhoneLibrary.kt:178`) | ouvre la feuille qui **copie et lit** | « **Copier sur la TV et lire…** » |
| « Caster… » (`:177`), « Caster vers la TV » (`S/player/PlayerScreen.kt:239`), icône « Caster » (`:263`, `S/player/ImageViewer.kt:87`), « Diffuser sur » (`S/player/CastSheet.kt:77`) | ouvre la feuille des façons d'envoyer | « **Lire sur la TV** » (jargon « caster » retiré) |
| « Lire en direct (sans copier) » (`C/phone/PhonePlayer.kt:160`) | lecture depuis le téléphone | « **Lire en direct** » + « La TV lit depuis le téléphone et ne garde rien : restez sur le même Wi-Fi jusqu'à la fin. » |
| « Diffuser » (onglet TV DLNA, `S/MainActivity.kt:252`) | lecture en direct DLNA | « **Lire en direct** » |
| « copier et lire » / « déplacement » (carte de file, `S/TvHome.kt:477`) | ne disait pas qu'un envoi d'accueil est lu à la fin | « copie et lecture » · « copie puis lecture » · « copie » · « déplacement » · « déplacement puis lecture » (`QueueGlances.kind`) |
| Aide de la TV « Touchez « Envoyer une vidéo » » (`R/PlayerActivity.kt:455-457`) | nomme un bouton qui change de nom | texte du cœur (`TvHelpTexts`) qui cite `SendWay.COPY.label` : ne peut plus dériver |

Règle : **les boutons disent ce qui arrive au fichier** (copier / déplacer / lire en direct) ; « **envoi** » reste le nom générique d'un transfert en cours (notifications `C/xfer/XferTexts.kt`, file d'attente) ; « Diffuser », « Caster », « DLNA » ne sont plus des verbes d'action (DLNA reste le nom de l'onglet « TV DLNA » : D-UX-2).

Non changés volontairement (autre sens) : « Déplacer vers <clé> » entre volumes de la TV (`S/TvLibraryScreen.kt:179`) ; « Envoyer à la TV » des lots (`C/lots/LotsToDeliver.kt:50`, vocabulaire de W17) ; « Télécharger sur la TV » (la TV télécharge d'Internet).

### 1.2 Blocages muets relevés (téléphone)

| # | Où | Ce qui se passait | Après |
|---|---|---|---|
| M1 | `S/TvHome.kt:159` | fichiers choisis sans TV joignable : abandonnés sans un mot | **refus avant le sélecteur** + « Votre TV n'est pas encore jointe… Réessayez dès qu'elle est connectée » / « Aucune TV… « Ajouter ma TV » d'abord » ; même texte si la liaison tombe pendant le choix |
| M2-M4 | `S/TvHome.kt:324-332`, `:356-358` | « Regarder sur la TV », « Bibliothèque de la TV », « Échanger des fichiers » sans TV : rien | « « <tâche> » attend la TV : elle n'est pas jointe pour l'instant. Vérifiez qu'elle est allumée, sur le même Wi-Fi, avec CastBridge-TV ouvert… » |
| M5 | `S/TvHome.kt:185` | commande de lecture (affiche « Reprendre », pause) sans TV : rien | même message |
| M6 | `S/TvHome.kt:151` | « Le code de la TV a changé » écrit puis l'assistant s'affichait sans lui (état perdu au retour anticipé) | message déclaré avant les retours et **affiché en tête de l'assistant** |
| M7 | `S/TvHome.kt:344` | message sous la ligne de flottaison, rouge pour un succès, jamais effacé | en haut, rouge seulement si problème, bouton ✕ |
| (laissés) | `S/TvTransferScreen.kt:61-70,142-153,182` ; `S/StoragePanel.kt:68,82` ; `S/TvHub.kt:175,190` ; `S/LotsScreen.kt:126` ; `S/ShareToTvActivity.kt:126` ; `S/TvPairScreen.kt:71,131,192,248` ; `S/player/CastSheet.kt:54` | erreurs avalées ou montrées comme « vide » | cahiers ux-01, ux-02, ux-05, ux-06, ux-07 |

## 2. Audit : CastBridge-TV à la télécommande (appuis D-pad)

Accueil : 18 tuiles sur **une** rangée (`R/PlayerActivity.kt:537-615`), 6-7 visibles ; focus initial sur la première carte vidéo quand la bibliothèque n'est pas vide (`R/HomeScreen.kt:175-179`), +1 HAUT pour toute tuile. Appuis comptés depuis le focus initial, OK compris, bibliothèque non vide (adulte / enfant).

| Parcours TV | Avant | Après | Reste (→) |
|---|---|---|---|
| **Lire un fichier reçu** | 1 (OK sur la 1ʳᵉ carte « Récemment ajoutés ») ; **focus perdu** si la rangée apparaît pendant qu'on est sur l'accueil (`R/HomeScreen.kt:150-153`) : 2-3 appuis « à l'aveugle » | **1** ; focus remis en place après reconstruction | pas d'action « Lire » sur la bannière de réception (ux-03) |
| **Ouvrir une leçon** (Apprendre) | HAUT + 2 DROITE + OK = 4, puis « Qui apprend ? » › classe › fiche | 4 | grille 3×2 : w11-10 (≤ 3) |
| **Langues** | HAUT + 3 DROITE + OK = 5 ; **masquée en profil enfant** | 5 | D-UX-1 ; w11-10 la range dans Apprendre |
| **Boutique** | inexistante | inexistante | W17 (w17-08) |
| **Voir la progression d'une réception** | seulement la puce d'en-tête de l'accueil, 16 sp, 2 lignes (`R/HomeScreen.kt:65`) ; rien ailleurs ; **échec de réception sans bannière**, visible 8 s (`C/tv/TransferProgress.kt:20,145`) | inchangé | **ux-03** (défaut prouvé : échec muet hors accueil) |
| **Revenir de « Bibliothèque » par RETOUR et y retourner** | focus sur la 1ʳᵉ carte → HAUT + OK = 2 | **1** (focus sur la tuile d'origine) | — |
| **Revenir de « Connexion & réglages » (tuile 15) et y retourner** | HAUT + 14 DROITE + OK = **16** | **1** | — |
| **Profil enfant : jouer** | tuile « Jeux » **absente** (`C/parental/ParentalModel.kt:227` cite « Quiz », « Échecs ») : impossible | HAUT + 2 DROITE + OK = 4 | ids au lieu d'étiquettes : w11-04 |
| **Contrôle parental** (adulte) | HAUT + 16 DROITE + OK = 18 | 18 | w11-10 (grille, « Parents ») |

Autres constats TV (preuves, non corrigés ici sauf mention) :
- **Taille du texte à 720p (1 sp = 1 px)** : seul le badge de clé est sous 16 sp (14 sp, `R/KeyBadgeOverlay.kt:30`) → w11-02. Étiquettes de tuiles, puces, en-têtes au minimum de 16 sp (`R/TvCards.kt:276,279`, `R/HomeScreen.kt:65`) → w11-03 (19 sp). Langues ≥ 17 px, Apprendre ≥ 18 px : conformes.
- **Pièges de focus** : la colonne de puces d'état consomme GAUCHE/DROITE (`R/StatusBarView.kt:119`), sortie par BAS ou RETOUR seulement ; l'écran Langues sans lot ni Internet n'a **aucune vue focalisable** (`R/LanguesActivity.kt:91-103`), la bibliothèque vide non plus (`R/LibraryScreen.kt:245`) → **ux-04**.
- **États vides** : bibliothèque (cause + action, bon), Langues « envoyez un lot depuis le téléphone » **sans le chemin** → corrigé dans le cœur (`LangCatalog.EMPTY_MESSAGE` : « Sur le téléphone : CastBridge › Apprendre › « Données hors ligne » › Langues… ») ; échec de mise à jour Langues sans cause (`R/LanguesActivity.kt:146`), gestionnaire de téléchargements arrêté sans action (`R/DownloadsActivity.kt:90`), erreur de lecture de la bibliothèque montrée comme « Aucun fichier » (`R/HomeScreen.kt:132`, `R/LibraryScreen.kt:122`) → **ux-04**.
- **Actions TV muettes** : `playAll` (`R/PlayerActivity.kt:669`), permission Wi-Fi Direct (`:698`), audio Langues (`R/LanguesActivity.kt:211`), « Ajouter un lien » sans gestionnaire (`R/DownloadsActivity.kt:186`) → **ux-03** (`playAll`, Wi-Fi Direct) et **ux-04** (Langues, Téléchargements).
- **Doublons** tuile ↔ menu « Connexion & réglages » : 11 fonctions, « Recevoir du téléphone » = « Aide », id `bluetooth` porté par deux tuiles ; l'essai filtre par **étiquette** (`R/ParentalHub.kt:271-274`) et le menu contourne l'essai (« Toute la bibliothèque », « Téléchargements », `R/PlayerActivity.kt:715-717`) → w11-04 (déjà prévu) ; contournement d'essai signalé à w11-04 (amendement § 6).

## 3. Classement (impact × risque) et ce qui a été fait

Impact : 3 = touche chaque usage / bloque sans explication ; 2 = parcours fréquent ; 1 = confort. Risque : 1 = texte ou décision pure ; 2 = câblage d'écran mince ; 3 = comportement (route, transfert, persistance) ou TV sans appareil.

| Rang | Élément | Impact | Risque | Décision |
|---|---|---|---|---|
| 1 | Onglet de départ = accueil (`PhoneTabs.START`) | 3 | 1 | **FAIT** |
| 2 | Blocages muets de l'accueil (M1-M7, `HomeNotices`) | 3 | 2 | **FAIT** |
| 3 | Vocabulaire unique + une ligne d'explication (`SendWay`, `SendWays`) | 3 | 1 | **FAIT** |
| 4 | Barre « où en est ma copie » sur tous les onglets + notification vers la carte (`QueueGlances`) | 3 | 2 | **FAIT** |
| 5 | « Ouvrir avec » : « Copier sur la TV et lire » en premier, chaque bouton expliqué | 2 | 1 | **FAIT** |
| 6 | Feuille du lecteur : ordre Copier et lire › Lire en direct › Déplacer (`castOrder`) | 2 | 1 | **FAIT** |
| 7 | TV : focus remis après reconstruction des rangées, retour sur la tuile (`TvHomeFocus`) | 2 | 3 (TV) | **FAIT** (défaut prouvé ; à confirmer sur la TV de référence) |
| 8 | TV profil enfant : « Jeux » (`KID_HOME`) | 3 (enfant) | 1 | **FAIT** |
| 9 | Aide TV et écran Langues vide : bons boutons, bon chemin (`TvHelpTexts`, `EMPTY_MESSAGE`) | 2 | 1 | **FAIT** |
| 10 | Échec de réception muet hors accueil (TV) | 3 | 3 | cahier **ux-03** |
| 11 | Messages et chemin de « Données hors ligne » / Langues | 2 | 2 | cahier **ux-01** |
| 12 | Écran « Échange de fichiers » : erreurs montrées comme vide, impasse « vérification… » | 2 | 2 | cahier **ux-02** |
| 13 | États vides TV focalisables et honnêtes | 2 | 3 | cahier **ux-04** |
| 14 | Impasses de l'ajout de TV, « Mes TV », diagnostics | 2 | 2 | cahier **ux-05** |
| 15 | Suppression sans confirmation (`S/TvScreen.kt:271`) ; panneau stockage masqué en silence | 2 | 2 | cahier **ux-06** |
| 16 | Feuille du lecteur grisée pendant un envoi alors que la file accepterait | 2 | 3 | cahier **ux-07** |
| 17 | Actions TV sans message | 1 | 3 | cahiers **ux-03** / **ux-04** |
| — | Barre du bas, « Plus », grille TV 3×2, badge court, tuiles 19 sp | 3 | 3 | **W11** (cahiers prêts, non refaits) |
| — | Boutique visible téléphone + TV | 3 | 3 | **W17** (cahiers prêts, non refaits) |

## 4. Ce qui a changé dans le code (tout passe par `C/ux/UiTexts.kt`)

| Fonction pure (`castbridge.core.ux`) | Rôle | Écran mince qui la dessine |
|---|---|---|
| `SendWay` (4 façons : libellé + explication), `SendWays.of / castOrder / castLabel / castHint`, `HOME_COPY_HINT`, `SHEET_TITLE`, `MENU_PLAY_ON_TV` | un vocabulaire, un ordre | `S/OpenWithActivity.kt`, `S/player/CastSheet.kt`, `S/player/PhoneLibrary.kt`, `S/player/PlayerScreen.kt`, `S/player/ImageViewer.kt`, `S/TvHome.kt`, `S/MainActivity.kt` (bouton DLNA) |
| `HomeNotices.needsTv / canPick / pickBlocked / queued / info / error`, `UiNotice` | jamais de blocage muet, ton juste | `S/TvHome.kt` |
| `PhoneTabs` (ordre, libellés, ids `screen_time`/`feature`, `START`) | départ sur l'accueil | `S/MainActivity.kt` |
| `QueueGlances.of / kind / stripVisible`, `QueueGlance` | « où en est ma copie » | `S/MainActivity.kt` (`QueueStrip`), `S/TvHome.kt` (carte de file), `S/TransferQueueService.kt` (la notification ouvre la carte : `TvHomeRequest.TV`, `FLAG_UPDATE_CURRENT`) |
| `TvHelpTexts.send` | l'aide de la TV nomme le bouton actuel | `R/PlayerActivity.kt` (`openHelp`, texte seulement) |
| `TvHomeFocus.onShow / afterRebuild` | focus D-pad jamais perdu | `R/HomeScreen.kt` (`apply`, `focus`, clics de tuiles et de cartes) |
| `ParentalRules.KID_HOME` (+ « Jeux »), `LangCatalog.EMPTY_MESSAGE`, `QueueTexts.SOURCE_LOST`, `UploadService.BUSY_TEXT` | défauts de texte/filtre | — |

Tests : `CT/ux/UiTextsTest.kt` (20 tests, **rouges par assertion sur des corps factices** puis verts : 19 échecs sur 20, le 20ᵉ étant le cas « file vide ⇒ rien »), `CT/LangLotConsumerTest.kt` (texte épinglé mis à jour). Aucun test existant affaibli.

## 5. Maquettes (texte)

### 5.1 Téléphone : accueil « CastBridge TV » (après cette branche, sans W11)

```
┌──────────────────────────────────────┐
│ [logo] Activer la TV Locations 🔒 ⚙ │  (barre du haut : w11-01 / w17-07)
│ TV DLNA │CastBridge TV│Jeux│Sur le…▸│  ← s'ouvre ICI (avant : TV DLNA)
│ ● Salon connectée · détail           │
│ 12,3 Go libres sur la TV             │
│ « Bibliothèque de la TV » attend la  │  ← message EN HAUT, rouge seulement
│ TV : elle n'est pas jointe…       ✕  │     si problème (avant : sous les affiches)
│ ┌ Envoi vers la TV ─ 42 % ───────┐   │
│ ┌ File d'attente des envois · 2  ┐   │
│ │ 1. Film A  En cours · copie puis lecture │
│ │ 2. Film B  En attente · copie   │   │
│ [Copier sur la TV ][Déplacer vers TV]│  ← « Copier sur la TV » (avant « Envoyer une vidéo »)
│  Une vidéo seule est lue à la fin…   │
│ [Regarder sur la TV][Télécommande]   │
│ [Bibliothèque TV][Échanger fichiers] │
│ Reprendre sur la TV [■][■][■]        │
│ ⚙ Avancé                             │
└──────────────────────────────────────┘
Autre onglet (ex. Jeux) pendant une copie :
┌──────────────────────────────────────┐
│ TV DLNA │CastBridge TV│ Jeux │ …     │
│ ⇧ Envoi vers la TV : « Film A » · 2 en attente   [Voir] │  ← nouvelle barre (QueueGlances)
│   (2ᵉ ligne : la cause d'une pause ou du 1er échec)      │
│ … contenu de l'onglet …              │
```

### 5.2 Téléphone : « Ouvrir avec CastBridge » (après)

```
┌ Mon film ───────────────────────────┐
│ 1,2 Go · TV : SALON                 │
│ [ Copier sur la TV et lire        ] │  ← 1er (avant : 2e)
│  La TV démarre la lecture dès qu'elle a assez d'avance ; ce téléphone devient sa télécommande.
│ [ Copier sur la TV                ] │  ← avant « Copier vers la TV »
│  Gardé sur la TV pour plus tard, sans lecture ; … Suivez la copie dans la notification.
│ [ Déplacer vers la TV             ] │
│  Copié sur la TV, puis effacé du téléphone une fois la copie vérifiée : libère de la place.
│                    Annuler  Lire ici │
└─────────────────────────────────────┘
```

### 5.3 TV : accueil (inchangé dans sa forme ; focus corrigé) et cible W11

```
Aujourd'hui (rangée unique de 18 tuiles)          Cible W11 (w11-10, non faite ici)
┌───────────────────────────────────────────┐    ┌───────────────────────────────────────────┐
│ [logo]   ESSAI · …   ● Prêt · code 12••••  │    │ [logo]                    20:41  [⌁][⌁]   │
│ Titre du héros                             │    │ ESSAI · 11 h restantes                     │
│ Outils et fonctions                        │    │ Reprendre  [■■■][■■■][■■■]  ← focus initial│
│ [Biblio][Ajouter][Appren][Langues][Jeux]▸  │    │ [ Regarder ] [ Apprendre ] [ Jeux ]        │
│ Récemment ajoutés · 15 [■■■]←focus initial │    │ [ Téléphone ] [ Parents ] [ Plus ]         │
│ RETOUR depuis Bibliothèque ⇒ focus sur     │    │ ■ Téléphone ■ Apprendre ■ Jeux ■ Regarder  │
│   [Biblio] (avant : 1re carte)             │    └───────────────────────────────────────────┘
│ Rangée qui apparaît ⇒ focus remis (avant : │
│   perdu)                                   │
└───────────────────────────────────────────┘
```

## 6. Amendements aux vagues W11 et W17 (sans éditer leurs cahiers)

| Cahier | Ce que cette branche a déjà fait | Ce qui reste au cahier |
|---|---|---|
| w11-01 | onglet de départ = « CastBridge TV » (`PhoneTabs.START`, défaut équivalent à son `last_tab = 1`) | la **mémoire** du dernier onglet (lire/écrire `last_tab`, en passant par `PhoneTabs.at(i)` pour un index inconnu) et la barre du haut ⋮ |
| w11-04 | « Jeux » ajouté à `KID_HOME` (par étiquette, testé) | le passage aux **ids** (`KID_HOME_IDS`) ; **ajouter** : le menu « Connexion & réglages » contourne l'essai (« Toute la bibliothèque », « Quiz culture générale », « Téléchargements », `R/PlayerActivity.kt:715-717`) ; filtrer ces lignes par le même `TrialPolicy` |
| w11-05 / w11-08 | `C/ux/UiTexts.kt` existe : `SendWay`, `HomeNotices`, `QueueGlances` | `NavTexts`/`QuickActions` **réutilisent** ces libellés (pas de second vocabulaire) ; la « carte d'envoi unique » de w11-08 se calcule avec `QueueGlances.of` |
| w11-10 | `TvHomeFocus` (retour sur la tuile, focus après reconstruction) | la grille garde ces deux règles (le test `TvFocusGraphTest` les ajoute à ses cas) |
| w11-13 | — | ajouter aux tâches mesurées : P-40…P-44 (§ 8) |
| w17-02 | — | `KID_HOME` contient désormais « Jeux » : ajouter « Boutique » **à côté** (une ligne, pas de conflit de sens) |
| w17-07 | `S/MainActivity.kt` : `Root()` lit `PhoneTabs` et affiche `QueueStrip` sous la barre d'onglets | l'entrée « Boutique » remplace toujours « Locations » dans `actions` (zone non touchée ici) |

## 7. Décisions pour le propriétaire (recommandation en gras)

| Id | Question | Options | Recommandation |
|---|---|---|---|
| D-UX-1 | **Langues en profil enfant** sur la TV (aujourd'hui masquée : absente de `KID_HOME`) | (a) **visible** (apprentissage) ; (b) masquée (statu quo) | **(a)** : c'est du contenu éducatif ; une ligne du cœur, à faire avec w11-04 |
| D-UX-2 | Onglet « TV DLNA » du téléphone | (a) **le garder en 1ʳᵉ position jusqu'à W11 (Plus › Connexion avancée)** ; (b) le renommer « Autres TV » maintenant | **(a)** : l'app ne s'ouvre plus dessus ; un renommage isolé dérouterait sans gain |
| D-UX-3 | Doublon « Regarder sur la TV » / « Bibliothèque de la TV » (même écran quand rien ne joue) | (a) **fusion en « Sur la TV » dans w11-08** ; (b) statu quo | **(a)** |
| D-UX-4 | Accueil du téléphone : « Copier sur la TV » (lu **à la fin**) ou « Copier sur la TV et lire » (lu **pendant**, R-08) pour une vidéo seule | (a) **ajouter « Copier sur la TV et lire » à l'accueil dans w11-08** (même chemin `CastSession` que « Ouvrir avec ») ; (b) statu quo, explication seulement (fait) | **(a)** : c'est le parcours n° 1 du propriétaire ; changement de comportement ⇒ hors gel |
| D-UX-5 | Ordre des boutons de « Ouvrir avec » | (a) **Copier et lire › Copier › Déplacer (fait)** ; (b) ancien ordre | **(a)** |
| D-UX-6 | « Libérer de la place sur la TV » | (a) **entrée qui ouvre la bibliothèque de la TV triée par taille, sélection multiple, une confirmation** (cahier à écrire après le gel) ; (b) suppression seulement fichier par fichier (statu quo) | **(a)** ; en attendant, ux-06 ajoute la confirmation manquante |

## 8. Parcours à confirmer sur appareils (ajoutés à `docs/test-plans/PARCOURS-CRITIQUES.md`)

P-40 (départ sur l'accueil), P-41 (vocabulaire et ordre de « Ouvrir avec » / feuille du lecteur), P-42 (aucun blocage muet de l'accueil), P-43 (barre de file sur tous les onglets, notification vers la carte), P-44 (TV : focus D-pad de l'accueil, profil enfant « Jeux », aide). Seuls des appareils réels confirment : le rendu de la barre de file sur un écran de 6" avec grand texte ; le focus D-pad sur GaiaOS 32 bits 720p (délai de 80 ms, reconstruction des rangées pendant une réception) ; la notification de file qui ouvre bien la carte sur le S21+ ; la lisibilité des nouvelles lignes d'explication.

## 9. Ce qui n'a pas pu être vérifié

- Aucun rendu réel (pas de capture : le téléphone était dans une autre application, `am start` interdit ; TV non touchée). Comptages = code.
- Le comportement de `CastSession` en file pendant un envoi (`S/player/CastSession.kt:179`) est lu, pas rejoué : ux-07 le vérifie avant de dégriser.
- La TV peut garder le drapeau « ouvert depuis une tuile » si l'usager ouvre une tuile d'activité (Apprendre…), revient, puis qu'une vidéo est lancée **depuis le téléphone** : le focus revient alors sur cette tuile au lieu de la 1ʳᵉ carte (toujours un focus visible, jamais perdu). Noté pour w11-10.
