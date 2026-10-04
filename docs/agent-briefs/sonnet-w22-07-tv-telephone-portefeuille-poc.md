# w22-07 — Câblage TV et page téléphone du POC : carte « Jetons », écran « Mes jetons » (convertir dans les deux sens, transférer, recevoir, saisir un bon, historique), choix de la mise dans Créer/Rejoindre, cagnotte et gain, bons poussés par le téléphone, tout derrière `quiz.online`
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (aucun solde calculé localement, motifs identiques sur les deux apps, aucun appel téléphone → API portefeuille) · statut : **ATTEND w22-03, w22-05, w22-06 et w20-05 (POC)** ; **exception de gel D-W22-13** (comme D-AM-1)
> **Groupe : W22-N1** (ordre 4) · porte : `:core:test` + `:receiver:assembleRelease -PrequireActivation=true` + `:sender:assembleDebug` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 3.5, § 7.1-7.7). Branche `claude/w22-07-tv-portefeuille`. Rapport : `docs/agent-reports/sonnet-w22-07.md`.

## Objectif (autonome)
Rendre le portefeuille **visible et utilisable** sur la TV de démonstration (720p, télécommande, 32 bits), à partir des pièces signées de w22-03 et des routes de w22-02/05/06, sans jamais calculer un solde localement, avec les mêmes motifs de refus sur la TV et sur la page `/quiz` des téléphones (W19). Tout est **derrière le drapeau `quiz.online`** (éteint par défaut en release, modèle `android/core/src/main/kotlin/castbridge/core/store/StoreFlag.kt`) ; builds **verrouillés** seulement (`-PrequireActivation=true`).

## Fichiers possédés
- **Nouveaux** : `android/receiver/src/main/kotlin/castbridge/receiver/wallet/{WalletActivity,WalletCardView,ConvertPanel,TransferPanel,ReceivePanel,VoucherPanel,HistoryPanel,StakePicker,WalletClient,WalletHub}.kt` ; ressources `android/receiver/src/main/res/layout/wallet_*.xml` si nécessaires (sinon vues programmatiques comme `GamesUi.kt`) ; `android/receiver/src/main/assets/wallet-keys.txt` et `wallet-voucher-keys.txt` (clés **publiques** ; câblage de build sur le modèle de `activation-trusted-keys.txt`, `android/receiver/build.gradle.kts:27`) ; `android/sender/src/main/kotlin/castbridge/sender/WalletVoucherPush.kt` (« Envoyer un bon à la TV » : coller le texte du bon ou lire un QR **si** un lecteur existe déjà dans l'application ; sinon coller seulement, et le dire au rapport).
- **Zone additive** : `R/HomeScreen.kt` (carte compacte « ◎ Jetons 3 450 NDEM · 12 MBOKO » quand le drapeau est allumé et la TV activée) ; `R/QuizActivity.kt` et les écrans POC de w20-05 (Créer : `StakePicker` ; Rejoindre : affichage de la mise et choix de `k` ; bandeau « Cagnotte : … » ; fin : gain ou « mises rendues ») ; `R/TvService.kt` (routes locales `GET /api/wallet` et `POST /api/wallet/voucher`, même jeton local que `/quiz`) ; `android/core/src/main/resources/castbridge/quiz/play.html` (carte compacte, cagnotte, gain, motif) ; `android/receiver/build.gradle.kts` (assets de clés).
- **Interdit** : `C/wallet/**` (consommé), `server-play/**`, `backend/**`, toute route du téléphone vers l'API portefeuille ou le service de jeu.

## Spécification
1. `WalletClient` : appels HTTPS à l'API par `Routes` (direct puis passerelle SOCKS, `android/core/src/main/kotlin/castbridge/core/connect/Routes.kt`) avec le jeton d'appareil existant ; `sync` à l'ouverture du Quiz en ligne, au retour de partie, après chaque opération, toutes les 15 min si en ligne ; envoie les bons en attente (`VoucherRedeemer.pendingForSync`) **avant** toute opération ; toute réponse met à jour `WalletCache` par son `cbw1`.
2. Écrans du § 7 de la conception, textes **exacts** : carte (§ 7.1), « Mes jetons » (4 actions + historique), **« Convertir mes jetons »** avec choix du **sens** (← → sur la ligne « Sens »), taux et frais lus de `GET /api/v1/wallet/policy`, soldes avant/après calculés **pour l'affichage seulement** à partir de l'instantané, confirmation OK avec montant en chiffres et en lettres (§ 7.3) ; Recevoir (code en très grand, compte à rebours) ; Transférer (saisie du code par groupes, destinataire masqué, confirmation) ; Saisir un bon (téléphone, clé USB `Download/castbridge-bons/*.cbv1` via `UsbImporter.kt` en lecture, saisie au clavier 5 × 7 Crockford) ; Historique (50 lignes).
3. `StakePicker` : `Mise : Aucune · NDEM · MBOKO` ; `Par joueur : 10 · 20 · 50 · 100` ; `Joueurs ici qui misent : k` ; récapitulatif « Mise bloquée : 40 MBOKO (quarante) · Solde après : 8 MBOKO » ; OK ⇒ `POST /wallet/escrow` **dans le salon**, avant « Commencer » ; le `cbe1` est joint à `create`/`join` (champ de w22-04) ; essai : MBOKO grisé avec `TRIAL_NO_MBOKO`.
4. Fin de partie : la TV reçoit `result{token}` et le poste à `POST /wallet/settle` (voie rapide ; le collecteur reste la voie sûre), puis `sync`.
5. Hors ligne : soldes de l'instantané + « Hors ligne : soldes au … » ; seules les actions possibles hors ligne (Saisir un bon) restent actives ; les autres disent leur motif.
6. Téléphone : la page `/quiz` lit `GET /api/wallet` de **sa TV** ; aucun bouton d'opération, sauf « Envoyer un bon à la TV » dans l'application (route locale de la TV, PIN / téléphone de confiance existants).
7. Accessibilité 720p : textes ≥ 28 px, focus visible, jamais la couleur seule, montants en chiffres et en lettres pour toute confirmation.

## Critères d'acceptation
- Tests JVM du cœur inchangés et verts ; build release verrouillé produit ; drapeau éteint ⇒ aucune carte ni écran visible (test de l'état du drapeau dans le cœur si une logique pure est extraite).
- **Essai sur la TV de référence** (rapport avec captures) : carte visible ; conversion N→M puis M→N ; un transfert de TV A vers TV B par code ; un bon activé en mode avion puis confirmé à la reconnexion ; une partie Créer/Rejoindre avec mise NDEM puis MBOKO, cagnotte et gain affichés sur la TV et sur un téléphone relayé ; refus `TRIAL_NO_MBOKO` lisible sur une TV d'essai.
- Recherche de source : aucune URL de l'API portefeuille ni du service de jeu dans `android/sender/**`.
- APK TV copiés dans le `Download` de la clé USB par l'exploitant (procédure habituelle).

## À ne pas faire
- Calculer ou stocker un solde ailleurs que dans l'instantané ; ajouter une dépendance ; montrer un écran hors drapeau ; produire un APK non verrouillé.
