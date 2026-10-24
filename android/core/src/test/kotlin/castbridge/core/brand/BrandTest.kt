package castbridge.core.brand

import kotlin.test.Test
import kotlin.test.assertEquals

class BrandTest {
    private fun custom() = Brand.init("eduplay", "EduPlay", "EduPlay TV", "TechnoHub Enterprise", "", "")
    private fun reset() = Brand.init("castbridge", "CastBridge", "CastBridge-TV", "CastBridge", "", "")

    @Test fun castbridgeLeavesTextsUntouched() {
        reset()
        val s = "Ouvrez CastBridge-TV puis CastBridge TV et CastBridge (Download/CastBridge)"
        assertEquals(s, Brand.t(s))
    }

    @Test fun customBrandReplacesNamesButNotPaths() {
        custom()
        try {
            assertEquals("Ouvrez EduPlay TV puis EduPlay TV et EduPlay (Download/CastBridge)", Brand.t("Ouvrez CastBridge-TV puis CastBridge TV et CastBridge (Download/CastBridge)"))
            assertEquals("EduPlay › Activer la TV", Brand.t("CastBridge › Activer la TV"))
        } finally { reset() }
    }
}
