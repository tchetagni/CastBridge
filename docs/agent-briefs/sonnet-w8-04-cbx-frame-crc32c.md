# w8-04 — Cœur : trame binaire CBX (codec strict) et CRC32C pur Kotlin

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (indépendant)
> **Groupe : W8a-1** (vague W8a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*CbxFrame*' --tests '*Crc32c*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (indépendant).** Conception : § 5.1, 5.2, 5.4 (CRC). Branche `claude/sonnet-w8-04`. Rapport : `docs/agent-reports/sonnet-w8-04.md`.

## Objectif
Le format de fil des **flux bruts** (Bluetooth RFCOMM dédié, USB accessoire) : en-tête fixe de 24 octets, types de trames, validation stricte (une trame malformée ferme la liaison), CRC32C des payloads non chiffrés. Tout en Kotlin pur, `java.util.zip.CRC32C` n'existant qu'à partir de l'API 33.

## Pourquoi (preuves)
- Le tunnel API v2 a ses propres trames 5 octets (`core/tunnel/Mux.kt:12-25`) : trop petites (16 Kio) et sans identité de transfert/bloc ; CBT1 est séquentiel (`BtProtocol.kt:22-29`).
- `minSdk = 26` (`android/receiver/build.gradle.kts:14`) : pas de `CRC32C` JCA ; `java.util.zip.CRC32` (non Castagnoli) existe mais on veut le polynôme Castagnoli (meilleure détection, vecteurs connus).

## Fichiers possédés
Nouveaux `C/xfer/{CbxFrame,Crc32c}.kt`, `CT/xfer/{CbxFrameTest,Crc32cTest}.kt` ; `C/xfer/Blocks.kt` (ajouter `Manifest.idBytes: ByteArray` = 8 premiers octets de l'hex de `id` décodés ; rien d'autre). **Hors zone** : tout le reste.

## Étapes
1. `Crc32c` : table de 256 entrées (polynôme réfléchi `0x82F63B78`), `update(crc, buf, off, len)`, `of(bytes)`, implémentation « slicing-by-4 » si simple, sinon table simple (objectif ≥ 300 Mo/s sur JVM de bureau ; mesurer dans un test ignoré par défaut). Vecteurs : `""` → `0x00000000`, `"123456789"` → `0xE3069283`, 32 octets de `0x00` → `0x8A9136AA`, 32 octets de `0xFF` → `0x62A8AB43`.
2. `CbxFrame` : `data class Header(version, type, flags, laneId, transferId: ByteArray(8), idx: Int, slice: Int, payloadLen: Int)` ; `encode(header, payload: ByteBuffer, out: OutputStream)` et `decodeHeader(in: InputStream): Header` (lecture exacte de 24 o, `EOFException` propre) ; constantes de types (`HELLO=0x01 … BYE=0x40`) et de drapeaux (`ENC=1, GZIP=2, LAST=4, WITH_HASH=8`) ; longueur en multiples de 256 o (`payloadLen % 256 == 0` sauf pour les types de contrôle où les 2 premiers octets du payload donnent la longueur exacte) ; `maxPayload = 16 Mio`.
3. Codeurs/décodeurs des payloads de contrôle (`Hello`, `HelloOk`, `Err`, `ChunkAck`, `StateReq`, `State`) selon la table § 5.2 de la conception ; identifiants (`credential`) jamais journalisés (`toString` masque).
4. Validation stricte : magie, version ≠ 1, type inconnu, longueur > max, `laneId` = 0 sur un `CHUNK`, `slice` hors plage, payload de contrôle tronqué → `CbxFrame.Malformed(reason)` (sous-classe de `IOException`) ; le lecteur **ne resynchronise jamais**.
5. Tests : aller-retour de chaque type ; vecteurs hex figés d'un `HELLO`, d'un `CHUNK` de 256 o et d'un `CHUNK_ACK` (dans le test, lisibles) ; chaque cas malformé ; CRC vecteurs ; `idBytes` de `Manifest.of("a.mp4", 1)` stable.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.CbxFrameTest' --tests 'castbridge.core.xfer.Crc32cTest'   # vert, ≥ 16 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.MultipathTransferTest'   # vert (Blocks.kt inchangé sinon idBytes)
```

## Cas limites
Payload de 0 o (PING, BYE) ; `CHUNK` dont la longueur n'est pas un multiple de 256 (refusé : le dernier bloc est rempli de zéros **par l'émetteur** jusqu'au multiple, et la longueur utile vient du manifeste : documenter dans le KDoc) ; `transferId` tout à zéro hors HELLO/PING (refusé).

## À ne pas faire
Pas de chiffrement ici (w8-05) ; pas de socket ; pas de dépendance ; ne pas toucher `Mux.kt`.

## Rapport
`STATUT`, vecteurs hex (copiés), débit CRC mesuré sur la machine de l'agent, tests avant/après.
