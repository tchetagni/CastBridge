package castbridge.core.tv.status

import castbridge.core.brand.BrandTokens
import castbridge.core.status.IconKind
import castbridge.core.status.IconState
import castbridge.core.status.StatusIcon
import castbridge.core.status.Tech
import castbridge.core.tv.PlayerIcons
import kotlin.math.pow
import kotlin.test.*

/** Pastilles d'état à 4 couleurs signalétiques (noir, vert, orange, rouge) + bleu et gris. Le dessin Android n'est pas prouvé ici (compilation seulement). */
class StatusLevelTest {
    private val L = StatusLevel.values().toList()
    private fun lv(v: Verdict) = v.level
    private val GB = 1L shl 30

    @Test fun wifiTable() {
        assertEquals(StatusLevel.OFF, lv(StatusRules.wifi(false, false, false, null, null)))
        assertEquals(StatusLevel.OK, lv(StatusRules.wifi(true, true, false, -50, true)))
        assertEquals(StatusLevel.OK, lv(StatusRules.wifi(true, true, false, null, null)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.wifi(true, true, false, -70, true)))
        assertEquals(StatusLevel.OK, lv(StatusRules.wifi(true, true, false, -69, true)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.wifi(true, true, false, -40, false)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.wifi(true, false, false, null, null)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.wifi(true, false, true, null, null)))
        assertEquals("connecté", StatusRules.wifi(true, true, false, -50, true).word)
        assertEquals("sans Internet", StatusRules.wifi(true, true, false, -50, false).word)
        assertEquals("signal faible", StatusRules.wifi(true, true, false, -80, true).word)
    }
    @Test fun wifiDirectTable() {
        assertEquals(StatusLevel.OFF, lv(StatusRules.wifiDirect(false, DirectPhase.GROUP_ACTIVE)))
        assertEquals(StatusLevel.OK, lv(StatusRules.wifiDirect(true, DirectPhase.GROUP_ACTIVE)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.wifiDirect(true, DirectPhase.STARTING)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.wifiDirect(true, DirectPhase.FAILED)))
        assertEquals(StatusLevel.OFF, lv(StatusRules.wifiDirect(true, DirectPhase.IDLE)))
    }
    @Test fun bluetoothTable() {
        assertEquals(StatusLevel.OFF, lv(StatusRules.bluetooth(false, BtPhase.IDLE, false)))
        assertEquals(StatusLevel.OK, lv(StatusRules.bluetooth(true, BtPhase.IDLE, false)))
        assertEquals(StatusLevel.OK, lv(StatusRules.bluetooth(true, BtPhase.PHONE_LINKED, false)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.bluetooth(true, BtPhase.CONNECTING, false)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.bluetooth(true, BtPhase.ADAPTER_ERROR, false)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.bluetooth(true, BtPhase.PHONE_REFUSED, false)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.bluetooth(true, BtPhase.GATEWAY_ONLY, true)))
        assertEquals(StatusLevel.OK, lv(StatusRules.bluetooth(true, BtPhase.GATEWAY_ONLY, false)))
    }
    @Test fun internetTable() {
        assertEquals(StatusLevel.OK, lv(StatusRules.internet(InternetPath.DIRECT, latencyMs = 80)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.internet(InternetPath.DIRECT, latencyMs = 1500)))
        assertEquals(StatusLevel.OK, lv(StatusRules.internet(InternetPath.DIRECT, latencyMs = 1499)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.internet(InternetPath.VIA_PHONE)))
        assertEquals("lent", StatusRules.internet(InternetPath.VIA_PHONE).word)
        assertEquals(StatusLevel.ERROR, lv(StatusRules.internet(InternetPath.NONE, expected = true)))
        assertEquals(StatusLevel.OFF, lv(StatusRules.internet(InternetPath.NONE, expected = false)))
        assertEquals(StatusLevel.UNKNOWN, lv(StatusRules.internet(InternetPath.UNTESTED)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.internet(InternetPath.CHECKING)))
    }
    @Test fun storageThresholdsAreExact() {
        assertEquals(StatusLevel.OK, lv(StatusRules.storage(true, 20L * GB, 100L * GB)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.storage(true, 20L * GB - 1, 100L * GB)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.storage(true, 5L * GB, 100L * GB)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.storage(true, 5L * GB - 1, 100L * GB)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.storage(true, 0, 100L * GB)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.storage(true, 50L * GB, 100L * GB, readOnly = true)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.storage(true, 50L * GB, 100L * GB, error = true)))
        assertEquals(StatusLevel.OFF, lv(StatusRules.storage(false, 0, 0)))
        assertEquals(StatusLevel.UNKNOWN, lv(StatusRules.storage(true, 0, 0)))
        assertEquals("12 % libre", StatusRules.storage(true, 12L * GB, 100L * GB).word)
        assertEquals("lecture seule", StatusRules.storage(true, 50L * GB, 100L * GB, readOnly = true).word)
        assertEquals("absent", StatusRules.storage(false, 0, 0).word)
    }
    @Test fun otherBadgesTables() {
        assertEquals(StatusLevel.OFF, lv(StatusRules.phones(0))); assertEquals(StatusLevel.OK, lv(StatusRules.phones(3)))
        assertEquals(StatusLevel.OK, lv(StatusRules.phones(7))); assertEquals(StatusLevel.WARN, lv(StatusRules.phones(8)))
        assertEquals("3/8", StatusRules.phones(3).word)
        assertEquals(StatusLevel.OFF, lv(StatusRules.copy(false, null, false, false)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.copy(true, 42, false, false)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.copy(true, 42, true, false)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.copy(false, null, false, true)))
        assertEquals("42 %", StatusRules.copy(true, 42, false, false).word)
        assertEquals(StatusLevel.OFF, lv(StatusRules.quiz(QuizLink.IDLE)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.quiz(QuizLink.CONNECTING)))
        assertEquals(StatusLevel.OK, lv(StatusRules.quiz(QuizLink.CONNECTED, 200)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.quiz(QuizLink.CONNECTED, 1500)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.quiz(QuizLink.NOT_ACTIVATED)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.quiz(QuizLink.SERVER_UNREACHABLE)))
        assertEquals(StatusLevel.UNKNOWN, lv(StatusRules.tokens(null, 10)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.tokens(false, 10)))
        assertEquals(StatusLevel.UNKNOWN, lv(StatusRules.tokens(true, null)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.tokens(true, 0)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.tokens(true, 5)))
        assertEquals(StatusLevel.OK, lv(StatusRules.tokens(true, 6)))
        assertEquals(StatusLevel.OK, lv(StatusRules.phoneLink(true, false, false, 80)))
        assertEquals(StatusLevel.WARN, lv(StatusRules.phoneLink(true, false, false, 1500)))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.phoneLink(false, true, false, null)))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.phoneLink(false, false, true, null)))
    }
    private fun icon(kind: IconKind, state: IconState = IconState.CONNECTED, tech: Tech = Tech.WIFI_LAN, latency: Long? = null) =
        StatusIcon(kind, tech, null, kind.label, state, 0, latencyMs = latency)
    @Test fun everyStatusBarKindHasAnExplicitRuleAndAStateWord() {
        for (k in IconKind.values()) for (s in IconState.values()) {
            val r = StatusRules.icon(icon(k, s))
            assertNotEquals(StatusLevel.UNKNOWN, r.level, "${k.wire}/${s.wire}"); assertTrue(r.word.isNotBlank(), "mot d'état ${k.wire}/${s.wire}")
        }
        assertEquals(StatusLevel.OK, lv(StatusRules.icon(icon(IconKind.INTERNET, tech = Tech.ETHERNET))))
        assertEquals(StatusLevel.WARN, lv(StatusRules.icon(icon(IconKind.INTERNET, tech = Tech.BLUETOOTH))))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.icon(icon(IconKind.INTERNET, IconState.ERROR, Tech.NONE))))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.icon(icon(IconKind.INTERNET, IconState.CONNECTING, Tech.NONE))))
        assertEquals(StatusLevel.WARN, lv(StatusRules.icon(icon(IconKind.PHONE, IconState.DEGRADED))))
        assertEquals(StatusLevel.WARN, lv(StatusRules.icon(icon(IconKind.GATEWAY, latency = 2000))))
        assertEquals(StatusLevel.OK, lv(StatusRules.icon(icon(IconKind.GATEWAY, latency = 100))))
        assertEquals(StatusLevel.ERROR, lv(StatusRules.icon(icon(IconKind.USB_DRIVE, IconState.ERROR))))
        assertEquals(StatusLevel.BUSY, lv(StatusRules.icon(icon(IconKind.DOWNLOAD))))
    }
    @Test fun warnAndErrorAreNeverMerged() {
        assertNotEquals(StatusRules.storage(true, 10L * GB, 100L * GB).level, StatusRules.storage(true, 1L * GB, 100L * GB).level)
        assertNotEquals(StatusPalette.ring(StatusLevel.WARN), StatusPalette.ring(StatusLevel.ERROR))
    }
    @Test fun unknownIsNeverTreatedAsOk() {
        assertNotEquals(StatusLevel.OK, lv(StatusRules.internet(InternetPath.UNTESTED)))
        assertNotEquals(StatusPalette.ring(StatusLevel.UNKNOWN), StatusPalette.ring(StatusLevel.OK))
        assertEquals("non testé", StatusRules.internet(InternetPath.UNTESTED).word)
    }
    @Test fun everyLevelHasAFrenchColourNameAndMeaning() {
        assertEquals(listOf("Noir", "Vert", "Orange", "Rouge", "Bleu", "Gris"), L.map { it.colour })
        assertTrue(L.all { it.meaning.length > 8 })
        assertEquals(L.size, L.map { it.wire }.toSet().size)
        assertEquals(StatusLevel.WARN, StatusLevel.fromWire("warn"))
    }
    @Test fun textAlwaysCarriesTheLabelAndTheStateWord() {
        assertEquals("Wi-Fi · connecté", StatusRules.wifi(true, true, false, -50, true).text("Wi-Fi"))
        assertEquals("Stockage · 12 % libre", StatusRules.storage(true, 12L * GB, 100L * GB).text("Stockage"))
        assertEquals("Internet · lent", StatusRules.internet(InternetPath.VIA_PHONE).text("Internet"))
        assertEquals("Wi-Fi · désactivé", StatusRules.wifi(false, false, false, null, null).text("Wi-Fi"))
    }

    // ---- couleurs ----
    private fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    private fun lum(a: Int) = 0.2126 * lin(a shr 16 and 255) + 0.7152 * lin(a shr 8 and 255) + 0.0722 * lin(a and 255)
    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun colourMappingUsesTheCharteTokens() {
        assertEquals(BrandTokens.Semantic.SUCCESS_DARK, StatusPalette.ring(StatusLevel.OK))
        assertEquals(BrandTokens.Semantic.WARNING_DARK, StatusPalette.ring(StatusLevel.WARN))
        assertEquals(BrandTokens.Semantic.ERROR_DARK, StatusPalette.ring(StatusLevel.ERROR))
        assertEquals(BrandTokens.Semantic.INFO_DARK, StatusPalette.ring(StatusLevel.BUSY))
        assertEquals(BrandTokens.Semantic.OFF_DARK, StatusPalette.fill(StatusLevel.OFF))
        assertEquals(BrandTokens.Semantic.OFF_DARK, StatusPalette.dot(StatusLevel.OFF))
        assertEquals(BrandTokens.Semantic.SUCCESS_LIGHT, StatusPalette.dotLight(StatusLevel.OK))
        assertEquals(L.size, L.map { StatusPalette.ring(it) }.toSet().size, "six anneaux distincts")
    }
    @Test fun blackLevelStaysVisibleOnTheDarkTvBackground() {
        val bg = BrandTokens.Dark.BACKGROUND
        for (l in L) assertTrue(ratio(StatusPalette.ring(l), bg) >= 3.0, "anneau ${l.wire} sur le fond de la TV")
        assertTrue(ratio(StatusPalette.ring(StatusLevel.OFF), StatusPalette.fill(StatusLevel.OFF)) >= 3.0, "anneau clair sur remplissage noir")
        assertNotEquals(StatusPalette.ring(StatusLevel.OFF), StatusPalette.fill(StatusLevel.OFF))
        assertEquals(0xFF000000.toInt(), StatusPalette.fill(StatusLevel.OFF))
        val a = StatusPalette.glyphAlpha(StatusLevel.OFF)
        assertTrue(a in 0.4f..0.9f, "icône atténuée mais jamais invisible : $a")
        for (l in L) assertTrue(StatusPalette.glyphAlpha(l) >= 0.4f)
        assertEquals(1f, StatusPalette.glyphAlpha(StatusLevel.OK))
        for (l in L) assertTrue(ratio(StatusPalette.ring(l), StatusPalette.fill(l)) >= 3.0, "anneau ${l.wire} sur son remplissage")
    }
    @Test fun legendListsTheSixLevelsWithTheirMeaning() {
        val legend = PlayerIcons.legend()
        for (l in L) {
            val e = legend.firstOrNull { it.id == "level-${l.wire}" }
            assertNotNull(e, l.wire)
            assertTrue(e.label.contains(l.colour) && e.meaning.contains(l.meaning), "légende ${l.wire}")
        }
    }
}
