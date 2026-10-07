package castbridge.core.trust

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-30 (audit anti-régression 2026-10-07 b, B2) : `RelayRuntime` lisait le code d'une TV avec son adresse Bluetooth NUE (`AA:BB:…`) ; `PinKeys.resolve` ne reconnaît une adresse que
 * préfixée `bt:` et `PinBook.formOf` lit la nue comme un hôte IPv6 : ni jeton ni code, `synced = false`, la TV écartait le téléphone une heure (« non synchronisé ») et le tuyau
 * automatique ne s'ouvrait JAMAIS. La clé d'une TV connue par sa seule adresse se construit en UN endroit ([PinKeys.btKey]) ; ce test de SOURCE garde les appelants du téléphone.
 */
class BluetoothPinKeySourceTest {
    private val sender = PhoneSources.kotlinFiles("sender")

    @Test fun theGuardSeesTheFilesItWatches() {
        val names = sender.map { it.name }.toSet()
        for (f in listOf("RelayRuntime.kt", "BtGatewayService.kt", "TvDeviceRequestActivity.kt", "TvHub.kt", "RemoteController.kt", "OpenTv.kt", "LotsRuntime.kt", "PlaybackService.kt", "PinStore.kt"))
            assertTrue(f in names, "le test ne voit plus $f (${sender.size} fichiers lus depuis ${java.io.File(".").absolutePath})")
    }

    @Test fun noSenderSourceBuildsABluetoothKeyByHand() {
        // « bt:$adresse », « bt:${tv.address} », « "bt:" + x » : la seule fabrique est PinKeys.btKey (majuscules, un seul préfixe)
        val byHand = Regex("""["']bt:\$|"bt:"\s*\+|\+\s*"bt:"""")
        val offenders = sender.filter { byHand.containsMatchIn(PhoneSources.stripComments(it.readText())) }.map { it.name }.sorted()
        assertEquals(emptyList(), offenders, "une clé « bt: » écrite à la main : utiliser PinKeys.btKey(adresse)")
    }

    @Test fun noPinOrTokenLookupUsesABareBluetoothAddress() {
        // PinStore(ctx).get(address, …), pins.get(r.address, …), pins.pinOnly(tv.address), TvLinkManager.credentialFor(tv.address) : l'adresse nue n'est la clé de rien.
        // (TvLinkManager.saved.get(address) est le registre des TV, qui se lit bien par adresse : il n'est pas visé.)
        val argIsAnAddress = """\(\s*(?:[A-Za-z_]\w*\.)*(?:address|addr|btAddress)\s*[,)]"""
        val bare = Regex("""(?:PinStore\([^)]*\)|\bpins|\bstore)\s*\.(?:get|pinOnly|put|decide|lockLeft|refused|link)$argIsAnAddress|TvLinkManager\s*\.(?:credentialFor|savedFor|credentialForBase)$argIsAnAddress""")
        val offenders = mutableListOf<String>()
        for (f in sender) PhoneSources.stripComments(f.readText()).lines().forEachIndexed { i, l ->
            if (bare.containsMatchIn(l)) offenders += "${f.name}:${i + 1} : ${l.trim()}"
        }
        assertEquals(emptyList(), offenders, "une adresse Bluetooth nue sert de clé de code ou de jeton : PinKeys.btKey(adresse)")
    }

    @Test fun theGuardRecognisesTheBugItGuards() {
        // le motif trouve bien l'écriture fautive de RelayRuntime avant correctif, et laisse passer la bonne (pas de garde « à vide »)
        val argIsAnAddress = """\(\s*(?:[A-Za-z_]\w*\.)*(?:address|addr|btAddress)\s*[,)]"""
        val bare = Regex("""(?:PinStore\([^)]*\)|\bpins|\bstore)\s*\.(?:get|pinOnly|put|decide|lockLeft|refused|link)$argIsAnAddress|TvLinkManager\s*\.(?:credentialFor|savedFor|credentialForBase)$argIsAnAddress""")
        assertTrue(bare.containsMatchIn("val credential = runCatching { PinStore(ctx).get(address, tv.name) }.getOrDefault(\"\")"))
        assertTrue(bare.containsMatchIn("val pin = pins.pinOnly(tv.address)"))
        assertTrue(bare.containsMatchIn("TvLinkManager.credentialFor(tv.address)"))
        assertFalse(bare.containsMatchIn("PinStore(ctx).get(PinKeys.btKey(address), tv.name)"))
        assertFalse(bare.containsMatchIn("val tv = TvLinkManager.saved.get(dev.address) ?: return"))
        assertFalse(bare.containsMatchIn("pins.get(tv.name)"))
    }

    @Test fun theRelayAndTheGatewayServiceLookTheCodeUpUnderTheBluetoothKey() {
        for (f in listOf("sender/src/main/kotlin/castbridge/sender/RelayRuntime.kt", "sender/src/main/kotlin/castbridge/sender/BtGatewayService.kt")) {
            val code = PhoneSources.code(f)
            assertTrue(Regex("""PinStore\([^)]*\)\.get\(\s*PinKeys\.btKey\(""").containsMatchIn(code), "$f : le code d'une TV connue par son adresse se lit sous PinKeys.btKey(adresse)")
        }
        assertTrue(Regex("""pins\.pinOnly\(\s*PinKeys\.btKey\(""").containsMatchIn(PhoneSources.code("sender/src/main/kotlin/castbridge/sender/TvDeviceRequestActivity.kt")),
            "TvDeviceRequestActivity : le code gardé de la TV par défaut se lit sous PinKeys.btKey(adresse)")
    }

    @Test fun theRelayDecidesSyncedFromTheSavedTvNotOnlyFromAFreshToken() {
        // B2, second half : un jeton expiré (cas normal d'un téléphone réveillé après 12 h) ne vaut pas « non synchronisé » pour une TV enregistrée
        val code = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/RelayRuntime.kt")
        assertTrue(Regex("""synced\s*=\s*RelaySync\.synced\(\s*TvLinkManager\.saved\.get\(\s*address\s*\)\s*,\s*credential\s*\)""").containsMatchIn(code),
            "RelayRuntime.decideAndStart : synced = RelaySync.synced(TvLinkManager.saved.get(address), credential) : la TV telle que le registre la garde AU MOMENT de la décision (oubliée entre-temps ⇒ pas synchronisée)")
        assertFalse(Regex("""synced\s*=\s*TvAuth\.isUsable\(""").containsMatchIn(code), "synced ne dépend plus du seul jeton vivant")
    }
}
