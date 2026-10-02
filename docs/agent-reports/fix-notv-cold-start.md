# Correctif : « Aucune TV ajoutée » dans « Ouvrir avec CastBridge » alors qu'une TV est connectée

Branche `claude/fix-notv-cold-start` (depuis `integration/agents` 23bc458), agent Opus, 2026-10-02.

## 1. Symptôme (terrain, téléphone du propriétaire)

Telegram › « Ouvrir avec CastBridge » sur une vidéo : `OpenWithActivity` affiche « Aucune TV ajoutée : ouvrez CastBridge pour
ajouter votre TV. » et grise « Copier vers la TV » / « Déplacer vers la TV », alors que l'onglet « CastBridge TV » montre
`SMART_TV · Connectée · 49,8 Go libres` et que « Prison Break [S02 - E01] » est déjà arrivé sur la TV.

## 2. Diagnostic : la piste du démarrage à froid est **réfutée pour cet incident**

Piste reçue : `_state` initialisé à `LinkUi.NoTv` (`sender/.../TvLink.kt:104`), premier HELLO de plusieurs secondes, donc « Aucune
TV » transitoire. Ce défaut existe bien (il est corrigé, § 3.2), mais **ce n'est pas ce qui s'est passé** chez le propriétaire.

Preuves (téléphone RFCR313ABNF, CastBridge 1.2.31-beta, versionCode 61) :

1. **Le registre des TV de confiance est vide.** `logcat` au premier plan de l'app, sans aucune action de ma part :
   ```
   10-02 15:54:34.080 I/TvLink  ( 1883): reprise de Smart TV: Refused
   ```
   Ce message ne sort que de `TvLinkManager.recoverKnownTv()`, qui **retourne immédiatement si `saved.list()` n'est pas vide**
   (`TvLink.kt:154` avant correctif). Donc `SavedTvs` (préférences `castbridge_trust`, clé `tvs`) est vide dans ce processus.
   La tentative de reprise de la TV appairée en Bluetooth (« Smart TV ») est **refusée** par la TV (le téléphone n'est pas dans
   son registre de confiance).
2. **L'écran principal « vert » ne vient pas de la liaison de confiance** mais du **chemin à code (PIN)** : avec un registre vide,
   `TvHome` dessine la ligne « code » (`TvHome.kt:173-182` avant correctif) : nom = `HomeTv.name` (préférences `castbridge_home`),
   point vert = dernier `TvClient(tv.base, pin).info()` réussi via la découverte mDNS (`TvHome.kt:125-137`). C'est exactement
   l'affichage « SMART_TV · Connectée · 49,8 Go libres » (capture du 15:56).
3. **`OpenWithActivity` ne connaissait que la liaison de confiance** : `link is LinkUi.NoTv` ⇒ « Aucune TV ajoutée »
   (`OpenWithActivity.kt:53` avant correctif), boutons actifs seulement avec une `LinkUi.Connected` (`:58-59`). Même actifs, ils
   n'auraient rien donné : `TransferQueue.waitForTv()` n'accepte qu'une session de confiance et abandonne après 60 s
   (`TransferQueue.kt:112-118`).
4. **Reproduction à froid** (`am force-stop`, puis `am start -a VIEW` d'une vidéo de test de 7 ko poussée dans `/sdcard/Download`,
   supprimée ensuite) : à 15:58:19 l'activité s'ouvre ; logcat `15:58:21.498 I/TvLink (17557): reprise de Smart TV: Refused` ;
   captures à +0, +3, +11 et **+33 s** : « Aucune TV ajoutée » et boutons gris **à chaque fois**. L'état n'est pas transitoire :
   il ne se rétablit jamais (`collectAsState` fonctionne, mais aucun `Connected` n'est jamais publié, puisque aucune TV de
   confiance n'existe).

Conclusion : **cause racine = l'écran « Ouvrir avec » ne connaît pas le chemin à code (PIN) que l'écran principal utilise**, et
il prend « aucune TV de confiance » pour « aucune TV ». Lien avec l'incident du matin (PIN à ressaisir alors que l'app était
verte) : même chemin à code, point vert fondé sur un `/api/info` passé ; c'est l'objet du design W13
(`docs/coordination/DESIGN-W13-AUCUN-BLOCAGE-SILENCIEUX-2026-10-02.md`, lignes `PIN_WRONG`, `QUEUE_NO_LINK`). La ligne
`COLD_START_FALSE_STATE` (§ 2 de ce design) décrit l'hypothèse du démarrage à froid : elle reste juste comme classe de défaut
(corrigée ici), mais **l'incident du 2026-10-02 relève de « TV à code, registre de confiance vide »** ; à reporter dans W13.

Défauts latents de la même classe trouvés en lisant le code (corrigés) :
- `_state = NoTv` jusqu'au premier pas de la boucle (`TvLink.kt:104`) : faux « Aucune TV » de quelques secondes pour une TV
  de confiance enregistrée, dans **tous** les écrans qui lisent `TvLinkManager.state` au démarrage.
- `publish()` mappait `saved.default() == null` sur `NoTv` (`TvLink.kt:245`) : plusieurs TV enregistrées sans TV par défaut ⇒
  « Aucune TV ».
