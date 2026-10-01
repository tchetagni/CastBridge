package castbridge.desktop

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyScope
import castbridge.core.owner.Kdf
import castbridge.core.owner.OwnerVault
import castbridge.core.owner.TrustedKey
import castbridge.core.owner.VaultBlob
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * The desk signing key at rest: the 32-byte seed encrypted with AES-GCM under a key derived from the owner's code (scrypt, memory-hard). Only the PUBLIC key and
 * the `kid` are readable without the code. The code and the seed are never written anywhere else (not in the journal, not in the logs).
 * Where to keep the backup of this file (and why losing the code loses the key) is in docs/ACTIVATION-TOOLS.md.
 */
class KeyFile(val file: File) {
    class Info(val kid: String, val publicKey: String, val scopes: Set<KeyScope>, val label: String)

    private fun read() = JsonLite.obj(file.readText())
    private fun b64(s: String) = Base64.getDecoder().decode(s)
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    fun exists() = file.isFile

    fun info(): Info = read().let { m ->
        Info(m.str("kid")!!, m.str("publicKey")!!, (m["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet(), m.str("label") ?: "bureau")
    }

    fun trusted(): TrustedKey = info().let { TrustedKey(it.kid, it.publicKey, it.scopes) }

    /** Creates the key: refuses to overwrite an existing one (losing it would orphan every token already issued). */
    fun create(passphrase: CharArray, kdf: Kdf = ScryptKdf(), scopes: Set<KeyScope> = KeyScope.ALL, label: String = "bureau", rnd: SecureRandom = SecureRandom()): Info {
        require(!exists()) { "Une clé existe déjà : ${file.path} (on ne l'écrase jamais ; déplacez-la pour en créer une autre)" }
        require(passphrase.size >= 10) { "Le code de déverrouillage doit faire au moins 10 caractères" }
        val seed = ByteArray(32).also(rnd::nextBytes)
        val signer = Ed25519Signer(seed)
        val blob = OwnerVault.seal(seed, passphrase, kdf, rnd)
        seed.fill(0)
        file.parentFile?.mkdirs()
        val json = JsonLite.write(linkedMapOf(
            "format" to "castbridge-desk-key-v1", "label" to label, "kid" to signer.keyId, "publicKey" to signer.publicKeyBase64, "scopes" to scopes.map { it.name }.sorted(),
            "kdf" to blob.kdf, "salt" to b64(blob.salt), "nonce" to b64(blob.nonce), "ciphertext" to b64(blob.ciphertext), "check" to b64(blob.check)))
        val tmp = File(file.path + ".tmp"); tmp.writeText(json + "\n"); tmp.renameTo(file)
        runCatching { file.setReadable(false, false); file.setReadable(true, true); file.setWritable(false, false); file.setWritable(true, true) }
        return info()
    }

    /** The signer, or null when the code is wrong. The seed lives in memory only while the signer does. */
    fun unlock(passphrase: CharArray, kdf: Kdf = ScryptKdf()): Signer? {
        val m = read()
        val blob = VaultBlob(m.str("kdf")!!, b64(m.str("salt")!!), b64(m.str("nonce")!!), b64(m.str("ciphertext")!!), b64(m.str("check")!!))
        val seed = OwnerVault.open(blob, passphrase, kdf) ?: return null
        val s = Ed25519Signer(seed); seed.fill(0)
        return Signer(s, info().scopes)
    }

    class Signer(val signer: Ed25519Signer, val scopes: Set<KeyScope>)
}
