# Langues : envoyer un lot téléchargé à la main vers la TV (R-07)

Bug terrain : le bouton global « Envoyer à la TV » ne livrait que `LotsRuntime.needs()` (classes + lots du profil de langue) ; les cartes de « Vos données » n'avaient aucune action d'envoi.

## Constat
Le HEAD contenait déjà une liste `pickedLangues` (préférences) ajoutée à `needs()`, mais sans magasin testé, sans action par carte ni « Télécharger et envoyer ». Elle est remplacée et migrée.

## Changements
- core `lots/LotsToDeliver.kt` : `PinnedLots` (ensemble de `LotId` sur fichier, écriture sûre, survit au redémarrage) ; `LotsToDeliver.needs/decide` (pure, passe par `LotPlanner` : même budget TV, jamais d'éviction silencieuse) ; `sendButton` (décision d'UI pure).
- core `DeliveryQueue.kt` : `LotDelivery.deliver(..., only = id)` livre un seul lot, les autres restent en file.
- sender `LotsRuntime` : `pins` (migration de `lang_picked`), `needs()` = classes + profil + épinglés, `deliverLot`, `downloadAndSendLanguage`, chemin commun `deliver(only, userAsked)` ; retrait de la TV désépingle.
- sender `LotsScreen` : bouton « Envoyer à la TV » par carte (état via `LotStatusText`), « Télécharger et envoyer » dans « Disponibles sur le serveur », message global « Rien à envoyer à la TV : tout est déjà sur la TV. ».
- TV, serveur, TransferQueue, UploadService, TvLink, PinStore : inchangés.

## Preuves
Rouge par assertion avant correctif : 10 tests (8 `LotsToDeliverTest`, `PinnedLotsTest`, `SendButtonTest`) ; le test `only` a été écrit avant le correctif mais n'a pas été exécuté en rouge. Vert après : `:core:test` complet, 2498 tests, 0 échec ; `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` OK.

## Reste à vérifier sur appareil réel
Parcours P-37 : envoi d'une leçon épinglée TV allumée puis éteinte, refus d'une TV d'essai, rendu des deux boutons dans la liste (largeur d'écran).
