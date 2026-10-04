# w21-01b — Cœur pur de la remise des statistiques : compression à dictionnaire (`Packer`), enveloppe scellée AES-256-GCM TV → serveur (`Sealer`), file scellée bornée de la TV (`SealedOutbox`), file coursier du téléphone (`CourierQueue`), accusés HMAC, codec de remise par morceaux
<!-- routage architecte 2026-10-04 (W21, amendement du propriétaire sur l'envoi) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (cryptographie, dérivation de clé, le téléphone ne lit ni ne fausse rien, files bornées) · statut : **ATTEND w21-01** (catalogue JSON)
> **Groupe : W21-A** (ordre 1 bis) · porte : `:core:test --tests 'castbridge.core.telemetry.*'` (par `tools/agents/gradle-lock.sh`) + `python3 -m pytest tools/analysis/tests/test_tele_dict.py`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 3.4 (politique d'envoi, compression), § 3.4 bis (files), § 3.4 ter (morceaux), § 3.4 quater (enveloppe, accusés, téléphones multiples). Branche `claude/w21-01b-sealed-envelope`. Rapport : `docs/agent-reports/sonnet-w21-01b.md`.

## Objectif (autonome)
Amendement du propriétaire (2026-10-04) : une TV reliée à Internet sans passerelle envoie ses statistiques **toutes les 12 h** ; une TV synchronisée à un téléphone les **remet au téléphone toutes les 4 h** (Bluetooth permis s'il est le seul canal) ; le téléphone, une fois en ligne, les envoie au serveur. Le téléphone ne doit **ni apprendre le jeton d'appareil de la TV, ni lire, ni fausser** les mesures. Le serveur garde `device.token_hash = SHA-256(jeton)` en hexadécimal (`backend/src/main/java/castbridge/server/devices/DeviceService.java:111`). Livrer, **pur JVM**, les briques communes aux deux apps et au serveur (vecteurs de test partagés).

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/telemetry/tech/{Packer,TeleDict,Sealer,Envelope,Receipt,SealedOutbox,CourierQueue,ChunkCodec}.kt` ; `android/core/src/main/resources/castbridge/telemetry/tele-dict-1.bin` ; `tools/analysis/build_tele_dict.py` (construit le dictionnaire à partir de `tools/analysis/catalog/tech-metrics.json` + noms des évènements d'usage existants ; sortie déterministe) ; `tools/analysis/catalog/tele-vectors.json` (vecteurs : jeton de test, `batchId`, nonce, en-tête, clair, chiffré, accusé — pour w21-02b) ; tests `android/core/src/test/kotlin/castbridge/core/telemetry/tech/{Packer,Sealer,SealedOutbox,CourierQueue,ChunkCodec}Test.kt`, `tools/analysis/tests/test_tele_dict.py`.
- **Interdit** : `R/`, `S/`, `backend/` (w21-02b reprend les vecteurs), `TelemetryUploader.kt`, `C/policy/**` (les trames `CBTO` éventuelles sont pour w21-04/07).

## Étapes
1. **Rouge** (sortie collée) : `SealerTest.phoneCannotTamper`.
2. `Packer` : évènements (même schéma que `/api/v1/events/batch`) ⇒ quantification (cases et compteurs nuls omis ; durées ≥ 1 s à 10 ms, ouverture à 50 ms ; tas en Mo ; octets par 64 ; `ts` à la seconde pour l'usage, à la minute pour les techniques ; UUID en 22 caractères base64url) ⇒ lignes JSON ⇒ **DEFLATE brut niveau 9 avec `setDictionary(TeleDict v1)`** ; `unpack` inverse (borne 2 Mo décompressés) ; lot ≤ 32 Ko compressés et ≤ 500 évènements (sinon découpé).
3. `Sealer` : `K = HKDF-SHA256(ikm = octets de SHA-256(jeton UTF-8), sel = batchId, info = "castbridge-tele-v1")` ; AES-256-GCM, nonce 96 bits aléatoire (injectable en test), étiquette 128 bits ; données associées = en-tête JSON canonique `{v, deviceId, batchId, createdAt, dict, events, path, consent, consentVersion}` ; `Envelope` = en-tête + nonce + chiffré, base64url ; `open(envelope, tokenHashHex)` pour les tests et pour le serveur (même algorithme en Java, w21-02b).
4. `Receipt` : `HMAC-SHA256(K, "receipt|" + batchId + "|" + status + "|" + pocMetrics)` ; `verify` à temps constant.
5. `SealedOutbox` (TV, fichier par lot + index atomique, modèle `C/policy/OrderQueue.kt`) : 128 Ko et 64 lots ; TTL 30 j ; éviction : d'abord les lots les plus anciens sans essentiels, puis les plus anciens ; état par lot `READY | HANDED(phone, at) | SENT_DIRECT(at)` ; réoffre d'un lot `HANDED` sans accusé après 24 h ; purge **seulement** sur `Receipt` valide.
6. `CourierQueue` (téléphone) : enveloppes **opaques** par TV ; 16 lots et 64 Ko par TV, 8 TV, 512 Ko au total ; TTL 14 j ; éviction (TV la plus chargée, lot le plus ancien ; 9e TV ⇒ remplace la TV muette depuis le plus longtemps) ; accusés du serveur gardés jusqu'à remise à la TV ; aucune méthode qui expose le clair.
7. `ChunkCodec` : morceaux de 4 Ko, reprise par offset, empreinte SHA-256 du lot vérifiée à la fin, idempotent (même morceau deux fois = sans effet).
8. Mesure du rapport de compression sur un corpus synthétique (100 journées de TV, générateur à graine) : consigné dans le rapport (objectif estimé ≈ 0,2 du JSON quantifié).
9. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `SealerTest.phoneCannotTamper` : un octet changé dans l'en-tête, le nonce ou le chiffré ⇒ ouverture refusée (mutation : GCM remplacé par CTR sans étiquette ⇒ échec) ; mauvais jeton ⇒ refus ; l'enveloppe ne contient ni le jeton ni son empreinte (recherche de sous-chaîne, hexadécimal et base64) ; vecteurs `tele-vectors.json` reproduits octet pour octet.
- `PackerTest` : aller-retour sans perte des valeurs utiles aux règles (parts au-delà des seuils identiques avant/après quantification) ; bombe (2 Mo + 1) refusée ; dictionnaire différent ⇒ refus explicite (`dict` inconnu).
- `SealedOutboxTest` : 70 lots ⇒ 64 gardés, les essentiels en dernier ; TTL ; purge seulement sur accusé valide (mutation : purge à la remise ⇒ échec) ; faux accusé ⇒ lot gardé et compté ; réoffre après 24 h.
- `CourierQueueTest` : bornes par TV et totales ; 9e TV ; TTL 14 j ; persistance après redémarrage (fichier abîmé ⇒ file vide sans plantage).
- `ChunkCodecTest` : coupure au morceau 3 sur 8 puis reprise ⇒ lot identique ; empreinte fausse ⇒ jeté.
- `test_tele_dict.py` : dictionnaire déterministe (deux constructions = mêmes octets), ≤ 4 Ko, identique au fichier de ressources.

## Interdits
Aucune dépendance ; aucun algorithme maison de chiffrement ; aucune clé en clair dans un journal ou un `toString` ; aucun réseau.

## Rapport
Rouge, vert, mutations, rapport de compression mesuré, tailles d'enveloppe (typique, maximale), vecteurs publiés pour w21-02b.
