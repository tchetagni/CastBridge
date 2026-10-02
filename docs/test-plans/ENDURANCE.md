# Plan d'endurance (soak) : transferts, liaison, lecteurs (W15-S3, 2026-10-02)

> Source : `docs/coordination/PLAN-STABILISATION-MULTIMEDIA-SYNC-2026-10-02.md` § 4-5. Ce qu'on laisse tourner **la nuit** pour attraper ce que ni les tests JVM ni la liste humaine de 15 min ne voient : fuites, dérives, états rares, attentes sans fin. **Aucun secret ici** (le PIN se lit sur l'écran de la TV ; jamais dans un fichier ni un script). Règle W14 D-W14-3 : **la TV du propriétaire n'est jamais la cible d'un script automatique**, sauf la nuit « vrais appareils » (§ 6) que le propriétaire lance lui-même, et qui ne fait que des copies et des lectures (aucune réinstallation, aucun effacement).

## 1. Cibles et outillage

| Cible | Comment | Ce qu'elle prouve |
|---|---|---|
| **Fausse TV du Mac** (`FakeTvMain`, cahier W14 w14-10 ; en attendant : `ReceiverServer` lancé par `tools/soak/fake_tv.py` via `gradle :core:soakTv`, cahier w15-08) | `127.0.0.1:<port>` + `adb reverse tcp:8765 tcp:<port>` vers le téléphone émulé `cbconnect_phone` (console 5582) ou réel | tout le téléphone (services, notifications, file, PIN, reprise) contre une TV au comportement choisi (`fresh`, `slow`, `busy`, `pin-rotated`, `kill`) |
| **TV émulée** `cbconnect_tv` (Android TV 34 arm64, console 5580 ; APK TV verrouillée + clés de TEST) | `adb -s emulator-5580` | la vraie app TV : `TvService`, `.cbx`, rangement, carte d'accueil (`uiautomator dump`), mémoire (`dumpsys meminfo`), redémarrage (`adb reboot`) |
| **Vrais appareils** (S21+ RFCR313ABNF, TV GaiaOS 32 bits) | relais `nc` du téléphone (mémoire « Relais téléphone → TV ») ; `stay_on_while_plugged_in=3` | 32 bits, libVLC, exFAT, RFCOMM réel, GaiaOS en veille |

Scripts (cahier w15-08, `tools/soak/`, Python 3.12 stdlib, un verrou `tools/soak/.lock`) : `soak.py --scenario <nom> --tv fake|emu|real-ro --phone emu|real --hours N --out tools/soak/out/<horodatage>/`. Chaque scénario écrit `REPORT.md` (une page : PASS/FAIL par seuil, premier écart avec preuve), `events.jsonl` (un événement par copie : début, fin, octets, durée, état final, texte de notification), `mem.csv` (relevés), `evidence/` (logcat filtré, `ui.xml`, captures).

Fichiers de test : générés déterministes (`random.seed`), `small-1M.bin`, `mid-100M.bin`, `big-2G.bin` (un MP4 valide `moov` en tête pour la lecture : `tools/soak/make_media.py` produit un MP4 de 10 min via `ffmpeg` si présent, sinon copie un fichier fourni par le propriétaire hors dépôt). SHA-256 calculé à la création, comparé à l'arrivée (`GET /stream/<nom>` par Range ou `adb pull` sur l'émulateur).

## 2. Scénarios

### S-A : copies LAN répétées (8 h)

| Pas | Commande (côté script) | Attendu |
|---|---|---|
| boucle 1 Mo × 500 | `am start -a SEND --eu STREAM <uri>` vers `OpenWithActivity` (téléphone émulé) ou `curl -T` sur `/upload` (sans téléphone, mesure la TV seule) | chaque copie finit en < 10 s ; `dumpsys notification` : une notification de progression puis « Terminé » ou disparition **avec** marqueur `xfer.finish` (W14 w14-11) |
| boucle 100 Mo × 60 (rapide) puis × 20 (classique, réglage `fast=false`) | idem | % monotone ; `GET /api/transfer/state` (rapide) ou `/api/part` (classique) croissant pendant ; carte TV avec % (`uiautomator dump` toutes les 5 s contient « % ») |
| 2 Go × 4 | idem | fin < taille / 2 Mo/s sur vraie TV ; `finish` ≠ 500 ; `.cbx/` vide après |
| fin | `GET /api/library` + SHA | tous présents, SHA identiques, rangés par catégorie ; `GET /api/transfer/sessions` = 0 vivante |

**Seuils** : succès ≥ 99 % ; 0 `FATAL EXCEPTION`/`ANR` dans `castbridge.*` ; 0 copie « figée » (aucun octet pendant > 90 s **sans** texte de blocage) ; 0 notification résiduelle à la fin ; 0 session `.cbx` orpheline.

