# wios-tv-02 — TV : appairage HTTP par PIN pour les téléphones sans Bluetooth classique (`pin-http-v1`), clé d'appareil Ed25519, jeton 12 h, compté dans les 8
<!-- routage architecte 2026-10-04 (vague iOS, ordre 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (confiance, PIN, jetons, plafond de 8) · statut : **PRÊT** (cœur pur + une ligne de délégation : permis pendant le gel)
> **Groupe : WIOS-TV** (ordre 2) · porte : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.trust.*' --tests 'castbridge.core.tv.*Pair*' --tests '*HelloCompat*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 3.2, § 3.3, § 3.4, D-IOS-3, D-IOS-13). Branche `claude/wios-tv-02-pin-http`. Rapport : `docs/agent-reports/sonnet-wios-tv-02.md`.

## Objectif (autonome)
Aujourd'hui un téléphone n'obtient un jeton (`X-CB-Token`, 256 bits, 12 h) **que** par HELLO sur RFCOMM appairé (`C/trust/HelloHandler.kt`, `docs/BT-PLUG-AND-PLAY.md` § Sécurité) et n'entre dans `TrustRegistry` (8 au plus, `MAX_PHONES`) **que** par Bluetooth. Un iPhone n'a pas de RFCOMM. Il faut un chemin HTTP **additif** : le PIN tapé une fois + une clé publique Ed25519 de l'appareil ⇒ confiance (même registre, même plafond) + jeton ; renouvellement du jeton par signature d'un défi, sans PIN.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/trust/PinPairing.kt` (pur), `android/core/src/main/kotlin/castbridge/core/tv/PairRoutes.kt` ; tests `android/core/src/test/kotlin/castbridge/core/trust/PinPairingTest.kt`, `android/core/src/test/kotlin/castbridge/core/tv/PairRoutesTest.kt` ; `tools/ios-vectors/pair-vectors.json` (clés de **test** déterministes : graines = SHA-256 de textes publics).
- **Zones additives** : `C/tv/ReceiverServer.kt` (**une** ligne de délégation vers `PairRoutes` + champs additifs de `/api/hello` : `id`, `pinLen`, `pair:["pin-http-v1"]` ; si w19-02 est fusionné, **réutiliser** ses `caps`/`id` au lieu de les dupliquer) ; `C/trust/TrustRegistry.kt` (accepter une clé `key:<kid16hex>` à côté d'une adresse Bluetooth, ≤ 20 lignes, format de fichier relu par l'ancienne version : S-7 de W19) ; `tools/routes/routes.txt` (3 lignes).
- **Interdit** : `R/`, `S/`, `ios/**`, `C/owner/**` (sauf lecture de `X25519.kt`/`Ed25519`), toute route existante.

## Spécification
1. `POST /api/pair/pin` : en-tête `X-CB-Pin` (jamais en URL) ; corps JSON `{"name": ≤ 40 car., "pub": Base64 32 o, "platform": "ios"|"other"}` ; `PinGuard` existant **par IP** ; PIN faux ⇒ 401 + `retryAfter` comme aujourd'hui ; `kid = hex(SHA-256(pub)[0:8])` ; `TrustRegistry.trust("key:"+kid, name)` : `Full` ⇒ 409 `{"error":"full","phones":[noms]}` (rien écrit, personne retiré) ; succès ⇒ 200 `{"token","ttlMs","tvId","tvName"}`, jeton émis **par le même code** que le HELLO Bluetooth (stocké haché, révoqué avec le téléphone).
2. `GET /api/pair/challenge?kid=` ⇒ `{"nonce": 32 o hex, "expiresInMs": 120000}` (usage unique, horloge **monotone**, 64 défis vivants au plus, plus vieux évincé) ; `POST /api/pair/renew` `{"kid","nonce","sig"}` : signature Ed25519 (vérifiée par `castbridge.core.update.Ed25519.verify`) sur les octets UTF-8 de `castbridge-pair-renew-v1\n<tvId>\n<nonce>` ; clé inconnue ou retirée ⇒ 401 `{"error":"unknown_phone"}` (**jamais** compté comme PIN faux) ; succès ⇒ nouveau jeton.
3. États de la TV : **essai** ⇒ autorisé (comme l'appairage Bluetooth) ; **verrouillée** (`FeatureGate` Locked) ⇒ 403 `{"error":"locked"}` (la surface verrouillée n'est pas élargie : le test `theLockedSurfaceIsExactlyTheActivationSurface` doit rester vert **sans modification**).
4. « Retirer ce téléphone » (existant, `replace`/retrait) efface la clé : un `renew` ultérieur ⇒ `unknown_phone`.
5. `pinLen` = `Pin.LENGTH` ; aucune donnée secrète dans `hello` ; `pair` permet à un client de savoir que la TV connaît `pin-http-v1` (TV ancienne : champ absent ⇒ 404 sur les routes).
6. `pair-vectors.json` : 3 clés de test, défis fixes, signatures attendues (Ed25519 déterministe), 6 cas refusés (mauvais domaine, mauvais `tvId`, défi réutilisé, défi expiré, `kid` ≠ `pub`, signature tronquée).

## Critères d'acceptation (mutations au rapport)
- `PinPairingTest` : 8 appairages réussissent, le 9e ⇒ `Full` sans écriture ; `replace` puis appairage ⇒ OK ; un `renew` avec un défi déjà utilisé ⇒ refus ; une signature sans le domaine ⇒ refus (mutation : retirer le domaine ⇒ test rouge).
- `PairRoutesTest` (vraie `ReceiverServer` port 0) : PIN en paramètre d'URL ⇒ ignoré/refusé ; 5 PIN faux ⇒ verrou 60 s ; jeton obtenu ouvre `/api/info` et **pas** `/api/ssh*`, `/api/apk/install`, `/api/update/install` ; TV verrouillée ⇒ 403.
- `HelloCompatTest` (existant) vert ; un client qui ignore les champs neufs fonctionne.
- Aucune chaîne `pin`, `token`, `sig` dans un journal (test de `toString`/`Redact`).

## À ne pas faire
- Dériver une clé du PIN ; accepter le PIN dans une URL ; évincer un téléphone ; élargir la surface verrouillée ; ajouter une dépendance ; toucher au protocole Bluetooth.
