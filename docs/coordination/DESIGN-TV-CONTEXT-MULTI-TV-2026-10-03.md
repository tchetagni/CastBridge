# Conception — une fiche par TV, contexte porté par la TV, plusieurs TV (2026-10-03)

Demande du propriétaire (2026-10-03) : « l'app phone ne persiste pas le PIN d'une TV qui contrôle et c'est embêtant de relancer ce PIN après chaque nouvelle session d'ouverture. Les infos de session sont dans la TV. Une app phone peut interagir avec plusieurs TV qui chacune lui apporte les éléments de son contexte ».

Trois exigences : **(A)** CastBridge ne redemande jamais le code d'une TV qu'il contrôle déjà à l'ouverture suivante ; **(B)** le contexte (session, profils, état parental, lots, file, locations, réglages) vit **sur la TV** ; **(C)** un téléphone parle à **plusieurs** TV, chacune apportant son contexte, jamais mélangé.

Diagnostic et correctif livré : `docs/agent-reports/pin-persistence.md`, régression `R-10`, parcours `P-45`. Cahiers de la suite : `docs/agent-briefs/SONNET-TVCTX-INDEX.md`.

## 1. Modèle cible

```
                      CastBridge (téléphone)                                         CastBridge-TV (chaque TV)
 ┌───────────────────────────────────────────────────────────┐          ┌──────────────────────────────────────┐
 │ Carnet des TV  (castbridge_pins + castbridge_trust)        │          │ identité : installId (TrustRegistry)  │
 │                                                           │          │   tvId public = H(installId) [tvctx-01]│
 │  tvId ─┬─ fiche « bt:AA:BB:… »   (TV appairée)             │  HELLO   │ registre des téléphones de confiance │
 │        │   jeton (12 h, renouvelé à mi-vie, par TV)  ◄─────┼──────────┤   jetons (haché), révocation          │
 │        │   code tapé (repli)  · installId vu au HELLO       │  BT/Wi-Fi│ code (PIN) 6 chiffres, stable         │
 │        │   clés d écran : nom, nom mDNS, IP:port, bt:       │          │                                      │
 │        ├─ fiche « name:castbridge tv m1 » (TV à code)       │  X-CB-Pin│ GET /api/hello  (public, + tvId)       │
 │        │   code tapé · alias IP:port vus par la découverte  ├──────────► GET /api/info, /api/library, /api/lots, │
 │        └─ fiche …                                          │ X-CB-Token /api/rental, /api/parental/config/get,│
 │                                                           │          │   /api/storage, /api/activation …     │
 │ Cache de contexte PAR tvId (lecture hors ligne seulement)  │◄─────────┤ GET /api/context  [tvctx-02]           │
 │  profils · parental · lots · file · locations · réglages   │  borné   │   lecture seule, ≤ 64 Ko, sans secret │
 │                                                           │          │                                      │
 │ Liste des TV connues [tvctx-03] :                          │          └──────────────────────────────────────┘
 │  joignable ? · identifiant valide ? · code à saisir ?      │
 └───────────────────────────────────────────────────────────┘
   Changer de TV = changer de fiche (identifiant ET contexte), jamais de fusion entre TV.
```

