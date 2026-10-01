package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTest extends LicenseTestBase {
    private final Instant t0 = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private LedgerService.ImportReport imp(byte[] file, boolean dry) { return ledger.importLedger(OWNER, file, dry); }

    private int count(String sql, Object... a) { return jdbc.queryForObject(sql, Integer.class, a); }

    @Test
    void signatureAndOriginAreChecked() throws Exception {
        var l = license(2);
        int importsBefore = count("select count(*) from lic_ledger_import");
        var e = List.of(issuanceEntry(l.licenseId(), code(), "PURCHASE", hex(1), fp("a"), t0));
        // not signed by a trusted key
        assertThatThrownBy(() -> imp(ledgerFile("desktop", STRANGER, "desktop", e), false)).isInstanceOf(ApiException.class).hasMessageContaining("inconnue");
        // desktop key claiming to be the phone
        assertThatThrownBy(() -> imp(ledgerFile("desktop", DESKTOP, "phone", e), false)).hasMessageContaining("ne correspond pas");
        // altered payload: change one byte of the signed payload
        byte[] file = ledgerFile("desktop", DESKTOP, "desktop", e);
        JsonNode env = json.readTree(file);
        byte[] payload = Base64.getDecoder().decode(env.get("payload").asText());
        payload[payload.length / 2] ^= 1;
        ((com.fasterxml.jackson.databind.node.ObjectNode) env).put("payload", Base64.getEncoder().encodeToString(payload));
        assertThatThrownBy(() -> imp(json.writeValueAsBytes(env), false)).hasMessageContaining("Signature");
        assertThatThrownBy(() -> imp("pas du json".getBytes(), false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> imp(new byte[0], false)).isInstanceOf(ApiException.class);
        assertThat(count("select count(*) from lic_ledger_import")).isEqualTo(importsBefore);
    }

    @Test
    void serverNeverSignsATransferAndRefusesAFileThatSaysSo() throws Exception {
        var l = license(2);
        var bad = transferEntry(l.licenseId(), code(), code(), t0);
        bad.put("signedBy", "server");
        assertThatThrownBy(() -> imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(bad)), false)).hasMessageContaining("refusé").hasMessageContaining("invalides")
                .satisfies(e -> assertThat(((ApiException) e).details().toString()).contains("serveur"));
        // and the server's own export contains no transfer it signed: a file from the server tool with a transfer is refused
        assertThatThrownBy(() -> ledger.importLedger(OWNER, ledgerFile("server", key(), "server", List.of(transferEntry(l.licenseId(), code(), code(), t0))), false)).isInstanceOf(ApiException.class);
    }

    @Test
    void importReconcilesAndReportsConflictsThenIsIdempotent() throws Exception {
        var l = license(2);
        String a = code(), b = code(), c = code();
        String unknownLic = "LIC-ZZZZZ-ZZZZZ";
        var entries = List.of(
                issuanceEntry(l.licenseId(), a, "PURCHASE", hex(1), fp("a"), t0),
                issuanceEntry(l.licenseId(), b, "PURCHASE", hex(2), fp("b"), t0.plusSeconds(10)),
                issuanceEntry(l.licenseId(), c, "PURCHASE", hex(3), fp("c"), t0.plusSeconds(20)),      // third device on 2 seats
                issuanceEntry(unknownLic, code(), "PURCHASE", hex(4), fp("d"), t0.plusSeconds(30)));   // licence unknown to the server
        byte[] file = ledgerFile("desktop", DESKTOP, "desktop", entries);
        int importsBefore = count("select count(*) from lic_ledger_import"), conflictsBefore = count("select count(*) from lic_conflict");

        // dry run: the full report, nothing written, and the same file can still be imported afterwards
        var dry = imp(file, true);
        assertThat(dry.dryRun()).isTrue();
        assertThat(dry.applied()).isEqualTo(2);
        assertThat(dry.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactlyInAnyOrder("OVER_QUOTA", "UNKNOWN_LICENSE");
        assertThat(count("select count(*) from lic_issuance where license_pk = ?", l.id())).isZero();
        assertThat(count("select count(*) from lic_ledger_import")).isEqualTo(importsBefore);
        assertThat(count("select count(*) from lic_conflict")).isEqualTo(conflictsBefore);

        var r = imp(file, false);
        assertThat(r.entries()).isEqualTo(4);
        assertThat(r.applied()).isEqualTo(2);
        assertThat(r.conflicts()).hasSize(2);
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(2);
        assertThat(count("select count(*) from lic_issuance where license_pk = ? and source = 'IMPORT' and channel = 'ledger-desktop'", l.id())).isEqualTo(2);

        // same file again: nothing happens, said clearly
        assertThatThrownBy(() -> imp(file, false)).hasMessageContaining("déjà été importé");
        // another file carrying the same issuances (same nonces): duplicates, no new rows
        var again = imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(entries.get(0), entries.get(1))), false);
        assertThat(again.duplicates()).isEqualTo(2);
        assertThat(again.applied()).isZero();
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(2);

        // manual decisions: the over-quota device is accepted (the quota is raised, audited), the unknown licence is rejected
        var over = ledger.conflicts("OPEN", 0, 50).items().stream().filter(x -> x.type().equals("OVER_QUOTA") && l.licenseId().equals(x.licenseId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> ledger.decide(OWNER, over.id(), true, " ")).hasMessageContaining("motif");
        assertThat(ledger.decide(OWNER, over.id(), true, "le client a bien acheté un 3e poste").status()).isEqualTo("ACCEPTED");
        assertThat(licenses.get(l.licenseId()).seatsAllowed()).isEqualTo(3);
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(3);
        var unk = ledger.conflicts("OPEN", 0, 50).items().stream().filter(x -> x.type().equals("UNKNOWN_LICENSE") && unknownLic.equals(x.licenseId())).findFirst().orElseThrow();
        assertThat(ledger.decide(OWNER, unk.id(), false, "licence inconnue : à vérifier").status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> ledger.decide(OWNER, unk.id(), false, "encore")).hasMessageContaining("déjà été décidé");
        assertThat(count("select count(*) from lic_audit where action in ('CONFLICT_ACCEPT','CONFLICT_REJECT')")).isGreaterThanOrEqualTo(2);
        // invariant kept: never more active seats than allowed
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isLessThanOrEqualTo(licenses.get(l.licenseId()).seatsAllowed());
    }

    @Test
    void sameSeatIssuedDifferentlyByTwoToolsIsAConflict() throws Exception {
        var l = license(3);
        String dev = code();
        var d = issuanceEntry(l.licenseId(), dev, "PURCHASE", hex(10), fp("x"), t0);
        var p = issuanceEntry(l.licenseId(), dev, "PURCHASE", hex(11), fp("y"), t0.plusSeconds(5));
        assertThat(imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(d)), false).applied()).isEqualTo(1);
        var r = imp(ledgerFile("phone", PHONE, "phone", List.of(p)), false);
        assertThat(r.applied()).isZero();
        assertThat(r.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactly("TWO_TOOLS");
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        // the identical re-issue by the phone (same fingerprint, other nonce) is NOT a conflict
        var same = issuanceEntry(l.licenseId(), dev, "PURCHASE", hex(12), d.get("fingerprint").asText(), t0.plusSeconds(9));
        assertThat(imp(ledgerFile("phone", PHONE, "phone", List.of(same)), false).applied()).isEqualTo(1);
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
    }

    @Test
    void transfersAreRecordedCountedAndCapped() throws Exception {
        var l = license(2); // default cap: 2 per year
        String a = code(), b = code(), c = code(), d = code();
        imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(issuanceEntry(l.licenseId(), a, "PURCHASE", hex(20), fp("t1"), t0))), false);
        var r = imp(ledgerFile("phone", PHONE, "phone", List.of(
                transferEntry(l.licenseId(), a, b, t0.plus(1, ChronoUnit.DAYS)),
                transferEntry(l.licenseId(), b, c, t0.plus(2, ChronoUnit.DAYS)),
                transferEntry(l.licenseId(), c, d, t0.plus(3, ChronoUnit.DAYS)))), false);
        assertThat(r.applied()).isEqualTo(2);
        assertThat(r.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactly("TRANSFER_CAP");
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from lic_seat where license_pk = ? and device_code = ?", String.class, l.id(), a)).isEqualTo("TRANSFERRED");
        assertThat(jdbc.queryForObject("select state from lic_seat where license_pk = ? and device_code = ?", String.class, l.id(), c)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select signed_by from lic_transfer where license_pk = ? limit 1", String.class, l.id())).isEqualTo("phone");
        assertThat(licenses.detail(l.licenseId()).transfersLastYear()).isEqualTo(2);
        // the owner may accept it anyway (a deliberate exception) or reject it (kept as a refused transfer)
        var cap = ledger.conflicts("OPEN", 0, 50).items().stream().filter(x -> x.type().equals("TRANSFER_CAP")).findFirst().orElseThrow();
        ledger.decide(OWNER, cap.id(), false, "troisième transfert refusé");
        assertThat(count("select count(*) from lic_transfer where license_pk = ? and accepted = FALSE", l.id())).isEqualTo(1);
        assertThat(licenses.get(l.licenseId()).seatsUsed()).isEqualTo(1);
        // the cap is adjustable per licence
        licenses.update(OWNER, l.licenseId(), null, 5, null);
        assertThat(licenses.get(l.licenseId()).transferCap()).isEqualTo(5);
    }

    @Test
    void unknownLicenceTransferAndMalformedEntriesAreHandled() throws Exception {
        var r = imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(transferEntry("LIC-NOPE0-NOPE0", code(), code(), t0))), false);
        assertThat(r.conflicts()).extracting(LedgerService.ConflictRow::type).containsExactly("UNKNOWN_LICENSE");
        var bad = issuanceEntry("not a licence", "zz", "PURCHASE", "xx", "yy", t0);
        assertThatThrownBy(() -> imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(bad)), false)).hasMessageContaining("invalides");
        var weirdKind = issuanceEntry("LIC-AAAAA-BBBBB", code(), "GOD_MODE", hex(30), fp("z"), t0);
        assertThatThrownBy(() -> imp(ledgerFile("desktop", DESKTOP, "desktop", List.of(weirdKind)), false)).isInstanceOf(ApiException.class);
    }

    @Test
    void serverExportIsSignedAndReimportingItChangesNothing() throws Exception {
        var l = license(2);
        issue(l.licenseId(), code());
        byte[] file = ledger.export(OWNER);
        JsonNode env = json.readTree(file);
        byte[] payload = Base64.getDecoder().decode(env.get("payload").asText());
        byte[] pub = Base64.getDecoder().decode(activationsKeyring().publicKeyBase64());
        assertThat(LicenseKeyring.verify(pub, payload, Base64.getDecoder().decode(env.get("sig").asText()))).isTrue();
        assertThat(json.readTree(payload).get("entries").toString()).contains(l.licenseId()).doesNotContain("CBP0");
        int before = licenses.activeSeats(l.id());
        var r = imp(file, false);
        assertThat(r.applied()).isZero();
        assertThat(r.conflicts()).isEmpty();
        assertThat(licenses.activeSeats(l.id())).isEqualTo(before);
    }

    @org.springframework.beans.factory.annotation.Autowired LicenseKeyring keyring;

    private LicenseKeyring activationsKeyring() { return keyring; }

    @Test
    void roleMatrixOnImport() throws Exception {
        byte[] file = ledgerFile("desktop", DESKTOP, "desktop", List.of());
        assertThatThrownBy(() -> ledger.importLedger(SUPPORT, file, true)).hasMessageContaining("rôle");
        assertThatThrownBy(() -> ledger.importLedger(READONLY, file, true)).hasMessageContaining("rôle");
        assertThatThrownBy(() -> ledger.export(SUPPORT)).hasMessageContaining("rôle");
    }
}
