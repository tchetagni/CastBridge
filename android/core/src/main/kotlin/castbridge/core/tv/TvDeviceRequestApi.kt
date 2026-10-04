package castbridge.core.tv

import castbridge.core.net.JsonLite
import castbridge.core.owner.OwnerFrames

/**
 * `GET /api/tv/device-request` (docs/TV-DEMANDE-APPAREIL.md): what the owner's tools need to build an activation key for this TV, and NOTHING else.
 * Same authentication as every route (PIN header or trusted phone's token: the guard runs before extensions), read-only, additive.
 *
 * Answer: `{"code":"XXXX-…","k":2,"factors":[{"type":"FLASH","fingerprint":"<32 hex>"}],"install":"<64 hex>"|null,"installSig":"<64 hex>"|null}`: exactly these keys (`installSig` = the Ed25519
 * public key of the wallet `bind` proof, added by the W23-05 audit corrections; an older reader ignores the unknown field). [requestText] is the TV's own
 * device request (`ActivationCenter.requestText()`); it is parsed and only these five things are re-emitted, so a line this version does not know (a newer TV, a mistake)
 * never leaves. The fingerprints are salted hashes, `install` is the installation's PUBLIC key: no PIN, token, private key, owner password or raw hardware value.
 */
class TvDeviceRequestApi(private val requestText: () -> String?) : ApiExtension {
    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != PATH) return null
        if (method != "GET") return ApiReply(405, """{"error":"use GET"}""")
        val body = runCatching { requestText()?.let(::json) }.getOrNull() ?: return ApiReply(503, """{"error":"demande d'appareil indisponible"}""")
        return ApiReply(200, body)
    }

    companion object {
        const val PATH = "/api/tv/device-request"

        /** The five-key JSON of [requestText], null when it is not a readable device request. Strings go through the JSON helper (and are hex or a checked code anyway). */
        fun json(requestText: String): String? {
            val d = OwnerFrames.parseDeviceInfo(requestText) ?: return null
            val factors = d.fp.byKind.entries.joinToString(",", "[", "]") { """{"type":${JsonLite.quote(it.key.name)},"fingerprint":${JsonLite.quote(it.value)}}""" }
            val install = d.installPub?.joinToString("") { "%02x".format(it) }?.let(JsonLite::quote) ?: "null"
            val installSig = d.installSig?.joinToString("") { "%02x".format(it) }?.let(JsonLite::quote) ?: "null"
            return """{"code":${JsonLite.quote(d.code)},"k":${d.k},"factors":$factors,"install":$install,"installSig":$installSig}"""
        }
    }
}
