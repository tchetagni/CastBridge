package castbridge.core.link

/**
 * Les trois récepteurs du manifeste du téléphone que le Bluetooth d'Android réveille, même CastBridge fermé (R-31, audit anti-régression 2026-10-07 b, I-9) :
 *  - [Receiver.RELAY] (`RelayWakeReceiver`) : la TV qui veut un tuyau Internet frappe à la porte du téléphone (« appareil connecté »), qui vient lire sa demande ;
 *  - [Receiver.LINK] (`LinkWakeReceiver`) : la liaison de confiance reprend en arrière-plan (un travail court) ;
 *  - [Receiver.LOTS] (`LotsTriggerReceiver`) : la livraison des lots à la TV et le réveil des travaux planifiés au démarrage.
 *
 * Ils étaient `exported="false"`. Or « appareil Bluetooth connecté » (`ACL_CONNECTED`) est diffusé par l'application Bluetooth d'Android (uid 1002, ni root ni système) : le système refuse
 * une diffusion qui vient d'un autre uid vers un récepteur non exporté (« is not exported from uid »), donc ils ne se réveillaient jamais sur ce signal. Ils sont exportés ; c'est sans
 * danger à deux conditions, que ce code et ses tests tiennent : (1) toutes les actions écoutées sont des diffusions PROTÉGÉES ([PROTECTED] : aucune application ne peut les envoyer, le
 * système lève une SecurityException) ; (2) chaque `onReceive` commence par [accepts] : une intention explicite d'une autre application, avec une autre action, ne fait rien.
 */
object BluetoothWake {
    const val ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED"
    const val BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED"
    const val ADAPTER_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED"
    const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"

    /** Les diffusions que seul le système peut envoyer. Un récepteur exporté ne peut écouter que celles-ci ([Receiver.mayBeExported]). */
    val PROTECTED: Set<String> = setOf(ACL_CONNECTED, BOND_STATE_CHANGED, ADAPTER_STATE_CHANGED, BOOT_COMPLETED)

    /** Les récepteurs et les actions que chacun traite ; le manifeste déclare exactement les mêmes (`BluetoothWakeManifestTest`). */
    enum class Receiver(val actions: Set<String>) {
        RELAY(setOf(ACL_CONNECTED)),
        LINK(setOf(ACL_CONNECTED, BOND_STATE_CHANGED, ADAPTER_STATE_CHANGED)),
        LOTS(setOf(ACL_CONNECTED, BOOT_COMPLETED));

        /** Vrai quand tout ce que le récepteur écoute est protégé : alors seulement il peut être exporté. */
        val mayBeExported: Boolean get() = PROTECTED.containsAll(actions)
    }

    /** L'action de l'intention reçue est-elle une de celles que ce récepteur traite ? (null, vide, inconnue ou forgée : non.) */
    fun accepts(r: Receiver, action: String?): Boolean = action != null && action in r.actions
}
