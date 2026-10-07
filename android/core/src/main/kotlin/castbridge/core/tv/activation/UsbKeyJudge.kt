package castbridge.core.tv.activation

import castbridge.core.owner.Activation
import castbridge.core.owner.Envelope
import castbridge.core.owner.GroupedText
import java.security.MessageDigest

/**
 * What the TV says about a key it finds on a USB drive when that key is ALREADY installed (R-44, audit anti-régression 2026-10-07 b, I-16).
 *
 * The lookup at plug-in only VERIFIED the key (signature, this TV, install window), and the verifier knew nothing of what was installed. A trial key left on the drive (the desktop tool
 * writes three copies and the key stays plugged) came back « activation trouvée › Passer en production » at every plug-in for 48 hours, then « clé périmée : demandez-en une nouvelle »
 * to a customer who was perfectly well activated (the 48 h are only the window to INSTALL a key; once installed it stays). Now a key whose text, or whose signature, is already installed
 * is [Verdict.INSTALLED]: nothing to announce.
 *
 * The memory is the installed keys themselves (`activations.txt`): their texts are compared by FINGERPRINT (SHA-256 of the text without spaces) and, for a full `cbx1` token, by SIGNATURE
 * (the same activation written differently). A different key (another seat, a renewal, another TV's) has another fingerprint and another signature: it is judged as before.
 */
object UsbKeyJudge {
    /** SHA-256 (hex) of the key's text without any space or line break: what the memory of installed keys holds, and what a file is compared with. Never shown, never logged. */
    fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.filterNot { it.isWhitespace() }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /**
     * The signature of a full activation (what identifies one activation): from the `cbx1` token itself or from its grouped text (groups of 5, [GroupedText]); null for any other form
     * (compact key, junk). Reads the text, verifies nothing.
     */
    fun signatureOf(text: String): String? {
        val t = text.trim()
        val token = if (t.startsWith(Envelope.PREFIX + ".")) t else (GroupedText.decode(t) as? GroupedText.Decoded.Ok)?.let { String(it.bytes, Charsets.US_ASCII) } ?: return null
        return Activation.decode(token)?.signature?.takeIf { it.isNotEmpty() }
    }

    /** Is the key whose first line is [line] the one already installed: its text ([installedFingerprints] of the installed texts) or its activation ([installedSignatures]) is known. */
    fun installed(line: String, installedFingerprints: Set<String>, installedSignatures: Set<String>): Boolean =
        fingerprint(line) in installedFingerprints || signatureOf(line)?.let { it in installedSignatures } == true

    /**
     * The verdict of the file whose first line is [line]: [verdict] (the verifier's) unless the key is already installed ([installed]), then [Verdict.INSTALLED].
     */
    fun verdict(line: String, verdict: Verdict, installedFingerprints: Set<String>, installedSignatures: Set<String>): Verdict =
        if (installed(line, installedFingerprints, installedSignatures)) Verdict.INSTALLED else verdict
}
