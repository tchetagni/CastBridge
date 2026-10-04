# Accueil de CastBridge-TV : groupes et grilles (2026-10-04)

Demande du propriétaire : les outils et fonctions (Jetons, Téléchargements, Administration…) sont rangés **en grille dans un bouton de groupe**.

## Ce que l'on voit

L'accueil garde ses rangées de vidéos (« Reprendre », « Récemment ajoutés »…). Sous « Outils et fonctions », une ligne de grandes tuiles :

1. **Bibliothèque** (accès rapide, la plus utilisée) ; en version d'essai, « Passer à la version complète » la précède.
2. **Code PIN** (accès rapide, à côté de Bibliothèque) : le code de connexion de la TV en grand, groupé par 3 chiffres (« Code PIN · 482 913 »), masqué « •••••• » sous un profil enfant ; jamais caché par un réglage.
3. Cinq **boutons de groupe** : icône, nom (28 sp) et résumé (« 4 outils · 2 actifs », « · à vérifier » si un outil a un avertissement).

OK sur un groupe ouvre une **grille** plein écran : titre du groupe, 3 colonnes, autant de lignes que nécessaire, chaque tuile avec grande icône ET nom (28 sp) et son badge (« Activé », « Code 12•••• », « 3 fichier(s) »…), la description de la tuile focalisée en bas. Zone de sécurité de 5 %.

## Groupes et rattachement des tuiles

Le rattachement se fait par **identifiant de tuile** (le premier argument de `tile("…")` dans `PlayerActivity.kt`) : `android/core/.../tv/home/HomeGroups.kt`.

| Groupe | Tuiles (ordre de la grille) |
|---|---|
| (accès rapide) | `upgrade` (essai), `library` Bibliothèque, `pin` Code PIN |
| Médias | `library` Bibliothèque, `downloads` Téléchargements, `usb` Clé USB, `receive` Recevoir du téléphone |
| Apprendre | `learn` Apprendre, `langues` Langues |
| Jeux et jetons | `games` Jeux, `online_game` Partie Internet, `wallet` ◎ Jetons |
| Téléphones et réseau | `pair` Ajouter un téléphone, `remote` Télécommande, `bluetooth` Bluetooth, `wifi_direct` Wi-Fi Direct, `internet` Internet, `phones` Téléphones synchronisés |
| Administration | `admin` Administration, `updates` Mises à jour, `settings` Connexion & réglages, `dev_options` Options développeur, `parental` Contrôle parental, `help` Aide |

Notes : « Ajouter un téléphone » partage l'identifiant de télémétrie `bluetooth` avec la tuile Bluetooth ; elle a donc un identifiant d'accueil distinct, `homeId = "pair"`. `online_game` et `phones` n'existent pas encore comme tuiles d'accueil (Partie Internet se lance depuis « Jeux », les téléphones synchronisés depuis la barre d'état) : leur place est réservée, ils apparaîtront dès qu'une tuile portera cet identifiant. Une tuile **inconnue** des groupes n'est jamais perdue : elle s'affiche seule après les groupes.

## Règles de visibilité

- Les filtres existants s'appliquent **avant** le regroupement : `ParentalHub.filterHome` (profil enfant, catégories bloquées, tuiles fermées de l'essai), réglage `wallet.enabled`, service indisponible. Une tuile masquée n'existe dans aucune grille.
- Un groupe sans tuile visible disparaît ; **un groupe à une seule tuile visible ouvre cette tuile directement** (bouton nommé comme la tuile) ; une tuile déjà en accès rapide n'est pas répétée.
- Les actions des tuiles sont inchangées (`tile(...)` : comptage de télémétrie, `ParentalHub.guardTile`).

## Navigation (télécommande à 5 touches)

