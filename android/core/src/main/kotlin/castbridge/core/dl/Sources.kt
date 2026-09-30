package castbridge.core.dl

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

/** What the user asked to download, as understood before handing it to aria2. */
sealed class Source {
    /** HTTP(S), FTP, SFTP: one or several mirrors of the same file. */
    data class Url(val uris: List<String>) : Source()
    data class Magnet(val uri: String, val infoHash: String, val name: String?, val size: Long?) : Source()
    data class Torrent(val info: TorrentInfo) : Source()
    data class Metalink(val info: MetalinkInfo) : Source()
}

/** Recognises what the user pasted. Only schemes aria2 downloads by itself; everything else is refused. */
object LinkParser {
    val SCHEMES = setOf("http", "https", "ftp", "sftp")

    sealed class Result {
        data class Ok(val source: Source) : Result()
        data class Bad(val message: String) : Result()
    }

    /** Several links (one per line or separated by spaces) are mirrors of one file only if they end with the same name. */
    fun parse(text: String): Result {
        val t = text.trim()
        if (t.isEmpty()) return Result.Bad("Collez un lien.")
        if (t.length > 16_384) return Result.Bad("Lien trop long.")
        if (t.startsWith("magnet:", ignoreCase = true)) return magnet(t)
        val parts = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val uris = ArrayList<String>()
        for (p in parts) {
            val u = runCatching { URI(p) }.getOrNull() ?: return Result.Bad("Ce lien n'est pas valide.")
            val scheme = u.scheme?.lowercase() ?: return Result.Bad("Il manque le début du lien (http://, https://, ftp://, sftp:// ou magnet:).")
            if (scheme !in SCHEMES) return Result.Bad("Type de lien non pris en charge ($scheme:). Liens acceptés : http, https, ftp, sftp, magnet.")
            if (u.host.isNullOrEmpty()) return Result.Bad("Ce lien n'a pas d'adresse de serveur.")
            // The TV's own services (aria2's RPC, the CastBridge API) are not download sources.
            if (isLoopback(u.host)) return Result.Bad("Ce lien désigne la TV elle-même : refusé.")
            uris += p
        }
        if (uris.size > 16) return Result.Bad("Trop de liens à la fois (16 au maximum pour un même fichier).")
        return Result.Ok(Source.Url(uris))
    }

    fun magnet(t: String): Result {
        val q = t.substringAfter('?', "")
        val params = q.split('&').filter { it.contains('=') }.map {
            it.substringBefore('=').lowercase() to runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrDefault(it.substringAfter('='))
        }
        val xt = params.filter { it.first == "xt" }.map { it.second }
        val btih = xt.firstOrNull { it.startsWith("urn:btih:", ignoreCase = true) }?.substring(9)
            ?: return Result.Bad("Lien magnet sans identifiant BitTorrent (xt=urn:btih:…).")
        val hash = normaliseHash(btih) ?: return Result.Bad("Identifiant BitTorrent illisible dans le lien magnet.")
        val name = params.firstOrNull { it.first == "dn" }?.second?.takeIf { it.isNotBlank() }
        val size = params.firstOrNull { it.first == "xl" }?.second?.toLongOrNull()?.takeIf { it > 0 }
        return Result.Ok(Source.Magnet(t, hash, name, size))
    }

    /** 40 hex digits, or 32 base32 characters (older magnets), to lowercase hex. */
    fun normaliseHash(h: String): String? = when {
        h.length == 40 && h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> h.lowercase()
        h.length == 32 -> base32ToHex(h.uppercase())
        else -> null
    }

    private fun base32ToHex(s: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0L; var n = 0
        val out = StringBuilder()
        for (c in s) {
            val v = alphabet.indexOf(c); if (v < 0) return null
            bits = (bits shl 5) or v.toLong(); n += 5
            while (n >= 8) { n -= 8; out.append("%02x".format(((bits shr n) and 0xff).toInt())) }
        }
        return out.toString().takeIf { it.length == 40 }
    }

    fun isLoopback(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        return h == "localhost" || h.endsWith(".localhost") || h.startsWith("127.") || h == "::1" || h == "0:0:0:0:0:0:0:1" || h == "0.0.0.0" || h == "::"
    }

    /** The link as shown to people: without "user:password@" (SFTP/FTP links may carry credentials). */
    fun display(u: String): String = u.replace(Regex("^([a-zA-Z][a-zA-Z0-9+.-]*://)[^/@]*@"), "$1")

    /** A readable name for a URL download before aria2 knows the real one (last path segment, decoded). */
    fun nameFromUrl(u: String): String? = runCatching {
        val path = URI(u).rawPath ?: return null
        URLDecoder.decode(path.substringAfterLast('/').replace("+", "%2B"), "UTF-8").takeIf { it.isNotBlank() }
    }.getOrNull()
}

// ---------------------------------------------------------------------------------------------------------------
// Bencode (.torrent) - just enough to show a name and a total size, and to plan the space before adding it
// ---------------------------------------------------------------------------------------------------------------

object Bencode {
    class Error(msg: String) : IllegalArgumentException(msg)

    /** Decoded value: Long, ByteArray (strings are bytes), List, Map<String, Any> (keys as UTF-8). */
    fun decode(b: ByteArray): Any = Reader(b).let { r -> r.value().also { if (r.i != b.size) throw Error("trailing data") } }

