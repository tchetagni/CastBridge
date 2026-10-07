package castbridge.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comportement observable des échecs GELÉ avant la migration de `ChessRoom` / `ChessHttp` sur la plateforme de jeux (chantier games-G1) :
 * les deux fichiers de référence ont été produits par l'implémentation d'AVANT, et la sortie d'aujourd'hui doit leur être identique octet pour octet
 * (états JSON complets, versions `v`, codes, jetons dérivés de l'aléa à graine, notices, codes et corps HTTP, trame SSE, page, verrou des codes faux).
 * Une différence n'est pas forcément un bug (une règle d'échecs peut changer exprès) : elle oblige à relire et à regénérer le fichier en connaissance de cause.
 */
class ChessGoldenTest {
    private fun golden(name: String): List<String> {
        val s = javaClass.getResourceAsStream("/castbridge/games/$name")
        assertNotNull(s, "fichier de référence $name absent")
        return s.use { String(it.readBytes(), Charsets.UTF_8) }.trimEnd('\n').split("\n")
    }

    private fun same(expected: List<String>, actual: List<String>) {
        for (i in 0 until maxOf(expected.size, actual.size)) {
            val e = expected.getOrNull(i); val a = actual.getOrNull(i)
            assertEquals(e, a, "ligne ${i + 1} différente :\nattendu : ${e?.take(700)}\nobtenu  : ${a?.take(700)}\n")
        }
    }

    @Test fun roomsBehaveExactlyAsBeforeTheMigration() = same(golden("chess-golden-rooms.txt"), ChessScenario.run())

    @Test fun httpRoutesAnswerExactlyAsBeforeTheMigration() = same(golden("chess-golden-http.txt"), ChessScenario.runHttp())
}
