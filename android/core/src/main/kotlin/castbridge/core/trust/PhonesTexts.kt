package castbridge.core.trust

/**
 * Les mots de l'écran « Téléphones synchronisés » de CastBridge-TV et des messages de la TV quand elle a déjà 8 téléphones
 * (règle du propriétaire 2026-10-04). Un seul endroit, comme `castbridge.core.ux.UiTexts` ; les écrans ne font que les afficher.
 */
object PhonesTexts {
    private val MAX = TrustRegistry.MAX_PHONES
    const val TITLE = "Téléphones synchronisés"
    const val EMPTY = "Aucun téléphone synchronisé pour l'instant. Ouvrez « Ajouter un téléphone » pour en ajouter un."
    const val HINT = "Ils pilotent la TV sans code. « Retirer » leur enlève l'accès tout de suite."
    const val SUGGESTION = "Suggestion : le moins récemment vu"
    const val REMOVE = "Retirer"
    const val CANCEL = "Annuler l'ajout"
    const val CLOSE = "Fermer"
    const val ADD = "Ajouter un téléphone"
    const val CONFIRM_REMOVE_TEXT = "Ce téléphone perd l'accès tout de suite. Pour revenir, il faudra saisir le code de la TV ou être approuvé de nouveau sur cette TV."

    const val WRITE_FAILED = "La liste des téléphones n'a pas pu être enregistrée : rien n'a changé. Réessayez."
    const val NOT_PENDING = "La demande d'ajout n'est plus en attente : rien n'a changé."
    const val ALREADY_GONE = "Ce téléphone n'est plus dans la liste."

    fun counter(count: Int, max: Int = MAX) = "$count / $max"
    fun menuEntry(count: Int, max: Int = MAX) = "$TITLE (${counter(count, max)})…"
    fun replaceTitle(newName: String) = "Cette TV a déjà $MAX téléphones : choisissez celui à retirer pour ajouter $newName"
    fun confirmRemoveTitle(name: String) = "Retirer $name ?"
    fun confirmReplaceText(removed: String, added: String) = "$removed sera retiré et $added sera ajouté. $CONFIRM_REMOVE_TEXT"
    fun removed(name: String) = "$name a été retiré."

    /** Ce que la TV dit à son propriétaire (bandeau, état), jamais en silence : même raison que celle donnée au téléphone. */
    fun tvMessage(e: PairCapacityFlow.Event): String = when (e.kind) {
        PairCapacityFlow.Kind.REQUESTED -> replaceTitle(e.name)
        PairCapacityFlow.Kind.REPLACED -> "${e.removedName ?: "Un téléphone"} a été retiré, ${e.name} est ajouté."
        PairCapacityFlow.Kind.CANCELLED -> "L'ajout de ${e.name} est annulé : aucun téléphone n'a été retiré."
        PairCapacityFlow.Kind.TIMED_OUT -> "L'ajout de ${e.name} est annulé : personne n'a choisi de téléphone à retirer à temps."
    }
}
