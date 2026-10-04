# w21-02b — API : `POST /api/v1/events/relay` (lots scellés TV → serveur, reçus de la TV elle-même ou d'un téléphone coursier), ouverture et attribution à la TV, `tele_batch`, plafonds, accusés HMAC, jeton précédent, agrégats sur 14 jours glissants
<!-- routage architecte 2026-10-04 (W21, amendement du propriétaire sur l'envoi) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (authentification, attribution, rejeu, plafonds, consentement, bombes de décompression) · statut : **ATTEND w21-02** (V62, validation) **et w21-01b** (vecteurs `tools/analysis/catalog/tele-vectors.json`, dictionnaire)
> **Groupe : W21-A** (ordre 2) · porte : `cd backend && mvn -q test -Dtest='EventsRelay*,EnvelopeOpener*,Tech*'` puis `mvn -q test`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 3.4 quater (enveloppe, route, accusés, téléphones multiples), § 6 (`tele_batch`, `prev_token_hash`), § 7.3 bis (fraîcheur), § 9. Branche `claude/w21-02b-events-relay`. Rapport : `docs/agent-reports/sonnet-w21-02b.md`. Livraison : **server-1.1.2** (avec w21-02).

## Objectif (autonome)
Les TV n'enverront plus leurs statistiques que (a) elles-mêmes toutes les 12 h quand elles ont Internet sans passerelle, ou (b) par un téléphone coursier qui a reçu d'elles un lot scellé. Format (w21-01b) : enveloppe = en-tête JSON en clair authentifié `{v:1, deviceId, batchId, createdAt, dict, events, path, consent, consentVersion}` + nonce + chiffré AES-256-GCM, clé `HKDF-SHA256(SHA-256(jeton), batchId, "castbridge-tele-v1")`, clair = lignes JSON DEFLATE à dictionnaire. Livrer la route qui ouvre ces enveloppes **au nom de la TV** et les fait passer par la validation **existante** (catalogue, consentement, cohorte, quotas, cohérence de w21-02).

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/telemetry/tech/{EventsRelayController,EnvelopeOpener,TeleBatchService}.java` ; `backend/src/main/resources/tele-dict-1.bin` (copie, parité testée) ; tests `backend/src/test/java/castbridge/server/telemetry/tech/{EventsRelay,EnvelopeOpener,TeleBatch}*Test.java`.
- **Zone additive** : `DeviceService.java` (au réenregistrement : `prev_token_hash` = ancienne empreinte, `prev_token_until` = maintenant + 14 j ; colonnes créées par V62 de w21-02 — si absentes à la fusion de w21-02, **coordonner** : une seule migration V62) ; `TelemetryService.java` (méthode additive `ingestAs(Device tv, List<JsonNode> events, String path)` qui réutilise `validate`/`store` ; règle : pour un évènement relayé dont `ts` a plus de 30 jours, **refus** `too_old` pour les techniques et **pas** de redatation au jour du serveur) ; `TechRollupJob.java` (reconstruction des heures et jours **touchés** par les arrivées, fenêtre 14 jours).
- **Interdit** : V1-V61, toute nouvelle migration (tout est dans V62), `SecurityConfig.java`, `android/`, `server-play/`.

## Étapes
1. **Rouge** (sortie collée) : `EventsRelayTest.tamperedEnvelopeIsRejectedWithMac`.
2. `EnvelopeOpener` (Java, JDK seulement : `javax.crypto` AES/GCM/NoPadding, HMAC-SHA256 ; HKDF écrit en 15 lignes selon la RFC 5869) : reproduit les vecteurs `tele-vectors.json` octet pour octet ; essaie `token_hash` puis `prev_token_hash` si `prev_token_until` n'est pas passé ; décompression bornée à 2 Mo (`Inflater` + `setDictionary`).
3. `EventsRelayController` `POST /api/v1/events/relay` : `Authorization: Bearer <jeton d'appareil>` (téléphone `app=phone` **ou** la TV elle-même) ; corps `{"envelopes":["…"]}` 1 à 16, ≤ 256 Ko (gzip accepté comme `/events/batch`) ; par enveloppe : en-tête lu ⇒ `deviceId` existant et de type TV ⇒ ouverture (sinon `mac`) ⇒ `batchId` déjà dans `tele_batch` ⇒ `duplicate` + même accusé, `copies + 1` ⇒ plafonds (par téléphone : 200 enveloppes et 1 Mo / jour ; par TV : 16 / jour ; enveloppe ≤ 32 Ko) ⇒ consentement retenu = **le plus restrictif** de l'en-tête et de la fiche ⇒ `ingestAs` ⇒ ligne `tele_batch` (`via_device_id` = appelant si ≠ TV, `path`, `oldest_ts`, `newest_ts`) ⇒ accusé `HMAC-SHA256(K, "receipt|" + batchId + "|" + status + "|" + pocMetrics)`. Réponse `{"results":[{"batchId","status","reason?","receipt"}]}` ; **aucun écho** du contenu ; une enveloppe `path:"self"` (statistiques **propres** de l'appelant, téléphone à 12 h : **non scellée**, compressée par le même dictionnaire) est décompressée (borne 2 Mo) puis passe par `TelemetryService.ingest` **au nom de l'appelant** (son consentement) ; motifs fermés : `mac`, `token_stale`, `replay`, `too_old`, `quota_phone`, `quota_tv`, `size`, `cohort`, `consent`.
4. Métriques M-56, M-57, M-58 : comptes et histogramme de fraîcheur `received_at − newest_ts` par chemin, servis par `GET /api/v1/admin/tech/delivery` (jeton admin ; agrégé, aucun `device_id`).
5. **Vert** + suite complète.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `EnvelopeOpenerTest` : vecteurs reproduits ; un octet changé ⇒ `mac` ; ancien jeton dans les 14 jours ⇒ ouvert ; au-delà ⇒ `token_stale` (mutation : ignorer `prev_token_until` ⇒ échec) ; 2 Mo + 1 décompressés ⇒ refus.
- `EventsRelayTest` : lot relayé par un téléphone ⇒ évènements attribués **à la TV** (`telemetry_event.device_id` = TV), jamais au téléphone (mutation ⇒ échec) ; même lot par un second téléphone ⇒ `duplicate`, aucun évènement doublé, `copies = 2` ; TV hors cohorte ⇒ techniques refusés `cohort`, essentiels acceptés ; en-tête `consent:"essential"` avec fiche `usage` ⇒ évènements d'usage refusés ; 17e enveloppe d'une TV le même jour ⇒ `quota_tv` ; évènement de 31 jours ⇒ `too_old` et **pas** de ligne au jour du serveur ; chemin direct (la TV elle-même) accepté ; accusé vérifiable avec `K`.
- `EventsRelayTest.selfEnvelope` : enveloppe `self` d'un téléphone ⇒ évènements attribués au **téléphone** ; `self` portant un `deviceId` d'une autre machine ⇒ refus.
- `TeleBatchTest` : purge à 35 jours ; effacement de la TV ⇒ ses `tele_batch` disparaissent ; lot arrivant après l'effacement ⇒ refusé.
- `TechRollupJobTest` (ajout) : un lot arrivé avec 5 jours de retard ⇒ l'heure et le jour concernés reconstruits.
- Journal : aucune ligne ne contient d'en-tête, de chiffré ou de clair (journal capturé).
- Suite `mvn test` complète verte.

## Interdits
Aucune dépendance cryptographique externe ; aucune clé ni empreinte dans les journaux ; aucune connexion à la production.

## Rapport
Rouge, vert, mutations, temps d'ouverture d'une enveloppe (mesuré en test), tableau des motifs, ce que le téléphone peut encore faire (retenir, rejouer) et pourquoi c'est sans effet.
