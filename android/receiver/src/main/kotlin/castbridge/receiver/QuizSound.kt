package castbridge.receiver

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * The quiz's little sounds, synthesized at start-up (a few sine notes with an envelope, written once as tiny WAV files
 * in the cache, ~150 ko in all) and played through a SoundPool: no audio asset in the APK, no music licence, low latency.
 * Everything fails silently: a TV without audio output simply plays the quiz without sound.
 */
class QuizSound(ctx: Context) {
    enum class Clip { JOIN, SELECT, LOCK, RIGHT, WRONG, TICK, WIN, NEXT }
    var enabled = true
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(3)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    }.getOrNull()
    private val ids = HashMap<Clip, Int>()

    init {
        val dir = File(ctx.cacheDir, "quiz-sounds").apply { mkdirs() }
        for (c in Clip.values()) runCatching {
            val f = File(dir, "${c.name.lowercase()}-v1.wav")
            if (!f.isFile || f.length() < 100) writeWav(f, render(c))
            pool?.load(f.path, 1)?.let { ids[c] = it }
        }
    }

    fun play(c: Clip, volume: Float = 0.8f) {
        if (!enabled) return
        val id = ids[c] ?: return
        runCatching { pool?.play(id, volume, volume, 1, 0, 1f) }
    }

    fun release() { runCatching { pool?.release() } }

    private data class Note(val hz: Double, val ms: Int, val gain: Double = 0.6)

    private fun render(c: Clip): ShortArray = when (c) {
        Clip.JOIN -> seq(Note(E6, 90, 0.4), Note(A6, 160, 0.4))
        Clip.SELECT -> seq(Note(A5, 140, 0.45))
        Clip.NEXT -> seq(Note(C5, 90, 0.4), Note(G5, 150, 0.4))
        Clip.TICK -> seq(Note(1_000.0, 35, 0.3))
        Clip.LOCK -> pulse(110.0, 1_700)                                   // low heartbeat for the suspense
        Clip.RIGHT -> seq(Note(C5, 110), Note(E5, 110), Note(G5, 110), Note(C6, 380))
        Clip.WRONG -> seq(Note(G4, 260, 0.55), Note(DS4, 520, 0.55))
        Clip.WIN -> seq(Note(C5, 140), Note(E5, 140), Note(G5, 140), Note(C6, 260), Note(G5, 140), Note(C6, 700))
    }

    private fun seq(vararg notes: Note): ShortArray {
        val out = ArrayList<Short>()
        for (n in notes) {
            val len = RATE * n.ms / 1000
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                val env = envelope(i, len)
                val v = (sin(2 * PI * n.hz * t) + 0.25 * sin(4 * PI * n.hz * t) + 0.1 * sin(6 * PI * n.hz * t)) / 1.35
                out += (v * env * n.gain * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    private fun pulse(hz: Double, ms: Int): ShortArray {
        val len = RATE * ms / 1000
        return ShortArray(len) { i ->
            val t = i.toDouble() / RATE
            val beat = (t * 2.2) % 1.0                                     // ~130 bpm heartbeat
            val env = if (beat < 0.12) sin(PI * beat / 0.12) else if (beat in 0.22..0.34) 0.7 * sin(PI * (beat - 0.22) / 0.12) else 0.0
            val fade = envelope(i, len)
            (sin(2 * PI * hz * t) * env * fade * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** 5 ms attack, exponential-ish release over the last 40 %. */
    private fun envelope(i: Int, len: Int): Double {
        val a = RATE * 5 / 1000
        return when {
            i < a -> i.toDouble() / a
            i > len * 0.6 -> maxOf(0.0, (len - i) / (len * 0.4)).let { it * it }
            else -> 1.0
        }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val tmp = File(f.path + ".tmp")
        RandomAccessFile(tmp, "rw").use { w ->
            w.setLength(0)
            val data = pcm.size * 2
            fun i32(v: Int) { w.write(v and 0xff); w.write(v shr 8 and 0xff); w.write(v shr 16 and 0xff); w.write(v shr 24 and 0xff) }
            fun i16(v: Int) { w.write(v and 0xff); w.write(v shr 8 and 0xff) }
            w.writeBytes("RIFF"); i32(36 + data); w.writeBytes("WAVE")
            w.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
            w.writeBytes("data"); i32(data)
            val buf = ByteArray(data)
            pcm.forEachIndexed { k, s -> buf[2 * k] = (s.toInt() and 0xff).toByte(); buf[2 * k + 1] = (s.toInt() shr 8 and 0xff).toByte() }
            w.write(buf)
        }
        tmp.renameTo(f)
    }

    companion object {
        private const val RATE = 22_050
        private const val DS4 = 311.13; private const val G4 = 392.0; private const val C5 = 523.25; private const val E5 = 659.25
        private const val G5 = 783.99; private const val A5 = 880.0; private const val C6 = 1046.5; private const val E6 = 1318.5; private const val A6 = 1760.0
    }
}
