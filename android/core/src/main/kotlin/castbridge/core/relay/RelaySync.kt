package castbridge.core.relay

import castbridge.core.trust.SavedTv
import castbridge.core.trust.TvAuth

/**
 * « Ce téléphone est synchronisé avec cette TV » pour la politique du relais ([RelayInput.synced]) : la synchronisation EST le consentement (décision du propriétaire, DESIGN-RELAIS § 4).
 *
 * R-30 (audit anti-régression 2026-10-07 b, B2) : la règle d'avant ne regardait que le jeton VIVANT ou un code gardé. Un téléphone réveillé après 12 h par « appareil connecté » a un jeton
 * expiré (c'est le cas normal : l'application était fermée) : il disait « non synchronisé » à une TV avec laquelle il est pourtant appairé (« Ajouter ma TV »), et la TV l'écartait une heure.
 * Une TV ENREGISTRÉE dans le téléphone suffit donc : la TV revérifie elle-même la confiance (l'adresse Bluetooth du téléphone) à la poignée de main de la passerelle. Une TV qui n'est
 * pas enregistrée (oubliée entre le réveil et la décision) n'est synchronisée que par un identifiant utilisable (un code de six chiffres gardé, un jeton vivant).
 */
object RelaySync {
    /** [saved] : la TV telle que le registre du téléphone la garde MAINTENANT (null = inconnue ou oubliée) ; [credential] : ce que `PinStore.get(PinKeys.btKey(adresse))` a rendu ("" = rien). */
    fun synced(saved: SavedTv?, credential: String?): Boolean = saved != null || TvAuth.isUsable(credential)
}
