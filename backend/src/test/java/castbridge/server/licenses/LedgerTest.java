package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The licence registry: signed events (docs/ACTIVATION-FORMAT.md § 8-9), union import, conflicts, transfers recorded but never signed by the server. */
class LedgerTest extends LicenseTestBase {
    private final long t0 = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
    private static final long DAY = 86_400_000L;

    private int count(String sql, Object... a) { return jdbc.queryForObject(sql, Integer.class, a); }

    private static String lic() { return "lic-" + Long.toString(RND.nextLong() & 0xffffffffffL, 36); }

    private long licenseCount(String wire) { return count("select count(*) from lic_license where license_id = ?", wire); }

    @Test
    void everyEventIsVerifiedAndRefusedWhenKeyOrSignatureOrScopeIsWrong() throws Exception {
        String lic = lic();
        var good = licenseEvent(DESKTOP, t0, lic, 2, 2);
        var unknownKey = licenseEvent(STRANGER, t0 + 1, lic() , 1, 2);
        // a licence event signed by the key of a tool that only has the TRANSFER scope (here: forged with the phone key, which lacks REGISTRY but has ISSUE_*)
        var forged = new RegistryEvent(good.kid(), good.text().replace("seats=2", "seats=900"), good.signature());
        var noScopeTransfer = transferEvent(serverKey(), t0 + 5, lic, "0123456789abcdef", dev(), nonce());   // the SERVER key has no TRANSFER scope
        var r = importAuto(List.of(good, unknownKey, forged, noScopeTransfer));
        assertThat(r.applied()).isEqualTo(1);
        assertThat(r.rejections()).extracting(LedgerService.Rejection::reason).containsExactlyInAnyOrder("UNKNOWN_KEY", "BAD_SIGNATURE", "KEY_NOT_ALLOWED");
        assertThat(licenseCount(lic)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select seats_allowed from lic_license where license_id = ?", Integer.class, lic)).isEqualTo(2);
        // refused events are not kept in the registry
        assertThat(registry.has(unknownKey.id())).isFalse();
        assertThat(registry.has(forged.id())).isFalse();
        assertThat(registry.has(noScopeTransfer.id())).isFalse();
        assertThat(registry.has(good.id())).isTrue();
        // a revoked key signs nothing any more
        importAuto(List.of(revokeKeyEvent(DESKTOP, t0 + 10, kid(SPARE))));
        assertThat(importAuto(List.of(licenseEvent(SPARE, t0 + 11, lic(), 1, 2))).rejections()).extracting(LedgerService.Rejection::reason).containsExactly("REVOKED_KEY");
    }

    @Test
    void malformedFilesAndEntriesAreHandled() throws Exception {
        assertThatThrownBy(() -> ledger.importLedger(OWNER, "pas du json".getBytes(), false, false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ledger.importLedger(OWNER, "{\"format\":\"autre\"}".getBytes(), false, false)).hasMessageContaining("Format de registre inconnu");
        assertThatThrownBy(() -> ledger.importLedger(OWNER, new byte[0], false, false)).isInstanceOf(ApiException.class);
        // an entry whose id does not match its text is skipped, never trusted
        var e = licenseEvent(DESKTOP, t0, lic(), 1, 2);
        ObjectNode file = (ObjectNode) json.readTree(registryFile(List.of(e)));
        ((ObjectNode) file.get("events").get(0)).put("id", "0000000000000000");
        var r = ledger.importLedger(OWNER, json.writeValueAsBytes(file), false, true);
        assertThat(r.ignored()).isEqualTo(1);
        assertThat(r.applied()).isZero();
        // an event with a bad field (seats = 0) passes the signature but is MALFORMED
        var bad = licenseEvent(DESKTOP, t0 + 1, lic(), 0, 2);
        assertThat(importAuto(List.of(bad)).rejections()).extracting(LedgerService.Rejection::reason).containsExactly("MALFORMED");
        var unknownType = event(DESKTOP, "gift", t0 + 2, List.of("license=zzz"), java.util.Map.of());
        assertThat(importAuto(List.of(unknownType)).rejections()).extracting(LedgerService.Rejection::reason).containsExactly("MALFORMED");
        // too many events
        assertThatThrownBy(() -> ledger.importLedger(OWNER, registryFile(java.util.Collections.nCopies(6000, e)), false, false)).hasMessageContaining("Trop d'événements");
    }

    @Test
    void importIsAnIdempotentUnionAndDryRunChangesNothing() throws Exception {
        String lic = lic();
        Dev a = dev(), b = dev();
        var events = List.of(licenseEvent(DESKTOP, t0, lic, 2, 2), issueEvent(DESKTOP, t0 + 1000, lic, seatOf(lic, a), "tv", "production", a, nonce()),
                issueEvent(PHONE, t0 + 2000, lic, seatOf(lic, b), "tv", "production", b, nonce()), issueEvent(DESKTOP, t0 + 3000, "trial", seatOf("trial", dev()), "tv", "trial", dev(), nonce()));
        int eventsBefore = count("select count(*) from lic_event"), importsBefore = count("select count(*) from lic_ledger_import");
        var dry = ledger.importLedger(OWNER, registryFile(events), true, false);
        assertThat(dry.dryRun()).isTrue();
        assertThat(dry.applied()).as(dry.toString()).isEqualTo(4);
        assertThat(count("select count(*) from lic_event")).isEqualTo(eventsBefore);
        assertThat(count("select count(*) from lic_ledger_import")).isEqualTo(importsBefore);
        assertThat(licenseCount(lic)).isZero();

        var r = importReview(events);
        assertThat(r.applied()).isEqualTo(4);
        assertThat(r.conflicts()).isEmpty();
        var l = licenses.get(lic);
        assertThat(l.seatsUsed()).isEqualTo(2);
        assertThat(l.createdBy()).startsWith("import:");
        assertThat(count("select count(*) from lic_issuance where license_pk = ? and source = 'IMPORT'", l.id())).isEqualTo(2);
        // the same file again, and a file carrying only some of the same events: nothing new, no error
        var again = importReview(events);
        assertThat(again.applied()).isZero();
        assertThat(again.duplicates()).isEqualTo(4);
        assertThat(importReview(events.subList(0, 2)).duplicates()).isEqualTo(2);
        assertThat(licenses.get(lic).seatsUsed()).isEqualTo(2);
        // the trial issue is logged in the registry and counted as a trial, never as a seat
        assertThat(((Number) abuse.dashboard().get("trialsIssued")).longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void overQuotaDuplicateHardwareUnknownLicenceAndDecisions() throws Exception {
        String lic = lic(), ghost = lic();
        Dev a = dev(), b = dev(), c = dev();
        Dev aTwice = a.withModuleChanged(DeviceIdentity.Factor.WIFI);
        var events = List.of(licenseEvent(DESKTOP, t0, lic, 2, 2),
                issueEvent(DESKTOP, t0 + 1000, lic, seatOf(lic, a), "tv", "production", a, nonce()),
                issueEvent(PHONE, t0 + 2000, lic, "aaaaaaaaaaaaaaaa", "tv", "production", aTwice, nonce()),          // same hardware under another seat id (two tools)
                issueEvent(DESKTOP, t0 + 3000, lic, seatOf(lic, b), "tv", "production", b, nonce()),
                issueEvent(DESKTOP, t0 + 4000, lic, seatOf(lic, c), "tv", "production", c, nonce()),                // third device on 2 seats
                issueEvent(DESKTOP, t0 + 5000, ghost, seatOf(ghost, dev()), "tv", "production", dev(), nonce()));  // licence unknown to the server
        var r = importReview(events);
        assertThat(r.applied()).isEqualTo(3); // licence + first seat + b
        assertThat(r.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactlyInAnyOrder("TWO_TOOLS", "OVER_QUOTA", "UNKNOWN_LICENSE");
        assertThat(licenses.get(lic).seatsUsed()).isEqualTo(2);
        // conflicted events are in the registry (they are facts) but have no effect on the seats yet
        assertThat(registry.has(events.get(2).id())).isTrue();
        assertThat(jdbc.queryForObject("select applied from lic_event where id = ?", Boolean.class, events.get(2).id())).isFalse();

        var open = ledger.conflicts("OPEN", 0, 50).items();
        var dup = open.stream().filter(x -> x.type().equals("TWO_TOOLS") && lic.equals(x.licenseId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> ledger.decide(OWNER, dup.id(), true, " ")).hasMessageContaining("motif");
        // accepting merges the duplicate into the older seat (alias): still 2 seats used, and the alias is remembered
        assertThat(ledger.decide(OWNER, dup.id(), true, "même télévision, module Wi-Fi remplacé").status()).isEqualTo("ACCEPTED");
        assertThat(licenses.get(lic).seatsUsed()).isEqualTo(2);
        assertThat(count("select count(*) from lic_seat_alias where alias_seat_id = ?", "aaaaaaaaaaaaaaaa")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select applied from lic_event where id = ?", Boolean.class, events.get(2).id())).isTrue();
        // the over-quota device: accepted = the quota is raised (traced), never silently
        var over = open.stream().filter(x -> x.type().equals("OVER_QUOTA") && lic.equals(x.licenseId())).findFirst().orElseThrow();
        assertThat(ledger.decide(OWNER, over.id(), true, "le client a bien acheté un 3e poste").status()).isEqualTo("ACCEPTED");
        assertThat(licenses.get(lic).seatsAllowed()).isEqualTo(3);
        assertThat(licenses.get(lic).seatsUsed()).isEqualTo(3);
        // the unknown licence: refused to apply while it is unknown, rejected otherwise; a decided conflict cannot be decided twice
        var unk = open.stream().filter(x -> x.type().equals("UNKNOWN_LICENSE") && ghost.equals(x.licenseId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> ledger.decide(OWNER, unk.id(), true, "tentative")).hasMessageContaining("créez d'abord la licence");
        assertThat(ledger.decide(OWNER, unk.id(), false, "licence inconnue : à vérifier").status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> ledger.decide(OWNER, unk.id(), false, "encore")).hasMessageContaining("déjà été décidé");
        assertThat(count("select count(*) from lic_audit where action in ('CONFLICT_ACCEPT','CONFLICT_REJECT')")).isGreaterThanOrEqualTo(3);
        // invariant kept: never more active seats than allowed
        assertThat(licenses.get(lic).seatsUsed()).isLessThanOrEqualTo(licenses.get(lic).seatsAllowed());
    }

    @Test
    void policyAutoAppliesTheFormatAsIs() throws Exception {
        String lic = lic();
        Dev a = dev(), b = dev();
        Dev aTwice = a.withModuleChanged(DeviceIdentity.Factor.BLUETOOTH);
        var r = importAuto(List.of(licenseEvent(DESKTOP, t0, lic, 1, 2), issueEvent(DESKTOP, t0 + 1000, lic, seatOf(lic, a), "tv", "production", a, nonce()),
                issueEvent(PHONE, t0 + 2000, lic, "bbbbbbbbbbbbbbbb", "tv", "production", aTwice, nonce()),   // duplicate hardware: merged into the older seat, counted once
                issueEvent(DESKTOP, t0 + 3000, lic, seatOf(lic, b), "tv", "production", b, nonce())));          // over quota: signalled, quota raised
        assertThat(r.conflicts()).isEmpty();
        assertThat(r.warnings()).hasSize(1).first().asString().contains("plus de postes que prévu");
        assertThat(licenses.get(lic).seatsUsed()).isEqualTo(2);
        assertThat(count("select count(*) from lic_seat_alias where alias_seat_id = 'bbbbbbbbbbbbbbbb'")).isEqualTo(1);
        // over-cap transfer and unknown licence are rejected, not queued
        var rej = importAuto(List.of(issueEvent(DESKTOP, t0 + 4000, lic(), "cccccccccccccccc", "tv", "production", dev(), nonce())));
        assertThat(rej.rejections()).extracting(LedgerService.Rejection::reason).containsExactly("UNKNOWN_LICENSE");
        assertThat(rej.conflicts()).isEmpty();
    }

    @Test
    void transfersAreRecordedCountedCappedAndNeverSignedByTheServer() throws Exception {
        String lic = lic();
        Dev a = dev(), b = dev(), c = dev(), d = dev();
        String seat = seatOf(lic, a);
        importReview(List.of(licenseEvent(DESKTOP, t0, lic, 2, 2), issueEvent(DESKTOP, t0 + 1000, lic, seat, "tv", "production", a, nonce())));
        var r = importReview(List.of(transferEvent(PHONE, t0 + DAY, lic, seat, b, nonce()), transferEvent(DESKTOP, t0 + 2 * DAY, lic, seat, c, nonce()), transferEvent(PHONE, t0 + 3 * DAY, lic, seat, d, nonce())));
        assertThat(r.applied()).isEqualTo(2);
        assertThat(r.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactly("TRANSFER_CAP");
        // the seat follows the new hardware (same seat id), nothing else is consumed; the old activation is revoked at the transfer date
        var l = licenses.get(lic);
        assertThat(l.seatsUsed()).isEqualTo(1);
        var seatRow = licenses.detail(lic).seats().get(0);
        assertThat(seatRow.seatId()).isEqualTo(seat);
        assertThat(seatRow.deviceCode()).isEqualTo(c.code());
        assertThat(jdbc.queryForObject("select count(*) from lic_revocation where license_id = ? and seat_id = ?", Integer.class, lic, seat)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select signed_by from lic_transfer where license_pk = ? order by at limit 1", String.class, l.id())).isEqualTo(kid(PHONE));
        assertThat(licenses.detail(lic).transfersLastYear()).isEqualTo(2);
        // the activation re-issued for the NEW hardware is issued after the transfer revocation (the owner tools do the same)
        ensureProducts();
        licenses.addProduct(OWNER, lic, "p-test", null);
        var reissued = activations.issue(OWNER, new ActivationService.IssueRequest(lic, "tv", c.text(), null, null, null), "server-api");
        assertThat(reissued.seatId()).isEqualTo(seat);
        // rejected = kept out and recorded as a refused transfer; the cap is adjustable per licence
        var cap = ledger.conflicts("OPEN", 0, 50).items().stream().filter(x -> x.type().equals("TRANSFER_CAP") && lic.equals(x.licenseId())).findFirst().orElseThrow();
        ledger.decide(OWNER, cap.id(), false, "troisième transfert refusé");
        assertThat(count("select count(*) from lic_transfer where license_pk = ? and accepted = FALSE", l.id())).isEqualTo(1);
        licenses.update(OWNER, lic, null, 5, null);
        assertThat(licenses.get(lic).transferCap()).isEqualTo(5);
        // a year later the cap counts again from zero
        var next = importReview(List.of(transferEvent(DESKTOP, t0 + 400 * DAY, lic, seat, dev(), nonce())));
        assertThat(next.applied()).isEqualTo(1);
        // a transfer for an unknown seat or licence is a conflict (UNKNOWN_LICENSE), never silently dropped
        var unknown = importReview(List.of(transferEvent(DESKTOP, t0 + 5 * DAY, lic, "dddddddddddddddd", dev(), nonce()), transferEvent(DESKTOP, t0 + 6 * DAY, lic(), seat, dev(), nonce())));
        assertThat(unknown.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactly("UNKNOWN_LICENSE", "UNKNOWN_LICENSE");
    }

    @Test
    void theServerSignsNoTransferAndRefusesOneThatClaimsToBeIt() throws Exception {
        // an event of type transfer signed with the SERVER key lacks the TRANSFER scope: refused by the scope check
        String lic = lic();
        var forgedByServer = transferEvent(serverKey(), t0, lic, "0123456789abcdef", dev(), nonce());
        assertThat(importAuto(List.of(forgedByServer)).rejections()).extracting(LedgerService.Rejection::reason).containsExactly("KEY_NOT_ALLOWED");
        // and the server builds none: its registry events are license, issue and revoke only
        var l = license(1);
        issue(l.licenseId(), dev());
        licenses.releaseSeat(OWNER, l.licenseId(), licenses.detail(l.licenseId()).seats().get(0).seatId(), "test");
        assertThat(jdbc.queryForList("select distinct type from lic_event where source = 'SERVER'", String.class)).doesNotContain("transfer").contains("license", "issue", "revoke");
    }

    @Test
    void revokeEventsFeedTheSignedRevocationList() throws Exception {
        String lic = lic();
        Dev a = dev();
        String seat = seatOf(lic, a);
        importReview(List.of(licenseEvent(DESKTOP, t0, lic, 1, 2), issueEvent(DESKTOP, t0 + 1000, lic, seat, "tv", "production", a, nonce())));
        importReview(List.of(revokeSeatEvent(PHONE, t0 + 5000, lic, seat), revokeKeyEvent(DESKTOP, t0 + 6000, kid(STRANGER))));
        assertThat(jdbc.queryForObject("select count(*) from lic_revocation where license_id = ? and seat_id = ?", Integer.class, lic, seat)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lic_revocation where kid = ?", Integer.class, kid(STRANGER))).isEqualTo(1);
        // a revoked key is refused at the next import
        assertThat(importAuto(List.of(licenseEvent(STRANGER, t0 + 7000, lic(), 1, 2))).rejections()).extracting(LedgerService.Rejection::reason).containsExactly("UNKNOWN_KEY");
    }

    @Test
    void serverExportIsTheSignedUnionAndReimportingItChangesNothing() throws Exception {
        var l = license(2);
        Dev d = dev();
        var a = issue(l.licenseId(), d);
        String imported = lic();
        importReview(List.of(licenseEvent(DESKTOP, t0, imported, 1, 2)));
        byte[] file = ledger.export(OWNER);
        JsonNode root = json.readTree(file);
        assertThat(root.get("format").asText()).isEqualTo(LedgerService.FORMAT);
        assertThat(root.get("events").toString()).contains(l.licenseId()).contains(imported).doesNotContain("cba1.");
        // every exported event verifies against its author's key and its id matches its text
        for (JsonNode n : root.get("events")) {
            RegistryEvent e = RegistryEvent.fromJson(n);
            assertThat(e).isNotNull();
            var key = new TrustedKeys(new LicenseProperties(true, false, null, java.nio.file.Path.of("/nonexistent"), null, null, null, null, null,
                    List.of(trusted("desktop", DESKTOP, ALL_SCOPES), trusted("phone", PHONE, PHONE_SCOPES)), null, null, null, null), keyring).find(e.kid());
            assertThat(key).isNotNull();
            assertThat(LicenseKeyring.verify(key.publicKey(), e.text().getBytes(java.nio.charset.StandardCharsets.UTF_8), e.signatureBytes())).isTrue();
        }
        int before = licenses.activeSeats(l.id());
        var r = ledger.importLedger(OWNER, file, false, false);
        assertThat(r.applied()).isZero();
        assertThat(r.conflicts()).isEmpty();
        assertThat(r.rejections()).isEmpty();
        assertThat(licenses.activeSeats(l.id())).isEqualTo(before);
        assertThat(a.seatId()).hasSize(16);
    }

    @Test
    void roleMatrixOnImport() throws Exception {
        byte[] file = registryFile(List.of());
        assertThatThrownBy(() -> ledger.importLedger(SUPPORT, file, true, false)).hasMessageContaining("rôle");
        assertThatThrownBy(() -> ledger.importLedger(READONLY, file, true, false)).hasMessageContaining("rôle");
        assertThatThrownBy(() -> ledger.export(SUPPORT)).hasMessageContaining("rôle");
    }
}
