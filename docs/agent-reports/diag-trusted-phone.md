# Diagnostic : « l'option téléphone de confiance n'est plus fonctionnelle » (2026-10-02)

Branche `claude/fix-trusted-phone` (depuis `integration/agents`). Aucun secret (PIN, jeton) dans ce rapport.

## 1. Constat sur les appareils (lecture seule, 16:23-16:33 WAT)

| Élément | Valeur | Source |
|---|---|---|
| Téléphone | CastBridge 1.2.31-beta (code 61), `lastUpdateTime` 2026-10-02 14:25:51 | `dumpsys package castbridge.sender` |
| Téléphone : 1re installation | **2026-10-01 10:53:37** : l'app a été désinstallée puis réinstallée ce jour-là, ses données (TV enregistrées, jetons) ont donc été perdues à ce moment-là (`allowBackup=false`) | idem |
| Téléphone : liaison Bluetooth | `74:24:CA:06:00:46 Smart TV` **liée** (depuis le 2026-09-29 vers 23:27), UUID CBT1 `…0005` annoncé | `dumpsys bluetooth_manager` |
| TV | CastBridge-TV **0.14.18-beta-verrouillee** (code 63), APK posé le **2026-10-02 à 13:30:25**, installateur `com.android.packageinstaller` (installation à la main) ; même certificat de signature que la 0.14.17 (mise à jour sans désinstallation possible) | `pm path` + `stat`, `cbdev status`, `apksigner` sur l'APK installé |
| TV : boîte de dépôt de cbdev | `castbridge-tv.apk` de 13:29, même taille que l'APK installé | `ls files/inbox` |
| Journal du téléphone | `16:23:10.733 I/TvLink: reprise de Smart TV: Refused` puis `16:26:12.117 … Refused` (connexion RFCOMM établie, puis refus de la TV en 1 s) | `logcat --pid` |
| Écran du téléphone | « SMART_TV · Connectée · 49,4 Go libres » (chemin du code PIN), copie et lecture OK | screencap |

