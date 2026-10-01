package castbridge.receiver

import castbridge.core.trust.FileTrustPersistence
import castbridge.core.trust.TrustPersistence
import java.io.File

/**
 * The trusted-phones registry on disk: a private file of the app (not readable by other apps, excluded from backups: see
 * the manifest). Written through a temporary file and fsync, with the previous good copy kept as `.bak` ([FileTrustPersistence]), so a
 * power cut never leaves half a file and a damaged one is recovered. It holds phone names and Bluetooth addresses and only the SHA-256
 * of tokens; it never holds the PIN.
 */
class TrustFile(file: File) : TrustPersistence by FileTrustPersistence(file)
