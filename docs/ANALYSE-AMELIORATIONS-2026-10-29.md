# CastBridge — analyse et programme d’amélioration

Date : 29 octobre 2026. Base examinée : commit `bbbb7e31`. Audit du dépôt, lectures ciblées du code et de la documentation, tests locaux et premières propositions visuelles. Aucune modification du code applicatif, aucun déploiement.

## 1. Orientation retenue avec le propriétaire

Le constat utilisateur est prioritaire : **la copie, la reprise et la synchronisation avec la TV restent instables ; l’ergonomie, le design et l’expérience utilisateur doivent être nettement améliorés**. La présence de nombreuses règles et de milliers de tests JVM ne démontre pas la fiabilité sur les appareils.

Le premier chantier utilisant les images génératives porte sur **le design et les parcours TV–téléphone**. Les formations et quiz multimédias seront abordés après inventaire, essais et choix des outils d’images, de synthèse vocale et de vidéo. Le prototype de famille créé pendant l’exploration est mis de côté ; il n’est pas intégré au contenu.

Ordre recommandé : conception des parcours et de leurs états → fiabilisation technique associée → implémentation et validation sur appareils → production pédagogique multimédia avec les outils sélectionnés. Les risques d’exploitation avérés peuvent être corrigés dans un lot court indépendant.

## 2. Ce qui constitue déjà une base utile

- Séparation d’un cœur Kotlin sans Android, des applications, du backend et du service de jeux.
- File de transferts persistante, ancrage des sources, reprises, détection des doublons et arbitrage Wi-Fi/Bluetooth déjà présents. Il faut éprouver leur intégration, pas les réinventer sans diagnostic.
- Signatures d’activation, contrôles de confiance, limitations de tentatives, restrictions de routes et consentements déjà développés.
- Charte, tokens partagés, icônes vectorielles, budgets de ressources et profils pour TV modestes.
- Contenus structurés, règles de validation, outils de lots, traçabilité et préparation d’une chaîne multimédia.
- Tests backend, migrations, tests de sockets et de conditions réseau côté jeux : ne pas assimiler l’absence de tests UI Android à une absence générale de tests d’intégration.

L’inventaire hors build/target compte environ 210 000 lignes Kotlin/Java/Python sous Android, 51 000 sous backend et 10 000 sous server-play, tests compris. L’étendue fonctionnelle est importante ; la capacité à livrer une expérience cohérente devient le principal enjeu.

## 3. Premier chantier : design et expérience utilisateur

### Livrables visuels produits

- [Accueil coordonné TV–téléphone](../work/ameliorations-2026-10-29/design/01-accueils-tv-telephone.png).
- [Envoi, interruption et reprise](../work/ameliorations-2026-10-29/design/02-copie-interruption-reprise.png).
- [Prompts complets](../work/ameliorations-2026-10-29/design/PROMPTS.txt), génération par l’outil intégré image_gen.

Références : `branding/README.md`, captures historiques dans `branding/guide/screens/`, code actuel `MainActivity.kt`, `TvHome.kt`, `HomeGroups.kt`. Les captures ne sont pas présentées comme des captures de la dernière version installée.

### Direction proposée

**Téléphone.** Entrée « Ma TV », identité claire de la TV, état réel de connexion, bouton « Envoyer des fichiers », accès directs à Télécommande et Bibliothèque TV, copie active visible. Navigation proposée : Ma TV / Fichiers / Découvrir / Réglages. Les fonctions existantes restent accessibles dans cette arborescence ; leur migration nécessite une table exhaustive des destinations et liens profonds.

**Télévision.** Quatre intentions principales : Bibliothèque / Recevoir / Apprendre / Jouer. Continuer une lecture et suivre la réception doivent être immédiats. Connexion, stockage et réglages restent accessibles, avec les alertes utiles visibles. Un accueil vide doit guider vers le premier envoi. Le mode enfant conserve ses restrictions et ne doit jamais exposer un code administrateur.

**Identité visuelle.** Conserver les couleurs du projet : bleu nuit TV, surfaces indigo, ambre pour l’action et le focus, fond clair téléphone. Clarifier les niveaux de titres et la densité. Les logos et pictogrammes définitifs proviendront des sources vectorielles existantes ; les pictogrammes des maquettes ne sont pas des remplacements approuvés.

