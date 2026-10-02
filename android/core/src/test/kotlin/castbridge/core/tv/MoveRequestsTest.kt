package castbridge.core.tv

import java.io.File
import kotlin.test.*

class MoveRequestsTest {
    private val dir = kotlin.io.path.createTempDirectory("moves").toFile()
    private val f = File(dir, "move-requests.jsonl")
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    private fun req(i: Int) = MoveRequests.Request("id$i", "content://media/$i", "film \"$i\" é.mkv", 1000L + i)

    @Test fun requestsAreHandledOneAtATimeInOrder() {
        val q = MoveRequests(f)
        (1..4).forEach { q.add(req(it)) }
        val seen = ArrayList<String>()
        while (true) { val r = q.next() ?: break; seen += r.id; q.done(r.id) }
        assertEquals(listOf("id1", "id2", "id3", "id4"), seen)
        assertEquals(0, q.size())
    }

    @Test fun theQueueSurvivesARestartAndKeepsTheUnhandledOnes() {
        val q = MoveRequests(f)
        (1..3).forEach { q.add(req(it)) }
        q.done("id1")
        val reborn = MoveRequests(f)                                   // the app was killed: a new object over the same file
        assertEquals(listOf(req(2), req(3)), reborn.all())
        assertEquals("id2", reborn.next()?.id)
    }

    @Test fun theSameOriginalQueuedTwiceIsOneRequestAndADamagedLineIsSkipped() {
        val q = MoveRequests(f)
        q.add(req(1)); q.add(req(1).copy(id = "other"))
        assertEquals(1, q.size())
        f.appendText("not json\n")
        assertEquals(1, MoveRequests(f).size(), "a damaged line never becomes a request")
    }

    @Test fun moveDeletesOnlyAfterProof() {
        // a fake « Déplacer »: the TV says complete and of the right size, but this job sent no byte (a homonym that was already there)
        assertFalse(MoveProof.byUpload(sentByThisJob = 0, size = 100, tvCheckedSize = true, tvDone = true))
        assertFalse(MoveProof.byUpload(sentByThisJob = 100, size = 100, tvCheckedSize = false, tvDone = true), "an older TV ignoring the size proves nothing")
        assertFalse(MoveProof.byUpload(sentByThisJob = 99, size = 100, tvCheckedSize = true, tvDone = true))
        assertFalse(MoveProof.byUpload(sentByThisJob = 100, size = 100, tvCheckedSize = true, tvDone = false))
        assertTrue(MoveProof.byUpload(sentByThisJob = 100, size = 100, tvCheckedSize = true, tvDone = true))
        assertFalse(MoveProof.byFastTransfer(done = true, rootVerified = false))
        assertTrue(MoveProof.byFastTransfer(done = true, rootVerified = true))
    }

    @Test fun alreadyThereNeedsSizeAndBothEdges() {
        val a = ByteArray(10) { 1 }; val b = ByteArray(10) { 2 }
        assertTrue(MoveProof.alreadyThere(5000, 5000, a, a.copyOf(), b, b.copyOf()))
        assertFalse(MoveProof.alreadyThere(5000, 5001, a, a, b, b), "size differs")
        assertFalse(MoveProof.alreadyThere(5000, 5000, a, b, b, b), "head differs")
        assertFalse(MoveProof.alreadyThere(5000, 5000, a, a, b, a), "tail differs")
        assertFalse(MoveProof.alreadyThere(5000, 5000, a, a, null, null), "tail not read is no proof")
        assertEquals((0L to 10) to (0L to 10), MoveProof.edges(10))
        assertEquals((0L to MoveProof.EDGE) to (1_000_000L - MoveProof.EDGE to MoveProof.EDGE), MoveProof.edges(1_000_000))
    }
}
