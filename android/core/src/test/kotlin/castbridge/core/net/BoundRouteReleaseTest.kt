package castbridge.core.net

import java.net.Socket
import java.net.URL
import java.net.URLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-29 (relay-R4): the manual screens and the automatic path now share [BoundRoute]; leaving a group must not remove the binding of a join that replaced it
 * (the automatic path may have joined the same TV's group meanwhile).
 */
class BoundRouteReleaseTest {
    private fun binding(opened: MutableList<String> = mutableListOf()) = object : BoundRoute.Binding {
        override fun open(url: URL): URLConnection { opened += url.host; return url.openConnection() }
        override fun bind(s: Socket) {}
    }

    @Test fun releasingTheInstalledBindingUnbindsTheGroupAddresses() {
        val b = binding()
        BoundRoute.set("192.168.49.", b)
        try { assertTrue(BoundRoute.applies("192.168.49.1")); BoundRoute.release(b); assertFalse(BoundRoute.applies("192.168.49.1")) } finally { BoundRoute.clear() }
    }

    @Test fun aLeftJoinDoesNotRemoveTheBindingOfTheJoinThatReplacedIt() {
        val mine = binding(); val theirs = binding()
        BoundRoute.set("192.168.49.", mine)
        BoundRoute.set("192.168.49.", theirs)               // another join (the automatic path) took the route over
        try {
            BoundRoute.release(mine)                        // mine is left: it must not unbind theirs
            assertTrue(BoundRoute.applies("192.168.49.1"), "the newer join keeps its route")
            BoundRoute.release(theirs)
            assertFalse(BoundRoute.applies("192.168.49.1"))
        } finally { BoundRoute.clear() }
    }

    @Test fun onlyTheAddressesOfTheGroupAreBoundNotTheInternetOfTheApp() {
        val opened = mutableListOf<String>(); val b = binding(opened)
        BoundRoute.set("192.168.49.", b)
        try {
            BoundRoute.open(URL("http://192.168.49.1:8765/api/hello")); BoundRoute.open(URL("http://127.0.0.1:1/x"))
            assertEquals(listOf("192.168.49.1"), opened, "every other address opens as usual: the Internet of the app is untouched (R-29)")
        } finally { BoundRoute.clear() }
    }
}