**Navigation.** Un seul focus TV évident, retour au dernier élément sélectionné, aucune perte de focus après rafraîchissement, pas de défilement horizontal cachant une action essentielle. Tester à 720p, à distance réelle, puis sur téléphone étroit et avec agrandissement des caractères. Un lecteur d’écran et une navigation au clavier font partie de la validation.

### Contrat des états de copie

| État | Information visible | Actions attendues |
|---|---|---|
| Préparation | TV, fichiers, destination, place estimée après copie | Changer la destination, commencer |
| Envoi | Progression confirmée, fichier et destination ; durée seulement si estimation fiable | Pause, détails, annulation distincte |
| Liaison perdue | Dernier état confirmé, recherche en cours, aucune fausse réussite | Pause, aide adaptée, annulation |
| Réconciliation | Vérification de la copie partielle sur la TV | Attendre ou annuler sans détruire la source |
| Reprise | Octets effectivement confirmés par la TV, progression suivante | Pause, retour à l’accueil |
| Vérification finale | Vérification d’intégrité et enregistrement | Pas de « terminé » prématuré |
| Terminé | Fichier disponible et destination | Lire, ouvrir la bibliothèque |
| Action nécessaire | Cause précise : USB retirée, espace insuffisant, source inaccessible, autorisation expirée | Une action adaptée au problème |

Les pourcentages et volumes des maquettes sont fictifs. « La copie continue si vous quittez cet écran » n’est affichable que si le service peut réellement poursuivre ; un refus Android doit produire une consigne différente. « Copie reprise » exige un accusé de la TV. Une copie en cours est bleue ; l’ambre est réservé au focus ou à l’attente nécessitant attention : la teinte ambre de la carte de réception de la première planche devra être ajustée à l’implémentation.

Les planches sont des références de direction, pas des écrans exécutables ni une validation des contrastes. Leur ratio TV composé n’est pas une spécification de grille : la déclinaison réelle sera contrôlée en 16:9, avec zone sûre et tailles mesurées. L’image générée ne doit jamais remplacer l’interface accessible.

## 4. Fiabiliser copie, reprise et synchronisation

Repères : `sender/TransferQueue.kt`, `core/link/BulkRoute.kt`, `core/tv/TvClient.kt`, `core/tv/ReceiverServer.kt`, `core/trust/`, services d’envoi Wi-Fi/Bluetooth.

La file possède déjà de nombreuses protections, mais le parcours traverse plusieurs services et états persistants. Les commentaires et le journal montrent des corrections successives autour des coupures, changements de route et métadonnées partielles. Cela justifie une analyse de bout en bout ; aucune cause unique de l’instabilité actuelle n’a été reproduite dans cet audit.

Travail recommandé :

1. Reproduire les incidents sur les appareils de référence et enregistrer une chronologie corrélée téléphone/TV, sans PIN ni contenu privé : identifiant de copie, tentative, route, état, octets confirmés, motif de bascule.
2. Définir une seule machine d’états persistante et une autorité claire pour chaque donnée. La connexion découverte, l’authentification, la disponibilité du stockage et la capacité à recevoir sont des faits différents.
3. À la reconnexion, réconcilier l’identité de la TV, du fichier, du volume et de la copie partielle avant de reprendre. Ne pas considérer nom et taille seuls comme une preuve d’identité du contenu.
4. Vérifier les frontières transactionnelles existantes : données réellement écrites, métadonnées, finalisation atomique, réponse de succès. Rejouer une demande ne doit créer ni doublon ni suppression de source prématurée.
5. Lors d’une bascule réseau, arrêter proprement l’ancien producteur, empêcher deux écritures concurrentes et repartir du point confirmé. Conserver la pause explicite de l’utilisateur au redémarrage.
6. Distinguer « téléphone autorisé », « TV joignable », « bibliothèque actualisée » et « copie terminée ». Ne pas appeler toutes ces situations « synchronisé ».
7. Protéger les commandes et sondes des lectures lentes de stockage. Le cache et la lecture unique ajoutés à `ReceiverServer.listing()` sont utiles, mais les lecteurs non tolérants peuvent attendre le scan ; tester la saturation et prévoir une réponse bornée ou un résultat provisoire explicitement daté.

