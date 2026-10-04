package castbridge.core.trust

import castbridge.core.trust.UploadFailure.Cause
import castbridge.core.tv.TvClient
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.*

class UploadFailureTest {
    private fun exc(t: Throwable) = UploadFailure.ofException(t)
    private fun http(c: Int, b: String = "") = UploadFailure.ofHttp(c, b)

    @Test fun exceptionTable() {
        val rows = listOf<Pair<Throwable, Cause>>(
            SocketTimeoutException("timeout") to Cause.TIMEOUT,
            ConnectException("Connection refused") to Cause.TV_UNREACHABLE,
            NoRouteToHostException("No route to host") to Cause.TV_UNREACHABLE,
            UnknownHostException("castbridge-tv.local") to Cause.TV_NOT_FOUND,
            SocketException("Broken pipe") to Cause.CONNECTION_LOST,
            IOException("sendto failed: EPIPE (Broken pipe)") to Cause.CONNECTION_LOST,
            SocketException("Connection reset") to Cause.CONNECTION_LOST,
            SocketException("Software caused connection abort") to Cause.CONNECTION_LOST,
            SocketException("Network is unreachable") to Cause.NETWORK_CHANGED,
            IOException("ENETUNREACH (Network is unreachable)") to Cause.NETWORK_CHANGED,
            SecurityException("Permission Denial: opening provider") to Cause.FILE_UNREADABLE,
            FileNotFoundException("/storage/emulated/0/Download/x.mp4: open failed: ENOENT") to Cause.FILE_UNREADABLE,
            InterruptedIOException("cancelled") to Cause.CANCELLED,
            IOException("autre chose") to Cause.CONNECTION_LOST,
            IllegalStateException("bizarre") to Cause.UNKNOWN,
        )
        for ((t, c) in rows) assertEquals(c, exc(t).cause, t.toString())
    }

    @Test fun httpTable() {
        val rows = listOf(
            Triple(401, "{\"error\":\"locked\",\"retryAfter\":60}", Cause.PIN_LOCKED), Triple(401, "bad token", Cause.TOKEN_EXPIRED), Triple(401, "{}", Cause.PIN_REFUSED),
            Triple(403, "", Cause.PIN_REFUSED), Triple(404, "", Cause.TV_OLD), Triple(408, "", Cause.TIMEOUT), Triple(409, "", Cause.TV_BUSY),
            Triple(413, "", Cause.NO_SPACE), Triple(507, "", Cause.NO_SPACE), Triple(429, "", Cause.TV_BUSY), Triple(503, "", Cause.TV_BUSY),
            Triple(500, "", Cause.TV_ERROR), Triple(502, "", Cause.TV_ERROR), Triple(418, "", Cause.TV_ERROR), Triple(400, "", Cause.TV_ERROR),
        )
        for ((c, b, k) in rows) assertEquals(k, http(c, b).cause, "$c $b")
        assertEquals(Cause.NO_SPACE, exc(TvClient.HttpError(507, "{}")).cause)
        assertEquals(Cause.PIN_REFUSED, exc(TvClient.HttpError(401, "{\"error\":\"unauthorized\"}")).cause)
    }

    @Test fun reasonTable() {
        val rows = listOf(
            "TV introuvable" to Cause.TV_NOT_FOUND, "La TV reste introuvable : vérifiez qu'elle est allumée" to Cause.TV_NOT_FOUND,
            "Fichier illisible" to Cause.FILE_UNREADABLE, "Accès au fichier perdu (téléphone redémarré, ou fichier déplacé)" to Cause.FILE_UNREADABLE,
            "annulé" to Cause.CANCELLED, "Envoi interrompu" to Cause.SERVICE_STOPPED,
            "L'envoi n'a pas démarré (aucune réponse du service d'envoi en 90 s)" to Cause.SERVICE_STOPPED,
            "Android a limité les envois en arrière-plan (limite de durée)" to Cause.TIME_LIMIT, "Service refusé par le système : x" to Cause.BACKGROUND_BLOCKED,
            "Code de la TV incorrect." to Cause.PIN_REFUSED, "Trop d'essais avec un mauvais code : attendez une minute." to Cause.PIN_LOCKED,
            "L'autorisation de ce téléphone a expiré : reconnexion à la TV en cours." to Cause.TOKEN_EXPIRED,
            "Plus de place sur la TV." to Cause.NO_SPACE, "Un autre fichier du même nom est déjà sur la TV." to Cause.NAME_TAKEN,
            "la TV ne garde pas le transfert" to Cause.RESUME_FAILED, "la TV refuse la copie après 7 essais" to Cause.RESUME_FAILED,
            "liaison perdue" to Cause.CONNECTION_LOST, "TV non connectée : l'envoi n'a pas pu démarrer." to Cause.TV_NOT_FOUND,
            "Fichier vide" to Cause.FILE_EMPTY, "quelque chose d'inédit" to Cause.UNKNOWN,
        )
        for ((r, c) in rows) assertEquals(c, UploadFailure.ofReason(r).cause, r)
    }

