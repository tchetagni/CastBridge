package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import castbridge.core.ux.SignalLevel

/**
 * Codec JSON de `play-v1` avec le `Json` du cœur. Décodage des messages CLIENT STRICT : type inconnu ⇒ `UNSUPPORTED`, champ hors borne,
 * mal typé ou message de plus de 2 048 octets ⇒ `BAD_REQUEST`. Les clés inconnues sont ignorées (additif, règle W19). Le codec n'ajoute
 * RIEN à la vue : ce qui part est ce que `QuizRoom.view` a déjà filtré.
 */
object PlayCodec {
    sealed class Decoded {
        data class Ok(val msg: ClientMsg) : Decoded()
        data class Bad(val reason: String, val detail: String) : Decoded()
    }

    private class BadField(val detail: String) : Exception(detail)

    // ------------------------------------------------------------------ client → serveur

    fun encode(m: ClientMsg): String = Json.write(when (m) {
        is ClientMsg.Hello -> linkedMapOf("t" to m.type, "proto" to m.proto, "caps" to m.caps, "deviceHash" to m.deviceHash, "ticket" to m.ticket)
        is ClientMsg.Create -> linkedMapOf("t" to m.type, "name" to m.name, "mode" to m.mode, "activation" to m.activation, "rentals" to m.rentals)
        is ClientMsg.Join -> linkedMapOf("t" to m.type, "code" to m.code, "name" to m.name, "token" to m.token, "deviceHash" to m.deviceHash, "spectate" to m.spectate)
        is ClientMsg.Resume -> linkedMapOf("t" to m.type, "roomId" to m.roomId, "token" to m.token, "lastSeq" to m.lastSeq)
        is ClientMsg.Act -> linkedMapOf("t" to m.type, "seq" to m.seq, "questionId" to m.questionId, "action" to m.action, "choice" to m.choice, "arg" to m.arg)
        is ClientMsg.RelayAct -> linkedMapOf("t" to m.type, "seq" to m.seq, "token" to m.token, "questionId" to m.questionId, "choice" to m.choice, "localElapsedMono" to m.localElapsedMono)
        is ClientMsg.Scope -> linkedMapOf("t" to m.type, "open" to m.open)
        is ClientMsg.Kick -> linkedMapOf("t" to m.type, "playerId" to m.playerId)
        is ClientMsg.Mute -> linkedMapOf("t" to m.type, "playerId" to m.playerId, "muted" to m.muted)
        is ClientMsg.Report -> linkedMapOf("t" to m.type, "questionId" to m.questionId, "reason" to m.reason)
        is ClientMsg.Pong -> linkedMapOf("t" to m.type, "id" to m.id)
    })

    fun decodeClient(text: String): Decoded {
        val size = text.toByteArray(Charsets.UTF_8).size
        if (size > PlayProtocol.MAX_CREATE_BYTES) return Decoded.Bad(PlayProtocol.BAD_REQUEST, "message trop long")
        val m = try { Json.parse(text) as? Map<*, *> ?: return Decoded.Bad(PlayProtocol.BAD_REQUEST, "objet attendu") } catch (e: Json.ParseError) {
            return Decoded.Bad(PlayProtocol.BAD_REQUEST, "JSON invalide")
        }
        val t = m["t"] as? String ?: return Decoded.Bad(PlayProtocol.BAD_REQUEST, "type manquant")
        if (size > PlayProtocol.MAX_MESSAGE_BYTES && t != "create") return Decoded.Bad(PlayProtocol.BAD_REQUEST, "message trop long")   // seul `create` porte une activation
        val f = Fields(m)
        return try {
            Decoded.Ok(when (t) {
                "hello" -> ClientMsg.Hello(f.int("proto", 1..1_000), f.strings("caps"), f.str("deviceHash", PlayProtocol.MAX_ID, false), f.str("ticket", PlayProtocol.MAX_TICKET, false))
                "create" -> ClientMsg.Create(f.str("name", PlayProtocol.MAX_NAME_WIRE, false), f.str("mode", 16, false), f.token("activation"), f.tokens("rentals"))
                "join" -> ClientMsg.Join(f.str("code", 16, true)!!, f.str("name", PlayProtocol.MAX_NAME_WIRE, false), f.str("token", PlayProtocol.MAX_TOKEN, false),
                    f.str("deviceHash", PlayProtocol.MAX_ID, false), f.boolOr("spectate", false))
                "resume" -> ClientMsg.Resume(f.str("roomId", PlayProtocol.MAX_ID, true)!!, f.str("token", PlayProtocol.MAX_TOKEN, true)!!, f.long("lastSeq", 0L..(1L shl 53), true)!!)
                "act" -> {
                    val action = f.str("action", 16, true)!!
                    if (action !in PlayProtocol.ACTIONS) throw BadField("action inconnue")
                    ClientMsg.Act(f.str("questionId", PlayProtocol.MAX_ID, false), action, f.intOrNull("choice", -1..3), f.str("arg", PlayProtocol.MAX_ARG, false), f.long("seq", 0L..(1L shl 53), false) ?: 0L)
                }
                "relayAct" -> ClientMsg.RelayAct(f.str("token", PlayProtocol.MAX_TOKEN, true)!!, f.str("questionId", PlayProtocol.MAX_ID, true)!!, f.int("choice", 0..3),
                    f.long("localElapsedMono", 0L..PlayProtocol.MAX_ELAPSED_MS, true)!!, f.long("seq", 0L..(1L shl 53), false) ?: 0L)
                "scope" -> ClientMsg.Scope(f.bool("open", true)!!)
                "kick" -> ClientMsg.Kick(f.str("playerId", PlayProtocol.MAX_ID, true)!!)
                "mute" -> ClientMsg.Mute(f.str("playerId", PlayProtocol.MAX_ID, true)!!, f.bool("muted", true)!!)
                "report" -> ClientMsg.Report(f.str("questionId", PlayProtocol.MAX_ID, false), f.str("reason", PlayProtocol.MAX_REASON, true)!!)
                "pong" -> ClientMsg.Pong(f.str("id", PlayProtocol.MAX_ID, true)!!)
                else -> return Decoded.Bad(PlayProtocol.UNSUPPORTED, "type inconnu : ${t.take(24)}")
            })
        } catch (e: BadField) { Decoded.Bad(PlayProtocol.BAD_REQUEST, e.detail) }
    }

