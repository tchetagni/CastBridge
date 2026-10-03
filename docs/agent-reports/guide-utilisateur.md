# Rapport : guide utilisateur CastBridge (image d'abord)

Branche `claude/guide-utilisateur`, depuis `integration/agents` (25709b2). Docs seulement.

## Environnement des captures

- Émulateur `emulator-5580` (arm64), seul appareil touché. Le téléphone `RFCR313ABNF` et les TV réelles n'ont reçu aucune commande.
- Apps construites depuis ce worktree par le verrou Gradle : CastBridge-TV `0.14.21-beta` (debug, déverrouillée, installée sans problème d'ABI) et CastBridge `1.2.37-beta` (l'ancienne `1.2.1-beta` était signée autrement : désinstallée sur l'émulateur, puis réinstallée).
- Vidéos de test générées avec ffmpeg (barres de couleur, mire). Aucun nom, numéro ou code réel.
- Parental : un code de test a été créé sur l'émulateur seulement (jamais montré en clair). Il reste enregistré sur cet émulateur.
- État final vérifié : `wm size` = override 1280x720, `wm density` = override 160 (physique 1080x2400 @420), animations remises à 1.0.

## Résultat

- 35 captures réelles embarquées dans le HTML (17 téléphone, 18 TV), 45 figures (certaines captures sont réutilisées avec d'autres repères) plus 13 dessins SVG signalés « Dessin, pas une capture ».
- 52 phrases d'étape, toutes de 12 mots ou moins (contrôlé par le script).
- `guide-utilisateur.html` : 2,52 Mo, aucune adresse `http(s)://`, aucune ressource externe, HTML équilibré (html.parser, 0 erreur).
- Vérifié visuellement : rendu à 400 px (aucun débordement horizontal) et à 1000 px, thème sombre. Les repères de 12 captures ont été dessinés en rouge sur un PNG de contrôle et regardés ; deux décalages (champs de code, accueil TV masqué par une notification) ont été corrigés.

## Ce que montre chaque écran

Téléphone : s01 « CastBridge et vos données » (deux choix) ; s02 onglet « CastBridge TV » (premier écran) ; s03 onglets suivants (Sur le téléphone, Apprendre, Parental) ; s04 saisie manuelle de la TV, champs vides ; s05 adresse et code saisis (code masqué par l'app) ; s06 TV jointe (« SUR LA TV ») ; s07 « Sur le téléphone » ; s08 lecteur avec « Lire sur la TV » ; s09 feuille « Lire sur la TV » ; s10 « Ouvrir avec CastBridge » avec les quatre libellés et leurs explications ; s11 « Apprendre » ; s12 et s13 « Données hors ligne » (Wi-Fi uniquement, « Tout mettre à jour », « Envoyer à la TV », section Langues, « Télécharger mes leçons de langue ») ; s14 « Jeux » ; s15 « Activer la TV » ; s16 « Locations sur la TV » ; s17 « Réglages ».

TV : s18 accueil avec tuiles et vidéos ; s19 tuile « Recevoir du téléphone » et tuiles suivantes (code masqué) ; s20 aide « Copier une vidéo sur la TV » (code masqué) ; s21 fenêtre Bluetooth d'Android ; s22 « Ajouter un téléphone » ; s23 « Apprendre » ; s24 « Langues » (avec lots) ; s25 niveaux d'une langue ; s26 « Jeux » ; s27 Quiz des Millions ; s28 Bibliothèque ; s29 lecture ; s30 « Clé USB » ; s31 tuile « Contrôle parental » ; s32 à s35 Contrôle parental (accueil, clavier du code, réglages des parents, profils des enfants).

## Écrans demandés NON capturés, et pourquoi

- Onglet « Parental » du téléphone : l'écran est protégé (FLAG_SECURE), la capture sort noire. Le guide montre seulement l'onglet dans la rangée.
- File d'envoi (« Envoi vers la TV : … · 2 en attente ») : sur l'émulateur la copie de 66 Mo finit instantanément, et aucune TV de confiance n'est reliée au téléphone. Le guide la montre par un dessin signalé, avec le format de texte lu dans `QueueGlances`.
- « Lire en direct » : n'apparaît que dans la feuille « Lire sur la TV » quand une TV est trouvée ; sur l'émulateur la recherche ne trouve rien. Le libellé figure dans le texte du guide (source `UiTexts.kt`).
- Boutons de « Ouvrir avec CastBridge » actifs : ils sont grisés (« Aucune TV ajoutée ») car le Bluetooth de confiance ne peut pas s'établir sur un émulateur.
- « Envoyer à la TV » et « Télécharger et envoyer » par lot : la liste des lots du serveur est vide hors réseau. Libellés pris dans le code (`LotsScreen.kt`) et cités dans une note.
- Carte de progression de réception sur la TV : le transfert est trop rapide pour la saisir.
- « Langues » de la TV vide : la TV de l'émulateur contient déjà 14 lots.
- Installation de la TV depuis une clé USB et écran d'activation de la TV : non capturables avec une version déverrouillée. Dessins signalés.
- Profil enfant créé et règles : la saisie du prénom demande un clavier ; seul « Ajouter un profil » est montré.
- Écran « Téléchargements » : une fenêtre d'avertissement légal s'ouvre d'abord ; il n'est pas utilisé dans le guide.

## Points non vérifiés

- Les huit cas de « Si ça ne marche pas » : seuls le message « Le code de la TV a changé : saisissez-le à nouveau. » et l'attente en file sont tirés du code. « Pas de son » et « Mauvais Wi-Fi » sont des conseils généraux ; « TV pleine : libérez de la place ou rangez sur une clé USB » n'a pas été testé (« Où ranger les nouveaux fichiers… » existe bien sur la tuile « Clé USB »).
- La phrase « Quand la TV est trouvée, les façons d'envoyer s'affichent » vient de `SendWays.castOrder`, pas d'une capture.
- Affichage en thème clair : les couleurs existent mais je n'ai vu que le rendu sombre.
- Le numéro d'aide est un cadre vide, rien n'est inventé.
- Rendu testé avec Chrome seulement.