**Matrice minimale sur appareils :** Wi-Fi coupé à plusieurs étapes, Bluetooth seul puis retour Wi-Fi, extinction/redémarrage TV, application téléphone tuée, écran éteint, USB retirée puis remontée, disque plein, autorisation URI perdue, deux fichiers homonymes, plusieurs téléphones, acquittement final perdu. Chaque scénario vérifie le fichier final par empreinte, l’absence de doublon et la cohérence des écrans.

**Cibles proposées, à mesurer avant de contractualiser :** zéro corruption et zéro perte de source sur cette matrice ; une seule copie finale par opération ; reconnexion avec progression cohérente ; réponse visible à une action en moins de 200 ms hors traitement ; accueil utilisable en moins de 2 s ; commandes locales p95 sous 500 ms sur le réseau de référence. Une reprise en moins de 10 s après retour d’une route stable est une cible à valider, pas une performance acquise.

## 5. Autres dimensions fonctionnelles

| Dimension | Amélioration proposée | Validation concrète |
|---|---|---|
| Première utilisation | Parcours guidé unique : trouver la bonne TV, autoriser, premier envoi, résultat | Un nouvel utilisateur termine sans jargon réseau ni aide orale |
| Bibliothèque | Chargement progressif, recherche et filtres, distinction TV/téléphone/USB, dossier et état de disponibilité | Bibliothèque de 1 000 fichiers utilisable pendant réception et miniatures |
| Gestion de fichiers | Rendre les conséquences d’une suppression de masse explicites ; proposer une récupération lorsque compatible avec l’espace et la politique retenue | Aucun fichier supprimé par confusion de volume ou sélection cachée |
| Lecture | Reprise lisible, sous-titres/audio accessibles, états de chargement et erreurs de décodage compréhensibles | Lecture pendant copie, changement de piste et reprise après arrêt sur TV 32 bits |
| Télécommande | Accès immédiat, retour perceptible, état de connexion fidèle, commandes essentielles prioritaires | Délai mesuré et aucun ordre silencieusement perdu ou rejoué à tort |
| Multiutilisateur | Identité du téléphone/TV visible, règles de prise de contrôle et rôles explicites | Deux utilisateurs ne perturbent pas silencieusement la lecture ou la file |
| Contrôle parental | État réel de protection, permissions manquantes et limites expliqués au parent | Parcours enfant/parent et contournements documentés vérifiés sur matériel |
| Licences et boutique | Montrer ce qui est disponible, ce qui expire et l’effet hors ligne ; alléger les rappels | Un utilisateur comprend son accès sans consulter l’administration |
| Portefeuille et jeux | Historique intelligible, opération en attente distincte d’un échec, réconciliation après coupure | Aucun double effet ; rapprochement entre opération affichée et registre serveur |
| Assistance | Diagnostic compréhensible, rapport volontaire expurgé, instructions adaptées à la panne | Un incident est reproductible sans transmettre de secrets |
| Internationalisation | Centraliser les libellés FR/EN, unités et formats ; préserver les noms locaux | Aucun écran tronqué avec libellés longs et police agrandie |

## 6. Qualité pédagogique et multimédia — chantier différé

L’absence de médias est un manque de produit majeur, pas une finition. Le comptage des fichiers ordinaires sous `content/learn`, `content/quiz` et `content/langues` n’a trouvé aucun PNG/JPEG/WebP/SVG, MP3/WAV/OGG/M4A, MP4/WebM. Ce comptage n’inspecte pas les archives et ne couvre pas les dessins générés à l’exécution, les ressources Android ou un éventuel dépôt externe.

Les 259 `content/learn/*/pack.json` inspectés indiquent `draft`. Les 12 988 questions de `content/quiz/batches/*.json` indiquent `review` ; ce sous-ensemble n’est pas toute la banque. `ContentPolicy.kt` contient encore `legacyStable = true` pour afficher certains contenus en relecture. Définir un seuil de qualité publiable avant généralisation.