    private class Fields(val m: Map<*, *>) {
        fun str(k: String, max: Int, required: Boolean): String? {
            val v = m[k] ?: if (required) throw BadField("$k manquant") else return null
            val s = v as? String ?: throw BadField("$k : texte attendu")
            if (s.length > max) throw BadField("$k trop long")
            if (s.any { it.isISOControl() }) throw BadField("$k : caractère interdit")
            if (required && s.isEmpty()) throw BadField("$k vide")
            return s
        }
        fun strings(k: String): List<String> {
            val v = m[k] ?: return emptyList()
            val l = v as? List<*> ?: throw BadField("$k : liste attendue")
            if (l.size > PlayProtocol.MAX_CAPS) throw BadField("$k : trop d'éléments")
            return l.map { (it as? String)?.takeIf { s -> s.length <= 24 && s.none { c -> c.isISOControl() } } ?: throw BadField("$k : élément invalide") }
        }
        /** Un jeton signé (`cbx1.…`) : texte ASCII visible, borné ; absent ou `null` = aucun. */
        fun token(k: String): String? {
            val v = m[k] ?: return null
            val s = v as? String ?: throw BadField("$k : texte attendu")
            if (s.length > PlayProtocol.MAX_ACTIVATION) throw BadField("$k trop long")
            if (s.any { it.code !in 33..126 }) throw BadField("$k : caractère interdit")
            return s.ifEmpty { null }
        }
        fun tokens(k: String): List<String> {
            val v = m[k] ?: return emptyList()
            val l = v as? List<*> ?: throw BadField("$k : liste attendue")
            if (l.size > PlayProtocol.MAX_RENTALS) throw BadField("$k : trop d'éléments")
            return l.map { e -> (e as? String)?.takeIf { it.length in 1..PlayProtocol.MAX_ACTIVATION && it.all { c -> c.code in 33..126 } } ?: throw BadField("$k : élément invalide") }
        }
        fun long(k: String, r: LongRange, required: Boolean): Long? {
            val v = m[k] ?: if (required) throw BadField("$k manquant") else return null
            val n = (v as? Number)?.takeIf { it is Long || it is Int } ?: throw BadField("$k : entier attendu")
            return n.toLong().also { if (it !in r) throw BadField("$k hors borne") }
        }
        fun int(k: String, r: IntRange): Int = long(k, r.first.toLong()..r.last.toLong(), true)!!.toInt()
        fun intOrNull(k: String, r: IntRange): Int? = long(k, r.first.toLong()..r.last.toLong(), false)?.toInt()
        fun bool(k: String, required: Boolean): Boolean? {
            val v = m[k] ?: if (required) throw BadField("$k manquant") else return null
            return v as? Boolean ?: throw BadField("$k : booléen attendu")
        }
        fun boolOr(k: String, default: Boolean): Boolean = bool(k, false) ?: default
    }

    // ------------------------------------------------------------------ serveur → client

