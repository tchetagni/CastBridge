package castbridge.sender.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import castbridge.sender.PhoneConnect

/**
 * playback_start / playback_end of the phone player (docs/TELEMETRY.md): codec and resolution of the video format,
 * where the media comes from (a file of the phone, or a stream), time watched, % seen. Never the name or the address.
 */
class PlaybackStats(private val p: ExoPlayer) : Player.Listener {
    private var item: MediaItem? = null
    private var started = false
    private var playingSince = 0L
    private var watched = 0L
    private var codec: String? = null
    private var resolution: String? = null
    private var source = "phone"

    init { p.addListener(this) }

    fun detach() { end(ok = true, error = null); p.removeListener(this) }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val now = System.currentTimeMillis()
        if (isPlaying) {
            if (!started) begin()
            playingSince = now
        } else if (playingSince > 0) { watched += now - playingSince; playingSince = 0 }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { end(ok = true, error = null) }

    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) end(ok = true, error = null, ended = true) }

    override fun onPlayerError(error: PlaybackException) { end(ok = false, error = error.errorCodeName.removePrefix("ERROR_CODE_").lowercase()) }

    private fun begin() {
        started = true
        item = p.currentMediaItem
        val f = p.videoFormat
        codec = (f?.sampleMimeType ?: p.audioFormat?.sampleMimeType)?.substringAfter('/')
        resolution = f?.takeIf { it.width > 0 }?.let { "${it.width}x${it.height}" }
        val scheme = item?.localConfiguration?.uri?.scheme
        source = if (scheme == "http" || scheme == "https") "stream" else "phone"
        PhoneConnect.track("playback_start", mapOf("codec" to codec, "resolution" to resolution, "source" to source))
    }

    private fun end(ok: Boolean, error: String?, ended: Boolean = false) {
        if (!started) return
        val now = System.currentTimeMillis()
        if (playingSince > 0) { watched += now - playingSince; playingSince = 0 }
        val dur = p.duration.takeIf { it > 0 }
        val pct = if (ended) 100.0 else dur?.let { (p.currentPosition.coerceAtLeast(0) * 100.0 / it).coerceIn(0.0, 100.0) }
        PhoneConnect.track("playback_end", mapOf("ms" to watched, "pct" to pct, "codec" to codec, "resolution" to resolution, "source" to source,
            "abandoned" to (!ended && ok && (pct ?: 0.0) < 90.0), "ok" to ok, "error" to error))
        started = false; watched = 0; item = null
    }
}
