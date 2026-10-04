package castbridge.core.wallet.ui

import java.security.SecureRandom

/** Clé d'idempotence d'une opération (convertir, envoyer) : au plus 64 caractères parmi A-Z a-z 0-9 . _ : - (motif du serveur), jamais deux fois la même. Générée à la confirmation, rejouée telle quelle sur une coupure. */
object WalletIdem {
    fun newKey(random: SecureRandom = SecureRandom()): String = "tv-" + ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}
