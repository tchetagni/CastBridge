# w7-19 — CastBridge (téléphone) : première liaison en 3 touches, chorégraphie des permissions, ré-adoption / identité, « Réparer la connexion »

**Vague 7c · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-16/17/18/20 : contrats `LinkRuntime`, `SyncClient`, `CdmAssociation`, `LinkChip`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 8.1-8.3, § 4.4, D-W7-7. Branche `claude/sonnet-w7-19`. Rapport : `docs/agent-reports/sonnet-w7-19.md`.

## Objectif
(1) `MainActivity` : onglet « CastBridge TV » **par défaut** tant qu'aucune TV n'est liée ; `intent-filter` `castbridge://tv` (lien profond QR) ⇒ `AddTvFlow` pré-rempli ; retrait de la demande `POST_NOTIFICATIONS` au démarrage de l'onglet DLNA (`:201-202`) ; appel `LinkChip()` (w7-20) dans la barre ; (2) `TvPairScreen.AddTvFlow` v2 : étapes § 8.1 (textes `LinkTexts`), liste fusionnée SDP + mDNS v2 (`LinkRuntime.onCandidates`), cas « connue par cette TV ? » ⇒ **Ré-adopter** (code foyer facultatif selon D-W7-5, puis « Validez sur la TV : code 47 12 »), bouton « Scanner le code sur la TV » (ouvre l'appareil photo système : `MediaStore.ACTION_IMAGE_CAPTURE` ? **non** : simple texte « Ouvrez l'appareil photo et visez le code sur la TV » + bouton « Ouvrir l'appareil photo » via `Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)`), « Envoyer un toc » quand la TV est trouvée mais fermée ; fin : son + animation 600 ms + `CdmAssociation.offerOnce` + demande `POST_NOTIFICATIONS` **après** la liaison ; (3) `PermissionFlow` : une demande par processus, détection « ne plus demander », explication ; (4) écran **« Réparer la connexion »** : `SelfTest.run(LinkRuntime.observations())` ⇒ une phrase + un bouton (`RepairAction` → intention), journal `SELF_TEST` ; (5) dialogue **identité changée** (`SyncEvent.IdentityChanged`) : empreinte ancienne/nouvelle, « Confirmer » ⇒ ré-épingle + relance.

## Pourquoi (preuves)
- `S/MainActivity.kt:115-131` (onglets, défaut 0 = DLNA), `:201-202` (notification au démarrage), `:99-104` (geste super : **conserver**) ; `S/TvHome.kt:406-456` (`FirstConnection`), `:94-110` ; `S/TvPairScreen.kt:139-230` (`AddTvFlow`), `:232-280` (`PairProgress`), `:59-98` (`TvLinkStatus` : actions existantes à garder) ; `S/BtPermission.kt:37-65` (`rememberBtPermission`) ; `C/trust/PairFlow.kt:18-32` (`PairStep`).
- `C/link/{SelfTest,LinkTexts,Readoption,Identity}.kt` (7a), `S/link/{LinkRuntime,CdmAssociation}.kt` (w7-16), `S/link/{Knock,DeepLinkTv}.kt` (w7-17), `S/link/SyncClient.kt` (w7-18).

