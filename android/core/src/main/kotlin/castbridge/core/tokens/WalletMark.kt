package castbridge.core.tokens

import castbridge.core.owner.SafeFile
import java.io.File

/**
 * Marque d'existence du porte-jetons (D-W5-J2) : « une chaîne a été ouverte sur cette installation ». [install] = installId (16 hex), [openedFp] = empreinte (16 hex) du bon d'ouverture de la
 * chaîne courante, [chain] = rang de la chaîne (1..n), [atMs] = date d'ouverture, [grantSeq] = plancher de numéro de bon : une réouverture n'accepte qu'un bon d'ouverture de numéro STRICTEMENT
 * supérieur (après une ouverture réussie c'est le numéro du bon d'ouverture ; pendant une ouverture en cours c'est ce numéro moins un, pour que le MÊME bon répare une coupure de courant).
 */
class Mark(val install: String, val openedFp: String, val chain: Int, val atMs: Long, val grantSeq: Long = 0) {
    init { require(HEX16.matches(install) && HEX16.matches(openedFp) && chain in 1..1_000_000 && atMs >= 0 && grantSeq >= 0) { "marque hors bornes" } }
    private companion object { val HEX16 = Regex("^[0-9a-f]{16}$") }
}

/**
 * Où vit la marque. Interface du cœur : la TV fournit [FileWalletMark] (`files/tokens/wallet.mark`), les tests [InMemoryWalletMark]. [read] rend null si la marque est absente, illisible, de
 * mac faux ou d'une autre installation : ces cas valent « pas de marque ». [write] rend faux si l'écriture échoue (jamais d'exception). La marque n'est PAS un élément de sécurité (la sécurité
 * vient du bon d'ouverture `fresh=1`, D-W5-J1) : elle sert à distinguer un fichier effacé (« à rétablir ») d'une première installation (« aucun jeton »).
 */
interface WalletMark {
    fun read(): Mark?
    fun write(m: Mark): Boolean
}

/** Marque en mémoire (tests). [currentInstall] non nul : une marque d'une autre installation est ignorée, comme le fait le mac du fichier. */
class InMemoryWalletMark(private val currentInstall: String? = null) : WalletMark {
    @Volatile var stored: Mark? = null
    @Volatile var failWrites = false
    override fun read(): Mark? = stored?.takeIf { currentInstall == null || it.install == currentInstall }
    override fun write(m: Mark): Boolean { if (failWrites) return false; stored = m; return true }
}

/**
 * Marque dans un fichier (≤ 4 lignes, écrit par [SafeFile]) :
 * ```
 * castbridge-wallet-mark-v1
 * install=<installId 16 hex>
 * opened=<fp 16 hex>|<chain 1..n>|<ms>|<grantSeq 0..n>
 * mac=<HMAC-SHA256 16 hex sous kWallet des trois lignes précédentes>
 * ```
 * [keys] : la clé du porte-jetons ([WalletKey.derive]) ; sans clé, [read] rend null et [write] rend faux. [installId] (facultatif) : l'installId courant ; s'il est donné, une marque d'une autre
 * installation est ignorée (la clé dérivée de la clé privée d'installation rend déjà le mac faux dans ce cas).
 */
class FileWalletMark(private val file: File, private val keys: WalletKeyProvider, private val installId: () -> String? = { null }) : WalletMark {
    override fun read(): Mark? = runCatching {
        val key = keys.key()?.takeIf { it.size == 32 } ?: return null
        val text = (file.takeIf { it.isFile } ?: return null).readText()
        val lines = text.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
        require(lines.size == 4 && lines[0] == MAGIC && lines[1].startsWith("install=") && lines[2].startsWith("opened=") && lines[3].startsWith("mac="))
        val given = lines[3].removePrefix("mac=")
        require(java.security.MessageDigest.isEqual(TokenWallet.mac(key, lines.take(3).joinToString("\n")).toByteArray(), given.toByteArray()))
        val f = lines[2].removePrefix("opened=").split('|'); require(f.size == 4)
        val m = Mark(lines[1].removePrefix("install="), f[0], f[1].toInt().also { require(it.toString() == f[1]) }, f[2].toLong().also { require(it.toString() == f[2]) }, f[3].toLong().also { require(it.toString() == f[3]) })
        m.takeIf { installId()?.let { id -> id == m.install } ?: true }
    }.getOrNull()

    override fun write(m: Mark): Boolean = runCatching {
        val key = keys.key()?.takeIf { it.size == 32 } ?: return false
        val body = listOf(MAGIC, "install=${m.install}", "opened=${m.openedFp}|${m.chain}|${m.atMs}|${m.grantSeq}")
        SafeFile.write(file, (body + "mac=${TokenWallet.mac(key, body.joinToString("\n"))}").joinToString("\n") + "\n")
    }.isSuccess

    companion object { const val MAGIC = "castbridge-wallet-mark-v1" }
}