### 1.1 Identité stable d'une TV (jamais son IP)
- TV appairée par Bluetooth : **`bt:<adresse>`** (ce que la liaison appairée prouve ; c'est déjà la clé de `SavedTvs`), complétée par l'`installId` reçu au HELLO : un `installId` différent = TV réinitialisée (nouveau code, nouveaux jetons).
- TV jointe seulement par le code (cas du propriétaire aujourd'hui, registre de confiance vide) : la TV ne donne aujourd'hui **aucun** identifiant stable sur le Wi-Fi (`GET /api/hello` = `app`, `v`, `pinRequired`). Livré maintenant : fiche par **nom de service** (`name:<nom>`, « (Bluetooth) » retiré, « (2) » gardé car c'est peut-être l'autre TV du même modèle) + **alias** d'adresses vues avec ce nom (`PinBook.link`). Cible : `tvId` public dans `/api/hello` (cahier tvctx-01), **haché** depuis l'`installId` (`H = SHA-256("cbtv-id|" + installId)`, 16 hex) pour ne pas exposer l'identifiant qui sert aux locations ; le téléphone recalcule `H` depuis l'`installId` du HELLO Bluetooth et relie les deux fiches.
- Règles d'alias (après audit Opus) : seules les **adresses** sont des alias, jamais un nom (« … (n) » peut être l'autre TV du même modèle) ; un alias d'adresse ne mène à un code que si un nom de **même nom de base** est vu à cette adresse ; aucune écriture ne passe par un alias (elle le délie) ; un 401 ne marque un code refusé que si nom **et** adresse concordent.
- La boucle locale du tunnel (`127.0.0.1:18765`) n'est pas une TV : elle vaut `bt:<adresse que la passerelle atteint>` tant que la passerelle tourne, **rien** sinon. L'adresse Wi-Fi Direct `192.168.49.1` est la même sur toutes les TV : jamais un alias.

### 1.2 Une fiche d'identifiant par TV
- Préféré : le **jeton** de téléphone de confiance émis par la TV (déjà : `TrustRegistry`, 12 h, renouvelé à mi-vie par `LinkDriver`). Le code tapé n'est qu'un **repli**, gardé dans la même fiche.
- Stockage : `SharedPreferences` privées, **exclues de toute sauvegarde et de tout transfert d'appareil** (`allowBackup=false` + `backup_rules.xml` + `data_extraction_rules.xml`, désormais avec `castbridge_pins.xml`), écritures **synchrones** (`commit()`). Jamais journalisé ni notifié.
- Chiffrement Android Keystore : **cible** (cahier tvctx-04), pas livré dans ce correctif. Le dépôt a déjà le motif (`R/KeystoreWrapper.kt` : AES-256-GCM dans `AndroidKeyStore`, classement « clé perdue » vs « transitoire » de `InstallKeyPolicy`) ; aucune bibliothèque `security-crypto` (construction hors ligne). Clé perdue (réinitialisation des données de sécurité, changement d'appareil) ⇒ fiche illisible ⇒ **une** demande de code avec la cause, jamais une boucle. Raison du report : le défaut est une perte de **clé de rangement**, pas de chiffrement ; ajouter une dépendance au Keystore dans le correctif aurait ajouté une cause de redemande (Keystore momentanément illisible au démarrage sur Android 6-9) sans rien corriger.

### 1.3 Revalidation silencieuse à chaque session
- TV de confiance : la boucle `LinkDriver` fait déjà HELLO à l'ouverture (`Trigger.APP_OPENED`), garde le jeton valide pendant la reconnexion, renouvelle à mi-vie. **Pendant** ce premier HELLO, aucun écran ne demande le code (`CredentialDecision` ⇒ `Wait("Connexion à la TV en cours : aucun code à saisir.")`).
- TV à code : la première requête authentifiée (`GET /api/info` de l'accueil) est la sonde ; un succès relie l'adresse vue à la fiche (`PinBook.link`).
- Plusieurs TV : aujourd'hui seule la TV **par défaut** est renouvelée ; cible : renouvellement par TV (cahier tvctx-04, `LinkDriver` multi-TV) — d'ici là, la fiche garde le code de chaque TV et le jeton d'une autre TV sert tant qu'il vit.

### 1.4 Le code n'est demandé que si la TV le refuse — une fois par TV
`CredentialDecision.decide` (pur, `C/trust/PinBook.kt`) :

| Faits | Décision | Texte |
|---|---|---|
| jeton vivant, non refusé | `UseToken` | — |
| clé = boucle du tunnel sans passerelle | `AskPin(RELAY_UNKNOWN)` | « Passerelle Bluetooth arrêtée : saisissez le code de la TV. » |
| TV : `locked` (trop d'essais) | `Wait(retryAfter)` | « Trop d'essais de code : nouvel essai dans N s. » (jamais l'assistant) |
| TV de confiance, jeton refusé, HELLO en cours | `Wait` | R-01 : `PinFallback.REFUSED` (aucun code présenté) |
| TV réinitialisée (installId changé / refus « autre installation ») | `AskPin(TV_RESET)` | « La TV a été réinitialisée : saisissez le nouveau code… » |
| code gardé, jamais refusé | `UsePin` | — |
| code gardé, refusé par la TV (`bad pin`) | `AskPin(PIN_REFUSED)` | « Le code de la TV a changé : saisissez-le à nouveau. » |
| TV de confiance, téléphone retiré | `AskPin(PHONE_REMOVED)` | « Ce téléphone a été retiré de la TV… (ou « Réassocier ») » |
| TV de confiance, liaison en cours (premier lancement) | `Wait` | « Connexion à la TV en cours : aucun code à saisir. » |
| TV de confiance, Bluetooth injoignable, pas de code | `AskPin(TOKEN_EXPIRED)` | « Reconnexion automatique impossible pour l'instant… » |
| rien | `AskPin(FIRST_TIME)` | « Saisissez le code affiché sur la TV. » |

Un code refusé est **marqué** (empreinte SHA-256) et n'est plus présenté par aucune boucle ; il n'est **pas effacé** (w13-08) ; le prochain code tapé lève la marque.

### 1.5 Contexte apporté par la TV (B, C)
Ce qui existe déjà sur la TV et sera **réutilisé** (aucune nouvelle donnée inventée) :

| Élément | Route existante | Remarque |
|---|---|---|
| fichiers, espace, lecteur, liste de lecture, volumes | `GET /api/info` | déjà sondé toutes les 2 s par l'accueil |
| bibliothèque | `GET /api/library` | |
| lots sur la TV | `GET /api/lots` | |
| locations | `GET /api/rental` | |
| état parental (profil enfant, verrou) | `GET /api/parental`, `/api/parental/config/get` | jamais le code parental |
| réglages de stockage | `GET /api/storage` | |
| édition / activation | `GET /api/activation` | |
| file de réception | `GET /api/transfer/state` | |
| icônes de connexion | `GET /api/connections` | |
| liste des téléphones de confiance | **aucune route** (seulement l'écran TV) | à ne pas exposer au téléphone (vie privée des autres téléphones) |

À ajouter (cahier tvctx-02) : `GET /api/context` **additif**, authentifié (jeton ou code), lecture seule, **borné à 64 Ko**, qui agrège les champs ci-dessus déjà lisibles par ce téléphone (`tvId`, nom, version, édition, profil/état parental sans code, lots résumés, locations résumées, file, réglages), avec `etag` ; rien que le téléphone ne puisse déjà lire. Le téléphone le met en cache **par `tvId`** (fichier privé exclu des sauvegardes) pour l'affichage hors ligne, marqué « vu le … », jamais fusionné entre TV. Parité : `tools/routes/routes.txt`, listes propriétaire / `TrialPolicy` (lecture autorisée en essai).

### 1.6 Liste des TV connues (cahier tvctx-03, après le gel)
Une ligne par fiche : nom, état **joignable** (sonde `/api/hello`), **identifiant valide** (jeton vivant ou code non refusé), **code à saisir** (cause de `CredentialDecision`), TV par défaut ; toucher = changer de fiche et de contexte. Pas d'écran nouveau pendant le gel : `ManageTvsDialog` existant d'abord.

## 2. Ce qui est livré maintenant (sans écran nouveau)
- `C/trust/PinBook.kt` : `PinBook` (une fiche par TV, alias, migration des anciennes clés, refus marqués, TV réinitialisée), `CredentialDecision`, `TvAuthReply` (401 `bad pin` / `locked` / `bad token`), `HomeTvMatch` (nom mDNS « (2) »).
- `C/trust/LinkDriver.kt` : un 401 « bad token » retire le jeton de la TV qui le détient (plus celui du défaut).
- `S/PinStore.kt` (lecture/écriture par `PinBook`, `commit()`), `S/TvLink.kt` (`pinScope`, `linkFacts`, liste des TV en `commit()`), `S/TvHome.kt` (TV retrouvée à son adresse connue même renommée « (2) », sinon choix de l utilisateur avec le code gardé proposé ; `locked` ≠ code changé ; refus marqué seulement si nom et adresse concordent), règles de sauvegarde.
- Aucune modification de CastBridge-TV : non nécessaire au correctif (preuve : tous les cas de R-10 se corrigent côté téléphone ; le seul cas qui demande la TV — deux TV **du même modèle** à code dont les « (2) » s'échangent — est rare et documenté, cahier tvctx-01).
