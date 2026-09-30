package castbridge.server.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.config.CastbridgeProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningAndApkTest {
    static final String SEED_HEX = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"; // RFC 8032 test 1
    static final String PUB_B64 = "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=";

    /** The manifest also used by android/core UpdateManifestTest: both sides must build the same signed text. */
    static final UpdateManifest FIXTURE = new UpdateManifest("tv", "stable", "armeabi-v7a", 42, "0.6", "https://cb.example/castbridge/dl/tv/castbridge-tv-0.6-42-armeabi-v7a-0123abcd.apk",
            "0123abcd" + "0".repeat(56), 1234567, 26, "Corrections « Wi-Fi Direct » et lecteur.\nDeuxième ligne.", true, 40,
            "2026-09-30T10:00:00+01:00", null, null);

    @Test
    void rfc8032Vector1() throws Exception {
        ManifestSigner s = new ManifestSigner(ManifestSigner.parse(Base64.getEncoder().encode(HexFormat.of().parseHex(SEED_HEX))));
        assertEquals(PUB_B64, s.publicKeyBase64());
        // RFC 8032 section 7.1, test 1: empty message
        assertEquals("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                HexFormat.of().formatHex(Base64.getDecoder().decode(s.signBase64(""))));
    }

    @Test
    void acceptsPemDerAndSeedAndVerifiesWithTheJdk() throws Exception {
        byte[] seed = HexFormat.of().parseHex(SEED_HEX);
        // PKCS#8 DER of an Ed25519 key = fixed 16-byte prefix + seed (what "openssl genpkey -algorithm ed25519" writes)
        byte[] der = new byte[48];
        System.arraycopy(HexFormat.of().parseHex("302e020100300506032b657004220420"), 0, der, 0, 16);
        System.arraycopy(seed, 0, der, 16, 32);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END PRIVATE KEY-----\n";
        for (byte[] input : new byte[][] {pem.getBytes(StandardCharsets.US_ASCII), der, Base64.getEncoder().encode(der), Base64.getEncoder().encode(seed)}) {
            assertEquals(PUB_B64, new ManifestSigner(ManifestSigner.parse(input)).publicKeyBase64());
        }
        ManifestSigner s = new ManifestSigner(ManifestSigner.parse(pem.getBytes(StandardCharsets.US_ASCII)));
        String payload = FIXTURE.canonicalPayload();
        byte[] sig = Base64.getDecoder().decode(s.signBase64(payload));
        PublicKey pub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(concat(
                HexFormat.of().parseHex("302a300506032b6570032100"), Base64.getDecoder().decode(PUB_B64))));
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(pub);
        v.update(payload.getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(sig));
        v.initVerify(pub);
        v.update(payload.replace("versionCode=42", "versionCode=43").getBytes(StandardCharsets.UTF_8));
        assertFalse(v.verify(sig));
    }

    @Test
    void keyFromFileAndInvalidKey(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("k.pem");
        Files.writeString(f, Base64.getEncoder().encodeToString(HexFormat.of().parseHex(SEED_HEX)));
        assertEquals(PUB_B64, new ManifestSigner(ManifestSigner.load(new CastbridgeProperties.Signing(null, f))).publicKeyBase64());
        assertFalse(new ManifestSigner((org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters) null).enabled());
        assertThrows(IllegalStateException.class, () -> ManifestSigner.load(new CastbridgeProperties.Signing("pas-une-clé!!", null)));
    }

    /** The exact text signed for FIXTURE (copied into android/core UpdateManifestTest). */
    @Test
    void canonicalPayloadIsStable() throws Exception {
        assertEquals("""
                castbridge-update-manifest-v1
                app=tv
                channel=stable
                abi=armeabi-v7a
                versionCode=42
                versionName=0.6
                url=https://cb.example/castbridge/dl/tv/castbridge-tv-0.6-42-armeabi-v7a-0123abcd.apk
                sha256=0123abcd00000000000000000000000000000000000000000000000000000000
                size=1234567
                minSdk=26
                mandatory=true
                minSupportedVersionCode=40
                publishedAt=2026-09-30T10:00:00+01:00
                notesSha256=""" + HexFormat.of().formatHex(ManifestSigner.sha256(FIXTURE.notes().getBytes(StandardCharsets.UTF_8))),
                FIXTURE.canonicalPayload());
        ManifestSigner s = new ManifestSigner(ManifestSigner.parse(Base64.getEncoder().encode(HexFormat.of().parseHex(SEED_HEX))));
        System.out.println("FIXTURE signature: " + s.signBase64(FIXTURE.canonicalPayload()));
    }

    @Test
    void readsTheBinaryManifestOfAnApk() throws Exception {
        Path apk = Path.of(getClass().getResource("/apk/receiver-42.apk").toURI());
        ApkInspector.ApkInfo info = ApkInspector.inspect(apk).orElseThrow();
        assertEquals("castbridge.receiver", info.packageName());
        assertEquals(42, info.versionCode());
        assertEquals("0.6-test", info.versionName());
        assertEquals(26, info.minSdk());
    }

    @Test
    void notAnApk(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("x.apk");
        Files.writeString(f, "hello");
        assertTrue(ApkInspector.inspect(f).isEmpty());
        assertEquals(null, ApkInspector.parseManifest(new byte[] {1, 2, 3}));
    }

    @Test
    void rolloutBucketIsStableAndSpread() {
        assertEquals(ReleaseService.rolloutBucket("device-abcdef", "tv", 42), ReleaseService.rolloutBucket("device-abcdef", "tv", 42));
        int under50 = 0;
        for (int i = 0; i < 2000; i++) if (ReleaseService.rolloutBucket("dev-" + i + "-xyzw", "tv", 42) < 50) under50++;
        assertTrue(under50 > 850 && under50 < 1150, "about half the devices are in a 50 % rollout: " + under50);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
