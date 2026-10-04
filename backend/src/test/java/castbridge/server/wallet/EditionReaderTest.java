package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.EnvelopeVerifier;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.EditionSpan;
import java.security.KeyPair;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * L'activation {@code cbx1} ne dit à l'API que l'IDENTITÉ et l'édition ESSAI (conception § 1.2) : la production (durée, illimitée, grâce) est l'état de la LICENCE.
 * Lecture sans module des licences, avec l'horloge du serveur ; fermée au moindre doute.
 */
class EditionReaderTest {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    static final long NOW = T0.toEpochMilli();
    static final long DAY = Acts.DAY;

    static EditionReader reader(KeyPair issuer, Set<SignerScope> scopes, EnvelopeVerifier.Revocations rev) {
        byte[] pub = WalletTestBase.rawPublicBytes(issuer);
        String kid = LicenseKeyring.kidOf(pub);
        return new EditionReader(Map.of(kid, new EnvelopeVerifier.TrustedKey(kid, pub, scopes)), () -> rev);
    }

    static EditionReader reader() {
        return reader(WalletTestBase.ISSUER, EnumSet.of(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PRODUCTION, SignerScope.SUPER_UNLIMITED), EnvelopeVerifier.Revocations.none());
    }

    @Test
    void trialOf30DaysGivesATrialSpanAndTheIdentity() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30)), T0);
        assertEquals(tv.code(), r.identity());
        assertEquals(List.of(new EditionSpan(Edition.TRIAL, T0, T0.plusSeconds(30 * 86_400))), r.spans());
        assertFalse(r.productionKey());
        assertTrue(r.rejected().isEmpty());
    }

    @Test
    void trialWithoutUsageRightIsImplicit30Days() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.trialImplicit(WalletTestBase.ISSUER, tv, NOW)), T0);
        assertEquals(List.of(new EditionSpan(Edition.TRIAL, T0, T0.plusSeconds(30 * 86_400))), r.spans());
    }

    @Test
    void trialDurationIsReadInTheKey() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 90)), T0);
        assertEquals(T0.plusSeconds(90 * 86_400), r.spans().get(0).endExclusive());
    }

    @Test
    void productionKeyOnlyProvesTheIdentity() {
        Acts.Tv tv = Acts.Tv.random();
        // même avec un droit usage : la durée d'une production ne vient JAMAIS de la clé d'activation
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.production(WalletTestBase.ISSUER, tv, NOW, List.of("usage|duree|" + NOW + "|" + (NOW + 90 * DAY)))), T0);
        assertEquals(tv.code(), r.identity());
        assertTrue(r.productionKey());
        assertTrue(r.spans().isEmpty(), "aucune édition de production n'est lue dans cbx1");
    }

    @Test
    void superRightGivesASuperSpanWithoutEnd() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.production(WalletTestBase.ISSUER, tv, NOW, List.of("super|tout|" + NOW))), T0);
        assertEquals(List.of(new EditionSpan(Edition.SUPER, T0, null)), r.spans());
        assertTrue(r.productionKey());
    }

    @Test
    void superRightNeedsAKeyThatMaySignIt() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader rd = reader(WalletTestBase.ISSUER, EnumSet.of(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PRODUCTION), EnvelopeVerifier.Revocations.none());
        EditionReader.Reading r = rd.read(tv.code(), List.of(Acts.production(WalletTestBase.ISSUER, tv, NOW, List.of("super|tout|" + NOW))), T0);
        assertTrue(r.spans().isEmpty());
        assertEquals(List.of(EditionReader.Rejection.KEY_NOT_ALLOWED), r.rejected());
    }

    @Test
    void anotherTvsActivationIsRefused() {
        Acts.Tv mine = Acts.Tv.random(), other = Acts.Tv.random();
        EditionReader.Reading r = reader().read(mine.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, other, NOW, 30)), T0);
        assertTrue(r.spans().isEmpty());
        assertFalse(r.productionKey());
        assertEquals(List.of(EditionReader.Rejection.OTHER_TV), r.rejected());
    }

    @Test
    void unknownIssuerAndTamperedTokenAreRefused() {
        Acts.Tv tv = Acts.Tv.random();
        String foreign = Acts.trialDays(WalletTestBase.pair(), tv, NOW, 30);
        assertEquals(List.of(EditionReader.Rejection.UNKNOWN_KEY), reader().read(tv.code(), List.of(foreign), T0).rejected());
        // signature d'une autre clé sous le kid de confiance : refusée
        String good = Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30);
        String[] p = good.split("\\.");
        String other = Acts.trialDays(WalletTestBase.ISSUER, Acts.Tv.random(), NOW, 30).split("\\.")[2];
        EditionReader.Reading r = reader().read(tv.code(), List.of(p[0] + "." + p[1] + "." + other), T0);
        assertTrue(r.spans().isEmpty());
        assertEquals(List.of(EditionReader.Rejection.BAD_SIGNATURE), r.rejected());
        assertEquals(List.of(EditionReader.Rejection.MALFORMED), reader().read(tv.code(), List.of("cbx1.n'importe.quoi"), T0).rejected());
    }

    @Test
    void revokedKeyAndRevokedSeatAreRefused() {
        Acts.Tv tv = Acts.Tv.random();
        String token = Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30);
        String kid = LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(WalletTestBase.ISSUER));
        EditionReader keyRevoked = reader(WalletTestBase.ISSUER, EnumSet.of(SignerScope.ISSUE_TRIAL), new EnvelopeVerifier.Revocations(Set.of(kid), Map.of()));
        assertEquals(List.of(EditionReader.Rejection.REVOKED), keyRevoked.read(tv.code(), List.of(token), T0).rejected());
        String seat = castbridge.server.licenses.WireActivation.defaultSeat("trial", tv.factors());
        EditionReader seatRevoked = reader(WalletTestBase.ISSUER, EnumSet.of(SignerScope.ISSUE_TRIAL), new EnvelopeVerifier.Revocations(Set.of(), Map.of("trial|" + seat, NOW + 1)));
        EditionReader.Reading r = seatRevoked.read(tv.code(), List.of(token), T0);
        assertTrue(r.spans().isEmpty());
        assertEquals(List.of(EditionReader.Rejection.REVOKED), r.rejected());
    }

    @Test
    void keyWithoutTheRightScopeIsRefused() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader trialOnly = reader(WalletTestBase.ISSUER, EnumSet.of(SignerScope.ISSUE_TRIAL), EnvelopeVerifier.Revocations.none());
        EditionReader.Reading r = trialOnly.read(tv.code(), List.of(Acts.production(WalletTestBase.ISSUER, tv, NOW, List.of())), T0);
        assertFalse(r.productionKey());
        assertEquals(List.of(EditionReader.Rejection.KEY_NOT_ALLOWED), r.rejected());
    }

    @Test
    void activationIssuedMoreThan24HoursInTheFutureIsAClockDoubt() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading future = reader().read(tv.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, tv, NOW + 25 * 3_600_000L, 30)), T0);
        assertTrue(future.clockDoubt());
        assertTrue(future.spans().isEmpty());
        EditionReader.Reading near = reader().read(tv.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, tv, NOW + 23 * 3_600_000L, 30)), T0);
        assertFalse(near.clockDoubt());
        assertEquals(1, near.spans().size());
    }

    @Test
    void tooManyOrTooLongTokensAreRefusedShut() {
        Acts.Tv tv = Acts.Tv.random();
        String t = Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30);
        EditionReader.Reading five = reader().read(tv.code(), List.of(t, t, t, t, t), T0);
        assertTrue(five.spans().isEmpty());
        assertEquals(List.of(EditionReader.Rejection.TOO_MANY), five.rejected());
        EditionReader.Reading big = reader().read(tv.code(), List.of("cbx1." + "a".repeat(8_200)), T0);
        assertEquals(List.of(EditionReader.Rejection.MALFORMED), big.rejected());
    }

    @Test
    void noActivationMeansNoIdentityProof() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(), T0);
        assertNull(r.identity());
        assertFalse(r.accepted());
    }

    @Test
    void revocationsFileIsOptionalSignedAndReloadedWhenItChanges() throws Exception {
        Acts.Tv tv = Acts.Tv.random();
        String token = Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30);
        String kid = LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(WalletTestBase.ISSUER));
        java.nio.file.Path file = java.nio.file.Files.createTempFile("wallet-rev", ".txt");
        String trusted = "issuer:" + WalletTestBase.rawPublic(WalletTestBase.ISSUER) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+REVOKE";
        EditionReader noFile = EditionReader.of(new WalletProperties(true, "", "", List.of(trusted), "", null, null));
        assertEquals(1, noFile.read(tv.code(), List.of(token), T0).spans().size(), "liste de révocations facultative");
        EditionReader withFile = EditionReader.of(new WalletProperties(true, "", "", List.of(trusted), file.toString(), null, null));
        java.nio.file.Files.writeString(file, "");
        assertEquals(1, withFile.read(tv.code(), List.of(token), T0).spans().size(), "fichier vide : rien de révoqué");
        // une liste signée par une clé SANS la portée REVOKE est ignorée ; une liste falsifiée aussi
        String forged = Acts.revocation(WalletTestBase.pair(), NOW, List.of("key=" + kid));
        java.nio.file.Files.writeString(file, forged + "\n" + "cbx1.n'importe.quoi" + "\n");
        assertEquals(1, withFile.read(tv.code(), List.of(token), T0).spans().size(), "liste non signée par une clé de confiance : ignorée");
        java.nio.file.Files.writeString(file, Acts.revocation(WalletTestBase.ISSUER, NOW, List.of("key=" + kid)) + "\n");
        java.nio.file.Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        EditionReader.Reading r = withFile.read(tv.code(), List.of(token), T0);
        assertTrue(r.spans().isEmpty());
        assertEquals(List.of(EditionReader.Rejection.REVOKED), r.rejected());
    }

    @Test
    void severalTrialKeysGiveOneSpanEach() {
        Acts.Tv tv = Acts.Tv.random();
        EditionReader.Reading r = reader().read(tv.code(), List.of(Acts.trialDays(WalletTestBase.ISSUER, tv, NOW - 40 * DAY, 30), Acts.trialDays(WalletTestBase.ISSUER, tv, NOW, 30)), T0);
        assertEquals(2, r.spans().size());
        assertEquals(T0.minusSeconds(40 * 86_400), r.firstTrialStart());
    }
}
