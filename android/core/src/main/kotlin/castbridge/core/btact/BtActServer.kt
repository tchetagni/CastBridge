package castbridge.core.btact

import castbridge.core.owner.DeviceRequestText
import castbridge.core.tv.WdCode
import castbridge.core.tv.activation.ActivationAttemptGate
import castbridge.core.tv.activation.LockedActivationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The TV side of « activer par Bluetooth sans appairage » (`docs/BT-PLUG-AND-PLAY.md`): serves ONE connection of the activation service `…0008` on a LOCKED TV, over any pair of streams (an INSECURE
 * RFCOMM socket or an insecure L2CAP channel on Android, in-memory pipes in the tests). No pairing box: it is the 6-digit connection code, proven by a PAKE ([Cpace]), that authenticates the phone.
 *
 * The order is the one of the HTTP route ([LockedActivationApi]), for the same reasons: (1) the terms of use first, so a TV that did not accept them never says whether a code is right; (2) the
 * shared [gate] before any computation: a peer locked for 60 s, or the global cap of wrong codes, is turned away without a single operation on the curve; (3) the PAKE; (4) the phone's CONFIRM, the
 * only place where the TV learns the code was wrong, and where it COUNTS the attempt in the gate (5 wrong codes lock that Bluetooth peer for 60 s; 20 wrong codes in 10 minutes close every way in,
 * the HTTP route too, and vice versa); (5) only then the encrypted channel, which carries the SAME two messages as the HTTP route: the complete device request ([DeviceRequestText.complete]) and the
 * key (the same verifier as a pasted key, [install]), with the same allowances per peer ([ActivationAttemptGate.takeRead], [ActivationAttemptGate.takeTry]) and three refused keys per link at most.
 *
 * Nothing is ever logged here (no code, no key, no request), and [serve] returns a word ([End]) only. Not started on an activated TV: the host (`ActivationBtHost`) only runs while the locked
 * route is open, so [code] is the code on the activation screen.
 */
