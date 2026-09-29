package castbridge.core.dl

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Size of an HTTP(S) download before it starts (HEAD, then a 1-byte ranged GET for servers that do not answer HEAD
 * properly), following up to 5 redirects by hand (HttpURLConnection does not follow http -> https) and refusing any
 * redirect to the TV itself. null = unknown (FTP/SFTP, no Content-Length, server down): the in-flight check covers it.
 */
object HttpProbe {
    fun size(url: String, timeoutMs: Int = 6000): Long? = runCatching { probe(url, timeoutMs) }.getOrNull()

    private fun probe(start: String, timeoutMs: Int): Long? {
        var url = start
        repeat(6) {
            val u = URI(url)
            val scheme = u.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (u.host == null || LinkParser.isLoopback(u.host)) return null
            for (method in listOf("HEAD", "GET")) {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.instanceFollowRedirects = false
                    c.requestMethod = method
                    c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
                    c.setRequestProperty("User-Agent", "aria2/1.37.0")
                    if (method == "GET") c.setRequestProperty("Range", "bytes=0-0")
                    val code = c.responseCode
                    if (code in 300..399) {
                        val loc = c.getHeaderField("Location") ?: return null
                        url = URI(url).resolve(loc).toString()
                        return@repeat
                    }
                    if (method == "GET" && code == 206) {
                        return c.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull()
                    }
                    if (method == "HEAD" && code == 200) {
                        c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
                    }
                    if (method == "GET" && code == 200) return c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 1 }
                } finally { c.disconnect() }                  // never drain a body: a server ignoring Range would send the whole file
            }
            return null
        }
        return null
    }
}
