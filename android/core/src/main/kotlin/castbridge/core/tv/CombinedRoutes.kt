package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD

/** Several [PublicRoutes] behind the server's single hook (quiz, chess…): the first that answers wins. */
class CombinedRoutes(private vararg val routes: PublicRoutes) : PublicRoutes {
    override val extraThreads: Int get() = routes.sumOf { it.extraThreads }
    override fun serve(s: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        for (r in routes) r.serve(s)?.let { return it }
        return null
    }
}
