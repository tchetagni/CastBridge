STATUT: TERMINÉ
CAHIER: sonnet-w6-01 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w6-01 · COMMIT: voir git log
JETONS: inconnu
PORTE: gradle :core:test --tests '*PhoneGate*' --tests '*PhoneMatrix*' → VERT (29 s)
SUITE COMPLÈTE: gradle :core:test → VERT (4 min 36 s) ; écarts : aucun ; 31 cas neufs (20 + 11)
FICHIERS: android/core/src/main/kotlin/castbridge/core/owner/PhoneGate.kt ; android/core/src/test/kotlin/castbridge/core/owner/PhoneGateTest.kt ; android/core/src/test/kotlin/castbridge/core/owner/PhoneMatrixTest.kt ; docs/agent-reports/sonnet-w6-01.md
CHOIX:
- API : PhoneFeature(minimalAllowed, agentAllowed, tvFacing) ; MINIMAL_WHITELIST / AGENT_WHITELIST ; ProofRequirement ; ProofSummary(tvCode, tvName, verifiedAt, validUntil, endsAt?).validAt ; PhoneGateState {NotRequired, Grace(untilMs), Minimal(MinimalReason), Linked(proofs), Agent, Super} ; PhoneGate.state / canUse / columnOf / cell(feature, tv, sync, superSession, phoneGrace=false) / targetRule(feature, tvs, active, superSession=false) ; Cell {OPEN, TV_DECIDES, CLOSED(id), PARTIAL(id)} ; PhoneColumn A..H ; PhoneMessages (constantes).
- Colonne C = TRIAL ou GRACE (GRACE remplace M-TV-TRIAL par M-TV-GRACE) ; colonne E = LOCKED (M-TV-LOCKED), SUSPENDED (M-TV-SUSPENDED), DEGRADED (M-TV-ENDED). F seulement pour une TV de PRODUCTION injoignable ; une TV d'essai injoignable reste C.
- H : jamais fermé sauf fonction tvFacing dont la cellule F est fermée et la TV injoignable (CLOSED M-TV-UNREACHABLE). LOTS_SYNC_FULL en H reste OPEN (livraison différée).
- Grâce du téléphone : paramètre phoneGrace (lu comme D ; OPEN d'une fonction tvFacing devient TV_DECIDES).
- targetRule hors tvFacing : OPEN > TV_DECIDES > PARTIAL > CLOSED sur toutes les TV (inclut la règle « une TV de production prouvée »).
- Lignes du § 3.7 groupées : TRANSFER_MULTIPATH = ligne « envoi » ; TV_LIBRARY_MANAGE identique ; ASSISTANT_IA, DOWNLOADS, TOKENS, SHOP_ORDER, jeux = « valeur propre ».
NON FAIT / À VALIDER SUR MATÉRIEL: rien de matériel ; branchement Android, interrupteur REQUIRE_TV_PROOF (w6-11), textes (w6-02).
QUESTION: aucune
CELLULES DOUTEUSES (à auditer) :
- TRIAL_LOTS_SYNC : le § 3.7 n'a qu'une ligne « lots » ; j'ai mis B, C, F, G, H = OPEN (lots d'essai), A et E = fermé, sinon la fonction minimale ne remplirait pas l'essai.
- ASSISTANT_IA : ✖ sans identifiant dans le § 3.7 ; j'ai repris M-NO-TV, M-SYNC-FIRST, M-TV-TRIAL, M-TV-ENDED, M-PROOF-EXPIRED comme les fonctions propres au téléphone.
- SUPER_ADMIN_ENTRY en H (« — » dans le tableau) : OPEN. AGENT_* : OPEN dans toutes les colonnes (leur porte est l'état Agent, pas la TV). PARENTAL_DASHBOARD H « si désigné » : OPEN (désignation hors cœur).
- PARTIAL : identifiants M-PROOF-EXPIRED (parental G, lots G), M-TV-ENDED (TV_ADMIN E), M-SHOP-READ-ONLY, M-TRIAL-LOTS-ONLY.
- SHOP_BROWSE en A est fermé (M-NO-TV) alors qu'il est dans la liste minimale : la porte d'état (canUse) et la matrice se cumulent.
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
