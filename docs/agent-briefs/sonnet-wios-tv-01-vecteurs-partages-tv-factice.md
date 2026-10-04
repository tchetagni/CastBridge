# wios-tv-01 — Vecteurs partagés Kotlin → Swift (`tools/ios-vectors/`) et TV factice exécutable pour le simulateur iOS
<!-- routage architecte 2026-10-04 (vague iOS, ordre 1) -->
> **Modèle : sonnet** · escalade : aucune (aucun changement de comportement de la TV) · statut : **PRÊT** (tests du cœur + outil : permis pendant le gel)
> **Groupe : WIOS-TV** (ordre 1, en parallèle de wios-01) · porte : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.iosvectors.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 4.1, § 7). Branche `claude/wios-tv-01-vecteurs`. Rapport : `docs/agent-reports/sonnet-wios-tv-01.md`.

## Objectif (autonome)
L'app iPhone (Swift, répertoire `ios/`) réimplémente un petit sous-ensemble du cœur Kotlin. Pour garantir des octets identiques sur le fil, le **Kotlin écrit des vecteurs JSON** que le Swift rejoue (méthode déjà utilisée : `tools/activation/test-vectors.json`, `tools/wallet/wallet-vectors.json`). Et pour tester le client Swift contre la **vraie** TV sans TV, il faut une **TV factice exécutable** : la vraie `ReceiverServer` (`android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt`) lancée sur 127.0.0.1 (le simulateur iOS partage le réseau du Mac). Le harnais W14 (`android/core/src/test/kotlin/castbridge/core/journey/TvSim.kt`, port 0, `restart()`) montre comment l'instancier ; `FakeTvMain` **n'existe pas** (constat W19 § 0.6).

## Fichiers possédés
- **Nouveaux** : `android/core/src/test/kotlin/castbridge/core/iosvectors/{IosVectorsWriter,IosVectorsTest,FakeTvMain}.kt` ; `tools/ios-vectors/{transfer,tv-api,qr,signal-texts}-vectors.json` ; `tools/ios-vectors/README.md` ; `tools/ios/fake-tv.sh`.
- **Zone additive** : `android/core/build.gradle.kts` (une tâche `JavaExec` `fakeTv` sur le classpath de test, ≤ 15 lignes, aucun changement des tâches existantes).
- **Interdit** : `android/core/src/main/**` (lecture seule), `R/`, `S/`, `ios/**`, `server-play/**`, `backend/**`.

## Spécification
1. `transfer-vectors.json` (`castbridge-ios-transfer-vectors-v1`) : pour des tailles 0, 1, 256 Kio, 1 Mio−1, 1 Mio, 64 Mio, 64 Mio+1, 1 Gio, 1 Gio+1, 5 Gio : `Manifest.blockSizeFor`, nombre de blocs, longueur du dernier bloc, tranches de 256 Kio ; pour 3 fichiers synthétiques **déterministes** (octet i = `(i * 31 + 7) & 0xFF`, tailles 3 Mio+5, 9 Mio, 300 Kio) : SHA-256 hex de chaque bloc et `Manifest.root` ; l'identifiant de transfert tel que calculé par le client Kotlin (lire `C/xfer/TransferClient.kt`, `C/xfer/Blocks.kt` ; si l'identifiant est rendu par la TV et non calculé, l'écrire tel quel et le dire).
2. `tv-api-vectors.json` : transcriptions **réelles** obtenues en appelant la vraie `ReceiverServer` (port 0, PIN de test `000000` ou celui de `TvSim`) : requête (méthode, chemin, en-têtes **sans secret réel**), statut, corps, pour `/api/hello`, `/api/info`, `/api/library` (vide et 2 fichiers), `/api/storage/check` (ok, 507), `/upload` (offset juste, 409), `/api/transfer/caps|begin|chunk|state|finish` (dont 422 hachage faux, 429), `/api/have`, `/api/pause|resume|seek|volume`, 401 PIN faux, 403 `Host` non IP. But : figer la forme que le Swift doit lire.
3. `qr-vectors.json` : `WifiDirect.wifiUri` (échappement ZXing de `; , : \ "`), et le format **prévu** `castbridge://tv?v=1&id=<8 hex>&ip=<IPv4>&port=<n>&wd=<SSID>&wp=<mot de passe>` (W18 D-W18-5) : 10 valides, 10 invalides avec motif (`id` non hex, IP non privée, port hors 1..65535, `wd` sans préfixe `DIRECT-`, champ en double, schéma autre). Mots de passe **de test** seulement.
4. `signal-texts-vectors.json` : textes français des états de liaison et de copie produits par `C/ux/TvSignal.kt`, `C/ux/TransferStatusLine.kt`, `C/xfer/XferTexts.kt` pour une liste fermée d'entrées (le Swift doit afficher les mêmes phrases).
5. `IosVectorsTest` : régénère en mémoire et **échoue** si un fichier de `tools/ios-vectors/` diffère (message : « lancer `IosVectorsWriter` ») ; `IosVectorsWriter` a un `main` qui réécrit les fichiers.
6. `FakeTvMain` : démarre `ReceiverServer` sur `127.0.0.1:<port>` (argument, défaut 18765), dossier temporaire, PIN de test lu dans l'argument `--pin` (jamais un vrai PIN), journal minimal sur la sortie ; s'arrête sur SIGINT ou après `--hold-sec`. `tools/ios/fake-tv.sh` : construit sous verrou, puis lance la tâche `fakeTv` **hors** verrou (le serveur ne doit pas tenir le verrou de build).

## Critères d'acceptation (mutations au rapport)
- `IosVectorsTest` vert ; mutation : changer une taille de bloc dans le JSON ⇒ rouge.
- Les transcriptions `tv-api` viennent d'appels réels (pas recopiées à la main) : le rapport cite la commande.
- `tools/ios/fake-tv.sh --port 18765 --pin 000000 --hold-sec 30` répond à `curl http://127.0.0.1:18765/api/hello`.
- Recherche de secrets : aucun PIN réel, jeton réel, clé privée réelle dans les JSON.

## À ne pas faire
- Modifier le comportement de la TV ; ajouter une dépendance ; lier un port autre que 127.0.0.1 ; écrire un vrai PIN.
