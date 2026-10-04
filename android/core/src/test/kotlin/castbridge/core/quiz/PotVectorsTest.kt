package castbridge.core.quiz

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vecteurs communs de `Pot.split` : tools/wallet/ledger-vectors.json (format `castbridge-ledger-vectors-v1`).
 * Le même fichier est lu par le test Java `PotSplitVectorsTest` du serveur, qui vérifie le port `PotSplit.split` : si l'un des deux
 * partages diverge, l'un des deux tests échoue. Une ligne par cas (`"scores"` en paires ordonnées [nom, points]).
 */
class PotVectorsTest {
    private fun vectorsFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "tools/wallet/ledger-vectors.json")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("tools/wallet/ledger-vectors.json introuvable depuis ${File("").absolutePath}")
    }

    private val pair = Regex("""\["([^"]+)",\s*(-?\d+)]""")

    @Test fun potSplitMatchesTheSharedVectors() {
        val text = vectorsFile().readText()
        assertTrue(text.contains("\"castbridge-ledger-vectors-v1\""), "format du fichier de vecteurs")
        val cases = text.lines().filter { it.contains("\"pot\":") && it.contains("\"scores\":") }
        assertTrue(cases.size >= 12, "au moins 12 cas, trouvés ${cases.size}")
        for (line in cases) {
            val name = Regex(""""name":"([^"]*)"""").find(line)!!.groupValues[1]
            val pot = Regex(""""pot":(-?\d+)""").find(line)!!.groupValues[1].toLong()
            val scoresPart = line.substringAfter("\"scores\":").substringBefore("\"expect\":")
            val expectPart = line.substringAfter("\"expect\":")
            val scores = LinkedHashMap<String, Int>()
            pair.findAll(scoresPart).forEach { scores[it.groupValues[1]] = it.groupValues[2].toInt() }
            val expect = LinkedHashMap<String, Long>()
            pair.findAll(expectPart).forEach { expect[it.groupValues[1]] = it.groupValues[2].toLong() }
            val got = Pot.split(pot, scores)
            assertEquals(expect.toList(), got.toList(), name)
        }
    }
}
