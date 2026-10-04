package castbridge.core.ux

import castbridge.core.trust.Advice
import castbridge.core.trust.PairStep

/**
 * R-18 : UNE ligne d'état de « Ajouter ma TV » pour l'écran ET la notification (même texte, mêmes couleurs : [SignalLevel]), y compris
 * « Cette TV a déjà 8 téléphones » (en attente de la TV : orange, borné ; réussite : vert ; refus, annulation, délai, retrait : rouge).
 * Un texte = « titre : détail » du [PairStep.advice], jamais un nom d'exception ni de code brut.
 */
object PairStatusLine {
    fun of(step: PairStep): StatusLine = StatusLine(text(step.advice), when (step) {
        is PairStep.Done -> SignalLevel.GREEN
        is PairStep.Failed -> SignalLevel.RED
        PairStep.Bonding, PairStep.StaleBond, is PairStep.WaitingTvWindow, PairStep.WaitingOwner, is PairStep.WaitingReplace -> SignalLevel.ORANGE
    })

    private fun text(a: Advice) = if (a.title.isEmpty()) a.detail else if (a.detail.isEmpty()) a.title else "${a.title} : ${a.detail}"

    fun forScreen(step: PairStep): String = of(step).text
    fun forNotification(step: PairStep): String = of(step).text
}
