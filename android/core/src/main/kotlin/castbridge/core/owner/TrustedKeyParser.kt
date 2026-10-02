package castbridge.core.owner

/**
 * Reads the trusted-key lines the TV is built with (« kid=… pub=… scopes=A,B »). FAIL CLOSED: a line without `scopes=` grants NO scope (a mistyped line must never give a key
 * every power, SUPER_UNLIMITED included), unknown scope names are ignored. [Parsed.warnings] says what was dropped (French, for the log and the build).
 */
object TrustedKeyParser {
    class Parsed(val keys: List<TrustedKey>, val warnings: List<String>)

    fun parse(text: String): Parsed {
        val keys = ArrayList<TrustedKey>(); val warnings = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim(); if (line.isEmpty() || line.startsWith("#")) continue
            val kv = line.split(' ', '\t').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val kid = kv["kid"]?.takeIf { it.isNotEmpty() }; val pub = kv["pub"]?.takeIf { it.isNotEmpty() }
            if (kid == null || pub == null) { warnings += "ligne de clé ignorée (kid= ou pub= manquant)"; continue }
            val names = kv["scopes"]
            if (names == null) warnings += "clé « $kid » sans scopes= : AUCUNE portée accordée"
            val scopes = names?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.mapNotNull { n ->
                KeyScope.values().firstOrNull { it.name == n }.also { if (it == null) warnings += "clé « $kid » : portée inconnue « $n » ignorée" }
            }?.toSet() ?: emptySet()
            keys += TrustedKey(kid, pub, scopes)
        }
        return Parsed(keys, warnings)
    }

    fun parseLine(line: String): TrustedKey? = parse(line).keys.singleOrNull()
}
