package castbridge.core.wallet

import castbridge.core.owner.Base32C

/**
 * Les trois encodages TEXTE d'un [Voucher] (le même texte partout) : code long tapé à la main, fichier `.cbv1` (Download/castbridge-bons/ de la clé USB), contenu de QR.
 *
 * Les 91 octets en Crockford Base32 (146 caractères), en groupes de 4 caractères suivis d'1 caractère de contrôle (le dernier groupe n'en a que 2 + 1) : 37 groupes, 183 caractères,
 * séparés par des tirets. Le contrôle dépend du rang du groupe (même règle que `GroupedText` : toute faute d'un caractère est détectée et localisée au groupe). O lit 0, I et L lisent 1,
 * minuscules et espaces tolérés. Lecture STRICTE : longueur exacte, bits de bourrage nuls, texte canonique.
 */
object VoucherCode {
    const val GROUPS = 37
    const val CHARS = 183
    private const val DATA_CHARS = 146

    sealed class Decoded {
        data class Ok(val voucher: Voucher) : Decoded()
        /** Faute de frappe dans le groupe [group] (1..37). */
        data class BadGroup(val group: Int) : Decoded() { val message: String get() = "Faute de frappe dans le groupe $group" }
        object Malformed : Decoded()
    }

    fun encode(voucher: Voucher): String {
        val data = Base32C.encode(voucher.encode())
        check(data.length == DATA_CHARS)
        return data.chunked(4).mapIndexed { i, g -> g + Base32C.check(g, salt = i + 1) }.joinToString("-")
    }

    fun decode(text: String): Decoded {
        val chars = text.filter { it != '-' && !it.isWhitespace() }
        if (chars.length != CHARS) return Decoded.Malformed
        val data = StringBuilder()
        var i = 0; var rank = 1
        while (i < chars.length) {
            val len = minOf(5, chars.length - i)
            val g = chars.substring(i, i + len).map { c -> val v = Base32C.value(c); if (v < 0) return Decoded.BadGroup(rank) else Base32C.ALPHABET[v] }.joinToString("")
            if (g.length < 2 || Base32C.check(g.dropLast(1), salt = rank) != g.last()) return Decoded.BadGroup(rank)
            data.append(g, 0, g.length - 1)
            i += len; rank++
        }
        val bytes = Base32C.decode(data.toString(), Voucher.SIZE) ?: return Decoded.Malformed
        if (Base32C.encode(bytes) != data.toString()) return Decoded.Malformed       // bits de bourrage non nuls : pas canonique
        val v = Voucher.decode(bytes) ?: return Decoded.Malformed
        return Decoded.Ok(v)
    }

    /** Contenu d'un fichier `.cbv1` : le même texte, une ligne, fin de ligne finale. */
    fun encodeFile(voucher: Voucher): String = encode(voucher) + "\n"

    /** Lit un fichier `.cbv1` (≤ 1 Ko, une seule ligne de texte, BOM toléré). */
    fun decodeFile(content: String): Decoded {
        val t = content.removePrefix("﻿").trim()
        if (t.length > 1_024 || t.contains('\n')) return Decoded.Malformed
        return decode(t)
    }

    /** Contenu d'un QR : le même texte. */
    fun qrContent(voucher: Voucher): String = encode(voucher)
}
