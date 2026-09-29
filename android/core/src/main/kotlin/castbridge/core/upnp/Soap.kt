package castbridge.core.upnp

object Xml {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** First text content of <tag> (namespace prefix ignored), unescaped. */
    fun tag(xml: String, tag: String): String? =
        Regex("<(?:[\\w.-]+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.let(::unesc)

    fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}

object Soap {
    const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RC = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CD = "urn:schemas-upnp-org:service:ContentDirectory:1"

    fun envelope(service: String, action: String, args: List<Pair<String, String>>): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            "<s:Body><u:$action xmlns:u=\"$service\">" +
            args.joinToString("") { (k, v) -> "<$k>${Xml.esc(v)}</$k>" } +
            "</u:$action></s:Body></s:Envelope>"

    fun soapAction(service: String, action: String) = "\"$service#$action\""

    fun hms(sec: Long): String = "%02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60)

    fun parseHms(s: String?): Long {
        val p = s?.split(":")?.mapNotNull { it.substringBefore('.').toLongOrNull() } ?: return 0
        return if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2] else 0
    }
}

object Didl {
    fun item(url: String, title: String, mime: String, protocolInfo: String): String =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1"><dc:title>${Xml.esc(title)}</dc:title>""" +
            "<upnp:class>${if (mime.startsWith("audio")) "object.item.audioItem" else "object.item.videoItem"}</upnp:class>" +
            """<res protocolInfo="${Xml.esc(protocolInfo)}">${Xml.esc(url)}</res></item></DIDL-Lite>"""

    fun protocolInfo(mime: String, features: String) = "http-get:*:$mime:$features"

    data class Entry(val id: String, val title: String, val isContainer: Boolean, val url: String?)

    fun parse(didl: String): List<Entry> =
        Regex("<(container|item)\\s([^>]*)>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(didl).map { m ->
            val attrs = m.groupValues[2]
            Entry(
                id = Regex("""\bid="([^"]*)"""").find(attrs)?.groupValues?.get(1)?.let(Xml::unesc) ?: "",
                title = Xml.tag(m.groupValues[3], "title") ?: "?",
                isContainer = m.groupValues[1] == "container",
                url = Regex("<res[^>]*>([^<]+)</res>").find(m.groupValues[3])?.groupValues?.get(1)?.let(Xml::unesc),
            )
        }.toList()
}