| Où | Touche | Effet |
|---|---|---|
| Accueil | flèches | comme avant (focus Android) |
| Accueil, bouton de groupe | OK | ouvre la grille, focus sur la première tuile |
| Grille | GAUCHE / DROITE | tuile voisine ; s'arrête aux bords de la ligne (pas de bouclage) |
| Grille | HAUT / BAS | ligne voisine ; HAUT sur la première ligne et BAS sur la dernière restent sur place ; BAS vers une dernière ligne incomplète tombe sur la dernière tuile |
| Grille | OK | lance l'outil (la grille reste ouverte derrière l'écran lancé) |
| Grille | RETOUR | ferme la grille, focus rendu au bouton du groupe |
| Accueil | RETOUR | comme avant (l'application passe en arrière-plan) |

Si les statuts changent grille ouverte (Wi-Fi Direct activé…), la grille se redessine sur la même tuile ; si le groupe disparaît (profil enfant activé), retour à l'accueil.

## Code

- Logique (testée sans Android) : `android/core/src/main/kotlin/castbridge/core/tv/home/HomeGroups.kt` ; tests `android/core/src/test/kotlin/castbridge/core/tv/home/HomeGroupsTest.kt`.
- Dessin : `android/receiver/.../HomeGroupViews.kt` (`GroupTile`, `GridTile`, `GridPanel`), `HomeScreen.fillTools` ; RETOUR : `PlayerActivity.onKeyDown` → `HomeScreen.closeGrid()`.
- `HomeTool` porte maintenant un `id` (vide = tuile inconnue des groupes).

## Ajouter une tuile

1. Créer la tuile avec `tile("mon_id", icône, "Nom", …)` dans `PlayerActivity.homeTools()` (ou `HomeTool(…, id = "mon_id")` si elle n'a pas d'identifiant de télémétrie ; `homeId = "…"` si l'identifiant de télémétrie est déjà pris).
2. Ajouter `"mon_id"` à la bonne liste de `HomeGroups.GROUPS` (et au besoin au test `cinq groupes…`). Le test « les identifiants du mappage sont ceux de PlayerActivity » échoue tant qu'une tuile n'a pas de groupe.
3. Si elle doit être masquée selon le profil ou l'essai, c'est `ParentalHub.filterHome` (par libellé) : rien à faire dans les groupes.

## Logo

L'accueil et l'écran Serveur utilisent `logo_castbridge_tv_horizontal_compact` (56 dp de haut), **sans** le sous-titre MBOKO (6 dp à cette hauteur, illisible à 3 m). Le logo complet avec MBOKO reste pour les grands formats (bannière, exports). Généré par `branding/tools/gen_cameroun_mark.py` (`write_tv_compact`), puis `gen_app_assets.py`.

## Test par le propriétaire (TV réelle)

1. Installer l'APK TV verrouillé, ouvrir l'accueil : « Bibliothèque » puis 5 boutons de groupe, logo net avec « CastBridge » et le badge TV.
2. « Médias » : grille de 4 tuiles (1 ligne de 3 + 1) ; BAS depuis la 2e colonne tombe sur « Recevoir du téléphone » ; RETOUR : le focus est sur « Médias ».
3. « Téléphones et réseau » : 5 tuiles (6 avec « Téléphones synchronisés »), tuile « Wi-Fi Direct » : OK l'active, le badge passe à « Activé » sans fermer la grille.
4. « Administration » : « Aide », « Mises à jour »… OK ouvre l'écran, RETOUR de cet écran revient à la grille, RETOUR ferme la grille.
5. Profil enfant (Contrôle parental) : seuls les outils autorisés restent, les groupes vides disparaissent.
6. Lisibilité à 3 m : noms et icônes lus sans effort ; le focus ne sort jamais de la grille avec les flèches.

## Accueil du Quiz

Régression corrigée (retour du propriétaire : « je préfère encore l'ancien ») : l'ajout de la carte « Partie Internet » portait l'accueil du Quiz à 6 choix, ce qui déclenchait le mode « compact » (une seule ligne de 6, cartes de 120 dp) : « Meilleurs scores » était écrasé et « Partie Internet » / « Quitter » sortaient de l'écran.
- Aspect d'origine (tv-0.14.28-beta) rétabli : cartes de 300 dp, 2 par ligne (2 + 2 + 1 ; avec « Partie Internet » : 2 + 2 + 2), même en-tête et mêmes espacements. Ordre : Amis, Mise / Entraînement, Meilleurs scores / Partie Internet (si non masquée), Quitter.
- Le cœur pur `QuizHomeLayout` (`rows`, `cardWidthDp`, `move`) décide du nombre de cartes par ligne selon la largeur en dp (zone sûre de 90 %, carte jamais sous 220 dp) : sur un écran étroit les cartes passent à la ligne au lieu de déborder ; l'accueil défile si besoin.
- Télécommande : GAUCHE/DROITE restent dans la ligne (pas de bouclage), HAUT/BAS changent de ligne en gardant la colonne la plus proche.
- Les autres étapes du Quiz (parcours, niveaux, scores, mise, duel) ont au plus 4 cartes sauf niveaux/filières (déjà en grille `LevelGridLayout`) : même cause absente.
- Dessin Android vérifié par compilation seulement ; l'aspect réel se confirme sur une TV (GaiaOS 720p).
## Tuile « Code PIN » et nouveau PIN

OK sur la tuile ouvre « Code PIN de la TV » (`PinActivity`) : le code en très grand, « Saisissez ce code sur le téléphone, dans CastBridge, une seule fois », les téléphones de confiance Bluetooth (n / 8, noms ; **non touchés**), « Générer un nouveau PIN » et « Retour ».

« Générer un nouveau PIN » : code parental d'abord s'il existe (session fermée) ; confirmation (« Annuler » présélectionné) ; nouveau code (`Pin.generate`, différent de l'actuel et des deux précédents, dont seules les empreintes SHA-256 tronquées sont gardées) ; écriture atomique (`TvPrefs.pinStore`, `commit`) ; ensuite seulement : `PinGuard.rotate` (l'ancien code est refusé « PIN faux » code 2, les compteurs et blocages d'essais de TOUTES les adresses sont effacés, car ils comptaient des essais contre l'ancien code), `TvService.pin` et les badges. Si l'écriture échoue : l'ancien code reste actif, l'écran le dit. Les jetons des téléphones de confiance ne dérivent pas du code : rien à invalider.

Refus : profil enfant actif (code masqué), copie en cours (« Une copie est en cours »), plus de 3 régénérations par heure glissante. Le journal ne reçoit que « PIN régénéré » (jamais la valeur). Règles pures : `castbridge.core.tv.pin` (`PinRules`, `PinRegenerator`, `PinFlow`, `PinDisplay`).
