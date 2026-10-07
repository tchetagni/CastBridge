package castbridge.core.quiz.online

import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessOnlineTexts
import castbridge.core.chess.online.ChessResultSummary
import castbridge.core.chess.online.ChessStakeFlow
import castbridge.core.wallet.WalletView

/**
 * Les textes du Quiz MISÉ (games-G5), identiques sur la TV et dans les tests. Une mise s'écrit toujours avec sa monnaie : « 20 NDEM », « 5 MBOKO » (jamais « jetons » seul : les « jetons » sont la monnaie du
 * portefeuille de la TV, NDEM et MBOKO) ; « Libre » dit une partie sans rien en jeu. Les points d'une compétition entre téléphones d'une même TV (« Compétition à points ») n'ont rien à voir : ce sont des
 * points sans valeur, que personne ne paie ni ne gagne.
 */
object QuizStakeTexts {
    const val FREE = "Libre"
    const val FREE_DETAIL = "Libre : rien n'est en jeu"
    const val CREATE_TITLE = "Créer une partie avec mise ?"
    const val JOIN_TITLE = "Cette partie se joue avec une mise"
    /** Dit avant chaque partie misée (créer, rejoindre) ; assez court pour tenir sur deux lignes de la TV. */
    const val LEAVE_WARNING = "Quitter la partie fait perdre la mise ; plus de 60 s hors ligne, vos joueurs ne marquent plus."
    const val ONE_TV_ONLY = "Une partie avec mise a besoin d'au moins deux TV qui misent, chacune avec un joueur."
    const val NO_WATCH = "Cette partie se joue avec une mise : pour la rejoindre, bloquez votre mise."
    const val PHONES_LOCKED = "Les mises sont figées : aucun nouveau joueur ne peut s'asseoir."

    /** « Mise : 20 NDEM par joueur » : la mise d'une partie, partout où elle s'affiche. */
    fun stakeLine(s: StakeSpec) = "Mise : ${WalletView.thousands(s.per)} ${s.cur} par joueur"

    /** La ligne de la salle : la mise et la cagnotte (mise × sièges qui misent), d'après le bloc `stake` de la vue du service ; null pour une salle libre. */
    fun roomLine(stake: Map<*, *>?): String? {
        val cur = stake?.get("cur") as? String ?: return null
        val per = (stake["per"] as? Number)?.toLong() ?: return null
        val pot = (stake["pot"] as? Number)?.toLong() ?: 0L
        return "Mise : ${WalletView.thousands(per)} $cur par joueur · cagnotte ${WalletView.thousands(pot)} $cur"
    }

    /** « Ici : 2 joueurs qui misent sur 2 mises bloquées » : les téléphones de CETTE TV face à ce qu'elle a bloqué. */
    fun hereLine(playersHere: Int, blockedSeats: Int): String {
        val who = when (playersHere) { 0 -> "aucun joueur"; 1 -> "1 joueur"; else -> "$playersHere joueurs" }
        val stakes = if (blockedSeats == 1) "1 mise bloquée" else "$blockedSeats mises bloquées"
        return "Ici : $who · $stakes"
    }

    /** La ligne sous le titre de la confirmation d'une mise : montant, joueurs d'ici, ce qui est bloqué, solde connu. */
    fun confirmSubtitle(plan: PlayStakePlan, balance: Long?): String =
        "${WalletView.thousands(plan.spec.per)} ${plan.spec.cur} par joueur · ${if (plan.seats == 1) "1 joueur ici" else "${plan.seats} joueurs ici"} · bloqué : ${WalletView.thousands(plan.blocked)} ${plan.spec.cur}" +
            (balance?.let { " · votre solde ${WalletView.thousands(it)} ${plan.spec.cur}" } ?: "")

    /** Ce que la TV lit quand elle rejoint une salle misée (avant de bloquer quoi que ce soit). */
    fun joinQuestion(s: StakeSpec) = "Cette partie se joue avec une mise de ${WalletView.thousands(s.per)} ${s.cur} par joueur. Combien de joueurs de cette TV misent ?"

    /** Le service dit la mise d'une salle : la mise lue de `data` du refus `STAKE_ESCROW_REQUIRED`, ou null s'il manque (service plus récent ou autre jeu). */
    fun specOf(data: Map<String, Any?>?): StakeSpec? {
        val cur = data?.get("cur") as? String ?: return null
        val per = (data["per"] as? Number)?.toLong() ?: return null
        if (cur !in PlayProtocol.STAKE_CURRENCIES || per < 1) return null
        return StakeSpec(cur, per)
    }
}