    /** Byte range [start, end) of the top-level "info" dictionary (its SHA-1 is the torrent's info hash). */
    fun infoRange(b: ByteArray): IntRange? {
        val r = Reader(b)
        if (b.isEmpty() || b[0] != 'd'.code.toByte()) return null
        r.i = 1
        while (r.i < b.size && b[r.i] != 'e'.code.toByte()) {
            val k = String(r.bytes(), Charsets.UTF_8)
            val start = r.i
            r.value()
            if (k == "info") return start until r.i
        }
        return null
    }

    private class Reader(val b: ByteArray) {
        var i = 0
        var depth = 0
        fun value(): Any {
            if (i >= b.size) throw Error("unexpected end")
            return when (b[i].toInt().toChar()) {
                'i' -> { i++; val e = indexOf('e'); val v = String(b, i, e - i, Charsets.US_ASCII).toLongOrNull() ?: throw Error("bad integer"); i = e + 1; v }
                'l' -> { i++; enter(); val l = ArrayList<Any>(); while (peek() != 'e') l += value(); i++; depth--; l }
                'd' -> { i++; enter(); val m = LinkedHashMap<String, Any>(); while (peek() != 'e') { val k = String(bytes(), Charsets.UTF_8); m[k] = value() }; i++; depth--; m }
                in '0'..'9' -> bytes()
                else -> throw Error("bad token at $i")
            }
        }
        fun enter() { if (++depth > 64) throw Error("too deep") }
        fun peek(): Char { if (i >= b.size) throw Error("unexpected end"); return b[i].toInt().toChar() }
        fun indexOf(c: Char): Int { var j = i; while (j < b.size && b[j] != c.code.toByte()) j++; if (j >= b.size) throw Error("unexpected end"); return j }
        fun bytes(): ByteArray {
            val colon = indexOf(':')
            val len = String(b, i, colon - i, Charsets.US_ASCII).toIntOrNull() ?: throw Error("bad length")
            if (len < 0 || colon + 1 + len > b.size) throw Error("string past end")
            i = colon + 1 + len
            return b.copyOfRange(colon + 1, colon + 1 + len)
        }
    }
}

data class TorrentFile(val path: String, val length: Long)

data class TorrentInfo(val name: String, val totalLength: Long, val files: List<TorrentFile>, val infoHash: String, val private: Boolean) {
    companion object {
        const val MAX_BYTES = 4 shl 20        // a .torrent is a few hundred kB at most; refuse anything absurd

        fun parse(b: ByteArray): TorrentInfo {
            if (b.size > MAX_BYTES) throw Bencode.Error("torrent too large")
            val root = Bencode.decode(b) as? Map<*, *> ?: throw Bencode.Error("not a torrent")
            val info = root["info"] as? Map<*, *> ?: throw Bencode.Error("no info dictionary")
            val name = (info["name.utf-8"] ?: info["name"])?.let { String(it as ByteArray, Charsets.UTF_8) } ?: "torrent"
            val files = (info["files"] as? List<*>)?.map { f ->
                val m = f as? Map<*, *> ?: throw Bencode.Error("bad file entry")
                val parts = ((m["path.utf-8"] ?: m["path"]) as? List<*>).orEmpty().map { String(it as ByteArray, Charsets.UTF_8) }
                TorrentFile(parts.joinToString("/"), (m["length"] as? Long) ?: 0)
            } ?: listOf(TorrentFile(name, (info["length"] as? Long) ?: throw Bencode.Error("no length")))
            val range = Bencode.infoRange(b) ?: throw Bencode.Error("no info dictionary")
            val hash = MessageDigest.getInstance("SHA-1").digest(b.copyOfRange(range.first, range.last + 1)).joinToString("") { "%02x".format(it) }
            return TorrentInfo(name, files.sumOf { it.length }, files, hash, (info["private"] as? Long) == 1L)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Metalink (RFC 5854 / 3.0): names and sizes only; aria2 does the real work (mirrors, checksums)
// ---------------------------------------------------------------------------------------------------------------

data class MetalinkInfo(val names: List<String>, val totalLength: Long?) {
    companion object {
        const val MAX_BYTES = 1 shl 20

        fun parse(b: ByteArray): MetalinkInfo {
            if (b.size > MAX_BYTES) throw IllegalArgumentException("metalink too large")
            val x = String(b, Charsets.UTF_8)
            if (!x.contains("<metalink", ignoreCase = true)) throw IllegalArgumentException("not a metalink")
            // Neither DOCTYPE nor entities are needed: refuse them (no XML entity expansion games, aria2 would parse them).
            if (x.contains("<!DOCTYPE", ignoreCase = true) || x.contains("<!ENTITY", ignoreCase = true)) throw IllegalArgumentException("DOCTYPE not allowed")
            val files = Regex("<file\\s[^>]*name\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</file>", RegexOption.DOT_MATCHES_ALL).findAll(x).toList()
            val names = files.map { unescape(it.groupValues[1]) }
            val sizes = files.map { f -> Regex("<size>\\s*(\\d+)\\s*</size>").find(f.groupValues[2])?.groupValues?.get(1)?.toLongOrNull() }
            return MetalinkInfo(names, if (sizes.isNotEmpty() && sizes.all { it != null }) sizes.sumOf { it!! } else null)
        }

        private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    }
}
