package castbridge.core.library.agent

import castbridge.core.FakePlayer
import castbridge.core.tv.*
import java.io.File
import java.io.RandomAccessFile

/**
 * A pretend TV for demos and emulator screenshots (NOT a test): a real [ReceiverServer] on the given port with a messy library
 * made of sparse files (sizes look real, nothing is written to disk), the bin routes, and play marks.
 * Run it from the compiled test classes:  java -cp ... castbridge.core.library.agent.DemoTv 8765 123456
 */
object DemoTv {
    private const val MB = 1L shl 20
    private const val GB = 1L shl 30

    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.getOrNull(0)?.toInt() ?: 8765
        val pin = args.getOrNull(1) ?: "123456"
        val root = File(System.getProperty("java.io.tmpdir"), "castbridge-demo-tv").apply { deleteRecursively(); mkdirs() }
        val internal = File(root, "internal").apply { mkdirs() }
        val usb = File(root, "usb").apply { mkdirs() }
        fun sparse(dir: File, name: String, size: Long, seed: Int = 0) = RandomAccessFile(File(dir, name), "rw").use { f ->
            f.setLength(size)
            if (seed != 0) { f.seek(0); f.write(ByteArray(64) { (seed + it).toByte() }) }
        }
        // internal memory: nearly full
        sparse(internal, "Prison.Break.S01E01.FRENCH.DVDRip.XviD-JMT.avi", 350 * MB, 1)
        sparse(internal, "Prison.Break.S01E02.FRENCH.DVDRip.XviD-JMT.avi", 350 * MB, 2)
        sparse(internal, "prison break s01e03 vostfr 720p.mkv", 420 * MB, 3)
        sparse(internal, "[www.torrent9.ph] Breaking.Bad.S05E14.FRENCH.720p.WEB-DL.H264-Ghost.mkv", 400 * MB, 4)
        sparse(internal, "Inception.2010.1080p.BluRay.x264-SPARKS.mkv", 1400 * MB, 5)
        sparse(internal, "WhatsApp Video 2024-03-15 at 14.22.11.mp4", 38 * MB, 6)
        sparse(internal, "VID-20240315-WA0012.mp4", 22 * MB, 7)
        sparse(internal, "Mariage Jean & Sophie 2022.mp4", 310 * MB, 8)
        sparse(internal, "Burna Boy - Last Last (Official Video).mp4", 48 * MB, 9)
        sparse(internal, "Cours de Maths - Chapitre 3 - Les Limites.mp4", 120 * MB, 10)
        sparse(internal, "Film A.mkv", 600 * MB, 11)
        sparse(internal, "Intouchables (2011) MULTi 1080p BluRay.mkv", 900 * MB, 12)
        sparse(internal, "Prison Break – S01E04.mkv", 380 * MB, 13)
        // USB key: plenty of room, some of it already tidy, one duplicate of "Film A", one lower-quality version
        sparse(usb, "Film A (1).mkv", 600 * MB, 11)
        sparse(usb, "Taxi.2.2000.FRENCH.DVDRip.mkv", 1200 * MB, 14)
        sparse(usb, "The.Walking.Dead.S10E22.720p.HDTV.x264-SYNCOPY.mkv", 1500 * MB, 15)
        sparse(usb, "The.Walking.Dead.S10E22.1080p.WEB.H264.mkv", 2600 * MB, 16)
        sparse(usb, "Backup films 2023.mkv", 30 * GB, 17)

        val now = System.currentTimeMillis()
        val meta = object : LibraryMeta {
            override fun meta(name: String, size: Long): FileMeta = when {
                name.startsWith("The.Walking.Dead.S10E22.720p") -> FileMeta(durationMs = 2_580_000, watched = true, playedAtMs = now - 150L * 86_400_000)
                name.startsWith("Taxi.2") -> FileMeta(durationMs = 5_220_000, watched = true, playedAtMs = now - 220L * 86_400_000)
                name.startsWith("Inception") -> FileMeta(durationMs = 8_880_000)
                name.startsWith("Film A") -> FileMeta(durationMs = 6_000_000)
                else -> FileMeta()
            }
            override fun thumb(name: String, size: Long, file: File?): ByteArray? = null
            override fun thumbFailed(name: String, size: Long, file: File?) = true
        }
        val capacity = mapOf("internal" to 6_200L * MB, "usb-1234" to 58L * GB)
        fun used(d: File) = d.walkTopDown().filter { it.isFile && !it.path.contains(TrashApi.BIN) }.sumOf { it.length() }
        val registry = VolumeRegistry(StaticVolumes {
            listOf(
                StorageVolume("internal", "Mémoire interne", internal, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, capacity.getValue("internal"), false),
                StorageVolume("usb-1234", "Clé USB", usb, VolumeKind.REMOVABLE, Fs.EXFAT, 0, capacity.getValue("usb-1234"), true, true, 0),
            )
        }) { v -> (capacity[v.id] ?: 0L) - used(v.dir) }.also { it.refresh() }
        val player = FakePlayer()
        val trash = TrashApi(registry, playing = { player.state().takeIf { it.state != "idle" }?.name }, library = meta)
        ReceiverServer(registry, player, port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), pin = pin, guard = PinGuard(pin, maxFailures = 1000),
            extension = trash, library = meta).start(5000, false)
        println("DemoTv on port $port, PIN $pin, root $root")
        while (true) Thread.sleep(60_000)
    }
}
