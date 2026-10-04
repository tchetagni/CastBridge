package castbridge.core.wallet.millions

import castbridge.core.wallet.millions.MillionsOfflineBudget.Decision
import castbridge.core.wallet.millions.MillionsOfflineBudget.Reason
import castbridge.core.wallet.millions.MillionsOfflineBudget.Unconfirmed
import castbridge.core.wallet.millions.MillionsWinCaps.CapStatus
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MillionsBudgetTest {
    private val open = CapStatus.Open
    private fun u(id: String, stake: Long = 500, gain: Long = 0, finished: Boolean = true) = Unconfirmed(id, stake, gain, finished)

    @Test fun availableIsConfirmedMinusUnconfirmedStakes() {
        val b = MillionsOfflineBudget(2800, listOf(u("a", gain = 1200), u("b", gain = 0), u("c", finished = false)))
        assertEquals(2800L - 1500, b.available())
        assertEquals(1200L, b.pendingGains())
    }

    @Test fun pendingGainsNeverEnterAvailable() {
        val without = MillionsOfflineBudget(1000, listOf(u("a", gain = 0)))
        val with = MillionsOfflineBudget(1000, listOf(u("a", gain = 10_000)))
        assertEquals(without.available(), with.available()); assertEquals(500L, with.available())
        assertEquals(10_000L, with.pendingGains())
        assertTrue(with.decide(501, 0, 20, open) is Decision.Refused, "les gains en attente ne financent pas une mise")
    }

    @Test fun inProgressGameIsNotCountedAsPendingGain() {
        assertEquals(0L, MillionsOfflineBudget(1000, listOf(u("a", gain = 800, finished = false))).pendingGains())
    }

    @Test fun refusesStakeAboveAvailable() {
        val b = MillionsOfflineBudget(700, listOf(u("a")))
        val d = b.decide(500, 0, 20, open) as Decision.Refused
        assertEquals(Reason.NOT_ENOUGH, d.reason); assertTrue(d.text.contains("500"))
        assertEquals(Decision.Allowed, MillionsOfflineBudget(700, emptyList()).decide(500, 0, 20, open))
        assertEquals(Decision.Allowed, MillionsOfflineBudget(500, emptyList()).decide(500, 0, 20, open), "exactement le disponible")
        assertEquals(Reason.NOT_ENOUGH, (MillionsOfflineBudget(499, emptyList()).decide(500, 0, 20, open) as Decision.Refused).reason)
    }

    @Test fun unconfirmedStakesMayExceedConfirmedButAvailableStaysZero() {
        val b = MillionsOfflineBudget(300, listOf(u("a"), u("b")))
        assertEquals(0L, b.available())
    }

    @Test fun refusesAtDailyPlayMax() {
        val b = MillionsOfflineBudget(100_000, emptyList())
        assertEquals(Decision.Allowed, b.decide(500, 19, 20, open))
        assertEquals(Reason.DAILY_PLAYS, (b.decide(500, 20, 20, open) as Decision.Refused).reason)
        assertEquals(Reason.DAILY_PLAYS, (b.decide(500, 21, 20, open) as Decision.Refused).reason)
    }

    @Test fun refusesWhenWinCapsClosed() {
        val closed = CapStatus.Closed(MillionsWinCaps.Cap.DAY, 0, "Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie possible demain à 00:00.")
        val d = MillionsOfflineBudget(100_000, emptyList()).decide(500, 0, 20, closed) as Decision.Refused
        assertEquals(Reason.WIN_CAP, d.reason); assertEquals(closed.text, d.text)
    }

    @Test fun withAndWithoutGame() {
        val b = MillionsOfflineBudget(2000, emptyList()).withGame(u("a", gain = 500))
        assertEquals(1500L, b.available())
        assertEquals(2000L, b.withoutGame("a").available())
        assertEquals(b.unconfirmed, b.withoutGame("zz").unconfirmed)
    }

    @Test fun neverStakesBeyondAvailableOver2000Sequences() {
        repeat(2000) { seed ->
            val r = Random(seed)
            val confirmed = r.nextLong(0, 5_000)
            var b = MillionsOfflineBudget(confirmed, emptyList())
            repeat(r.nextInt(1, 30)) { step ->
                val stake = listOf(500L, 500L, 250L, 1000L)[r.nextInt(4)]
                val before = b.available()
                if (b.decide(stake, r.nextInt(0, 22), 20, open) == Decision.Allowed) {
                    assertTrue(stake <= before, "seed=$seed step=$step : mise $stake > disponible $before")
                    b = b.withGame(u("g$step", stake, gain = r.nextLong(0, 10_001), finished = r.nextBoolean()))
                }
                assertTrue(b.unconfirmed.sumOf { it.stake } <= confirmed, "seed=$seed : mises non confirmées > confirmé")
                assertEquals(confirmed - b.unconfirmed.sumOf { it.stake }, b.available())
                assertTrue(b.available() >= 0)
                // les gains (en attente) ne changent jamais le disponible
                assertEquals(b.available(), MillionsOfflineBudget(confirmed, b.unconfirmed.map { it.copy(gain = 0) }).available())
            }
        }
    }
}

/** Le cœur ne peut créer aucune valeur : aucune méthode publique du paquet ne crédite, ne crée ni n'augmente un solde. */
class MillionsNoMintSourceTest {
    private val dir = File("src/main/kotlin/castbridge/core/wallet/millions")
    private val files get() = dir.listFiles { f -> f.extension == "kt" }!!.toList()

    @Test fun packageExists() { assertTrue(files.size >= 7, "fichiers : ${files.map { it.name }}") }

    @Test fun noPublicFunctionRaisesABalance() {
        val banned = Regex("""(?i)\b(credit|mint|deposit|topup|grant|award|payout|reward|refund|airdrop|increase|raise|addbalance|addndem|addmboko|setbalance|setavailable|settle)\w*""")
        for (f in files) for ((n, line) in f.readLines().withIndex()) {
            val m = Regex("""^\s*(?!private|internal|protected)(?:override\s+)?(?:inline\s+)?fun\s+(?:<[^>]*>\s*)?(?:\w+\.)?(\w+)""").find(line) ?: continue
            if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) continue
            val name = m.groupValues[1]
            assertFalse(banned.containsMatchIn(name), "${f.name}:${n + 1} méthode publique « $name » suspecte")
        }
    }

    @Test fun noMutableAmountIsPublic() {
        for (f in files) for ((n, line) in f.readLines().withIndex()) {
            if (Regex("""^\s*(?!private)(?:public\s+)?var\s+\w*(balance|ndem|mboko|available|solde|credit)\w*""", RegexOption.IGNORE_CASE).containsMatchIn(line)) assertTrue(false, "${f.name}:${n + 1} montant modifiable : $line")
        }
    }

    @Test fun doesNotTouchTheWalletCacheNorSignPacks() {
        for (f in files) {
            val t = f.readText()
            assertFalse(t.contains("WalletCache"), f.name)
            assertFalse(t.contains("receiveVoucher"), f.name)
        }
        // la TV ne signe QUE le journal : ni pack ni instantané n'ont de méthode de signature en production
        assertFalse(File(dir, "MillionsPack.kt").readText().contains("fun sign"))
    }
}
