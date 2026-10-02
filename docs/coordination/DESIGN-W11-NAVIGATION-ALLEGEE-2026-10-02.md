# Conception W11 — Navigation allégée de CastBridge (téléphone) et CastBridge-TV (2026-10-02)

Auteur : architecte UX / information (Fable, conception seulement). Exécution : cahiers `docs/agent-briefs/sonnet-w11-NN-*.md`, index `docs/agent-briefs/SONNET-WAVE11-INDEX.md`.
Demande du propriétaire (2026-10-02) : « l'ergonomie de navigation des fonctionnalités est trop lourde à l'écran, améliore cela ».
Lecture faite sur la branche `integration/agents` (aucune compilation, aucune capture : tout vient du code et des docs ; les lignes citées sont celles de ce jour).

---

## 0. Résumé en dix lignes

1. **Téléphone** : 6 onglets défilants + 4 actions dans la barre du haut + 1 barre de lecture = **11 cibles de navigation permanentes** ; l'onglet ouvert au lancement est « TV DLNA » (`MainActivity.kt:83`), pas l'accueil. L'accueil « CastBridge TV » empile 6 tuiles, 3 cartes d'état et une ligne « Avancé » qui cache 6 fonctions à **3 touches + défilement**.
2. **TV** : **18 tuiles** (19 en essai) dans une seule rangée horizontale (`PlayerActivity.kt:543-607`), dont 9 doublons du menu « Connexion & réglages » (22 à 24 lignes, `PlayerActivity.kt:704-748`) ; **9,5 appuis en moyenne** pour atteindre une tuile depuis la première, 17 pour « Contrôle parental ». Étiquettes à **16 sp** (`TvCards.kt:276`), badge permanent à **14 sp** (`KeyBadgeOverlay.kt:30`) avec jusqu'à 5 phrases concaténées (`KeyBadge.kt:13`).
3. Cible : **5 destinations** sur le téléphone (barre du bas), **6 grandes tuiles** sur la TV + une rangée « Reprendre », tout le reste derrière **« Plus »** ou en contexte.
4. Un **modèle de navigation en `core`** (`castbridge.core.nav`), pur Kotlin, testé en JVM : destinations, visibilité par état (essai / production / super / minimal W6 / enfant), graphe de focus TV, budgets de mots. Les deux apps le **rendent** sans y mettre de logique.
5. **Gains rapides (≤ 1 jour chacun)** : onglet de départ = accueil ; badge TV raccourci à ≤ 4 mots ; descriptions des tuiles hors écran ; suppression des doublons tuile/menu ; tuiles à 3 lignes → 2 lignes ; libellés 19 sp.
6. **Trois nouveaux composants** seulement : `BottomNav` + `PlusSheet` (téléphone), `HomeGrid` 3×2 + `PlusPanel` (TV), `QuickActions` (« Que voulez-vous faire ? », 3-4 suggestions selon le contexte).
7. **États** : l'essai, le mode minimal W6 et le mode enfant **réduisent** l'accueil au lieu de le griser ; une seule rangée discrète « Version complète » regroupe ce qui est fermé.
8. **W5/W6/W7/W10 absorbés** sans nouvelle tuile : la boutique entre par le contenu (« Obtenir ») et par « Plus », les jetons par la boutique et le quiz, les rapports parentaux par la destination « Parents », l'état de connexion par **une** puce.
9. **Mesure sans télémétrie** : test JVM du graphe de focus (≤ 3 appuis en moyenne depuis l'accueil TV), script de « touches jusqu'à la fonction » sur émulateur, protocole de 5 usagers réels (dont 2 peu lettrés / âgés), seuil de réussite ≥ 90 % au premier essai et ≤ 3 touches.
10. **Risques** : bugs de focus D-pad sur la vraie TV (GaiaOS 32 bits 720p) ; régressions des ids de télémétrie ; conflit avec w6-16 (« ne pas masquer d'onglet ») résolu par un cadenas visible, pas une disparition.

---

## 1. Diagnostic

### 1.1 Inventaire — CastBridge (téléphone)

Point d'entrée `MainActivity.Root()` (`android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt:82-136`).

| Niveau | Élément | Preuve | Touches depuis le lancement | Profondeur |
|---|---|---|---|---|
| Barre du haut | Logo (entrée propriétaire cachée : 7 touches + appui long) | `MainActivity.kt:99-104` | 8 gestes | 0 |
| Barre du haut | « Activer la TV » (texte) | `MainActivity.kt:107` | 1 | 1 |
| Barre du haut | « Locations » (texte) | `MainActivity.kt:108` | 1 | 1 |
| Barre du haut | Cadenas « Contrôle parental » | `MainActivity.kt:109` | 1 | 1 |
| Barre du haut | « Réglages » | `MainActivity.kt:110` | 1 | 1 |
| Onglets (défilants, 6) | TV DLNA · CastBridge TV · Jeux · Sur le téléphone · Apprendre · Parental | `MainActivity.kt:115-123` ; onglet initial = 0 = **TV DLNA** (`:83`) | 1 | 1 |
| Barre du bas | `CastMiniBar` (lecture en cours) | `MainActivity.kt:126` | — | — |
| Onglet « CastBridge TV » | Puce de liaison : titre, détail, indice, 1 bouton d'action, « Mes TV », « Diagnostic » | `TvPairScreen.kt:81-96` | 1 | 1 |
| idem | Ligne « … libres sur la TV » | `TvHome.kt:172` | — | — |
| idem | Carte « Envoi vers la TV » (quand envoi) | `TvHome.kt:198-219` | — | — |
| idem | Carte « File d'attente des envois » | `TvHome.kt:226-250` | — | — |
| idem | Carte « Classer les séries » : interrupteur + « Classer maintenant » + « Annuler le dernier classement » | `TvHome.kt:257-280` | 2 | 2 |
| idem | Carte « Sur la TV » (lecture) : 2 boutons | `TvHome.kt:304-319` | 2 | 2 |
| idem | 6 tuiles : Envoyer une vidéo · Déplacer vers la TV · Regarder sur la TV · Télécommande · Bibliothèque de la TV · Échanger des fichiers | `TvHome.kt:322-336` | 2 | 2 |
| idem | Rangée « Reprendre sur la TV » / « Récemment ajoutés » (8 affiches) | `TvHome.kt:339-346` | 2 | 2 |
| idem | Ligne « Avancé » (texte d'aide : 6 fonctions énumérées) | `TvHome.kt:351-356` | 2 | 2 |
| Avancé | 3 canaux segmentés Wi-Fi · Bluetooth · Wi-Fi Direct | `TvHub.kt:40-46` | 3 | 3 |
| Avancé › Wi-Fi | `TvScreen` (lecteurs, adresse manuelle, PIN, fichier…) + `TvTools` (Bibliothèque, Échange de fichiers) + `DownloadsEntry` + `AdminPanel` (stockage, volume, infos système, `UpdatePanel` 3 boutons, « Redémarrer l'app TV ») | `TvHub.kt:48`, `TvLibraryScreen.kt:231-242`, `DownloadsScreen.kt:51-63`, `TvHub.kt:151-192`, `:197-283` | 3 + défilement | 3-4 |
| Avancé › Bluetooth | Appairés, PIN, fichier, Envoyer, passerelle Internet, panneau SSH | `TvHub.kt:85-147` | 3 | 3 |
| Onglet « Jeux » | 3 cartes (Quiz, Échecs, Sudoku) + bouton « Données hors ligne » | `GamesScreen.kt:71-94` | 2 | 2 |
| Onglet « Sur le téléphone » | Recherche + 5 sous-onglets (Récents, Vidéos, Musique, Photos, Dossiers) + grille ; menu long-appui 4 entrées | `PhoneLibrary.kt:60`, `:139-184` | 2-3 | 3 |
| Onglet « Apprendre » | « Données hors ligne » + 3 sous-onglets (Leçons, Piloter la TV, Parents) ; Leçons → Mes classes → pack → leçon → série | `LearnScreen.kt:53-64`, `:89-144` | 4 pour une leçon | 4 |
| Onglet « Parental » | Porte PIN puis 9 sections | `ParentalTab.kt:41-49`, `:144` | 2 + PIN | 3 |
| Réglages | À propos (+3 puces de thème) · Confidentialité (interrupteur + 3 boutons) · Mises à jour (2 boutons) · Données hors ligne (2 boutons) · Connexion · Contrôle parental (bouton) · Avancé (dépliable : serveur 2 boutons, `TvServerPanel` 3 boutons) | `ConnectScreens.kt:110-140`, `:274-297`, `:355-366` | 2 + défilement | 2-3 |
| Télécommande | Menu ⋮ : 6 entrées (dont « Ma TV » → `MyTvActivity`, « voies Bluetooth » → `BtRoutesScreen`) | `RemoteScreen.kt:315-327` | 3 | 3 |
| Bibliothèque de la TV | Assistant « Ranger ma bibliothèque » (icône + bandeau) | `TvLibraryScreen.kt:128-133` | 3 | 3 |
| Activités hors arbre | `ShareToTvActivity`, `OpenWithActivity` (intentions système), `player/PlayerActivity` | manifeste | — | — |

**Chiffres téléphone (écran « CastBridge TV », TV reliée, bibliothèque non vide)** :
- cibles tactiles permanentes de navigation : 4 (haut) + 6 (onglets) + 1 (barre du bas) = **11** ;
- cibles sur l'écran lui-même : 3 (puce) + 3 (séries) + 2 (lecture) + 6 (tuiles) + 8 (affiches) + 1 (Avancé) = **23**, soit **34 cibles** en tout, dont ~**30 sans défiler** sur un écran de 6" ;
- éléments de texte visibles : ≈ **45** (6 tuiles × 2 lignes, puce 3 lignes, carte séries 4 lignes, Avancé 2 lignes, en-têtes) ; mots sur l'écran ≈ **190** ;
- touches jusqu'à : envoyer une vidéo **2** (+ sélecteur), télécommande **2**, leçon **4**, quiz **2**, téléchargements TV **3 + défilement**, installer un APK sur la TV **3 + défilement**, volume TV **3 + défilement**, « Ma TV » **3**, contenus libres **2 + défilement**, données hors ligne **2** (trois entrées différentes : Réglages, Apprendre, Jeux) ;
- doublons : « Contrôle parental » (onglet + cadenas + Réglages), « Bibliothèque de la TV » (tuile + `TvTools`), « Échanger des fichiers » (tuile + `TvTools`), « Données hors ligne » (3 entrées), « Télécommande » (tuile + carte lecture + feuille de lecture), « Ajouter ma TV » (puce + assistant + « Mes TV »).

### 1.2 Inventaire — CastBridge-TV

Point d'entrée `PlayerActivity.homeTools()` (`android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt:530-608`), rendu par `HomeScreen` (`HomeScreen.kt:134-213`).

| # | Tuile (étiquette) | id télémétrie | Preuve | Ce que OK ouvre | Appuis depuis la 1re tuile |
|---|---|---|---|---|---|
| 0 | Passer en production (essai seulement) | `upgrade` | `:539-542` | `ActivationActivity` | 1 |
| 1 | Bibliothèque | `library` | `:544` | grille | 1 |
| 2 | Ajouter un téléphone | `bluetooth` | `:545-546` | `PairActivity` | 2 |
| 3 | Apprendre | `learn` | `:547-549` | `LearnActivity` | 3 |
| 4 | Langues | `langues` (hors catalogue `EventCatalog.TV_FEATURES`, `Telemetry.kt:16-17`) | `:550-553` | `LanguesActivity` (5 niveaux : langue › niveau › thème › unité › contenu, `LanguesActivity.kt:84-115`) | 4 |
| 5 | Jeux | `games` | `:555-557` | `GamesActivity` (3 tuiles) | 5 |
| 6 | Téléchargements | `downloads` | `:558-560` | `DownloadsActivity` | 6 |
| 7 | Télécommande | `remote` | `:561-564` | `RemoteSetupActivity` | 7 |
| 8 | Recevoir du téléphone | `receive` | `:565` | dialogue d'aide (**même action que « Aide »**) | 8 |
| 9 | Clé USB | `usb` | `:566-575` | sous-menu 5-6 entrées | 9 |
| 10 | Bluetooth | `bluetooth` (**même id que #2**) | `:576-581` | sous-menu 2 entrées (dont « Ajouter un téléphone » = #2) | 10 |
| 11 | Internet | `internet` | `:582-584` | sous-menu 4 tests | 11 |
| 12 | Wi-Fi Direct | `wifi_direct` | `:585` | bascule | 12 |
| 13 | Administration | `admin` | `:586-598` | sous-menu 3 entrées | 13 |
| 14 | Mises à jour | `updates` | `:599-600` | `ServerActivity` | 14 |
| 15 | Connexion & réglages | `settings` | `:601` | `SettingsPanel` | 15 |
| 16 | Options développeur | `dev_options` | `:602` | réglages Android | 16 |
| 17 | Contrôle parental | (aucun, volontaire) | `:604-605` | `ParentalActivity` | 17 |
| 18 | Aide | `help` | `:606` | dialogue d'aide | 18 |

**Chiffres TV (accueil, production, bibliothèque non vide)** :
- **18 tuiles** (19 en essai, 14 après filtrage de l'essai `ParentalHub.kt:271-274`) dans **une** rangée horizontale `HorizontalScrollView` (`HomeScreen.kt:183-193`) ; largeur d'une tuile 170 dp + marges 20 dp = 190 dp ; zone utile ≈ 1 184 dp → **6 tuiles visibles**, 12 hors écran ;
- appuis D-pad depuis la première tuile : moyenne **(0+…+17)/18 + 1 OK = 9,5**, maximum **18** ; le focus initial est sur la première **carte vidéo** (`HomeScreen.kt:174-177`), donc **+1 (HAUT)** pour toute tuile ;
- chaque tuile : icône 46 dp + étiquette **16 sp** sur 2 lignes + ligne d'état 16 sp (`TvCards.kt:271-282`) ; description de 10 à 20 mots envoyée dans le héros à chaque focus (`HomeScreen.kt:209`) ;
- éléments d'état simultanés sur l'accueil : logo, puce « ● Prêt à recevoir · code 12•••• » (`HomeScreen.kt:126`), horloge, titre + sous-titre du héros, **badge de clé** (haut centre, 14 sp, `KeyBadgeOverlay.kt:30-31`, texte = titre + jusqu'à 4 phrases jointes par « · », `KeyBadge.kt:13`, ex. essai : « ESSAI · Clé valable jusqu'au 31/12/2026 (90 j) · Lots locatifs : 12 h d'essai, une seule fois · Streaming, Sudoku et lots d'essai seulement · « Passer en production » pour tout débloquer » ≈ **35 mots**), **colonne de puces de statut** en haut à droite (une par connexion/mode, + « +n », `StatusBarView.kt:59-82`), bannière éphémère, ligne « lead » ⇒ **6 à 12 éléments d'état** en même temps ;
- rangées sous les tuiles : jusqu'à 5 (« Reprendre », « Récemment ajoutés », « Sur la clé USB », « Toutes les vidéos », « Autres fichiers », `HomeScreen.kt:140-146`) ;
- « Connexion & réglages » : gauche **15 à 19** paires clé/valeur (`PlayerActivity.kt:615-631`), droite **22 à 24** actions en liste (`:704-748`), aussi ouverte par MENU pendant une vidéo (`:696-702`) ;
- **doublons tuile ↔ menu** : Bibliothèque, Téléchargements, Ajouter un téléphone, Bluetooth visible, Wi-Fi Direct, USB (5 entrées × 2), Stockage, Internet, Mises à jour, Options développeur, SSH ⇒ **11 fonctions atteignables par deux chemins**, plus « Recevoir du téléphone » = « Aide » ;
- touches de couleur : seul BLEU (bibliothèque, `PlayerActivity.kt:1103`) sur l'accueil ; ROUGE/VERT/JAUNE utilisées seulement dans le Sudoku (`SudokuActivity.kt:379-381`) et le lecteur de leçons ;
- mémoire de focus : `HomeScreen.show()` refait toujours `focusFirst = true` (`HomeScreen.kt:113`) ⇒ retour d'une vidéo ou d'un hub = focus perdu ; la rangée d'outils, elle, garde son index (`:204-212`) ;
- pile Retour : accueil → `moveTaskToBack` (`PlayerActivity.kt:1058`) ; réglages/bibliothèque → accueil (`:1056-1057`) ;
- filtrage par **étiquette** et non par id : essai (`ParentalHub.kt:271`), mode enfant (`ParentalModel.kt:227-230` : `KID_HOME` cite « Quiz » et « Échecs » alors que la tuile s'appelle « Jeux », `PlayerActivity.kt:555`) ⇒ **en mode enfant la tuile « Jeux » disparaît** (à vérifier sur appareil, mais la logique le dit).

### 1.3 Pourquoi c'est lourd (diagnostic en cinq causes)

1. **Tout est au même niveau.** 18 tuiles TV et 11 + 23 cibles téléphone sans hiérarchie : une connexion Bluetooth et « Regarder une vidéo » pèsent le même poids visuel.
2. **Les états débordent sur la navigation.** Badge de clé, puces de statut, puce « prêt · code », cartes d'envoi, de file, de séries : l'état occupe le tiers haut de chaque écran, et une partie se répète (code de la TV dans la puce, dans l'aide, dans « Recevoir », dans les réglages).
3. **Les doublons rassurent le développeur, pas l'usager.** Onze fonctions TV ont deux chemins ; trois boutons « Données hors ligne » sur le téléphone ; « Aide » = « Recevoir du téléphone ».
4. **Les mots remplacent la hiérarchie.** Sous-titres de tuiles, descriptions de 20 mots, texte d'aide d'« Avancé » énumérant 6 fonctions, menu TV de 24 lignes de 6 à 12 mots.
5. **Le point de départ est faux.** Le téléphone s'ouvre sur « TV DLNA » (fonction rare) ; la TV pose le focus sur une vignette et non sur la tâche la plus probable (reprendre).

---

## 2. Principes de la nouvelle architecture

| Principe | Règle vérifiable |
|---|---|
| Un écran, un but | Un écran porte **une** question (« Que voulez-vous faire ? », « Quelle vidéo ? », « Quel réglage ? »). Aucun écran ne mélange état technique et choix d'une tâche. |
| Divulgation progressive | Niveau 1 : ≤ 5 destinations (téléphone) / ≤ 6 tuiles (TV). Niveau 2 : « Plus » (une liste groupée). Niveau 3 : contextuel (sur un contenu, sur la puce de connexion). Jamais de niveau 4 sauf dans le contenu lui-même (leçon › exercice). |
| Entrées contextuelles | La boutique s'ouvre depuis le contenu fermé (« Obtenir »), la réparation de connexion depuis la puce, les réglages du lecteur depuis le lecteur. |
| Simplification par état | Essai, grâce, minimal (W6), enfant, super : le **même** modèle de navigation retire ou cadenasse des entrées ; aucun écran spécial sauf le mur W6. |
| Régime de texte | Titre de destination : **≤ 2 mots**. Tuile TV : étiquette ≤ 2 mots, état ≤ 3 mots. Ligne de « Plus » : ≤ 4 mots. Aide : derrière un « ? » ou un appui long, jamais en permanence. |
| Densité | Téléphone : ≤ 8 cibles par écran sans défiler, zone du pouce pour les 3 principales. TV 720p à 3 m : ≤ 7 cibles focusables par écran, texte ≥ 19 sp (étiquettes) et ≥ 16 sp (états), anneau de focus 3 dp + échelle 1,04 (déjà `TvStyle`). |
| Mouvement | TV bas de gamme : seulement échelle/alpha/translation GPU ≤ 150 ms ; pas de flou, pas de défilement animé des rangées ; Ken Burns conservé (déjà GPU). |
| Mémoire | La TV revient **là où elle était** (destination + index) ; le téléphone rouvre la dernière destination. |
| Un modèle, deux rendus | `castbridge.core.nav` décrit destinations, visibilité et graphe de focus ; `sender` et `receiver` ne font que dessiner. Les tests JVM vivent dans `core` (ni `sender` ni `receiver` n'ont de `src/test`, vérifié). |

---

## 3. Nouvelle IA — CastBridge (téléphone)

### 3.1 Cinq destinations (barre du bas)

| Position | Destination (≤ 2 mots) | Icône charte | Contenu | Remplace |
|---|---|---|---|---|
| 1 | **Accueil** | `ic_cb_recevoir_du_telephone` | puce TV (1 ligne) · « Que voulez-vous faire ? » 3-4 actions · « Reprendre sur la TV » · envoi en cours (carte unique) | onglet « CastBridge TV » (`TvHome.kt`) |
| 2 | **Mes fichiers** | `ic_cb_sur_le_telephone` | bibliothèque du téléphone (recherche + 5 sous-onglets existants) ; action « Envoyer / Déplacer » par appui long ou bouton flottant | onglet « Sur le téléphone » |
| 3 | **Apprendre** | `ic_cb_apprendre` | Leçons · Langues (nouveau sous-onglet, W-langues) · Quiz-révision ; « Piloter la TV » devient une action contextuelle d'une leçon | onglet « Apprendre » + Langues |
| 4 | **Jeux** | `ic_cb_quiz` | 3 cartes (inchangé) | onglet « Jeux » |
| 5 | **Parents** | cadenas | porte PIN puis 4 sections W6 (Aujourd'hui, Semaine, Toute la TV, Parents de cette TV) + réglages de la TV ; pas de 9 sections | onglet « Parental » + cadenas de la barre |

Barre du haut : **logo** (entrée propriétaire cachée conservée telle quelle, `MainActivity.kt:99-104`) et **une seule action** : « Plus » (⋮). Rien d'autre.

### 3.2 « Plus » (feuille du bas, groupée, ≤ 12 lignes, 4 groupes)

| Groupe | Lignes (≤ 4 mots) | Cible existante |
|---|---|---|
| Ma TV | Télécommande · Bibliothèque de la TV · Échanger des fichiers · Téléchargements sur la TV | `RemoteActivity`, `TvLibraryDialog`, `TvTransferDialog`, `DownloadsScreen` |
| Boutique et clés | Boutique (W5) · Activer la TV · Locations sur la TV | `ShopScreen` (w5-11), `ActivateTvActivity`, `RentalDeliveryActivity` |
| Données | Données hors ligne · Contenus libres | `LotsScreen`, `FreeContentActivity` |
| Réglages | Réglages · Connexion avancée (TV DLNA, Bluetooth, Wi-Fi Direct, SSH, installer des APK) · Aide | `SettingsScreen`, `TvHubAdvanced`, dialogue d'aide |

Règles : une ligne = icône + libellé, pas de sous-titre ; les lignes fermées par l'état (essai, minimal W6) portent un **cadenas** et ouvrent le message de déblocage (catalogue `PhoneGateTexts`, w6-02), elles ne disparaissent pas (règle de w6-16 : « ne pas masquer d'onglet », étendue aux lignes de « Plus »).

### 3.3 Accueil : « Que voulez-vous faire ? »

3 à 4 **actions suggérées** calculées par `QuickActions.suggest(ctx)` (cœur, testé) :

| Contexte | Suggestions (ordre) |
|---|---|
| TV reliée, une vidéo en pause sur la TV | Reprendre « titre » · Envoyer une vidéo · Télécommande |
| TV reliée, rien en cours | Envoyer une vidéo · Regarder sur la TV · Une leçon (dernière classe) · Un jeu |
| TV reliée, leçon ouverte sur la TV | Piloter la leçon · Envoyer une vidéo · Télécommande |
| Aucune TV | Ajouter ma TV · Mes fichiers · Une leçon · Un jeu |
| Essai (TV en essai) | Regarder en direct · Une leçon d'essai · Sudoku · **Passer en production** |
| Minimal W6 (téléphone non prouvé) | Relier ma TV · Une leçon · Un jeu · (cadenas) Envoyer une vidéo → mur |
| Enfant actif sur la TV | Une leçon · Un jeu · (bandeau « TV en mode enfant ») |

Chaque suggestion = **grande cible** (hauteur ≥ 64 dp, icône + ≤ 3 mots), 2 par rangée, dans la **zone du pouce** (moitié basse). Au-dessus : la **puce TV** (1 ligne : point de couleur + nom + état en ≤ 3 mots ; toucher = feuille « Connexion » avec l'action unique de `LinkView` + « Mes TV » + « Diagnostic » ; la feuille remplace les 3 boutons permanents de `TvPairScreen.kt:89-95`). En dessous : « Reprendre sur la TV » (rangée existante `TvHome.kt:339-346`, 6 affiches max). La carte d'envoi (`TvHome.kt:198-219`) et la file (`:226-250`) deviennent **une** carte compacte « Envoi : 42 % · 2 en attente » ; la carte « Classer les séries » (`:257-280`) part dans Bibliothèque de la TV (« Plus ») et dans l'assistant.

### 3.4 Réglages réorganisés (`SettingsScreen`)

4 sections au lieu de 7, chacune en une ligne qui s'ouvre : **Apparence** (thème) · **Confidentialité** (consentement, mes données) · **Mises à jour** (téléphone, TV) · **Avancé** (serveur, serveur de la TV, voies Bluetooth, Ma TV / autres marques). « Données hors ligne » et « Contenus libres » quittent les réglages (ils sont dans « Plus › Données ») ; « Contrôle parental » quitte les réglages (destination « Parents »).

### 3.5 Zone du pouce et gestes

- Barre du bas 5 destinations (56 dp) ; au-dessus, `CastMiniBar` quand une lecture est en cours (inchangé).
- Bouton flottant **unique** et contextuel : « Envoyer » dans Mes fichiers (sélection multiple), « Piloter » dans une leçon ; jamais ailleurs.
- Appui long sur un fichier : feuille à 4 lignes existante (`PhoneLibrary.kt:175-180`).
- Retour : depuis une destination → Accueil ; depuis Accueil → quitter (comportement Android standard).

---

## 4. Nouvelle IA — CastBridge-TV

### 4.1 Accueil : 6 grandes tuiles + « Reprendre »

```
Rangée « Reprendre » (0 à 6 cartes, focus initial sur la 1re, sinon sur la tuile 1)
Grille 3 × 2 :
  [ Regarder ]   [ Apprendre ]   [ Jeux ]
  [ Téléphone ]  [ Parents ]     [ Plus ]
```

| Tuile (≤ 2 mots) | id | État (≤ 3 mots) | OK ouvre | Appui long / MENU |
|---|---|---|---|---|
| Regarder | `library` | « 124 vidéos » | grille `LibraryScreen` | « Clé USB », « Téléchargements » |
| Apprendre | `learn` | « CM2 · Jean » | `LearnActivity` (+ Langues en 1re ligne du hub) | « Langues », « Profils » |
| Jeux | `games` | « 3 jeux » | `GamesActivity` | — |
| Téléphone | `receive` | « 2 de confiance » / « Code 12•••• » | **une** page « Téléphone » : code en grand, « Ajouter un téléphone », « Rendre visible », état Bluetooth/Wi-Fi/Internet (fusion de #2, #8, #10, #11, aide) | « Diagnostic Internet » |
| Parents | (aucun id, comme aujourd'hui) | « Actif · Léa » | `ParentalActivity` | — |
| Plus | `settings` | « Réglages, outils » | `PlusPanel` | — |

En essai : la tuile **Plus** affiche « Version complète » en état et la première ligne de `PlusPanel` est « Passer en production » (remplace la tuile #0). Les tuiles fermées (`TrialPolicy.CLOSED_TILES`) **ne sont pas dessinées en gris** : « Regarder » reste (streaming) mais sa grille dit en une ligne « Vidéos stockées : version complète » ; Langues apparaît comme **une** ligne cadenassée dans Apprendre.

### 4.2 `PlusPanel` (remplace la rangée d'outils et les 22-24 lignes du menu)

Deux colonnes, **≤ 12 lignes**, groupées, chaque ligne ≤ 4 mots :

| Groupe | Lignes |
|---|---|
| Stockage | Clé USB · Où ranger les fichiers · Téléchargements |
| Connexions | Téléphone (même page que la tuile) · Wi-Fi Direct (bascule) · Administration (SSH, API Bluetooth, page web) |
| Appareil | Mises à jour · Boutique (W5, si en ligne ou via téléphone) · Démarrer avec la TV (bascule) · Lecture à distance (bascule) |
| À propos | Assistance à distance · Confidentialité · Options développeur · Aide |

Les informations de « Connexion & réglages » (15-19 paires) deviennent la page **Téléphone** (code, adresse, téléphones de confiance) et la page **À propos** (version, identifiant, serveur, assistance) : aucune liste de 19 lignes à lire.

### 4.3 Carte de focus D-pad (graphe `TvFocusGraph`, testé en JVM)

- Nœuds : `resume[0..n]`, `tile[0..5]`, `chip`, `status[0..k]`.
- Arêtes : `tile[i]` → DROITE `tile[i+1]` (même rangée), BAS `tile[i+3]`, HAUT `tile[i-3]` ou `resume[0]` ; `resume[j]` → BAS `tile[0]` (colonne la plus proche) ; `tile[0]`/`resume[0]` → HAUT `chip` → HAUT `status[0]`.
- Focus initial : `resume[0]` si la rangée existe, sinon `tile[0]`.
- **Coût** depuis le focus initial jusqu'à OK sur une tuile : Regarder 2, Apprendre 2, Jeux 3, Téléphone 3, Parents 3, Plus 4 (avec « Reprendre » non vide : +1 sur chaque) ⇒ **moyenne 2,8 (3,8 avec Reprendre)**, maximum 5 ; contre 9,5 / 18 aujourd'hui. Une ligne de `PlusPanel` : ≤ 4 + 6 = 10 au pire, 7 en moyenne (contre 15 + position du menu aujourd'hui).
- Test JVM : `TvFocusGraphTest` calcule le plus court chemin de chaque nœud à chaque destination et échoue si la moyenne depuis le focus initial dépasse **3,0** (sans Reprendre) / **4,0** (avec), ou si un nœud est inatteignable, ou si une arête quitte l'écran.

### 4.4 Touches

| Touche | Accueil | Vidéo | Hub (Apprendre, Jeux, Plus) |
|---|---|---|---|
| OK | ouvrir | lecture/pause | ouvrir |
| MENU / appui long OK | actions contextuelles de la tuile ou carte | panneau du lecteur (inchangé) | actions de l'élément |
| RETOUR | **une seule fois** : quitter (`moveTaskToBack`) | stop → accueil | accueil (jamais de double Retour) |
| BLEU | Regarder (bibliothèque) — déjà `LIBRARY_KEYS` | — | — |
| VERT | Apprendre | — | — |
| JAUNE | Jeux | sous-titres (`CAPTIONS` déjà) | — |
| ROUGE | Téléphone (code en grand) | infos (`INFO` déjà) | — |
| GUIDE / INFO | Plus | infos | — |

Les couleurs sont des **raccourcis**, jamais l'unique chemin (toutes les télécommandes ne les ont pas). Un rappel discret « ■ ■ ■ ■ » de 4 pastilles de couleur avec un mot chacune, en bas de l'accueil, remplace la ligne d'aide `LibraryScreen.header()` (`PlayerActivity.kt:654`).

### 4.5 Pile Retour, mémoire de focus, veille

- Pile : `accueil → hub → écran de contenu` ; Retour remonte d'un cran ; un hub ne pousse jamais un autre hub (depuis Apprendre, « Langues » est une ligne du hub, pas un hub empilé).
- Mémoire : `HomeScreen.show(restore = true)` garde `(destination, index)` dans `TvPrefs` (`home_focus`) ; retour d'une vidéo ⇒ focus sur la carte « Reprendre » de cette vidéo (elle est en 1re position) ; retour d'un hub ⇒ sur la tuile du hub.
- Veille : après 10 min sans touche sur l'accueil, le héros et les tuiles s'estompent à 40 % et le Ken Burns continue (pas de nouvelle animation) ; une touche restaure (pas de capture d'écran, pas d'économiseur séparé).
- Sans voix : rien ne dépend d'un micro.
- Hubs profonds existants (hors périmètre de W11, noté pour une vague contenu) : Apprendre TV = accueil › Apprendre › « Qui apprend ? » › accueil de l'élève (7 tuiles) › matière › fiche › page (`docs/LEARN.md:15-34`, 5-6 niveaux) ; Langues TV = 5 niveaux (`LanguesActivity.kt:84-115`). Deux règles immédiates et peu coûteuses : (1) un seul profil ⇒ « Qui apprend ? » est sauté ; (2) la première carte de l'accueil de l'élève est toujours « Reprendre » quand une leçon est en cours.

### 4.6 Éléments permanents : badge et puces

- **Badge de clé** (reste visible partout, exigence conservée) : `Badge.short` = titre + **un** complément ≤ 4 mots (« ESSAI · 11 h restantes », « PRODUCTION · jusqu'au 31/12 », « SUPER ILLIMITÉ », « SANS CLÉ ») à **16 sp**, coin **haut gauche sous le logo** (jamais au centre, jamais au-dessus du héros), opacité 85 % ; le texte complet (`Badge.text`) reste dans « Plus › À propos » et sur OK/INFO quand le badge est focusable (accueil seulement, comme les puces). Pendant une vidéo : 12 sp, opacité 50 %, disparaît avec la barre de progression après 4 s, revient avec elle.
- **Puces de statut** (`StatusBarView`) : ≤ **3** puces + « +n » sur l'accueil (aujourd'hui non borné autrement que par `bar.hidden`), **0** pendant une vidéo sauf ERREUR ; les étiquettes françaises ne s'affichent qu'au focus ou 4 s après un changement (déjà le cas, `StatusBarView.kt:134`).
- **Puce « prêt · code »** : fusionne avec la tuile « Téléphone » ; la ligne « ● Prêt à recevoir · code 12•••• » n'est plus dans l'en-tête. L'en-tête garde logo, badge court, horloge.

### 4.7 Règles de densité et de typographie TV (720p, 3 m)

| Élément | Minimum | Aujourd'hui |
|---|---|---|
| Étiquette de tuile | **19 sp** (= `Type.BODY`), 1 ligne, ≤ 2 mots | 16 sp, 2 lignes (`TvCards.kt:276`) |
| Ligne d'état | 16 sp, ≤ 3 mots | 16 sp, non bornée (`IconTile`) |
| Lignes de `PlusPanel` | 21 sp (`Type.SUBTITLE`), ≤ 4 mots | 19 sp, 6-12 mots |
| Badge | 16 sp / 12 sp en vidéo | 14 sp |
| Cibles focusables par écran | ≤ 7 (accueil : 6 tuiles + 1 rangée) | 18 + 5 rangées + puces |
| Contraste texte/fond | ≥ 4,5:1 (jetons `BrandTokens.Dark`) | ok |
| Anneau de focus | 3 dp `RING` + échelle 1,04 (inchangé) | ok |
| Mouvement | ≤ 150 ms, propriétés GPU seulement ; aucune animation en entrée de liste | ok sauf `notifyDataSetChanged` sur grilles |

---

## 5. États : essai, grâce, production, super, minimal (W6), enfant

| État | Téléphone (barre + Plus) | TV (accueil + Plus) | Source de vérité |
|---|---|---|---|
| Production | tout | tout | `GateState.Activated`, `TvAccess` |
| Essai / grâce restreinte | Accueil (suggestions d'essai) ; cadenas sur « Envoyer », « Bibliothèque de la TV », « Téléchargements » ; « Passer en production » en 1re ligne de Plus | 6 tuiles conservées ; `Regarder` = streaming + ligne « Vidéos stockées : version complète » ; Langues = ligne cadenassée ; Plus : 1re ligne « Passer en production » ; lignes fermées **absentes** (pas grises) ; badge « ESSAI · 11 h restantes » | `TrialPolicy.CLOSED_TILES` (par **id**, plus par étiquette) |
| Verrouillée | — | `ActivationActivity` seule (inchangé) | `GateState.Locked` |
| Super illimité | tout + bandeau de session (w6-19) | tout ; badge « SUPER ILLIMITÉ » | `Right.Super` |
| Minimal W6 (téléphone non prouvé) | 5 destinations **visibles** ; les fonctions fermées portent un cadenas et ouvrent `GateWall` (w6-16) ; une seule rangée « Pour tout débloquer : reliez une TV en production » sur l'Accueil | sans effet | `PhoneGate.state` (w6-01) |
| Enfant actif (TV) | bandeau « TV en mode enfant (prénom) » sur l'Accueil ; pas de code parental demandé sur le téléphone (w6-16) | accueil réduit : Regarder (filtré), Apprendre, Jeux (si non bloqué), Parents ; **Téléphone** et **Plus** masqués ; code de la TV masqué | `ParentalRules.kidHome` par **id** (`library`, `learn`, `games`, `parental`) au lieu d'étiquettes |
| Point focal / agent (w4-13, w5-13) | Plus › « Mode point focal » | — | `AGENT_WHITELIST` |

Règle commune : **un état retire ou cadenasse, il n'ajoute jamais de tuile** (l'exception « Passer en production » est une ligne de Plus, pas une tuile).

---

## 6. Absorption des fonctions à venir (W5, W6, W7, W10)

Vérifié le 2026-10-02 : `docs/coordination/` contient W4, W5, W6 (suivis) et, **non suivis par git, créés le même jour par d'autres sessions** : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md`, `DESIGN-W8-TRANSPORT-MULTIVOIE.md`, `DESIGN-W10-BOUTIQUE-PRODUCTEURS-LOCAUX-2026-10-02.md` (pas de W9 de conception, seulement des cahiers w9-* de production de contenus). Ils sont absorbés ci-dessous (§ 6.4-6.6) ; leurs cahiers touchent des fichiers que W11 touche aussi : l'index W11 fixe l'ordre de fusion.

### 6.1 W5 — boutique, locations, jetons (`DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md`)

| Ce que W5 prévoit | Où W5 le plaçait | Où W11 le place | Pourquoi |
|---|---|---|---|
| Téléphone : écran « Boutique » 5 sections (Leçons à louer, Jetons du Quiz, Payer, Mes commandes/reçus, Ma TV) (`:289-297`) | remplace « Locations » dans la barre du haut | **Plus › Boutique et clés › Boutique** + bouton **« Obtenir »** sur toute classe/langue absente ou fermée (Apprendre) + ligne « Jetons : 23 » dans Jeux › Quiz | la barre du haut n'a plus qu'une action ; l'achat part du besoin |
| `RentalDeliveryActivity` → « Livraison avancée » dans ⋯ | menu ⋯ | Plus › Boutique et clés › **Locations sur la TV** (même écran) | inchangé, nom plus clair |
| TV : tuile « Boutique » après « Jeux » (`:301-306`), `ShopActivity` à 5 entrées, touches de couleur internes (rouge Annuler, vert Valider, jaune Code, bleu Aide) | tuile d'accueil | **Plus › Appareil › Boutique** + « Obtenir sur le téléphone » dans Apprendre (contenu fermé) ; les touches de couleur de `ShopActivity` restent **dans** la boutique (l'accueil a les siennes, pas de conflit) | 6 tuiles maximum (D-W11-2) |
| « À propos : Boutique et jetons » (`:388`) | menu | Plus › À propos | — |
| Jetons et bonus du Quiz (`:262-264`) | dans le Quiz | inchangé (contextuel) ; solde affiché dans l'état de la tuile Jeux seulement si > 0 (« 3 jeux · 23 jetons ») | pas de nouvelle entrée |
| Catégorie parentale « Achats et jetons », « Demandez à un parent » (`:283-285`) | boutique | inchangé ; l'allocation quotidienne se règle dans **Parents › Règles de la TV** | — |
| Essai : boutique en lecture seule ; mode réduit : saisie d'un bon `cle-production` | boutique | la ligne « Passer en production » / « Renouveler la clé » de Plus ouvre l'activation **et** propose « Boutique » en second bouton | une seule porte vers la version complète |
| Télémétrie `shop`, `tokens_spend` | — | ajoutés avec `plus`, `phone_page`, `quick_action`, `langues` | liste close |

### 6.2 W6 — rapports parentaux, mode minimal, session super (`DESIGN-W6-PARENTAL-PHONE-GATE.md`)

| Ce que W6 prévoit | Où W11 le place |
|---|---|
| Porte du téléphone : onglets fermés **visibles avec cadenas**, `GateWall` (titre « Connectez-vous à une TV activée en production pour tout débloquer », 3 boutons) (`:168-175`) | les 5 destinations restent visibles ; le cadenas est sur la destination et sur les lignes de Plus ; le mur est inchangé ; l'Accueil montre **une** rangée « Reliez une TV en production pour tout débloquer » (pas de tuiles grises) |
| Puce « TV cible : <nom> · production (preuve il y a 2 h) » (`:271`) | **c'est la puce TV de l'Accueil** : « ● Salon · production » ; toucher = feuille Connexion (action unique de `LinkView`, « Mes TV », « Dépannage », état de la preuve) ; la feuille remplace les 3 « Diagnostic » (UX-11) |
| Ligne « Mode : minimal / complet / super administrateur / point focal » (`:175`) | Réglages › pied de page (une ligne) |
| Bandeau « La TV est en mode enfant (prénom) » (`:273`) | Accueil, sous la puce TV |
| Onglet Parental v2 : 8 sections (`:115-130`) | destination **Parents** ; la porte PIN puis **4 entrées** de premier niveau : Aujourd'hui / Semaine (tableau de bord + Toute la TV), Apprendre et Quiz, Parents de cette TV, Plus (Rapports, Exports, Alertes, Confidentialité, Règles de la TV) : 8 sections conservées, 4 visibles |
| Bandeau de session super « Session super administrateur jusqu'à HH:MM · Fermer » (`:286`) | bandeau fin au-dessus de la barre du bas (comme `CastMiniBar`) ; jamais une destination |
| TV : ligne supplémentaire du badge « Rapports d'usage partagés avec 2 téléphones », icône près de « Contrôle parental », page « Téléphones des parents », « À propos › Contrôle parental et rapports » (`:101-106`) | la ligne **ne va pas** dans le badge court : elle devient l'**icône** sur la tuile Parents (w6-14 autorise « réduire à une icône ») et une ligne dans Plus › À propos ; en mode enfant, l'état de la tuile Parents dit « Résumé envoyé aux parents » ; la page « Téléphones des parents » s'ouvre depuis Parents (TV) |
| `HolderConsentActivity` (consentement à la télécommande) | inchangé (écran modal) |

### 6.3 W4 — mode réduit, vente de terrain (`DESIGN-W4-MODE-DEGRADE.md`, `DESIGN-W4-VENTE-TERRAIN.md`)

| W4 | W11 |
|---|---|
| Tuile « Renouveler la clé » en premier, tuiles `receive`/`usb`/`downloads` grisées « Clé à renouveler », badge « CLÉ TERMINÉE » 3 lignes (`MODE-DEGRADE:33,40`) | Plus › 1re ligne « Renouveler la clé » ; l'état de la tuile Plus = « Clé à renouveler » ; dans Regarder, une ligne « Nouveaux fichiers : clé à renouveler » ; badge court « CLÉ TERMINÉE · renouveler » ; rappel quotidien inchangé |
| Mode « Point focal » du téléphone (`S/focal/**`), « Demander la clé de production » (`ActivateTvActivity`) | Plus › Boutique et clés › « Point focal » (visible seulement si `AGENT_WHITELIST` actif) ; « Activer la TV » inchangé |

### 6.4 W7 — plug-and-play et synchronisation (`DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 8-9)

| Ce que W7 prévoit | Où W11 le place |
|---|---|
| D-W7-7 : onglet « CastBridge TV » par défaut tant qu'aucune TV n'est liée (`§ 8.1`, w7-19 modifie `S/MainActivity.kt`) | w11-01 va plus loin (toujours l'accueil, mémoire du dernier onglet) : **compatible** ; w7-19 et w11-01/07 touchent `S/MainActivity.kt`, `S/TvPairScreen.kt`, `S/TvHome.kt` ⇒ ordre de fusion **w7-19 avant 11c** (index W11) |
| Puce permanente « ● Salon · Wi-Fi / ◐ Reconnexion… / ○ Hors de portée / ✕ Action requise », toucher ⇒ écran « Connexion » ; **un** bouton « Réparer la connexion » (`SelfTest` : une phrase, une action) (`§ 8.2`, w7-08, w7-19 `S/link/RepairScreen.kt`) | **c'est `TvChip`** (§ 3.3) : mêmes quatre glyphes, textes `LinkTexts` (w7-08) ; la feuille Connexion de w11-07 **ouvre `RepairScreen`** quand il existe et ne garde en propre que « Mes TV » ; les trois « Diagnostic » actuels disparaissent (UX-11) |
| Première liaison en 3 touches, « Trouvons votre TV » + [Ajouter ma TV] (`§ 8.1`) | suggestion « Ajouter ma TV » de l'Accueil (aucune TV) ; `FirstConnection` reste l'écran plein de l'Accueil tant qu'aucune TV n'est liée |
| TV : page « Connexion » `R/LinkDiagActivity.kt` (w7-15 : beacon, téléphones, empreinte, journal, « Auto-test », « Rendre visible 2 min », « Autoriser les mises à jour ») dans « Connexion & réglages » | **Plus › Connexions › Connexion** ouvre `LinkDiagActivity` ; la page « Téléphone » (w11-12) garde le code et l'ajout, et son bouton « Diagnostic » appelle `LinkDiagActivity` quand elle existe (sinon l'`internetMenu` actuel) |
| Permissions « juste à temps », plus de `POST_NOTIFICATIONS` au démarrage de l'onglet DLNA (`§ 8.3`) | sans objet pour la navigation ; le DLNA n'est plus un onglet (Plus › Connexion avancée) |
| Télémétrie `link_*`, `sync_latency` | hors navigation |

### 6.5 W8 — transport multivoie (`DESIGN-W8-TRANSPORT-MULTIVOIE.md` § 11)

Seule entrée visible : l'écran « Voies du transfert » dans `TvTransferScreen` (w8-16) ⇒ **Plus › Ma TV › Échanger des fichiers** (déjà là), et la carte d'envoi unique de l'Accueil (§ 3.3) affiche « Envoi : 42 % · Wi-Fi + Bluetooth » quand `BulkTransfer` expose ses voies (une ligne, ≤ 6 mots). w8-16/17 touchent `S/TvTransferScreen.kt`, `S/TvScreen.kt`, `S/ConnectScreens.kt` ⇒ w11-09 (réglages) se fusionne **après** 8c ou avant, jamais en parallèle.

### 6.6 W10 — boutique à trois familles et œuvres locales (`DESIGN-W10-BOUTIQUE-PRODUCTEURS-LOCAUX-2026-10-02.md` § 5)

| Ce que W10 prévoit | Où W11 le place | Pourquoi |
|---|---|---|
| TV : **tuile « Œuvres »** après « Apprendre » (`§ 5.2`, w10-09 : `homeTools`, `R/HomeScreen.kt`) → `WorksActivity` | **pas de 7e tuile** : une **rangée de contenu « Œuvres locales »** entre « Reprendre » et la grille (cartes à couverture issues des aperçus présents, dernière carte « Tout voir » → `WorksActivity`) ; en plus, « Regarder » MENU → « Œuvres locales » ; touche verte **dans** `WorksActivity` inchangée | une rangée de cartes est du contenu, pas de la navigation (§ 2) ; 6 tuiles tenues (D-W11-9) |
| Téléphone : onglets Leçons · Langues · Œuvres locales dans la Boutique de W5 (`§ 5.1`, w10-11 : `S/works/**`, `S/MainActivity.kt` « entrée sans W5 ») | Plus › Boutique et clés › Boutique (onglets W10) ; sans W5, la ligne « Boutique » ouvre `WorksScreen` ; **jamais** d'entrée dans `MainActivity` (w10-11 doit lire l'index W11 : l'« entrée sans W5 » est une ligne de `PhoneNav.plus`) ; option : rangée « Œuvres locales » sur l'Accueil sous « Reprendre » si le catalogue est en cache (D-W11-9) | la barre du haut n'a qu'une action |
| Fiche œuvre : « Louer », « Aperçu gratuit », taille contre le budget | contextuel (inchangé) ; sur la TV, « Obtenir sur le téléphone » quand W5 est absent (texte W10 § 5.2) | — |
| Profil enfant : filtre de classification, pas de « Louer » (w10-10) | inchangé ; la rangée « Œuvres locales » est filtrée par `ParentalHub` comme la bibliothèque | — |
| Télémétrie `work_play` | hors navigation (catalogue étendu par w10-07) | — |

### 6.7 Places réservées et règles transverses

- **Statut plug-and-play** : couvert par la puce unique (§ 3.3, § 6.4) ; aucune seconde puce ne sera ajoutée (règle : 1 puce de liaison par écran).
- **Contact du vendeur** (UX-2, `OWNER_CONTACT`) : dans Aide (Plus) et sur le mur W6, jamais sur l'accueil.
- **Producteurs** (outils de bureau W10, console serveur) : aucune entrée dans les apps ; rien à placer.

### 6.8 Règle d'absorption pour toute vague future

Une fonction nouvelle entre par **l'un des trois** chemins : (1) une suggestion contextuelle de l'Accueil si elle dépend de l'état (ex. « Renouveler »), (2) une ligne de Plus dans un groupe existant, (3) une action sur un contenu. Elle n'ajoute **jamais** une destination ou une tuile ; le test `NavModelTest` échoue au-delà de 5 / 6.

---

## 7. Maquettes (texte)

### 7.1 Téléphone — Accueil, avant / après

```
AVANT (MainActivity.kt:93-136 + TvHome.kt:168-357)        APRÈS
┌──────────────────────────────────────┐                   ┌──────────────────────────────────────┐
│ [logo] Activer la TV Locations 🔒 ⚙ │                   │ [logo]                           ⋮   │
│ TV DLNA │CastBridge TV│Jeux│Sur le…▸│                   │ ● Salon · connectée        ▸         │
│ ● Salon connectée · détail · indice  │                   │                                      │
│   [Action] Mes TV  Diagnostic        │                   │ Reprendre sur la TV                  │
│ 12,3 Go libres sur la TV             │                   │ [■■][■■][■■][■■]                     │
│ ┌ Classer les séries ─── (◯) ┐       │                   │                                      │
│ │ Ex. Prison Break › Saison… │       │                   │ Que voulez-vous faire ?              │
│ │ Automatique… [Classer] [Annuler]   │                   │ ┌────────────┐ ┌────────────┐        │
│ ┌ Sur la TV  titre ▬▬▬  🎮 ⏸ ┐       │                   │ │ ▶ Reprendre│ │ ⇧ Envoyer  │        │
│ [Envoyer une vidéo][Déplacer vers TV]│                   │ │  « titre » │ │  une vidéo │        │
│  Copiée : elle reste… Libère la place│                   │ └────────────┘ └────────────┘        │
│ [Regarder sur la TV][Télécommande]   │                   │ ┌────────────┐ ┌────────────┐        │
│  Choisir une vidéo  Flèches, OK, vol…│                   │ │ 🎮 Télécom.│ │ 📚 Leçon   │        │
│ [Bibliothèque TV][Échanger fichiers] │                   │ └────────────┘ └────────────┘        │
│  124 fichier(s)   Dans les deux sens │                   │                                      │
│ Reprendre sur la TV [■][■][■][■]…    │                   │ Envoi : 42 % · 2 en attente   ✕      │
│ ─────────────────────────────────    │                   ├──────────────────────────────────────┤
│ ⚙ Avancé  Adresse manuelle, Bluet…   │                   │ Accueil  Fichiers  Apprendre Jeux 🔒 │
│ ▶ titre en lecture        ⏸ ■        │                   └──────────────────────────────────────┘
└──────────────────────────────────────┘
34 cibles · ≈ 190 mots · 6 onglets dont 2 hors écran      ≤ 12 cibles · ≈ 35 mots · 5 destinations toutes visibles
```

### 7.2 Téléphone — « Plus »

```
┌──────────────────────────────────────┐
│ Plus                              ✕  │
│ MA TV                                │
│  🎮 Télécommande    📚 Bibliothèque  │
│  ⇅ Échanger         ⬇ Téléchargements│
│ BOUTIQUE ET CLÉS                     │
│  🛒 Boutique        🔑 Activer la TV │
│  📦 Locations                        │
│ DONNÉES                              │
│  💾 Données hors ligne  🆓 Contenus libres │
│ RÉGLAGES                             │
│  ⚙ Réglages  🔧 Connexion avancée  ? Aide │
└──────────────────────────────────────┘
```

### 7.3 TV — Accueil, avant / après (1280 × 720 dp)

```
AVANT (HomeScreen.kt + PlayerActivity.homeTools)
┌────────────────────────────────────────────────────────────────────────────┐
│ [logo]      ESSAI · Clé valable jusqu'au … · Lots locatifs : … · Streaming…│ ← badge 14 sp, 35 mots
│                              ● Prêt à recevoir · code 12••••   20:41  [⌁][⌁][+2] │
│ Titre du héros (34 sp)                                                      │
│ description 2 lignes                                                        │
│ Outils et fonctions                                                         │
│ [Passer][Biblio][Ajouter][Appren][Langues][Jeux] ▸ (12 tuiles hors écran)   │
│ Reprendre · 3   [■■■][■■■][■■■]                                            │
│ Récemment ajoutés · 15 [■■■][■■■][■■■][■■■]…                               │
│ Sur la clé USB · 40 …   Toutes les vidéos · 124 …   Autres fichiers · 9 …  │
└────────────────────────────────────────────────────────────────────────────┘

APRÈS
┌────────────────────────────────────────────────────────────────────────────┐
│ [logo]                                                     20:41   [⌁][⌁]  │
│ ESSAI · 11 h restantes                                                      │
│ Reprendre            [■■■■■][■■■■■][■■■■■]                                  │
│                                                                             │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                      │
│   │  ▶ Regarder  │  │ 📚 Apprendre │  │  🎲 Jeux     │                      │
│   │  124 vidéos  │  │  CM2 · Jean  │  │  3 jeux      │                      │
│   └──────────────┘  └──────────────┘  └──────────────┘                      │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                      │
│   │ 📱 Téléphone │  │ 🔒 Parents   │  │  ⋯ Plus      │                      │
│   │ 2 de confiance│ │ Actif · Léa  │  │ Réglages     │                      │
│   └──────────────┘  └──────────────┘  └──────────────┘                      │
│  ■ Téléphone  ■ Apprendre  ■ Jeux  ■ Regarder                               │
└────────────────────────────────────────────────────────────────────────────┘
tuiles 340 × 150 dp, étiquette 27 sp (TITLE), état 19 sp ; 7 nœuds focusables + puces
```

### 7.4 TV — « Plus »

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Plus                                                    ESSAI · 11 h restantes │
│  VERSION COMPLÈTE        CONNEXIONS                 APPAREIL                 │
│  › Passer en production  › Téléphone                › Mises à jour           │
│  STOCKAGE                › Wi-Fi Direct      (◯)    › Boutique               │
│  › Clé USB               › Administration           › Démarrer avec la TV (●)│
│  › Où ranger les fichiers                           › Lecture à distance  (●)│
│  › Téléchargements       À PROPOS                                            │
│                          › Assistance à distance  › Confidentialité          │
│                          › Options développeur    › Aide                     │
└────────────────────────────────────────────────────────────────────────────┘
≤ 12 lignes, 3 colonnes, D-pad : GAUCHE/DROITE change de colonne, HAUT/BAS dans la colonne
```

### 7.5 Entrée boutique (contextuelle, téléphone et TV)

```
Téléphone : Apprendre › classe « 3e » (pas sur le téléphone)      TV : Apprendre › « Terminale » (cadenas)
┌──────────────────────────────┐                                   ┌──────────────────────────────┐
│ 3e · Maths                   │                                   │ Terminale                    │
│ Pas encore sur ce téléphone  │                                   │ Version complète ou location │
│ [ Obtenir ]  (→ Boutique)    │                                   │ [ Obtenir sur le téléphone ] │
└──────────────────────────────┘                                   └──────────────────────────────┘
```

### 7.6 Réglages du téléphone

```
┌──────────────────────────────┐
│ ← Réglages                   │
│ Apparence            Sombre ▸│
│ Confidentialité              ▸│
│ Mises à jour         à jour ▸│
│ Avancé                       ▸│
│ Version 1.2.30 · identifiant ab12 │
└──────────────────────────────┘
```

---

## 8. Tables de libellés (copie UX, français)

### 8.1 Téléphone

| Élément | Libellé | Budget | Ancien |
|---|---|---|---|
| Destination 1 | Accueil | 1 mot | CastBridge TV |
| Destination 2 | Fichiers | 1 | Sur le téléphone |
| Destination 3 | Apprendre | 1 | Apprendre |
| Destination 4 | Jeux | 1 | Jeux |
| Destination 5 | Parents | 1 | Parental |
| Action du haut | Plus | 1 | Activer la TV · Locations · 🔒 · ⚙ |
| Titre d'accueil | Que voulez-vous faire ? | 4 | — |
| Suggestions | Reprendre « titre » · Envoyer une vidéo · Regarder sur la TV · Télécommande · Une leçon · Un jeu · Ajouter ma TV · Passer en production | ≤ 3 mots + titre | tuiles à 2 lignes |
| Puce TV | ● Salon · connectée / ● Salon · reconnexion… / ○ Aucune TV | ≤ 3 mots après le nom | titre + détail + indice + 3 boutons |
| Carte d'envoi | Envoi : 42 % · 2 en attente | 5 | 2 cartes, 44 sp |
| Rangée minimale W6 | Reliez une TV en production pour tout débloquer | 8 | — |
| Bandeau enfant | TV en mode enfant (Léa) | 5 | — |
| Plus › groupes | Ma TV · Boutique et clés · Données · Réglages | ≤ 3 | — |

### 8.2 TV

| Élément | Libellé | Budget | Ancien |
|---|---|---|---|
| Tuiles | Regarder · Apprendre · Jeux · Téléphone · Parents · Plus | 1 mot | 18 étiquettes de 1 à 3 mots |
| États de tuile | 124 vidéos · CM2 · Jean · 3 jeux · 2 de confiance · Code 12•••• · Actif · Léa · Réglages | ≤ 3 | descriptions 10-20 mots |
| Badge | ESSAI · 11 h restantes / PRODUCTION · jusqu'au 31/12 / SUPER ILLIMITÉ / SANS CLÉ | ≤ 4 mots après le titre | jusqu'à 35 mots |
| Page Téléphone | Code de la TV · 123456 (54 sp) · Ajouter un téléphone · Rendre visible 2 min · Téléphones de confiance (2) · Internet : Wi-Fi ok | ≤ 4 par ligne | dialogue d'aide + 3 tuiles + 19 lignes |
| Plus › lignes | voir § 4.2 | ≤ 4 | 22-24 lignes de 6-12 mots |
| Aide des couleurs | ■ Téléphone ■ Apprendre ■ Jeux ■ Regarder | 1 mot chacune | « OK : lire · MENU (ou OK maintenu) : actions · RETOUR : accueil » |
| Ligne essai dans Regarder | Vidéos stockées : version complète | 4 | message `TrialPolicy.MESSAGE` (23 mots) |

---

## 9. Plan de migration (code minimal)

### 9.1 Gains rapides (≤ 1 jour chacun, livrables en premier, sans nouveau composant)

| # | Gain | Fichiers | Exécuteur |
|---|---|---|---|
| Q1 | Onglet de départ = « CastBridge TV » (`tab = 1`) et mémoire du dernier onglet | `MainActivity.kt:83` | haiku |
| Q2 | Badge TV court (`Badge.short`), 16 sp, haut gauche | `KeyBadge.kt`, `KeyBadgeOverlay.kt:30-31,43` | haiku (+ test core) |
| Q3 | Tuiles TV : étiquette 19 sp sur 1 ligne, état ≤ 3 mots (troncature `take(3 mots)`), description retirée du héros (reste pour TalkBack) | `TvCards.kt:261-285`, `HomeScreen.kt:209` | haiku |
| Q4 | Suppression des doublons tuile/menu : retirer de `menuItems()` ce qu'une tuile porte déjà (Bibliothèque, Téléchargements, Ajouter un téléphone, Wi-Fi Direct, USB×2, Internet, Mises à jour, Options développeur, SSH) ⇒ menu ≤ 12 lignes ; fusion « Recevoir du téléphone » + « Aide » ; id `langues` ajouté à `TV_FEATURES` | `PlayerActivity.kt:704-748`, `:565`, `:606`, `Telemetry.kt:16` | sonnet |
| Q5 | Tri de la rangée d'outils par usage (Regarder, Apprendre, Jeux, Téléphone, Parents, puis le reste) et rangée « Reprendre » **au-dessus** des outils (focus initial = reprendre) | `PlayerActivity.kt:543`, `HomeScreen.kt:151-152` | haiku |
| Q6 | Téléphone : retirer « Locations » et « Activer la TV » de la barre du haut vers un menu ⋮ (Compose `DropdownMenu`), et le cadenas (l'onglet Parental existe) | `MainActivity.kt:106-111` | haiku |
| Q7 | Filtrage essai/enfant par **id** et non par étiquette (corrige « Jeux » absent en mode enfant) | `ParentalHub.kt:271-280`, `ParentalModel.kt:227-231`, `TvCards.kt:258` (`HomeTool.id`) | sonnet |

### 9.2 Nouveaux composants (vague principale)

| Composant | Module | Rôle | Réutilise |
|---|---|---|---|
| `core/nav/NavModel.kt` | core | destinations, lignes de Plus, visibilité par `NavState` (production/essai/minimal/enfant/super), budgets de mots vérifiés par test | `TrialPolicy`, `ParentalRules`, `PhoneGate` (w6-01) |
| `core/nav/QuickActions.kt` | core | `suggest(ctx: QuickContext): List<QuickAction>` (3-4, ordre déterministe) | `LibrarySections.RESUME`, `LinkState` |
| `core/nav/TvFocusGraph.kt` | core | nœuds/arêtes de l'accueil TV, plus court chemin, coût moyen | — |
| `sender/Shell.kt` (`BottomNav`, `PlusSheet`, `TvChip`) | sender | remplace `Root()` et la barre du haut | `TvLinkStatus` (réduit), `CastMiniBar` |
| `sender/HomeScreen.kt` (téléphone) | sender | « Que voulez-vous faire ? » + Reprendre + carte d'envoi | `TvHome.kt` (découpé : `pick`, `cmd`, `Poster` gardés) |
| `receiver/HomeGrid.kt` | receiver | grille 3×2 à partir de `NavModel`, focus par `TvFocusGraph`, mémoire | `IconTile` (agrandie), `TvStyle.focusZoom` |
| `receiver/PlusPanel.kt` | receiver | 3 colonnes de lignes depuis `NavModel` | `SettingsPanel` (supprimé ensuite) |
| `receiver/PhonePageActivity.kt` | receiver | page « Téléphone » (code, ajouter, visible, confiance, Internet) | `PairActivity` (réutilisée pour l'ajout), `internetMenu()` |

### 9.3 Fusions / suppressions

| Aujourd'hui | Devient |
|---|---|
| `TvHome.kt` (456 lignes) | `HomeScreen.kt` téléphone (≈ 200 lignes) + `SeriesCard` déplacée dans `TvLibraryDialog` |
| `TvHub.kt` (`TvHubAdvanced`) | inchangé, ouvert par Plus › « Connexion avancée » |
| `ScrollableTabRow` 6 onglets | `NavigationBar` 5 destinations |
| `SettingsPanel` TV + `menuItems()` | `PlusPanel` + page « Téléphone » + page « À propos » |
| tuiles « Ajouter un téléphone », « Recevoir du téléphone », « Bluetooth », « Internet », « Aide » | tuile « Téléphone » |
| tuiles « Clé USB », « Téléchargements », « Wi-Fi Direct », « Administration », « Mises à jour », « Options développeur », « Connexion & réglages » | lignes de Plus |
| tuile « Langues » | ligne du hub Apprendre (TV) / sous-onglet (téléphone) |
| 3 boutons « Données hors ligne » | 1 ligne de Plus |

### 9.4 Interrupteurs de fonctionnalité

- `castbridge.nav.v2` : propriété Gradle `-PnavV2=true` → `BuildConfig.NAV_V2` (défaut **false** jusqu'à validation sur la TV de référence) ; en `false`, les gains rapides seuls s'appliquent. Les deux rendus coexistent le temps d'une version (`HomeScreen` ↔ `HomeGrid`), choisis dans `PlayerActivity.onBound` et `MainActivity.Root`.
- Pas de nouveau drapeau serveur ; pas d'ordre à distance.

### 9.5 Risques et parades

| Risque | Parade |
|---|---|
| Focus D-pad perdu ou piégé sur la vraie TV (GaiaOS 32 bits, 720p) | graphe testé en JVM ; `nextFocusXId` explicites sur chaque tuile (pas de recherche de focus automatique) ; campagne sur la TV de référence avant `NAV_V2=true` |
| Lecture d'une vidéo pendant que le focus est sur « Plus » | `PlusPanel.hide()` dans `hideScreens()` comme `settingsPanel` |
| Ids de télémétrie (liste close `EventCatalog`) | ajouter `langues`, `plus`, `phone_page`, `quick_action` ; test `TelemetryCatalogTest` mis à jour |
| Conflit avec w6-16 (« ne pas masquer d'onglet ») | les 5 destinations restent visibles ; cadenas + mur, jamais de disparition |
| Conflit avec w5-15/16 (écran boutique TV comme tuile) | la boutique est une ligne de Plus et une entrée contextuelle, pas une tuile (décision D-W11-2) |
| Régression du mode enfant | test `NavModelTest.kidHome` sur ids |
| Conflits de fichiers avec W7/W8/W10 (w7-19 : `S/MainActivity.kt`, `S/TvPairScreen.kt`, `S/TvHome.kt` ; w10-09 : `R/PlayerActivity.kt`, `R/HomeScreen.kt` ; w10-11 : `S/MainActivity.kt` ; w8-16/17 : `S/TvTransferScreen.kt`, `S/ConnectScreens.kt`) | ordre de fusion fixé dans l'index W11 (w7-19 → 11c ; w10-09 → 11d ou 11d → w10-09 avec relecture) ; w10-09 et w10-11 appliquent § 6.6 (rangée, pas tuile ; ligne de Plus, pas d'entrée dans `MainActivity`) |
| Mémoire : `HomeGrid` + `PlusPanel` + grille vidéo en même temps | un seul conteneur visible à la fois, `RecyclerView` conservé pour « Reprendre » |

### 9.6 Effort

| Lot | Cahiers | Effort (agent·jours) |
|---|---|---|
| Gains rapides | w11-01 … w11-04 | ≈ 3 |
| Cœur `nav` + tests | w11-05, w11-06 | ≈ 3,5 |
| Téléphone | w11-07, w11-08, w11-09 | ≈ 6 |
| TV | w11-10, w11-11, w11-12 | ≈ 7 |
| Mesure, docs, campagne | w11-13, w11-14 | ≈ 2 |
| **Total** | 14 cahiers (`docs/agent-briefs/SONNET-WAVE11-INDEX.md`) | **≈ 21,5** |

---

## 10. Mesure sans télémétrie

### 10.1 Test JVM du graphe de focus (`core`)

`TvFocusGraphTest` : pour chaque `NavState` (production, essai, enfant), construire le graphe de l'accueil, calculer par Dijkstra le coût depuis le focus initial jusqu'à chaque destination (OK compris) ; assertions : moyenne ≤ 3,0 (sans Reprendre) et ≤ 4,0 (avec), maximum ≤ 5, aucune destination inatteignable, chaque arête symétrique (DROITE puis GAUCHE revient), aucun nœud hors écran (coordonnées 0..1280 × 0..720). `NavModelTest` : ≤ 6 tuiles, ≤ 12 lignes de Plus, budgets de mots (étiquette ≤ 2, état ≤ 3, ligne ≤ 4), aucune étiquette dupliquée, un état ne crée jamais de tuile.

### 10.2 Script « touches jusqu'à la fonction » (émulateur, sans télémétrie)

`tools/ux/taps.sh` : pour une liste de 14 tâches (envoyer une vidéo, télécommande, leçon, langue, quiz, boutique, activer, données hors ligne, parental, réglages, diagnostic, téléchargements TV, bibliothèque TV, contenus libres) le script lance l'app sur l'émulateur (`adb shell am start`), rejoue une séquence `adb shell input tap` / `keyevent` décrite dans `tools/ux/tasks.tsv` et vérifie l'activité ou le texte attendu (`uiautomator dump`) ; il imprime le nombre de touches par tâche et échoue si une tâche dépasse **3** (téléphone) ou **5 appuis** (TV). Il sert de liste de contrôle manuelle quand l'émulateur manque (colonne « observé »).

### 10.3 Protocole avec 5 usagers réels

- Recrutement : 5 personnes dont ≥ 1 peu lettrée, ≥ 1 de plus de 60 ans, ≥ 1 n'ayant jamais utilisé l'app ; TV de référence + leur propre téléphone si possible.
- 8 tâches, formulées sans le vocabulaire de l'app (« Mettez cette vidéo du téléphone sur la TV », « Reprenez le film d'hier », « Ouvrez une leçon pour un enfant de CM2 », « Trouvez combien de temps l'enfant a regardé la TV cette semaine », « Faites jouer un jeu », « Donnez le code de la TV à un ami », « Mettez la TV à jour », « Trouvez où acheter la classe de 3e »).
- Mesures : réussite au premier essai (sans aide), touches/appuis, temps, hésitations verbalisées, erreurs de retour (Retour inattendu).
- Seuils : **≥ 90 % de réussite au premier essai** sur les 5 tâches principales, **≤ 3 touches** (téléphone) / **≤ 5 appuis** (TV) pour ces tâches, aucun abandon ; avant/après sur la même grille (version actuelle d'abord, à une semaine d'écart).
- Compte rendu : `docs/agent-reports/ux-w11-usagers.md` (sans nom, sans vidéo).

### 10.4 Avant / après attendu

| Mesure | Avant | Après (cible) |
|---|---|---|
| Destinations permanentes téléphone | 11 | 6 (5 + Plus) |
| Cibles sur l'accueil téléphone | 34 | ≤ 12 |
| Mots sur l'accueil téléphone | ≈ 190 | ≤ 40 |
| Tuiles accueil TV | 18 | 6 |
| Appuis moyens vers une tuile TV | 9,5 | ≤ 3 |
| Lignes du menu TV | 22-24 | ≤ 12 |
| Mots du badge TV | ≤ 35 | ≤ 5 |
| Éléments d'état simultanés TV | 6-12 | ≤ 5 |
| Profondeur max hors contenu | 4 | 3 |

---

## 11. Accessibilité

- **TalkBack (téléphone)** : chaque destination a un `contentDescription` = libellé ; les suggestions annoncent « bouton, Envoyer une vidéo » ; la puce TV annonce l'état complet (`LinkView.detail`) ; ordre de lecture = ordre visuel ; cibles ≥ 48 dp ; la barre du bas est un `NavigationBar` Material (rôles corrects).
- **Grand texte** : la barre du bas garde les libellés sur 1 ligne jusqu'à 1,3× (sinon icônes seules + libellé de la destination active) ; les suggestions passent à 1 par rangée à partir de 1,3×.
- **Daltonisme** : aucune information portée par la couleur seule (déjà la règle des puces TV, `StatusBarView.kt:27`) ; les 4 raccourcis de couleur TV portent un mot ; le point de la puce TV est doublé d'un mot (« connectée »).
- **Peu lettrés** : icône + 1 mot partout ; les 4 suggestions sont des verbes ; le code de la TV est en 54 sp monospace (déjà `ActivationActivity`) ; l'aide est un dialogue en 3 étapes numérotées (existant, `PlayerActivity.kt:448-454`) ; audio : la TV peut lire une étiquette au focus long via `TextToSpeech` **si** la voix existe (optionnel, pas de dépendance).
- **Langues locales** : tous les libellés passent par `NavModel` (chaînes centralisées, `core`) : traduire = fournir une table ; les budgets de mots sont testés par langue ; pas de texte dans les icônes.
- **TV** : 19 sp minimum sur les étiquettes, contraste ≥ 4,5:1, anneau de focus 3 dp, aucune cible hors écran, `importantForAccessibility` sur le badge (lecture seule, non focusable, inchangé).

---

## 12. Décisions pour le propriétaire (recommandation en gras)

| Id | Question | Options | Recommandation |
|---|---|---|---|
| D-W11-1 | Nombre et nom des 5 destinations du téléphone | (a) Accueil · Fichiers · Apprendre · Jeux · Parents ; (b) remplacer Jeux par Boutique ; (c) 4 destinations (Jeux dans Apprendre) | **(a)** : Jeux est une fonction d'appel ; la boutique est contextuelle |
| D-W11-2 | Boutique sur la TV : tuile ou ligne de Plus + entrée contextuelle | (a) tuile (comme w5-15 le laissait entendre) ; (b) **ligne de Plus + « Obtenir » sur le contenu** | **(b)** : garde 6 tuiles ; w5-15/16 restent valides (l'écran existe, seule l'entrée change) |
| D-W11-3 | Badge court permanent | (a) titre seul (« ESSAI ») ; (b) **titre + 1 complément ≤ 4 mots** ; (c) badge complet (statu quo) | **(b)** |
| D-W11-4 | Touches de couleur | (a) **4 raccourcis (rouge Téléphone, vert Apprendre, jaune Jeux, bleu Regarder)** ; (b) bleu seul (statu quo) | **(a)**, avec le rappel à 4 pastilles |
| D-W11-5 | Mode enfant sur l'accueil TV | (a) **Téléphone et Plus masqués** ; (b) tout visible, cadenassé | **(a)** : moins d'éléments pour l'enfant ; Parents reste la porte |
| D-W11-6 | Interrupteur `NAV_V2` | (a) **éteint par défaut jusqu'à validation sur la TV de référence**, puis allumé dans une version TV + téléphone publiées ensemble ; (b) allumé dès la fusion | **(a)** |
| D-W11-7 | Test usagers | (a) **5 personnes, 2 sessions (avant/après)** ; (b) après seulement | **(a)** ; coût : 2 demi-journées |
| D-W11-8 | « TV DLNA » (autres TV) | (a) **ligne de Plus › Connexion avancée** ; (b) suggestion de l'Accueil quand aucune CastBridge-TV n'est trouvée | **(a)** + (b) si aucune TV CastBridge n'a jamais été ajoutée |
| D-W11-9 | Œuvres locales (W10) sur la TV | (a) tuile « Œuvres » (w10-09 tel quel, 7 tuiles) ; (b) **rangée de contenu « Œuvres locales » sous « Reprendre » + entrée dans « Regarder »** | **(b)** : garde 6 tuiles ; `WorksActivity` inchangée |
| D-W11-10 | Ordre de fusion avec W7 (téléphone) | (a) **w7-19 avant 11c** (W11 reprend `RepairScreen`, `LinkTexts`) ; (b) 11c avant W7 (w7-19 relit `Shell.kt`) | **(a)** si W7 part dans le mois ; sinon (b) |

Aucun BLOQUÉ : rien dans cette conception ne dépend d'un fait externe non vérifié.

---

## 13. Ce qui n'a pas pu être vérifié

- Le rendu réel (aucune capture, aucun gradle, aucune TV) : largeurs de tuiles, nombre de tuiles visibles (calcul à partir de 170 dp + marges, `HomeScreen.kt:184,197`).
- Le comportement du mode enfant sur « Jeux » (déduit de `KID_HOME` par étiquettes, `ParentalModel.kt:227`).
- Les conceptions W7, W8 et W10 et leurs cahiers sont **non suivis par git** au moment de la lecture (créés le même jour par d'autres sessions) : lus tels quels (§ 6.4-6.6) ; s'ils changent avant leur commit, l'index W11 (ordre de fusion, fichiers partagés) est à relire. Aucune conception W9 n'existe (seulement des cahiers de production de contenus w9-*).
- Les cahiers W5/W6/W7/W8/W10 non fusionnés : la conception suppose leurs noms de fichiers tels qu'annoncés dans leurs index.
- `docs/agent-briefs/routing.json` (non suivi, autre session) n'a pas été modifié (lecture seule sur l'existant) : les 14 entrées W11 sont dans `docs/agent-briefs/routing-w11.json`, à fusionner par le coordinateur.
