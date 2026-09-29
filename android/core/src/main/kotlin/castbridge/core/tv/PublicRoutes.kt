package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD

/**
 * Routes of [ReceiverServer] that do NOT use the admin PIN (e.g. the quiz at /quiz, which has its own room code and
 * player tokens). They are asked first; returning null lets the request go on to the PIN check and the admin API.
 */
interface PublicRoutes {
    /** Worker threads to add to the server's pool for these routes (long-lived connections such as event streams). */
    val extraThreads: Int get() = 0

    /** The answer if [s] is one of these routes, else null. Must never serve anything of the admin API. */
    fun serve(s: NanoHTTPD.IHTTPSession): NanoHTTPD.Response?
}
