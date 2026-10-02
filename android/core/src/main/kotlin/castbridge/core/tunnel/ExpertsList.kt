package castbridge.core.tunnel

import castbridge.core.net.JsonLite
import castbridge.core.owner.KeyScope
import castbridge.core.owner.Signer
import castbridge.core.owner.TrustedKey
import java.util.Base64

/**
 * The list of the experts allowed to administer CastBridge-TV remotely (docs/REMOTE-TUNNEL.md). Signed OFFLINE by the owner with the key that holds
 * [KeyScope.REGISTRY] (the desk key), published by the server (`/api/v1/tunnel/experts`), verified by every TV (to install the authorized keys)
 * and by the server (to let the experts through its sshd). Removing an expert = signing a new list without him.
 *
 * File: `{"generatedAt":<ms>,"keyId":"<kid>","experts":[{"id":"alice","publicKey":"ssh-ed25519 AAAA… comment","notAfter":<ms, 0 = none>}],"signature":"<base64>"}`.
 * Signed text (UTF-8): `castbridge-experts-v1\ngeneratedAt=<ms>\nexpert=<id>|<publicKey>|<notAfter>\n…`, experts sorted by id, no trailing newline.
 */
data class ExpertsList(val generatedAt: Long, val keyId: String, val experts: List<Expert>, val signature: String = "") {

    /** One expert. The constructor refuses anything the signed text or the sshd could misread (French message). */
    data class Expert(val id: String, val publicKey: String, val notAfter: Long = 0L) {
        init {
            if (!ID.matches(id)) throw Refused("Identifiant d'expert invalide « $id » : 1 à 32 caractères parmi a-z, 0-9 et « - », commençant par une lettre ou un chiffre")
            if (notAfter < 0) throw Refused("Date de fin invalide pour l'expert $id")
            checkKey(id, publicKey)
        }

        /** The base64 blob of the key (the second word of [publicKey]). */
        val keyBase64: String get() = publicKey.split(' ')[1]

        fun expiredAt(now: Long) = notAfter != 0L && notAfter <= now
    }

    /** Refused list or expert; the message is French and meant for the screen. */
    class Refused(message: String) : IllegalArgumentException(message)

    /** The text that is signed. */
    fun signedText(): String = signedText(generatedAt, experts)

    fun toJson(): String = JsonLite.write(linkedMapOf(
        "generatedAt" to generatedAt, "keyId" to keyId,
        "experts" to experts.sortedBy { it.id }.map { linkedMapOf("id" to it.id, "publicKey" to it.publicKey, "notAfter" to it.notAfter) },
        "signature" to signature))

    /** One OpenSSH authorized_keys line per expert that has not expired at [now]: `restrict,pty` = a terminal and nothing else (no port, agent or X11 forwarding). Comment = the id. */
    fun authorizedKeysLines(now: Long): List<String> =
        experts.sortedBy { it.id }.filter { !it.expiredAt(now) }.map { "restrict,pty ssh-ed25519 ${it.keyBase64} ${it.id}" }

    /** The same list without [id] (unsigned: sign it again). */
    fun without(id: String) = copy(experts = experts.filter { it.id != id }, signature = "")

    class Verified(val list: ExpertsList)

