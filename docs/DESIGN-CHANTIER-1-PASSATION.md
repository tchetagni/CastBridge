# Chantier 1 — design et UX, passation à Claude

29 octobre 2026. Le propriétaire a demandé de terminer le premier chantier puis d’arrêter toute compilation ici pour la confier à Claude et économiser les tokens. Aucun agent Claude n’a été contacté automatiquement. Le dépôt partagé a avancé pendant la session : à la clôture, HEAD = `a38e26a4` et une partie de cette refonte est déjà versionnée par un autre travail. Les dernières retouches listées par `git diff` restent non commitées. Cette session n’a modifié aucun numéro de version.

## Périmètre réalisé dans les sources

### Téléphone

- `MainActivity.kt` : navigation basse Ma TV / Fichiers / Découvrir / Réglages. Les anciens écrans et identifiants restent accessibles. Retour depuis les sous-écrans vers Découvrir, puis Ma TV. La télémétrie s’arrête dans Découvrir, Réglages et Parental au lieu de les attribuer à l’accueil.
- Nouveau `DiscoverScreen.kt` : Apprendre, Jouer, Autres téléviseurs (DLNA), Espace parental. Les activités propriétaires et fonctions d’activation restent dans le menu existant.
- `Theme.kt` : clair par défaut pour une nouvelle installation ; le choix enregistré clair/sombre/système est conservé.
- `TvHome.kt` : titre et carte de connexion, un bouton principal Envoyer des fichiers, Télécommande et Bibliothèque TV. Actions de déplacement, échange, classement et diagnostics sous Autres actions et connexion.
- Première connexion : étapes courtes, une seule action Bluetooth principale, recherche Wi-Fi distincte et aide dépliable.
- Confirmation après sélection de fichiers : nombre, TV, stockage selon le réglage TV, espace actuel si connu ; pour un fichier, choix explicite Garder dans la bibliothèque / Lire sur la TV. La lecture automatique n’est plus imposée par l’accueil. Avertissement distinct pour un déplacement.
- Carte de copie : progression bornée, volumes, état d’attente explicite, pas de fausse confirmation de reprise. Annulation confirmée et ciblée sur l’identifiant de file sélectionné, pour ne pas annuler le fichier suivant si le premier se termine pendant le dialogue. Pas de bouton Pause ajouté : le moteur ne fournit pas encore de pause utilisateur persistante fiable.
- La file n’affiche pas une seconde ligne pour la copie déjà représentée dans la grande carte. Les échecs, réessais et doublons restent visibles.
- Lecture de bibliothèque indépendante de la sonde de connexion : une clé lente ne bloque plus cette boucle d’affichage. Les données de l’ancienne TV sont effacées au changement de client ; la lecture en cours n’est montrée que lorsque la TV répond.
- `TvPairScreen.kt` : actions de connexion sous le texte, pour ne pas écraser celui-ci sur écran étroit.

### TV

- Nouveau `core/tv/home/HomeDashboard.kt` : Bibliothèque / Recevoir / Apprendre / Jouer, puis Connexion et réglages. Chaque outil autorisé apparaît exactement une fois ; aucune permission ou fonction cachée par le contrôle parental n’est réintroduite.
- `HomeScreen.kt` : grille de quatre destinations visibles, entrée secondaire pleine largeur, fond calme sans animation de miniature, message de réception dédié. Focus conservé par identifiant pendant un rafraîchissement. Les grilles existantes, le retour au bouton d’origine, le code protégé et les actions restent réutilisés.
- `HomeGroupViews.kt` : dimensions adaptables et descriptions neutres ; une description de catégorie n’est plus assimilée à un état vert.
- Nouveau `HomeDashboardTest.kt` : conservation de tous les outils, filtrage enfant, retour depuis Recevoir.

## Références et preuves

- Maquettes : `work/ameliorations-2026-10-29/design/01-accueils-tv-telephone.png` et `02-copie-interruption-reprise.png` ; prompts dans le même dossier.
- Capture réelle intermédiaire à 1280×720 : `work/ameliorations-2026-10-29/verification/tv-720p-intermediaire.png`. Prise sur émulateur Android téléphone configuré en paysage, pas sur GaiaOS ni sur un émulateur Android TV. Les dernières retouches de marges et de couleur des descriptions sont postérieures à cette capture.
- Log de construction et tests ciblés réussis : `verification/build-et-tests-cibles.log` dans le même dossier.
- Essai complet du cœur : `verification/tests-complets-incomplets.log`. Gradle a échoué en écrivant les résultats XML de sept classes ; les XML lisibles comptabilisés alors indiquaient 6 006 tests sans assertion en échec, mais ce n’est PAS une suite complète certifiée verte. La cause n’est pas établie.
- Une relance ciblée a été interrompue à la demande du propriétaire. Elle n’est pas une validation finale.

La compilation des deux applications et les suites `castbridge.core.ux.*` / `castbridge.core.tv.home.*` ont réussi avant les dernières retouches. Aucun essai de transfert réel ni test de perte réseau n’a été réalisé. L’instabilité métier signalée par le propriétaire reste un chantier distinct : ne pas la déclarer corrigée par cette refonte.

## Vérifications à effectuer par Claude

Utiliser le verrou JVM partagé du dépôt, une seule commande Gradle à la fois :

```sh
bash tools/agents/gradle-lock.sh --timeout 120 -- gradle -p android --offline :sender:assembleDebug :receiver:assembleDebug :core:test --tests 'castbridge.core.ux.*' --tests 'castbridge.core.tv.home.*'
```

Puis suite complète du cœur si l’environnement le permet ; rechercher la cause de l’erreur de rapports avant de présenter un résultat global. Vérifier aussi les gardes de source qui référencent le code Android.

Contrôles UI essentiels :

1. Téléphone 360 dp et police agrandie : première connexion, navigation basse, Découvrir, retour, réglages clair/sombre. Pas de titre ou bouton tronqué. L’accès propriétaire par geste sur le logo reste disponible.
2. Ma TV connectée et déconnectée : afficher l’état réel, pas un ancien solde d’espace présenté comme actuel ; ouvrir la bibliothèque et la télécommande.
3. Sélection simple/multiple, fermeture et rotation du dialogue, copie sans lecture par défaut, lecture demandée explicitement, avertissement de déplacement. Vérifier les permissions URI.
4. Copie en attente, échec, réessai, annulation pendant progression et pendant la fin d’un fichier : le fichier suivant ne doit pas être annulé par l’ancien dialogue. La progression reflète le transport, pas une preuve inventée de réception durable.
5. TV 720p/1080p : les quatre cartes tiennent sans défilement horizontal, anneau de focus non coupé, grille Recevoir, retour au même bouton, Connexion et réglages et tous ses outils, bibliothèque vide/remplie et réception. Test D-pad complet et profil enfant.
6. Lecture de bibliothèque lente : la connexion reste actualisée ; vérifier que les nouvelles lectures indépendantes ne surchargent pas une TV modeste.

Les APK debug servent uniquement aux essais locaux. La TV distribuée doit rester verrouillée et signée selon `docs/RELEASES.md`. Ne pas distribuer les APK debug de cette session. Cette session n’a réalisé aucune publication ; les versions et dépôts mentionnés ailleurs dans HANDOFF proviennent du travail concurrent.

## Hors périmètre

Pas de génération de formations ou de quiz multimédias. Pas de nouvelle pause persistante, de protocole de reprise ou de preuve d’intégrité. Pas de correction des scripts de sauvegarde/supervision de l’audit initial. Pas de prétention à une validation matérielle finale.
