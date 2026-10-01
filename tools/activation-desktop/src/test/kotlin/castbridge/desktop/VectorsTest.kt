package castbridge.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorsTest {
    @Test fun everyCommonVectorGivesTheSameBytes() {
        val f = File(System.getProperty("activation.vectors"))
        assertEquals(emptyList(), Vectors.run(f))
        assertTrue(Vectors.lastCount >= 45, "les vecteurs de construction, de vérification et d'empreintes sont rejoués (${Vectors.lastCount})")
    }
}
