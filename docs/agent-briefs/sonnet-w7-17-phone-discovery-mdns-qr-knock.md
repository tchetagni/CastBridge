# w7-17 — CastBridge (téléphone) : découverte v2 (NSD mDNS v2, sonde LAN, lien profond QR `castbridge://tv`, toc)

> **Amendement (Fable, 2026-10-03, DESIGN-W18)** : le lien profond QR porte **aussi** `wd=<nom>&wp=<mot de passe>` (`WdCredentials.fromDeepLinkParams`, w18-01) : `DeepLinkTv` les relit, les range par `PinBook.writeWd` et déclenche la jonction (`WdRuntime.learn`, w18-08) : c'est le **chemin zéro saisie** du premier contact (D-W18-5). `NsdDiscoveryV2` ne traverse pas un groupe P2P : la présence sur le groupe vient de `WdDnsSdClient` (w18-08) et de la sonde `/api/hello` sur 192.168.49.1 (identité par `id`, jamais par l'adresse). Lire `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.1 (f), § 5.2 ; le reste du cahier est inchangé.

**Vague 7c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-16 : contrat `LinkRuntime.onCandidates`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 4.1 (rangs 1, 3, 4, 6). Branche `claude/sonnet-w7-17`. Rapport : `docs/agent-reports/sonnet-w7-17.md`.

## Objectif
(1) `S/link/NsdDiscoveryV2.kt` : découverte `_castbridge._tcp` avec lecture du TXT v2 (`MdnsTxt.decode`), résolutions série (comme `TvDiscovery`), `MulticastLock` **seulement pendant** la fenêtre demandée (4 s par défaut, continu quand l'écran « Ajouter ma TV » est ouvert), candidats poussés à `LinkRuntime.onCandidates` ; API 34 : `registerServiceInfoCallback` quand disponible ; (2) `S/link/LanProbe.kt` : sondes `GET /api/hello` v2 (lit `id`) parallèles bornées (3), délai 1,2 s, 2 essais, résultat `Candidate(source = CACHE|MDNS)` ; (3) `S/link/DeepLinkTv.kt` : intention `castbridge://tv?...` (filtre dans le manifeste, **géré par `MainActivity`** : w7-19 ajoute l'`intent-filter`, ce cahier fournit le parseur + validation + `Candidate(source = QR)` et le SAS attendu) ; (4) `S/link/Knock.kt` : toc par `…0005` (`OwnerFrames.KNOCK`, via `TvBluetooth.with` de `OL/`) ou `POST /api/knock` ; (5) `TvDiscovery` (v1) reste pour DLNA et pour la liste « Saisissez le code » mais **consomme** `NsdDiscoveryV2` (un seul listener NSD par app : `NsdManager` refuse les doublons).

## Pourquoi (preuves)
- `S/TvDiscovery.kt:27-99` (NSD v1, lock, résolutions série, `role`) ; `S/LinkAndroid.kt:75-82` (sonde) ; `R/TvService.kt:960-979` (TXT v1 : la TV ancienne reste trouvée).
- `C/link/{MdnsTxt,DiscoveryPlanner,Candidate}.kt` (w7-09), `C/owner/OwnerFrames.kt` (`KNOCK` w7-07), `OL/TvBluetooth.kt:112-133` (`with`).
- Problème terrain 4 (IP qui change, isolation) : la re-résolution continue est la réponse.

## Fichiers possédés
Nouveaux : `S/link/NsdDiscoveryV2.kt`, `S/link/LanProbe.kt`, `S/link/DeepLinkTv.kt`, `S/link/Knock.kt`, `CT/link/DeepLinkTvTest.kt` (parseur pur : mettre le parseur dans `C/link/TvDeepLink.kt`, nouveau, pour le tester en JVM). Modifié : `S/TvDiscovery.kt` (délègue). **Hors zone** : `S/TvLink.kt`, `S/link/LinkRuntime.kt` (w7-16), `S/MainActivity.kt` (w7-19), manifeste (w7-16 ; demander l'`intent-filter` par rapport si w7-19 ne l'a pas).

## Signatures à respecter
```kotlin
class NsdDiscoveryV2(ctx: Context, val onCandidate: (Candidate) -> Unit) { fun scan(durationMs: Long); fun continuous(on: Boolean); fun stop() }
object LanProbe { fun probe(bases: List<String>, timeoutMs: Long = 1_200): List<Candidate> /* bloquant, IO */ }
// C/link/TvDeepLink.kt (pur)
data class TvDeepLink(val id: String, val bt: String?, val ip: String?, val port: Int, val pub: ByteArray, val sas: Int?)
object TvDeepLinkCodec { fun parse(uri: String): TvDeepLink? /* null si invalide ; jamais d'exception */ ; fun encode(l: TvDeepLink): String }
object Knock { fun send(ctx: Context, c: Candidate, phoneName: String, pub: ByteArray): Result<String> /* bloquant */ }
```

## Étapes
1. NSD v2 : `onServiceResolved` ⇒ `MdnsTxt.decode(attributes)` ; `host` IPv4 seulement ; v1 (sans `id`) ⇒ `Candidate(id=null)` ; perte ⇒ `Candidate` retiré (callback `onLost`) ; aucune résolution parallèle.
2. `LanProbe` : lit `/api/hello` v2 ; `id` renvoyé ⇒ identité ; échecs comptés pour `IsolationObs.lanFailures` (via `LinkRuntime.journal()`).
3. `TvDeepLinkCodec` : validation stricte (id 8 hex, bt format `TrustRegistry.isAddress`, ip IPv4, port 1-65535, pub 32 octets b64url, sas 0-9999) ; tests ≥ 15.
4. `Knock` : Bluetooth d'abord si appairé, sinon LAN ; réponse texte affichée par w7-19.
5. `TvDiscovery` v1 : garde son API (`tvs`, `find`, `restart`) mais s'abonne à `NsdDiscoveryV2` (un listener).

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.DeepLinkTvTest'
grep -rn 'discoverServices(' android/sender/src/main/kotlin/castbridge/sender/ | wc -l   # 1 (un seul listener NSD)
```
Observable : TV v1 (ancienne) et TV v2 toutes deux listées ; TV qui change d'IP ⇒ nouveau candidat ≤ 5 s avec l'écran ouvert.

## Cas limites
Deux TV avec le même nom mDNS ⇒ distinguées par `id` ; `MulticastLock` jamais tenue > 60 s hors écran ouvert ; QR d'une autre TV que celle attendue ⇒ `id` ≠ épinglé ⇒ message (w7-19).

## À ne pas faire
Pas de `startDiscovery` BT ici ; pas de bibliothèque QR (le scan est fait par l'appareil photo du système et le lien profond) ; pas de texte utilisateur.

## Rapport
`STATUT`, signatures, comportement NSD par version Android testée.
