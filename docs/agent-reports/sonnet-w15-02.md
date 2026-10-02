STATUT: TERMINÉ
CAHIER: sonnet-w15-02 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w15-02 · COMMIT: voir git log
JETONS: inconnu
PORTE: :core:test PinKeysTest + *SendChoice* + *Trust* + journey.* + lint.* → VERT ; :sender:compileDebugKotlin → VERT
SUITE COMPLÈTE: :core:test → VERT (4 min 39 s), aucun test exclu, aucun écart
FICHIERS: android/core/src/main/kotlin/castbridge/core/trust/PinKeys.kt, android/core/src/test/kotlin/castbridge/core/trust/PinKeysTest.kt, android/sender/src/main/kotlin/castbridge/sender/PinStore.kt, android/sender/src/main/kotlin/castbridge/sender/TvLink.kt (zone savedFor/savedForHost/credentialFor/credentialForBase), docs/agent-reports/sonnet-w15-02.md
CHOIX: PinKeys existait déjà (fix-w14-05-pure) : étendu, pas dupliqué (surcharges keysOf(SavedTv,…), lookupKeys(SavedTv), resolve, ajoutées à côté des signatures à champs).
CHOIX: keysOf(tv) n'inclut PAS la boucle locale du tunnel (elle désigne « la TV par défaut », l'écrire sous toutes les TV mélangerait leurs PIN) ; resolve la gère.
CHOIX: tunnelPort = BtSshGatewayService.API_PORT (18765, constante) passé par TvLink.savedFor ; 127.0.0.1 sur un autre port, ou sans TV par défaut ⇒ null.
CHOIX: ordre de résolution bt: > IP (port absent, port de la TV ou 8765) > nom exact (nom/mDNS, casse ignorée) > nom sans « (Bluetooth) »/« (n) ». Un nom réellement « X (Bluetooth) » gagne sur le retrait.
CHOIX: ambiguïté (homonymes sans IP/BT distinguants) ⇒ la TV par défaut si elle est parmi les candidats, sinon null (jamais un jeton offert à une TV tirée au hasard).
CHOIX: PinStore.put écrit sous la clé + toutes les clés de la TV si connue (moitié de D-W13-7, sans effacement) ; get/pinOnly lisent sous n'importe quelle clé de la TV (lookupKeys). Format de castbridge_pins inchangé (ajouts de clés).
ROUGE (avant implémentation): `:core:test --tests PinKeysTest` → `e: PinKeysTest.kt:60:125 Unresolved reference 'resolve'` + 8 erreurs de signature sur keysOf(SavedTv…)/lookupKeys(SavedTv) → compileTestKotlin FAILED, aucun test exécuté.
VERT (après): PinKeysTest 21 tests, 0 échec (dont 11 nouveaux : table de 20 lignes d'origines d'écran, tunnel, homonymes, ambiguïté, IPv6, aller-retour keysOf→resolve).
Ligne proposée pour docs/REGRESSIONS.md : R-01 · jeton introuvable pour une clé d'écran (« X (Bluetooth) », IP sans port, URL, tunnel 127.0.0.1:18765) ⇒ PIN périmé présenté à la TV · PinKeysTest.resolveTable.
Test R-01 : fonction pure testée par table ; le harnais journey ne couvre pas TvLink/PinStore (sender), donc pas de test de parcours.
FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
NON FAIT / À VALIDER SUR MATÉRIEL: PinStore.put sans effacement sur PIN_WRONG (w13-08) ; écrans non touchés (w13-09) ; PinFallback non branché (hors zone).
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