### S-B : coupures aléatoires (4 h)

Toutes les 3 à 8 min, pendant une copie de 100 Mo ou 2 Go, une perturbation tirée au sort :

| Perturbation | Commande | Attendu |
|---|---|---|
| Wi-Fi téléphone coupé 5-60 s | `adb shell svc wifi disable` / `enable` | pause nommée (« réseau »), reprise sans action, % ne recule pas |
| TV émulée tuée | `adb -s emulator-5580 shell am force-stop castbridge.receiver` puis `am start` du `BootReceiver`/activité | « ne répond plus… » ≤ 40 s ; reprise à la carte de blocs ≤ 60 s après retour ; jamais 0 % |
| TV émulée redémarrée | `adb -s emulator-5580 reboot` | `TvService` relancé seul, `/api/hello` ≤ 90 s, copie reprise |
| PIN tourné (fausse TV `pin-rotated`, une fois par nuit) | commande de la fausse TV | message « code de la TV a changé » + notification « Envoi bloqué » ; **aucune** boucle ; après saisie (script `input text` sur le téléphone émulé), reprise |
| 3 `begin` abandonnés (fausse TV `busy`) | `curl -X POST /api/transfer/begin` ×3 sans suite | la 4ᵉ copie passe après ≤ 5 min (balayage) ou immédiatement (éviction) ; jamais 429 définitif |
| annulation à 30 % puis renvoi | `am broadcast` ACTION_CANCEL puis renvoi | notification retirée ; `abort` reçu ; renvoi repart proprement |

**Seuils** : reprise ≤ 60 s après retour dans 100 % des cas ; 0 état « Failed » sans texte ; 0 429 persistant ; état final = fichier complet et SHA correct.

### S-C : Bluetooth (2 h, vrais appareils seulement)

Wi-Fi du téléphone coupé ; 50 copies de 2 Mo par « Envoyer » ; relevé `dumpsys bluetooth_manager` et logcat `LinkPool`/`BtUploadService` ; TV : `uiautomator` impossible (vraie TV) ⇒ `GET /api/screenshot` toutes les 10 s par le relais, différence d'image. **Seuils** : ≥ 100 Ko/s moyen ; % monotone ; carte TV avec % ; ≤ 2 jetons émis par heure (compter les HELLO dans le logcat `TvLink`) ; 0 `connect()` concurrent (logcat `BtConnectLock`).

### S-D : lecteurs (2 h)

