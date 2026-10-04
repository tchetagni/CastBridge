# w23-06 — Avis d'activation signé par la TV (`cbx1` type `actnotice`), scellement pour la clé publique « relais » du serveur, reçu signé (`cbx1` type `receipt`), route `POST /api/v1/activations/relay`, rapport v2
<!-- routage architecte 2026-10-04 (W23-B) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **obligatoire** (cryptographie, forme canonique, rejeu) · statut : **ATTEND w23-05** (interface du registrar ; des faux conformes suffisent pour démarrer)
> **Groupe : W23-B** (ordre 2) · porte : `cd android && gradle :core:test --tests 'castbridge.core.activation.Notice*'` + `cd backend && mvn -o test -Dtest='castbridge.server.activations.Notice*,Relay*'` + `python3 tools/activation/verify_vectors.py`
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5 j)

**Conception** : `DESIGN-W23B-…` § 2.3, § 2.4, § 2.5, § 6.2, § 10. Branche `claude/w23-06-avis-scelle`. Rapport : `docs/agent-reports/sonnet-w23-06.md`.

## Fichiers possédés
- **Nouveaux (cœur, pur)** : `android/core/src/main/kotlin/castbridge/core/activation/{ActivationNotice,NoticeSeal,Receipt}.kt` (construction, scellement X25519 + HKDF-SHA256 + AES-256-GCM avec `C/owner/X25519.kt`, ouverture du reçu) + tests.
- **Nouveaux (serveur)** : `backend/.../activations/{NoticeVerifier,RelayBox,ReceiptSigner,RelayController}.java` ; vecteurs `tools/activation/notice-vectors.json` (clés de TEST dérivées de textes publics) ; extension de `tools/activation/verify_vectors.py` (types `actnotice`, `receipt`, boîte).
- **Zone additive** : `A/ReportController.java` (v2 : champ `notice`, v1 inchangée mais **jamais** enregistrante) ; `docs/ACTIVATION-FORMAT.md` (nouveaux types, section additive) ; `application.yml` (bloc `castbridge.activations.relay.*`, clé `relay-box.key` lue dans le dossier des secrets).
- **Interdit** : toute modification des types existants (`activation`, `command`, `order`, `revocation`, `proof`), Android hors `core/activation`, écrans.

## Spécification
Corps exacts du § 2.3 (forme canonique, ordre des lignes, bornes : ≤ 4 jetons, ≤ 16 Ko) ; `kid` = identifiant de la clé d'installation, `installKey` dans le corps ; scellement § 2.4 (AAD `{v,kind,id,createdAt,size}`, `sel = noticeId`, info `castbridge-actrelay-v1`) ; reçu : signé par la clé des **ordres** (portée `POLICY`), `nonce = noticeId`, `target=device`, corps § 2.4, refusé s'il ne correspond à aucun avis ouvert ; route : jeton du téléphone (`app=phone`) ou de la TV, ≤ 16 objets, ≤ 256 Ko, 200 objets et 1 Mo / jour / téléphone, 16 avis / jour / TV, rejeu ⇒ `duplicate` avec le même reçu.

## Critères d'acceptation
- Trois implémentations (Kotlin, Java, Python) produisent et acceptent les mêmes octets sur tous les vecteurs, refus compris (signature, forme, clé d'installation ≠ `kid`, facteurs ≠ code, taille, avis expiré, boîte altérée d'un octet, mauvaise clé de relais, reçu pour un autre avis, reçu d'une clé sans `POLICY`).
- `RelayControllerTest` : un téléphone non authentifié ⇒ 401 ; objet altéré ⇒ `rejected` sans écriture ; deux téléphones, même avis ⇒ un seul enregistrement, deux reçus identiques ; débits contrôlés par appareil **avant** le budget global.
- Mutation : accepter un reçu sans avis ouvert ⇒ échec ; ne pas lier l'AAD ⇒ échec.