## Fichiers possédés
Modifiés : `S/MainActivity.kt`, `S/TvPairScreen.kt`, `S/TvHome.kt` (`FirstConnection` → appel du nouveau flux ; carte de liaison inchangée), `S/BtPermission.kt`. Nouveaux : `S/link/PermissionFlow.kt`, `S/link/RepairScreen.kt`, `S/link/IdentityDialog.kt`, `S/link/ReadoptFlow.kt`, `android/sender/src/main/res/raw/link_ok.ogg` (son court libre de droits, ≤ 20 Ko, origine indiquée dans le rapport). **Hors zone** : `S/TvLink.kt`, `S/link/LinkRuntime.kt`, manifeste (w7-16 : demander l'`intent-filter` `castbridge://tv` par rapport ; **si** w7-16 est déjà fusionné, l'`intent-filter` est ajouté par ce cahier dans `AndroidManifest.xml` et l'index le note comme exception validée par le coordinateur), `S/link/ConnectionScreen.kt` (w7-20), `S/ActivateTvActivity.kt` (w7-22).

## Étapes
1. Onglet par défaut : `remember { if (TvLinkManager.saved.list().isEmpty()) 1 else prefs… }` ; lien profond ⇒ `TvDeepLinkCodec.parse` ⇒ `AddTvFlow(prefill)`.
2. `AddTvFlow` v2 : liste unique de candidats (`DiscoveryPlanner.merge`) avec puce « connue par cette TV ? » quand `id` correspond à une TV **jamais** enregistrée ici mais qui répond `HINT_SAME_INSTALL`/`readoptable` ; « Lier » ⇒ `TvLinkManager.pair` (existant) ; « Ré-adopter » ⇒ `ReadoptFlow` (CBSX + `READOPT`, attend `Autoriser` sur la TV, affiche le SAS) ; QR ⇒ LAN + SAS implicite ; toc ⇒ `Knock.send` ⇒ « Demande envoyée : validez sur la TV ».
3. Fin de liaison : `PairStep.Done` ⇒ son (`SoundPool`, respect du mode silencieux) + animation ⇒ `CdmAssociation.offerOnce` (texte `LinkTexts.cdmWhy`) ⇒ `PermissionFlow.notificationsOnce` (33+) ⇒ barre « Synchronisation… n/8 » lue dans `SyncClient.ageOf`.
4. `PermissionFlow` : `BLUETOOTH_CONNECT` (+ `SCAN` dans le flux seulement), `NEARBY_WIFI_DEVICES` (33+) avant WD, jamais de localisation ; état `BLOCKED` ⇒ carte + « Ouvrir les réglages de l'app ».
5. `RepairScreen` : bouton « Réparer la connexion » sur `TvLinkStatus` quand `tone == BAD` ou `TvUnreachable` > 1 min ; verdict ⇒ action (`ENABLE_BT` → `ACTION_REQUEST_ENABLE`, `OPEN_WIFI` → `ACTION_WIFI_SETTINGS`, `USE_WIFI_DIRECT` → w7-21 `WifiDirectAuto.start`, `OPEN_OEM_BATTERY` → `OemBattery.forManufacturer`, `DISABLE_VPN` → `ACTION_VPN_SETTINGS`, `CONFIRM_TV` → `IdentityDialog`, `READOPT` → `ReadoptFlow`, `REASSOCIATE` → existant) ; bouton « Copier le rapport » (journal).
6. Tests : logique pure ajoutée dans `C/link/` ? (le choix « connue par cette TV ? » = `Readoption`/`Candidate` : déjà pur) ; `:sender` compile ; test de source : aucune chaîne de refus/aide hors `LinkTexts`/`LinkText`/`SelfTest` ; parcours sur appareil : 3 touches chronométrées ; QR ; ré-adoption après `pm clear castbridge.sender`.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin
grep -rn 'POST_NOTIFICATIONS' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt | wc -l   # 0
grep -rn 'Réparer la connexion' android/sender/src/main/kotlin | grep -v 'LinkTexts' | wc -l   # 0 (libellé centralisé)
grep -c 'TapSequence\|superTap' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # ≥ 1 (geste conservé)
```
Observable (S21+ Android 15, Tecno Android 12, TV de référence) : première liaison ≤ 45 s, 3 touches + dialogues système ; refus de permission ×2 ⇒ carte, pas de boucle ; `pm clear` puis ré-adoption sans saisie de code (hors code foyer si D-W7-5) ; TV réinstallée ⇒ dialogue identité ⇒ Confirmer ⇒ Autoriser sur TV ⇒ lié.

## Cas limites
Aucun candidat après 20 s ⇒ « Ma TV n'apparaît pas » (QR, adresse manuelle existante, « Saisir le code ») ; téléphone sans Bluetooth ⇒ parcours QR/LAN seul ; D-W7-5 non tranchée ⇒ champ code foyer **masqué**, ré-adoption = fenêtre + Autoriser.

## À ne pas faire
Pas de bibliothèque de scan QR ; pas de localisation ; ne pas supprimer « Saisir le code de la TV » ni « adresse manuelle » (secours) ; ne pas toucher au geste super-admin.

## Rapport
`STATUT`, captures de chaque étape, chronométrages, origine du son, `À BRANCHER`.
