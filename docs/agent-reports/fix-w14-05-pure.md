# fix-w14-05-pure : correctifs d'audit des fonctions pures d'état (core seulement, non branchées)

Branche claude/fix-w14-05-pure (depuis integration/agents 6d4ca89). `xfer/ReceiveCard.kt` non touché.

## Constats corrigés
- (2) HomeLinkView : `HomeFacts.credentialIsToken` décide le chemin de confiance (plus `savedCount > 0`). Jeton + 401 = puce « vérification » + RECONNECTING, jamais Connectée, pas d'assistant. Cas mixte (TV enregistrée, pas de session, sonde avec le code) : 401 = assistant avec CODE_CHANGED. Ligne fausse « registre : 401 … Connected » remplacée.
- (3) Étiquette R-01 réécrite « corrigé par construction (motif rendu) ». Défaut reproduit isolé : `ManualTvViewTest.regressionR01_neverAskedCountsAsReachableAndSendable` (à inverser par w14-06) ; retiré de la table.
- (4) PinKeys : `keysOf(..., hosts: List<String>, port)`, `lookupKeys` (normalisées puis brutes, ex. « 192.168.0.5 » écrit par TvScreen:77), hôte/IPv6 documentés (IPv6 entre crochets), `PinFallback.choose` (un jeton refusé d'une TV de confiance ne retombe JAMAIS sur l'ancien code).
- (5) ActivateTargetView : `Target` porte `base` (de session) et `credentialKey` ; `decide(..., sessionBase)` ; LAN sans base de session = NOT_JOINED.
- (6) PairScreenView : `pinTvName` dans `decide` et `emptyListText` (R-02) ; `request(action, address)` : REASSOCIATE porte l'adresse, sinon ADD_TV (R-03).
- (7) `HomeFacts.FRESH_MS = 15 000` ; KDoc : w14-06 doit faire avancer `nowMs` par minuteur et utiliser une horloge monotone.
- (8) XferTexts : `Waiting` garde « · n % » (sent/total) ; `XferState.Queue` (titre « CastBridge : envois vers la TV », « Envoi en cours »).
- (9) `lastOkAt`/`nowMs` retirés de ManualTvFacts (inutilisés ; la fraîcheur est la charge de l'appelant).
- (10) Libellés : « Code incorrect : retapez le code affiché sur la TV. », « Trop d'essais : la TV est verrouillée 60 s, attendez puis réessayez. », CODE_CHANGED = « Code refusé par la TV : saisissez le code affiché sur la TV. ».

## À faire par w14-06
Remplir `credentialIsToken` avec `TvAuth.isToken(client.pin)` ; minuteur + horloge monotone pour `nowMs` ; appeler `PinFallback.choose` à la place du repli de `PinStore.get` et chercher avec `lookupKeys` (ou migrer) ; passer `sessionBase`/`credentialKey` à ActivateTvActivity ; passer `pinTvName` à TvPairScreen ; retirer la ligne REASSOCIATE sans adresse ; inverser `regressionR01_*` ; brancher `XferState.Queue` dans TransferQueueService ; retirer les anciens libellés « PIN incorrect » des écrans. SendChoice.kt garde encore « code PIN » (non unifié ici).