- `LinkDriver.restore()` reprenait un modèle persistant « notv » même avec une TV enregistrée ; or `nextAttempt(NoTv)` vaut
  `Retry.Never` et `step()` sort alors sans contacter la TV (`LinkDriver.kt:149`) : « Aucune TV » **figé** jusqu'à un geste de
  l'utilisateur (fenêtre étroite : mort du processus entre l'ajout d'une TV et la sauvegarde suivante du modèle).

## 3. Ce qui change

### 3.1 Décision pure (core, testée)
`android/core/src/main/kotlin/castbridge/core/trust/SendChoice.kt` :
- `LinkStart.view(savedCount, defaultName, stepView)` : `null` (= vraiment aucune TV) **seulement** si le registre est vide ;
  aucun pas publié, ou pas « NoTv » ⇒ « Vérification de la liaison avec <TV>… » ; plusieurs TV sans défaut ⇒ « Choisissez votre TV ».
- `SendChoices.decide(SendFacts)` ⇒ `SendChoice(route, status, copyEnabled, moveEnabled, note, action)` :
  session de confiance ⇒ file (`QUEUE`), Déplacer actif sauf Bluetooth seul ; TV de confiance en vérification / connexion /
  reconnexion / injoignable / jeton en renouvellement ⇒ **Copier actif**, Déplacer gris + ligne d'explication ; refus définitifs
  (TV réinitialisée, Bluetooth éteint, association à refaire…) ⇒ phrase du `LinkView` + « Ouvrir CastBridge », pas de file
  morte ; **TV à code** : code vérifié ⇒ envoi par code (`PIN_UPLOAD`), Copier + Déplacer ; vérification en cours / TV
  introuvable ⇒ Copier actif, Déplacer gris ; code refusé / absent / TV verrouillée ⇒ une phrase + « Saisir le code PIN de la
  TV » ; rien de connu ⇒ « Aucune TV ajoutée » + « Ajouter ma TV ».

### 3.2 Téléphone
- `TvLink.kt` : `publish(step?)` passe par `LinkStart` ; `init()` publie aussitôt l'état « Vérification… » si une TV est
  enregistrée (plus de `NoTv` initial). Appairage, jetons, cadence de la boucle : inchangés.
- `LinkDriver.restore()` (core) : un modèle « notv » persistant est ignoré quand une TV est enregistrée.
- `OpenWithActivity.kt` : lit aussi la TV du chemin à code (`HomeTv`, rendue `internal`) ; **une seule** vérification
  `/api/info` avec le code par adresse trouvée (jamais répétée après un refus : verrou de 5 essais), comme le point vert de
  l'accueil ; envoi par `UploadService.start(..., autoPlay = false, move)` pour la route à code (rien ne démarre sur la TV),
  `TransferQueue.enqueue` pour la route de confiance ; bouton d'action.
- `MainActivity.kt` / `TvHome.kt` : `TvHomeRequest` (extra `castbridge.open`) ouvre l'onglet « CastBridge TV » puis
  « Ajouter ma TV » (`adding`) ou l'assistant existant de saisie du code (`wizard`). Aucun nouveau flux PIN.
- Autres écrans lisant `TvLinkManager.state` (`TvHome`, `TvScreen`, `ActivateTvActivity`, `RentalDeliveryActivity`,
  `ParentalTab`, `ParentalScreen`, `RemoteScreen`, `TvPairScreen`, `LotsRuntime`, `TransferQueue`) : le faux `NoTv` de
  démarrage disparaît à la source (`TvLinkManager`), aucun autre changement.

## 4. Tests
- `core/src/test/kotlin/castbridge/core/SendChoiceTest.kt` (nouveau, 6 tests, table de 19 situations dont celle du propriétaire) :
  routes, boutons, action, mots attendus, « Aucune TV » jamais dit quand une TV est connue, Déplacer seulement avec une TV
  jointe, explication présente quand Déplacer est gris.
- `LinkDriverTest.persistedNoTvModelDoesNotHideASavedTv` (échoue sans le correctif de `restore`).
- `:core:test` complet : 2157 tests, 0 échec. `:sender:compileDebugKotlin` : OK.

## 5. Risques résiduels
- **Non vérifié sur l'appareil** : il faut une build téléphone (verrouillée) pour voir le nouveau dialogue ; je n'ai rien
  installé (consigne).
- Route à code : `UploadService` gère un seul envoi à la fois (comme l'accueil pour un fichier) ; un envoi par code lancé
  pendant un autre le remplace. La file (`TransferQueue`) ne sait toujours pas se replier sur le code (`QUEUE_NO_LINK`, W13).
- Le code est vérifié une fois à l'ouverture ; s'il change entre la vérification et l'envoi, l'échec relève de W13 (`PIN_WRONG`).
- La vérification par code ajoute un `/api/info` avec le PIN à chaque ouverture du dialogue (un refus compte vers le verrou de la
  TV : 1 essai par ouverture, jamais répété).
- `ActivateTvActivity` dit encore « Aucune TV n'est ajoutée dans CastBridge » quand seule une TV à code existe (l'état
  d'activation exige la liaison de confiance) : formulation à revoir dans W13, hors de ce correctif.
- Pourquoi le registre de confiance est vide chez le propriétaire (réinstallation du téléphone ? TV réinstallée ?) et pourquoi la TV
  refuse la reprise : non établi sans lire les préférences privées (non fait : elles contiennent des jetons). La reprise reste
  « Ajouter ma TV » (un « Autoriser » sur la TV).