/**
 * L'enchaînement d'argent d'UNE partie en ligne du Quiz côté TV, PUR (réseau, mémoire, horloge injectés) : bloquer la mise (mise × sièges qui misent), la porter au service avec la création ou l'entrée, puis
 * régler le résultat signé que le service renvoie. Il RÉUTILISE [ChessStakeFlow] (blocage, rejeu de la clé d'idempotence, résultats gardés hors ligne, règlement, jamais un solde calculé par la TV) : seul
 * change le jeu (`quiz`) et le nombre de sièges. L'écran dit le gain net APRÈS frais d'après la réponse de l'API, jamais d'après un calcul de la TV.
 * Les appels sont BLOQUANTS (réseau) : à lancer hors du fil de l'écran.
 */
class QuizOnlineStake(private val flow: ChessStakeFlow, private val clock: () -> Long, private val async: (() -> Unit) -> Unit) {
    /** La mise de la partie courante de CETTE TV (null : partie libre). */
    @Volatile var plan: PlayStakePlan? = null; private set
    @Volatile var settlement: ChessOnlineGame.Settlement = ChessOnlineGame.Settlement.None; private set
    /** Rappel (n'importe quel fil) à chaque changement de règlement : l'écran se redessine. */
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile private var myEscrow: String? = null

    /** Bloque la mise de [p] (ou réutilise un blocage encore valable pour exactement cela) ; le blocage obtenu est celui que la TV porte au service. */
    fun lock(p: PlayStakePlan): ChessStakeFlow.Lock = flow.lock(p.spec, clock(), p.seats).also {
        if (it is ChessStakeFlow.Lock.Ok) { plan = p; myEscrow = it.escrow.eid; settlement = ChessOnlineGame.Settlement.None }
    }

    /** Le service a assis la TV avec ce blocage : il est employé, il ne se réutilise plus. */
    fun escrowUsed() = flow.escrowUsed()

    /** Le service dit que ce blocage ne vaut pas pour cette partie (échu, déjà employé, autre TV) : on l'oublie, un nouveau sera demandé. */
    fun escrowRejected() = flow.escrowRejected()

    /** Un résultat signé arrive du service (n'importe quel fil) : il est gardé puis réglé sur un fil à part. */
    fun onResultMessage(token: String) = async { handleResult(token) }

    /** Garde puis règle un résultat signé ; public pour les tests et les reprises. */
    fun handleResult(token: String) {
        val sum = ChessResultSummary.of(token)
        val cur = sum?.cur ?: plan?.spec?.cur.orEmpty()
        val mine = myEscrow
        val before = if (sum != null && mine != null) sum.netOf(mine) else null
        publish(ChessOnlineGame.Settlement.Pending(before?.let { ChessOnlineTexts.outcome(cur, it, sum?.aborted == true) + " · " + ChessOnlineTexts.SETTLING } ?: ChessOnlineTexts.SETTLING))
        when (val r = flow.onResult(token)) {
            is ChessStakeFlow.Settled.Done -> {
                val net = mine?.let { flow.netAfterFees(r.done, it) } ?: before ?: 0L
                val aborted = r.done.kind == "ABORT"
                publish(ChessOnlineGame.Settlement.Done(ChessOnlineTexts.outcome(r.done.cur, net, aborted) + " · " + ChessOnlineTexts.SETTLED, net, r.done.cur, aborted))
            }
            ChessStakeFlow.Settled.Later ->
                publish(ChessOnlineGame.Settlement.Later(before?.let { ChessOnlineTexts.outcome(cur, it, sum?.aborted == true) + " · " + ChessOnlineTexts.SETTLE_LATER } ?: ChessOnlineTexts.SETTLE_LATER))
            is ChessStakeFlow.Settled.Refused -> publish(ChessOnlineGame.Settlement.Refused(ChessOnlineTexts.SETTLE_REFUSED + " (" + r.text + ")"))
        }
    }

    /** Reposte les résultats gardés (ouverture de l'écran, retour de la connexion) ; rend le nombre de règlements faits. */
    fun retryPending(): Int = flow.retryPending().size

    /** Une partie MISÉE de cette TV est-elle en cours de règlement ou à régler (pour ne pas quitter sans le résultat) ? */
    fun settling(): Boolean = plan != null && settlement.let { it is ChessOnlineGame.Settlement.None || it is ChessOnlineGame.Settlement.Pending || it is ChessOnlineGame.Settlement.Later }

    private fun publish(s: ChessOnlineGame.Settlement) { settlement = s; onChange?.invoke() }
}
