package castbridge.core.lots

/**
 * Pure decisions about the installation key's envelope (the Android code only maps its exceptions onto these inputs), so that a TRANSIENT Keystore failure can never destroy the alias
 * that protects `install.key` (audit w4-03: every v2 rental would be lost for good).
 */
object InstallKeyPolicy {
    enum class Step { USE, GENERATE, RECREATE, FAIL }

    /** What to do with the alias: [present] = `containsAlias` answer, null if the lookup itself failed (never generate then: the alias may well exist). */
    fun forLookup(present: Boolean?): Step = when (present) { true -> Step.USE; false -> Step.GENERATE; null -> Step.FAIL }

    /** What to do when encrypting with the existing key failed: only a permanently invalidated key is replaced; anything else is rethrown and retried later. */
    fun forEncryptFailure(permanentlyInvalidated: Boolean): Step = if (permanentlyInvalidated) Step.RECREATE else Step.FAIL

    /**
     * May the process use a wrapper labelled [chosen] when the stored key says [stored] (null = no readable key file = first creation)? A key stored under `keystore` is never read or
     * regenerated through a weaker wrapper: the caller throws and retries at the next call.
     */
    fun mayUseWrapper(stored: String?, chosen: String): Boolean = stored == null || stored == chosen || stored != "keystore"
}
