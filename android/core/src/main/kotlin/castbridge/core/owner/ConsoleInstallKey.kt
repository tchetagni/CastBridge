package castbridge.core.owner

/**
 * What the owner phone's « Activer » screen says about the TV's installation key (`install_sig=`), before and after it signs a PRODUCTION activation (w23-05, second audit MEDIUM-C).
 * The device request is not signed: the owner must compare [ActivationBinding.fingerprint] of the key with the TV's activation screen BEFORE handing the activation over. Pure: no Android.
 */
object ConsoleInstallKey {
    /** [text] to show; [warning] = no key to bind (the activation will wait for the owner's decision on the server). */
    class Notice(val text: String, val warning: Boolean)

    /** Shown as soon as a complete device request is pasted or read: null for a trial key (it carries no `ik`) or a bare device code. */
    fun before(info: OwnerFrames.DeviceInfo?, production: Boolean): Notice? {
        if (info == null || !production) return null
        val key = info.installSig
            ?: return Notice("Cette TV n'envoie pas sa clé de signature d'installation (CastBridge-TV trop ancien) : l'activation sera émise sans clé liée ; le serveur attendra votre décision. Mettez la TV à jour (0.14.37 ou plus) pour une activation automatique.", true)
        return Notice("Clé d'installation de la TV : empreinte ${ActivationBinding.fingerprint(key)}. COMPAREZ-LA avec celle de l'écran d'activation de la TV avant de remettre l'activation.", false)
    }

    /** Shown with the issued activation: the fingerprint of the key actually signed in it (read back from the signed token), or null when it carries none. */
    fun after(issued: ActivationIssuer.Issued): String? =
        ActivationBinding.installKeyOf(issued.activation)?.let { "Activation liée à la clé d'installation d'empreinte ${ActivationBinding.fingerprint(it)} : à comparer avec l'écran de la TV avant de la remettre." }
}
