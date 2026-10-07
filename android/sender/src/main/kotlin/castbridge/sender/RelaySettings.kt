package castbridge.sender

import android.content.Context
import castbridge.core.connect.KeyValueStore
import castbridge.core.relay.PipeNeed
import castbridge.core.trust.TrustRegistry

/**
 * Les réglages et l'état persistant du relais sur le téléphone (relay-R1, DESIGN-RELAIS § 4 décisions 1 et 2) : dans les préférences privées `castbridge_relay`, exclues des sauvegardes.
 * - « Données mobiles pour la TV » ([allowMobile], éteint par défaut) : lève le plafond du jour et l'interdiction du gros volume sur réseau facturé ;
 * - « Ne plus relayer pour cette TV » ([optedOut], éteint par défaut, par TV) : la porte de sortie du propriétaire (pas d'écran de consentement : la synchronisation par PIN EST le consentement) ;
 * - la mise en sommeil après un « Arrêter » de la notification ([snooze]) ;
 * - le compteur d'octets du jour ([meterStore], voir [castbridge.core.relay.RelayMeter]) et la dernière session (reprise après une mort du service).
 * Aucun nom de TV, aucune adresse complète n'est jamais écrit dans un journal.
 */
class RelaySettings(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_relay", Context.MODE_PRIVATE)

    var allowMobile: Boolean
        get() = sp.getBoolean("allow_mobile", false)
        set(v) { sp.edit().putBoolean("allow_mobile", v).apply() }

    fun optedOut(address: String): Boolean = sp.getBoolean("optout." + TrustRegistry.norm(address), false)
    fun setOptedOut(address: String, on: Boolean) { sp.edit().apply { if (on) putBoolean("optout." + TrustRegistry.norm(address), true) else remove("optout." + TrustRegistry.norm(address)) }.apply() }

    fun snoozedUntil(address: String): Long = sp.getLong("snooze." + TrustRegistry.norm(address), 0L)
    fun snooze(address: String, untilMs: Long) { sp.edit().putLong("snooze." + TrustRegistry.norm(address), untilMs).apply() }

    /** Le compteur du jour de [castbridge.core.relay.RelayMeter] : deux clés de texte, écrites sur place. */
    val meterStore: KeyValueStore = object : KeyValueStore {
        override fun get(key: String): String? = sp.getString("meter.$key", null)
        override fun put(key: String, value: String?) { sp.edit().apply { if (value == null) remove("meter.$key") else putString("meter.$key", value) }.commit() }
    }

    /** La session en cours d'un tuyau automatique, pour que le système, s'il tue puis relance le service, le reprenne (START_STICKY) tant que l'activité est récente. */
    class Session(val address: String, val name: String, val limitBytes: Long?, val metered: Boolean, val lastActivityAt: Long, val need: PipeNeed? = null)

    fun saveSession(s: Session) {
        sp.edit().putString("session.address", s.address).putString("session.name", s.name).putLong("session.limit", s.limitBytes ?: -1L)
            .putBoolean("session.metered", s.metered).putLong("session.activity", s.lastActivityAt).apply { if (s.need == null) remove("session.need") else putString("session.need", s.need.wire) }.commit()
    }

    fun loadSession(): Session? {
        val a = sp.getString("session.address", null) ?: return null
        return Session(a, sp.getString("session.name", null) ?: "la TV", sp.getLong("session.limit", -1L).takeIf { it >= 0 }, sp.getBoolean("session.metered", false), sp.getLong("session.activity", 0L),
            PipeNeed.of(sp.getString("session.need", null)))
    }

    fun clearSession() { sp.edit().remove("session.address").remove("session.name").remove("session.limit").remove("session.metered").remove("session.activity").remove("session.need").commit() }
}
