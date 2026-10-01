package castbridge.core.owner

/** Where the console keeps the licence registry (the phone: a private file; tests: memory). The registry holds signed events and NO secret. */
interface RegistryStore {
    fun events(): List<LicenseEvent>
    fun save(events: List<LicenseEvent>)
}

class MemoryRegistry(var list: List<LicenseEvent> = emptyList()) : RegistryStore {
    override fun events() = list
    override fun save(events: List<LicenseEvent>) { list = events }
}

/** What a token generated on the owner phone offers to the screen: the same encodings as on the desk, plus the Bluetooth frame for the TV in front of the owner. */
class PhoneIssue(val delivered: Delivered) {
    val token: String get() = delivered.issued.token
    /** Frame ACTIVATION of the owner Bluetooth channel (docs/ACTIVATION-FORMAT.md § 5.2): sent as is to the TV after the CBTO handshake. */
    val bluetoothFrame: ByteArray get() = delivered.issued.bluetoothFrame
    /** Content of the `activation` file (USB drive) and the QR payload (the token itself). */
    val fileContent: String get() = delivered.issued.fileContent
}

sealed class PhoneUnlock {
    class Unlocked(val session: PhoneConsole.Session) : PhoneUnlock()
    /** Wrong code: [waitMs] is the delay before the next attempt (0 for the first free ones), [failures] the count since the last success. */
    class Wrong(val failures: Int, val waitMs: Long) : PhoneUnlock()
    /** Too early: the guard is still counting down. The code was NOT tried (a blocked attempt never reaches the key derivation). */
    class Wait(val waitMs: Long) : PhoneUnlock()
}

/**
 * Pure core of the owner phone's « Générer un jeton » screen (docs/OWNER-CONSOLE.md § 10; the Android screen only draws it). Flow: read the TV's device request (frame DEVICE_INFO,
 * or a pasted text) → choose the rights and the duration → unlock with the owner's code → token + file + QR + Bluetooth frame.
 * The signing key is the PHONE's own key (never the desk's): the seed is stored encrypted ([OwnerVault]), decrypted only inside a [Session] and wiped by [Session.lock].
 * Wrong codes are slowed by [UnlockGuard] (persist `guard.state` next to the vault). Every action is appended to the hash-chained [AuditChain] (no secret in it).
 * The Android side adds the Bluetooth link, the camera (QR of the device code) and a stronger KDF than the JDK's if wanted (any [Kdf]).
 */
class PhoneConsole(
    private val vault: VaultBlob, private val kdf: Kdf, private val guard: UnlockGuard, private val scopes: Set<KeyScope>, private val own: TrustedKey,
    private val registry: RegistryStore = MemoryRegistry(), private val otherKeys: () -> List<TrustedKey> = { emptyList() },
    val audit: AuditChain = AuditChain(), private val clock: () -> Long = System::currentTimeMillis,
) {
    fun unlock(passphrase: CharArray): PhoneUnlock {
        val wait = guard.waitMs()
        if (wait > 0) return PhoneUnlock.Wait(wait)
        val seed = OwnerVault.open(vault, passphrase, kdf)
        if (seed == null) {
            guard.failure(); audit.append(clock(), "unlock", "-", "wrong")
            return PhoneUnlock.Wrong(guard.state.failures, guard.waitMs())
        }
        guard.success(); audit.append(clock(), "unlock", "-", "ok")
        return PhoneUnlock.Unlocked(Session(Ed25519Signer(seed).also { seed.fill(0) }))
    }

    inner class Session(private var signer: Ed25519Signer?) {
        private fun s() = signer ?: throw IssueException("Console verrouillée : saisissez le code")
        private fun ring() = KeyRing((listOf(own) + otherKeys()).distinctBy { it.keyId })
        private fun flow() = LicensedIssuer(s(), scopes, ::ring, registry::events, registry::save, clock)

        /** [deviceInfo] = the text of the TV's device request (frame DEVICE_INFO). */
        fun issue(deviceInfo: String, spec: IssueSpec): PhoneIssue {
            val device = DeviceRequest.parse(deviceInfo)
            return try {
                PhoneIssue(flow().issue(device, spec)).also { audit.append(clock(), "issue", device.code, "${spec.kind.name.lowercase()}:${spec.license}") }
            } catch (e: IssueException) { audit.append(clock(), "issue", device.code, "refused"); throw e }
        }

        fun createLicense(license: String, seats: Int, maxTransfersPerYear: Int = LicenseBook.DEFAULT_TRANSFERS_PER_YEAR) {
            flow().createLicense(license, seats, maxTransfersPerYear); audit.append(clock(), "license", license, "seats=$seats")
        }

        /** Locks again (screen off, app in the background for 3 minutes, explicit button): the key can no longer sign. */
        fun lock() { signer = null }
        val unlocked: Boolean get() = signer != null
    }
}
