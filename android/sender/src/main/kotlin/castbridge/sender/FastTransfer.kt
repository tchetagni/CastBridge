package castbridge.sender

import android.content.Context

/** Réglage « Transfert rapide (plusieurs voies) » : activé par défaut, utilisé seulement si la TV le sait (sinon l'envoi classique). Voir docs/TRANSFER.md. */
object FastTransfer {
    private const val FILE = "castbridge_transfer"
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("fast", true)
    fun set(ctx: Context, on: Boolean) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("fast", on).apply() }
}
