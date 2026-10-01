package castbridge.core.owner

import java.io.InputStream
import java.io.OutputStream

/**
 * The TV side of the owner Bluetooth channel ([OwnerFrames.SERVICE_UUID]): answers on ONE connection, from a phone that is already paired with the TV.
 * It exposes exactly two things and nothing else: the device request (code + fingerprints: not secret) and the reception of an activation, which is
 * verified by [activate] (signature, binding to THIS device, key scope): a forged token is simply refused. Works while the TV is locked.
 * Android glue (the RFCOMM server socket) lives in the receiver app; this class only needs two streams, so it is tested on the JVM.
 */
class OwnerChannelServer(
    private val deviceInfo: () -> String,
    private val activate: (String) -> ActivationResult,
    private val onRefusal: () -> Unit = {},
    /** Text sent back when the token is valid (the TV may only STAGE it for the owner to confirm: then it says so). */
    private val acceptedText: String = "Activée",
) {
    companion object { const val MAX_FRAMES = 12 }

    fun serve(input: InputStream, output: OutputStream) {
        if (!OwnerFrames.readHello(input)) return                       // not our channel: close
        output.write(OwnerFrames.hello()); output.flush()
        var refusals = 0
        repeat(MAX_FRAMES) {
            val f = OwnerFrames.read(input) ?: return
            when (f.type) {
                OwnerFrames.DEVICE_INFO_REQUEST -> output.write(OwnerFrames.encode(OwnerFrames.DEVICE_INFO, deviceInfo()))
                OwnerFrames.ACTIVATION -> {
                    val token = f.text.trim()
                    val r = if (token.length in 20..OwnerFrames.MAX_PAYLOAD) runCatching { activate(token) }.getOrElse { ActivationResult.Rejected(Rejection.MALFORMED, "Erreur de vérification") }
                    else ActivationResult.Rejected(Rejection.MALFORMED, "Activation illisible")
                    output.write(resultFrame(r))
                    if (r is ActivationResult.Rejected) { onRefusal(); if (++refusals >= 3) { output.flush(); return } }   // three refusals on one link: hang up
                }
                else -> output.write(OwnerFrames.encode(OwnerFrames.RESULT, byteArrayOf(0) + "Non pris en charge par cette TV".toByteArray(Charsets.UTF_8)))
            }
            output.flush()
        }
    }

    private fun resultFrame(r: ActivationResult): ByteArray = when (r) {
        is ActivationResult.Accepted -> OwnerFrames.encode(OwnerFrames.RESULT, byteArrayOf(1) + acceptedText.toByteArray(Charsets.UTF_8))
        is ActivationResult.Rejected -> OwnerFrames.encode(OwnerFrames.RESULT, byteArrayOf(0) + r.message.toByteArray(Charsets.UTF_8))
    }
}

/** The console side: reads the TV's device request and hands it an activation. Two streams in, two out; no Android here. */
class OwnerChannelClient(private val input: InputStream, private val output: OutputStream) {
    data class Answer(val ok: Boolean, val message: String)

    fun hello(): Boolean { output.write(OwnerFrames.hello()); output.flush(); return OwnerFrames.readHello(input) }

    /** The text of the device request (`code=…`, `k=…`, `factor=…`), or null if the TV does not answer properly. */
    fun deviceInfo(): String? {
        output.write(OwnerFrames.encode(OwnerFrames.DEVICE_INFO_REQUEST)); output.flush()
        val f = OwnerFrames.read(input) ?: return null
        return if (f.type == OwnerFrames.DEVICE_INFO) f.text else null
    }

    fun sendActivation(token: String): Answer {
        if (runCatching { output.write(OwnerFrames.encode(OwnerFrames.ACTIVATION, token)); output.flush() }.isFailure) return Answer(false, "Liaison coupée avec la TV")
        val f = OwnerFrames.read(input) ?: return Answer(false, "La TV n'a pas répondu")
        if (f.type != OwnerFrames.RESULT || f.payload.isEmpty()) return Answer(false, "Réponse inattendue de la TV")
        return Answer(f.payload[0].toInt() == 1, String(f.payload, 1, f.payload.size - 1, Charsets.UTF_8))
    }
}
