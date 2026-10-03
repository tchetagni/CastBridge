package castbridge.core.store

import kotlin.test.Test
import kotlin.test.assertEquals

/** Priorité du drapeau de la Boutique : réglage W12, puis ordre signé, puis défaut compilé (w17-02, D-W17-10). */
class StoreFlagTest {
    @Test fun nameIsTheClosedListEntry() { assertEquals("store.enabled", StoreFlag.NAME) }

    @Test fun priorityTable() {
        // (réglage, ordre, défaut) -> attendu
        val table = listOf(
            Triple<Boolean?, Boolean?, Boolean>(null, null, false) to false,
            Triple<Boolean?, Boolean?, Boolean>(null, null, true) to true,
            Triple<Boolean?, Boolean?, Boolean>(null, true, false) to true,
            Triple<Boolean?, Boolean?, Boolean>(null, false, true) to false,
            Triple<Boolean?, Boolean?, Boolean>(true, false, false) to true,
            Triple<Boolean?, Boolean?, Boolean>(false, true, true) to false,
            Triple<Boolean?, Boolean?, Boolean>(true, null, false) to true,
            Triple<Boolean?, Boolean?, Boolean>(false, null, true) to false,
        )
        assertEquals(8, table.size)
        for ((input, expected) in table) assertEquals(expected, StoreFlag.enabled(input.first, input.second, input.third), "$input")
    }
}
