# Vague 11 pour agents Sonnet/Haiku — index (2026-10-02) : navigation allégée de CastBridge (téléphone) et CastBridge-TV

Source : `docs/coordination/DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` (lire § 0, § 2 et le § cité par chaque cahier avant de commencer). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, textes en français, dire « CastBridge » (téléphone) / « CastBridge-TV »). Ce fichier ne modifie pas les index des vagues 1-6 ; le § « Changements aux cahiers w5/w6 » liste ce que la vague 11 amende sans les éditer.

**Demande du propriétaire qui fonde la vague (2026-10-02)** : « l'ergonomie de navigation des fonctionnalités est trop lourde à l'écran, améliore cela ».

**Modèle d'exécution recommandé par cahier** (règle de `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` § 4.7 : Haiku = forme mécanique avant/après seulement) : `haiku` = w11-01, w11-02, w11-03 (éditions avant/après fournies dans le cahier) ; `sonnet` = tout le reste (focus D-pad, états, modèle testé, rédaction). Chaque cahier porte l'en-tête de routage (`<!-- routage Fable 2026-10-02 -->`) ; les entrées `routing.json` sont dans **`docs/agent-briefs/routing-w11.json`** (à fusionner par le coordinateur : `routing.json` est un fichier d'une autre session, non modifié ici). Efforts en agent·jours (S ≈ 0,5-1, M ≈ 1,5-2, L ≈ 3-3,5) ; coût API estimé de la vague ≈ 11 $ (jauges de `routing-w11.json`, non vérifiées).

**Interrupteur** : tout le nouveau rendu est derrière `BuildConfig.NAV_V2` (Gradle `-PnavV2=true`, défaut `false`, téléphone et TV). Les gains rapides (11a) s'appliquent **sans** interrupteur. Décision D-W11-6 : `NAV_V2` reste éteint jusqu'à la validation sur la TV de référence (GaiaOS 32 bits, 720p), puis s'allume dans une version TV + téléphone publiées ensemble.

**Prérequis fusionnés** : aucun (la vague tient seule). **Souhaités** : w6-01/02 (`PhoneGate`, `PhoneGateTexts` : sinon `NavState.phoneMinimal = false` et textes de repli), w6-16 (`GateWall`, `ProofStore` : sinon dialogue de repli), w5-11/w5-15 (`ShopScreen`/`ShopActivity` : sinon la ligne « Boutique » est absente), w4-07 (`TvAccess.degraded` : sinon `Edition.REDUCED` jamais produit), w2-04 (badge lisible : w11-02 le remplace de toute façon).

## Cinq sous-vagues ; fichiers disjoints à l'intérieur d'une sous-vague (11d séquentielle)

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **11a — gains rapides** (livrables seuls, sans interrupteur) | onglet de départ + barre du haut (téléphone) ; badge court ; tuiles lisibles ; doublons et ids (TV) | w11-01, w11-02, w11-03 en parallèle ; w11-04 après w11-03 | ≈ 3 j |
| **11b — cœur** (JVM, testé) | `castbridge.core.nav` : modèle, textes, suggestions ; graphe de focus | w11-05, w11-06 en parallèle | ≈ 3,5 j |
| **11c — téléphone** | coquille (barre du bas, Plus, puce) ; accueil « Que voulez-vous faire ? » ; réglages et entrées | w11-07 d'abord (contrat), puis w11-08 et w11-09 en parallèle | ≈ 6 j |
| **11d — TV** | grille 3 × 2 + focus + couleurs ; `PlusPanel` + À propos ; page Téléphone | w11-10 → w11-11 → w11-12 (séquentiel : tous touchent `PlayerActivity.kt`) | ≈ 7 j |
| **11e — mesure et docs** | script de touches, protocole usagers, campagne ; docs et ids de télémétrie | w11-13 (dès 11a) ; w11-14 (ids dès 11b, docs après 11d) | ≈ 2 j |

Total ≈ **21,5 agent·jours**, 14 cahiers.

## Les 14 cahiers

| id | Cahier | Objet | Effort | Modèle | Gain rapide | Statut | Dépend de |
|---|---|---|---|---|---|---|---|
| w11-01 | `sonnet-w11-01-phone-start-tab-topbar.md` | Onglet de départ = « CastBridge TV », mémoire du dernier onglet, barre du haut = logo + ⋮ (4 anciennes actions en menu) | S | haiku | **oui** | PRÊT | — |
| w11-02 | `sonnet-w11-02-tv-badge-short.md` | `Badge.short` (≤ 5 mots) + test, badge 16 sp en haut à gauche, 12 sp/50 % en vidéo | S | haiku | **oui** | PRÊT | — |
| w11-03 | `sonnet-w11-03-tv-tiles-typography.md` | Tuiles : étiquette 19 sp sur 1 ligne, état ≤ 3 mots (`TileText`), description hors héros, `HomeTool.id` | S | haiku | **oui** | PRÊT | — |
| w11-04 | `sonnet-w11-04-tv-dedupe-ids.md` | Doublons tuile/menu supprimés (menu ≤ 9), fusion Recevoir/Aide et Bluetooth/Ajouter, ordre par usage, filtres essai/enfant par id (corrige « Jeux » en mode enfant), id `langues` | M | sonnet | **oui** | PRÊT | w11-03 |
| w11-05 | `sonnet-w11-05-nav-model-core.md` | `NavState`, `PhoneNav`, `TvNav`, `NavTexts` (budgets de mots), `QuickActions.suggest` + tests (≥ 17 cas) | M | sonnet | non | PRÊT | — |
| w11-06 | `sonnet-w11-06-tv-focus-graph.md` | `TvFocusGraph.home(...)`, coûts BFS, test « ≤ 3 appuis en moyenne », liste des voisins pour `nextFocus*` | M | sonnet | non | PRÊT | — |
| w11-07 | `sonnet-w11-07-phone-shell-bottomnav-plus.md` | `AppShell` : `NavigationBar` 5 destinations, `PlusSheet`, `TvChip` + feuille Connexion (« Dépannage »), cadenas W6, `NAV_V2` téléphone | L | sonnet | non | PRÊT | w11-01, w11-05 |
| w11-08 | `sonnet-w11-08-phone-home-quick-actions.md` | `PhoneHome` : suggestions, Reprendre, carte d'envoi unique ; `TvHome.kt` découpé ; carte séries dans la bibliothèque | M | sonnet | non | PRÊT | w11-05 ; contrat w11-07 |
| w11-09 | `sonnet-w11-09-phone-settings-entries.md` | Réglages en 4 lignes + « Mode : … », entrées « Données hors ligne » retirées de Jeux/Apprendre/Réglages, Apprendre › Langues | S | sonnet | non | PRÊT | w11-07 ; jamais en parallèle de w8-17 |
| w11-10 | `sonnet-w11-10-tv-home-grid.md` | `HomeGrid` 3 × 2, `nextFocus*` depuis le graphe, mémoire `home_focus`, touches de couleur + rappel, veille, ≤ 3 puces, `NAV_V2` TV | L | sonnet | non | PRÊT | 11a, w11-05, w11-06 |
| w11-11 | `sonnet-w11-11-tv-plus-panel.md` | `PlusPanel` ≤ 12 lignes en 3 colonnes, bascules, `AboutPage`, MENU hors vidéo | M | sonnet | non | PRÊT | w11-10 |
| w11-12 | `sonnet-w11-12-tv-phone-page.md` | `PhonePageActivity` : code 54 sp, ajouter, visible, confiance, Internet, aide repliée | M | sonnet | non | PRÊT | w11-11 (ordre de fusion) |
| w11-13 | `sonnet-w11-13-ux-measure-protocol.md` | `tools/ux/taps.sh` + `tasks.tsv`, `docs/UX-MESURES.md`, `docs/UX-TEST-PROTOCOLE.md`, campagne § W11 | S | sonnet | non | PRÊT | — (coordonnées `NAV_V2` après 11c/11d) |
| w11-14 | `sonnet-w11-14-docs-telemetry-ids.md` | `docs/NAVIGATION.md`, docs mises à jour, HANDOFF, ids `plus`/`phone_page`/`quick_action`/`shop`/`tokens_spend` | S | sonnet | non | PRÊT | ids : w11-04 ; docs : 11c, 11d |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `RR/` = `android/receiver/src/main/res/`, `D/` = `docs/`.

### 11a
| id | Fichiers possédés |
|---|---|
| w11-01 | `S/MainActivity.kt` (fonction `Root()` seulement) |
| w11-02 | `C/owner/KeyBadge.kt`, `CT/owner/KeyBadgeTest.kt`, `R/KeyBadgeOverlay.kt` |
| w11-03 | `R/TvCards.kt` (`HomeTool`, `IconTile`, nouveau `TileText`), `R/HomeScreen.kt` (`fillTools` seulement) |
| w11-04 (après w11-03) | `R/PlayerActivity.kt` (`homeTools`, `menuItems`, `tile`), `R/ParentalHub.kt` (`filterHome`), `C/parental/ParentalModel.kt` (`KID_HOME_IDS`, `kidHome`), `C/telemetry/Telemetry.kt` (`langues`), nouveau `CT/parental/KidHomeIdsTest.kt`, test existant citant `KID_HOME`/`TV_FEATURES` s'il y en a un |

### 11b
| id | Fichiers possédés |
|---|---|
| w11-05 | nouveaux `C/nav/{NavModel,NavTexts,QuickActions}.kt`, `CT/nav/{NavModelTest,QuickActionsTest}.kt` |
| w11-06 | nouveaux `C/nav/TvFocusGraph.kt`, `CT/nav/TvFocusGraphTest.kt` |

### 11c
| id | Fichiers possédés |
|---|---|
| w11-07 | nouveaux `S/nav/Shell.kt`, `S/nav/NavStateProvider.kt` ; `S/MainActivity.kt`, `android/sender/build.gradle.kts`, `S/TvPairScreen.kt` (extraction `TvChipLine`/`TvLinkActions`) |
| w11-08 | nouveau `S/nav/PhoneHome.kt` ; `S/TvHome.kt` (extractions `internal`), `S/TvLibraryScreen.kt` (`SeriesCard` en tête) |
| w11-09 | `S/ConnectScreens.kt`, `S/GamesScreen.kt`, `S/LearnScreen.kt`, `S/LotsScreen.kt` (`LanguagesSection` internal) |

### 11d (séquentiel : w11-10 → w11-11 → w11-12)
| id | Fichiers possédés |
|---|---|
| w11-10 | nouveaux `R/HomeGrid.kt`, `R/NavStateTv.kt` ; `R/HomeScreen.kt`, `android/receiver/build.gradle.kts`, `R/PlayerActivity.kt` (`homeApi` : `navTiles/openTile/tileActions` ; `onKeyDown` couleurs ; `setPlaying`), `R/StatusBarView.kt` (`maxChips`) |
| w11-11 | nouveaux `R/PlusPanel.kt`, `R/AboutPage.kt` ; `R/PlayerActivity.kt` (`showPlus`, `plusActions`, `showMenu` hors vidéo, BACK), `RR/layout/activity_player.xml` (`@+id/plus`) |
| w11-12 | nouveau `R/PhonePageActivity.kt` ; `android/receiver/src/main/AndroidManifest.xml`, `R/PlayerActivity.kt` (branches `"phone"` d'`openTile`/`plusActions` seulement) |

### 11e
| id | Fichiers possédés |
|---|---|
| w11-13 | nouveaux `tools/ux/{taps.sh,tasks.tsv,tasks-legacy.tsv,README.md}`, `D/UX-MESURES.md`, `D/UX-TEST-PROTOCOLE.md` ; `D/TEST-CAMPAIGN.md` (§ W11) |
| w11-14 | nouveau `D/NAVIGATION.md` ; `D/{ADMIN,REMOTE,PARENTAL,GAMES,LANGUES,TRIAL-EDITION,TELEMETRY,HANDOFF}.md` ; `C/telemetry/Telemetry.kt` (ids) et son test |

Chevauchements entre sous-vagues (acceptés car séquentiels) : `R/PlayerActivity.kt` (w11-04 → w11-10 → w11-11 → w11-12), `R/HomeScreen.kt` (w11-03 → w11-10), `S/MainActivity.kt` (w11-01 → w11-07), `C/telemetry/Telemetry.kt` (w11-04 → w11-14).

## Graphe de dépendances

```
w11-01 ─┐                      w11-05 ─┬─> w11-07 ─┬─> w11-08
w11-02  │ (11a, parallèles)            │           └─> w11-09
w11-03 ─┼─> w11-04                     └─┐
        │                      w11-06 ───┴─> w11-10 ─> w11-11 ─> w11-12
        └──────────────────────────────────┘
w11-13 (indépendant ; coordonnées NAV_V2 après 11c/11d)      w11-14 (ids après w11-04 ; docs après 11d)
```

Dépendances externes (souhaitées, non bloquantes) : w6-01/02/16 (porte et mur du téléphone), w5-11/15 (boutique), w4-07 (mode réduit).

## Ordre de lancement conseillé

1. **Jour 0** : w11-01, w11-02, w11-03, w11-05, w11-06, w11-13 en parallèle (6 agents, dont 4 haiku).
2. Après w11-03 : w11-04. Après w11-01 + w11-05 : w11-07.
3. Après w11-07 : w11-08 et w11-09 en parallèle. Après 11a + w11-05 + w11-06 : w11-10.
4. Après w11-10 : w11-11 ; après w11-11 : w11-12.
5. Après 11c + 11d : w11-14 (docs) ; w11-13 complète `tasks.tsv` avec les coordonnées `NAV_V2`.
6. **Validation sur la TV de référence** (campagne § W11 de w11-13, parcours de focus de w11-10/11/12) puis protocole usagers (w11-13), puis D-W11-6 : allumer `NAV_V2`.

Les gains rapides (11a) peuvent être publiés dès leur fusion, dans une version TV et une version téléphone, sans attendre le reste.

## Vagues W7, W8, W10 (conceptions et cahiers non suivis par git au 2026-10-02) : fichiers partagés et ordre

| Cahier d'une autre vague | Fichiers partagés avec W11 | Règle |
|---|---|---|
| w7-19 (première liaison, « Réparer la connexion ») | `S/MainActivity.kt`, `S/TvPairScreen.kt`, `S/TvHome.kt` | **w7-19 avant 11c** (D-W11-10) : w11-07 ouvre `S/link/RepairScreen.kt` et prend `LinkTexts` ; w11-01 (onglet de départ) est un sur-ensemble de D-W7-7 et peut passer avant w7-19 (w7-19 relit alors `last_tab`) |
| w7-15 (`LinkDiagActivity`) | aucun fichier commun ; w11-11 (`link`) et w11-12 (« Diagnostic ») l'ouvrent si la classe existe | aucun ordre imposé |
| w8-16 / w8-17 (voies du transfert, Wi-Fi Direct) | `S/TvTransferScreen.kt`, `S/ConnectScreens.kt` | w11-09 jamais en parallèle de w8-17 ; fusion entre les deux |
| w10-09 (tuile « Œuvres », `R/PlayerActivity.kt`, `R/HomeScreen.kt`) | `R/PlayerActivity.kt`, `R/HomeScreen.kt` | D-W11-9 : **rangée « Œuvres locales », pas de tuile** ; si w10-09 est fusionné avant 11d, w11-10 retire la tuile et crée la rangée ; sinon w10-09 relit w11-10 et crée la rangée directement |
| w10-11 (boutique téléphone, « entrée sans W5 » dans `S/MainActivity.kt`) | `S/MainActivity.kt` | l'entrée sans W5 est la ligne `shop` de `PhoneNav.plus` (w11-05/07) : w10-11 **ne touche pas** `MainActivity.kt` si 11c est fusionné |
| w5-11 / w5-15 (boutique) | voir ci-dessous | — |

## Changements aux cahiers w5/w6 (sans les éditer : à lire comme un amendement)

- **w5-11** (boutique téléphone) : l'entrée n'est plus « remplace « Locations » dans la barre du haut » mais `Plus › Boutique et clés › Boutique` et un bouton « Obtenir » sur un contenu absent/fermé ; `RentalDeliveryActivity` devient `Plus › Locations sur la TV` (D-W11-2).
- **w5-15/16** (boutique TV) : `ShopActivity` reste ; son entrée est `Plus › Appareil › Boutique` et « Obtenir sur le téléphone » dans Apprendre, **pas** une tuile d'accueil. Ses touches de couleur internes sont inchangées (l'accueil a les siennes : rouge Téléphone, vert Apprendre, jaune Jeux, bleu Regarder).
- **w5-17** (jetons) : le solde apparaît comme état de la tuile Jeux (« 3 jeux · 23 jetons ») seulement si > 0 ; pas d'entrée dédiée.
- **w6-14** (indicateur de consentement TV) : la ligne « Rapports d'usage partagés avec n téléphones » ne va pas dans le badge court (w11-02) ; elle devient une icône sur la tuile Parents + une ligne de `Plus › À propos` ; la page « Téléphones des parents » s'ouvre depuis Parents.
- **w6-16** (porte du téléphone) : « ne pas masquer d'onglet » devient « ne pas masquer de destination ni de ligne de Plus » ; la puce « TV cible » **est** `TvChip` ; `GateWall` inchangé.
- **w6-18** (onglet Parental v2) : les 8 sections restent ; la destination « Parents » en montre 4 au premier niveau (Aujourd'hui/Semaine, Apprendre et Quiz, Parents de cette TV, Plus) ; w6-18 n'a rien à changer, w11-07 rend `ParentalTab()` tel quel (le regroupement est une évolution ultérieure de w6-18 si le propriétaire le confirme).
- **w4-08** (mode réduit TV) : « tuiles grisées « Clé à renouveler » » devient « ligne « Renouveler la clé » en tête de Plus + état de la tuile Plus » ; le rappel quotidien est inchangé.

## Décisions prises par l'architecte (renversables, écrites pour ne pas bloquer)

- Les deux rendus (legacy et `NAV_V2`) coexistent une version ; rien n'est supprimé avant que `NAV_V2` soit allumé et validé.
- Le modèle de navigation vit dans `core` (seul module testé en JVM) ; aucune logique d'état dans `sender`/`receiver`.
- Les filtres d'état utilisent des **ids**, plus jamais des étiquettes.
- `parental` reste sans id de télémétrie (règle existante) ; `plus`, `phone_page`, `quick_action` sont ajoutés par w11-14 ; en attendant, les cahiers utilisent des ids existants (documenté dans chaque cahier).
- Les touches de couleur sont des raccourcis, jamais l'unique chemin.

## Questions au propriétaire (aucune ne bloque : recommandation appliquée par défaut)

D-W11-1 à D-W11-8 de la conception § 12 (destinations, boutique en ligne de Plus, badge court, touches de couleur, mode enfant, interrupteur, test usagers, « TV DLNA » dans Connexion avancée). Chaque cahier applique la recommandation ; une réponse contraire du propriétaire se traduit par une ligne dans « Réponses du coordinateur » du cahier concerné.
