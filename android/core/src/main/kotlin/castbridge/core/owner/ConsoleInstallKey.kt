package castbridge.core.owner

/**
 * The one sentence of the owner phone's « Activer » screen when a TRIAL key cannot be issued because the device request carries no `install=` line (the installation's PUBLIC X25519 key: the rented
 * lots of a trial are boxed for it, v2). Since 2026-10-07 (ACT-F4 amended) EVERY request read from a recent TV carries it, whether read by the connection code (route
 * `GET /api/activation/device-request`) or by Bluetooth (the same `requestText()`): a request without it means a key not ready yet (the TV's key store is still preparing it) or an older TV, so the
 * screen opened by « Activer la TV » ([readByCode]) never sends the owner round by Bluetooth (it would give the same text) nor by the weak v1 envelope. A request pasted by hand keeps its switch
 * « Enveloppe v1 (TV ancienne) », accepted until 2027-01-01.
 */
object ConsoleTrialBox {
    fun noKeyMessage(readByCode: Boolean): String =
        if (readByCode) "Cette TV n'a pas encore fourni sa clé d'installation (son coffre de clés n'est pas prêt, ou CastBridge-TV est trop ancienne) : relisez sa demande dans quelques secondes (« Activer la TV », saisissez à nouveau le code) ou mettez CastBridge-TV à jour."
        else "Cette TV n'a pas fourni sa clé d'installation (CastBridge-TV trop ancien) : mettez-la à jour, ou activez « Enveloppe v1 (TV ancienne) »"
}

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