La chaîne `tools/media-pipeline` prépare les demandes, budgets, empreintes et contrôles. Sa documentation précise qu’elle ne produit pas les médias et que l’interface ASR attend des résultats externes. Elle est surtout décrite autour des langues ; son extension aux formations générales et quiz doit être vérifiée.

**Outils dans cette session :** génération d’images intégrée disponible et utilisée pour les maquettes. Aucun outil direct de synthèse vocale ou de génération vidéo n’a été identifié dans le premier inventaire des outils exposés. Cela ne clôt pas la recherche des plugins, services et moyens locaux. Aucun choix de fournisseur audio/vidéo n’est donc arrêté.

Avant de commencer les formations : établir une matrice outils × qualité × langues/accents × maîtrise des diagrammes × sous-titrage × coûts × droits × formats hors ligne × reproductibilité. Produire ensuite un échantillon comparatif court avec les outils accessibles. Distinguer vidéo générative et animation pédagogique montée à partir d’illustrations : elles servent des besoins différents.

Le pilote pédagogique devra réunir une leçon réellement illustrée, des pistes vocales écoutées, une courte vidéo utile, les exercices associés et un quiz multimédia, tous lisibles hors ligne sur la TV. Les schémas scientifiques et textes exacts peuvent nécessiter SVG et composition déterministe plutôt qu’une image générée seule. Ajouter transcriptions, sous-titres et alternatives aux médias ; vérifier la prononciation, les réponses et la compréhension avec un relecteur compétent. Mesurer complétion, erreurs et acquis, pas uniquement le volume de médias produits.

## 7. Constats non fonctionnels prioritaires

### Supervision — défaut confirmé, priorité haute

`ops/monitoring/host-check.sh` active `set -Eeuo pipefail`. `log_check` et `log_error` terminent par `[ "$DRY_RUN" = true ] && echo ...`. En mode `--apply`, cette expression retourne 1 ; l’appel de fonction provoque l’arrêt avant le signalement. Reproduction locale des fonctions exactes : code 1, marqueur de fin non atteint, pour les deux fonctions.

Corriger les retours, tester les modes réel/simulation avec commandes simulées, garantir le signalement d’échec. Une absence de conteneur, de dump ou de réponse TLS ne doit pas être traitée comme un état normal. Ajouter une vraie sonde HTTPS externe ; l’incident TLS ancien du journal n’a pas été revérifié en production.

### Sauvegarde et restauration — défauts statiques, priorité haute

- `offsite-backup.sh` sélectionne le plus récent `castbridge-*.sql.gz`. Cela inclut le dump partiel `castbridge-licenses-...`, écrit après le dump complet par `backend/backup.sh` : risque de n’expédier que les licences.
- Le hook `OFFSITE_HOOK` appelle le script sans `--apply`, alors que celui-ci le requiert. L’utilisation du script brut comme dans l’exemple ne réalise donc pas la copie.
- La procédure de restauration de `ops/monitoring/README.md` fournit un `.gz` directement au client SQL, ne prépare pas correctement un serveur MySQL prêt, puis cherche une API HTTP sur le port 3306. Elle doit être remplacée par un exercice exécutable et vérifié.
- Le cron utilise `source` sans imposer Bash ; vérifier sa portabilité sur le serveur cible et l’export des variables nécessaires.