    @Test fun everyCauseHasAFrenchTextAnAction() {
        val infos = Cause.values().map { c ->
            listOf(exc(SocketTimeoutException()), http(500), UploadFailure.ofReason("x")).firstOrNull { it.cause == c }
        }
        // build one Info per cause through the public entry points
        val all = listOf<UploadFailure.Info>(exc(SocketTimeoutException()), exc(ConnectException()), exc(UnknownHostException()), exc(SocketException("Broken pipe")),
            exc(SocketException("Network is unreachable")), exc(SecurityException()), exc(InterruptedIOException()), exc(IllegalStateException()),
            http(401, "locked"), http(401, "bad token"), http(401), http(404), http(409), http(413), http(500)) +
            listOf("Fichier vide", "annulé", "Envoi interrompu", "Android a limité les envois en arrière-plan", "Service refusé par le système", "Plus de place sur la TV.",
                "même nom", "la TV ne garde pas le transfert", "zzz").map { UploadFailure.ofReason(it) }
        assertEquals(Cause.values().toSet(), all.map { it.cause }.toSet(), "every cause is reachable: ${Cause.values().toSet() - all.map { it.cause }.toSet()}")
        assertNotNull(infos)
        for (i in all) {
            assertTrue(i.what.isNotBlank(), "${i.cause} what"); assertTrue(i.action.isNotBlank(), "${i.cause} action")
            assertTrue(UploadFailure.message(i, 63).isNotBlank())
        }
        assertEquals(all.size, all.size)
    }

    @Test fun theOwnersSentence() {
        val m = UploadFailure.message(exc(SocketException("Broken pipe")), 63)
        assertEquals("Copie interrompue : la connexion à la TV a été coupée à 63 %. Touchez pour réessayer", m)
        assertEquals("Copie impossible : la connexion à la TV a été coupée. Touchez pour réessayer", UploadFailure.message(exc(SocketException("Broken pipe")), null))
        assertTrue("Copie impossible" in UploadFailure.message(exc(SocketException("Broken pipe")), 0))
    }

    @Test fun pinCausesAskForThePin() {
        assertTrue(http(401).asksPin); assertTrue(http(401, "locked").asksPin)
        assertTrue("code PIN" in UploadFailure.message(http(401), 10))
        assertFalse(http(507).asksPin)
    }

    @Test fun noSensitiveDataEverLeaks() {
        val secret = listOf(
            exc(FileNotFoundException("/storage/emulated/0/Android/media/org.telegram.messenger/Telegram Video/secret_film.mp4: open failed")),
            exc(SecurityException("Permission Denial: content://com.android.providers.media.documents/document/video%3A12345 from pid")),
            exc(IOException("Authorization: Bearer cbt_aabbccddeeff00112233445566778899")),
            exc(IOException("pin=482913 refused")),
            UploadFailure.ofReason("Échec avec le code 482913 sur /data/user/0/app/files/x"),
            http(401, "{\"pin\":\"482913\",\"token\":\"cbt_abcdef0123456789\"}"),
        )
        for (i in secret) for (t in listOf(i.what, i.action, i.technical, UploadFailure.message(i, 5))) {
            assertFalse("/storage" in t || "/data/" in t || "content://" in t, t)
            assertFalse(Regex("\\d{6}").containsMatchIn(t), t)
            assertFalse("cbt_" in t || "Bearer" in t || "secret_film" in t || "482913" in t, t)
        }
    }

    @Test fun technicalIsOneShortLine() {
        val i = exc(IOException("a".repeat(500) + "\nsecond line"))
        assertTrue(i.technical.length <= 120); assertFalse('\n' in i.technical)
        assertEquals("", UploadFailure.sanitize(null))
        assertEquals("sans chemin", UploadFailure.sanitize("sans chemin"))
    }

    @Test fun unknownCauseIsGenericWithTheSummarisedException() {
        val i = exc(IllegalStateException("etat impossible"))
        assertEquals(Cause.UNKNOWN, i.cause)
        assertTrue("IllegalStateException" in i.technical && "etat impossible" in i.technical)
        assertTrue(UploadFailure.message(i, 40).contains("40 %"))
        val r = UploadFailure.ofReason("quelque chose d'inédit")
        assertTrue("quelque chose d'inédit" in r.technical)
    }
}
