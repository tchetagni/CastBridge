package castbridge.play.entitlement

import castbridge.core.owner.KeyRing
import castbridge.core.owner.KeyScope
import castbridge.core.owner.TrustedKey
import java.util.Base64

/**
 * Les clés PUBLIQUES des émetteurs de confiance (`CASTBRIDGE_PLAY_TRUSTED_KEYS`, même format que `CASTBRIDGE_LICENSES_TRUSTED_KEYS`) :
 * `desktop:<clé publique Ed25519 brute en Base64>:ISSUE_TRIAL+ISSUE_PRODUCTION+REVOKE,phone:<Base64>:…`. FERMÉ : une entrée sans portées, avec une clé illisible
 * ou dont toutes les portées sont inconnues est IGNORÉE (une ligne mal tapée ne donne jamais tous les pouvoirs). Aucune clé privée ici.
 */
object TrustedIssuers {
    class Parsed(val ring: KeyRing, val warnings: List<String>)

    fun parse(text: String?): Parsed {
        val keys = ArrayList<TrustedKey>(); val warnings = ArrayList<String>()
        for (raw in text.orEmpty().split(',')) {
            val entry = raw.trim(); if (entry.isEmpty()) continue
            val f = entry.split(':')
            if (f.size < 3) { warnings += "clé de confiance ignorée : aucune portée (format nom:clé:PORTÉES)"; continue }
            val pub = f[1].trim()
            val rawKey = runCatching { Base64.getDecoder().decode(pub) }.getOrNull()
            if (rawKey == null || rawKey.size != 32) { warnings += "clé de confiance « ${f[0]} » ignorée : clé publique illisible (32 octets en Base64 attendus)"; continue }
            val scopes = f[2].split('+').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { n -> KeyScope.values().firstOrNull { it.name == n } }.toSet()
            if (scopes.isEmpty()) { warnings += "clé de confiance « ${f[0]} » ignorée : aucune portée connue"; continue }
            keys += TrustedKey(KeyRing.idOf(pub), pub, scopes)
        }
        return Parsed(KeyRing(keys), warnings)
    }
}
