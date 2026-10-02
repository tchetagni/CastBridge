# Recommandations d'architecture et plan d'amélioration — CastBridge (2026-10-02)

> **Portée.** Branche `integration/agents` au commit `1c26a11` **plus l'arbre de travail non commité** (81 fichiers : grâce absolue, horloge monotone, liste blanche d'essai, `SafeFile`, `TrustedKeyParser`, catalogue de bouquets signé, module tunnel du serveur). Audit en **lecture seule** : aucun fichier existant modifié, ni Gradle ni Maven lancés, ni serveur, ni `main`, ni `~/.castbridge-signing` touchés. Aucun secret reproduit.
>
> **Méthode.** Sept lectures parallèles par axe (sécurité, architecture, UX, fiabilité/ops, tests, contenu, serveur/monétisation), puis vérification manuelle de chaque `fichier:ligne` cité dans les cahiers (`grep -n`). Ce document **s'appuie sur** `AUDIT-PROJET-2026-10-02.md` (Opus) et `AUDIT-CONTENU-APPRENDRE-2026-10-02.md` : il ne les répète pas, il dit ce qui a changé depuis, ce qui est contesté, et ce qui manquait.
>
> **Chemins.** Relatifs à la racine du dépôt ; `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `C/` = `android/core/src/main/kotlin/castbridge/core/`, `B/` = `backend/src/main/java/castbridge/server/`.
>
> **Plan d'exécution.** 42 cahiers autonomes pour des agents Sonnet, en 3 vagues de tâches à fichiers disjoints : `docs/agent-briefs/SONNET-WAVES-INDEX.md`.

## 1. Résumé exécutif : les 12 améliorations à plus fort levier

| # | Amélioration | Impact attendu | Effort | Cahier |
|---|---|---|---|---|
| 1 | **Révocation et numéro de séquence persistés sur la TV** (aujourd'hui `R/ActivationCenter.kt:105` recrée un `ActivationReceiver` avec `RevocationState()` vide et `SeqState()` neuf ; aucun traitement `cbr1` côté TV) | Seule protection réelle contre le vol d'un outil d'émission (téléphone propriétaire, bureau), **la menace à plus forte valeur** : sans elle, une clé compromise reste acceptée par tout le parc jusqu'à une nouvelle APK | M | w2-01 |
| 2 | **`allowBackup="false"` sur CastBridge-TV et CastBridge** (+ exclusions) et **`noSuperAdmin` par défaut** | Ferme le rembobinage des locations/essai par `adb backup` et la fuite du coffre propriétaire par la sauvegarde Google ; une ligne par manifeste | S | w1-01 |
| 3 | **Supervision et sauvegarde hors site du serveur** (aucune alerte aujourd'hui ; sauvegardes sur le même disque, `backend/README.md:259-266`) | Un disque plein, un certificat expiré, une sauvegarde en échec deviennent visibles ; une perte du VPS ne perd plus les licences | S/M | w1-12 |
| 4 | **Fermer le résiduel de la grâce** (`firstInstallTime` + horloge reculée + réinstallation ⇒ `GateState.Grace` tout ouvert, sans `TrialPolicy`) et **persister l'uptime cumulé** pour les plafonds (`C/owner/Keys.kt:86-90` reconnaît le trou au redémarrage) | Supprime le dernier chemin « tout ouvert sans clé » accessible à un technicien sans PIN ; les plafonds d'usage ne se figent plus en redémarrant | M | w1-05 |
| 5 | **Écritures durables** (`fsync`) pour les clés et lots de location (`C/lots/RentalVault.kt:38-39,69-70`), l'index des lots (`C/lots/TvLotStore.kt:251-252`), la progression (`C/learn/Progress.kt:250-251`) via `SafeFile`/`AtomicFile` déjà présents | Une coupure de courant pendant une livraison ne suspend plus une location payée ni n'efface la progression d'un enfant | S | w1-02 |
| 6 | **Parcours d'activation lisible** : retirer « [Texte à valider…] » affiché au client (`C/owner/FeatureGate.kt:172`), **contact du vendeur** (aucun numéro nulle part), messages de refus actionnables, ordre « demander → coller → envoyer » sur le téléphone, clé Bluetooth acceptée sans second « Valider » | Chaque friction ici est un appel de support ou une vente perdue ; coût quasi nul | S/M | w2-03 |
| 7 | **CI utile et sans danger** : `release.yml` signe et publie des APK **non verrouillées** si des secrets existent (`.github/workflows/release.yml:57-80`) ; aucun test Python, ni vérificateur de vecteurs, ni contrôle de contenu, ni `:sshd:test` ne tourne | Les 119 tests Python, 172 vecteurs et budgets de contenu deviennent des gardes automatiques ; plus de risque de build publié sans verrou | S | w1-07 |
| 8 | **Durcissement API TV** : PIN accepté en paramètre d'URL (`C/tv/ReceiverServer.kt:459`), aucun contrôle `Host`, écoute toutes interfaces (`:78`), rétrogradation d'APK avec `force=1` (`R/UpdateInstaller.kt:133`) | Ferme CSRF/rebinding sur le réseau local et la réinstallation d'anciennes APK non verrouillées | S | w1-03 |
| 9 | **Profil « TV hors ligne »** : sonde `connectivitycheck.gstatic.com` périodique (`R/TvNetDiag.kt:21`, `R/TvService.kt:439`), DHT/BitTorrent à l'écoute 6881-6889 (`C/dl/Aria2Config.kt:80,83`) | Conforme au principe du propriétaire ; supprime une exposition juridique (pairs BitTorrent) | S/M | w1-04 |
| 10 | **Shell SSH retiré de la release** (`sshd/.../TvSshServer.kt:122,125` livre `sh -i` et exec en production ; seul l'essai le ferme, `R/SshControl.kt:43`) | Sans cela, un client payant avec son PIN lit les clés de location en clair et réécrit `clock.txt` : les points 4, 5 et 1 restent contournables | M | w2-02 |
| 11 | **Boutique de locations sur le téléphone, phase 0 (preuve de paiement manuelle)** : aujourd'hui le client doit recevoir des `.lot`, un `catalog.json`, taper `produit@période` et être en Wi-Fi (`S/RentalDeliveryActivity.kt:48-50,69-72`) | C'est le chemin de revenu ; la phase 0 (référence MoMo/OM saisie, confirmation par le propriétaire, clé + lots poussés par Bluetooth) ne demande aucun prestataire | L | w2-06, w2-10 (BLOQUÉS : prix, numéro, secret maître) |
| 12 | **Boucle de relecture enseignants outillée** (0 décision dans `content/validation/`, 0 rapport QA, 259 packs `draft`, 3 vocabulaires d'état) et **porte qualité v1** (alt manquant sur 273 figures, aucune licence/avertissement par pack) | Les 488 fiches embarquées dans l'APK (contenu de base) sont du brouillon IA non relu : priorité absolue de relecture ; les gardes évitent de republier des défauts connus | M | w2-12, w2-13, w3-04 |

## 2. Ce qui a changé depuis l'audit Opus (vérifié dans l'arbre de travail)

| Constat Opus | État | Preuve |
|---|---|---|
| #2 Grâce illimitée (A1-1) | **Partiellement corrigé** : grâce absolue `LOCK_GRACE_START_MS + 30 j`, `first_run*` ignorés (`C/owner/FeatureGate.kt:37-42`, `R/ActivationCenter.kt:226-228`, `version.properties:13`) ; résiduel = horloge reculée + réinstallation | w1-05 |
| #3 Recul d'horloge gèle le temps (A1-2) | **Corrigé en cœur** : temps monotone `elapsedRealtime` (`C/owner/Keys.kt:78-106`, `C/owner/Activation.kt:187`) ; résiduel = redémarrage avant la sauvegarde horaire (`PolicyHub.kt:15`) ; A1-3 (saut avant < 400 j) non traité | w1-05 |
| #4 Clé compacte d'essai sans `usage` (A1-4) | **Corrigé** : plafond implicite 30 j (`C/owner/Activation.kt:198-203`) | — |
| #7 Essai = liste noire (A1-12) | **Corrigé** : liste blanche `TrialPolicy.routeAllowed` (`C/owner/TrialPolicy.kt:40`), `/stream` réservé au lecteur local (`C/tv/ReceiverServer.kt:273-277`), Bluetooth/USB/SSH gardés ; **49 routes du code ne sont classées dans aucune liste du test** `TrialRoutesTest` | w1-06 |
| A1-6 `parseKey` tout ouvert | **Corrigé** : `TrustedKeyParser` ferme par défaut + avertissement au build (`receiver/build.gradle.kts:33-34`) | — |
| #10 Persistance fragile (A6-1/2/3) | **Corrigé** : `SafeFile` (fsync, `.bak`, validation), ledger dégradé = locations suspendues (`C/lots/RentalLedger.kt:172-186`), balayage contrat par contrat (`C/lots/RentalSweeper.kt:59-60`) ; A6-4 **partiel** : `OwnedLots` câblé mais sans catalogue sur la TV, un lot acheté via bouquet nommé est encore supprimé (`C/lots/OwnedLots.kt:8-10`) | w3-03 |
| #1 KEK publique, #5 lot en clair, #6 SSH, #8 hors ligne, #9 révocation, A1-9 rétrogradation, A2-3 sauvegardes, A5-2 super-admin | **Ouverts, inchangés** | §1 |

## 3. Désaccords explicites avec l'audit précédent

1. **A2-1 « Critique » (KEK dérivée des empreintes).** Le constat est exact, mais la **priorité** ne l'est pas. Le bien protégé est un zip ≤ 3 Mo de leçons en brouillon ; la correction propre (X25519 dans le Keystore, format v2, trois vérificateurs) coûte L. Le bien réellement précieux est la **clé d'émission** (téléphone/bureau) : sa compromission donne le parc entier et, par la portée `REGISTRY`, l'accès SSH distant à toutes les TV enrôlées. Nous recommandons donc **révocation + séquence sur la TV d'abord** (w2-01), et d'**accepter** A2-1/A2-2 jusqu'à l'existence d'un contenu relu et payant, en corrigeant la documentation et le commentaire faux de `C/lots/RentalKeys.kt:70` (w1-13).
2. **A1-1 « Critique ».** Rétrogradé à **Moyen** : il faut désormais désinstaller, reculer l'horloge avant le 2026-10-02 et réinstaller ; et la grâce expire de toute façon au 2026-11-01 d'uptime. Fermable en S (w1-05).
3. **A3 « La TV n'est pas hors ligne ».** D'accord sur la sonde gstatic et le DHT ; **pas d'accord** sur `/api/playurl` : c'est la fonction « Lire en direct » depuis le téléphone, voulue en essai. C'est une **décision du propriétaire** (§6, D2), pas un défaut.
4. **A4-6 Spring Boot 3.5.16 « fin de support probablement passée ».** Non vérifiable hors ligne ; ne pas planifier une migration 4.0 sur cette base.
5. **A5-5 Git LFS pour `content/quiz/lots`.** Vrai mais sans effet sur le produit ; à reporter après le dépôt `castbridge-content`.
6. **Durée de location « dès l'activation » (A7-4).** Nous **recommandons de la figer** telle quelle (RENTAL-LOTS § 5 : vérifiable hors ligne) plutôt que de la rouvrir.

## 4. Ce que l'audit précédent avait manqué (nouveau)

- `release.yml` **signe et publie** des APK (clé via secret `KEYSTORE_BASE64`) **sans `-PrequireActivation=true`** : une exécution par tag publierait une TV déverrouillée ; 0 tag existe (`git tag` vide).
- Le parc est signé avec la **clé de debug du Mac** (`docs/HANDOFF.md:150`) : perte du Mac = plus aucune mise à jour sans réinstallation.
- **Aucune supervision** du VPS, sauvegardes sur le même disque, pas de purge trouvée des événements bruts de télémétrie (TELEMETRY.md § 6 « 13 mois »), `deviceName` (texte libre de l'utilisateur) envoyé en « essentiel » (`R/TvConnect.kt:260`).
- **Android 14 et `foregroundServiceType="connectedDevice"`** (`receiver/AndroidManifest.xml:75`, `R/TvService.kt:154`) : exige une permission Bluetooth/USB accordée au moment de `startForeground` ; sur une installation neuve l'échec est avalé (`:158`) et le service tourne non promu.
- **Démarrage à froid sur le fil principal** : `ActivationCenter.init` (exec `getprop`, lectures `/sys`, vérification Ed25519 de chaque activation, `fsync`) dans `PlayerActivity.onCreate` et `TvService.startCore` ; `ActivationCenter.scanFiles()` (volumes USB) toutes les 5 s sur le fil principal (`R/TvService.kt:174`) ; un `Thread` neuf chaque minute dans Apprendre (`R/LearnActivity.kt:90`) ; attente active dans un gestionnaire HTTP (`R/LearnHub.kt:141-142`).
- **Deux routeurs HTTP** (`C/tv/ReceiverServer.kt:266-432` + `R/TvService.kt:772-866`, ~50 routes sans table) et la **politique d'essai dupliquée par libellé** (`R/ParentalHub.kt:271` `TRIAL_CLOSED_LABELS` vs `C/owner/TrialPolicy.kt:11`).
- **0 test** sur `:receiver`, `:sender`, `:ownerlib` (ni `src/test` ni `androidTest`) : tout le câblage Android ne se vérifie que sur appareil ; `rental-vectors.json` n'est rejoué **ni en Java ni en Python**.
- **UX** : aucun contact du vendeur dans aucune app ; la notice « Location terminée » n'atteint pas l'écran Apprendre (`R/TvService.kt:572` n'affiche que si l'accueil est visible) alors que les fichiers sont supprimés pendant la leçon ; badge de clé 14 sp et `IMPORTANT_FOR_ACCESSIBILITY_NO` (`R/KeyBadgeOverlay.kt:30`) = seul avertissement avant expiration ; tuile Langues affichée en essai mais fermée sans explication ; boutique inexistante (livraison Wi-Fi seulement).
- **Contenu** : deux registres de versions aux clés différentes (`content/learn/lots.json` `maternelle` vs `content/LOT-VERSIONS.json` `learn:mat`), `content/TRIAL-MANIFEST.json` jamais généré (`check` échoue sur 55 cellules Langues vides), alt absent sur 273 figures d'exercices (`C/learn/LessonValidator.kt:77` ne vérifie que `illustration`), règles de « fiche type » appliquées à 67/259 packs seulement (`:92`), aucun pack n'a `license`/`credits`/avertissement.

## 5. Constats par axe

Gravité : **C** critique, **É** élevé, **M** moyen, **F** faible. Effort : S ≤ 1 j, M ≤ 3 j, L > 3 j.

### Axe 1 : architecture et modularité

| id | Grav. | Fichier:ligne | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| AR-1 | M | `R/TvService.kt:71-1000` (≈75 fonctions, 36 champs mutables, 9 responsabilités) | Service d'avant-plan + bootstrap activation + Bluetooth + icônes + sonde réseau (machine d'états propre, 7 `@Volatile`) + lancement d'écrans + stockage + **second routeur HTTP** `extraApi/serverApi` (`:772-866`) + `Device` | Extraire `TvNetMonitor`, `TvExtraRoutes : ApiExtension`, `ScreenLauncher`, `StorageEvents` ; `TvService` reste la racine de composition | M | faible (déplacement pur) |
| AR-2 | M | `R/PlayerActivity.kt:44-1071` | libVLC, télémétrie de lecture, navigation, catalogue d'outils d'accueil (80 lignes), dialogues, SAF, `Screen`+`Player`, touches | `VlcPlayerHost : castbridge.core.tv.Player`, `PlaybackStats`, `HomeToolsCatalog` (pur, testable) | M | faible-moyen |
| AR-3 | M | `C/tv/ReceiverServer.kt:35-1284` (constructeur à 17 collaborateurs, `when` de 167 lignes `:266-432`) | Routage + transfert + stockage + streaming + playlist + HTML admin dans une classe | Table de routes `Route(method, path, scope, handler)` ; `UploadHandler`, `StorageApi`, `StreamHandler` ; test cœur « chaque route est classée par `TrialPolicy` » | L | moyen (bonne couverture cœur) |
| AR-4 | M | `R/ParentalHub.kt:271`, `R/PlayerActivity.kt:511`, `R/HomeScreen.kt:199` | Politique d'essai dupliquée par **libellé** ; libellés de tuiles dupliqués | `HomeTiles` (id → libellé) en cœur ; filtres par id | S | faible |
| AR-5 | M | 16 appels `ActivationCenter.trial()/locked()` dans 9 fichiers (`R/BtServer.kt:122`, `R/UsbImporter.kt:39,49,57,89`, `R/TvService.kt:175,196,257,806`, `R/Games.kt:43`, `R/SshControl.kt:43`…) | Porte dispersée | Un `TvGateSnapshot` injecté dans `routeGuard`, `acceptFile`, `UsbImporter`, `Games` | M | moyen |
| AR-6 | F | `C/net/JsonLite.kt`, `C/quiz/Json.kt`, `C/dl/Json.kt` ; `org.json` dans 4 fichiers sender ; 59 gabarits JSON à la main dans `R/` | Trois analyseurs JSON en cœur, schémas bibliothèque/stockage dupliqués téléphone/TV (`S/TvLibraryScreen.kt:53` vs `C/tv/Library.kt:46`) | Un seul `JsonLite` ; `LibraryItem.parse`/`StorageSnapshot.parse` en cœur avec test aller-retour | S | faible |
| AR-7 | F | `C/trust/ResilientCall.kt`, `C/learn/LearnQuiz.kt`, `C/curriculum/LevelPick.kt`, `C/owner/PhoneConsole.kt` (sauf `RegistryStore`), `C/xfer/Lanes.kt`, 20+ types sans référence, 16 drawables non référencés | Code mort embarqué | Supprimer ; déplacer les doublures `Memory*` en `src/test` | S | faible |
| AR-8 | M | `android/receiver/src/test`, `android/sender/src/test` : inexistants ; `proguard-rules.pro:2,8` garde tout `org.videolan.**` et `org.apache.sshd.**` | Aucun test JVM d'app ; MINA non réduit = levier de taille d'APK | Continuer l'extraction vers le cœur (`homeTools()`, `menuItems()`, `netSummary()`, aides de `QuizActivity`) ; `testImplementation(kotlin("test"))` dans les apps ; affiner les `-keep` ; essayer `libvlc` au lieu de `libvlc-all` | M | faible |

### Axe 2 : sécurité et modèle de licence

| id | Grav. | Fichier:ligne | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| SE-1 | **É** | `R/ActivationCenter.kt:105`, `C/owner/FeatureGate.kt:82-85`, `C/owner/Activation.kt:134`, `C/owner/License.kt:215` (`RevocationNotice.verify` jamais appelé par la TV) | Révocation et séquence inopérantes sur la TV | Persister `SeqState` et `RevocationState` (`SafeFile`), accepter un `cbr1` par fichier USB, Bluetooth et `POST /api/activation/install` ; clé de secours dans l'anneau | M | moyen |
| SE-2 | **É** | `receiver/AndroidManifest.xml:56-57` (pas d'`allowBackup`), `res/xml/backup_rules.xml` ; `sender` idem ; `ownerlib/.../OwnerStore.kt:14,18,21` (coffre inclus malgré le commentaire) | `adb backup`/restauration rembobine locations et essai ; coffre propriétaire dans la sauvegarde Google | `allowBackup="false"` sur les deux apps ; exclusions explicites | S | nul |
| SE-3 | **É** | `R/ActivationCenter.kt:226-228`, `C/owner/FeatureGate.kt:52-53` (la grâce n'applique pas `TrialPolicy`) | Résiduel de grâce par horloge reculée + réinstallation | Refuser la grâce quand `clock.txt` est absent et `firstInstallTime < lockStart` ; `graceDays=0` par défaut une fois le parc activé (**décision D1**) | S | faible |
| SE-4 | **É** | `C/owner/Keys.kt:86-90`, `R/PolicyHub.kt:15` | Uptime non persisté : redémarrer < 1 h avec horloge reculée fige les plafonds ; saut avant < 400 j cru (A1-3) | Compteur d'uptime cumulé persisté toutes les 5-10 min, expiration au premier de (date, uptime) ; règle AHEAD 45 j appliquée à `TvGate` | M | faible |
| SE-5 | **É** | `sshd/.../TvSshServer.kt:48,122,125` ; `R/SshControl.kt:43` | Shell et exec livrés en production (PIN suffit) | Release : SFTP limité au dossier média, ni shell ni exec (sauf `cbdev` interne) ; drapeau `castbridge.sshShell` | M | faible |
| SE-6 | M | `C/tv/ReceiverServer.kt:459,78` ; aucun `Host` ; `R/UpdateInstaller.kt:133` | PIN en query, rebinding, rétrogradation forcée | PIN en en-tête seul ; refuser `Host` hors adresses IP privées/locales ; `force` ignoré en release | S | faible |
| SE-7 | M | `R/TvNetDiag.kt:21`, `R/TvService.kt:439`, `C/dl/Aria2Config.kt:80,83` | Sonde Google périodique, DHT/BT à l'écoute | Sonde seulement sur action manuelle ; `enable-dht=false`, `bt-*` off (**décision D3**) | S/M | faible |
| SE-8 | M | `C/owner/TrialPolicy.kt:31` (`/api/playurl` en essai), `LearnApi.kt:56` (`/api/learn/packs/import` sous préfixe autorisé), `TrialRoutesTest` (49 routes non classées) | Liste blanche non prouvée exhaustive | Test cœur générant la table des routes depuis le code ; `DENIED_UNDER_ALLOWED += /api/learn/packs/import` ; playurl = **décision D2** | S | faible |
| SE-9 | M | `C/lots/RentalKeys.kt:70-76`, `C/lots/RentalApi.kt:35-41`, `C/lots/RentalVault.kt:67,75` (`readLot` jamais appelé) | KEK dérivable du fichier `activation` ; lot réécrit en clair | **Accepter** jusqu'à contenu relu payant (**décision D4**) ; corriger commentaire, doc § 11 et test `afterTheKeyIsGone…` | S (doc) / L (fix) | — |
| SE-10 | M | `ownerlib/build.gradle.kts:9-13` ; `OwnerStore.kt:85` (PBKDF2, pas de Keystore) | Hachage super-admin compilé par défaut ; coffre non enveloppé par le Keystore | `noSuperAdmin=true` par défaut, opt-in pour la build propriétaire ; Keystore (M, Android, vague 3) | S | faible |
| SE-11 | M | `C/lots/OwnedLots.kt:8-10`, `R/RentalHub.kt:33` | Lot acheté via bouquet nommé supprimé à l'échéance de sa location | Livrer le catalogue signé à la TV avec l'activation ; `OwnedLots.of(…, catalog)` | S/M | faible |
| SE-12 | M | `C/tunnel/ExpertsList.kt:110` (portée `REGISTRY`) ; `B/tunnel/TunnelService.java:181-186` (essai enrôlé) | La clé qui émet les activations autorise aussi l'accès SSH distant ; les essais s'enrôlent ; pas d'indicateur TV | Portée dédiée `EXPERTS` ou clé distincte ; enrôlement réservé à `production` ; `notAfter` obligatoire (**décision D13**) | S | faible |
| SE-13 | F | `R/TvConnect.kt:260` → `B/devices/Device.java:35` | `deviceName` (texte libre) envoyé en « essentiel », conservé 365 j | Retirer ou hacher | S | nul |
| SE-14 | F | `.gitignore`, `android/core/castbridge-owner-journal.log` (non suivi, non ignoré) | Motifs `*.jks`, `*.pem`, `*.key`, `*.log`, `.env` absents | Compléter `.gitignore` | S | nul |

**Modèle de menace réaliste (synthèse).** (a) Client curieux : rien de nouveau, sauf redémarrer la TV toutes les < 1 h avec horloge reculée pour figer l'essai (fastidieux). (b) Technicien avec clé USB et le PIN affiché à l'écran : réinstallation avec horloge reculée ⇒ grâce tout ouvert jusqu'au 2026-11-01 ; `POST /api/ssh/enable` en production ⇒ shell ⇒ copie des clés de location et réécriture de `clock.txt` ; script PC de 20 lignes ⇒ KEK ⇒ lot déchiffré. Coût 1-4 h ; gain : usage complet gratuit et copie de brouillons ≤ 3 Mo. (c) TV rootée/émulateur : APK re-signée sans `REQUIRE_ACTIVATION` redistribuable (2-8 h) ; aucune clé de signature n'est sur la TV. (d) Téléphone volé avec la console : **le plus grave** (émission illimitée, toutes les clés de location par `masterFrom`, aucune révocation possible côté TV). **Ce qui vaut la peine** : SE-1, SE-2, SE-3, SE-5, SE-6, SE-10 ; **à accepter** pour l'instant : SE-9, aria2 si la fonction est voulue.

### Axe 3 : produit et UX

| id | Grav. | Fichier:ligne | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| UX-1 | **É** | `C/owner/FeatureGate.kt:170-172`, affiché `R/ActivationActivity.kt:77-78` | « [Texte à valider par le propriétaire…] » sur le premier écran du client | Texte définitif (**décision D7**) | S | nul |
| UX-2 | **É** | aucune occurrence de contact/WhatsApp dans `R/` ni `S/` | Le client ne sait pas à qui envoyer le code | `OWNER_CONTACT` au build, affiché sur l'écran d'activation, l'écran de passage en version complète et une tuile « Aide » | S | nul |
| UX-3 | **É** | `R/ActivationActivity.kt:45-47` | Clé reçue par Bluetooth déjà vérifiée mais exige un second « Valider la clé » | Accepter directement, message « Clé reçue du téléphone : activée » | S | nul |
| UX-4 | **É** | `C/owner/Activation.kt:189-193`, `R/KeyBadgeOverlay.kt:30` (14 sp, non accessible) | À l'échéance, la TV se verrouille sans explication ; seul avertissement = badge illisible à 3 m | Écran « Votre clé s'est terminée le … » + étapes ; rappels J-7/J-3/J-1 sur l'accueil ; badge ≥ 20 sp accessible ; **décision D6** sur le mode dégradé | M | faible |
| UX-5 | **É** | `S/RentalDeliveryActivity.kt:48-50,64-92` | Pas de boutique ; Wi-Fi obligatoire ; `produit@période` à taper | Boutique phase 0 (w2-06) ; livraison par Bluetooth (transport existant `LotsRuntime`) | L | moyen |
| UX-6 | M | `R/TvService.kt:572`, `R/RentalSweeper` pendant leçon, `R/LearnActivity.kt:539` | « Location terminée » perdue si Apprendre est ouvert ; tuiles vides sans explication ; compteur 12 h invisible dans Apprendre | Router la notice vers `LearnHub.screen` ; en-tête « Essai : 3 h 20 restantes » ; plein écran « Temps d'essai terminé » | M | faible |
| UX-7 | M | `R/LearnViews.kt:84`, `R/LearnActivity.kt:575,579,611` (19 px à 720p) ; `C/learn/BaseContent.kt:26` ; `R/LearnActivity.kt:573-575` (chemin `Android/data/...` affiché) | Trop petit à 3 m ; jargon « lot », « contenu de base » ; chemin technique | Planchers 24/26 px ; « 2 leçons d'aperçu. Pour toute la classe : … » ; chemin sous « Détails techniques » | S | nul |
| UX-8 | M | `R/ActivationActivity.kt:130` + raisons de `Rejection` | Messages de refus techniques (« Numéro de séquence déjà vu ») | Table `Rejection → phrase + action` en cœur | M | nul |
| UX-9 | M | `S/ActivateTvActivity.kt:123,154,63,137,167-168` | Ordre inversé (coller avant demander), phrases longues, texte WhatsApp = dump technique | Réordonner, préfixer un message humain | S | nul |
| UX-10 | M | `R/PlayerActivity.kt:517`, `C/owner/TrialPolicy.kt:11` ; `R/LanguesActivity.kt:147,221` | Tuile Langues ouverte en essai mais fermée ; « pas encore » dans le flux | Tuile grise « Version complète requise » ; « Langues (aperçu) » | S | nul |
| UX-11 | M | `S/TvHome.kt:178,449`, `S/UploadService.kt:185,191` ; 3 écrans « Diagnostic » | Exceptions brutes affichées ; trois diagnostics | `TvReachability` → une raison en français ; écran « Dépannage » unique | M | faible |
| UX-12 | M | `receiver/res/values/strings.xml` : 2 chaînes ; sender : aucune ; ≈ 878 `Text("` + 134 chaînes en dur | Aucune voie d'i18n ; « production » = jargon ; « lots locatifs » | Dire « version complète », « leçons louées » ; i18n à reporter (budget) | S | nul |
| UX-13 | F | `receiver/res/values/cb_colors.xml` : `text_low` ≈ 4,0:1 ; `R/ActivationActivity.kt:68,87` #7B849C ≈ 3,6:1 à 15-16 sp | Contraste sous 4,5:1 pour le petit texte | `cb_text_medium` pour tout texte < 24 sp | S | nul |

### Axe 4 : monétisation et mécanique commerciale

| id | Grav. | Fichier:ligne | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| MO-1 | **É** | `B/licenses/ProductService.java:17`, `V50:25-27`, `C/lots/Entitlement.kt:13` | Aucun prix, aucune commande, aucun reçu, aucun rappel de renouvellement, aucun compte revendeur | Table `shop_order` + `shop_price` (phase 0 manuelle), page admin « Commandes » avec confirmation TOTP, émission automatique à la confirmation (**décisions D8, D9**) | L | moyen |
| MO-2 | **É** | `docs/RENTAL-LOTS.md:133-141`, `C/lots/RentalKeys.kt:26-27`, aucun `master` dans `backend/` | Locations serveur non implémentées ; secret maître dérivé de la clé de chaque outil | Secret maître **serveur dédié** (fichier secret, jamais la clé de signature) ; le serveur scelle lui-même les lots ; port Java de `RentalKeys` sur les vecteurs `box`/`seal` | L | moyen |
| MO-3 | M | `C/owner/Activation.kt:189-193` | Fin du plafond = verrouillage total, achats et médias compris | **Décision D6** : mode dégradé (médias + achats conservés, 7 j de préavis) | M | faible |
| MO-4 | M | `docs/ACTIVATION-FORMAT.md:93-95`, `B/licenses/ActivationService.java:68-69` | « Durée de la clé » ≠ droit de contenu : ambiguïté achat perpétuel/abonnement | Grille tarifaire à deux dimensions (clé / contenu), libellés « version complète N jours » | S | nul |
| MO-5 | M | `B/licenses/LedgerService.java:352-355` (AUTO relève les quotas), `AbuseService.java:45-101` (alerte, jamais bloque) | Anti-partage comptable seulement ; pas de client de liste de révocation côté TV (SE-1) | REVIEW par défaut ; compteur d'essais par code d'appareil ; cap commandes/appareil/jour | S | faible |
| MO-6 | M | — | Paiement Cameroun : phase 0 virement MoMo/OM + référence saisie ; phase 1 agrégateur (CinetPay, Campay, Monetbil, NotchPay, Flutterwave : frais ≈ 2-4 %, KYC entreprise, **à vérifier**) ; phase 2 SDK | Démarrer phase 0 maintenant (aucune dépendance) | — | — |

### Axe 5 : contenu (qualité et échelle)

| id | Grav. | Fichier | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| CO-1 | **É** | `content/validation/` absent ; `content/qa/` absent ; `docs/LEARN-REVIEW.md` (8 135 lignes non actionnables) ; `LessonModel.kt:11` vs `CONTENT-VALIDATION.md` vs `cbvalidate.py:39` (3 vocabulaires) | Aucune relecture commencée ; 488 fiches de brouillon embarquées dans l'APK | Boucle CSV (`cbvalidate.py index --lot` → enseignant → `import-csv` → `apply`), relire d'abord les 2 premières fiches de chaque pack ; un seul vocabulaire d'état (**décision D16**) | M | faible |
| CO-2 | **É** | `C/learn/LessonValidator.kt:77,92,104-112` ; 273 figures sans `alt` ; 0 `license`/`credits`/avertissement ; `outOf == 20` non imposé ; 8 `epreuve-blanche*.json` rangés en chapitres | Porte qualité incomplète | Porte v1 : méta de pack, alt partout, fiche type pour tous, épreuves, doublons, longueur, orthographe (hunspell, avertissement) | M | faible |
| CO-3 | M | `content/learn/lots.json` vs `content/LOT-VERSIONS.json` ; `tools/publish-content.sh:31-37` (quiz sauté si python < 3.12) ; `content/TRIAL-MANIFEST.json` absent | Deux registres de versions ; manifeste d'essai jamais produit | Source unique ; `select --only learn,quiz` commité ; échec au lieu d'avertissement avec `--go` | M | faible |
| CO-4 | M | `docs/curriculum/*.md` (descripteurs, pas de chapitres) ; 142/259 packs « à vérifier » | « Porte 70 % programme » impossible sans listes de chapitres officielles | Un fichier par (classe, matière) à partir des PDF MINESEC/MINEDUB/GCE (**décision D15**) ; porte calculée par `cbvalidate.py` | M | faible |
| CO-5 | M | `content/MEDIA-MANIFEST.json` (0 média) ; `tools/langues/tts_synth.py` (kokoro/piper non testés) ; `R/LanguesActivity.kt` (aucun badge `synthetic`) | Aucun audio ; marque « voix de synthèse » exigée mais non affichée | Kokoro/Piper sur le Mac du propriétaire pour la famille libre ; badge TV ; enregistrements humains pour les lots réservés | S + propriétaire | faible |
| CO-6 | M | `content/langues/zh-a0-salut-fr` (1 unité) ; pas de générateur de leçons Langues | Tranche minimale = 1 langue × A0-A1 ≈ 18 unités ≈ 8-12 h d'agent + audio | Choisir la première langue (**décision D14**) | L | faible |
| CO-7 | F | `C/learn/Progress.kt:19,26`, `LearnMerge.kt` | Renommer un id de fiche perd la progression ; aucune règle écrite | « ids immuables ; réécriture = nouvel id » dans `docs/LEARN.md` ; contrôle d'ids retirés dans `LearnTool lots --update` | S | nul |

### Axe 6 : fiabilité et exploitation

| id | Grav. | Fichier:ligne | Constat | Recommandation | Effort | Risque |
|---|---|---|---|---|---|---|
| OP-1 | **É** | `backend/README.md:259-266`, `backup.sh` ; aucune alerte | Sauvegardes sur le même disque ; aucune supervision | Copie hors site chiffrée (rclone/restic), sonde externe gratuite, pings de cron, script hôte 10 min, exercice de restauration trimestriel | S/M | nul |
| OP-2 | **É** | `.github/workflows/release.yml:57-80` | Signature + publication en CI, build non verrouillée | `workflow_dispatch` + build seule, jamais de signature ni de publication | S | nul |
| OP-3 | **É** | `docs/HANDOFF.md:150` | Clé de debug pour le parc | Keystore de release sauvegardé hors site ; migration unique documentée (**décision D12**) | M | moyen |
| OP-4 | M | `C/lots/RentalVault.kt:38-39,69-70`, `C/lots/TvLotStore.kt:251-252`, `C/learn/Progress.kt:250-251`, `C/tv/Storage.kt:142,147` | Écritures sans fsync (ou en place) | `SafeFile`/`AtomicFile` | S | faible |
| OP-5 | M | `receiver/AndroidManifest.xml:75`, `R/TvService.kt:154-158` | Type de service d'avant-plan exigeant une permission runtime sur API 34 ; échec avalé | `specialUse` (+ sous-type) ou `mediaPlayback` ; journaliser `e.message` | M | moyen |
| OP-6 | M | `R/PlayerActivity.kt:95`, `R/TvService.kt:191-215,174` ; `R/LearnActivity.kt:90` ; `R/LearnHub.kt:141-149` ; 14 `Thread{}` non poolés en receiver, 20 en sender | Travail bloquant sur le fil principal au démarrage ; threads à la minute ; attente active dans HTTP | `init` sur `bg` avec `ready` ; un exécuteur partagé ; `CountDownLatch` | M | moyen |
| OP-7 | M | `backend/.../db/migration` : V61 non suivi ; `wip/external-ai-changes` porte un `V4__bt_address.sql` en collision | Numérotation Flyway sans règle écrite | Règle « plus haut + 1 », plages réservées documentées ; jamais fusionner la V4 externe | S | nul |
| OP-8 | M | `docs/RELEASES.md` absent (cité par `version.properties:2`) ; 0 tag ; sha256 à la main dans HANDOFF | Processus de release non écrit | Rédiger (plan § 7 du cahier w1-08) ; `SHA256SUMS` à côté des APK ; tags `tv-x.y.z` | S | nul |
| OP-9 | F | `docs/TELEMETRY.md:142` vs `B/telemetry` (aucun `@Scheduled` de purge) | Purge des événements bruts non trouvée | Tâche planifiée ou documenter l'agrégation | S | nul |
| OP-10 | F | `docs/LICENSE-ADMIN.md:229` | Rotation de la clé serveur : un paragraphe, pas de procédure | § 3.6 « Rotation » (nouveau `kid`, chevauchement, révocation de l'ancien) | S | nul |

### Axe 7 : stratégie de test

| id | Grav. | Fichier | Constat | Recommandation | Effort |
|---|---|---|---|---|---|
| TE-1 | **É** | `android/receiver`, `android/sender`, `android/ownerlib` : 0 test | Tout le câblage Android se vérifie sur appareil seulement | Campagne de 40 étapes sur la TV GaiaOS (cahier w3-14) ; extraction de logique pure vers le cœur (w3-11) | M |
| TE-2 | **É** | `tools/activation/rental-vectors.json` (41 cas) non rejoué en Java ni en Python ; `server-issued.json` non rejoué en Python ; pas de `requirements` pour `cryptography` | Trois implémentations non prouvées alignées sur les locations | `RentalVectorsTest.java`, `verify_vectors.py` étendu, `tools/requirements-dev.txt` | S |
| TE-3 | M | `TrialRoutesTest.kt` (36 + 54 routes à la main ; 49 routes orphelines) | Liste blanche non prouvée exhaustive | Génération de la table des routes depuis le code | S |
| TE-4 | M | `sshd/.../TvSshServerTest.kt:59` (port TOCTOU, `FailureTracker` sur horloge murale, identités `~/.ssh` chargées), `TrustTest.kt:27` (`SecureRandom`), `ChessRelayTest.kt:97-103` (port TOCTOU) | Trois tests instables connus | Port 0 lié **dans** le serveur, horloge injectée, `EMPTY_KEYS_PROVIDER`, 404 explicite du faux relais | S |
| TE-5 | M | tests manquants : rollback à travers un redémarrage, `activations.txt` tronqué (logique dans `:receiver`), exclusions de sauvegarde, clé révoquée par route, lot > 3 Mo par `/api/lots/upload`, verrou PIN par 6 requêtes HTTP, `Host`, fenêtre 12 h après réinstallation | Tests négatifs absents | Ajoutés dans les cahiers correspondants | S/M |
| TE-6 | F | 57 `Thread.sleep` dans 20 fichiers, 46 fichiers avec vrais ports | Suite lente et sensible à la charge | Horloges injectées progressivement ; `maxParallelForks` prudent | M |

### Axe 8 : juridique, vie privée, licences (signalement, pas un avis juridique)

| id | Grav. | Constat | Piste |
|---|---|---|---|
| LE-1 | **É** | Langues sous CC BY-SA 4.0 (`docs/LANGUES.md:440-442`) : « jamais chiffré, jamais verrouillé » **vs** option A « rien sans clé » (`docs/TRIAL-EDITION.md:195-208`) | **Décision D10** : la famille `free` doit être lisible sans activation (au moins l'archive publique annoncée au § 13) ; la TV peut garder son verrou d'application mais pas enfermer ce contenu |
| LE-2 | **É** | Tunnel SSH inverse permanent (`docs/REMOTE-TUNNEL.md:107-109`) ; essais enrôlés ; pas d'indicateur | Texte de divulgation à l'activation + CGU + journal ; réservé à `production` ; relecture juriste avant activation (**D13**) |
| LE-3 | M | Consentement télémétrie donné sur la TV par n'importe qui (enfant possible) ; `deviceName` ; serveur hors du Cameroun ; loi 2024/017 « à valider » | Consentement par le parent (téléphone, code parental) ; retirer `deviceName` ; registre des traitements |
| LE-4 | M | Aucune CGV/CGU de vente ni de location (essai unique, suppression à l'échéance, horloge fausse = suspension, remboursements) | Rédiger avant toute vente (brouillon w3-13, validation juriste) |
| LE-5 | M | 0 avertissement sur les 24 packs de droit et les fiches santé ; titularité du contenu IA avant CC BY-SA | Champ `disclaimer` par pack (porte CO-2) ; avis juriste |
| LE-6 | F | aria2 (GPL) sans texte de licence ni offre de sources (`jniLibs/*/libaria2c.so`) | `COPYING` + offre, ou retrait |

## 6. Décisions qui n'appartiennent qu'au propriétaire

| # | Question | Recommandation |
|---|---|---|
| D1 | Mettre `-Pcastbridge.graceDays=0` par défaut pour la build verrouillée dès que la TV du propriétaire et les bêta-testeurs ont une clé ? | **Oui**, dès que le parc existant est activé ; en attendant, refuser la grâce sans `clock.txt` (w1-05) |
| D2 | `/api/playurl` (« Lire en direct » depuis le téléphone) reste-t-il ouvert en essai, bien qu'il fasse sortir la TV vers Internet ? | **Oui** (c'est l'offre d'essai annoncée : streaming) ; mais sonde gstatic et DHT fermés |
| D3 | Téléchargements aria2 dans la build distribuée : retirer, ou garder HTTP seul (DHT/BitTorrent coupés) ? | **Garder HTTP seul** ; retirer magnet/torrent ; ajouter `COPYING` |
| D4 | Accepter pour l'instant la KEK dérivée des empreintes et le lot loué en clair (contenu = brouillons ≤ 3 Mo) ? | **Oui, documenté** ; reprendre (Keystore X25519) quand un contenu relu et payant existe |
| D5 | Shell SSH en production : retirer (SFTP média seul) ? | **Oui** ; garder le shell dans les builds de test |
| D6 | Fin du plafond d'une clé de **production** : verrouillage total (actuel) ou mode dégradé (lecteur, bibliothèque, achats conservés ; contenu loué suspendu) avec préavis 7 j ? | **Mode dégradé** : un client qui a payé ne doit pas perdre ses propres vidéos |
| D7 | Texte définitif de l'avis d'usage et **numéro WhatsApp / contact** à compiler dans les apps | Fournir les deux ; sans contact, le parcours d'activation est incomplet |
| D8 | Qui détient le secret maître des locations : secret serveur dédié (recommandé) ou secret commun aux trois outils ? | **Secret serveur dédié**, jamais la clé de signature ; outils de bureau = tests seulement |
| D9 | Paiement : valider la **phase 0** (virement MoMo/OM + référence saisie + confirmation console), fournir la **grille de prix** (XAF) et le numéro marchand ; choisir plus tard un agrégateur | **Phase 0 maintenant** ; agrégateur après 50 ventes |
| D10 | Langues (CC BY-SA) : la famille `free` est-elle lisible **sans clé** sur la TV, et l'archive publique est-elle publiée ? | **Oui aux deux** ; sinon changer la licence des contenus originaux (réservée) avant production |
| D11 | Durée de location dès l'activation : figer ? | **Figer** (vérifiable hors ligne) |
| D12 | Keystore de release : créer maintenant et planifier la réinstallation unique du parc ? | **Oui**, avant 10 TV |
| D13 | Tunnel SSH TV : construire le client TV ? Si oui, réservé aux clés `production`, texte de divulgation, indicateur à l'écran | **Reporter** jusqu'au texte juridique ; préparer la divulgation |
| D14 | Première langue de « Langues » (tranche A0-A1) | **Anglais** (marché), puis chinois |
| D15 | Fournir les PDF des programmes officiels (MINESEC, MINEDUB, GCE Board) pour la porte « 70 % » | Nécessaire ; sans eux la porte reste un proxy |
| D16 | Recruter 2-3 enseignants (maths, français, anglais) pour relire d'abord les 488 fiches embarquées | Oui, octobre |
| D17 | « Apprendre » reste ouvert en essai avec le contenu de base ? | **Oui** : c'est l'entonnoir |
| D18 | Questions de quiz *calculées* en `review` jouables (actuel) ? | **Oui** ; les factuelles attendent la relecture |
| D19 | Fusion `integration/agents` → `main`, tags, commit des 81 fichiers en attente (dont V61, tunnel, catalogue signé) | Commiter d'abord par thèmes (sécurité / tunnel / catalogue), puis fusionner après la campagne TV |
| D20 | Retirer `deviceName` de la télémétrie et donner le consentement par le téléphone du parent ? | Oui |

## 7. Ce qui est solide (vérifié)

- **Cœur pur et testé** : `C/` sans aucun `import android` ; 1 865 tests cœur, 193 serveur, 32 bureau, 119 Python ; vecteurs partagés Kotlin/Java/Python pour les activations (131 cas).
- **Format `cbx1`** : forme canonique rejouée octet pour octet, Ed25519, portées par type, fenêtre 48 h sans exception, k parmi n, décodage en échec fermé, genre de droit inconnu conservé ; `TrustedKeyParser` fermé par défaut.
- **Locations** : balayage idempotent et rejouable, clé détruite avant fichiers, pierre tombale, ledger dégradé = suspension, `SafeFile` avec `.bak`, journal sans donnée personnelle, horloge `BEHIND/AHEAD` suspend au lieu de supprimer.
- **Essai** : liste blanche par défaut fermée, `/stream` réservé au lecteur local, Bluetooth n'accepte que les lots, USB et SSH fermés, badge sur chaque écran.
- **API TV** : PIN en temps constant, jetons hachés, `safeName`, corps bornés, pas de CORS, blocage par IP, routes PIN-only pour activation/SSH/APK.
- **Serveur** : BCrypt 12, TOTP, CSRF, CSP, cookies stricts, actuator non publié, `FOR UPDATE` sur les postes, audit chaîné, secrets par fichiers, validation des zips, modules sensibles **éteints par défaut** (licences, ordres, tunnel) ; `deploy.sh` avec sauvegarde, verrou, santé et retour arrière ; MySQL isolé, `cap_drop ALL`, rotation des journaux.
- **Contenu** : 32 scopes couverts, 0 id dupliqué, 0 prérequis orphelin, budgets vérifiés (`LotBudget`, `StarterBudget`, 3 Mo/lot), lots signés, installation atomique avec retour arrière, progression fusionnable.
- **Outillage** : `tools/rental-test` prouve la chaîne location de bout en bout sur émulateur ; `tools/trial-edition` déterministe ; `tools/core-harness` permet les tests sans SDK Android.

## 8. Graphe de dépendances du travail

```
Vague 1 (sécurité rapide, fiabilité, hygiène) — toutes indépendantes entre elles
  w1-01 sauvegardes+superadmin   w1-02 fsync   w1-03 API TV   w1-04 hors ligne   w1-05 horloge+grâce
  w1-06 liste blanche exhaustive  w1-07 CI      w1-08 RELEASES/.gitignore        w1-09 tests instables
  w1-10 vecteurs Java/Python      w1-11 hygiène serveur        w1-12 supervision/sauvegardes   w1-13 doc locations honnête

Vague 2 (produit, architecture, monétisation phase 0)
  w2-01 révocation TV      ← w1-05 (ActivationCenter stabilisé)
  w2-02 SSH release        ← w1-09 (TvSshServerTest fiabilisé)
  w2-03 UX activation      ← D7 (texte, contact)
  w2-04 expiration/badge   ← D6
  w2-05 Apprendre TV       (indépendant)
  w2-06 boutique téléphone ← D8, D9, w2-10 (BLOQUÉ)
  w2-07 TvService découpé  ← w1-04 (TvService netTick)
  w2-08 exécuteurs         (indépendant)
  w2-09 téléphone dépannage (indépendant)
  w2-10 serveur boutique   ← D8, D9 (BLOQUÉ)
  w2-11 Langues TV         (indépendant)
  w2-12 porte qualité v1   (indépendant)
  w2-13 pipeline contenu   (indépendant)
  w2-14 hygiène cœur       (indépendant)
  w2-15 tunnel/experts durcissement ← D13 (partiel)

Vague 3 (échelle, architecture profonde, campagne)
  w3-01 ReceiverServer table de routes ← w2-07, w1-06
  w3-02 démarrage à froid + FGS        ← w2-01, w2-07
  w3-03 OwnedLots catalogue + cap 3 Mo ← w1-02
  w3-04 relecture enseignants          ← w2-12, D16
  w3-05 Langues MVP                    ← D14 (BLOQUÉ)
  w3-06 programmes officiels + porte 70 % ← D15 (BLOQUÉ partiel)
  w3-07 contenu lycée mince            ← w2-12
  w3-08 contenu primaire anglophone + droit ← w2-12
  w3-09 relais de révocation en ligne  ← w2-01
  w3-10 clé de release + process       ← D12, w1-08
  w3-11 tests d'app / extraction cœur  ← w2-07
  w3-12 allègement APK                 ← w3-11
  w3-13 juridique (brouillons)         ← D7, D10, D13
  w3-14 campagne TV réelle             ← vague 1 + w2-01..05 (propriétaire présent)
```

## 9. Ce qui n'a pas pu être vérifié

- Comportement réel sur la TV GaiaOS de tout ce qui est non commité (grâce absolue, horloge monotone, liste blanche) : aucune compilation ni installation faite ici.
- Contenu de `~/.castbridge-signing/activation-trusted-keys.txt` (portées réelles des clés embarquées) : non lu, par consigne.
- Existence d'APK bêta non verrouillées chez des clients.
- Alignement Java/Python du plafond implicite 30 j des clés compactes (`implicitUsageEnd`).
- Arrêt du serveur SSH quand une clé d'essai remplace une production.
- Fin de support de Spring Boot 3.5 ; frais réels des agrégateurs de paiement (ordres de grandeur seulement).
- Que `MySqlContainerTest` s'exécute bien (et n'est pas sauté) dans `backend.yml`.
