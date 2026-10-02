# w7-02 — Trames et codec du protocole de synchronisation `CBSY` v1

**Vague 7a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 7.1. Branche `claude/sonnet-w7-02`. Rapport : `docs/agent-reports/sonnet-w7-02.md`.

## Objectif
Le **format filaire** des trames `CBSY` (HELLO, DIGEST, PULL, DELTA, ACK, NOTIFY, PING, PONG, BULK, KNOCK, READOPT, ERROR), son codec binaire compact, le versionnage (`proto`, `caps`) et une spécification écrite (`docs/SYNC-PROTOCOL.md`, section « Trames ») **vérifiée par des vecteurs** (`tools/activation/sync-frames-vectors.json`, rejoués en Kotlin ici et en Python par w7-11).

## Pourquoi (preuves)
- `C/tunnel/Mux.kt:15-25` : style de trame binaire déjà en usage (`type u8 | flux u16 | long u16`) : rester dans cet esprit, **sans** réutiliser `MuxFrame` (sémantique différente).
- `C/tv/BtProtocol.kt:435-463` (`HelloInfo.encode/decode` lignes `clé=valeur`) : convention pour les charges utiles texte des entrées.
- `C/owner/OwnerFrames.kt:13-24` : numérotation des trames existantes à ne pas confondre (canal propriétaire, pas de collision : ce sont des canaux distincts).

## Fichiers possédés
Nouveaux : `C/link/SyncFrames.kt`, `C/link/SyncCodec.kt`, `CT/link/SyncCodecTest.kt`, `tools/activation/sync-frames-vectors.json`, `docs/SYNC-PROTOCOL.md` (créé ici : trames et codage seulement ; les sections « domaines » et « canal » sont ajoutées par w7-04 et w7-03 dans **leurs** cahiers : convenir des titres `## 1. Trames`, `## 2. Canal sécurisé`, `## 3. Domaines`). **Hors zone** : tout le reste.

## Signatures à respecter (contrat pour w7-03, w7-04, w7-13, w7-18)
```kotlin
package castbridge.core.link
object SyncProto { const val VERSION = 1; const val MAX_FRAME = 65_535; const val MAX_ENTRY = 4_096
    // capacités (bits u16)
    const val CAP_SYNC = 1; const val CAP_BULK_HTTP = 2; const val CAP_WD = 4; const val CAP_PROOF = 8; const val CAP_SHOP = 16; const val CAP_PAR_V2 = 32 }
enum class Dom(val id: Int) { ACT(1), LOTS(2), LIB(3), XFER(4), PAR(5), SHOP(6), SET(7), ICO(8) }
data class DomState(val dom: Dom, val seq: Long, val digest: ByteArray /* 8 */)
sealed class SyncFrame { data class Hello(val proto: Int, val caps: Int, val states: List<DomState>) ; data class Digest(val states: List<DomState>)
    data class Pull(val dom: Dom, val sinceSeq: Long, val max: Int) ; data class Delta(val dom: Dom, val fromSeq: Long, val toSeq: Long, val snapshot: Boolean, val more: Boolean, val entries: List<ByteArray>)
    data class Ack(val dom: Dom, val seq: Long) ; data class Notify(val dom: Dom, val seq: Long, val digest: ByteArray) ; data class Ping(val echo: Int) ; data class Pong(val echo: Int)
    data class Bulk(val dom: Dom, val descriptor: String) ; data class Knock(val phoneName: String) ; data class Readopt(val flags: Int, val householdMac: ByteArray?) ; data class Error(val code: Int, val text: String) }
object SyncCodec { fun encode(f: SyncFrame): ByteArray ; fun decode(b: ByteArray): SyncFrame /* lève SyncFormatException */ ; fun write(out: OutputStream, f: SyncFrame) ; fun read(inp: InputStream): SyncFrame? }
```
Sur le fil : `u16 len | u8 type | charge` (le `len` couvre `type + charge`) ; `u32` séquences sur le fil mais `Long` en mémoire ; chaînes = `u16 len | UTF-8`.

## Étapes
1. Types 1-12 exactement comme la table § 7.1 ; `Error` codes 1-5 avec textes **français** par défaut (`SyncTexts.error(code)`), « Version de protocole inconnue », « Domaine inconnu », « Refusé », « Trop grand », « Occupé ».
2. Codec strict : taille > `MAX_FRAME` ⇒ exception ; entrée > `MAX_ENTRY` ⇒ exception ; type inconnu ⇒ `Error(2)` **décodable** (pas d'exception : un futur type doit être ignorable) ; `read` renvoie `null` à la fin propre du flux, lève `IOException` sur coupure au milieu.
3. Vecteurs : ≥ 30 cas (`hex` attendu pour chaque trame, dont limites : 0 entrée, 1 entrée max, 8 domaines, texte UTF-8 avec accents, chaîne vide) + ≥ 10 refus (len incohérent, troncature) ; `CASTBRIDGE_WRITE_VECTORS=1` régénère comme `ActivationVectorsTest`.
4. `docs/SYNC-PROTOCOL.md` § 1 : table des trames, octets, versionnage, règle « capacité absente = fonction absente ».

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.SyncCodecTest'   # vert, tous les vecteurs rejoués
python3 -c "import json;d=json.load(open('tools/activation/sync-frames-vectors.json'));print(len(d['cases']))"   # ≥ 40
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/SyncFrames.kt android/core/src/main/kotlin/castbridge/core/link/SyncCodec.kt | wc -l   # 0
```

## Cas limites
`Delta` avec `more=true` et 0 entrée (autorisé : « encore à venir ») ; `Hello` avec 0 domaine (TV verrouillée : `caps` sans `CAP_SYNC`) ; `Readopt` sans HMAC ; `Bulk.descriptor` ≤ 2 Kio.

## À ne pas faire
Aucun chiffrement ici (w7-03) ; aucune logique de domaine (w7-04) ; ne pas toucher `Mux.kt` ni `BtProtocol.kt`.

## Rapport
`STATUT`, table finale type → octets, nombre de vecteurs, signatures.
