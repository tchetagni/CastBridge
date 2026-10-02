# w10-08 — CastBridge-TV : second magasin des œuvres (`WorkStore`), consommateur, routage par fonction, lecture en mémoire par loopback, routes `/api/oeuvres*`, adoption USB, essai/réduit, ordre `work.takedown`

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (w4-04 souhaité : `LotCrypt`/`LotOpener` ; sinon lot en clair au repos comme aujourd'hui, à noter)
> **Groupe : W10c-1** (vague W10c) · prérequis : w10-01 ; w4-04 souhaité ; après w7-13/w7-14 et w8-10 s'ils sont lancés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.lots.*' --tests 'castbridge.core.owner.*Routes*' --tests 'castbridge.core.policy.*' && python3 tools/tests/test_routes.py`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui (routage des lots scellés, garde loopback)

**Vague 10c · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (w4-04 souhaité : `LotCrypt`/`LotOpener` ; sinon lot en clair au repos comme aujourd'hui, à noter).** Conception : DESIGN-W10 § 1 (constat), § 3.7, § 4.4, § 5.2 (lecture), § 5.4 (étape 7), § 10.4 B, § 14. Branche `claude/sonnet-w10-08`. Rapport : `docs/agent-reports/sonnet-w10-08.md`. Dépend de w10-01. **Publier l'interface `WorkHub` dans le rapport dès le premier jour** : w10-09 et w10-10 codent contre elle.

## Objectif
La TV reçoit, installe (loué et scellé, ou aperçu en clair), conserve sous **son propre budget** (200 Mo, volume vidéo, clé USB d'abord), **lit sans copie claire durable** et balaie à l'échéance des lots de fonction `oeuvre` ; expose `/api/oeuvres` (manifeste), `/api/oeuvres/play?id=`, `/api/oeuvres/catalog` (POST, catalogue des œuvres signé, sans W5) ; respecte l'essai (aperçus seuls), le mode réduit (lecture seule) ; applique l'ordre `work.takedown`.

## Pourquoi (preuves)
- `C/lots/TvLotStore.kt:62-69` : `maxBytes` est un paramètre ; `R/LotsHub.kt:23` (`register`), `:31` (un seul `TvLotStore` sur `files/lots`).
- `C/lots/RentalApi.kt:22-40` : un seul `store` ; `/api/rental/install` ouvre avec la clé du contrat puis `store.installReceived`.
- `C/tv/ReceiverServer.kt:97-114` (`streamToken`, `streamUrl`), `:277-283` (loopback + jeton = seul chemin sans PIN), `:1131-1165` (`/stream/<name>`, `Range`), `:1192` (`player.playStream`).
- `C/owner/TrialPolicy.kt:31-36` (allowlist), `tools/routes/routes.txt` (w1-06 : chaque route classée) ; `C/owner/DegradedPolicy.kt` (w4-07, si fusionné).
- `C/policy/PolicyActions.kt` (actions d'ordres côté TV) ; `R/PolicyHub.kt`.
- `docs/STORAGE.md` § 1 : volumes, dossier de l'app sur la clé USB (`getExternalFilesDirs`).

## Fichiers possédés
Nouveaux `R/WorkHub.kt`, `R/WorkConsumer.kt`, `R/WorkStream.kt` ; `C/lots/RentalApi.kt`, `C/lots/TvLotStore.kt` (additif), `C/tv/ReceiverServer.kt` (**zone `/stream/` seulement**), `R/LotsHub.kt`, `R/RentalHub.kt` (branchement), `R/TvService.kt` (chaîne `ApiExtension` : une ligne), `C/owner/TrialPolicy.kt`, `C/owner/DegradedPolicy.kt` (si présent), `tools/routes/routes.txt`, `C/policy/PolicyActions.kt`, `CT/lots/{RentalApiTest,WorkStoreTest}.kt`, `CT/owner/{TrialRoutesTest,DegradedRoutesTest}.kt`. **Hors zone** : `R/WorksActivity.kt` (w10-09), `R/ParentalHub.kt` (w10-10), `R/PlayerActivity.kt`.

## Étapes
1. `WorkHub` (objet, miroir de `LotsHub`) : `store: TvLotStore` sur `File(<volume de la bibliothèque choisi>, "Medias/lots/oeuvre")` (**même racine que les lots média de w9-14** : `Download/CastBridge/Medias/lots/<feature>/` ; politique : volume amovible sûr s'il existe, sinon le volume interne de la bibliothèque, repli `filesDir/oeuvres` ; réévaluée au montage/démontage : une clé retirée ⇒ les œuvres qu'elle portait sont « indisponibles », jamais effacées de l'index), `maxBytes = WorkBudget.TV_MAX_BYTES`, mêmes clés publiques que `LotsHub` ; `register(WorkConsumer)` ; API publiée : `list(): List<InstalledWork>`, `manifest(): String`, `open(id): WorkSource?` (clair en mémoire via `LotOpener` si w4-04, sinon lecture du zip), `rating(id): Rating?`, `remove(id)`, `playUrl(id): String?`.
2. `WorkConsumer : LotConsumer` (feature `oeuvre`) : `install` atomique (déballe, valide `Work.parse/validate`, écrit `work.json` + média (chiffré au repos si `LotSealing` disponible), index `WorkStoreIndex`), `remove`, `installed()` (taille réelle sur disque) ; un lot complet remplace son aperçu (règle `TvLotStore` existante) ; refus clair si classification `18`.
3. Routage par fonction : `RentalApi(storeFor: (LotId) -> TvLotStore)` (additif : constructeur secondaire gardant l'ancien) ; `LotsHub` : `/api/lots/part|upload|install|remove|priority` acceptent les noms `castbridge-lot-oeuvre-*` et les dirigent vers `WorkHub.store` (le manifeste `GET /api/lots` reste celui des lots d'Apprendre/Quiz ; **`GET /api/oeuvres`** donne celui des œuvres : `maxBytes`, `usedBytes`, `works[]`, `volume`, `rejected`) ; adoption USB/Bluetooth : `TvLotStore.adoptFrom(dir)` appelé aussi pour le magasin des œuvres (paires lot + preuve `castbridge-lot-oeuvre-*`).
4. Lecture : `WorkStream` : `ReceiverServer` reçoit un **fournisseur** `streamSources: (name) -> StreamSource?` (additif) ; `/stream/oeuvre/<id>?t=<jeton>` : loopback + jeton **obligatoires** (même garde `:277-283`), `Range` sur une source mémoire (`ByteArray`) ou fichier privé ; `WorkHub.play(id)` : si `bytes ≤ WorkBudget.MEMORY_PLAY_MAX_BYTES` ou appareil non « low RAM » ⇒ mémoire ; sinon fichier `files/.play/<id>` (0600) **effacé** à l'arrêt de lecture, au démarrage de l'app et par le balayage ; `player.playStream(url, titre, pos)` ; `PlayerFeatures` : reprise de position par id d'œuvre.
5. Routes `ApiExtension` (derrière PIN / téléphone de confiance) : `GET /api/oeuvres`, `POST /api/oeuvres/play?id=&pos=`, `POST /api/oeuvres/stop`, `POST /api/oeuvres/remove?id=` (jamais une œuvre louée en cours : 409), `POST /api/oeuvres/catalog` (corps = catalogue des œuvres signé ; vérifié `SignedWorksCatalog.verify` avec `UpdateKeys.PUBLIC_KEYS`, anti-retour ; gardé `files/oeuvres/catalog.json`), `GET /api/oeuvres/catalog`.
6. Essai / réduit : `TrialPolicy` : `/api/oeuvres` et `/api/oeuvres/catalog` ouverts ; `/api/oeuvres/play` ouvert **seulement** pour un aperçu (`edition == TRIAL`) : décision D-W10-6 (constante `TrialPolicy.WORK_TEASERS_IN_TRIAL = true`) ; `/api/rental/install` d'une œuvre en essai : refusé (comme tout lot complet loué : l'essai n'a pas de contrat) ; `DegradedPolicy` : lecture + aperçus + locations en cours, pas d'installation ; `routes.txt` : lignes ajoutées et classées (test w1-06 vert).
7. Balayage : `RentalSweeper` supprime les lots loués via le magasin qui les détient : `RentalHub` enregistre `markRented` pour les œuvres avec le **bon magasin** (`sweeper` reçoit `storeFor`) ; test : à l'échéance, l'œuvre est retirée du `WorkStore`, l'aperçu **n'est pas** retiré (édition essai), les fichiers `.play` sont effacés.
8. Ordre `work.takedown {work, reason, refund}` : `PolicyActions` + `PolicyHub` : retire l'œuvre (`WorkHub.remove`, y compris l'aperçu), journalise, `notice("Cette œuvre a été retirée à la demande de son ayant droit. CastBridge vous contacte pour la suite.")` ; idempotent ; test cœur sur l'action.
9. Tests cœur : `WorkStoreTest` (budget 200 Mo distinct, œuvre de 24 Mo acceptée, 26 refusée, aperçu remplacé par le complet, retrait USB = indisponible), `RentalApiTest` (routage `oeuvre` ⇒ second magasin), routes essai/réduit, action d'ordre.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*' --tests 'castbridge.core.owner.*Routes*' --tests 'castbridge.core.policy.*'   # vert
python3 tools/tests/test_routes.py                                                       # chaque route classée
cd android && gradle --offline :receiver:compileDebugKotlin                                # compile
# TV de référence (propriétaire) : aperçu lu ; œuvre louée par livrer.py (w10-13) lue ; mémoire mesurée (adb shell dumpsys meminfo castbridge.receiver) pendant une œuvre de 25 Mo ; horloge +31 j ⇒ balayage ; retrait de la clé USB pendant la lecture ⇒ arrêt propre
```

## Cas limites
- Deux volumes (interne + clé) : un seul magasin actif à la fois ; changement de volume = index relu, œuvres de l'autre volume « indisponibles ».
- Lot `oeuvre` reçu en essai (aperçu) par Bluetooth : accepté (`TrialPolicy.btFileAllowed` accepte déjà un nom de lot).
- Mémoire insuffisante en plein `play` (`OutOfMemoryError` attrapée) ⇒ repli fichier privé, message « Lecture en mode économe ».

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas changer `LotBudget.TV_MAX_BYTES` ni le magasin d'Apprendre ; aucune route qui télécharge depuis Internet ; pas de copie claire durable ; ne pas écrire l'écran (w10-09) ; messages en français.

## Rapport
`STATUT`, **interface `WorkHub` exacte** (jour 1), mesures sur la TV (mémoire, démarrage de lecture), si w4-04 était présent, questions (D-W10-6, D-W10-8).
