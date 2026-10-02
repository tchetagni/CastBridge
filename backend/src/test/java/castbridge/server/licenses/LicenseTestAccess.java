package castbridge.server.licenses;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;

/** Gives the other test packages the throwaway keys of {@link LicenseTestBase} (generated at random for each test run; never a real key). */
public final class LicenseTestAccess {
    private LicenseTestAccess() {}

    /** Trusted, every scope (REGISTRY included). */
    public static Ed25519PrivateKeyParameters desktop() { return LicenseTestBase.DESKTOP; }

    /** Trusted, every scope but REGISTRY. */
    public static Ed25519PrivateKeyParameters phone() { return LicenseTestBase.PHONE; }

    /** Not trusted. */
    public static Ed25519PrivateKeyParameters stranger() { return LicenseTestBase.STRANGER; }

    /** Trusted, every scope: kept for tests that revoke a key. */
    public static Ed25519PrivateKeyParameters spare() { return LicenseTestBase.SPARE; }
}
