# w23-08 — CastBridge-TV : câblage du moteur d'ordres (`PolicyHub`), accusés signés par la clé d'installation, objets descendants (reçu, révocation, `cbw1`), `downlink.pause`, états du portefeuille « créé / en attente de notification »
<!-- routage architecte 2026-10-04 (W23-B) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **obligatoire** · statut : **ATTEND w23-07** et le client portefeuille TV **w22-07** ; **exception de gel** : écran portefeuille (D-W22-13, déjà accordée) et insigne d'activation (texte d'état, à demander)
> **Groupe : W23-B** (ordre 4) · porte : `cd android && gradle :core:test --tests 'castbridge.core.policy.*' --tests 'castbridge.core.wallet.*' :receiver:assembleDebug -PrequireActivation=true`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,2 j)

**Conception** : `DESIGN-W23B-…` § 2.5 (affichage), § 4, § 5.4, § 9.2-9.6. Branche `claude/w23-08-tv-ordres`. Rapport : `docs/agent-reports/sonnet-w23-08.md`.

## Fichiers possédés
- **Zone additive TV** : brancher `R/PolicyHub.kt` comme le dit `docs/ORDRES.md` § 13 (init, trames `…0004`, horloge, `PolicyGate.effective`) — **aujourd'hui aucun appel n'existe hors de ce fichier** ; routes `GET /api/tele/inbox`, `POST /api/tele/acks` (servies aux seuls téléphones de confiance) ; accusé signé (`InstallSigner`, texte § 9.5) ; `downlink.pause/resume` ; application des objets `receipt` (état « notifiée »), `revocation`, `cbw1` (`WalletCache`).
- **Cœur** : `android/core/src/main/kotlin/castbridge/core/wallet/WalletView.kt` (états `WALLET_CREATED`, `NOTIFY_PENDING`, `REGISTRATION_REVIEW`, `SEAT_OVER_QUOTA`, `CATCHUP_HELD`, textes du § 5.4, aucun chiffre avant le premier `cbw1`) + tests.
- **Interdit** : tout nouvel écran ; toute action hors liste blanche ; toute lecture de fichiers de l'utilisateur.

## Critères d'acceptation
- Les tests d'ORDRES § 10 passent **sur la TV câblée** (rejeu, séquence, portée, cible, fenêtre, liste blanche, suspension sans perte).
- `AckSignatureTest` : accusé vérifiable par la clé d'installation ; altéré ⇒ refusé par le serveur (vecteur partagé avec w23-07).
- `WalletViewTest` : activation installée sans reçu ⇒ « Portefeuille créé · en attente de la première synchronisation » puis « Jetons en attente de notification de votre activation » ; reçu `pending OVER_QUOTA` ⇒ texte dédié ; premier `cbw1` ⇒ soldes « au JJ/MM HH:MM » ; la TV n'affiche **jamais** un montant calculé localement (mutation ⇒ échec).
- `downlink.pause` : seuls `downlink.resume`, `receipt`, `revocation` passent jusqu'à `until`.
- Build verrouillé compilé ; APK copié dans le `Download` de la clé USB par le coordinateur.