class BtActServer(
    /** The connection code the activation screen shows (6 digits); null = no code (the route is closed): turned away as unavailable. */
    private val code: () -> String?,
    private val gate: ActivationAttemptGate,
    private val termsAccepted: () -> Boolean,
    /** The TV's own device request text (`ActivationCenter.requestText()`); only its rebuilt [DeviceRequestText.complete] form ever leaves. null = not available yet. */
    private val deviceRequest: () -> String?,
    /** The same verifier as a pasted key (`ActivationCenter.installFromWifi`). */
    private val install: (String) -> LockedActivationApi.Install,
    private val tvName: () -> String,
    private val tvVersion: String,
    /** Told the peer (never the code) that proved the code: the activation screen shows « téléphone relié ». A failing listener changes nothing. */
    private val onAuthorized: (String) -> Unit = {},
    private val entropy: BtActWire.Entropy = BtActWire.Entropy.secure(),
) {
    /** How a connection ended, in one word (the host may log it: it holds no secret). */
    enum class End { NOT_OURS, PROTOCOL, VERSION, TERMS, TURNED_AWAY, UNAVAILABLE, WRONG_CODE, SERVED, LINK_LOST }

    /** [peer] = the Bluetooth address of the socket (never something the peer wrote). */
    fun serve(input: InputStream, output: OutputStream, peer: String?): End {
        val din = DataInputStream(input); val dout = DataOutputStream(output)
        val peerKey = ActivationAttemptGate.bluetoothPeer(peer)
        var keys: BtActKeys? = null; var channel: BtActChannel? = null
        try {
            val magic = ByteArray(4); din.readFully(magic)
            if (!magic.contentEquals(BtActWire.MAGIC.toByteArray(Charsets.US_ASCII))) return End.NOT_OURS
            val hello = BtActWire.readClear(din)
            if (hello.type != BtActWire.Msg.HELLO || hello.body.size != 1 + BtActWire.SID_BYTES + BtActWire.POINT_BYTES) return refuse(dout, BtActWire.Err.BAD_MESSAGE, End.PROTOCOL)
            if ((hello.body[0].toInt() and 0xFF) != BtActWire.VERSION) return refuse(dout, BtActWire.Err.VERSION, End.VERSION)
            // (1) the terms BEFORE the code, like the HTTP route's 409: nothing is learnt about the code of a TV whose terms are not accepted
            if (!termsAccepted()) return refuse(dout, BtActWire.Err.TERMS, End.TERMS)
            // (2) the gate BEFORE any computation: a locked peer or a closed TV costs one frame, no operation on the curve
            gate.refusal(peerKey)?.let { r -> return turnedAway(dout, r) }
            val connectionCode = code()?.takeIf(WdCode::isValid) ?: return refuse(dout, BtActWire.Err.UNAVAILABLE, End.UNAVAILABLE)

            // (3) the PAKE: the generator comes from the code and the session id the phone chose; the TV answers with its own point
            val sid = hello.body.copyOfRange(1, 1 + BtActWire.SID_BYTES)
            val ya = hello.body.copyOfRange(1 + BtActWire.SID_BYTES, hello.body.size)
            val generator = Cpace.generator(connectionCode.toByteArray(Charsets.US_ASCII), BtActWire.CHANNEL_ID, sid)
            val secret = entropy.bytes(32)
            val yb = Cpace.message(secret, generator)
            val k = Cpace.sharedPoint(secret, ya)
            secret.fill(0)
            if (k == null) { gate.recordWrong(peerKey); return refuse(dout, BtActWire.Err.BAD_MESSAGE, End.PROTOCOL) }     // a point of small order: hostile, counted like a wrong code
            val sessionKeys = BtActKeys.derive(Cpace.isk(sid, k, ya, BtActWire.AD_PHONE, yb, BtActWire.AD_TV), sid, ya, yb).also { keys = it }
            k.fill(0)
            BtActWire.writeClear(dout, BtActWire.Msg.REPLY, yb)

            // (4) the phone's CONFIRM: the one place where a wrong code shows, and where the attempt is counted (before the TV answers anything that depends on the key)
            val confirm = BtActWire.readClear(din)
            if (confirm.type != BtActWire.Msg.CONFIRM_PHONE || !BtActKeys.same(confirm.body, sessionKeys.tagPhone())) {
                val locked = gate.recordWrong(peerKey)
                return if (locked is ActivationAttemptGate.Refusal.Locked) turnedAway(dout, locked, End.WRONG_CODE) else refuse(dout, BtActWire.Err.BAD_CODE, End.WRONG_CODE)
            }
            gate.recordRight(peerKey)
            runCatching { onAuthorized(peerKey) }
            BtActWire.writeClear(dout, BtActWire.Msg.CONFIRM_TV, sessionKeys.tagTv())

            // (5) the encrypted channel: the same two messages as the HTTP route
            val ch = BtActChannel(din, dout, sessionKeys.tvToPhone, sessionKeys.phoneToTv).also { channel = it }
            ch.send(BtActWire.Type.WELCOME, welcome())
            return serveChannel(ch, peerKey)
        } catch (e: BtActWire.ProtocolException) {
            return End.PROTOCOL
        } catch (e: IOException) {
            return End.LINK_LOST
        } finally {
            keys?.wipe(); channel?.wipe()
        }
    }

    private fun refuse(out: DataOutputStream, err: BtActWire.Err, end: End): End {
        BtActWire.writeClear(out, BtActWire.Msg.ERROR, BtActWire.errorBody(err))
        return end
    }

    private fun turnedAway(out: DataOutputStream, r: ActivationAttemptGate.Refusal, end: End = End.TURNED_AWAY): End {
        when (r) {
            ActivationAttemptGate.Refusal.Closed -> BtActWire.writeClear(out, BtActWire.Msg.ERROR, BtActWire.errorBody(BtActWire.Err.CLOSED))
            is ActivationAttemptGate.Refusal.Locked -> BtActWire.writeClear(out, BtActWire.Msg.ERROR, BtActWire.errorBody(BtActWire.Err.LOCKED, r.seconds))
        }
        return end
    }

    private fun welcome(): ByteArray {
        fun line(s: String, max: Int) = s.replace(Regex("[\\r\\n\\t]"), " ").take(max)
        return "tv=${line(tvName(), 120)}\nv=${line(tvVersion, 40)}\n".toByteArray(Charsets.UTF_8)
    }

    private fun serveChannel(ch: BtActChannel, peerKey: String): End {
        var rejected = 0
        repeat(MAX_FRAMES) {
            val f = ch.receive()
            when (f.type) {
                BtActWire.Type.READ_REQUEST -> {
                    if (!gate.takeRead(peerKey)) ch.send(BtActWire.Type.LIMIT)
                    else {
                        val text = runCatching { deviceRequest()?.let(DeviceRequestText::complete) }.getOrNull()
                        if (text == null) ch.send(BtActWire.Type.UNAVAILABLE) else ch.send(BtActWire.Type.REQUEST, text.toByteArray(Charsets.UTF_8))
                    }
                }
                BtActWire.Type.INSTALL -> {
                    if (!gate.takeTry(peerKey)) ch.send(BtActWire.Type.LIMIT)
                    else when (val r = runCatching { install(String(f.payload, Charsets.UTF_8).removePrefix(BOM).trim()) }.getOrElse { LockedActivationApi.Install.Rejected("Erreur de vérification") }) {
                        is LockedActivationApi.Install.Accepted -> { ch.send(BtActWire.Type.INSTALLED, r.label.toByteArray(Charsets.UTF_8)); finish(ch); return End.SERVED }
                        is LockedActivationApi.Install.Rejected -> { ch.send(BtActWire.Type.REJECTED, r.message.toByteArray(Charsets.UTF_8).take(BtActWire.MAX_PAYLOAD).toByteArray()); if (++rejected >= MAX_REFUSED_KEYS) { finish(ch); return End.SERVED } }
                    }
                }
                BtActWire.Type.FIN -> { finish(ch); return End.SERVED }
                else -> return End.PROTOCOL
            }
        }
        finish(ch)
        return End.SERVED
    }

    private fun finish(ch: BtActChannel) { runCatching { ch.send(BtActWire.Type.FIN) } }

    companion object {
        /** Frames served on one link (a read, a key, a second try…): then the link is closed. */
        const val MAX_FRAMES = 8
        /** Refused keys on one link, then it is closed (the owner channel's rule). */
        const val MAX_REFUSED_KEYS = 3
        private const val BOM = "﻿"
    }
}
