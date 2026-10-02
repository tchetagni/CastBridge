# w1-03 — Durcissement de l'API HTTP de CastBridge-TV

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TvHardening*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : oui

**Vague 1 · Effort S (≈ 5 h) · Statut PRÊT.** Branche `claude/sonnet-w1-03`. Rapport : `docs/agent-reports/sonnet-w1-03.md`.

## Objectif
1. Le PIN n'est accepté **qu'en en-tête** `X-CB-Pin` (plus en paramètre d'URL).
2. Les requêtes dont l'en-tête `Host` n'est pas une adresse IP privée/locale (ou `localhost`) sont refusées (anti-rebinding DNS) ; option de configuration pour désactiver.
3. La rétrogradation d'APK (`force=1`) est **refusée en release** (`!BuildConfig.DEBUG`).
4. Tests cœur pour 1 et 2.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt:459` : `val given = s.headers["x-cb-pin"] ?: p["pin"]` → un `POST` `no-cors` depuis une page web du réseau local peut porter le PIN en query (CSRF), et le PIN finit dans les journaux d'accès.
- `ReceiverServer.kt:78` : `NanoHTTPD(port)` écoute sur toutes les interfaces ; aucun contrôle de `Host` (`grep -n Host` vide) → rebinding DNS depuis un navigateur.
- `android/receiver/src/main/kotlin/castbridge/receiver/UpdateInstaller.kt:133` : `if (a.code < installedCode() && !force) return err(409, …)` → avec `force=1` et le PIN, une APK plus ancienne signée par la même clé (par ex. une bêta non verrouillée) s'installe. Le chemin serveur (`:186`) refuse déjà.
- Audit : SE-6.

## Fichiers possédés
`C/tv/ReceiverServer.kt`, `C/tv/Security.kt`, `R/UpdateInstaller.kt`, `android/core/src/test/kotlin/castbridge/core/TvHardeningTest.kt`, `android/core/src/test/kotlin/castbridge/core/SecurityTest.kt`, `docs/ADMIN.md` (§ API : documenter le changement). **Hors zone** : `TvService.kt` (w1-04), `TrialPolicy.kt` (w1-06), `BtProtocol.kt`.

## Étapes
1. `Security.kt` : ajouter `object HostGuard { fun allowed(host: String?): Boolean }` — accepte vide/absent (clients non-navigateur anciens : garder la compatibilité avec le téléphone qui envoie l'IP), `localhost`, `127.0.0.1`, `[::1]`, toute IPv4 privée (10/8, 172.16/12, 192.168/16, 169.254/16) et IPv6 link-local/ULA, avec ou sans `:port`. Refuser tout nom d'hôte DNS.
2. `ReceiverServer.serve` : avant `routeGuard`, si `!HostGuard.allowed(s.headers["host"])` → 403 JSON `{"error":"Hôte non autorisé"}`. Paramètre de constructeur `hostCheck: Boolean = true` (pour les tests existants qui utilisent un nom d'hôte, si besoin).
3. `ReceiverServer.kt:459` : supprimer le repli `p["pin"]` ; garder le jeton téléphone en en-tête/paramètre tel qu'aujourd'hui (vérifier `TrustRegistry` : le paramètre `t` du `/stream` loopback est un jeton de **run**, pas le PIN : ne pas le casser).
4. Vérifier que l'app téléphone n'envoie jamais le PIN en query : `grep -rn '?pin=\|&pin=' android/sender android/core/src/main` doit être vide ; sinon lister dans le rapport (ne pas modifier le sender : hors zone) et laisser le repli **temporairement** derrière un paramètre `legacyPinQuery` par défaut `false`.
5. `UpdateInstaller.kt:133` : `val canForce = force && BuildConfig.DEBUG` ; message 409 « Version plus ancienne : refusée (rétrogradation impossible sur une TV distribuée) ».
6. Tests : `TvHardeningTest` — (a) `?pin=` correct → 401 ; (b) en-tête correct → 200 ; (c) `Host: evil.example` → 403 ; (d) `Host: 192.168.1.20:8765` → passe ; (e) 6 mauvais PIN → `401 locked` + `retryAfter`, bon PIN refusé pendant le verrou (test manquant signalé par l'audit TE-5).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.TvHardeningTest' --tests 'castbridge.core.SecurityTest' --tests 'castbridge.core.ReceiverServerTest*'   # vert
grep -n 'p\["pin"\]' android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt    # 0 hit (ou derrière legacyPinQuery=false)
cd android && gradle --offline :core:test   # suite complète verte (hors 3 instables connus, HANDOFF §« Tests instables »)
```
Observable sur TV : `curl 'http://TV:8765/api/library?pin=123456'` → 401 ; avec `-H 'X-CB-Pin: …'` → 200 ; `curl -H 'Host: evil.example' …` → 403.

## Cas limites
- Le téléphone passe par le tunnel Bluetooth (`/api/bluetooth/tunnel`, `TcpTunnel`) : l'en-tête `Host` peut être `127.0.0.1:18765` → autorisé.
- Clients anciens sans `Host` (HTTP/1.0) : autoriser l'absence.
- La page `admin.html` servie par la TV appelle l'API avec le PIN en en-tête ? Vérifier `ReceiverServer.kt:469` (`ADMIN_HTML`) ; si elle utilise `?pin=`, corriger le HTML (il est dans le même fichier, donc dans la zone).

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret. Ne pas toucher `TrialPolicy`, `TvService`, `BtProtocol`. Textes en français.

## Rapport
`STATUT`, diff résumé, sorties des commandes, liste des clients trouvés utilisant `?pin=`.
