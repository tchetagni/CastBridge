package castbridge.receiver

import castbridge.core.trust.TrustPersistence
import java.io.File

/**
 * The trusted-phones registry on disk: a private file of the app (not readable by other apps, excluded from backups: see
 * the manifest). Written through a temporary file so a power cut never leaves half a file. It holds phone names and Bluetooth
 * addresses and only the SHA-256 of tokens; it never holds the PIN.
 */
class TrustFile(private val file: File) : TrustPersistence {
    override fun load(): String? = runCatching { if (file.isFile) file.readText() else null }.getOrNull()

    override fun save(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
