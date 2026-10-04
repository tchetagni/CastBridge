# wios-02 — Swift : client de l'API HTTP de CastBridge-TV (`CBTv`) : identifiants, raisons, bibliothèque, lecteur
<!-- routage architecte 2026-10-04 (vague iOS, ordre 2) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (en-têtes d'identifiant, jamais en URL ni journal) · statut : **ATTEND wios-01, wios-tv-01**
> **Groupe : WIOS** (ordre 2, en parallèle de wios-03 et wios-tv-02) · porte : `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit --filter CBTvTests` + test d'intégration contre la TV factice (`tools/ios/fake-tv.sh`)
> **Jauge : ≈ 400 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 1, § 2 F2/F10/F11/F13/F14, § 3.4, § 6.5 ATS). Branche `claude/wios-02-client-tv`. Rapport : `docs/agent-reports/sonnet-wios-02.md`.

## Objectif (autonome)
L'iPhone parle à la TV par l'API HTTP JSON documentée dans `docs/ADMIN.md` § 3 (port 8765). Écrire en Swift le client équivalent de `android/core/src/main/kotlin/castbridge/core/tv/TvClient.kt` (456 lignes, à lire) **pour les routes de v1 seulement**, avec les mêmes règles de sécurité : identifiant dans `X-CB-Pin` **ou** `X-CB-Token` (construit par un seul type, comme `TvCredential` dans `C/trust/Credentials.kt`), **jamais** dans une URL ni un journal ; adresse **IPv4 littérale** (la TV refuse un `Host` non IP : 403) ; un identifiant refusé n'est jamais renvoyé (`CredentialGate`).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridgeKit/Sources/CBTv/{TvClient,TvCredential,TvEndpointAddress,TvReason,TvHello,TvInfo,TvLibrary,TvPlayer,TvStorageCheck,TvHttp}.swift` ; tests `ios/CastBridgeKit/Tests/CBTvTests/{TvClientVectorTests,TvCredentialTests,TvReasonTests,TvClientIntegrationTests}.swift`.
- **Remplace** : `ios/CastBridgeKit/Sources/CBTv/Placeholder.swift`.
- **Interdit** : `ios/CastBridge/**`, autres cibles du paquet, tout fichier hors `ios/`.

## Spécification
1. Routes : `GET /api/hello` (sans identifiant ; lit `app`, `v`, `pinRequired` et, s'ils existent, `id`, `pinLen`, `pair`, `caps`), `GET /api/info`, `GET /api/library`, `GET /api/thumb` (202 ⇒ « pas prête », réessai borné), `POST /api/play|pause|resume|stop|seek|volume`, `POST /api/player/next|prev`, `GET /api/storage/check`, `GET /api/have`, `GET /api/quiz`, `POST /api/quiz/open`.
2. `TvCredential` : `enum { pin(String), token(String, expiresAt), missing }` ; `.missing` ⇒ la requête n'est pas envoyée ; `description`/`debugDescription` masqués (`••••`) ; test de source : aucune autre occurrence de `"X-CB-Pin"`/`"X-CB-Token"` hors de ce fichier.
3. `TvReason` : lit `{"error": …}` (forme actuelle) et, si présent, l'enveloppe W19 `{code, message_fr, retryable, retryAfterMs}` ; 401 ⇒ `retryAfter` respecté, jamais de boucle ; 409 `needsForeground` ⇒ phrase « Ouvrez CastBridge-TV sur la TV » ; 403 `trial` ⇒ phrase d'essai ; 507 ⇒ message de la TV tel quel.
4. `TvHttp` : `URLSession` éphémère, délais (connexion 3 s, requête 10 s), aucun cookie, aucune mise en cache ; `Host` construit depuis l'IPv4 ; IPv6 refusée en v1.
5. **ATS** : tester sur la TV factice (127.0.0.1) **et** documenter au rapport si une IPv4 privée (192.168.x) exige `NSAllowsLocalNetworking` seule ou davantage (à confirmer sur appareil par wios-10) ; ne **jamais** proposer `NSAllowsArbitraryLoads`.
6. Lecture des vecteurs `tools/ios-vectors/tv-api-vectors.json` : chaque corps de réponse se décode ; chaque requête attendue est reproduite octet pour octet (méthode, chemin, paramètres, en-têtes hors secret).

## Critères d'acceptation (mutations au rapport)
- `TvClientVectorTests` : 100 % des transcriptions décodées ; mutation : renommer `received` ⇒ rouge.
- `TvClientIntegrationTests` (TV factice lancée par le test ou par `tools/ios/fake-tv.sh`, ignoré proprement si absente avec message) : hello, info, PIN faux ×5 ⇒ verrou respecté sans 6e essai, lecture/pause/volume, bibliothèque.
- `TvCredentialTests` : `String(describing:)` ne contient jamais le PIN ni le jeton.

## À ne pas faire
- Bonjour, Wi-Fi, appairage (autres cahiers) ; dépendance externe ; URL avec `?pin=`.
