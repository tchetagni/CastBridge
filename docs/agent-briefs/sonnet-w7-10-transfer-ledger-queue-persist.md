# w7-10 — `TransferLedger` (grand livre des transferts de la TV) et persistance de la file d'envoi du téléphone (cœur)

**Vague 7a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 2 (problème 7), § 7.2 (`xfer`). Branche `claude/sonnet-w7-10`. Rapport : `docs/agent-reports/sonnet-w7-10.md`.

## Objectif
(1) `TransferLedger` pur : un enregistrement par transfert entrant sur la TV (`id, name, total, done, lane, state ∈ {RECEIVING, INTERRUPTED, DONE, FAILED, ABANDONED}, err, startedAt, updatedAt`), persisté (`TextStore`), écriture ≤ 1/s, rétention 7 j / 100 entrées, reprise possible tant que le `.part`/`.cbx` existe ; (2) `TransferQueueModel` (téléphone) **persistable** : `encode()/decode()` sans URI de contenu éphémère invalide (garde l'URI et le nom ; à la relecture une entrée dont l'URI n'est plus lisible passe `FAILED « fichier déplacé »`) ; (3) textes de la ligne d'état TV dérivés du grand livre (« Réception de film.mkv 42 % · Wi-Fi », « Interrompu à 42 % : reprise possible », « Reçu ») ; (4) hooks pour `BtProtocol.serve` et `/api/upload`/`/api/transfer` **via interfaces** (câblés par w7-15).

## Pourquoi (preuves)
- `R/BtServer.kt:113-127` : la progression n'est qu'une chaîne `status("Bluetooth : réception … %")` écrasée par « prêt » ou par le transfert suivant : cause du « reste à prêt à recevoir ».
- `C/tv/TransferQueue.kt` (`TransferQueueModel`, `QueueItem`, `QueueStatus`) et `S/TransferQueue.kt:27` : file en mémoire, perdue à la mort du processus.
- `C/xfer/PartAssembler.kt:31-33,219-251` (`.cbx/<id>.state`) et `C/tv/BtProtocol.kt:203-230` (`.part`) : la **reprise** existe déjà au niveau fichier ; le grand livre ne fait que la rendre visible et synchronisable.
- `C/owner/SafeFile.kt` : écriture atomique à réutiliser côté Android (via `TextStore`).

## Fichiers possédés
Nouveaux : `C/link/TransferLedger.kt`, `CT/link/TransferLedgerTest.kt`. Modifié : `C/tv/TransferQueue.kt` (ajout `encode/decode`, champ `enqueuedAt`), `CT/tv/TransferQueueTest.kt` (créer si absent). **Hors zone** : `C/xfer/**`, `BtProtocol.kt`, `R/**`, `S/**`.

## Signatures à respecter (contrat pour w7-04 `XferDomain`, w7-15, w7-21)
```kotlin
package castbridge.core.link
enum class XferState { RECEIVING, INTERRUPTED, DONE, FAILED, ABANDONED }
data class XferRecord(val id: String, val name: String, val total: Long, val done: Long, val lane: String, val state: XferState, val err: String?, val startedAt: Long, val updatedAt: Long)
class TransferLedger(store: TextStore, val now: () -> Long, val maxEntries: Int = 100, val maxAgeMs: Long = 7 * 86_400_000L) {
    fun begin(name: String, total: Long, lane: String, resumeFrom: Long = 0): String /* id = sha256(name|total)[0:16] */
    fun progress(id: String, done: Long)       // coalescé : persiste au plus 1/s, en mémoire tout de suite
    fun interrupted(id: String, err: String?); fun done(id: String); fun failed(id: String, err: String); fun abandoned(id: String)
    fun list(): List<XferRecord>; fun active(): XferRecord?; fun statusLine(): String? /* texte français de la ligne 1-bt / 7-xfer */
    fun entries(): List<Map<String, String>>   // pour XferDomain (w7-04)
    var onChange: (XferRecord) -> Unit }
// TransferQueueModel (additif)
fun encode(): String ; companion fun decode(text: String, now: Long): TransferQueueModel
```

## Étapes
1. `TransferLedger` + format texte (`id\tname\ttotal\tdone\tlane\tstate\terr\tstarted\tupdated`), `SafeFile` par `TextStore` ; un `begin` sur un `id` déjà `INTERRUPTED` reprend l'entrée (`resumeFrom`) ; `ABANDONED` quand le `.part` a été supprimé par l'appelant.
2. `statusLine()` : priorité `RECEIVING` > `INTERRUPTED` (< 24 h) > dernier `DONE` (< 1 min) > `null` ; textes français avec pourcentage et voie (étiquette `RoutePolicy.label` si disponible, sinon la chaîne `lane`).
3. `TransferQueueModel.encode/decode` : une ligne par item, URI encodée, états `RUNNING` → `WAITING` à la relecture (reprise), `keepFinished` respecté.
4. Tests (≥ 30) : reprise après « mort du processus » (relecture), coalescence 1/s (horloge fausse), rétention, `statusLine` par état, encode/decode aller-retour, item dont l'URI est illisible ⇒ `FAILED` avec texte.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.TransferLedgerTest' --tests 'castbridge.core.tv.TransferQueueTest'   # vert
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/TransferLedger.kt | wc -l   # 0
```

## Cas limites
Deux transferts du même nom en parallèle (Wi-Fi + Bluetooth, `FileLocks`) ⇒ même `id`, l'un attend : le grand livre garde **un** enregistrement ; `total` inconnu (0) ⇒ pourcentage omis ; nom > 120 caractères ⇒ tronqué dans le texte seulement.

## À ne pas faire
Ne pas toucher au moteur `xfer` ni à `BtProtocol.serve` (w7-15 branche les hooks) ; pas de fil, pas d'Android.

## Rapport
`STATUT`, format du fichier, signatures, textes de `statusLine`.
