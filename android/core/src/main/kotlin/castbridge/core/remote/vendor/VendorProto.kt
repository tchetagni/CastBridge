package castbridge.core.remote.vendor

import castbridge.core.remote.RemoteKey

/**
 * Minimal protobuf writer for the white-label CVTE/Amlogic remote service (docs/REMOTE-VENDOR-CVTE.md): one message with
 * field 1 = type (varint) and field 2 = action (string). Written by hand from the observed wire format, no dependency.
 */
object VendorProto {
    const val TYPE_KEY = 1

    fun varint(v: Long): ByteArray {
        require(v >= 0)
        val out = java.io.ByteArrayOutputStream(10)
        var x = v
        while (x >= 0x80) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
        return out.toByteArray()
    }

    /** `08 <type> 12 <len> <action>`. */
    fun event(type: Int, action: String): ByteArray {
        val a = action.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x08) + varint(type.toLong()) + byteArrayOf(0x12) + varint(a.size.toLong()) + a
    }

    /** A key event: the action is the Android key code in decimal. */
    fun key(androidCode: Int): ByteArray = event(TYPE_KEY, androidCode.toString())

    /** What the relay may send: the closed [RemoteKey] list, never power or sleep. */
    fun key(k: RemoteKey): ByteArray = key(k.code)
}

/** The JSON information frame the service pushes on connection (`status` 500 with `msg:"successful"` is normal). */
data class VendorInfo(val name: String?, val width: Int?, val height: Int?, val websocketPort: Int?, val capabilities: Set<String>) {
    companion object {
        fun parse(json: String): VendorInfo? = runCatching {
            if (!json.trimStart().startsWith("{")) return null
            val data = Regex("\"data\"\\s*:\\s*\\{").find(json) ?: return null
            fun str(k: String) = Regex("\"$k\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json, data.range.first)?.groupValues?.get(1)
            fun int(k: String) = Regex("\"$k\"\\s*:\\s*\"?(\\d+)\"?").find(json, data.range.first)?.groupValues?.get(1)?.toIntOrNull()
            val cfg = Regex("\"config\"\\s*:\\s*\\{([^}]*)\\}").find(json)?.groupValues?.get(1).orEmpty()
            val caps = Regex("\"(\\w+)\"\\s*:\\s*true").findAll(cfg).map { it.groupValues[1] }.toSet()
            VendorInfo(str("name"), int("width"), int("height"), int("websocketPort") ?: int("port"), caps)
        }.getOrNull()
    }
}
