# pin-persistence — le code (PIN) d'une TV déjà contrôlée n'est plus redemandé à chaque ouverture (R-10)

Branche `claude/pin-persistence` (depuis `integration/agents` b405333d), un commit, non poussée. Conception : `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md`. Suite : `docs/agent-briefs/SONNET-TVCTX-INDEX.md`. Parcours `P-45`, régression `R-10`.

## 0. Preuve terrain (téléphone du propriétaire, `logcat -d` en lecture seule)
`10-03 12:06:20 Start proc castbridge.sender` puis `12:06:26 TvLink: reprise de Smart TV: TvAbsent`. Cette ligne n'est écrite que par `recoverKnownTv` (`S/TvLink.kt:157`), qui ne tourne **que si aucune TV de confiance n'est enregistrée**. Donc à l'ouverture, le registre de confiance du téléphone était vide : le propriétaire utilise le **chemin du code** (comme noté pour R-02). Aucune autre trace utile (le code n'est jamais journalisé, à raison). Aucune lecture de fichier privé, aucune installation.

## 1. Diagnostic classé
| # | Cause | Scénario | Preuve (fichier:ligne, avant correctif) | Classement |
|---|---|---|---|---|
| 1 | Code rangé **par clé d'écran**, pas par TV | TV à code : le code tapé sur l'accueil (clé = nom mDNS) n'existe pas pour « Avancé » (`TvScreen.kt:77` : nom ou IP), la télécommande (`RemoteController.kt:34` : `hôte:port`), l'écran Bluetooth (`TvHub.kt:96` : `bt:`) ; une clé IP est perdue au changement de bail DHCP | `PinKeys.writeKeys` : TV inconnue ⇒ `[key]` seul (`PinKeys.kt:120`), lecture idem (`:128-130`) ; `PinStore.kt:31-35` | **confirmée** (code), chemin du propriétaire |
| 2 | TV de confiance : **rien** gardé sous une IP | code tapé sur un écran à clé IP d'une TV qui avait un jeton : rien n'est écrit ; à l'ouverture suivante (jeton expiré, 12 h) ⇒ champ « PIN » | `PinKeys.kt:121` ; TTL `TrustRegistry.kt:39` | **confirmée** |
| 3 | Accueil : TV retrouvée par **nom exact** | le service s'appelle `"CastBridge TV " + Build.MODEL` (`R/TvService.kt:1032`) ; Android le renomme « … (2) » quand sa propre annonce périmée est encore en cache (redémarrage de l'app TV, mise à jour) ⇒ TV « introuvable » ⇒ « Changer » ⇒ code ressaisi ; deux TV du même modèle ⇒ même nom, « (2) » qui s'échange | `S/TvHome.kt:107` | **probable** (mécanisme Android connu ; non observé dans le journal) |
| 4 | 401 `locked` lu comme « code changé » | 5 essais faux (n'importe quel écran ou boucle) ⇒ la TV refuse **même le bon code** 60 s (`C/tv/Security.kt:42`) ⇒ l'accueil ouvre l'assistant « Le code de la TV a changé » | `S/TvHome.kt:151-153` | **confirmée** (code) |
| 5 | Course du premier lancement (TV de confiance) | jeton expiré, HELLO Bluetooth en cours (secondes) ou impossible (Bluetooth coupé) : `PinStore.get` = "" ⇒ chaque écran affiche « PIN de la TV » ; la liste des TV, elle, est chargée de façon synchrone (pas de course là) | `S/PinStore.kt:24` ; `TvScreen.kt:79-82`, `RemoteScreen.kt:266-284` | **confirmée** (le champ s'affiche), perçue comme une redemande |
| 6 | Plusieurs TV : seul le défaut est renouvelé | 2ᵉ TV de confiance : jeton jamais renouvelé ⇒ après 12 h, code demandé | `C/trust/LinkDriver.kt:147` | **confirmée** ; reportée (tvctx-04) |
| 7 | 401 « bad token » d'une autre TV | le jeton de la 2ᵉ TV restait présenté (boucle de 401), le défaut était marqué refusé | `LinkDriver.kt:101-105` | **confirmée** ; **corrigée** |
| 8 | Boucle du tunnel `127.0.0.1:18765` lue/écrite telle quelle | un code gardé sous la boucle est donné à n'importe quelle TV que la passerelle atteint ⇒ `bad pin` ⇒ verrouillage ⇒ #4 | `PinKeys.credential` lit `read(key)` d'abord (`PinKeys.kt:129`) | **probable** (si la passerelle a servi) |
| 9 | Code changé sur la TV | ne change que par réinitialisation / réinstallation (`R/TvPrefs.kt:12-14`, généré une fois) ; alors redemande **légitime** ; avant : rien n'empêchait de renvoyer le vieux code en boucle | — | confirmée, comportement voulu (une fois) |
| 10 | `apply()` | perte seulement si le processus meurt dans les millisecondes qui suivent (QueuedWork attend `onPause/onStop`) | `PinStore.kt:34`, `TvHome.kt:67`, `TvLink.kt:57` | **peu probable** ; corrigé quand même (`commit()`) |
| 11 | Sauvegarde / transfert d'appareil | `allowBackup=false`, mais `castbridge_pins.xml` absent des exclusions `device-transfer` (Android 12+) : un code en clair pouvait partir vers un nouveau téléphone. Pas une cause de redemande, une fuite | `res/xml/data_extraction_rules.xml` | **confirmée** ; corrigée |
| 12 | Jetons évincés (4 par téléphone, garde-vivant BT 60 s) | 401 en pleine session sur un jeton capturé ; pas à l'ouverture | w15-03 | hors sujet ici (w15-03) |
| 13 | « Réassocier », TV réinitialisée (nouvel `installId`) | chemin de confiance, refus explicite ; `prepareReassociate` garde la TV (R-03) | `LinkDriver.kt:128-134` | pas une cause de redemande à l'ouverture |
| 14 | Deux TV qui partagent une clé | voir #3 (même nom de modèle) | — | probable seulement avec deux TV du même modèle |

Ce qui reste **inconnu** sans le téléphone et la TV réels : lequel de #1, #3, #4 a joué chez le propriétaire (la version installée et les écrans utilisés ne sont pas lisibles en lecture seule).

## 2. Ce qui a changé
- **Cœur, pur et testé** — nouveau `android/core/src/main/kotlin/castbridge/core/trust/PinBook.kt` :
  - `PinBook` : **une fiche par TV sous un identifiant stable** — `bt:<adresse>` pour une TV enregistrée (ou celle que la passerelle atteint), `name:<nom>` / `host:<ip>:<port>` pour une TV à code ; `link` relie l'adresse (ou le nom « (2) ») vue par la découverte à la fiche ; un code **refusé** est marqué (empreinte SHA-256), plus jamais présenté seul, **jamais effacé** (w13-08), levé par le prochain code tapé ; `installId` gardé ⇒ TV réinitialisée = code périmé ; la boucle du tunnel sans passerelle ne désigne aucune TV ; l'adresse Wi-Fi Direct n'est jamais un alias.
  - `CredentialDecision` : jeton / code gardé / attendre / demander le code **avec la cause** (`FIRST_TIME`, `PIN_REFUSED`, `TV_RESET`, `PHONE_REMOVED`, `TOKEN_EXPIRED`, `RELAY_UNKNOWN`) ; garde la seconde moitié de R-01 (`PinFallback`).
  - `TvAuthReply` (401 `bad pin` / `locked` / `bad token`), `HomeTvMatch` (même adresse + même nom de base = même TV ; jamais par le nom seul).
  - `PinKeys.hostAndPort` passe `internal` (aucun changement de comportement).
- `C/trust/LinkDriver.kt` : `reportTokenRejected` retire le jeton de la TV qui le détient.
- Téléphone (modifications minimales, aucun écran nouveau) : `S/PinStore.kt` (lecture/écriture par `PinBook`, `PrefsPinKv` en `commit()`, `refused`, `link`, `decide` ; le champ de code dit « Connexion à la TV en cours : aucun code à saisir. » pendant la reconnexion ou l'attente d'un verrou) ; `S/TvLink.kt` (`pinScope`, `linkFacts`, liste des TV en `commit()`) ; `S/TvHome.kt` (TV retrouvée malgré « (2) », adresse reliée après un code accepté, `locked` = attente, `bad pin` = marque + assistant une fois) ; exclusions de sauvegarde et de transfert + `tools/tests/test_backup_rules.py`.
- CastBridge-TV : **aucun changement** (non nécessaire au correctif ; l'identifiant public de TV est le cahier tvctx-01).

## 3. Migration des codes existants
Aucune perte : les anciennes entrées (une par clé d'écran) **restent** et sont encore écrites (retour à une version antérieure possible). À la première lecture, une entrée trouvée par l'ancienne recherche (`PinKeys.credential`, clés héritées comprises) est recopiée dans la fiche `id:<tvId>` (et `inst:<tvId>`), puis la fiche sert, même après un changement d'IP (`legacyPinsByNameOrIpAreMigratedWithoutLoss`). Seule exception voulue : une entrée sous la boucle `127.0.0.1` n'est plus lue tant qu'aucune passerelle ne dit quelle TV elle atteint.

## 4. Choix du Keystore
Pas de chiffrement Keystore dans ce correctif (motif existant : `R/KeystoreWrapper.kt`, pas de bibliothèque `security-crypto` hors ligne). La cause était une **clé de rangement**, pas le stockage ; un Keystore momentanément illisible au démarrage aurait ajouté une cause de redemande. Les codes restent en `SharedPreferences` privées, maintenant exclues de la sauvegarde **et** du transfert d'appareil, jamais journalisées. Cible : cahier tvctx-04 (clé AES-GCM `AndroidKeyStore`, migration clair → chiffré sans perte ; clé perdue ⇒ **une** demande avec la cause `KEY_LOST`, transitoire ⇒ nouvel essai, jamais de boucle).

## 5. Tests (rouge puis vert)
- Ébauche reproduisant le comportement d'avant, puis tests : `PinBookTest` **10 des 17 rouges par assertion** (`trustedTvWithTokenKeepsTheTypedPinAcrossAnIpChange` « attendu 111111, obtenu "" », `pinOnlyTvFoundFromEveryScreenKey…`, `aPinTypedOnAHostKeyFollowsTheName…`, `legacyPinsByNameOrIp…` « attendu 654321, obtenu "" », `pinRotated…`, `tvReset…`, `firstLaunchRace…`, `lockedIsNotAChangedPin`, `relayLoopback…` « attendu "", obtenu 777777 », `homeTvIsFound…`) ; les 7 autres sont des gardes (deux TV, R-01, TV ancienne sans `installId`, échec d'écriture). `LinkDriverTest.aTokenRejectedByASecondTvDropsThatTvsTokenOnly` rouge (`LinkDriverTest.kt:243`). Ajouté ensuite : `relayToAnUnsavedBluetoothTvUsesTheCodeTypedForThatAddress` rouge (« attendu bt:AA:…, obtenu host:[bt:aa:…]:8765 ») puis vert.
- Après correctif : `PinBookTest` 18/18, `LinkDriverTest` 34/34, `PinKeysTest` 28/28 ; `:core:test` complet **2966 tests, 0 échec**, `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` verts (BUILD SUCCESSFUL) ; `test_backup_rules.py` vert.

## 6. Garanties gardées
Jeton toujours d'abord ; rien sous une IP pour une TV à jeton (DHCP périmé) ; R-01 seconde moitié (jeton refusé ⇒ aucun code présenté) ; pas d'effacement sur refus (w13-08) ; un code refusé n'est plus renvoyé par une boucle (moins de verrouillages) ; R-02/R-09 (chemin du code dans la file) inchangés ; R-03 (`prepareReassociate`) inchangé ; aucune authentification affaiblie, aucun code dans un journal, une notification ou une sauvegarde.

## 7. Risques
- Alias d'adresse : si le DHCP donne l'ancienne adresse d'une TV à une autre TV **du même nom de base**, un code peut lui être présenté une fois ; le refus retire l'alias (test). Deux TV du même modèle à code restent fragiles tant que tvctx-01 n'est pas fait.
- `commit()` sur le fil principal (champ de code, ~1 Ko) : négligeable.
- La 2ᵉ TV de confiance n'a toujours que son code gardé après 12 h (tvctx-04).

## 8. À confirmer sur le vrai téléphone et la vraie TV (P-45)
Rouvrir l'app après l'avoir tuée, après un changement de bail DHCP, après un redémarrage de l'app TV (renommage « (2) ») : aucun champ de code ; « Trop d'essais » n'ouvre pas l'assistant ; deux TV gardent chacune leur code ; code changé sur la TV : une seule demande ; la migration des codes déjà saisis sur le S21+.
