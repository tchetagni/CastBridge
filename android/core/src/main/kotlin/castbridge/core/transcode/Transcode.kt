package castbridge.core.transcode

/** Same five profiles as server/castbridge_server.py. */
enum class Profile(val id: String, val height: Int, val videoKbps: Int, val audioKbps: Int) {
    REMUX_AAC("remux-aac", 0, 0, 192),
    DLNA_480("dlna-480", 480, 1200, 128),
    DLNA_720("dlna-720", 720, 3500, 160),
    DLNA_1080("dlna-1080", 1080, 8000, 192),
    AUDIO_MP3("audio-mp3", 0, 0, 192);

    /** libVLC sout chain for on-phone transcoding, writing MPEG-TS over HTTP. */
    fun sout(port: Int, path: String): String {
        val tc = when (this) {
            REMUX_AAC -> "vcodec=h264,acodec=mp4a,ab=$audioKbps,channels=2"
            AUDIO_MP3 -> "acodec=mp3,ab=$audioKbps,channels=2"
            else -> "vcodec=h264,venc=x264{preset=veryfast,profile=high},vb=$videoKbps,height=$height,scale=0," +
                "acodec=mp4a,ab=$audioKbps,channels=2"
        }
        val mux = if (this == AUDIO_MP3) "raw" else "ts"
        return "#transcode{$tc}:http{mux=$mux,dst=:$port/$path}"
    }

    companion object {
        fun byId(id: String) = values().firstOrNull { it.id == id }
    }
}

data class MediaInfo(
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val tenBit: Boolean = false,
    val isAudioOnly: Boolean = false,
)

/** What the target renderer can play natively. Empty sets mean "unknown" -> conservative. */
data class Capabilities(
    val videoCodecs: Set<String> = setOf("h264"),
    val audioCodecs: Set<String> = setOf("aac", "mp3"),
    val containers: Set<String> = setOf("mp4", "mpegts", "mov"),
    val maxHeight: Int = 1080,
    val supports10Bit: Boolean = false,
)

sealed class Route {
    object Direct : Route()
    data class Transcode(val profile: Profile, val viaPc: Boolean) : Route()
}

object RoutePlanner {
    fun plan(info: MediaInfo, caps: Capabilities, pcAvailable: Boolean, forced: Profile? = null): Route {
        fun tc(p: Profile) = Route.Transcode(p, pcAvailable)
        if (forced != null) return tc(forced)
        if (info.isAudioOnly) {
            return if (info.audioCodec in caps.audioCodecs && info.container in caps.containers) Route.Direct
            else tc(Profile.AUDIO_MP3)
        }
        val videoOk = info.videoCodec in caps.videoCodecs &&
            (!info.tenBit || caps.supports10Bit) && info.height <= caps.maxHeight
        val audioOk = info.audioCodec == null || info.audioCodec in caps.audioCodecs
        val containerOk = info.container in caps.containers
        return when {
            videoOk && audioOk && containerOk -> Route.Direct
            videoOk && info.videoCodec == "h264" -> tc(Profile.REMUX_AAC)   // only audio/container is the issue
            info.height > 1080 || info.tenBit -> tc(if (pcAvailable) Profile.DLNA_1080 else Profile.DLNA_720)
            else -> tc(if (pcAvailable) Profile.DLNA_1080 else Profile.DLNA_720)
        }
    }
}