Le journal de la TV n'est pas lisible depuis SSH (uid `castbridge.dev` : `logcat` ne montre que ses propres lignes, `dumpsys package` refusé, pas de `screencap`). Le **code** du refus n'est pas journalisé par le téléphone (`TvLink.kt:166` n'écrivait que le nom de la classe).

## 2. Mécanisme (code)

- `TvLink.recoverKnownTv` (`sender/TvLink.kt:155-175`) ne tourne que si la liste des TV de confiance du téléphone est **vide**. Elle envoie un HELLO **sans** demande de confiance et **sans** `installId`.
- Côté TV, `HelloHandler.handle` (`core/trust/HelloHandler.kt:36-41`) répond `ERR_UNTRUSTED` si la TV ne voit pas de liaison Bluetooth avec ce téléphone, ou si ce téléphone n'est pas dans `trusted_phones.txt` ; `ERR_BUSY` seulement au-delà de 10 essais par fenêtre (`TvService.kt:305`) : impossible avec 2 essais espacés de 3 min. Le refus est donc presque sûrement **`ERR_UNTRUSTED` : la TV ne connaît plus ce téléphone**.
- Aucune des fusions récentes ne touche au HELLO : entre 0.14.17 (`faf8636`) et les builds installés (`c81fa23`/`d137b96`), seuls `TvService.kt` (sonde réseau) et `BtProtocol`/`BtServer` (`ERR_TRIAL`, envoi de **fichiers** en essai seulement) ont changé. Les fusions w4/w5/w6 sont postérieures aux builds installés. Le protocole n'est **pas** en cause.
- Le PIN de la TV n'est régénéré que si `castbridge_tv.xml` est vide (`receiver/TvPrefs.kt:12-15`). Or le propriétaire a dû ressaisir le PIN aujourd'hui : la TV a donc probablement perdu ses données (désinstallation puis installation), et avec elles `trusted_phones.txt`.
- **Défaut trouvé côté téléphone** : « Réassocier » (l'action proposée par « TV réinitialisée » / « La TV ne vous reconnaît plus », `core/trust/LinkText.kt:53-55`) appelait `TvLinkManager.requestReassociate` → `forget(address)` (`sender/TvLink.kt:226-229`), qui **efface la TV enregistrée et son jeton avant** que le nouvel appairage réussisse. L'adresse de reprise ne vivait qu'en mémoire (`pendingReassociate`). Si l'appairage n'aboutit pas (fenêtre « Ajouter un téléphone » non ouverte sur la TV dans les 150 s, « Saisir le code de la TV à la place », retour arrière, app tuée, ou `adb install -r` à 14:25), le téléphone se retrouve **sans aucune TV de confiance**, et rien ne peut la faire revenir : la reprise automatique est refusée par la TV. C'est exactement l'état observé : registre vide, TV connue seulement par le PIN, « Aucune TV ajoutée ».

## 3. Classement des causes

1. **Probable (cause de départ)** : la TV a perdu son registre de confiance, très probablement lors d'une réinstallation avec perte de données aujourd'hui (PIN changé ; 0.14.18 posée à 13:30 avec l'installateur du système). C'est prévu par la conception : une TV réinstallée ne reconnaît plus aucun téléphone (`TrustRegistry.kt:45`, nouvel `installId`). Non confirmé : je n'ai pas pu lire le registre de la TV.
2. **Probable (pourquoi le téléphone n'a plus rien)** : le défaut « Réassocier oublie d'abord » ci-dessus, ou bien la désinstallation de l'app téléphone le 2026-10-01 à 10:53 sans nouvel « Ajouter ma TV » depuis. Dans les deux cas, on arrive au même état : registre du téléphone vide et TV qui refuse.
3. Écartées : décalage de protocole HELLO (aucun changement), fenêtres 12 h/48 h des jetons (sans objet sans téléphone enregistré), horloge de la TV (le HELLO ne dépend pas de l'heure), limiteur (2 essais), essai/grâce (`ERR_TRIAL` ne concerne que l'envoi de fichiers par Bluetooth), liaison Bluetooth absente côté téléphone (elle est présente).
4. Inconnu : l'état de la liaison Bluetooth **côté TV** (une liaison tenue par un seul côté donnerait aussi `ERR_UNTRUSTED`). L'appairage guidé le détecte et le répare (`PairFlow.kt:84`).

## 4. Ce que le propriétaire doit faire maintenant

1. Sur la TV : CastBridge-TV › MENU › « Ajouter un téléphone / téléphones de confiance ». La fenêtre reste ouverte 2 min.
2. Sur le téléphone : onglet CastBridge TV › « Changer » / « Ajouter ma TV », puis choisir « Smart TV » (« Déjà associée en Bluetooth »).
3. Sur la TV, à la question « Autoriser ce téléphone à piloter cette TV ? » : **Autoriser** avec la télécommande.
4. Si le téléphone dit « Association Bluetooth périmée » : dans Paramètres Bluetooth du téléphone, « Dissocier » Smart TV, revenir dans l'app : l'association repart toute seule (codes identiques sur les deux écrans → valider).
5. Vérifier : sur la TV, « Téléphones de confiance (1) » ; sur le téléphone, puce verte **sans** code. Ne pas toucher « Saisir le code à la place » pendant l'étape 3.
6. À l'avenir, pour mettre à jour la TV, utiliser une **mise à jour** (même signature : l'installateur propose « Mettre à jour »), jamais une désinstallation : la désinstallation efface les téléphones de confiance **et** change le PIN.

## 5. Correctif (commit unique sur cette branche)

- `core/trust/LinkDriver.kt` : nouveau `prepareReassociate(address)`. La TV **reste** enregistrée ; seuls la session et le jeton, que la TV refuse, sont retirés. `adopt()` remplace l'entrée quand la TV a dit oui (même adresse, nouvel `installId`).
- `sender/TvLink.kt` : `requestReassociate` n'appelle plus `forget` mais `prepareReassociate` (hors du fil principal). Le journal de reprise donne maintenant le **code et l'indice** du refus (`reprise de Smart TV: Refused code 8 (…) indice 0`), pour qu'un prochain diagnostic n'ait plus à deviner.
- Tests (`core/LinkDriverTest`) : `reassociateKeepsTheTvSavedUntilTheNewPairingSucceeds` (TV réinstallée → « Réassocier » → appairage abandonné : la TV reste dans la liste, le jeton est retiré, l'état reste honnête ; puis appairage approuvé → une seule TV, nouvel `installId`, état connecté) et `reassociateOfAnUnknownAddressDoesNothing`.
- Effet : après un appairage inachevé, le téléphone affiche « La TV ne vous reconnaît plus · Réassocier » (vrai) au lieu de « Aucune TV ajoutée » (faux), et l'envoi par code PIN continue de retrouver la TV.

## 6. Reste à vérifier sur un vrai appareil

- Lire le code du refus après installation du correctif (`adb logcat -s TvLink`), et `trusted_phones.txt` / la liste « Téléphones de confiance » sur la TV.
- Parcours : TV réinstallée → « Réassocier » → quitter l'écran sans valider → la TV reste listée ; puis « Réassocier » → « Autoriser » → puce verte sans code ; vérifier qu'il n'y a pas de double entrée.
- Pistes suivantes (non faites) : faire envoyer `installId` par `recoverKnownTv` quand il est connu (indice même/autre installation) ; quand la reprise reçoit `ERR_UNTRUSTED` avec un PIN connu, proposer « Ajouter ma TV » au lieu de rester muet.
