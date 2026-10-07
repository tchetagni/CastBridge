package castbridge.core.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le raccourci « Ouvrir CastBridge-TV » (appui long sur l'icône) et le lien `castbridge://open-tv` sont du XML Android que le JVM ne charge pas : ce test lit les
 * fichiers du dépôt pour garder d'accord le raccourci, le manifeste du téléphone, les mots du cœur et la règle « aucune permission de plus côté téléphone ».
 */
class OpenTvShortcutTest {
    private fun repoFile(rel: String): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, rel).exists()) d = d.parentFile
        return File(d ?: error("repo root not found"), rel)
    }
    private val shortcuts by lazy { repoFile("android/sender/src/main/res/xml/shortcuts.xml").readText() }
    private val strings by lazy { repoFile("android/sender/src/main/res/values/open_tv_strings.xml").readText() }
    private val phoneManifest by lazy { repoFile("android/sender/src/main/AndroidManifest.xml").readText() }
    private val tvManifest by lazy { repoFile("android/receiver/src/main/AndroidManifest.xml").readText() }

    private fun attr(xml: String, name: String): String? = Regex("""android:$name="([^"]*)"""").find(xml)?.groupValues?.get(1)
    private fun string(name: String): String? = Regex("""<string name="$name">([^<]*)</string>""").find(strings)?.groupValues?.get(1)?.replace("\\'", "'")

    @Test fun theShortcutOpensTheLinkOfTheMainScreenOfThePhone() {
        assertEquals("castbridge://open-tv", attr(shortcuts, "data"))
        assertEquals("android.intent.action.VIEW", attr(shortcuts, "action"))
        assertEquals("castbridge.sender", attr(shortcuts, "targetPackage"), "the application id of the phone")
        assertEquals("castbridge.sender.MainActivity", attr(shortcuts, "targetClass"))
        assertEquals("true", attr(shortcuts, "enabled"))
        assertEquals("open_tv", attr(shortcuts, "shortcutId"))
        assertTrue(repoFile("android/sender/build.gradle.kts").readText().contains("""applicationId = "castbridge.sender""""), "the target package is the real application id (a resource cannot use a placeholder)")
    }

    @Test fun theLinkIsDeclaredOnTheMainActivityAndASingleWebPageCannotFireIt() {
        val main = phoneManifest.substringAfter("""<activity android:name=".MainActivity"""").substringBefore("</activity>")
        val filter = main.split("<intent-filter>").first { """android:host="open-tv"""" in it }.substringBefore("</intent-filter>")
        assertTrue("""android:scheme="castbridge"""" in filter)
        assertTrue("android.intent.action.VIEW" in filter)
        assertTrue("android.intent.category.DEFAULT" in filter)
        assertFalse("BROWSABLE" in filter, "no BROWSABLE: a web page must not be able to make the TV pop up")
        assertTrue("""<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />""" in main, "the shortcut file is referenced by the main activity")
    }

    @Test fun theShortcutNamesAreStringResourcesInAgreementWithTheCoreWords() {
        for (a in listOf("shortcutShortLabel", "shortcutLongLabel", "shortcutDisabledMessage")) assertTrue(attr(shortcuts, a)!!.startsWith("@string/"), "$a must be a string resource")
        assertEquals("Ouvrir CastBridge-TV", string("shortcut_open_tv_long"))
        assertEquals(OpenTvTexts.SHORTCUT, string("shortcut_open_tv_long"))
        val short = string("shortcut_open_tv_short")!!
        assertTrue(short.length <= 10, "Android advises 10 characters for the short label: « $short »")
        assertTrue(string("shortcut_open_tv_disabled")!!.isNotBlank())
        for (n in listOf("shortcut_open_tv_short", "shortcut_open_tv_long", "shortcut_open_tv_disabled")) assertTrue("@string/$n" in shortcuts, n)
        for (t in listOf(short, string("shortcut_open_tv_long")!!)) assertFalse("sender" in t.lowercase() || "receiver" in t.lowercase(), t)
    }

    @Test fun theIconOfTheShortcutExists() {
        assertEquals("@drawable/ic_cb_ouvrir_tv", attr(shortcuts, "icon"))
        assertTrue(repoFile("android/sender/src/main/res/drawable/ic_cb_ouvrir_tv.xml").isFile)
    }

    @Test fun thePhoneGainsNoPermissionForThis() {
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(phoneManifest).map { it.groupValues[1] }.toSet()
        // « SYSTEM_ALERT_WINDOW côté TV seulement » ; HDMI-CEC and the like are not even possible without system rights
        for (p in listOf("android.permission.SYSTEM_ALERT_WINDOW", "android.permission.USE_FULL_SCREEN_INTENT", "android.permission.REORDER_TASKS", "android.permission.WRITE_SETTINGS",
            "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND", "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO"))
            assertFalse(p in permissions, "the phone must not declare $p")
    }

    @Test fun theTvCanSeeWhetherTheOverlayScreenExistsOnThisBox() {
        // « queries » only shows an app's components when the declared intent matches their filter: that screen's filter asks for the package: scheme
        val q = tvManifest.substringAfter("<queries>").substringBefore("</queries>")
        assertTrue("""<intent><action android:name="android.settings.action.MANAGE_OVERLAY_PERMISSION" /><data android:scheme="package" /></intent>""" in q)
    }

    @Test fun theTvKeepsItsTwoExistingPermissionsAndAddsNoneForThis() {
        val permissions = Regex("""<uses-permission android:name="([^"]+)"""").findAll(tvManifest).map { it.groupValues[1] }.toSet()
        assertTrue("android.permission.SYSTEM_ALERT_WINDOW" in permissions, "the one place where it is declared and proposed once")
        assertTrue("android.permission.USE_FULL_SCREEN_INTENT" in permissions)
        for (p in listOf("android.permission.REORDER_TASKS", "android.permission.WRITE_SECURE_SETTINGS", "android.permission.INJECT_EVENTS", "android.permission.CONTROL_DISPLAY_BRIGHTNESS"))
            assertFalse(p in permissions, "the TV must not declare $p for this")
    }
}