    companion object {
        const val FORMAT = "castbridge-experts-v1"
        const val MAX_EXPERTS = 50
        val ID = Regex("^[a-z0-9][a-z0-9-]{0,31}$")
        private const val KEY_TYPE = "ssh-ed25519"

        fun signedText(generatedAt: Long, experts: List<Expert>): String = buildList {
            add(FORMAT); add("generatedAt=$generatedAt")
            experts.sortedBy { it.id }.forEach { add("expert=${it.id}|${it.publicKey}|${it.notAfter}") }
        }.joinToString("\n")

        /** Checks one public key line: `ssh-ed25519 <base64> [comment]`, a 32-byte ed25519 key blob (rsa, ecdsa, dss refused). Single spaces, no `|`, no control character. */
        fun checkKey(id: String, line: String) {
            if (line.any { it.isISOControl() } || '|' in line) throw Refused("Clé SSH de l'expert $id : une seule ligne, sans caractère de contrôle ni « | »")
            if (line != line.trim() || "  " in line) throw Refused("Clé SSH de l'expert $id : espaces en trop")
            val w = line.split(' ')
            if (w.size < 2) throw Refused("Clé SSH de l'expert $id : « ssh-ed25519 AAAA… commentaire » attendu")
            if (w[0] != KEY_TYPE) throw Refused("Clé SSH de l'expert $id : seul ssh-ed25519 est accepté (« ${w[0].take(24)} » refusé : ni rsa, ni ecdsa, ni dss)")
            val blob = try { Base64.getDecoder().decode(w[1]) } catch (e: IllegalArgumentException) { throw Refused("Clé SSH de l'expert $id : base64 invalide") }
            if (Base64.getEncoder().encodeToString(blob) != w[1]) throw Refused("Clé SSH de l'expert $id : base64 non canonique")
            val t = KEY_TYPE.toByteArray(Charsets.US_ASCII)
            val ok = blob.size == 4 + t.size + 4 + 32 && u32(blob, 0) == t.size && String(blob, 4, t.size, Charsets.US_ASCII) == KEY_TYPE && u32(blob, 4 + t.size) == 32
            if (!ok) throw Refused("Clé SSH de l'expert $id : ce n'est pas une clé ed25519 de 32 octets")
        }

        private fun u32(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)

        private fun check(experts: List<Expert>) {
            if (experts.size > MAX_EXPERTS) throw Refused("Trop d'experts (${experts.size}) : $MAX_EXPERTS au plus")
            experts.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }?.let { throw Refused("Expert en double : ${it.key}") }
            experts.groupBy { it.keyBase64 }.entries.firstOrNull { it.value.size > 1 }?.let { throw Refused("La même clé SSH est donnée à deux experts : ${it.value.joinToString { e -> e.id }}") }
        }

        /** Parses a file (signed or not) and validates every expert. */
        fun parse(json: String): ExpertsList {
            val m = try { JsonLite.obj(json) } catch (e: Exception) { throw Refused("Liste d'experts illisible : ${e.message}") }
            val at = (m["generatedAt"] as? Number)?.toLong()?.takeIf { it > 0 } ?: throw Refused("Liste d'experts sans date de génération valide")
            val kid = m["keyId"] as? String ?: ""
            val rows = m["experts"] as? List<*> ?: throw Refused("Liste d'experts sans tableau « experts »")
            val experts = rows.map { r ->
                val o = r as? Map<*, *> ?: throw Refused("Expert illisible")
                val na = o["notAfter"]; if (na != null && na !is Number) throw Refused("Date de fin illisible")
                Expert(o["id"] as? String ?: throw Refused("Expert sans identifiant"), o["publicKey"] as? String ?: throw Refused("Expert sans clé SSH"), (na as? Number)?.toLong() ?: 0L)
            }
            check(experts)
            return ExpertsList(at, kid, experts, m["signature"] as? String ?: "")
        }

        /**
         * Parses and verifies [json]: signed by one of [trustedKeys] that holds [KeyScope.REGISTRY], not older than [notOlderThan] (the generatedAt of the list already kept; 0 = no check).
         * Experts that have expired are NOT an error here (they simply produce no authorized key, see [authorizedKeysLines]).
         */
        fun verify(json: String, trustedKeys: List<TrustedKey>, notOlderThan: Long = 0L): Verified {
            val l = parse(json)
            val sig = l.signature.takeIf { it.isNotBlank() && it != "UNSIGNED" } ?: throw Refused("Liste d'experts non signée : refusée")
            val key = trustedKeys.firstOrNull { it.keyId == l.keyId }
                ?: throw Refused("Liste d'experts signée par une clé inconnue (${l.keyId.ifBlank { "sans identifiant" }}) : refusée")
            if (!key.allows(KeyScope.REGISTRY)) throw Refused("La clé ${key.keyId} n'a pas la portée REGISTRY : liste d'experts refusée")
            if (!key.verify(l.signedText(), sig)) throw Refused("Signature de la liste d'experts invalide (fichier modifié ou signé avec une autre clé) : refusée")
            if (notOlderThan > 0 && l.generatedAt < notOlderThan) throw Refused("Liste d'experts plus ancienne (${l.generatedAt}) que celle déjà enregistrée ($notOlderThan) : refusée (rejeu d'un ancien fichier)")
            return Verified(l)
        }

        /** Signs the list (desk tool). The caller checks that [signer]'s key holds REGISTRY; [verify] refuses the result otherwise. */
        fun sign(signer: Signer, keyId: String, experts: List<Expert>, now: Long): ExpertsList {
            check(experts)
            val unsigned = ExpertsList(now, keyId, experts.sortedBy { it.id })
            return unsigned.copy(signature = Base64.getEncoder().encodeToString(signer.sign(unsigned.signedText().toByteArray(Charsets.UTF_8))))
        }
    }
}