Séparer explicitement sauvegarde complète/partielle, produire un manifeste, vérifier l’âge et l’intégrité, tester la restauration dans un environnement isolé. Inclure les fichiers et secrets indispensables via une procédure distincte chiffrée. Définir RPO/RTO après mesure. L’import MySQL doit recevoir du SQL décompressé : [documentation MySQL](https://dev.mysql.com/doc/refman/9.7/en/reloading-sql-format-dumps.html).

### Tests et livraison

Aucun répertoire conventionnel `androidTest` trouvé dans les modules Android. Les workflows inspectés ne lancent pas explicitement `:server-play:test` ; la construction des installateurs desktop est manuelle. Le workflow Android est déclenché sur push et manuellement, sans déclencheur pull_request. Ajouter une matrice de tests appropriée et les parcours instrumentés critiques avant chaque publication. [Documentation Android sur les tests instrumentés](https://developer.android.google.cn/training/testing/instrumented-tests?hl=en).

Le README exige un Gradle installé localement, sans wrapper versionné. Ajouter un wrapper vérifié, des toolchains explicites, la vérification des dépendances, un inventaire des composants et licences, et une analyse régulière des dépendances. Aucun audit de vulnérabilités exhaustif n’a été réalisé ici.

`ReceiverServer.kt` atteint 2 408 lignes, `PlayerActivity.kt` 1 645 et `TvService.kt` 1 452. Découper progressivement par responsabilité, avec tests de contrat, particulièrement les états réseau, stockage et interfaces. Une réécriture globale ferait perdre les protections déjà présentes.

### Sécurité, confidentialité et exploitation

Le trafic local HTTP est autorisé ; `TvClient` applique un PIN ou un jeton de téléphone. Examiner une évolution vers une authentification par appareil et un canal chiffré compatible avec les TV existantes, sans supprimer la connexion hors ligne. L’audit ne démontre pas ici une exploitation ni une absence générale de chiffrement : les transports doivent être examinés séparément.

Maintenir une matrice routes/rôles, tester la révocation et la rotation de clés, limiter l’administration distante, vérifier la suppression/rétention des données et expurger les diagnostics. Tester les restaurations du portefeuille et des licences, ainsi que les migrations interrompues. Mesurer disponibilité, latence, mémoire, consommation réseau et énergie en usage réel. Les obligations relatives aux données personnelles, paiements et jeux nécessitent une revue dédiée ; aucune conclusion juridique n’est formulée ici.

### Documentation et décisions

Le journal mélange état actuel et longues listes anciennes. Exemple vérifié : l’ancienne note affirmant que `recover()` n’est pas branché est dépassée, car `sender/agent/AssistantModel.kt` l’appelle. Conserver une fiche d’état courte et datée, archiver le journal et relier chaque incident à sa reproduction, son correctif et sa validation sur appareil.

## 8. Vérifications réalisées et limites

- Suite `python3 -m unittest discover -s tools/tests` : **194 tests, deux échecs** reproduits. Un échec réel de cohérence : `routes.txt` périmé. L’autre provenait du chargement natif Gradle dans le bac à sable ; le test ciblé relancé hors bac à sable avec autorisation et verrou JVM a **réussi**. La suite complète n’a pas été relancée après ce test ciblé.
- Reproduction isolée du retour erroné des fonctions de supervision ; aucune exécution réelle du script de supervision ni de sauvegarde.
- Inventaires de fichiers, états de contenu, CI, code et documentation ; inspection de captures existantes et des maquettes générées.
- Pas de compilation générale, de test backend complet, d’essai TV/téléphone dans cette session, de mesure de performance actuelle ni d’inspection de production. Les résultats historiques du HANDOFF ne sont pas présentés comme des résultats de cet audit.

## 9. Lots de travail et critères de sortie

| Ordre | Lot | Effort relatif | Condition de sortie |
|---|---|---|---|
| 1 | Design TV–téléphone et contrats d’états | Moyen | Parcours normal, vide, coupé, repris et échoué cohérents ; navigation et textes spécifiés |
| 2 | Reproduction et fiabilisation des transferts | Élevé, estimation après diagnostic | Matrice de pannes réussie sur appareils, intégrité et états concordants |
| 3 | Implémentation UI Android et validation | Élevé | Focus 720p, téléphone étroit, accessibilité et parcours utilisateur validés |
| Indépendant urgent | Supervision, sélection des sauvegardes et restauration | Petit à moyen | Alertes testées et restauration complète démontrée |
| 4 | CI, contrats, performance et livraison | Moyen à élevé | Contrôles automatisés fiables, artefacts reproductibles et rapport matériel |
| Avant formations | Inventaire et sélection des outils médias | Moyen | Comparaison de sorties réelles, budget, formats et accès vérifiés |
| Ensuite | Pilote pédagogique multimédia | Moyen puis élevé pour généraliser | Une unité complète, relue, utile, légère et fonctionnelle hors ligne |

Éviter un calendrier artificiellement précis avant le diagnostic matériel. Les maquettes sont une première proposition concrète ; la stabilisation de la copie et la qualité d’interaction restent les critères décisifs.
