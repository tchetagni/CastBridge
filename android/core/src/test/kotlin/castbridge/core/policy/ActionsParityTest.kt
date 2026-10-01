package castbridge.core.policy

import castbridge.core.net.JsonLite
import java.io.File
import kotlin.test.*

/** The closed list of the TV (PolicyActions) equals the shared file tools/orders/actions.json, which the server's PolicyCatalog is also tested against: the two lists cannot drift apart. */
class ActionsParityTest {
    @Suppress("UNCHECKED_CAST")
    private fun file(): Map<String, Any?> {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "tools/orders/actions.json").exists()) d = d.parentFile
        return JsonLite.obj(File(d ?: error("tools/orders/actions.json introuvable"), "tools/orders/actions.json").readText())
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun theTvListEqualsTheSharedList() {
        val f = file()
        assertEquals((f["actions"] as List<String>).toSet(), PolicyActions.ALL.toSet())
        assertEquals((f["flags"] as List<String>).toSet(), PolicyActions.FLAGS)
        assertEquals((f["channels"] as List<String>).toSet(), PolicyActions.CHANNELS)
        assertEquals((f["levels"] as List<String>).toSet(), PolicyActions.MESSAGE_LEVELS)
        assertEquals((f["never"] as List<String>), PolicyActions.NEVER)
        val b = f["budgets"] as Map<String, List<Number>>
        assertEquals(b.mapValues { it.value[0].toLong()..it.value[1].toLong() }, PolicyActions.BUDGETS)
    }
}
