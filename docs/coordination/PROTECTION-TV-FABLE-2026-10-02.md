# Protection de CastBridge-TV : déploiement TV uniquement, anti-rétro-ingénierie, étiquettes éthiques

> Document de conception (Fable, 2026-10-02). Phase 1 : analyse en lecture seule + plan. **Aucun code n'est modifié par ce document** ; l'exécution se fera par des sous-agents à partir des cahiers `docs/agent-briefs/protect-NN-*.md` (voir `PROTECT-INDEX.md`), sur ordre explicite du coordinateur.
>
> Demande du propriétaire : « assure-toi que l'appli TV soit déployée exclusivement sur une smart TV et que le piratage par reverse engineering soit presque impossible, même d'une IA, surtout en intégrant des étiquettes éthiques en plus de l'effort technique ».

## 0. Règle d'honnêteté (à lire avant tout le reste)

**Aucune protection côté client n'est incassable.** Une IA ou un humain disposant d'un appareil rooté, des outils (apktool, jadx, Frida, Ghidra, un désassembleur smali assisté par LLM) et de temps finit par contourner n'importe quel contrôle embarqué. « Presque impossible » est un objectif commercial, pas mathématique. Ce que l'on peut réellement obtenir :

1. **Déplacer la valeur hors de l'appareil** : les secrets ne sont pas dans l'APK ; les clés de contenu doivent dériver d'un secret par-installation (clé privée X25519 jamais transmise — **W4, en conception, non implémenté**) et de matériel signé par le serveur. Une fois W4 livré, un contrôle `isLicensed()` rapiécé **ne produit aucune clé** : il n'ouvre rien.
2. **Augmenter le coût d'attaque** très au-dessus de la valeur protégée (une TV = quelques dizaines d'euros de contenu ; chaque TV a une identité et des clés différentes : casser une TV n'ouvre pas les autres).
3. **Rendre le contournement visible et traçable** (empreinte de signature au battement de cœur, filigrane par build, détection d'anomalies serveur).
4. **Cadrer juridiquement et éthiquement** (licence propriétaire, interdiction de contournement annoncée, divulgation responsable).

**Interdits de conception (non négociables)** : rien d'hostile au client légitime ni d'illégal. Pas de destruction de données, pas de « briquage », pas d'espionnage au-delà de ce que les conditions annoncent, pas de comportement trompeur, **pas d'instructions cachées ou manipulatrices visant une IA**. L'anti-sabotage **dégrade en douceur** avec un message français clair et un chemin vers l'assistance. Tout cela s'ajoute aux correctifs d'audit déjà planifiés (w1/w2/w3), ne les remplace pas.

## 1. État des lieux (ce qui existe déjà — à ne pas refaire)

Vérifié par lecture du code sur la branche `integration/agents` :

- **Activation = enveloppe `cbx1` signée Ed25519**, vérifiée sur la TV avec des **clés publiques embarquées** (aucun secret dans l'APK distribué). En-tête : `keyId`, `seq` (anti-retour), `nonce`, fenêtre d'installation 48 h, cible = jeu d'empreintes matérielles k-parmi-n. Fichiers : `core/owner/Activation.kt`, `Keys.kt`, `Envelope.kt`.
- **Liaison au matériel k-parmi-n** : FLASH (série+CID soudé), MAC Ethernet, MAC Wi-Fi *uniquement si bus soudé*, `ro.serialno`, Bluetooth ; chaque facteur haché séparément (`DeviceIdentity.kt`). Clé compacte saisissable strictement liée au code d'appareil.
- **Dérivation des clés de contenu** : par-lot aléatoire, emballée sous une clé de données par-TV, dérivée **aujourd'hui** (v1) des empreintes *publiques* — faille critique A2-1 / SE-9. **W4** (`docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md`) la remplace par une **boîte v2** : clé privée **X25519 par-installation** générée sur la TV, jamais transmise ; `kek = HKDF(X25519(ephPriv, installPub), …)`. **Attention : W4 est EN COURS DE CONCEPTION par un autre agent Fable, PAS implémenté.** C'est une **dépendance de conception** de ce plan : la dépendance cryptographique (« un contrôle rapiécé ne produit aucune clé ») n'est effective **qu'une fois W4 livré**. D'ici là, les couches 2 à 5 valent par elles-mêmes mais la couche 1 reste au niveau v1.
- **Horloge durcie** (monotone + plafond d'uptime cumulé) contre le recul d'horloge (`TvClock`, w1-05).
- **Révocation** (clés + postes) signée, à persister (w2-01).
- **Signaux de classe d'appareil déjà COLLECTÉS** (`UiModeManager` TELEVISION, `FEATURE_LEANBACK`, `SUPPORTED_ABIS`, tactile, téléphonie) dans `TvConnect.kt` / `DeviceReport.kt` — **mais seulement envoyés au serveur, jamais utilisés pour bloquer l'app**.
- **Vérification de signature APK déjà codée** (`apkContentsSigners`) dans `UpdateInstaller.kt` / `PhoneUpdater.kt` — **mais seulement pour valider un APK de mise à jour, pas pour l'auto-intégrité**.
- **R8 + rétrécissement des ressources activés** en `release` ; `proguard-rules.pro` existe (w3-12 l'affine). Splits ABI `armeabi-v7a` + `arm64-v8a`.
- **Verrou d'activation** = interrupteur de compilation `REQUIRE_ACTIVATION` + liste blanche `LOCKED_WHITELIST` épinglée par test (`FeatureGate.kt`). Module `devbridge` = outil SSH de test du propriétaire ; `BuildConfig.DEBUG` + `test-factors.txt` pour l'émulateur.
- `allowBackup="false"` déjà posé (w1-01 / SE-2 complètent les règles d'exclusion).

**Cibles matérielles (correction du propriétaire)** : **tous** les systèmes de smart TV compatibles — Android TV, Google TV, Fire TV / Fire OS, GaiaOS et autres firmwares TV basés sur Android. La TV de référence est GaiaOS 32 bits 720p, mais **aucune empreinte GaiaOS ni armeabi-v7a ne doit servir de critère** : les signaux de classe d'appareil sont génériques (UiModeManager TELEVISION, fonctionnalité leanback, absence de téléphonie, périphériques d'entrée/télécommande, classe d'affichage), **indépendants de l'ABI**. Le budget 32 bits / 1 Go reste une **contrainte de performance**, pas un critère d'identification.

**Conclusion :** la « valeur hors de l'appareil » est en place pour les activations (signées, identité matérielle) et **en cours de conception** pour les clés de contenu (boîte v2, W4). Les trois manques réels sont (A) le **TV-only imposé** (manifeste + contrôle d'exécution), (B) la **friction anti-RE côté binaire** (épinglage de signature, auto-intégrité, heuristiques d'environnement, filigrane, registre d'empreintes au serveur), et (C) les **étiquettes éthiques** (fichiers LICENSE-NOTICE / NOTICE / SECURITY / ETHICS, qui **n'existent pas** aujourd'hui).

## 2. Modèle de menace (profils, capacités, coût)

| Profil | Capacités | Ce qu'il vise | Coût actuel pour lui | Coût visé |
|---|---|---|---|---|
| **P1 — Curieux / client bricoleur** | Installer l'APK sur un téléphone, « clear data », rejouer un fichier d'activation | Débloquer gratuitement sa propre TV / l'utiliser sur un téléphone | Faible | **Élevé** : refus TV-only + grâce absolue + essai plafonné |
| **P2 — Revendeur de contenu** | Rooter *une* TV, extraire les lots déchiffrés en clair, les rediffuser | Revendre le contenu | Faible (A2-1 : fichier + lot = lisible hors TV) | **Élevé** : boîte v2 (clé privée par-install), lots chiffrés au repos, filigrane de fuite |
| **P3 — Pirate outillé + IA** | jadx/apktool, LLM pour déobfusquer et patcher le smali, Frida pour hooker à l'exécution | Forger une activation acceptée par *toute* TV ; produire un APK « débloqué » redistribuable | Moyen | **Très élevé** pour une attaque *qui se propage* ; reste **faible** pour débloquer sa *propre* TV (inévitable) |
| **P4 — Attaquant de la clé de signature** | Vol du keystore de release ou d'une clé privée d'émission | Signer des activations/MAJ universelles | Dépend de l'hygiène du secret (hors APK) | **Hors périmètre APK** : dépend de OP-3/D12 (keystore) + révocation (w2-01) |
| **P5 — Chercheur de bonne foi** | Analyse responsable | Signaler une faille | — | Lui offrir un **canal sûr** (SECURITY.md, safe harbour) |

**Principe directeur face à « même une IE / IA » (section 6)** : un LLM accélère énormément la *déobfuscation* et le *patch de contrôle de flux* (P3). Il n'aide presque pas contre un secret *qui n'est pas sur l'appareil*. Donc on investit sur la **dépendance cryptographique** (déjà W4) et la **traçabilité**, pas sur l'obfuscation lourde.

## 3. Plan en couches et effet attendu

### Couche 1 — La valeur vit côté émetteur/serveur (en place pour l'activation ; W4 en conception pour le contenu)
- Activations signées Ed25519, clés publiques seules dans l'APK (en place) ; liaison matérielle k-parmi-n (en place) ; révocation persistée (w2-01, à faire) ; **boîte v2 X25519 par-installation (W4 : dépendance de conception, non implémentée)**.
- **Effet** : patcher un booléen n'ouvre rien (pas de clé dérivée) ; casser une TV n'ouvre pas la suivante.
- **Rôle de ce document** : ne rien dupliquer ; **brancher** l'auto-intégrité et le registre serveur dessus.

### Couche 2 — Déploiement TV uniquement (NOUVEAU : protect-01, protect-02)
- **Paquet** : `uses-feature android.software.leanback required=true` (piloté par un `manifestPlaceholder` pour laisser l'émulateur/dev en `required=false`), tactile et téléphonie `required=false`, activité principale en **`LEANBACK_LAUNCHER` seule** (plus d'icône sur un lanceur de téléphone), sauf en build dev.
- **Exécution** : porte de classe d'appareil à **signaux génériques, indépendants de l'OS et de l'ABI** : `UiModeManager==TELEVISION` **ou** `FEATURE_LEANBACK` **ou** (pas de tactile **et** pas de téléphonie) ; indices secondaires : périphériques d'entrée (télécommande/D-pad sans tactile), classe d'affichage (grand écran, densité TV). **Aucun critère GaiaOS ni armeabi-v7a.** Décision **TV / PAS_TV / INCERTAIN** : PAS_TV → écran courtois « conçue pour une télévision » + contact, aucune donnée touchée ; INCERTAIN → **laisse passer** (PD4, décidé) et signale au serveur ; le serveur peut ensuite **bloquer ou autoriser par ordre signé** (protect-09, ci-dessous).
- **Ordre signé « classe d'appareil »** (protect-09, décision PD4) : réutilise l'enveloppe signée existante (type `order`, scope `POLICY`, `PolicyEngine`/`PolicyHub`), cibles `Device` / `License` / `Any` (= parc des INCERTAIN), actions `block`/`allow`, livré comme les révocations (battement de cœur, relais téléphone, fichier USB), **persisté** sur la TV, **levable** par un ordre ultérieur de `seq` supérieur ; blocage = message FR clair + contact d'assistance, jamais de destruction.
- **Doit rester vert** : émulateur + builds debug (`DEV_BUILD`/`BuildConfig.DEBUG`), l'app **CastBridge Dev** SSH, `adb install` sur la TV de référence. Le téléphone ne doit pas pouvoir se faire passer pour une TV sur le canal propriétaire → l'activation porte déjà `Subject.TV` (vérifié) ; la porte de classe ajoute une 2ᵉ barrière.
- **Effet** : stoppe P1 (usage sur téléphone/box non TV) sans friction pour un vrai téléviseur.

### Couche 3 — Friction anti-RE réaliste côté binaire (NOUVEAU : protect-03/04 ; + w3-12 existant)
- **Épinglage de signature + auto-intégrité** (protect-03) : comparer le certificat de signature courant (`apkContentsSigners`) à une empreinte attendue injectée au build (`EXPECTED_SIG_SHA256`, vide en dev = ignoré). **Contrôles redondants à ≥2 points d'appel** dans le chemin critique (p. ex. `ActivationCenter` + ouverture de lot), **pas de `isLicensed()` unique**. Un APK recompressé/re-signé est détecté.
- **Heuristiques d'environnement** (protect-04) : `debuggable`, émulateur (`Build.FINGERPRINT`/`ro.kernel.qemu`), root/hook (Magisk, Frida, Xposed, `TracerPid` de `/proc/self/status`). **Réponse graduée et inoffensive** : on **n'exécute pas** d'action destructrice ; on **lève un drapeau** transmis au battement de cœur (traçabilité) et, au plus, on refuse l'ouverture de contenu *loué* sur un environnement manifestement hooké, avec message clair. Jamais sur un simple `debuggable` (faux positifs sur vraies TV).
- **Dépendance cryptographique plutôt que contrôle de flux** : réaffirmer W4 — les clés de lot dérivent de la clé privée par-install + matériel signé ; un patch ne fabrique pas la clé.
- **Obscurcissement** : R8 full-mode est déjà là ; w3-12 affine `proguard-rules.pro`. Voir §5 « ce qu'il ne faut PAS faire » pour le chiffrement de chaînes et le natif.
- **Effet** : élève le coût de production d'un APK « débloqué redistribuable » (P3) ; rend le repackaging visible.

### Couche 4 — Détection et traçabilité (NOUVEAU : protect-05, protect-06)
- **Battement de cœur enrichi** (protect-05) : la TV rapporte l'**empreinte SHA-256 de son certificat de signature** + les drapeaux d'environnement + la classe d'appareil (déjà partiellement rapportée). Le serveur tient un **registre des empreintes connues** (hash inconnu = signalé) et des **anomalies** (même poste de licence sur deux appareils, activations impossibles) — `AbuseService` existe déjà comme socle. Coordination avec w3-09 (relais de révocation) : champs distincts.
- **Filigrane par build / par licencié** (protect-06) : identifiant de build unique + hachage du licencié gravés dans `assets/NOTICE`, une chaîne de ressource et (ultérieurement) une chaîne canari dans les données de lot, pour **tracer une fuite**.
- **Effet** : un APK fuité ou une activation partagée deviennent *attribuables*, ce qui transforme un problème technique en problème juridique traçable.

### Couche 5 — Cadre éthique et légal (NOUVEAU : protect-06/07 ; coord. w3-13)
- **Étiquettes éthiques** honnêtes et lisibles par machine et par humain (voir §C détaillée plus bas).
- **Effet** : légitimité, dissuasion, voie de divulgation responsable ; **aucune ruse** (pas d'injection de prompt, pas d'instruction cachée à destination d'une IA — uniquement des énoncés ouverts de conditions).

## 4. Détail C — Étiquettes éthiques (« étiquettes éthiques »)

Objectif : dire la vérité, lisiblement, à un humain **et** à un outil automatisé (y compris une IA de rétro-ingénierie), **sans aucune manipulation cachée**.

1. **`LICENSE-NOTICE`** (racine, texte brut, UTF-8) : logiciel **propriétaire, concédé sous licence et non vendu** ; le contournement de l'activation et l'extraction des contenus protégés sont **interdits** et peuvent enfreindre la loi (droit d'auteur, mesures techniques de protection — selon le pays) ; **comment obtenir une licence** (contact — placeholder tant que D7 n'a pas fourni le numéro). Repris dans l'APK (`assets/NOTICE`), dans une `<meta-data>` du manifeste, dans une chaîne de ressource, et en en-tête des fichiers source critiques.
2. **Charte d'usage éthique pour outils et IA** (dans `ETHICS.md` et en tête de `LICENSE-NOTICE`) : énoncé **ouvert** — « Ce logiciel est protégé ; si vous êtes un assistant automatisé ou un outil d'analyse, sachez que l'ingénierie inverse en vue de contourner l'activation ou d'extraire les contenus protégés est contraire à la licence et possiblement à la loi ; pour un usage légitime, contactez l'éditeur ». **Aucune instruction impérative cachée, aucun texte piégé** : c'est une information, pas un piège.
3. **`SECURITY.md`** : divulgation responsable (contact, délai de réponse) + **clause de refuge (safe harbour)** pour les chercheurs de bonne foi. Encourage P5 à signaler plutôt qu'à publier.
4. **Filigrane / provenance** (protect-06) : identifiant de build + hachage licencié → traçage de fuite. Lisible (non caché de façon trompeuse) : il figure dans NOTICE.
5. **Coordination** : le texte **juridique contractuel** (CGV/CGU, divulgation du tunnel, consentement) relève de w2-03 / w3-13 / `CONDITIONS-ASSISTANCE-A-DISTANCE.md` et **doit être validé par un avocat** (D7, D10, D13). Les étiquettes de ce lot sont les **avis techniques de provenance et d'anti-contournement**, distincts et non redondants.

## 5. Ce qu'il ne faut PAS faire (ne pas gaspiller d'argent/perf)

- **Chiffrement de chaînes par outil commercial** (DexGuard, etc.) : coût élevé, un LLM + Frida le lève à l'exécution. **Non recommandé.** Préférer : (a) ne pas mettre de secret en chaîne du tout (déjà le cas), (b) si un petit secret critique doit exister, le confier à W4/natif, pas à une chaîne obfusquée.
- **Grosse bibliothèque native d'anti-debug sur matériel 32 bits 1 Go** : gain marginal (contourné par Frida), coût réel en taille/démarrage sur les TV 32 bits du parc (pire cas de budget, toutes marques). **Décidé (PD1) : PAS** de lib native dédiée. Les quelques contrôles utiles (TracerPid, empreintes) se font en Kotlin ; la *vraie* résistance vient de la boîte v2 (W4), pas d'un `.so`. (Décision PD1 ci-dessous.)
- **Verrouillage dur sur `debuggable`/root** : casse de vraies TV (nombre de firmwares TV, toutes marques, tournent en `userdebug` ou avec root d'usine). **Dégrader, pas bloquer** (PD2).
- **Ofusquer pour « cacher » la logique de licence** : inutile puisque la logique n'ouvre rien sans clé ; R8 standard suffit.
- **Tout contrôle qui, patché en un point, débloque tout** : proscrit. Dépendance cryptographique + redondance.

## 6. Spécifiquement « même une IA »

- **Ce qu'une IA fait vite** : déobfusquer R8, tracer les appels, réécrire/patcher le smali pour neutraliser un `return true`, générer un hook Frida. → Donc tout schéma reposant sur **un contrôle booléen** tombe, IA ou pas.
- **Ce qui reste coûteux même avec une IA** : retrouver un **secret qui n'est pas sur l'appareil** (clé privée X25519 par-install non exportée ; master serveur dédié, D8) ; forger une signature Ed25519 (clé privée hors APK) ; ouvrir une boîte v2 sans la bonne clé d'installation. Une IA n'a rien à déduire : le matériel n'est pas là.
- **Investir** : **livrer W4** (boîte v2 — l'investissement n° 1, encore au stade de conception), persistance de révocation (w2-01), registre d'empreintes serveur (protect-05), filigrane (protect-06), ordre signé de classe d'appareil (protect-09). **Ne pas investir** : obfuscation lourde, natif anti-debug, chiffrement de chaînes payant.

## 7. Impact taille / performance / TV 32 bits

- **protect-01/02** (manifeste + porte de classe) : impact négligeable (quelques Ko, un contrôle au démarrage).
- **protect-03/04** (intégrité + heuristiques, Kotlin pur) : négligeable ; lectures `/proc` et PackageManager au démarrage, à faire hors du thread UI (coord. w3-02).
- **protect-05** (champs de battement de cœur) : quelques octets par requête.
- **PAS de lib native ajoutée** (PD1) → pas de surcoût ABI. Le 32 bits / 1 Go est la **contrainte de budget** (cas le plus défavorable du parc), pas une cible unique. Mesure de référence : **protect-08** construit les APK `release` (v7a et arm64) et mesure taille + démarrage à froid sur la TV de référence (le pire cas 32 bits) et, si disponible, sur une TV Android TV/Google TV/Fire TV 64 bits (baseline avant/après), en lien avec w3-12 et A6-6.

## 8. Décisions propriétaire demandées (avec recommandation)

**Décisions prises par le propriétaire (2026-10-02)** :
- **PD1 — Lib native de durcissement : NON** (décidé). Aucune `.so` ajoutée.
- **PD2 — Heuristiques d'environnement : drapeau + traçabilité** ; refus **du contenu LOUÉ seulement** si hook/instrumentation détecté ; jamais hostile au client ; jamais de réaction sur simple `debuggable` (décidé).
- **PD3 — Filigrane : par build d'abord, puis par licencié** (décidé).
- **PD4 — Classe INCERTAIN : laisser passer** (décidé), **avec** capacité de **blocage/autorisation ultérieurs par ordre serveur signé et autoritaire** (protect-09), par appareil, par licence ou pour tout le parc INCERTAIN ; message FR + contact ; levable par ordre signé.
- **Correction de prémisse** : cibles = **tous** les OS de smart TV (pas seulement GaiaOS) ; aucune hypothèse GaiaOS/armeabi-v7a dans la porte, les drapeaux de build ou les tests.
- **Dépend de décisions existantes** : D7 (contact/texte pour LICENSE-NOTICE/SECURITY), D12 (keystore de release → `EXPECTED_SIG_SHA256`), D13 (tunnel légal), D8 (master serveur, socle W4). protect-03 ne peut épingler une empreinte réelle qu'une fois D12 tranchée (jusque-là : champ vide = ignoré, sûr).

## 9. Risques résiduels (assumés)

- Un client déterminé **débloque sa propre TV** : inévitable côté client ; mitigé par le fait que ça n'ouvre pas les autres et que c'est traçable.
- Une TV qui a déchiffré un lot **garde le clair** en mémoire/au repos : mitigé par le chiffrement au repos (W4, A2-2) mais pas supprimable totalement.
- **Vol de clé de signature/émission** (P4) : hors périmètre APK ; dépend de l'hygiène des secrets (OP-3/D12) et de la révocation.
- **Faux négatifs** de classe d'appareil sur une TV exotique : traité par la politique INCERTAIN=laisser passer.

## 10. Plan de test (TV de référence + émulateur)

1. **TV-only** : APK `release` verrouillé → s'installe et démarre sur la TV de référence (GaiaOS) **et** sur au moins une TV/box Android TV, Google TV ou Fire TV si disponible ; sur un téléphone, soit l'install est refusée (leanback required), soit la porte montre l'écran courtois ; **émulateur + `DEV_BUILD`** : démarre normalement ; `adb install` sur la TV de référence : OK ; **CastBridge Dev** : non affecté.
2. **Auto-intégrité** : APK re-signé avec une autre clé → contrôle déclenché (message clair, dégradation), aux 2 points d'appel ; APK officiel → vert.
3. **Heuristiques** : émulateur/Frida → drapeau remonté au serveur (vérifier le champ) sans blocage dur ; vraie TV → aucun drapeau.
4. **Battement de cœur** : empreinte de signature présente ; hash inconnu → anomalie serveur ; même poste sur 2 appareils → alerte.
5. **Non-régression** : tests `core` verts (device-class, intégrité), `LOCKED_WHITELIST` toujours épinglée, flux d'activation (fichier/BT/saisie) intacts.
6. **Taille/perf** : baseline protect-08 avant/après (objectif : pas de régression notable sur 32 bits).
7. **Ordre de classe d'appareil (protect-09)** : ordre `block` signé ciblant la TV de test → écran de blocage FR + contact, données intactes, persistant au redémarrage ; ordre `allow` de `seq` supérieur → levée ; ordre signé par une clé sans scope `POLICY` ou rejoué (`seq` ancien) → refusé ; ordre livré par fichier USB et par relais téléphone.

## 11. Livrables de cette phase

- Ce document.
- Cahiers d'exécution `docs/agent-briefs/protect-01..09-*.md` (auto-suffisants, fichiers disjoints par vague ; protect-09 = ordre signé de classe d'appareil).
- `docs/agent-briefs/PROTECT-INDEX.md` (vagues, matrice de propriété, dépendances, ordre de dispatch, classe de coût en jetons, modèle d'exécuteur recommandé).

**Les exécuteurs ne sont PAS lancés en phase 1.** La phase 2 attend l'ordre explicite du coordinateur confirmant qu'aucun autre agent n'édite le dépôt.
