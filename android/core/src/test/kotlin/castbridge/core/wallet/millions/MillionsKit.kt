package castbridge.core.wallet.millions

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.InstallSigner
import castbridge.core.owner.KeyRing
import castbridge.core.owner.TrustedKey
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.WalletTestKeys

/** Outils de test du Défi des 10 000. CLÉS DE TEST SEULEMENT (graines fixes, publiques, sans valeur). */
internal object MK {
    val installSeed = ByteArray(32) { 0x55 }
    val install = InstallSigner(installSeed)
    val installEd = Ed25519Signer(installSeed)          // même clé, pour fabriquer des pièces fausses mais signées
    val installRing = KeyRing(listOf(TrustedKey(install.keyId, install.publicKeyBase64)))
    val stranger = InstallSigner(ByteArray(32) { 0x66 })
    const val TV = WalletTestKeys.TV
    const val PACK_ID = "00112233445566778899aabbccddeeff"
    const val GAME_ID = "ffeeddccbbaa99887766554433221100"
    const val NOW = WalletTestKeys.NOW
    const val DAY = 86_400_000L

    fun question(level: Int, i: Int): MillionsQuestion {
        val correct = (level + i) % 4
        val wrong = (0..3).filter { it != correct }
        return MillionsQuestion("q-$level-$i", "Question $level.$i ?", listOf("A", "B", "C", "D"), correct, listOf(wrong[0], wrong[2]))
    }

    fun levels(perLevel: Int = 3) = List(15) { l -> List(perLevel) { i -> question(l + 1, i) } }

    fun pack(
        id: String = TV, at: Long = NOW, from: Long = NOW - 1000, until: Long = NOW + 7 * DAY, lv: Long = 1, ladder: MillionsLadder = MillionsLadder.DEFAULT, timeSec: Int = 30,
        maxPlays: Int = 20, limits: WinLimits = WinLimits(3, 10, 15), def: WinDefinition = WinDefinition.GAIN_GT_STAKE, mw: ServerCounts = ServerCounts("2026-09-20", 0, "2026-09-14", 0, "2026-09", 0),
        levels: List<List<MillionsQuestion>> = levels(), kid: String = WalletTestKeys.wallet.keyId,
    ) = MillionsPack(kid, PACK_ID, id, at, from, until, lv, ladder, timeSec, maxPlays, limits, def, mw, levels)

    fun packToken(p: MillionsPack = pack(), signer: Ed25519Signer = WalletTestKeys.wallet, domain: String = MillionsPack.DOMAIN) = TestMint.token(MillionsPack.PREFIX, domain, p.payload(), signer)

    fun game(p: MillionsPack = pack(), t: () -> Long = { NOW }, mono: () -> Long = { 0L }) =
        MillionsGame.create(GAME_ID, p.packId, p.ladderVersion, p.ladder, p.timeSec, t, mono)

    /** Joue les bonnes réponses des niveaux [from]..[to] d'affilée (en [ms] ms chacune), en continuant aux paliers ; la partie doit être commencée. */
    fun correct(g: MillionsGame, from: Int, to: Int, ms: Long = 2000) {
        for (k in from..to) {
            val q = question(k, 0)
            check(g.show(q)) { "show $k" }
            g.answer(q.correct, ms)
            if (g.state is MillionsGame.State.AtStop && k < to) check(g.continueGame())
        }
    }

    fun journal(entries: List<MillionsJournal.Entry>, end: MillionsJournal.End, gain: Long, lv: Long = 1, packId: String = PACK_ID, t0: Long = NOW, t1: Long = NOW + 60_000) =
        MillionsJournal(install.keyId, GAME_ID, packId, lv, entries, end, gain, t0, t1)

    fun entry(level: Int, ok: Boolean = true, fifty: Boolean = false, ms: Long = 2000, i: Int = 0): MillionsJournal.Entry {
        val q = question(level, i)
        return MillionsJournal.Entry(q.qid, if (ok) q.correct else (q.correct + 1) % 4, fifty, ms)
    }

    fun correctEntries(n: Int) = List(n) { entry(it + 1) }
}