| Pas | Commande | Attendu |
|---|---|---|
| lecture TV d'un MP4 de 10 min, seek aléatoire × 500 (`input keyevent DPAD_LEFT/RIGHT`, 2-5 s d'écart) | TV émulée puis vraie TV (télécommande dans l'app par `/api/remote`) | 0 ANR ; `GET /api/info` `player.state` cohérent ; PSS stable |
| pause/reprise × 100, fin de fichier × 20 | `/api/player/pause|play` | libVLC libéré en fin (`dumpsys meminfo` : natif stable) |
| reprise de position : lecture 3 min, `force-stop`, relancer | `/api/play` | position restaurée ± 15 s (après w15-13 : ± 30 s au pire) |
| lecture pendant l'envoi : copie de 2 Go + `/api/play` à 10 % | fausse TV ou émulateur | lecture démarre dès le seuil, pas de relance à 0, envoi non ralenti > 50 % |
| téléphone : « Ouvrir avec » × 50 depuis `am start -a VIEW` | téléphone émulé | 0 notification media3 résiduelle 30 s après la fin (`dumpsys notification` sans `media3_group_key`) |

### S-E : liaison (4 h)

| Perturbation | × | Attendu |
|---|---|---|
| reboot TV émulée | 10 | puce rouge puis **verte ≤ 90 s** sans action (W14 P-31) ; 0 « Aucune TV » (`uiautomator dump`) |
| changement d'IP (fausse TV relancée sur un autre port + `adb reverse` refait) | 5 | HELLO/mDNS ⇒ « adresse retrouvée » ≤ 20 s |
| TV éteinte 10 min (`fake_tv.py --pause 600`) | 5 | « ne répond plus (veille) » ; reprise au retour ; **aucun** HELLO en rafale (≤ 12/min, limiteur) |
| jeton : horloge téléphone +13 h (émulateur `date` ou `TrustRegistry` de test) | 2 | renouvellement sans écran, copie en cours reprend ≤ 60 s |

### S-F : fuites et dérives (toute la nuit, en parallèle des autres)

Relevés toutes les 10 min, dans `mem.csv` :

| Mesure | Commande | Seuil |
|---|---|---|
| PSS TV (émulée) | `adb -s emulator-5580 shell dumpsys meminfo castbridge.receiver \| grep 'TOTAL PSS'` | dérive < 15 % entre la 1ʳᵉ heure et la dernière ; jamais > 350 Mo (cible 1 Go, 32 bits) |
| PSS téléphone | `dumpsys meminfo castbridge.sender` | dérive < 20 % |
| descripteurs TV | `adb shell run-as castbridge.receiver ls /proc/<pid>/fd \| wc -l` (émulateur ; `cbdev` sur vraie TV ne le peut pas) | < 200 ; pas de croissance monotone |
| threads TV | `dumpsys meminfo` (`Threads`) ou `ls /proc/<pid>/task` | < 80 ; stable ± 10 |
| sessions de transfert vivantes | `GET /api/transfer/sessions` (w15-08) | 0 hors copie en cours |
| `.cbx`, `.part` orphelins | `adb shell ls` (émulateur) / `GET /api/library` `arriving` (vraie TV) | 0 à la fin |
| notifications | `dumpsys notification --noredact \| grep castbridge` | 0 à la fin de chaque scénario |
| wake locks | `dumpsys power \| grep -i castbridge` | 0 tenu hors copie/lecture |
| HELLO et jetons | logcat `TvLink` (téléphone) | ≤ 2 jetons/h par TV en liaison stable |

## 3. Ce qui ouvre une régression

Tout dépassement de seuil = une ligne `R-NN` dans `docs/REGRESSIONS.md` (symptôme, scénario, preuve du dossier `evidence/`), puis un cahier correctif qui commence par le test rouge (règle R1 du plan). Un écart « hyp. » du registre § 2 **confirmé** par l'endurance passe au rang de défaut prouvé.

## 4. Calendrier

| Nuit | Cibles | Scénarios | Qui |
|---|---|---|---|
| 1 (après S0) | fausse TV + téléphone émulé | S-A, S-B, S-F | coordinateur |
| 2 (après S2) | TV émulée + téléphone émulé (ou réel, D-W15-7) | S-A, S-B, S-D, S-E, S-F | coordinateur |
| 3 (S3) | **vrais appareils** | S-A réduit (1 Mo × 100, 100 Mo × 10, 2 Go × 1), S-C, S-D (vraie TV), S-F (PSS via `cbdev status` si disponible, sinon `/api/info`) | propriétaire (§ 6) |

Sortie de stabilisation : les trois nuits conformes (plan § 5).

## 5. Commandes utiles (lecture seule sur les vrais appareils)

```sh
# téléphone réel : journaux de l'app, sans secret
adb -s RFCR313ABNF logcat -d -v time -s 'TvLink:*' 'UploadService:*' 'TransferQueue:*' 'BtUploadService:*' 'CastSession:*' 'AndroidRuntime:E'
adb -s RFCR313ABNF shell dumpsys notification --noredact | grep -A3 'pkg=castbridge'
adb -s RFCR313ABNF shell dumpsys meminfo castbridge.sender | grep 'TOTAL PSS'
# vraie TV, par le relais du téléphone (voir mémoire « Relais téléphone → TV ») : GET seulement
curl -s -H "X-CB-Pin: $PIN" http://127.0.0.1:18766/api/info
curl -s -H "X-CB-Pin: $PIN" http://127.0.0.1:18766/api/transfer/state?id=<id>
curl -s -H "X-CB-Pin: $PIN" http://127.0.0.1:18766/api/screenshot -o tv.png
# émulateurs (sacrifiables)
adb -s emulator-5580 shell dumpsys meminfo castbridge.receiver | grep 'TOTAL PSS'
adb -s emulator-5580 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5580 shell cat /sdcard/ui.xml | grep -o '%'
```

## 6. Nuit « vrais appareils » : procédure pour le propriétaire (≈ 10 min de mise en place)

1. TV allumée, clé USB exFAT branchée, téléphone branché au Mac (`stay_on_while_plugged_in=3`), même Wi-Fi, Bluetooth des deux allumé.
2. Vérifier que la liste humaine est 12/12 sur la build en place (sinon ne pas lancer).
3. Sur le Mac : `python3 tools/soak/soak.py --scenario night-real --tv real-ro --phone real --hours 8` ; le script ne fait que des copies (`am start -a SEND` depuis le téléphone, fichiers de test posés dans `/sdcard/Download/castbridge-soak/`), des lectures (`/api/play` des fichiers de test), des relevés ; il **n'installe rien, n'efface rien, ne touche pas au PIN**.
4. Le matin : lire `tools/soak/out/<horodatage>/REPORT.md` ; supprimer les fichiers de test de la TV depuis la bibliothèque (ou laisser le script le proposer avec confirmation).
5. Envoyer le rapport (sans secret) ; le coordinateur ouvre les lignes de régression.