    fun encode(m: ServerMsg): String = Json.write(when (m) {
        is ServerMsg.Welcome -> linkedMapOf("t" to m.type, "seq" to m.seq, "roomId" to m.roomId, "code" to m.code, "token" to m.token, "role" to m.role.name,
            "playerId" to m.playerId, "proto" to m.proto, "caps" to m.caps)
        is ServerMsg.State -> linkedMapOf("t" to m.type, "seq" to m.seq, "full" to m.full, "view" to m.view)
        is ServerMsg.Question -> linkedMapOf("t" to m.type, "seq" to m.seq, "questionId" to m.questionId, "index" to m.index, "count" to m.count, "text" to m.text,
            "choices" to m.choices, "opensAtServerMs" to m.opensAtServerMs, "serverNowMs" to m.serverNowMs, "windowMs" to m.windowMs)
        is ServerMsg.Reveal -> linkedMapOf("t" to m.type, "seq" to m.seq, "questionId" to m.questionId, "index" to m.index, "answer" to m.answer, "explanation" to m.explanation)
        is ServerMsg.Safety -> linkedMapOf("t" to m.type, "seq" to m.seq, "scope" to m.view.scope.name, "level" to m.view.level.name, "word" to m.view.word,
            "text" to m.view.text, "action" to m.view.action, "detail" to m.view.detail)
        is ServerMsg.Ping -> linkedMapOf("t" to m.type, "seq" to m.seq, "id" to m.id, "serverNowMs" to m.serverNowMs)
        is ServerMsg.Error -> linkedMapOf<String, Any?>("t" to m.type, "seq" to m.seq, "reason" to m.reason, "message" to m.message, "retryable" to m.retryable)
            .also { if (m.retryAfterMs > 0) it["retryAfterMs"] = m.retryAfterMs }
        is ServerMsg.RoomGone -> linkedMapOf("t" to m.type, "seq" to m.seq, "reason" to m.reason)
        is ServerMsg.Replay -> linkedMapOf("t" to m.type, "seq" to m.seq, "events" to m.events.map { linkedMapOf("seq" to it.seq, "kind" to it.kind, "data" to it.data) })
        is ServerMsg.Ack -> linkedMapOf("t" to m.type, "seq" to m.seq, "ref" to m.ref, "result" to m.result)
    })

    /** Décodage côté client : null si le message est inconnu ou mal formé (un client ignore ce qu'il ne comprend pas). */
    @Suppress("UNCHECKED_CAST")
    fun decodeServer(text: String): ServerMsg? = try {
        val m = Json.parse(text) as? Map<String, Any?> ?: return null
        fun s(k: String) = m[k] as? String
        fun l(k: String) = (m[k] as? Number)?.toLong()
        val seq = l("seq") ?: 0L
        when (s("t")) {
            "welcome" -> ServerMsg.Welcome(seq, s("roomId")!!, s("code")!!, s("token")!!, PlayRole.valueOf(s("role")!!), s("playerId"), l("proto")?.toInt() ?: 1,
                (m["caps"] as? List<*>)?.filterIsInstance<String>() ?: emptyList())
            "state" -> ServerMsg.State(seq, m["view"] as Map<String, Any?>, m["full"] as? Boolean ?: false)
            "question" -> ServerMsg.Question(seq, s("questionId")!!, l("index")!!.toInt(), l("count")!!.toInt(), s("text")!!, (m["choices"] as List<*>).map { it.toString() },
                l("opensAtServerMs")!!, l("serverNowMs")!!, l("windowMs")!!)
            "reveal" -> ServerMsg.Reveal(seq, s("questionId")!!, l("index")!!.toInt(), l("answer")!!.toInt(), s("explanation"))
            "safety" -> ServerMsg.Safety(seq, SafetyView(PlayScope.valueOf(s("scope")!!), SignalLevel.valueOf(s("level")!!), s("word")!!, s("text")!!, s("action"),
                (m["detail"] as? List<*>)?.map { it.toString() } ?: emptyList()))
            "ping" -> ServerMsg.Ping(seq, s("id")!!, l("serverNowMs") ?: 0L)
            "error" -> ServerMsg.Error(seq, s("reason")!!, s("message") ?: "", m["retryable"] as? Boolean ?: false, l("retryAfterMs")?.coerceIn(0L, 3_600_000L) ?: 0L)
            "roomGone" -> ServerMsg.RoomGone(seq, s("reason") ?: "")
            "replay" -> ServerMsg.Replay(seq, (m["events"] as List<Map<String, Any?>>).map { EventRing.Event((it["seq"] as Number).toLong(), it["kind"] as String, (it["data"] as? Map<String, Any?>) ?: emptyMap()) })
            "ack" -> ServerMsg.Ack(seq, l("ref") ?: 0L, s("result")!!)
            else -> null
        }
    } catch (e: Exception) { null }
}
