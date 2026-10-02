package castbridge.desktop

import castbridge.core.net.JsonLite
import castbridge.core.tunnel.ExpertsList
import castbridge.core.tunnel.ExpertsList.Expert
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The desk's local list of the remote-assistance experts (`<dossier>/experts.json.src`, public keys only, UNSIGNED) maintained by « experts-ajouter » / « experts-retirer »;
 * « experts-signer » turns it into the signed experts.json (docs/ACTIVATION-TOOLS.md § Experts de l'assistance à distance). The journal gets one line per change, never key material.
 */
class ExpertsStore(private val home: File, private val clock: () -> Long = System::currentTimeMillis) {
    val file = File(home, NAME)

    fun list(): List<Expert> = if (!file.isFile) emptyList() else (JsonLite.obj(file.readText())["experts"] as? List<*> ?: emptyList<Any>()).map { r ->
        val o = r as Map<*, *>; Expert(o["id"] as String, o["publicKey"] as String, (o["notAfter"] as? Number)?.toLong() ?: 0L)
    }

    private fun save(l: List<Expert>) {
        home.mkdirs()
        val tmp = File(home, "$NAME.tmp")
        tmp.writeText(JsonLite.write(linkedMapOf("experts" to l.sortedBy { it.id }.map { linkedMapOf("id" to it.id, "publicKey" to it.publicKey, "notAfter" to it.notAfter) })) + "\n")
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    fun add(id: String, rawKey: String, untilDate: String?, kid: String): Expert {
        val e = Expert(id, rawKey.trim().split(Regex("\\s+")).joinToString(" "), untilDate?.let { endOfDay(it) } ?: 0L)
        val cur = list()
        if (cur.any { it.id == id }) throw ExpertsList.Refused("L'expert $id existe déjà (« experts-retirer $id » d'abord pour changer sa clé)")
        if (cur.any { it.keyBase64 == e.keyBase64 }) throw ExpertsList.Refused("Cette clé SSH est déjà donnée à l'expert ${cur.first { it.keyBase64 == e.keyBase64 }.id}")
        if (cur.size >= ExpertsList.MAX_EXPERTS) throw ExpertsList.Refused("Déjà ${ExpertsList.MAX_EXPERTS} experts : le maximum")
        save(cur + e); log("experts-ajouter", id, kid); return e
    }

    fun remove(id: String, kid: String) {
        val cur = list()
        if (cur.none { it.id == id }) throw ExpertsList.Refused("Aucun expert « $id » (« experts-lister »)")
        save(cur.filter { it.id != id }); log("experts-retirer", id, kid)
    }

    fun logSigned(kid: String, count: Int) = log("experts-signer", "$count expert(s)", kid)

    private fun log(kind: String, subject: String, kid: String) {
        home.mkdirs()
        File(home, "journal.jsonl").appendText(JsonLite.write(linkedMapOf("at" to clock(), "kid" to kid, "kind" to kind, "subject" to subject, "device" to "-", "license" to "-", "seat" to "-",
            "notBefore" to 0L, "notAfter" to 0L, "rights" to emptyList<String>())) + "\n")
    }

    companion object {
        const val NAME = "experts.json.src"
        /** « AAAA-MM-JJ » = valid through that whole day (UTC): the key stops at the start of the next day. */
        fun endOfDay(date: String): Long = try { LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            catch (e: java.time.format.DateTimeParseException) { throw ExpertsList.Refused("Date invalide « $date » : AAAA-MM-JJ attendu") }
    }
}
