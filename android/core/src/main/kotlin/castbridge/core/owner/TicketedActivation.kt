package castbridge.core.owner

/**
 * The « activation avec ticket »: ONE line `<mandat cbx1…>|<activation cbx1…>` that carries the delegation of the field agent next to the activation the agent signed. `|` appears in no Base64,
 * so the line fits `activations.txt`, the USB file and the `ACTIVATION` frame. An old TV cannot read it (`Envelope.decode` fails: MALFORMED), which is the intended message.
 */
object TicketedActivation {
    fun encode(delegationToken: String, activationToken: String): String = "${delegationToken.trim()}|${activationToken.trim()}"

    /** (delegation, activation) of [line]: exactly one `|` and two `cbx1.` tokens; otherwise null. */
    fun split(line: String): Pair<String, String>? {
        val parts = line.trim().split('|')
        if (parts.size != 2) return null
        val (d, a) = parts
        return if (d.startsWith(Envelope.PREFIX + ".") && a.startsWith(Envelope.PREFIX + ".")) d to a else null
    }

    /** True when [text] looks like a ticketed line (an envelope followed by `|`); it may still be malformed ([split] returns null). */
    fun isTicketed(text: String): Boolean = text.trim().let { it.startsWith(Envelope.PREFIX + ".") && '|' in it }
}
